package com.eplik.ioniq5.connection

import android.content.Context
import com.eplik.ioniq5.ble.BleTransport
import com.eplik.ioniq5.ble.FoundDevice
import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.TrafficEntry
import com.eplik.ioniq5.core.transport.Transport
import com.eplik.ioniq5.core.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data class Connecting(val deviceName: String) : ConnectionStatus
    data class Connected(val deviceName: String, val adapterVersion: String) : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

/** Hoiab adapteri ühendust ja ELM327 draiverit; kogub liikluse logi diagnostika jaoks. */
class ConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _traffic = MutableStateFlow<List<TrafficEntry>>(emptyList())
    val traffic: StateFlow<List<TrafficEntry>> = _traffic.asStateFlow()

    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    private var transport: Transport? = null
    private var connectJob: Job? = null

    /** Draiver, kui ühendus on olemas. */
    var elm: Elm327? = null
        private set

    val lastDeviceAddress: String? get() = prefs.getString(KEY_LAST_ADDRESS, null)

    fun connect(found: FoundDevice) {
        connectJob?.cancel()
        connectJob = scope.launch {
            closeCurrent()
            val name = found.name ?: found.address
            _status.value = ConnectionStatus.Connecting(name)
            val newTransport = BleTransport(context, found.device)
            transport = newTransport
            try {
                newTransport.connect()
                val driver = Elm327(newTransport).also { it.trafficListener = ::addTraffic }
                val version = driver.initialize()
                elm = driver
                prefs.edit().putString(KEY_LAST_ADDRESS, found.address).apply()
                _status.value = ConnectionStatus.Connected(name, version)
                // Kui ühendus katkeb, märgime selle
                newTransport.state.drop(1).first { it != TransportState.CONNECTED }
                if (transport === newTransport) {
                    elm = null
                    _status.value = ConnectionStatus.Failed("Ühendus katkes")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                closeCurrent()
                _status.value = ConnectionStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            closeCurrent()
            _status.value = ConnectionStatus.Disconnected
        }
    }

    fun clearTraffic() {
        _traffic.value = emptyList()
    }

    private fun addTraffic(entry: TrafficEntry) {
        _traffic.update { (it + entry).takeLast(MAX_TRAFFIC) }
    }

    private suspend fun closeCurrent() {
        elm = null
        transport?.let {
            transport = null
            runCatching { it.disconnect() }
        }
    }

    private companion object {
        const val KEY_LAST_ADDRESS = "last_address"
        const val MAX_TRAFFIC = 2_000
    }
}
