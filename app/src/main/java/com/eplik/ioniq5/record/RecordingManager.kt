package com.eplik.ioniq5.record

import android.content.Context
import com.eplik.ioniq5.connection.ConnectionManager
import com.eplik.ioniq5.connection.ConnectionStatus
import com.eplik.ioniq5.core.pid.Ioniq5Pids
import com.eplik.ioniq5.core.record.RecordingFormat
import com.eplik.ioniq5.core.record.RecordingSummarizer
import com.eplik.ioniq5.core.record.RecordingSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecordingInfo(val file: File, val summary: RecordingSummary)

/**
 * Salvestab ühenduse ajal iga [intervalMs] järel rea CSV-faili (failid: filesDir/recordings).
 * Uus fail alustatakse, kui eelmisest reast on möödas üle [newFileGapMs] (auto oli väljas,
 * ühendus katkes). Une režiimis ei salvestata.
 */
class RecordingManager(
    context: Context,
    private val connection: ConnectionManager,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 5_000,
    private val newFileGapMs: Long = 5 * 60_000,
) {
    val directory: File = File(context.filesDir, "recordings").apply { mkdirs() }

    private val _recordings = MutableStateFlow<List<RecordingInfo>>(emptyList())
    val recordings: StateFlow<List<RecordingInfo>> = _recordings.asStateFlow()

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile.asStateFlow()

    private var lastRowMs = 0L
    private val nameFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

    fun start() {
        scope.launch(Dispatchers.IO) {
            refresh()
            while (true) {
                delay(intervalMs)
                runCatching { tick() }
            }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val readings = connection.readings.value
        val active = connection.status.value is ConnectionStatus.Connected &&
            !connection.sleeping.value &&
            readings[Ioniq5Pids.BATTERY_CURRENT]?.let { now - it.timeMs < 15_000 } == true
        if (!active) return
        var file = _currentFile.value
        if (file == null || now - lastRowMs > newFileGapMs) {
            file = File(directory, "ioniq5_${nameFormat.format(Date(now))}.csv")
            file.writeText(RecordingFormat.header + "\n")
            _currentFile.value = file
        }
        file.appendText(RecordingFormat.row(readings, now) + "\n")
        lastRowMs = now
    }

    /** Loeb kõik salvestused ja nende kokkuvõtted (uusim ees). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        _recordings.value = directory.listFiles { f -> f.name.endsWith(".csv") }
            .orEmpty()
            .mapNotNull { file -> load(file)?.let { RecordingInfo(file, it) } }
            .sortedByDescending { it.summary.startMs }
    }

    fun rows(file: File): List<RecordingFormat.Row> = file.useLines { RecordingFormat.parse(it) }

    private fun load(file: File): RecordingSummary? = runCatching { RecordingSummarizer.summarize(rows(file)) }.getOrNull()

    suspend fun delete(file: File) {
        if (file == _currentFile.value) _currentFile.value = null
        withContext(Dispatchers.IO) { file.delete() }
        refresh()
    }
}
