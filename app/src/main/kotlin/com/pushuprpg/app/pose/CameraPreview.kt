package com.pushuprpg.app.pose

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/**
 * The camera preview, with pose analysis attached.
 *
 * Analysis runs on its own single-threaded executor and keeps only the newest frame. Queuing frames
 * would be worse than dropping them: a backlog means the pose result the game reacts to describes a
 * position the user left half a second ago, and the attack lands after the effort that earned it.
 */
@Composable
fun CameraPreview(
    source: PoseLandmarkerSource,
    modifier: Modifier = Modifier,
    frontCamera: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // Centre-crop: the overlay's coordinate mapping assumes it, and a letterboxed preview
            // with black bars reads as a broken camera.
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(frontCamera) {
        // Owned by this effect rather than remembered across it, so a rebind cannot leave an
        // orphaned executor running behind the new one.
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            // CameraX initialisation takes a few hundred milliseconds on the first call of a
            // process. Leaving the screen before it completes would otherwise run this listener
            // after onDispose and bind a camera nothing is left to unbind.
            if (disposed) return@addListener

            val cameraProvider = providerFuture.get()
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 640x480 is plenty: the landmarker downsamples to its own input size regardless, and a
            // larger capture buys nothing but heat and dropped frames.
            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER)
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(analysisExecutor) { image ->
                        source.analyze(image, image.imageInfo.rotationDegrees)
                    }
                }

            val selector = if (frontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (_: IllegalArgumentException) {
                // No front camera on this device, or it is held by another app. Falling back is
                // better than a black screen; the user simply has to turn the phone around.
                try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    )
                } catch (_: Exception) {
                    // Surfaced to the user by the caller's error state.
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
