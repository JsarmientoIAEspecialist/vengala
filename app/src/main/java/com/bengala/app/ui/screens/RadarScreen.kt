package com.bengala.app.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bengala.app.data.MeshRepository
import com.bengala.app.location.CompassEngine
import com.bengala.app.location.Geo
import com.bengala.app.location.estimateNow
import com.bengala.app.ui.theme.NeonCyan
import com.bengala.app.ui.theme.NeonLime
import com.bengala.app.ui.theme.NeonMagenta
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar de amigos: cada persona con ubicación aparece como un punto según
 * su dirección real (relativa a donde apuntas el teléfono) y su distancia.
 */
@Composable
fun RadarScreen() {
    val peers by MeshRepository.peers.collectAsState()
    val myLocation by MeshRepository.myLocation.collectAsState()
    val meet by MeshRepository.meetPoint.collectAsState()
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }

    // Barrido giratorio estilo radar (puramente estético, pero se siente vivo)
    val sweep by androidx.compose.animation.core.rememberInfiniteTransition(label = "sweep")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(4000,
                    easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "sweepAngle",
        )

    DisposableEffect(Unit) {
        val compass = CompassEngine(context) { azimuth = it }
        compass.start()
        onDispose { compass.stop() }
    }

    val located = peers.values.filter { it.location != null }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "RADAR",
            style = MaterialTheme.typography.titleMedium,
            color = NeonCyan,
        )
        Text(
            when {
                myLocation == null -> "Esperando señal GPS... (sal a cielo abierto)"
                located.isEmpty() -> "Sin amigos con ubicación todavía"
                else -> "Apunta el teléfono y camina hacia el punto"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f - 24f

            // Anillos: 25 m, 100 m, 400 m (escala logarítmica)
            val rings = listOf(0.33f, 0.66f, 1f)
            for (r in rings) {
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.15f),
                    radius = radius * r,
                    center = center,
                    style = Stroke(width = 2f),
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(NeonCyan.copy(alpha = 0.10f), Color.Transparent),
                    center = center, radius = radius,
                ),
                radius = radius, center = center,
            )

            // Barrido giratorio
            val sweepAngle = Math.toRadians(sweep.toDouble() - 90.0)
            drawLine(
                color = NeonCyan.copy(alpha = 0.35f),
                start = center,
                end = Offset(
                    center.x + radius * cos(sweepAngle).toFloat(),
                    center.y + radius * sin(sweepAngle).toFloat(),
                ),
                strokeWidth = 3f,
            )

            // Marcador de norte en el borde
            val northAngle = Math.toRadians((-azimuth).toDouble() - 90.0)
            val northPos = Offset(
                center.x + radius * cos(northAngle).toFloat(),
                center.y + radius * sin(northAngle).toFloat(),
            )
            drawCircle(NeonLime.copy(alpha = 0.9f), 8f, northPos)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(230, 182, 255, 0)
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText("N", northPos.x, northPos.y - 16f, paint)
            }

            // Yo, en el centro
            drawCircle(NeonMagenta, 14f, center)
            drawCircle(NeonMagenta.copy(alpha = 0.3f), 28f, center)

            val me = myLocation
            if (me != null) {
                for (peer in located) {
                    val loc = peer.location ?: continue
                    val (pLat, pLon) = loc.estimateNow()
                    val dist = Geo.distanceMeters(
                        me.latitude, me.longitude, pLat, pLon,
                    )
                    val bearing = Geo.bearingDegrees(
                        me.latitude, me.longitude, pLat, pLon,
                    )
                    // Escala log: 10m→0.2R, 25m→0.33R, 100m→0.66R, 400m+→borde
                    val norm = (ln((dist / 10.0).coerceAtLeast(1.0)) /
                        ln(40.0)).coerceIn(0.08, 1.0).toFloat()
                    val angle = Math.toRadians(bearing - azimuth - 90.0)
                    val pos = Offset(
                        center.x + radius * norm * cos(angle).toFloat(),
                        center.y + radius * norm * sin(angle).toFloat(),
                    )
                    drawCircle(NeonCyan, 12f, pos)
                    drawCircle(NeonCyan.copy(alpha = 0.25f), 22f, pos)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 30f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawText(peer.name.take(10), pos.x, pos.y - 34f, paint)
                        paint.color = android.graphics.Color.argb(255, 0, 229, 255)
                        paint.textSize = 26f
                        drawText(Geo.formatDistance(dist), pos.x, pos.y + 48f, paint)
                    }
                }

                // Punto de encuentro: bandera lima
                meet?.let { mp ->
                    val dist = Geo.distanceMeters(me.latitude, me.longitude, mp.latitude, mp.longitude)
                    val bearing = Geo.bearingDegrees(me.latitude, me.longitude, mp.latitude, mp.longitude)
                    val norm = (ln((dist / 10.0).coerceAtLeast(1.0)) /
                        ln(40.0)).coerceIn(0.08, 1.0).toFloat()
                    val angle = Math.toRadians(bearing - azimuth - 90.0)
                    val pos = Offset(
                        center.x + radius * norm * cos(angle).toFloat(),
                        center.y + radius * norm * sin(angle).toFloat(),
                    )
                    drawCircle(NeonLime, 10f, pos)
                    drawCircle(NeonLime.copy(alpha = 0.25f), 20f, pos)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(255, 182, 255, 0)
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        drawText("⚑ ${Geo.formatDistance(dist)}", pos.x, pos.y - 26f, paint)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LegendDot(NeonMagenta, "Tú")
            LegendDot(NeonCyan, "Tu parche")
            LegendDot(NeonLime, "Norte")
        }
        myLocation?.let {
            Text(
                "GPS ±${it.accuracyMeters.toInt()} m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
