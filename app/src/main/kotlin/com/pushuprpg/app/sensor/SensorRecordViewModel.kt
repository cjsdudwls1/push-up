package com.pushuprpg.app.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.sensor.SensorKind
import com.pushuprpg.core.sensor.SensorSample
import com.pushuprpg.core.sensor.SensorSampleSink
import com.pushuprpg.core.sensor.SensorTrace
import com.pushuprpg.core.sensor.SensorTraceRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** What the sensor recording screen shows. */
data class SensorRecordState(
    val movement: ExerciseType = ExerciseType.PUSHUP,
    val recording: Boolean = false,
    val seconds: Long = 0,
    val accelSamples: Int = 0,
    val gyroSamples: Int = 0,
    /** How many reps the person says they did: what a replay test holds a detector to. */
    val actualReps: Int = 0,
    /** The last finished recording, until the next one starts. */
    val hasTrace: Boolean = false,
)

/**
 * Debug builds only: records a set from the phone's motion sensors, to be sent as a file and
 * replayed through a sensor rep detector on a PC.
 *
 * Every sample goes to [sinks] as it arrives — the recorder, and the :core sensor detector once
 * it is wired in — on the sensor thread. Held in memory only; nothing is written until the user
 * sends it.
 */
class SensorRecordViewModel(
    private val sensors: PhoneSensors,
    private val device: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SensorRecordState())
    val state: StateFlow<SensorRecordState> = _state.asStateFlow()

    val hasAccelerometer: Boolean get() = sensors.accelerometer != null
    val hasGyroscope: Boolean get() = sensors.gyroscope != null

    private val lock = Any()
    private var recorder: SensorTraceRecorder? = null
    private var trace: SensorTrace? = null

    // Counted on the sensor thread, read by the ticker on the main one.
    private val accelCount = AtomicInteger()
    private val gyroCount = AtomicInteger()
    private val firstMs = AtomicLong(-1)
    private val lastMs = AtomicLong(-1)
    private var ticker: Job? = null

    fun selectMovement(movement: ExerciseType) {
        if (!_state.value.recording) _state.update { it.copy(movement = movement) }
    }

    fun setActualReps(reps: Int) = _state.update { it.copy(actualReps = reps.coerceIn(0, 999)) }

    fun start() {
        if (_state.value.recording) return
        val recorder = SensorTraceRecorder(device)
        synchronized(lock) {
            this.recorder = recorder
            trace = null
        }
        accelCount.set(0)
        gyroCount.set(0)
        firstMs.set(-1)
        lastMs.set(-1)
        val sink = SensorSampleSink { sample -> onSample(sample, recorder) }
        if (!sensors.start(sink)) return
        _state.update { it.copy(recording = true, seconds = 0, accelSamples = 0, gyroSamples = 0, hasTrace = false) }
        ticker = viewModelScope.launch {
            while (isActive) {
                publishCounts()
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        if (!_state.value.recording) return
        sensors.stop()
        ticker?.cancel()
        ticker = null
        publishCounts()
        val built = synchronized(lock) { recorder?.takeIf { it.sampleCount > 0 }?.build() }
        synchronized(lock) {
            trace = built
            recorder = null
        }
        _state.update { it.copy(recording = false, hasTrace = built != null) }
    }

    /** The finished recording with the movement and the count the person gave, or null. */
    fun latest(): SensorTrace? {
        val s = _state.value
        val t = synchronized(lock) { trace } ?: return null
        val gyro = if (hasGyroscope) "" else " gyro=none"
        return t.copy(notes = "movement=${s.movement.name} reps=${s.actualReps} rateHz=${1_000_000 / PhoneSensors.SAMPLING_PERIOD_US}$gyro")
    }

    private fun onSample(sample: SensorSample, recorder: SensorTraceRecorder) {
        synchronized(lock) { recorder.onSample(sample) }
        when (sample.kind) {
            SensorKind.ACCEL -> accelCount.incrementAndGet()
            SensorKind.GYRO -> gyroCount.incrementAndGet()
        }
        firstMs.compareAndSet(-1, sample.timestampMs)
        lastMs.set(sample.timestampMs)
        // The :core sensor rep detector takes the same sample here once it lands.
    }

    private fun publishCounts() {
        val first = firstMs.get()
        val seconds = if (first < 0) 0 else (lastMs.get() - first) / 1_000
        _state.update {
            it.copy(seconds = seconds, accelSamples = accelCount.get(), gyroSamples = gyroCount.get())
        }
    }

    override fun onCleared() {
        sensors.stop()
    }

    companion object {
        private const val TICK_MS = 250L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SensorRecordViewModel(PhoneSensors(container.appContext), container.deviceName)
            }
        }
    }
}
