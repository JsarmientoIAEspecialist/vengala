package com.vengala.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vengala.app.data.MeshRepository
import com.vengala.app.data.TrackTarget
import com.vengala.app.mesh.MeshService
import com.vengala.app.ui.theme.NeonCyan
import com.vengala.app.ui.theme.NeonLime
import com.vengala.app.ui.theme.NeonMagenta
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Mapa del recinto (OpenStreetMap). Con internet en casa, "Descargar zona"
 * guarda los mosaicos; en la fiesta todo se dibuja desde el caché local.
 * Mantén presionado el mapa para marcar el punto de encuentro del parche.
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val peers by MeshRepository.peers.collectAsState()
    val myLocation by MeshRepository.myLocation.collectAsState()
    val meet by MeshRepository.meetPoint.collectAsState()
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var centered by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("MAPA", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Text(
                    if (meet != null) "⚑ punto de ${meet!!.byName}" else "sin punto de encuentro",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (meet != null) NeonLime
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Mantén presionado el mapa para marcar el punto de encuentro (le llega a todo el parche).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    osmdroidBasePath = File(ctx.filesDir, "osmdroid")
                    osmdroidTileCache = File(ctx.filesDir, "osmdroid/tiles")
                }
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(17.0)
                    overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                            override fun longPressHelper(p: GeoPoint): Boolean {
                                MeshService.instance?.sendMeetPoint(p.latitude, p.longitude)
                                Toast.makeText(
                                    ctx, "⚑ Punto de encuentro marcado para el parche",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return true
                            }
                        }),
                    )
                    mapRef = this
                }
            },
            update = { map ->
                // Redibuja marcadores (los MapEventsOverlay se conservan)
                map.overlays.removeAll { it is Marker }
                myLocation?.let { me ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(me.latitude, me.longitude)
                            title = "Tú"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        },
                    )
                    if (!centered) {
                        map.controller.setCenter(GeoPoint(me.latitude, me.longitude))
                        centered = true
                    }
                }
                for (peer in peers.values) {
                    val loc = peer.location ?: continue
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(loc.latitude, loc.longitude)
                            title = peer.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        },
                    )
                }
                meet?.let { mp ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(mp.latitude, mp.longitude)
                            title = "⚑ Punto de encuentro (${mp.byName})"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        },
                    )
                }
                map.invalidate()
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val map = mapRef
                    val me = myLocation
                    if (map == null || me == null) {
                        Toast.makeText(context, "Espera la señal GPS para saber qué zona bajar", Toast.LENGTH_SHORT).show()
                    } else {
                        // ~±2 km alrededor tuyo, zoom 13-18 (~15 MB). Necesita internet.
                        val bb = BoundingBox(
                            me.latitude + 0.02, me.longitude + 0.02,
                            me.latitude - 0.02, me.longitude - 0.02,
                        )
                        try {
                            CacheManager(map).downloadAreaAsync(context, bb, 13, 18)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo descargar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Descargar esta zona", style = MaterialTheme.typography.labelSmall)
            }
            if (meet != null) {
                Button(
                    onClick = { MeshRepository.setTrackedTarget(TrackTarget.MeetTarget) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                ) {
                    Text("Ir al punto ⚑", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        MeshService.instance?.sendMeetPoint(0.0, 0.0, remove = true)
                        Toast.makeText(context, "Punto de encuentro quitado", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("✕", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.padding(2.dp))
    }
}
