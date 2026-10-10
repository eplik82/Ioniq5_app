package com.eplik.ioniq5.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eplik.ioniq5.R
import com.eplik.ioniq5.ble.FoundDevice
import com.eplik.ioniq5.ble.scanBleDevices
import com.eplik.ioniq5.connection.ConnectionManager
import com.eplik.ioniq5.connection.ConnectionStatus
import kotlinx.coroutines.withTimeoutOrNull

private val BLE_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private const val SCAN_DURATION_MS = 15_000L

@Composable
fun ConnectionScreen(connection: ConnectionManager, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val status by connection.status.collectAsStateWithLifecycle()
    val devices = remember { mutableStateListOf<FoundDevice>() }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun hasPermissions() = BLE_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) scanning = true else error = context.getString(R.string.permission_denied)
    }

    fun startScan() {
        error = null
        if (hasPermissions()) scanning = true else permissionLauncher.launch(BLE_PERMISSIONS)
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        devices.clear()
        try {
            withTimeoutOrNull(SCAN_DURATION_MS) {
                scanBleDevices(context).collect { found ->
                    if (devices.none { it.address == found.address }) {
                        devices.add(found)
                        // LELink ettepoole, siis nimega seadmed
                        devices.sortWith(compareByDescending<FoundDevice> { it.isLeLink }.thenBy { it.name == null })
                    }
                }
            }
        } catch (e: IllegalStateException) {
            error = e.message
        } catch (e: SecurityException) {
            error = context.getString(R.string.permission_denied)
        }
        scanning = false
    }

    // Esimesel avamisel otsime kohe
    LaunchedEffect(Unit) { if (status !is ConnectionStatus.Connected) startScan() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.displayMedium)
        Text(statusText(status), style = MaterialTheme.typography.titleMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startScan() }, enabled = !scanning) { Text(stringResource(R.string.scan)) }
            if (status is ConnectionStatus.Connected || status is ConnectionStatus.Connecting || status is ConnectionStatus.Reconnecting) {
                OutlinedButton(onClick = { connection.disconnect() }) { Text(stringResource(R.string.disconnect)) }
            }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(Modifier.fillMaxWidth()) {
            items(devices, key = { it.address }) { found ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            scanning = false
                            connection.connect(found)
                            onBack()
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        found.name ?: stringResource(R.string.unknown_device),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (found.isLeLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    )
                    Text(found.address, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun statusText(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.Disconnected -> stringResource(R.string.connection_disconnected)
    is ConnectionStatus.Connecting -> stringResource(R.string.connection_connecting, status.deviceName)
    is ConnectionStatus.Connected -> stringResource(R.string.connection_connected, status.deviceName, status.adapterVersion)
    is ConnectionStatus.Failed -> stringResource(R.string.connection_failed, status.message)
    is ConnectionStatus.Reconnecting -> stringResource(R.string.connection_reconnecting, status.deviceName, status.inSeconds)
}
