package com.vengala.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log

/** Escanea buscando otros nodos Vengala (filtrado por UUID del servicio). */
@SuppressLint("MissingPermission")
class BleScanner(
    private val adapter: BluetoothAdapter,
    private val onNodeFound: (BluetoothDevice, rssi: Int) -> Unit,
) {
    private var running = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onNodeFound(result.device, result.rssi)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) onNodeFound(r.device, r.rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w("Vengala", "Scan falló: $errorCode")
            running = false
        }
    }

    fun start() {
        if (running) return
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            running = true
        } catch (e: Exception) {
            Log.w("Vengala", "No se pudo iniciar scan", e)
        }
    }

    fun stop() {
        if (!running) return
        try {
            adapter.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
        running = false
    }
}
