package com.eplik.ioniq5.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eplik.ioniq5.Ioniq5Application
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.elm.toHex
import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.scan.DidScanner
import com.eplik.ioniq5.core.scan.EcuScanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    val connection = (application as Ioniq5Application).connection
    val status = connection.status
    val traffic = connection.traffic

    /** Käimasoleva skaneeringu edenemine, nt "ECU 40/247"; null, kui midagi ei käi. */
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Skaneerimise tulemused loetaval kujul. */
    private val _results = MutableStateFlow<List<String>>(emptyList())
    val results: StateFlow<List<String>> = _results.asStateFlow()

    private var job: Job? = null

    fun send(input: String) {
        val elm = connection.elm ?: return notConnected()
        if (input.isBlank()) return
        launchExclusive {
            runCatching { elm.console(input) }.onFailure { _message.value = it.message }
        }
    }

    fun scanEcus() {
        val elm = connection.elm ?: return notConnected()
        launchExclusive {
            _results.value = emptyList()
            try {
                val found = EcuScanner(elm).scan { done, total -> _progress.value = "ECU $done/$total" }
                _results.value = found.map { ecu ->
                    val ids = ecu.identifiers.entries.joinToString("  ") { (did, value) -> "%04X=%s".format(did, value) }
                    "${ecu.address.requestHex} → ${ecu.address.responseHex}  $ids"
                }.ifEmpty { listOf("—") }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _message.value = e.message
            }
        }
    }

    fun scanDids(ecuHex: String, fromHex: String, toHex: String) {
        val elm = connection.elm ?: return notConnected()
        val ecu = ecuHex.trim().toIntOrNull(16)
        val from = fromHex.trim().toIntOrNull(16)
        val to = toHex.trim().toIntOrNull(16)
        if (ecu == null || from == null || to == null || from > to || to > 0xFFFF) {
            _message.value = getApplication<Application>().getString(R.string.invalid_hex)
            return
        }
        launchExclusive {
            _results.value = emptyList()
            try {
                val found = DidScanner(elm).scan(EcuAddress(ecu), from..to) { done, total ->
                    _progress.value = "DID $done/$total"
                }
                _results.value = found.map { "%04X: %s".format(it.did, it.data.toHex()) }.ifEmpty { listOf("—") }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _message.value = e.message
            }
        }
    }

    fun stop() {
        job?.cancel()
        _progress.value = null
    }

    fun clear() {
        connection.clearTraffic()
        _results.value = emptyList()
        _message.value = null
    }

    /** Kirjutab liikluse logi faili ja tagastab selle (jagamiseks). */
    fun exportLog(): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val dir = File(getApplication<Application>().cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, "ioniq5_$stamp.txt")
        file.writeText(
            buildString {
                append("# Ioniq 5 Monitor, diagnostikalogi $stamp\n")
                traffic.value.forEach {
                    append("# ").append(time.format(Date(it.timeMs))).append('\n')
                    append(it.format())
                }
                if (_results.value.isNotEmpty()) {
                    append("# Skaneerimise tulemused\n")
                    _results.value.forEach { append("# ").append(it).append('\n') }
                }
            },
        )
        return file
    }

    private fun notConnected() {
        _message.value = getApplication<Application>().getString(R.string.connection_disconnected)
    }

    private fun launchExclusive(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        _message.value = null
        job = viewModelScope.launch {
            connection.setPollingPaused(true)
            try {
                block()
            } finally {
                connection.setPollingPaused(false)
                _progress.value = null
            }
        }
    }
}
