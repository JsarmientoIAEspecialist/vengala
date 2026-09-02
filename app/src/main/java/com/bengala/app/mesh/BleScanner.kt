package com.bengala.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import com.bengala.app.data.MeshRepository

/**
 * Escanea buscando otros nodos Bengala.
 *
 * Filtramos por software (leyendo el scanRecord) en vez de usar ScanFilter:
 * el filtrado por hardware con UUIDs de 128 bits falla silenciosamente en
 * varios chipsets. LOW_LATENCY para descubrirse en segundos.
 */
@SuppressLint("MissingPermission")
class BleScanner(
    private val adapter: BluetoothAdapter,
    private val onNodeFound: (BluetoothDevice, rssi: Int) -> Unit,
) {
    @Volatile
    var running = false
        private set

    private val targetUuid = ParcelUuid(Protocol.SERVICE_UUID)

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handle(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) handle(r)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w("Bengala", "Scan falló: $errorCode")
            running = false
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "ya iniciado"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "registro falló (reinicia Bluetooth)"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "no soportado"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "sin recursos BLE"
                5 -> "demasiados reinicios de scan (espera 30 s)"
                else -> "error $errorCode"
            }
            MeshRepository.updateStats { it.copy(scanState = reason) }
        }
    }

    private fun handle(result: ScanResult) {
        val uuids = result.scanRecord?.serviceUuids ?: return
        if (targetUuid !in uuids) return
        onNodeFound(result.device, result.rssi)
    }

    fun start() {
        if (running) return
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            MeshRepository.updateStats {
                it.copy(scanState = "no disponible (¿Bluetooth apagado?)")
            }
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(null, settings, callback)
            running = true
            MeshRepository.updateStats { it.copy(scanState = "ok") }
        } catch (e: Exception) {
            Log.w("Bengala", "No se pudo iniciar scan", e)
            MeshRepository.updateStats { it.copy(scanState = "excepción: ${e.message}") }
        }
    }

    fun stop() {
        try {
            adapter.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
        running = false
    }
}
