package com.vengala.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vengala.app.data.ChatMessage
import com.vengala.app.data.MeshRepository
import com.vengala.app.mesh.MeshService
import com.vengala.app.ui.theme.NeonCyan
import com.vengala.app.ui.theme.NeonMagenta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen() {
    val messages by MeshRepository.messages.collectAsState()
    val stats by MeshRepository.stats.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Barra de estado del mesh
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "VENGALA",
                style = MaterialTheme.typography.titleMedium,
                color = NeonMagenta,
            )
            Text(
                if (stats.directPeers > 0) "● ${stats.directPeers} enlaces" else "○ buscando...",
                style = MaterialTheme.typography.labelSmall,
                color = if (stats.directPeers > 0) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Nadie ha dicho nada todavía.\nTodo lo que escribas salta de " +
                            "teléfono en teléfono por Bluetooth hasta llegar a tu parche.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(messages, key = { it.messageId }) { msg -> MessageBubble(msg) }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje al parche...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonMagenta,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                maxLines = 3,
            )
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        MeshService.instance?.sendChat(text)
                        input = ""
                    }
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = NeonMagenta,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val time = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (msg.isMine) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (msg.isMine) 16.dp else 4.dp,
                        bottomEnd = if (msg.isMine) 4.dp else 16.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!msg.isMine) {
                Text(
                    msg.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                )
            }
            Text(msg.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
            )
        }
    }
}
