package com.vengala.app.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Comparte el APK instalado de Vengala por Bluetooth (o Quick Share, etc.).
 * Así la app se propaga de teléfono en teléfono en plena fiesta, sin internet.
 * Quien la recibe solo necesita permitir "instalar apps desconocidas".
 */
object ApkSharer {

    fun shareApk(context: Context) {
        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            val outDir = File(context.cacheDir, "apk").apply { mkdirs() }
            val outFile = File(outDir, "vengala.apk")
            sourceApk.copyTo(outFile, overwrite = true)

            val uri = FileProvider.getUriForFile(
                context, "com.vengala.app.fileprovider", outFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Compartir Vengala por Bluetooth"),
            )
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo compartir el APK: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }
}
