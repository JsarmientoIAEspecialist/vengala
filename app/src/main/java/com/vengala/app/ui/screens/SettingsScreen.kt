package com.vengala.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vengala.app.data.Settings
import com.vengala.app.mesh.MeshService
import com.vengala.app.share.ApkSharer
import com.vengala.app.ui.theme.NeonCyan
import com.vengala.app.ui.theme.NeonMagenta

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    var name by remember { mutableStateOf(settings.displayName) }
    var partyCode by remember { mutableStateOf(settings.partyCode) }
    var shareLocation by remember { mutableStateOf(settings.shareLocation) }

    // Aplica nombre/código al mesh solo cuando el usuario deja de escribir
    // (recalcular la clave de cifrado por cada tecla congelaba la app).
    LaunchedEffect(Unit) {
        snapshotFlow { name to partyCode }
            .drop(1)
            .collectLatest {
                delay(600)
                MeshService.instance?.onSettingsChanged()
            }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("AJUSTES", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                settings.displayName = it
            },
            label = { Text("Tu nombre en la fiesta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = partyCode,
            onValueChange = {
                partyCode = it
                settings.partyCode = it
            },
            label = { Text("Código de fiesta") },
            supportingText = {
                Text(
                    "Todos los de tu parche deben escribir EXACTAMENTE el mismo código. " +
                        "Es la llave del cifrado: quien no lo tenga no puede leer nada.",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Compartir mi ubicación", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Tu posición GPS viaja cifrada solo a quienes tienen tu código",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = shareLocation,
                onCheckedChange = {
                    shareLocation = it
                    settings.shareLocation = it
                },
                colors = SwitchDefaults.colors(checkedTrackColor = NeonMagenta),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("PASA LA VOZ", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(8.dp))
        Text(
            "¿Tu parche no tiene la app? Mándasela por Bluetooth aquí mismo, " +
                "sin internet ni Play Store. Al recibirla deben permitir " +
                "\"instalar apps desconocidas\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { ApkSharer.shareApk(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
        ) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Compartir la app por Bluetooth")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Cómo funciona: tu teléfono forma una red mesh por Bluetooth con los " +
                "teléfonos cercanos. Los mensajes y ubicaciones saltan de teléfono en " +
                "teléfono (hasta 7 saltos) y se guardan un rato para entregarse a quien " +
                "estaba lejos. Nada sale a internet; todo va cifrado con tu código de fiesta.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ID de nodo: ${settings.nodeIdHex}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
