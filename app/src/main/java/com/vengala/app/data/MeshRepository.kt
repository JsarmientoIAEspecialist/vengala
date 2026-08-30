package com.vengala.app.data

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

    /** Peer que el usuario está siguiendo con la flecha (null = ninguno). */
    private val _trackedPeer = MutableStateFlow<Long?>(null)
    val trackedPeer: StateFlow<Long?> = _trackedPeer

    fun setTrackedPeer(id: Long?) {
        _trackedPeer.value = id
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
