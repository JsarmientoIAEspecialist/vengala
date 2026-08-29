package com.vengala.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.ParcelUuid
import android.util.Log

/**
 * Anuncia el servicio Vengala por BLE para que otros teléfonos nos descubran.
 * Bajo consumo: el advertising BLE gasta muy poca batería.
 */
@SuppressLint("MissingPermission")
class BleAdvertiser(private val adapter: BluetoothAdapter) {

    private var running = false

    private val callback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w("Vengala", "Advertise falló: $errorCode")
            running = false
        }
    }

    fun start() {
        if (running) return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()
        try {
            advertiser.startAdvertising(settings, data, callback)
            running = true
        } catch (e: Exception) {
            Log.w("Vengala", "No se pudo iniciar advertising", e)
        }
    }

    fun stop() {
        if (!running) return
        try {
            adapter.bluetoothLeAdvertiser?.stopAdvertising(callback)
        } catch (_: Exception) {
        }
        running = false
    }
}
