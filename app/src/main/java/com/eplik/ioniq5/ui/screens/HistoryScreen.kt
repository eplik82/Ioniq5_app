package com.eplik.ioniq5.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.record.RecordingFormat.Column as Col
import com.eplik.ioniq5.core.record.RecordingFormat
import com.eplik.ioniq5.core.record.RecordingSummary
import com.eplik.ioniq5.core.record.RecordingType
import com.eplik.ioniq5.record.RecordingInfo
import com.eplik.ioniq5.record.RecordingManager
import com.eplik.ioniq5.ui.components.LineChart
import com.eplik.ioniq5.ui.components.formatValue
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(manager: RecordingManager, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val recordings by manager.recordings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<RecordingInfo?>(null) }
    LaunchedEffect(Unit) { manager.refresh() }

    val current = selected
    if (current != null) {
        RecordingDetail(manager, current, onBack = { selected = null }, modifier = modifier)
        return
    }
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.displayMedium)
        if (recordings.isEmpty()) {
            Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(recordings, key = { it.file.name }) { rec ->
                RecordingCard(rec.summary, Modifier.clickable { selected = rec })
            }
        }
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun typeLabel(type: RecordingType) = stringResource(
    when (type) {
        RecordingType.DRIVE -> R.string.type_drive
        RecordingType.CHARGE -> R.string.type_charge
        RecordingType.PARKED -> R.string.type_parked
    },
)

private fun duration(ms: Long): String {
    val min = ms / 60_000
    return "%d:%02d".format(min / 60, min % 60)
}

@Composable
private fun RecordingCard(s: RecordingSummary, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${typeLabel(s.type)} · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.startMs)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(summaryLine(s), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun summaryLine(s: RecordingSummary): String {
    val parts = mutableListOf(duration(s.durationMs) + " h")
    if (s.type == RecordingType.DRIVE) {
        parts += formatValue(s.distanceKm, 1) + " km"
        s.consumptionKwhPer100Km?.let { parts += formatValue(it, 1) + " kWh/100 km" }
    }
    if (s.type == RecordingType.CHARGE) {
        parts += "+" + formatValue(s.chargedKwh, 1) + " kWh"
        s.maxChargePowerKw?.let { parts += "max " + formatValue(it, 0) + " kW" }
    }
    parts += "SOC ${formatValue(s.socStart, 0)}→${formatValue(s.socEnd, 0)} %"
    if (s.solarWh > 0.5) parts += "☀ " + formatValue(s.solarWh, 0) + " Wh"
    return parts.joinToString(" · ")
}

@Composable
private fun RecordingDetail(manager: RecordingManager, rec: RecordingInfo, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by produceState<List<RecordingFormat.Row>>(emptyList(), rec.file) {
        value = withContext(Dispatchers.IO) { manager.rows(rec.file) }
    }
    val s = rec.summary
    val start = s.startMs
    fun series(col: Col) = rows.map { (it.timeMs - start) / 60_000.0 to it[col] }
    val minutes: (Double) -> String = { "${formatValue(it, 0)} min" }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(typeLabel(s.type), style = MaterialTheme.typography.displayMedium)
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.startMs)) + " · " + summaryLine(s),
            style = MaterialTheme.typography.titleMedium,
        )
        LineChart(stringResource(R.string.state_of_charge), series(Col.SOC), "%", xLabel = minutes)
        LineChart(stringResource(R.string.battery_power), series(Col.POWER), "kW", zeroLine = true, xLabel = minutes)
        if (s.type == RecordingType.DRIVE) {
            LineChart(stringResource(R.string.speed_calc), series(Col.SPEED), "km/h", xLabel = minutes)
        }
        if (s.type == RecordingType.CHARGE) {
            // Laadimiskõver: võimsus laetuse järgi
            val curve = rows.mapNotNull { r -> r[Col.SOC]?.let { soc -> soc to r[Col.POWER]?.let { -it } } }.sortedBy { it.first }
            LineChart(stringResource(R.string.charge_curve), curve, "kW", xLabel = { "${formatValue(it, 0)} %" })
        }
        LineChart(stringResource(R.string.battery_temp), series(Col.TEMP_MAX), "°C", xLabel = minutes)
        if (s.solarWh > 0.5) LineChart(stringResource(R.string.solar_power), series(Col.SOLAR), "W", xLabel = minutes)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", rec.file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text(stringResource(R.string.export_csv)) }
            OutlinedButton(onClick = {
                scope.launch {
                    manager.delete(rec.file)
                    onBack()
                }
            }) { Text(stringResource(R.string.delete)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}
