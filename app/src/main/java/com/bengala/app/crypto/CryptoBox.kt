package com.bengala.app.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado simétrico del canal de fiesta.
 *
 * Todos los que comparten el mismo "código de fiesta" derivan la misma clave
 * AES-256 (PBKDF2-HMAC-SHA256) y pueden leerse entre sí. Quien no tiene el
 * código solo ve bytes aleatorios pasando por el mesh.
 *
 * Formato del blob: [IV 12 bytes][ciphertext+tag GCM]
 */
class CryptoBox(partyCode: String) {

    private val key: SecretKeySpec
    private val random = SecureRandom()

    init {
        // Un código vacío (p. ej. mientras el usuario borra el campo para
        // escribir el suyo) rompería PBKDF2: usamos el código por defecto.
        val normalized = partyCode.trim().lowercase().ifBlank { "bengala" }
        // 100k iteraciones: fuerza bruta contra códigos de fiesta débiles se
        // vuelve 10x más cara que en v1.0.x. Siempre derivar fuera del hilo UI.
        val spec = PBEKeySpec(
            normalized.toCharArray(),
            // NO cambiar esta sal: es la histórica ("vengala") y mantenerla
            // permite que instalaciones viejas y nuevas se sigan entendiendo.
            "vengala-mesh-v2".toByteArray(),
            100_000,
            256,
        )
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        key = SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    /** Devuelve null si el blob no fue cifrado con este código (GCM no autentica). */
    fun decrypt(blob: ByteArray): ByteArray? {
        if (blob.size < 13) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(128, blob.copyOfRange(0, 12)),
            )
            cipher.doFinal(blob.copyOfRange(12, blob.size))
        } catch (_: Exception) {
            null
        }
    }
}
