package com.vengala.app.location

import com.vengala.app.data.PeerLocation
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Utilidades geodésicas sin dependencias: distancia haversine y rumbo. */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Rumbo inicial en grados [0, 360) desde el punto 1 hacia el punto 2. */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        else -> String.format("%.1f km", meters / 1000)
    }

    /** Punto a `meters` metros del origen siguiendo un rumbo (fórmula directa). */
    fun project(lat: Double, lon: Double, bearingDeg: Double, meters: Double): Pair<Double, Double> {
        val dR = meters / EARTH_RADIUS_M
        val br = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lon)
        val phi2 = asin(sin(phi1) * cos(dR) + cos(phi1) * sin(dR) * cos(br))
        val lambda2 = lambda1 + atan2(
            sin(br) * sin(dR) * cos(phi1),
            cos(dR) - sin(phi1) * sin(phi2),
        )
        return Math.toDegrees(phi2) to Math.toDegrees(lambda2)
    }
}

/**
 * Posición estimada AHORA: si la persona venía caminando, proyecta su último
 * fix por su rumbo y velocidad en vez de mostrar dónde estaba hace N segundos.
 * Tope de 40 m y 30 s para no inventar de más.
 */
fun PeerLocation.estimateNow(nowMs: Long = System.currentTimeMillis()): Pair<Double, Double> {
    val ageSec = (nowMs - timestamp) / 1000.0
    if (speedMps < 0.5f || bearingDeg < 0f || ageSec <= 0.0 || ageSec > 30.0) {
        return latitude to longitude
    }
    val meters = (speedMps * ageSec).coerceAtMost(40.0)
    return Geo.project(latitude, longitude, bearingDeg.toDouble(), meters)
}
