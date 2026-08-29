package com.vengala.app.data

data class ChatMessage(
    val messageId: Long,
    val senderId: Long,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
)

data class PeerLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
)

data class Peer(
    val id: Long,
    val name: String,
    val lastSeen: Long,
    val location: PeerLocation? = null,
    val batteryPercent: Int? = null,
    /** true si estamos conectados por BLE directamente; false si nos llega vía otros saltos. */
    val directLink: Boolean = false,
)

data class MyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

data class MeshStats(
    val running: Boolean = false,
    val directPeers: Int = 0,
    val packetsRelayed: Long = 0,
    val packetsSeen: Long = 0,
)
