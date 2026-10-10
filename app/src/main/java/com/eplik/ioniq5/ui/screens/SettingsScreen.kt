package com.eplik.ioniq5.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.settings.PressureUnit
import com.eplik.ioniq5.settings.Settings
import com.eplik.ioniq5.settings.ThemeMode
import com.eplik.ioniq5.ui.components.formatValue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    detectedCapacityKwh: Double,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.displayMedium)

        Section(stringResource(R.string.setting_pressure_unit)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressureUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = settings.pressureUnit == unit,
                        onClick = { onChange { it.copy(pressureUnit = unit) } },
                        label = { Text(unit.label) },
                    )
                }
            }
        }

        Section(
            stringResource(R.string.setting_low_pressure) + ": " +
                formatValue(settings.lowPressureBar * settings.pressureUnit.fromBar, settings.pressureUnit.decimals) +
                " " + settings.pressureUnit.label,
        ) {
            Slider(
                value = settings.lowPressureBar.toFloat(),
                onValueChange = { v -> onChange { it.copy(lowPressureBar = (v * 20).toInt() / 20.0) } },
                valueRange = 1.6f..3.0f,
            )
        }

        Section(stringResource(R.string.setting_low_soc) + ": " + formatValue(settings.lowSocPercent, 0) + " %") {
            Slider(
                value = settings.lowSocPercent.toFloat(),
                onValueChange = { v -> onChange { it.copy(lowSocPercent = v.toInt().toDouble()) } },
                valueRange = 5f..30f,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.setting_alert_notifications), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = settings.alertNotifications, onCheckedChange = { v -> onChange { it.copy(alertNotifications = v) } })
        }

        Section(stringResource(R.string.setting_theme)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onChange { it.copy(themeMode = mode) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        Section(stringResource(R.string.setting_capacity)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.batteryCapacityKwh == null,
                    onClick = { onChange { it.copy(batteryCapacityKwh = null) } },
                    label = { Text(stringResource(R.string.capacity_auto, formatValue(detectedCapacityKwh, 1))) },
                )
                listOf(58.0, 72.6, 77.4, 84.0).forEach { kwh ->
                    FilterChip(
                        selected = settings.batteryCapacityKwh == kwh,
                        onClick = { onChange { it.copy(batteryCapacityKwh = kwh) } },
                        label = { Text(formatValue(kwh, 1) + " kWh") },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.setting_auto_connect), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = settings.autoConnect, onCheckedChange = { v -> onChange { it.copy(autoConnect = v) } })
        }

        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
