package com.eplik.ioniq5.connection

import android.content.Context
import com.eplik.ioniq5.ble.BleTransport
import com.eplik.ioniq5.ble.FoundDevice
import com.eplik.ioniq5.core.elm.Elm327
import com.eplik.ioniq5.core.elm.TrafficEntry
import com.eplik.ioniq5.core.pid.Ioniq5Pids
import com.eplik.ioniq5.core.poll.ChargeSession
import com.eplik.ioniq5.core.poll.ChargeSessionTracker
import com.eplik.ioniq5.core.poll.Poller
import com.eplik.ioniq5.core.poll.Readings
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

    /** Viimased näidud; jäävad alles ka pärast ühenduse katkemist (UI näitab neid aegununa). */
    private val _readings = MutableStateFlow<Readings>(emptyMap())
    val readings: StateFlow<Readings> = _readings.asStateFlow()

    private val chargeTracker = ChargeSessionTracker()
    private val _chargeSession = MutableStateFlow<ChargeSession?>(null)
    val chargeSession: StateFlow<ChargeSession?> = _chargeSession.asStateFlow()

    private var poller: Poller? = null
    private var pollJob: Job? = null

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
                startPolling(driver)
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

    /** Peatab pollimise näiteks diagnostika skaneerimise ajaks. */
    fun setPollingPaused(paused: Boolean) {
        poller?.paused?.value = paused
    }

    private fun startPolling(driver: Elm327) {
        val newPoller = Poller(driver, Ioniq5Pids.ALL)
        poller = newPoller
        pollJob = scope.launch {
            launch {
                newPoller.readings.collect { latest ->
                    _readings.update { it + latest }
                    _chargeSession.value = chargeTracker.update(_readings.value, System.currentTimeMillis())
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
        poller = null
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
