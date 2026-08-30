package com.vengala.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vengala.app.data.MeshRepository
import com.vengala.app.ui.screens.ChatScreen
import com.vengala.app.ui.screens.PeersScreen
import com.vengala.app.ui.screens.RadarScreen
import com.vengala.app.ui.screens.SettingsScreen
import com.vengala.app.ui.screens.TrackerScreen

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun MainScreen() {
    var selected by remember { mutableIntStateOf(0) }
    val peers by MeshRepository.peers.collectAsState()
    val trackedPeer by MeshRepository.trackedPeer.collectAsState()

    val tabs = listOf(
        Tab("Chat", Icons.Filled.Chat),
        Tab("Radar", Icons.Filled.Radar),
        Tab("Gente", Icons.Filled.Groups),
        Tab("Ajustes", Icons.Filled.Settings),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = {
                            selected = index
                            MeshRepository.setTrackedPeer(null)
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            if (index == 2 && peers.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${peers.size}") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tracked = trackedPeer
            if (tracked != null) {
                TrackerScreen(peerId = tracked)
            } else {
                when (selected) {
                    0 -> ChatScreen()
                    1 -> RadarScreen()
                    2 -> PeersScreen()
                    3 -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun PermissionsScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("VENGALA", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
        Text(
            "Mesh Bluetooth para fiestas.\nSin internet. Sin señal. Sin problema.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Text(
            "Para funcionar necesita Bluetooth (hablar con otros teléfonos) " +
                "y ubicación (GPS para el radar; nunca sale a internet).",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(onClick = onRequest) {
            Text("Dar permisos")
        }
    }
}
