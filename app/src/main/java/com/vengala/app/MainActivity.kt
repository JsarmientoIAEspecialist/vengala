package com.vengala.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.vengala.app.mesh.MeshService
import com.vengala.app.ui.MainScreen
import com.vengala.app.ui.PermissionsScreen
import com.vengala.app.ui.theme.VengalaTheme

class MainActivity : ComponentActivity() {

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= 31) {
            perms += listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        }
        if (Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms
    }

    private fun allGranted(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VengalaTheme {
                var granted by remember { mutableStateOf(allGranted()) }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    granted = result.values.all { it } || allGranted()
                    if (granted) MeshService.start(this)
                }

                LaunchedEffect(granted) {
                    if (granted) MeshService.start(this@MainActivity)
                }

                if (granted) {
                    MainScreen()
                } else {
                    PermissionsScreen(
                        onRequest = { launcher.launch(requiredPermissions().toTypedArray()) },
                    )
                }
            }
        }
    }
}
