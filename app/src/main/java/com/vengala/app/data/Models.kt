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
    /** m/s reportados por su GPS (0 si quieto o desconocido). */
    val speedMps: Float = 0f,
    /** Rumbo de movimiento en grados (< 0 = desconocido). */
    val bearingDeg: Float = -1f,
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

/** Intensidad de señal BLE suavizada hacia un peer (para el caliente/frío). */
data class RssiSample(
    val rssi: Float,
    val timestamp: Long,
)

data class MyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float = 0f,
    val bearingDeg: Float = -1f,
)

/** Punto de encuentro compartido con todo el parche por el mesh. */
data class MeetPoint(
    val latitude: Double,
    val longitude: Double,
    val byName: String,
    val timestamp: Long,
)

/** Qué está siguiendo la flecha: una persona o el punto de encuentro. */
sealed interface TrackTarget {
    data class PeerTarget(val id: Long) : TrackTarget
    data object MeetTarget : TrackTarget
}

data class MeshStats(
    val running: Boolean = false,
    val directPeers: Int = 0,
    val packetsRelayed: Long = 0,
    val packetsSeen: Long = 0,
    // Diagnóstico del descubrimiento BLE
    val bluetoothOn: Boolean = false,
    val locationServiceOn: Boolean = true,
    val advertiseState: String = "iniciando...",
    val scanState: String = "iniciando...",
    val devicesFound: Int = 0,
)
