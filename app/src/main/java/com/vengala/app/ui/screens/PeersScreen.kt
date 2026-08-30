package com.vengala.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vengala.app.data.MeshRepository
import com.vengala.app.data.Peer
import com.vengala.app.location.Geo
import com.vengala.app.ui.theme.NeonCyan
import com.vengala.app.ui.theme.NeonLime

@Composable
fun PeersScreen() {
    val peers by MeshRepository.peers.collectAsState()
    val myLocation by MeshRepository.myLocation.collectAsState()
    val stats by MeshRepository.stats.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("GENTE EN EL MESH", style = MaterialTheme.typography.titleMedium,
                color = NeonCyan)
            Text(
                "${peers.size} personas · ${stats.directPeers} enlaces directos · " +
                    "${stats.packetsRelayed} paquetes retransmitidos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            DiagnosticsCard(
                bluetoothOn = stats.bluetoothOn,
                locationOn = stats.locationServiceOn,
                advertise = stats.advertiseState,
                scan = stats.scanState,
                devicesFound = stats.devicesFound,
                linksReady = stats.directPeers,
            )
        }

        if (peers.isEmpty()) {
            Text(
                "Todavía no aparece nadie.\n\nDile a tu parche que abra Vengala con el " +
                    "mismo código de fiesta. En cuanto estén a unos 10–30 metros, " +
                    "los teléfonos se encuentran solos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    peers.values.sortedByDescending { it.lastSeen },
                    key = { it.id },
                ) { peer ->
                    PeerCard(peer, myLocation?.let { me ->
                        peer.location?.let { loc ->
                            Geo.distanceMeters(me.latitude, me.longitude,
                                loc.latitude, loc.longitude)
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun PeerCard(peer: Peer, distanceMeters: Double?) {
    val secondsAgo = (System.currentTimeMillis() - peer.lastSeen) / 1000
    val fresh = secondsAgo < 120

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(
                    if (fresh) NeonLime else MaterialTheme.colorScheme.onSurfaceVariant,
                    CircleShape,
                ),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(peer.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(if (fresh) "activo" else "visto hace ${formatAgo(secondsAgo)}")
                    if (peer.directLink) append(" · enlace directo")
                    peer.batteryPercent?.let { append(" · 🔋$it%") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        distanceMeters?.let {
            Text(
                Geo.formatDistance(it),
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan,
            )
        }
    }
}

private fun formatAgo(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}

/** Estado del descubrimiento BLE, para saber en qué etapa se atora. */
@Composable
private fun DiagnosticsCard(
    bluetoothOn: Boolean,
    locationOn: Boolean,
    advertise: String,
    scan: String,
    devicesFound: Int,
    linksReady: Int,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("DIAGNÓSTICO", style = MaterialTheme.typography.labelSmall, color = NeonLime)
        DiagRow("Bluetooth", if (bluetoothOn) "activo" else "APAGADO — enciéndelo", bluetoothOn)
        DiagRow(
            "Ubicación del sistema",
            if (locationOn) "activa" else "APAGADA — actívala (el scan BLE la necesita)",
            locationOn,
        )
        DiagRow("Anunciarme por BLE", advertise, advertise == "ok")
        DiagRow("Escanear BLE", scan, scan == "ok")
        DiagRow(
            "Teléfonos Vengala detectados", "$devicesFound",
            devicesFound > 0,
        )
        DiagRow("Enlaces conectados", "$linksReady", linksReady > 0)
        if (devicesFound == 0 && advertise == "ok" && scan == "ok") {
            Text(
                "Escaneando bien pero sin detectar a nadie: verifica que el otro teléfono " +
                    "tenga Vengala ABIERTA, Bluetooth encendido y su diagnóstico en verde.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) NeonLime else MaterialTheme.colorScheme.error,
        )
    }
}
