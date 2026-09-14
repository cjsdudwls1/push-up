package com.pushuprpg.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.share.ShareCards
import com.pushuprpg.app.ui.PushupRpgApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
                onShare = ::share,
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

    /**
     * Renders the card, then offers it.
     *
     * The render and the PNG encode run off the main thread — a 1080×1350 bitmap is not free, and
     * this button is pressed on a game-over screen where a dropped frame is the last impression the
     * run leaves. If the card cannot be written the chooser still opens, with text.
     */
    private fun share(data: ShareCardData) {
        lifecycleScope.launch {
            val uri = ShareCards.writeCard(this@MainActivity, data)
            try {
                startActivity(ShareCards.chooser(this@MainActivity, data, uri))
            } catch (e: ActivityNotFoundException) {
                // A device with nothing that accepts a share. Rare, but it is a crash otherwise.
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.share_unavailable),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
