package com.vengala.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.vengala.app.location.estimateNow
import com.vengala.app.ui.theme.NeonCyan
import com.vengala.app.ui.theme.NeonLime
import com.vengala.app.ui.theme.NeonMagenta
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Modo búsqueda en dos etapas:
 *  - Lejos: flecha con brújula guiada por GPS.
 *  - Cerca (<~20 m): caliente/frío por intensidad de señal Bluetooth directa,
 *    que a corta distancia es mucho más precisa que el GPS. Vibra al acercarte.
 * También guía hacia el punto de encuentro (sin etapa Bluetooth: es un lugar).
 */
@Composable
fun TrackerScreen(target: com.vengala.app.data.TrackTarget) {
    val peers by MeshRepository.peers.collectAsState()
    val myLocation by MeshRepository.myLocation.collectAsState()
    val rssiMap by MeshRepository.peerRssi.collectAsState()
    val meet by MeshRepository.meetPoint.collectAsState()
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

    val isMeet = target is com.vengala.app.data.TrackTarget.MeetTarget
    val peerId = (target as? com.vengala.app.data.TrackTarget.PeerTarget)?.id
    val peer = peerId?.let { peers[it] }
    val targetName = if (isMeet) "EL PUNTO ⚑" else (peer?.name ?: "???").uppercase()
    val loc = if (isMeet) {
        meet?.let {
            com.vengala.app.data.PeerLocation(it.latitude, it.longitude, 5f, it.timestamp)
        }
    } else peer?.location
    val me = myLocation
    val lost = if (isMeet) meet == null else peer == null

    // Señal Bluetooth fresca (< 10 s) hacia esta persona (no aplica al punto)
    val sample = peerId?.let { id ->
        rssiMap[id]?.takeIf { nowTick - it.timestamp < 10_000 }
    }
    // Distancia por RSSI con potencia autocalibrada por GPS para ESTE peer
    val txPower = peerId?.let { MeshRepository.txPowerFor(it).toDouble() } ?: -59.0
    val rssiMeters = sample?.let { 10.0.pow((txPower - it.rssi) / (10.0 * 2.2)) }
    val proximityTier = sample?.let {
        when {
            it.rssi >= -55f -> 0   // MUY CERCA  (~<3 m)
            it.rssi >= -67f -> 1   // CERCA      (~3-8 m)
            it.rssi >= -80f -> 2   // TIBIO      (~8-20 m)
            else -> 3              // FRÍO       (>20 m)
        }
    }

    // Vibra al pasar a un nivel más cercano (y doble al llegar)
    var lastTier by remember { mutableIntStateOf(99) }
    LaunchedEffect(proximityTier) {
        val tier = proximityTier ?: return@LaunchedEffect
        if (tier < lastTier) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    val effect = if (tier == 0) {
                        VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)
                    } else {
                        VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    vibrator.vibrate(effect)
                }
            } catch (_: Exception) {
            }
        }
        lastTier = tier
    }

    // Posición extrapolada: si venía caminando, proyectamos su rumbo/velocidad
    val peerPos = loc?.estimateNow(nowTick)
    val gpsDist = if (me != null && peerPos != null) {
        Geo.distanceMeters(me.latitude, me.longitude, peerPos.first, peerPos.second)
    } else null

    // Modo cercano: la señal BT manda cuando existe y ya estamos a tiro
    val nearMode = sample != null && (proximityTier!! <= 2 || gpsDist == null || gpsDist < 25.0)
    val arrived = (proximityTier == 0) || (gpsDist != null && gpsDist < 10.0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "BUSCANDO $targetName",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
            )
            IconButton(onClick = { MeshRepository.setTrackedTarget(null) }) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            lost -> Message(
                if (isMeet) "El punto de encuentro fue quitado."
                else "Se perdió la señal de esta persona en el mesh.",
            )
            loc == null && sample == null ->
                Message("Sin ubicación GPS ni señal Bluetooth directa todavía. Acércate o espera unos segundos.")
            else -> {
                // ----- Flecha (GPS) -----
                if (me != null && peerPos != null && gpsDist != null && !arrived) {
                    val bearing = Geo.bearingDegrees(me.latitude, me.longitude, peerPos.first, peerPos.second)
                    var diff = (bearing - azimuth).toFloat() % 360f
                    if (diff > 180f) diff -= 360f
                    if (diff < -180f) diff += 360f
                    val aligned = abs(diff) < 20f
                    val color = if (aligned) NeonLime else NeonMagenta

                    Canvas(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f)
                            .padding(12.dp),
                    ) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) / 2f
                        drawCircle(NeonCyan.copy(alpha = 0.12f), r - 8f, c, style = Stroke(3f))
                        rotate(degrees = diff, pivot = c) {
                            val h = r * 0.78f
                            val w = r * 0.52f
                            val arrow = Path().apply {
                                moveTo(c.x, c.y - h)
                                lineTo(c.x + w / 2f, c.y + h * 0.45f)
                                lineTo(c.x, c.y + h * 0.18f)
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
                } else if (arrived) {
                    Canvas(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .aspectRatio(1f)
                            .padding(12.dp),
                    ) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) / 2f
                        drawCircle(NeonLime.copy(alpha = 0.25f), r * 0.8f, c)
                        drawCircle(NeonLime, r * 0.3f, c)
                    }
                }

                // ----- Distancia principal -----
                val mainText = when {
                    arrived -> "¡AQUÍ!"
                    nearMode && rssiMeters != null -> "~${rssiMeters.toInt().coerceAtLeast(1)} m"
                    gpsDist != null -> Geo.formatDistance(gpsDist)
                    else -> "..."
                }
                Text(
                    mainText,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 54.sp),
                    color = if (arrived) NeonLime else if (nearMode) NeonCyan else NeonMagenta,
                )
                Text(
                    when {
                        isMeet && meet != null -> "punto fijo marcado por ${meet!!.byName}"
                        arrived -> "señal Bluetooth al máximo · ya deberían verse"
                        nearMode -> "midiendo por señal Bluetooth directa (más precisa que el GPS aquí)"
                        me != null && loc != null ->
                            "GPS ±${(me.accuracyMeters + loc.accuracyMeters).toInt()} m · posición de hace ${(nowTick - loc.timestamp) / 1000}s"
                        else -> "sin GPS: guiándote solo por señal Bluetooth"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )

                // ----- Termómetro caliente/frío -----
                if (sample != null && proximityTier != null) {
                    val fraction = ((sample.rssi + 95f) / 50f).coerceIn(0.05f, 1f)
                    val tierLabel = listOf("MUY CERCA", "CERCA", "TIBIO", "FRÍO")[proximityTier]
                    val tierColor = listOf(NeonLime, NeonLime, NeonCyan, NeonMagenta)[proximityTier]
                    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("SEÑAL BLUETOOTH", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tierLabel, style = MaterialTheme.typography.labelSmall,
                                color = tierColor)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .padding(top = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(7.dp)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(tierColor, RoundedCornerShape(7.dp)),
                            )
                        }
                    }
                } else if (nearMode || gpsDist == null) {
                    Text(
                        "Sin señal Bluetooth directa: aún están a más de ~30 m o hay mucha gente en medio.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                Text(
                    when {
                        arrived && isMeet -> "Llegaste al punto de encuentro"
                        arrived -> "¡Grita o alza la mano!"
                        nearMode -> "Camina despacio: la barra sube y el teléfono vibra al acercarte"
                        isMeet -> "Sigue la flecha hasta el punto que marcó ${meet?.byName ?: "el parche"}"
                        else -> "Sigue la flecha; al acercarte cambia a caliente/frío por Bluetooth"
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
