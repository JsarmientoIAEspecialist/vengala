package com.bengala.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado observable compartido entre el servicio mesh y la UI.
 * Singleton de proceso: la UI lo colecta con StateFlow y Compose.
 */
object MeshRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _peers = MutableStateFlow<Map<Long, Peer>>(emptyMap())
    val peers: StateFlow<Map<Long, Peer>> = _peers

    private val _myLocation = MutableStateFlow<MyLocation?>(null)
    val myLocation: StateFlow<MyLocation?> = _myLocation

    private val _stats = MutableStateFlow(MeshStats())
    val stats: StateFlow<MeshStats> = _stats

    /** Objetivo que sigue la flecha: persona o punto de encuentro (null = ninguno). */
    private val _trackedTarget = MutableStateFlow<TrackTarget?>(null)
    val trackedTarget: StateFlow<TrackTarget?> = _trackedTarget

    fun setTrackedTarget(target: TrackTarget?) {
        _trackedTarget.value = target
    }

    /** Punto de encuentro vigente (gana el más reciente que llegue por el mesh). */
    private val _meetPoint = MutableStateFlow<MeetPoint?>(null)
    val meetPoint: StateFlow<MeetPoint?> = _meetPoint

    fun setMeetPoint(mp: MeetPoint?) {
        val current = _meetPoint.value
        if (mp == null || current == null || mp.timestamp >= current.timestamp) {
            _meetPoint.value = mp
        }
    }

    /** Borrado total en memoria (botón de pánico / privacidad). */
    fun wipeAll() {
        _messages.value = emptyList()
        _peers.value = emptyMap()
        _meetPoint.value = null
        _trackedTarget.value = null
        _peerRssi.value = emptyMap()
    }

    // ---------- Proximidad por RSSI ----------
    // Las direcciones BLE son aleatorias: solo cuando un paquete llega directo
    // (sin saltos) sabemos qué dirección pertenece a qué nodo.
    private val addressToNode = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val _peerRssi = MutableStateFlow<Map<Long, RssiSample>>(emptyMap())
    val peerRssi: StateFlow<Map<Long, RssiSample>> = _peerRssi

    fun mapAddress(address: String, nodeId: Long) {
        addressToNode[address] = nodeId
    }

    // Potencia de transmisión aprendida por peer (dBm a 1 m). Cada modelo de
    // teléfono emite distinto; el GPS a media distancia sirve de regla para
    // calibrar y que el "~X m" del termómetro sea consistente entre ESTOS dos
    // teléfonos concretos.
    private val txPowerByPeer = java.util.concurrent.ConcurrentHashMap<Long, Float>()

    fun txPowerFor(peerId: Long): Float = txPowerByPeer[peerId] ?: -59f

    fun calibrateTxPower(peerId: Long, gpsDistanceMeters: Double, combinedAccuracyMeters: Float) {
        val sample = _peerRssi.value[peerId] ?: return
        if (System.currentTimeMillis() - sample.timestamp > 8_000) return
        // Solo calibra donde el GPS todavía es confiable como regla:
        // ni tan cerca (error GPS domina) ni tan lejos (RSSI ya no llega).
        if (gpsDistanceMeters < 8.0 || gpsDistanceMeters > 40.0) return
        if (combinedAccuracyMeters > 25f) return
        val implied = sample.rssi +
            (10f * 2.2f * kotlin.math.log10(gpsDistanceMeters)).toFloat()
        val old = txPowerByPeer[peerId]
        txPowerByPeer[peerId] = if (old == null) implied else old * 0.85f + implied * 0.15f
    }

    /** Suaviza con media móvil exponencial: el RSSI crudo salta mucho. */
    fun reportRssi(address: String, rssi: Int) {
        val node = addressToNode[address] ?: return
        val now = System.currentTimeMillis()
        _peerRssi.update { map ->
            val old = map[node]
            val smoothed =
                if (old == null || now - old.timestamp > 15_000) rssi.toFloat()
                else old.rssi * 0.7f + rssi * 0.3f
            map + (node to RssiSample(smoothed, now))
        }
    }

    private const val MAX_MESSAGES = 500
    private const val PEER_EXPIRY_MS = 10 * 60_000L

    fun addMessage(msg: ChatMessage) {
        _messages.update { list ->
            if (list.any { it.messageId == msg.messageId }) list
            else (list + msg).takeLast(MAX_MESSAGES)
        }
    }

    fun upsertPeer(
        id: Long,
        name: String? = null,
        location: PeerLocation? = null,
        battery: Int? = null,
        directLink: Boolean? = null,
    ) {
        _peers.update { map ->
            val old = map[id]
            val updated = Peer(
                id = id,
                name = name ?: old?.name ?: "???",
                lastSeen = System.currentTimeMillis(),
                location = location ?: old?.location,
                batteryPercent = battery ?: old?.batteryPercent,
                directLink = directLink ?: old?.directLink ?: false,
            )
            map + (id to updated)
        }
    }

    fun markPeerLink(id: Long, direct: Boolean) {
        _peers.update { map ->
            val old = map[id] ?: return@update map
            map + (id to old.copy(directLink = direct))
        }
    }

    fun pruneExpiredPeers() {
        val cutoff = System.currentTimeMillis() - PEER_EXPIRY_MS
        _peers.update { map -> map.filterValues { it.lastSeen >= cutoff } }
    }

    fun setMyLocation(loc: MyLocation?) {
        _myLocation.value = loc
    }

    fun updateStats(transform: (MeshStats) -> MeshStats) {
        _stats.update(transform)
    }

    fun reset() {
        _stats.value = MeshStats()
        _peers.value = emptyMap()
    }
}
