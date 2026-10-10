package com.eplik.ioniq5.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eplik.ioniq5.Ioniq5Application
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.elm.toHex
import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.scan.DevCapture
import com.eplik.ioniq5.core.scan.DidScanner
import com.eplik.ioniq5.core.scan.DiscoveryPlan
import com.eplik.ioniq5.core.scan.EcuScanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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

    // Arenduslogi
    private var captureJob: Job? = null
    private val pendingNotes = ConcurrentLinkedQueue<String>()
    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()
    private val _captureFile = MutableStateFlow<File?>(null)
    val captureFile: StateFlow<File?> = _captureFile.asStateFlow()

    /**
     * Arenduslogi: küsib [DevCapture.REQUESTS] ringiratast, kuni peatatakse, ja kirjutab toorvastused
     * ajatemplitega faili. Märkused lisatakse logisse järgmise päringu eel.
     */
    fun startCapture(note: String) {
        val elm = connection.elm ?: return notConnected()
        if (job?.isActive == true || captureJob?.isActive == true) return
        val app = getApplication<Application>()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val file = File(File(app.cacheDir, "logs").apply { mkdirs() }, "ioniq5_dev_$stamp.txt")
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
        pendingNotes.clear()
        if (note.isNotBlank()) pendingNotes.add(note)
        _message.value = null
        _capturing.value = true
        _captureFile.value = file
        connection.pausePolling()
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            file.bufferedWriter().use { w ->
                w.write("# Ioniq 5 Monitor arenduslogi $stamp, versioon $version\n")
                val start = System.currentTimeMillis()
                var cycle = 0
                while (isActive) {
                    cycle++
                    val elapsed = (System.currentTimeMillis() - start) / 1000
                    _progress.value = app.getString(R.string.capture_progress, cycle, "%d:%02d".format(elapsed / 60, elapsed % 60))
                    for (r in DevCapture.REQUESTS) {
                        while (true) {
                            val n = pendingNotes.poll() ?: break
                            w.write("# MÄRKUS ${time.format(Date())}: $n\n")
                        }
                        val lines = try {
                            elm.consoleRequest(r.ecu, r.request)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            listOf("VIGA: ${e.message}")
                        }
                        w.write("# ${time.format(Date())} ${r.ecu.requestHex} ${r.label}\n> ${r.request}\n")
                        lines.forEach { w.write(it); w.write("\n") }
                    }
                    w.flush()
                }
            }
        }.also {
            it.invokeOnCompletion {
                connection.resumePolling()
                _progress.value = null
                _capturing.value = false
            }
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
    }

    fun addNote(note: String) {
        if (note.isNotBlank()) pendingNotes.add(note)
    }

    /** AT-käsk läheb adapterile; hex-päring läheb [ecuHex] väljal olevale juhtplokile. */
    fun send(input: String, ecuHex: String) {
        val elm = connection.elm ?: return notConnected()
        if (input.isBlank()) return
        val isAt = input.trim().uppercase().startsWith("AT")
        val ecu = ecuHex.trim().toIntOrNull(16)
        if (!isAt && ecu == null) {
            _message.value = getApplication<Application>().getString(R.string.invalid_hex)
            return
        }
        launchExclusive {
            runCatching {
                if (isAt) elm.console(input) else elm.consoleRequest(EcuAddress(ecu!!), input)
            }.onFailure { _message.value = it.message }
        }
    }

    /** Logi tekstina (kopeerimiseks). */
    fun logText(): String = buildString {
        traffic.value.forEach { append(it.format()) }
        if (_results.value.isNotEmpty()) {
            append("# Skaneerimise tulemused\n")
            _results.value.forEach { append("# ").append(it).append('\n') }
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

    /** 3. etapi avastusskaneering: kõik [DiscoveryPlan.STAGE3] sammud järjest. */
    fun runDiscovery() {
        val elm = connection.elm ?: return notConnected()
        val plan = DiscoveryPlan.STAGE3
        launchExclusive {
            _results.value = emptyList()
            plan.forEachIndexed { step, task ->
                try {
                    val found = DidScanner(elm).scan(task.ecu, task.ids, task.service) { done, total ->
                        _progress.value = "${step + 1}/${plan.size} ${task.label} ${task.describe()}: $done/$total"
                    }
                    val lines = found.map { did ->
                        val id = if (task.service == 0x22) "22%04X".format(did.did) else "21%02X".format(did.did)
                        "${task.ecu.requestHex} $id: ${did.data.toHex()}"
                    }
                    _results.value = _results.value + "${task.label} (${task.describe()}): ${found.size}" + lines
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _results.value = _results.value + "${task.label}: ${e.message}"
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        captureJob?.cancel()
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
            connection.pausePolling()
            try {
                block()
            } finally {
                connection.resumePolling()
                _progress.value = null
            }
        }
    }
}
