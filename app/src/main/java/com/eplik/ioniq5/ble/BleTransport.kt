package com.eplik.ioniq5.ble

import com.eplik.ioniq5.R
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.eplik.ioniq5.core.transport.Transport
import com.eplik.ioniq5.core.transport.TransportState
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * LELink (ELM327 BLE) ühendus. Adapter kasutab UART-laadset teenust FFE0, karakteristikut FFE1:
 * sinna kirjutatakse käsud ja sealt tulevad teavitustena vastused.
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : Transport {

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 512)
    override val incoming: SharedFlow<ByteArray> = _incoming

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var characteristic: BluetoothGattCharacteristic? = null
    @Volatile private var ready: CompletableDeferred<Unit>? = null
    @Volatile private var maxChunk = DEFAULT_CHUNK
    private val writeDone = Channel<Boolean>(Channel.CONFLATED)
    private val writeMutex = Mutex()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (!g.requestMtu(REQUESTED_MTU)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val pending = ready
                _state.value = if (pending != null && !pending.isCompleted) TransportState.FAILED else TransportState.DISCONNECTED
                pending?.completeExceptionally(IOException("Ühendus katkes (staatus $status)"))
                writeDone.trySend(false)
                g.close()
                if (gatt === g) gatt = null
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) maxChunk = (mtu - 3).coerceAtLeast(DEFAULT_CHUNK)
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = findCharacteristic(g)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null) {
                ready?.completeExceptionally(IOException(context.getString(R.string.error_service_not_found)))
                return
            }
            characteristic = ch
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD)
            if (cccd == null || !writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                markReady()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            markReady()
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            _incoming.tryEmit(value)
        }

        // Android 12 ja vanemad
        @Deprecated("Asendatud API 33-s")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            ch.value?.let { _incoming.tryEmit(it.copyOf()) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            writeDone.trySend(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    override suspend fun connect() {
        _state.value = TransportState.CONNECTING
        val pending = CompletableDeferred<Unit>()
        ready = pending
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { pending.await() }
        } catch (e: Exception) {
            closeGatt()
            _state.value = TransportState.FAILED
            throw if (e is IOException) e else IOException(context.getString(R.string.error_connect_failed), e)
        }
    }

    override suspend fun disconnect() {
        closeGatt()
        _state.value = TransportState.DISCONNECTED
    }

    override suspend fun write(data: ByteArray) = writeMutex.withLock {
        val g = gatt ?: throw IOException(context.getString(R.string.error_not_connected))
        val ch = characteristic ?: throw IOException(context.getString(R.string.error_not_connected))
        val writeType = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        for (chunk in data.toList().chunked(maxChunk)) {
            writeDone.tryReceive() // vana tulemus minema
            if (!writeCharacteristic(g, ch, chunk.toByteArray(), writeType)) throw IOException(context.getString(R.string.error_write_failed))
            val ok = withTimeout(WRITE_TIMEOUT_MS) { writeDone.receive() }
            if (!ok) throw IOException(context.getString(R.string.error_write_failed))
        }
    }

    private fun markReady() {
        _state.value = TransportState.CONNECTED
        ready?.complete(Unit)
    }

    private fun closeGatt() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        characteristic = null
    }

    private fun findCharacteristic(g: BluetoothGatt): BluetoothGattCharacteristic? {
        g.getService(SERVICE)?.getCharacteristic(CHARACTERISTIC)?.let { return it }
        // Varuvariant: esimene karakteristik, mis toetab nii kirjutamist kui ka teavitusi
        val write = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        return g.services.flatMap { it.characteristics }.firstOrNull {
            it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && it.properties and write != 0
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        g.writeCharacteristic(ch, value, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
        ch.writeType = writeType
        ch.value = value
        g.writeCharacteristic(ch)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            g.writeDescriptor(descriptor)
        }

    private companion object {
        val SERVICE: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val CHARACTERISTIC: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEFAULT_CHUNK = 20
        const val REQUESTED_MTU = 185
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val WRITE_TIMEOUT_MS = 2_000L
    }
}
