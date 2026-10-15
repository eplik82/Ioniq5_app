package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.ChargeSession
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.core.poll.acInputPowerKw
import com.eplik.ioniq5.core.poll.batteryPowerKw
import com.eplik.ioniq5.core.poll.isCharging
import com.eplik.ioniq5.core.poll.obcActive
import com.eplik.ioniq5.core.poll.obcOutputPowerKw
import com.eplik.ioniq5.core.poll.minutesToSoc
import com.eplik.ioniq5.ui.components.PixelGauge
import com.eplik.ioniq5.ui.components.ValueTile
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow
import java.text.DateFormat
import java.util.Date

@Composable
fun ChargingScreen(readings: Readings, session: ChargeSession?, capacityKwh: Double, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val power = readings.batteryPowerKw()
    val fresh = readings[P.BATTERY_CURRENT].isFresh(now)
    val charging = fresh && readings.isCharging()
    // Sõidu ajal negatiivne võimsus = taaste (regen), mitte laadimine
    val regen = fresh && power != null && power < -0.5 && !charging
    val soc = readings[P.SOC_DISPLAY]?.value ?: readings[P.SOC_BMS]?.value
    // Laadija andmeid küsitakse harvemini; AC/DC otsustatakse pardalaadija voolu järgi (töötab ainult AC-laadimisel)
    val obcFresh = readings[P.OBC_DC_CURRENT]?.let { now - it.timeMs < OBC_STALE_MS } == true
    val ac = readings.obcActive()?.takeIf { obcFresh }
    val acIn = readings.acInputPowerKw()?.takeIf { ac == true }
    val obcOut = readings.obcOutputPowerKw()?.takeIf { ac == true }

    fun minutes(m: Double?) = m?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) } ?: "—"

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).alpha(if (fresh) 1f else 0.45f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    (
                        stringResource(
                            when {
                                charging -> R.string.charging_now
                                regen -> R.string.regen_now
                                else -> R.string.not_charging
                            },
                        ) +
                            when {
                                !charging || ac == null -> ""
                                ac -> " · AC"
                                else -> " · DC"
                            }
                        ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (charging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (charging) formatValue(-power!!, 1) + " kW" else formatValue(soc, 0) + " %",
                    style = MaterialTheme.typography.displayLarge,
                )
                PixelGauge(fraction = soc?.div(100)?.toFloat(), modifier = Modifier.fillMaxWidth(0.8f))
            }
        }
        item { ValueTile(stringResource(R.string.state_of_charge), formatValue(soc, 1), "%", fresh) }
        item { ValueTile(stringResource(R.string.time_to_80), minutes(readings.minutesToSoc(80.0, capacityKwh)), "h:min", charging) }
        item { ValueTile(stringResource(R.string.time_to_100), minutes(readings.minutesToSoc(100.0, capacityKwh)), "h:min", charging) }
        item { ValueTile(stringResource(R.string.battery_voltage), formatValue(readings[P.BATTERY_VOLTAGE]?.value, 1), "V", fresh) }
        item { ValueTile(stringResource(R.string.battery_current), formatValue(readings[P.BATTERY_CURRENT]?.value, 1), "A", fresh) }
        item {
            ValueTile(
                stringResource(R.string.charge_port_temps),
                "${formatValue(readings[P.CHARGE_PORT_DC1_TEMP]?.value, 0)} / ${formatValue(readings[P.CHARGE_PORT_DC2_TEMP]?.value, 0)} / " +
                    formatValue(readings[P.CHARGE_PORT_AC_TEMP]?.value, 0),
                "°C",
                readings[P.CHARGE_PORT_DC1_TEMP] != null,
            )
        }
        item {
            ValueTile(
                stringResource(R.string.battery_temp),
                "${formatValue(readings[P.BATTERY_TEMP_MIN]?.value, 0)}…${formatValue(readings[P.BATTERY_TEMP_MAX]?.value, 0)}",
                "°C",
                fresh,
            )
        }
        if (acIn != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.ac_charger), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            item { ValueTile(stringResource(R.string.ac_input_power), formatValue(acIn, 2), "kW", true) }
            item {
                ValueTile(
                    stringResource(R.string.ac_voltage),
                    "${formatValue(readings[P.OBC_AC_VOLTAGE]?.value, 0)} / ${formatValue(readings[P.OBC_AC_VOLTAGE_2]?.value, 0)}",
                    "V",
                    true,
                    detail = formatValue(readings[P.OBC_AC_FREQUENCY]?.value, 0) + " Hz",
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ValueTile(
                    stringResource(R.string.ac_phase_currents),
                    listOf(P.OBC_AC_CURRENT_1, P.OBC_AC_CURRENT_2, P.OBC_AC_CURRENT_3)
                        .joinToString(" / ") { formatValue(readings[it]?.value, 1) },
                    "A",
                    true,
                )
            }
            item { ValueTile(stringResource(R.string.obc_output_power), formatValue(obcOut, 2), "kW", obcOut != null) }
            item {
                ValueTile(
                    stringResource(R.string.obc_efficiency),
                    formatValue(if (obcOut != null && acIn > 0) obcOut / acIn * 100 else null, 0),
                    "%",
                    obcOut != null,
                )
            }
            item {
                ValueTile(
                    stringResource(R.string.obc_temps),
                    "${formatValue(readings[P.OBC_TEMP_1]?.value, 0)} / ${formatValue(readings[P.OBC_TEMP_2]?.value, 0)}",
                    "°C",
                    true,
                )
            }
        }
        if (session != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(if (session.active) R.string.session_current else R.string.session_last),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item { ValueTile(stringResource(R.string.session_energy), formatValue(session.chargedKwh, 1), "kWh", true) }
            item {
                ValueTile(
                    stringResource(R.string.session_duration),
                    minutes((session.lastMs - session.startMs) / 60_000.0),
                    "h:min",
                    true,
                    detail = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.startMs)),
                )
            }
            item {
                ValueTile(
                    stringResource(R.string.session_soc),
                    "${formatValue(session.startSoc, 0)} → ${formatValue(soc, 0)}",
                    "%",
                    true,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.charging_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

/** Laadija näit kehtib kauem kui teised (pollitakse iga 5 s, pikk mitmeosaline vastus võib vahel kaduda). */
private const val OBC_STALE_MS = 30_000L
