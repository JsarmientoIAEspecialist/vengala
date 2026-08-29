package com.vengala.app.data

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

/** Identidad y preferencias locales (sobreviven reinicios de la app). */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vengala", Context.MODE_PRIVATE)

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
}
