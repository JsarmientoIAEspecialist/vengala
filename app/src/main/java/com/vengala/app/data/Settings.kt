package com.vengala.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Identidad y preferencias locales, cifradas en reposo con una llave del
 * Keystore del teléfono (chip seguro): si alguien extrae los archivos de la
 * app, el código de fiesta y tu identidad no son legibles.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vengala_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ).also { secure ->
            // Migración desde las prefs planas de versiones < 1.1.0
            val legacy = context.getSharedPreferences("vengala", Context.MODE_PRIVATE)
            if (secure.getLong("nodeId", 0L) == 0L && legacy.getLong("nodeId", 0L) != 0L) {
                secure.edit()
                    .putLong("nodeId", legacy.getLong("nodeId", 0L))
                    .putString("displayName", legacy.getString("displayName", null))
                    .putString("partyCode", legacy.getString("partyCode", null))
                    .putBoolean("shareLocation", legacy.getBoolean("shareLocation", true))
                    .apply()
                legacy.edit().clear().apply()
            }
        }
    } catch (e: Exception) {
        // Algunos dispositivos con Keystore roto: mejor prefs planas que crashear.
        context.getSharedPreferences("vengala", Context.MODE_PRIVATE)
    }

    /** Id estable de este nodo en el mesh (8 bytes). */
    val nodeId: Long = run {
        val existing = prefs.getLong("nodeId", 0L)
        if (existing != 0L) existing
        else SecureRandom().nextLong().let { id ->
            val nonZero = if (id == 0L) 1L else id
            prefs.edit().putLong("nodeId", nonZero).apply()
            nonZero
        }
    }

    var displayName: String
        get() = prefs.getString("displayName", null) ?: "Raver-${nodeIdHex.takeLast(4)}"
        set(value) = prefs.edit().putString("displayName", value.trim().take(24)).apply()

    var partyCode: String
        get() = prefs.getString("partyCode", null) ?: "vengala"
        set(value) = prefs.edit().putString("partyCode", value.trim().take(48)).apply()

    var shareLocation: Boolean
        get() = prefs.getBoolean("shareLocation", true)
        set(value) = prefs.edit().putBoolean("shareLocation", value).apply()

    val nodeIdHex: String get() = java.lang.Long.toHexString(nodeId)

    /** Borrado total: identidad, código y nombre desaparecen del teléfono. */
    fun wipe() {
        prefs.edit().clear().apply()
    }
}
