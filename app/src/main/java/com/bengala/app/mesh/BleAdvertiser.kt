package com.bengala.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.util.Log
import com.bengala.app.data.MeshRepository

/**
 * Anuncia el servicio Bengala por BLE para que otros teléfonos nos descubran.
 * LOW_LATENCY: en una fiesta importa más encontrarse rápido que ahorrar mAh.
 */
@SuppressLint("MissingPermission")
class BleAdvertiser(private val adapter: BluetoothAdapter) {

    @Volatile
    var running = false
        private set

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            running = true
            MeshRepository.updateStats { it.copy(advertiseState = "ok") }
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w("Bengala", "Advertise falló: $errorCode")
            running = false
            val reason = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "datos muy grandes"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "demasiados anunciantes"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ya iniciado"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "no soportado por este teléfono"
                else -> "error $errorCode"
            }
            MeshRepository.updateStats { it.copy(advertiseState = reason) }
        }
    }

    fun start() {
        if (running) return
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            MeshRepository.updateStats {
                it.copy(advertiseState = "no disponible (¿Bluetooth apagado?)")
            }
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()
        try {
            advertiser.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            Log.w("Bengala", "No se pudo iniciar advertising", e)
            MeshRepository.updateStats { it.copy(advertiseState = "excepción: ${e.message}") }
        }
    }

    fun stop() {
        try {
            adapter.bluetoothLeAdvertiser?.stopAdvertising(callback)
        } catch (_: Exception) {
        }
        running = false
    }
}
