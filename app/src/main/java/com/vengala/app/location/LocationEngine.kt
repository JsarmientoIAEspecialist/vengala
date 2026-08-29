package com.vengala.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.vengala.app.data.MyLocation

/**
 * GPS puro con LocationManager: funciona sin internet y sin Google Play Services.
 * (El GPS es una radio receptora de satélites; no necesita señal de celular.)
 */
@SuppressLint("MissingPermission")
class LocationEngine(
    context: Context,
    private val onLocation: (MyLocation) -> Unit,
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(
                MyLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                ),
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun start() {
        if (running) return
        try {
            // Último fix conocido como arranque rápido.
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                listener.onLocationChanged(it)
            }
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 10_000L, 5f, listener,
            )
            // En interiores el GPS falla; la red (sin datos, solo celdas/wifi cacheado)
            // a veces da un fix aproximado.
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 30_000L, 20f, listener,
                )
            }
            running = true
        } catch (e: Exception) {
            Log.w("Vengala", "No se pudo iniciar GPS", e)
        }
    }

    fun stop() {
        if (!running) return
        try {
            manager.removeUpdates(listener)
        } catch (_: Exception) {
        }
        running = false
    }
}
