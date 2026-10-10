package com.eplik.ioniq5.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eplik.ioniq5.Ioniq5Application
import com.eplik.ioniq5.ui.diagnostics.DiagnosticsScreen
import com.eplik.ioniq5.ui.screens.AxlesScreen
import com.eplik.ioniq5.ui.screens.BatteryScreen
import com.eplik.ioniq5.ui.screens.ChargingScreen
import com.eplik.ioniq5.ui.screens.ConnectionScreen
import com.eplik.ioniq5.ui.screens.SolarScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.eplik.ioniq5.R
import com.eplik.ioniq5.ui.screens.OverviewScreen
import com.eplik.ioniq5.ui.screens.PlaceholderScreen

private enum class Tab(@StringRes val label: Int, val icon: ImageVector, val stage: Int) {
    OVERVIEW(R.string.tab_overview, Icons.Filled.Speed, 2),
    BATTERY(R.string.tab_battery, Icons.Filled.BatteryChargingFull, 2),
    AXLES(R.string.tab_axles, Icons.Filled.DirectionsCar, 3),
    CHARGING(R.string.tab_charging, Icons.Filled.EvStation, 3),
    SOLAR(R.string.tab_solar, Icons.Filled.WbSunny, 3),
    DIAGNOSTICS(R.string.tab_diagnostics, Icons.Filled.Build, 1),
}

@Composable
fun Ioniq5App() {
    val connection = (LocalContext.current.applicationContext as Ioniq5Application).connection
    val status by connection.status.collectAsStateWithLifecycle()
    val readings by connection.readings.collectAsStateWithLifecycle()
    val chargeSession by connection.chargeSession.collectAsStateWithLifecycle()
    val solarEnergyWh by connection.solarEnergyWh.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = {
                            selected = tab
                            showConnection = false
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(tab.label),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            showConnection -> ConnectionScreen(connection, onBack = { showConnection = false }, modifier = modifier)
            selected == Tab.OVERVIEW -> OverviewScreen(status, readings, onConnectionClick = { showConnection = true }, modifier = modifier)
            selected == Tab.BATTERY -> BatteryScreen(readings, modifier)
            selected == Tab.AXLES -> AxlesScreen(readings, modifier)
            selected == Tab.CHARGING -> ChargingScreen(readings, chargeSession, modifier)
            selected == Tab.SOLAR -> SolarScreen(readings, solarEnergyWh, modifier)
            selected == Tab.DIAGNOSTICS -> DiagnosticsScreen(onConnectionClick = { showConnection = true }, modifier = modifier)
            else -> PlaceholderScreen(stringResource(selected.label), selected.stage, modifier)
        }
    }
}
