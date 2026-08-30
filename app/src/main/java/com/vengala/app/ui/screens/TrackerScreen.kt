package com.vengala.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vengala.app.data.MeshRepository
import com.vengala.app.location.CompassEngine
import com.vengala.app.location.Geo
import com.vengala.app.ui.theme.NeonCyan
import com.vengala.app.ui.theme.NeonLime
import com.vengala.app.ui.theme.NeonMagenta
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs
import kotlin.math.min

/**
 * Modo búsqueda: flecha gigante que apunta hacia la persona seleccionada.
 * Verde cuando apuntas bien: camina en esa dirección y mira la distancia bajar.
 */
@Composable
fun TrackerScreen(peerId: Long) {
    val peers by MeshRepository.peers.collectAsState()
    val myLocation by MeshRepository.myLocation.collectAsState()
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        val compass = CompassEngine(context) { azimuth = it }
        compass.start()
        onDispose { compass.stop() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val peer = peers[peerId]
    val loc = peer?.location
    val me = myLocation

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "BUSCANDO A ${(peer?.name ?: "???").uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
            )
            IconButton(onClick = { MeshRepository.setTrackedPeer(null) }) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            peer == null -> Message("Se perdió la señal de esta persona en el mesh.")
            loc == null -> Message("${peer.name} todavía no comparte ubicación (¿tiene GPS y el interruptor activado?).")
            me == null -> Message("Esperando tu señal GPS... sal a cielo abierto.")
            else -> {
                val dist = Geo.distanceMeters(me.latitude, me.longitude, loc.latitude, loc.longitude)
                val bearing = Geo.bearingDegrees(me.latitude, me.longitude, loc.latitude, loc.longitude)
                var diff = (bearing - azimuth).toFloat() % 360f
                if (diff > 180f) diff -= 360f
                if (diff < -180f) diff += 360f
                val aligned = abs(diff) < 20f
                val arrived = dist < 15.0
                val color = when {
                    arrived -> NeonLime
                    aligned -> NeonLime
                    else -> NeonMagenta
                }

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(24.dp),
                ) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = min(size.width, size.height) / 2f
                    drawCircle(NeonCyan.copy(alpha = 0.12f), r - 8f, c, style = Stroke(3f))
                    if (arrived) {
                        // Estás encima: pulso en vez de flecha
                        drawCircle(NeonLime.copy(alpha = 0.25f), r * 0.55f, c)
                        drawCircle(NeonLime, r * 0.18f, c)
                    } else {
                        rotate(degrees = diff, pivot = c) {
                            val h = r * 0.78f
                            val w = r * 0.52f
                            val arrow = Path().apply {
                                moveTo(c.x, c.y - h)               // punta
                                lineTo(c.x + w / 2f, c.y + h * 0.45f)
                                lineTo(c.x, c.y + h * 0.18f)       // muesca
                                lineTo(c.x - w / 2f, c.y + h * 0.45f)
                                close()
                            }
                            drawPath(arrow, color)
                            drawPath(
                                arrow, color.copy(alpha = 0.35f),
                                style = Stroke(width = 22f, join = StrokeJoin.Round),
                            )
                        }
                    }
                }

                Text(
                    if (arrived) "¡AQUÍ!" else Geo.formatDistance(dist),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 56.sp),
                    color = color,
                )
                val staleness = (nowTick - loc.timestamp) / 1000
                val uncertainty = (me.accuracyMeters + loc.accuracyMeters).toInt()
                Text(
                    "±$uncertainty m · posición de hace ${staleness}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    when {
                        arrived -> "Ya deberían estarse viendo. ¡Grita!"
                        aligned -> "Flecha verde: camina de frente"
                        else -> "Gira hasta que la flecha apunte hacia arriba y se ponga verde"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(32.dp),
    )
}
