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
 *
 * Pide fixes cada 2 s y filtra: solo publica una posición si es más precisa
 * que la actual o si la actual ya envejeció. Así la posición que viaja por el
 * mesh es siempre el mejor fix disponible, no el último ruido.
 */
@SuppressLint("MissingPermission")
class LocationEngine(
    context: Context,
    private val onLocation: (MyLocation) -> Unit,
) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false
    private var best: Location? = null

    private fun accept(candidate: Location): Boolean {
        val current = best ?: return true
        val currentAgeMs = System.currentTimeMillis() - current.time
        if (currentAgeMs > 10_000) return true          // el fix actual ya es viejo
        return candidate.accuracy <= current.accuracy + 5f
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!accept(location)) return
            best = location
            onLocation(
                MyLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    speedMps = if (location.hasSpeed()) location.speed else 0f,
                    bearingDeg = if (location.hasBearing()) location.bearing else -1f,
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
                LocationManager.GPS_PROVIDER, 2_000L, 1f, listener,
            )
            // En interiores el GPS falla; la red (celdas/wifi cacheado, sin datos)
            // a veces da un fix aproximado.
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 15_000L, 10f, listener,
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
