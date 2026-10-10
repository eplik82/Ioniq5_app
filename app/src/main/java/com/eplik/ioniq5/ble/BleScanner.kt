package com.eplik.ioniq5.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundDevice(val name: String?, val address: String, val device: BluetoothDevice) {
    /** LELink kuulutab end nimega "OBDBLE". */
    val isLeLink: Boolean get() = name?.contains("OBDBLE", ignoreCase = true) == true
}

/** BLE-seadmete otsing. Load (BLUETOOTH_SCAN / asukoht) peab UI enne küsima. */
@SuppressLint("MissingPermission")
fun scanBleDevices(context: Context): Flow<FoundDevice> = callbackFlow {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val scanner = adapter?.bluetoothLeScanner
    if (adapter == null || !adapter.isEnabled || scanner == null) {
        close(IllegalStateException("Bluetooth on välja lülitatud"))
        return@callbackFlow
    }
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            trySend(FoundDevice(result.scanRecord?.deviceName ?: device.name, device.address, device))
        }

        override fun onScanFailed(errorCode: Int) {
            close(IllegalStateException("BLE otsing ebaõnnestus ($errorCode)"))
        }
    }
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    scanner.startScan(null, settings, callback)
    awaitClose { scanner.stopScan(callback) }
}
