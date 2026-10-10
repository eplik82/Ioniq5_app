package com.eplik.ioniq5.connection

import com.eplik.ioniq5.R
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.eplik.ioniq5.ble.BleTransport
import com.eplik.ioniq5.ble.FoundDevice
import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.TrafficEntry
import com.eplik.ioniq5.core.pid.Ioniq5Pids
import com.eplik.ioniq5.core.poll.ChargeSession
import com.eplik.ioniq5.core.poll.ChargeSessionTracker
import com.eplik.ioniq5.core.poll.EnergyIntegrator
import com.eplik.ioniq5.core.poll.Poller
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.core.transport.Transport
import com.eplik.ioniq5.core.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    data class Reconnecting(val deviceName: String, val inSeconds: Int) : ConnectionStatus
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

    /** Viimased näidud; jäävad alles ka pärast ühenduse katkemist (UI näitab neid aegununa). */
    private val _readings = MutableStateFlow<Readings>(emptyMap())
    val readings: StateFlow<Readings> = _readings.asStateFlow()

    private val chargeTracker = ChargeSessionTracker()
    private val _chargeSession = MutableStateFlow<ChargeSession?>(null)
    val chargeSession: StateFlow<ChargeSession?> = _chargeSession.asStateFlow()

    /** Päikeseenergia, mis on kogutud selle rakenduse ühenduse ajal (Wh). */
    private val solarIntegrator = EnergyIntegrator()
    private var lastSolarTimeMs = 0L
    private val _solarEnergyWh = MutableStateFlow(0.0)
    val solarEnergyWh: StateFlow<Double> = _solarEnergyWh.asStateFlow()

    private var poller: Poller? = null
    private var pollJob: Job? = null

    /** Auto paistab olevat välja lülitatud ja lugemine on harvendatud (une režiim). */
    private val _sleeping = MutableStateFlow(false)
    val sleeping: StateFlow<Boolean> = _sleeping.asStateFlow()

    @Volatile private var userDisconnected = false

    /** Kas ühendamise/taasühendamise tsükkel käib (ka siis, kui olek on hetkel Failed). */
    val isReconnecting: Boolean get() = connectJob?.isActive == true && !userDisconnected

    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    private var transport: Transport? = null
    private var connectJob: Job? = null

    /** Draiver, kui ühendus on olemas. */
    var elm: Elm327? = null
        private set

    val lastDeviceAddress: String? get() = prefs.getString(KEY_LAST_ADDRESS, null)

    fun connect(found: FoundDevice) {
        userDisconnected = false
        connectJob?.cancel()
        connectJob = scope.launch {
            var failures = 0
            while (true) {
                val wasConnected = connectOnce(found)
                if (userDisconnected) break
                failures = if (wasConnected) 0 else failures + 1
                if (failures > MAX_RECONNECT_FAILURES) break
                val waitMs = RECONNECT_DELAYS_MS[minOf(failures, RECONNECT_DELAYS_MS.size - 1)]
                _status.value = ConnectionStatus.Reconnecting(found.name ?: found.address, (waitMs / 1000).toInt())
                delay(waitMs)
            }
        }
    }

    /** Ühendub viimati kasutatud adapteriga (rakenduse käivitamisel), kui load on olemas. */
    fun connectToLast() {
        if (_status.value !is ConnectionStatus.Disconnected) return
        val address = lastDeviceAddress ?: return
        if (!hasConnectPermission()) return
        val device = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        }.getOrNull() ?: return
        connect(FoundDevice(prefs.getString(KEY_LAST_NAME, null), address, device))
    }

    /**
     * Üks ühendus kuni katkemiseni. Tagastab true, kui ühendus oli vahepeal olemas (katkes hiljem),
     * false, kui ühendamine ebaõnnestus.
     */
    private suspend fun connectOnce(found: FoundDevice): Boolean {
        closeCurrent()
        val name = found.name ?: found.address
        _status.value = ConnectionStatus.Connecting(name)
        val newTransport = BleTransport(context, found.device)
        transport = newTransport
        return try {
            newTransport.connect()
            val driver = Elm327(newTransport).also { it.trafficListener = ::addTraffic }
            val version = driver.initialize()
            elm = driver
            prefs.edit().putString(KEY_LAST_ADDRESS, found.address).putString(KEY_LAST_NAME, found.name).apply()
            _status.value = ConnectionStatus.Connected(name, version)
            startPolling(driver)
            newTransport.state.drop(1).first { it != TransportState.CONNECTED }
            closeCurrent()
            if (!userDisconnected) _status.value = ConnectionStatus.Failed(context.getString(R.string.error_connection_lost))
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            closeCurrent()
            _status.value = ConnectionStatus.Failed(e.message ?: e.javaClass.simpleName)
            false
        }
    }

    fun disconnect() {
        userDisconnected = true
        connectJob?.cancel()
        scope.launch {
            closeCurrent()
            _status.value = ConnectionStatus.Disconnected
        }
    }

    /** Une režiimist välja: järgmine pollimisring loeb kõike. */
    fun wakePolling() {
        poller?.wake()
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private var pauseCount = 0

    /**
     * Peatab pollimise (diagnostika vaade, skaneerimine). Iga [pausePolling] vajab oma [resumePolling];
     * pollimine jätkub, kui kõik peatajad on lõpetanud.
     */
    @Synchronized
    fun pausePolling() {
        pauseCount++
        poller?.paused?.value = true
    }

    @Synchronized
    fun resumePolling() {
        pauseCount = (pauseCount - 1).coerceAtLeast(0)
        poller?.paused?.value = pauseCount > 0
    }

    private fun startPolling(driver: Elm327) {
        val newPoller = Poller(driver, Ioniq5Pids.ALL)
        newPoller.paused.value = synchronized(this) { pauseCount > 0 }
        poller = newPoller
        pollJob = scope.launch {
            launch { newPoller.sleeping.collect { _sleeping.value = it } }
            launch {
                newPoller.readings.collect { latest ->
                    _readings.update { it + latest }
                    _chargeSession.value = chargeTracker.update(_readings.value, System.currentTimeMillis())
                    latest[Ioniq5Pids.SOLAR_POWER]?.takeIf { it.timeMs != lastSolarTimeMs }?.let {
                        lastSolarTimeMs = it.timeMs
                        solarIntegrator.add(it.value, it.timeMs)
                        _solarEnergyWh.value = solarIntegrator.energyWh
                    }
                }
            }
            newPoller.run()
        }
    }

    fun clearTraffic() {
        _traffic.value = emptyList()
    }

    private fun addTraffic(entry: TrafficEntry) {
        // Pidevat pollimist logisse ei kirjuta, ainult seadistust ja diagnostikat (pollimine on siis peatatud)
        if (poller?.paused?.value == false) return
        _traffic.update { (it + entry).takeLast(MAX_TRAFFIC) }
    }

    private suspend fun closeCurrent() {
        pollJob?.cancel()
        pollJob = null
        _sleeping.value = false
        poller = null
        elm = null
        transport?.let {
            transport = null
            runCatching { it.disconnect() }
        }
    }

    private companion object {
        const val KEY_LAST_ADDRESS = "last_address"
        const val KEY_LAST_NAME = "last_name"
        const val MAX_RECONNECT_FAILURES = 10
        val RECONNECT_DELAYS_MS = longArrayOf(3_000, 5_000, 10_000, 20_000, 30_000, 60_000)
        const val MAX_TRAFFIC = 2_000
    }
}
