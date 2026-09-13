package com.pushuprpg.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pushuprpg.app.ui.PushupRpgApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val cameraGranted = MutableStateFlow(false)
    private val permissionPermanentlyDenied = MutableStateFlow(false)

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted.value = granted
        // "Denied and we may no longer ask" is the state that needs a different screen: the system
        // dialog will not appear again, so pointing at the in-app button would be a dead end.
        permissionPermanentlyDenied.value =
            !granted && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val container = (application as PushupApp).container

        setContent {
            PushupRpgApp(
                container = container,
                cameraGranted = cameraGranted,
                permissionPermanentlyDenied = permissionPermanentlyDenied,
                onRequestCameraPermission = { requestCamera.launch(Manifest.permission.CAMERA) },
                onOpenAppSettings = ::openAppSettings,
                onShare = ::shareScore,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        cameraGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        (application as PushupApp).container.onAppResume()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun shareScore(score: Int) {
        val text = getString(R.string.survival_share_text, score)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                getString(R.string.survival_share),
            )
        )
    }
}
