package com.vengala.app.mesh

import com.vengala.app.data.MeshRepository
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Un enlace BLE directo por el que se pueden enviar bytes (cliente o servidor GATT). */
interface MeshLink {
    val linkId: String
    fun send(bytes: ByteArray): Boolean
}

/**
 * Enrutamiento por inundación (flooding) con TTL + deduplicación + store-and-forward.
 *
 * - Cada paquete lleva un messageId aleatorio; un caché LRU evita reprocesar
 *   o reenviar duplicados (los ciclos del grafo mueren solos).
 * - TTL de saltos limita el radio de propagación.
 * - Los paquetes recientes se guardan y se "replican" a cada peer nuevo que se
 *   conecta: los mensajes te alcanzan aunque hayas estado fuera de rango.
 */
class MeshRouter(
    private val onDeliver: (Protocol.Packet, fromLinkId: String?) -> Unit,
) {
    private val links = ConcurrentHashMap<String, MeshLink>()

    private val seen: MutableSet<Long> = Collections.newSetFromMap(
        object : LinkedHashMap<Long, Boolean>(4096, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>) =
                size > 4000
        },
    )

    private val storeLock = Any()
    private val store = ArrayDeque<Protocol.Packet>()
    private val maxStore = 150
    private val storeMaxAgeMs = 30 * 60_000L

    val linkCount: Int get() = links.size

    fun addLink(link: MeshLink) {
        links[link.linkId] = link
        // Store-and-forward: al peer recién conectado le repetimos lo reciente.
        val snapshot = synchronized(storeLock) { store.toList() }
        for (p in snapshot) link.send(p.encode())
    }

    fun removeLink(linkId: String) {
        links.remove(linkId)
    }

    /** Publica un paquete originado en este nodo. */
    fun sendLocal(packet: Protocol.Packet) {
        synchronized(seen) { seen.add(packet.messageId) }
        storePacket(packet)
        broadcast(packet.encode(), exceptLink = null)
    }

    /** Procesa bytes que llegaron por un enlace. */
    fun onReceived(bytes: ByteArray, fromLinkId: String) {
        val packet = Protocol.decode(bytes) ?: return
        val isNew = synchronized(seen) { seen.add(packet.messageId) }
        MeshRepository.updateStats { it.copy(packetsSeen = it.packetsSeen + 1) }
        if (!isNew) return

        onDeliver(packet, fromLinkId)
        storePacket(packet)

        if (packet.ttl > 0) {
            broadcast(packet.hop().encode(), exceptLink = fromLinkId)
            MeshRepository.updateStats { it.copy(packetsRelayed = it.packetsRelayed + 1) }
        }
    }

    private fun broadcast(bytes: ByteArray, exceptLink: String?) {
        for ((id, link) in links) {
            if (id == exceptLink) continue
            link.send(bytes)
        }
    }

    private fun storePacket(packet: Protocol.Packet) {
        // La ubicación caduca rápido; solo el chat y perfiles valen la pena replicar.
        if (packet.type == Protocol.TYPE_LOCATION) return
        val cutoff = System.currentTimeMillis() - storeMaxAgeMs
        synchronized(storeLock) {
            store.addLast(packet)
            while (store.size > maxStore || (store.isNotEmpty() && store.first().timestamp < cutoff)) {
                store.removeFirst()
            }
        }
    }

    fun clear() {
        links.clear()
    }
}
