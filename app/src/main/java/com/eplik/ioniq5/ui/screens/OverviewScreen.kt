package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.connection.ConnectionStatus
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.core.poll.batteryPowerKw
import com.eplik.ioniq5.ui.components.PixelGauge
import com.eplik.ioniq5.ui.components.ValueTile
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow

@Composable
fun OverviewScreen(
    status: ConnectionStatus,
    readings: Readings,
    sleeping: Boolean,
    onWake: () -> Unit,
    onConnectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    fun fresh(id: String) = readings[id].isFresh(now)

    // Näidiku SOC, kui olemas; muidu BMS-i oma
    val socId = if (readings[P.SOC_DISPLAY] != null) P.SOC_DISPLAY else P.SOC_BMS
    val soc = readings[socId]
    val power = readings.batteryPowerKw()
    val powerFresh = fresh(P.BATTERY_VOLTAGE) && fresh(P.BATTERY_CURRENT)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (sleeping) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.sleep_banner),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = onWake) { Text(stringResource(R.string.wake_now)) }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).alpha(if (soc.isFresh(now)) 1f else 0.45f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.state_of_charge).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    soc?.let { formatValue(it.value, 0) + " %" } ?: stringResource(R.string.no_value),
                    style = MaterialTheme.typography.displayLarge,
                )
                PixelGauge(fraction = soc?.value?.div(100)?.toFloat(), modifier = Modifier.fillMaxWidth(0.8f))
            }
        }
        item {
            ValueTile(
                stringResource(R.string.battery_power),
                formatValue(power, 1),
                "kW",
                powerFresh,
                detail = power?.let {
                    stringResource(if (it < -0.1) R.string.charging_now else if (it > 0.1) R.string.discharging_now else R.string.idle_now)
                },
            )
        }
        item { ValueTile(stringResource(R.string.battery_voltage), formatValue(readings[P.BATTERY_VOLTAGE]?.value, 1), "V", fresh(P.BATTERY_VOLTAGE)) }
        item {
            ValueTile(
                stringResource(R.string.battery_temp),
                "${formatValue(readings[P.BATTERY_TEMP_MIN]?.value, 0)}…${formatValue(readings[P.BATTERY_TEMP_MAX]?.value, 0)}",
                "°C",
                fresh(P.BATTERY_TEMP_MAX),
            )
        }
        item { ValueTile(stringResource(R.string.cabin_temp), formatValue(readings[P.CABIN_TEMP]?.value, 1), "°C", fresh(P.CABIN_TEMP)) }
        item { ValueTile(stringResource(R.string.outdoor_temp), formatValue(readings[P.OUTDOOR_TEMP]?.value, 1), "°C", fresh(P.OUTDOOR_TEMP)) }
        item { ValueTile(stringResource(R.string.aux_battery), formatValue(readings[P.AUX_VOLTAGE]?.value, 1), "V", fresh(P.AUX_VOLTAGE)) }
        item { ValueTile(stringResource(R.string.odometer), formatValue(readings[P.ODOMETER]?.value, 0), "km", readings[P.ODOMETER].isFresh(now - 60_000)) }
        item {
            ValueTile(
                stringResource(R.string.solar_power),
                formatValue(readings[P.SOLAR_POWER]?.value, 0),
                "W",
                fresh(P.SOLAR_POWER),
                detail = stringResource(R.string.candidate),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    statusText(status),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (status is ConnectionStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConnectionClick) {
                        Text(stringResource(if (status is ConnectionStatus.Connected) R.string.connection_title else R.string.connect))
                    }
                    OutlinedButton(onClick = onHistoryClick) { Text(stringResource(R.string.history_title)) }
                    OutlinedButton(onClick = onSettingsClick) { Text(stringResource(R.string.settings)) }
                }
            }
        }
    }
}
