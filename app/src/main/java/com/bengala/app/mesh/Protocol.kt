package com.bengala.app.mesh

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

/**
 * Protocolo binario de Bengala v1.
 *
 * Cabecera (30 bytes) + payload:
 *  [0]      versión = 1
 *  [1]      tipo (CHAT, LOCATION, PROFILE)
 *  [2]      TTL restante (saltos)
 *  [3]      flags (bit 0 = payload cifrado)
 *  [4-11]   messageId aleatorio (8 bytes) — deduplicación en el mesh
 *  [12-19]  senderId estable del nodo (8 bytes)
 *  [20-27]  timestamp epoch millis
 *  [28-29]  longitud del payload (uint16)
 *  [30..]   payload
 */
object Protocol {
    const val VERSION: Byte = 1
    const val HEADER_SIZE = 30
    const val MAX_PACKET = 500          // cabe en un write BLE con MTU 512
    const val DEFAULT_TTL: Byte = 7

    const val TYPE_CHAT: Byte = 1
    const val TYPE_LOCATION: Byte = 2
    const val TYPE_PROFILE: Byte = 3
    const val TYPE_MEET: Byte = 4

    const val FLAG_ENCRYPTED: Byte = 1

    /** UUID del servicio GATT de Bengala ("BENGALA" en los primeros bytes). */
    val SERVICE_UUID: UUID = UUID.fromString("56454e47-414c-4101-b000-000000000001")
    /** Característica por la que viajan los paquetes (write + notify). */
    val MESSAGE_CHAR_UUID: UUID = UUID.fromString("56454e47-414c-4102-b000-000000000002")
    /** Descriptor estándar para habilitar notificaciones. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val random = SecureRandom()

    fun newMessageId(): Long = random.nextLong()

    data class Packet(
        val type: Byte,
        val ttl: Byte,
        val flags: Byte,
        val messageId: Long,
        val senderId: Long,
        val timestamp: Long,
        val payload: ByteArray,
    ) {
        val isEncrypted get() = flags.toInt() and FLAG_ENCRYPTED.toInt() != 0

        fun encode(): ByteArray {
            val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size)
            buf.put(VERSION)
            buf.put(type)
            buf.put(ttl)
            buf.put(flags)
            buf.putLong(messageId)
            buf.putLong(senderId)
            buf.putLong(timestamp)
            buf.putShort(payload.size.toShort())
            buf.put(payload)
            return buf.array()
        }

        /** Copia con TTL decrementado, para reenviar. */
        fun hop(): Packet = copy(ttl = (ttl - 1).toByte())
    }

    fun decode(bytes: ByteArray): Packet? {
        if (bytes.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(bytes)
        val version = buf.get()
        if (version != VERSION) return null
        val type = buf.get()
        val ttl = buf.get()
        val flags = buf.get()
        val messageId = buf.long
        val senderId = buf.long
        val timestamp = buf.long
        val len = buf.short.toInt() and 0xFFFF
        if (len < 0 || HEADER_SIZE + len > bytes.size) return null
        val payload = ByteArray(len)
        buf.get(payload)
        return Packet(type, ttl, flags, messageId, senderId, timestamp, payload)
    }

    fun build(
        type: Byte,
        senderId: Long,
        payload: ByteArray,
        encrypted: Boolean,
        ttl: Byte = DEFAULT_TTL,
    ): Packet = Packet(
        type = type,
        ttl = ttl,
        flags = if (encrypted) FLAG_ENCRYPTED else 0,
        messageId = newMessageId(),
        senderId = senderId,
        timestamp = System.currentTimeMillis(),
        payload = payload,
    )
}
