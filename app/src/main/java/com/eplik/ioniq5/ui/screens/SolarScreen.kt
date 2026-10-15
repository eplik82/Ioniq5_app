package com.eplik.ioniq5.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.ui.components.ValueTile
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow

/** Päikesekatus: seni kinnitatud ja kandidaatväärtused; võimsus ja energia lisanduvad, kui leitud. */
@Composable
fun SolarScreen(readings: Readings, sessionEnergyWh: Double, modifier: Modifier = Modifier) {
    val now by rememberNow()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.solar_title), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 16.dp))
        }
        item {
            ValueTile(stringResource(R.string.solar_power), "—", "W", false, detail = stringResource(R.string.not_found_yet))
        }
        item {
            ValueTile(
                stringResource(R.string.iccu_hv_voltage),
                formatValue(readings[P.ICCU_HV_VOLTAGE]?.value, 1),
                "V",
                readings[P.ICCU_HV_VOLTAGE].isFresh(now),
            )
        }
        item { ValueTile(stringResource(R.string.solar_energy_total), "—", "kWh", false, detail = stringResource(R.string.not_found_yet)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.aux_system), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        item { ValueTile(stringResource(R.string.aux_soc), formatValue(readings[P.AUX_SOC]?.value, 0), "%", readings[P.AUX_SOC].isFresh(now)) }
        item { ValueTile(stringResource(R.string.aux_battery), formatValue(readings[P.AUX_VOLTAGE_ICCU]?.value, 2), "V", readings[P.AUX_VOLTAGE_ICCU].isFresh(now)) }
        item { ValueTile(stringResource(R.string.aux_current), formatValue(readings[P.AUX_CURRENT]?.value, 1), "A", readings[P.AUX_CURRENT].isFresh(now)) }
        item {
            ValueTile(
                stringResource(R.string.ldc_output),
                "${formatValue(readings[P.LDC_VOLTAGE]?.value, 2)} V · ${formatValue(readings[P.LDC_CURRENT]?.value, 1)}",
                "A",
                readings[P.LDC_VOLTAGE].isFresh(now),
                detail = formatValue(readings[P.LDC_TEMP]?.value, 0) + " °C",
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.solar_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
