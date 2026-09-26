package com.pushuprpg.app.pose

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
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
    /** Changed to bind the camera again: the retry after [onCameraError] said it would not open. */
    attempt: Int = 0,
    /** Which camera actually bound. The overlay mirrors only for the front one. */
    onCameraBound: (Boolean) -> Unit = {},
    /**
     * Whether the camera cannot be used: true when no camera would bind or the camera reports an
     * error — another app holding it, a policy that turns it off — and false once it is open.
     */
    onCameraError: (Boolean) -> Unit = {},
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

    DisposableEffect(frontCamera, attempt) {
        // Owned by this effect rather than remembered across it, so a rebind cannot leave an
        // orphaned executor running behind the new one.
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        // A bind that succeeds is not a camera that opened. Another app holding it, or a device
        // policy, shows only in the camera's own state, and it used to show as a black preview with
        // nothing said. CameraX retries a recoverable error by itself, so OPEN clears it again.
        var cameraState: LiveData<CameraState>? = null
        val stateObserver = Observer<CameraState> { state ->
            when {
                state.error != null -> onCameraError(true)
                state.type == CameraState.Type.OPEN -> onCameraError(false)
            }
        }

        providerFuture.addListener({
            // CameraX initialisation takes a few hundred milliseconds on the first call of a
            // process. Leaving the screen before it completes would otherwise run this listener
            // after onDispose and bind a camera nothing is left to unbind.
            if (disposed) return@addListener

            val cameraProvider = try {
                providerFuture.get()
            } catch (e: Exception) {
                // CameraX could not start at all: no camera service it can reach.
                Log.w(TAG, "camera provider unavailable", e)
                onCameraError(true)
                return@addListener
            }
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 640x480 is plenty: the landmarker downsamples to its own input size regardless, and a
            // larger capture buys nothing but heat and dropped frames.
            val resolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    // ..._THEN_LOWER rather than CLOSEST_HIGHER, which CameraX's own javadoc warns
                    // can throw from bindToLifecycle when no higher resolution exists. That throw
                    // used to land in the front-camera catch below and be misread as "no front
                    // camera", silently rebinding the back one with the same failing config.
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                // Hand the analyzer an UPRIGHT buffer, so every consumer agrees about which way is
                // up. Without it the buffer stays in the sensor's landscape orientation, MediaPipe
                // returns landmarks in that same unrotated space (Tasks projects results back to
                // the original image, not the rotated ROI), and the overlay then draws sensor-frame
                // coordinates into a portrait view. Measured from a device recording: a squat's
                // vertical travel appeared as 89px of HORIZONTAL skeleton motion against 26px
                // vertical — the body's y showing up as the drawing's x, correlation +0.78.
                //
                // Detection was never affected, because BodyFrameTracker builds its axis from the
                // shoulders and its normal by rot90, so it is invariant to a rolled frame by
                // construction. This is a drawing fix, and the reps were always right.
                //
                // With this on, imageInfo.rotationDegrees becomes 0, so the value handed to
                // MediaPipe below is 0 and nothing rotates twice.
                .setOutputImageRotationEnabled(true)
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

            // The landmarker outlives the screens; whether it has answered is asked of this camera.
            source.cameraStarting()
            val camera = try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                    .also { onCameraBound(frontCamera) }
            } catch (_: IllegalArgumentException) {
                // No front camera on this device, or it is held by another app. Falling back is
                // better than a black screen; the user simply has to turn the phone around — but
                // the caller has to be told, because the overlay mirrors for the front camera and
                // would otherwise mirror a back-camera image.
                try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                    ).also { onCameraBound(false) }
                } catch (e: Exception) {
                    Log.w(TAG, "no camera would bind", e)
                    null
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "camera would not bind", e)
                null
            }

            if (camera == null) {
                onCameraError(true)
                return@addListener
            }
            cameraState = camera.cameraInfo.cameraState.also {
                it.observe(lifecycleOwner, stateObserver)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            cameraState?.removeObserver(stateObserver)
            provider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private const val TAG = "CameraPreview"
