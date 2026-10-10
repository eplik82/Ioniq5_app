package com.eplik.ioniq5.ui.diagnostics

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eplik.ioniq5.Ioniq5Application
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.elm.toHex
import com.eplik.ioniq5.core.pid.EcuAddress
import com.eplik.ioniq5.core.scan.DevCapture
import com.eplik.ioniq5.core.scan.DidScanner
import com.eplik.ioniq5.core.scan.DiscoveryPlan
import com.eplik.ioniq5.core.scan.ScanTask
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

    // GPS arenduslogi jaoks: read kogutakse järjekorda ja kirjutatakse faili logitsükli sees
    private val gpsLines = ConcurrentLinkedQueue<String>()
    private var locationListener: LocationListener? = null
    private var lastLocation: Location? = null

    private fun startGps() {
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            gpsLines.add("# GPS: asukohaluba puudub")
            return
        }
        val manager = app.getSystemService(LocationManager::class.java) ?: return
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                gpsLines.add(formatLocation(location, lastLocation, time))
                lastLocation = location
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) {
                gpsLines.add("# GPS: välja lülitatud")
            }

            @Deprecated("API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_INTERVAL_MS, 0f, listener, Looper.getMainLooper())
            locationListener = listener
        } catch (e: SecurityException) {
            gpsLines.add("# GPS: ${e.message}")
        } catch (e: IllegalArgumentException) {
            gpsLines.add("# GPS: seadmel puudub")
        }
    }

    private fun stopGps() {
        locationListener?.let { getApplication<Application>().getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        locationListener = null
        lastLocation = null
    }

    override fun onCleared() {
        stopGps()
        super.onCleared()
    }

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
        gpsLines.clear()
        if (note.isNotBlank()) pendingNotes.add(note)
        _message.value = null
        _capturing.value = true
        _captureFile.value = file
        connection.pausePolling()
        startGps()
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
                        while (true) {
                            val g = gpsLines.poll() ?: break
                            w.write(g)
                            w.write("\n")
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
                viewModelScope.launch(Dispatchers.Main) { stopGps() }
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

    /** Avastusskaneering: kõik plaani sammud järjest (vaikimisi [DiscoveryPlan.STAGE3]). */
    fun runDiscovery(plan: List<ScanTask> = DiscoveryPlan.STAGE3) {
        val elm = connection.elm ?: return notConnected()
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

    private companion object {
        const val GPS_INTERVAL_MS = 1_000L
    }
}

/**
 * GPS-rida arenduslogi jaoks: aeg, koordinaadid, kiirus, suund, täpsus, kõrgus ning eelmise punkti
 * suhtes arvutatud kiirendus ja pöörde kiirus.
 */
internal fun formatLocation(loc: Location, prev: Location?, time: SimpleDateFormat): String {
    val sb = StringBuilder("# GPS ")
    sb.append(time.format(Date(loc.time)))
    sb.append(String.format(Locale.US, " %.6f %.6f", loc.latitude, loc.longitude))
    if (loc.hasSpeed()) sb.append(String.format(Locale.US, " kiirus %.1f km/h", loc.speed * 3.6))
    if (loc.hasBearing()) sb.append(String.format(Locale.US, " suund %.0f°", loc.bearing))
    if (loc.hasAccuracy()) sb.append(String.format(Locale.US, " täpsus %.0f m", loc.accuracy))
    if (loc.hasAltitude()) sb.append(String.format(Locale.US, " kõrgus %.0f m", loc.altitude))
    if (prev != null) {
        val dt = (loc.time - prev.time) / 1000.0
        if (dt > 0 && dt < 5) {
            if (loc.hasSpeed() && prev.hasSpeed()) {
                sb.append(String.format(Locale.US, " kiirendus %.2f m/s²", (loc.speed - prev.speed) / dt))
            }
            if (loc.hasBearing() && prev.hasBearing() && loc.speed > 2) {
                var d = loc.bearing - prev.bearing
                if (d > 180) d -= 360
                if (d < -180) d += 360
                sb.append(String.format(Locale.US, " pööre %.1f °/s", d / dt))
            }
        }
    }
    return sb.toString()
}
