package com.pushuprpg.app.pose

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.pushuprpg.core.pose.Landmark
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps MediaPipe's Pose Landmarker and turns its results into `:core`'s plain [PoseFrame].
 *
 * This is the only place in the app that knows MediaPipe exists. Everything downstream deals in
 * floats, which is what lets the whole rep-detection algorithm be tested on a plain JVM.
 */
class PoseLandmarkerSource(
    private val context: Context,
    private val onFrame: (PoseFrame) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var landmarker: PoseLandmarker? = null

    /**
     * Guards the landmarker across the two threads that touch it: [analyze] runs on the camera's
     * analysis executor, [close] on the main thread, and shutting the executor down does not wait
     * for a frame already inside detectAsync.
     */
    private val markerLock = Any()

    private var lastTimestampMs: Long = -1L

    /** True when the GPU delegate failed and we fell back; surfaced so quality can be dialled back. */
    var usingCpu: Boolean = false
        private set

    /** Set by the GPU delegate's first error on a frame; every setup after it goes to the CPU. */
    private val gpuFailedOnFrame = AtomicBoolean(false)

    /** The fallback sets up from its own thread, and two setups at once would leak a landmarker. */
    private val setupLock = Any()

    private val _ready = MutableStateFlow(false)

    /**
     * Whether the landmarker has returned a result since it was set up: the model has loaded and
     * frames are reaching it. Loading takes a second or three, and until now that stretch read as
     * 화면 안으로 들어와 주세요, said to someone already standing in the picture.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun setup(preferGpu: Boolean = true): Unit = synchronized(setupLock) {
        close()
        if (preferGpu && !gpuFailedOnFrame.get() && tryCreate(Delegate.GPU)) return
        // A GPU delegate can fail at creation on plenty of real devices — driver bugs, an OEM
        // OpenCL stub, a headless emulator. Falling back is normal operation, not an error path.
        if (!tryCreate(Delegate.CPU)) {
            onError("pose landmarker unavailable")
        }
    }

    private fun tryCreate(delegate: Delegate): Boolean = try {
        val base = BaseOptions.builder()
            .setDelegate(delegate)
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            // One pose. A second person walking through the room is handled by the detector's
            // subject-switch guard rather than by paying for a second landmark pass every frame.
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setOutputSegmentationMasks(false)
            .setResultListener { result, input -> publish(result, input) }
            .setErrorListener { e -> onRunError(delegate, e) }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
        usingCpu = delegate == Delegate.CPU
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "pose landmarker failed on $delegate", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "pose landmarker failed on $delegate", e)
        false
    }

    /**
     * An error from a landmarker that is already running.
     *
     * A GPU delegate that builds can still fail on its first frames, and that left a live preview
     * with a counter frozen at zero and the model error on screen. The first such error moves to
     * the CPU, once, as a failure at creation already does; what the GPU says after that is from
     * the landmarker being replaced.
     */
    private fun onRunError(delegate: Delegate, e: RuntimeException) {
        Log.w(TAG, "pose landmarker error on $delegate", e)
        if (delegate != Delegate.GPU) {
            onError(e.message ?: "pose error")
            return
        }
        if (gpuFailedOnFrame.compareAndSet(false, true)) {
            // Not on this thread, which is the landmarker's own: it cannot close itself from
            // inside its own callback.
            thread(name = "pose-cpu-fallback") { setup(preferGpu = false) }
        }
    }

    /**
     * Feeds one camera frame.
     *
     * The buffer arrives upright: CameraPreview sets `setOutputImageRotationEnabled(true)`, so
     * `rotationDegrees` is 0 here and the rotation passed below is a no-op kept only so a device
     * that ignores the flag still gets a correct frame.
     *
     * This used to hand MediaPipe the sensor's landscape buffer plus a rotation, on the reasoning
     * that pre-rotating wastes a bitmap a frame on a transform the library already does. The
     * library does do it — for inference. But Tasks projects its results back into the *original*
     * unrotated image, so the landmarks came back in sensor coordinates while the preview showed
     * the display frame, and the overlay drew one into the other. Detection never minded, because
     * the geometry is roll-invariant by construction; the skeleton was drawn sideways.
     *
     * Mirroring is still left to the renderer, because the detection maths is mirror-invariant.
     */
    fun analyze(image: ImageProxy, rotationDegrees: Int): Unit = synchronized(markerLock) {
        val marker = landmarker ?: run { image.close(); return }

        // detectAsync rejects a timestamp that is not strictly increasing, and the value it
        // receives is in milliseconds — so the guard has to be in milliseconds too. Checking
        // microseconds and submitting milliseconds lets two frames half a millisecond apart pass
        // the check and then collide, which throws.
        val timestampMs = image.imageInfo.timestamp / 1_000_000
        if (timestampMs <= lastTimestampMs) {
            image.close()
            return
        }
        lastTimestampMs = timestampMs

        try {
            val bitmap = image.toBitmap()
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            val processing = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()
            marker.detectAsync(mpImage, processing, timestampMs)
        } catch (e: RuntimeException) {
            Log.w(TAG, "frame dropped", e)
        } finally {
            image.close()
        }
    }

    private fun publish(result: PoseLandmarkerResult, input: MPImage) {
        _ready.value = true
        val timestampMs = result.timestampMs()
        val poses = result.landmarks()

        if (poses.isEmpty() || poses[0].size != PoseLandmarks.COUNT) {
            onFrame(PoseFrame.empty(timestampMs, input.width, input.height))
            return
        }

        val normalized = poses[0].map { lm ->
            Landmark(
                x = lm.x(),
                y = lm.y(),
                z = lm.z(),
                // These are documented but not reliably populated on Android. Passing the sentinel
                // through rather than defaulting to 1.0 is what lets :core notice they are missing
                // and fall back to its own geometric confidence instead of trusting a fake score.
                visibility = lm.visibility().orElse(Landmark.UNKNOWN),
                presence = lm.presence().orElse(Landmark.UNKNOWN),
            )
        }

        val world = result.worldLandmarks().firstOrNull()
            ?.takeIf { it.size == PoseLandmarks.COUNT }
            ?.map { Landmark(it.x(), it.y(), it.z()) }
            ?: emptyList()

        onFrame(
            PoseFrame(
                timestampMs = timestampMs,
                imageWidth = input.width,
                imageHeight = input.height,
                landmarks = normalized,
                worldLandmarks = world,
            )
        )
    }

    fun close() {
        synchronized(markerLock) {
            // A graph that failed while running throws its error again from close(): MediaPipe
            // gives the runner no listener, so it rethrows. From the CPU fallback's own thread that
            // would take the app down on the way to the recovery.
            try {
                landmarker?.close()
            } catch (e: RuntimeException) {
                Log.w(TAG, "pose landmarker did not close cleanly", e)
            }
            landmarker = null
            lastTimestampMs = -1L
            _ready.value = false
        }
    }

    companion object {
        private const val TAG = "PoseLandmarkerSource"

        /**
         * The lite model is bundled on purpose. It is the only variant that reliably holds 30fps on
         * the mid-range hardware most users have, and landmark *accuracy* is not what limits rep
         * counting here — landmark *stability* is, which the filter in `:core` handles.
         */
        const val MODEL_ASSET = "pose_landmarker_lite.task"

        const val MIN_DETECTION_CONFIDENCE = 0.5f
        const val MIN_PRESENCE_CONFIDENCE = 0.5f
        const val MIN_TRACKING_CONFIDENCE = 0.5f
    }
}
