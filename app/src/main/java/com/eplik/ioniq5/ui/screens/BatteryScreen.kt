package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.core.poll.batteryPowerKw
import com.eplik.ioniq5.core.poll.cellVoltages
import com.eplik.ioniq5.core.poll.moduleTemperatures
import com.eplik.ioniq5.ui.components.BatteryCellGrid
import com.eplik.ioniq5.ui.components.CellColorMode
import com.eplik.ioniq5.ui.components.ModuleTemperatureGrid
import com.eplik.ioniq5.ui.components.ValueTile
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow

@Composable
fun BatteryScreen(readings: Readings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val cells = readings.cellVoltages()
    val moduleTemps = readings.moduleTemperatures()
    var mode by rememberSaveable { mutableStateOf(CellColorMode.VOLTAGE) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val cellsFresh = readings[P.cellId(1)].isFresh(now)

    @Composable
    fun tile(labelRes: Int, id: String, decimals: Int, unit: String, detail: String? = null) =
        ValueTile(stringResource(labelRes), formatValue(readings[id]?.value, decimals), unit, readings[id].isFresh(now), detail = detail)

    fun cellNo(id: String) = readings[id]?.value?.toInt()?.let { "#$it" }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.tab_battery), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 16.dp))
        }
        if (cells.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().alpha(if (cellsFresh) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == CellColorMode.VOLTAGE,
                            onClick = { mode = CellColorMode.VOLTAGE },
                            label = { Text(stringResource(R.string.mode_voltage)) },
                        )
                        FilterChip(
                            selected = mode == CellColorMode.DEVIATION,
                            onClick = { mode = CellColorMode.DEVIATION },
                            label = { Text(stringResource(R.string.mode_deviation)) },
                        )
                    }
                    Text(
                        stringResource(R.string.cells_summary, cells.size, formatValue(cells.min(), 2), formatValue(cells.max(), 2)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    BatteryCellGrid(cells, mode, selected, onSelect = { selected = it }, modifier = Modifier.fillMaxWidth())
                    val sel = selected?.takeIf { it in cells.indices }
                    Text(
                        if (sel == null) {
                            stringResource(R.string.tap_cell_hint)
                        } else {
                            stringResource(
                                R.string.selected_cell,
                                sel + 1,
                                formatValue(cells[sel], 2),
                                String.format(java.util.Locale.getDefault(), "%+.0f", (cells[sel] - cells.average()) * 1000),
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (moduleTemps.any { it != null }) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(if (readings[P.moduleTempId(1)].isFresh(now)) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.module_temps), style = MaterialTheme.typography.titleMedium)
                    ModuleTemperatureGrid(moduleTemps, Modifier.fillMaxWidth())
                }
            }
        }
        item { tile(R.string.soc_display, P.SOC_DISPLAY, 1, "%", stringResource(R.string.candidate)) }
        item { tile(R.string.soc_bms, P.SOC_BMS, 1, "%") }
        item { tile(R.string.soh, P.SOH, 1, "%") }
        item {
            ValueTile(
                stringResource(R.string.battery_power),
                formatValue(readings.batteryPowerKw(), 1),
                "kW",
                readings[P.BATTERY_CURRENT].isFresh(now),
            )
        }
        item { tile(R.string.battery_voltage, P.BATTERY_VOLTAGE, 1, "V") }
        item { tile(R.string.battery_current, P.BATTERY_CURRENT, 1, "A") }
        item { tile(R.string.temp_max, P.BATTERY_TEMP_MAX, 0, "°C") }
        item { tile(R.string.temp_min, P.BATTERY_TEMP_MIN, 0, "°C") }
        item { tile(R.string.cell_max, P.CELL_V_MAX, 2, "V", cellNo(P.CELL_V_MAX_NO)) }
        item { tile(R.string.cell_min, P.CELL_V_MIN, 2, "V", cellNo(P.CELL_V_MIN_NO)) }
        item {
            val max = readings[P.CELL_V_MAX]?.value
            val min = readings[P.CELL_V_MIN]?.value
            ValueTile(
                stringResource(R.string.cell_deviation),
                formatValue(if (max != null && min != null) (max - min) * 1000 else null, 0),
                "mV",
                readings[P.CELL_V_MAX].isFresh(now),
            )
        }
        item { tile(R.string.total_charged, P.CUMULATIVE_CHARGED_KWH, 1, "kWh") }
        item { tile(R.string.total_discharged, P.CUMULATIVE_DISCHARGED_KWH, 1, "kWh") }
        item { tile(R.string.operating_time, P.OPERATING_HOURS, 0, "h") }
    }
}
