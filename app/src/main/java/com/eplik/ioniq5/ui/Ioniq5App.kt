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
import androidx.compose.runtime.LaunchedEffect
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
import com.eplik.ioniq5.ui.screens.SettingsScreen
import com.eplik.ioniq5.ui.screens.HistoryScreen
import com.eplik.ioniq5.service.MonitorService
import com.eplik.ioniq5.connection.ConnectionStatus
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.eplik.ioniq5.ui.screens.SolarScreen
import com.eplik.ioniq5.core.poll.cellVoltages
import com.eplik.ioniq5.settings.capacityKwh
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
    val app = LocalContext.current.applicationContext as Ioniq5Application
    val connection = app.connection
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val status by connection.status.collectAsStateWithLifecycle()
    val readings by connection.readings.collectAsStateWithLifecycle()
    val chargeSession by connection.chargeSession.collectAsStateWithLifecycle()
    val solarEnergyWh by connection.solarEnergyWh.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    val sleeping by connection.sleeping.collectAsStateWithLifecycle()
    // Käivitamisel ühendu viimati kasutatud adapteriga
    LaunchedEffect(Unit) { if (settings.autoConnect) connection.connectToLast() }
    val capacityKwh = settings.capacityKwh(readings.cellVoltages().size)

    // Teavituse luba (Android 13+) taustateenuse teavituse jaoks
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Ühenduse ajal hoiab taustateenus rakenduse elus (lugemine ja salvestus ka kustunud ekraaniga)
    LaunchedEffect(status) {
        if (status is ConnectionStatus.Connected) runCatching { MonitorService.start(context) }
    }
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
                            showSettings = false
                            showHistory = false
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
            showHistory -> HistoryScreen(app.recordings, onBack = { showHistory = false }, modifier = modifier)
            showSettings -> SettingsScreen(
                settings,
                detectedCapacityKwh = settings.copy(batteryCapacityKwh = null).capacityKwh(readings.cellVoltages().size),
                onChange = { app.settings.update(it) },
                onBack = { showSettings = false },
                modifier = modifier,
            )
            selected == Tab.OVERVIEW -> OverviewScreen(
                status,
                readings,
                sleeping = sleeping,
                onWake = { connection.wakePolling() },
                onConnectionClick = { showConnection = true },
                onSettingsClick = { showSettings = true },
                onHistoryClick = { showHistory = true },
                modifier = modifier,
            )
            selected == Tab.BATTERY -> BatteryScreen(readings, modifier)
            selected == Tab.AXLES -> AxlesScreen(readings, settings.pressureUnit, settings.lowPressureBar, modifier)
            selected == Tab.CHARGING -> ChargingScreen(readings, chargeSession, capacityKwh, modifier)
            selected == Tab.SOLAR -> SolarScreen(readings, solarEnergyWh, modifier)
            selected == Tab.DIAGNOSTICS -> DiagnosticsScreen(onConnectionClick = { showConnection = true }, modifier = modifier)
            else -> PlaceholderScreen(stringResource(selected.label), selected.stage, modifier)
        }
    }
}
