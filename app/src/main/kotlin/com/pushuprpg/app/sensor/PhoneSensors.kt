package com.pushuprpg.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.pushuprpg.core.sensor.SensorKind
import com.pushuprpg.core.sensor.SensorSample
import com.pushuprpg.core.sensor.SensorSampleSink

/**
 * The phone's accelerometer and gyroscope, handed to :core as [SensorSample]s.
 *
 * Samples are delivered on a thread of their own, not the main one: at 50 Hz from two sensors the
 * main thread would spend its frames on them. Each carries the sensor's own timestamp, not the
 * time it was delivered, which batching can put tens of milliseconds late.
 *
 * Android stops continuous sensors for an app in the background (Android 9+), so a recording only
 * runs while the screen holding it is on.
 */
class PhoneSensors(context: Context) {

    private val manager = context.getSystemService(SensorManager::class.java)

    val accelerometer: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Missing on some budget phones; the accelerometer alone is still recorded. */
    val gyroscope: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    val running: Boolean get() = listener != null

    /**
     * Starts delivering to [sink], replacing whatever was delivered to before. False when the
     * phone has no accelerometer, the one sensor every rep detector needs.
     */
    fun start(sink: SensorSampleSink): Boolean {
        stop()
        val accel = accelerometer ?: return false
        val manager = manager ?: return false
        val thread = HandlerThread("PhoneSensors").also { it.start() }
        val handler = Handler(thread.looper)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val kind = when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> SensorKind.ACCEL
                    Sensor.TYPE_GYROSCOPE -> SensorKind.GYRO
                    else -> return
                }
                val v = event.values
                sink.onSample(SensorSample(event.timestamp / NANOS_PER_MS, kind, v[0], v[1], v[2]))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, accel, SAMPLING_PERIOD_US, handler)
        gyroscope?.let { manager.registerListener(listener, it, SAMPLING_PERIOD_US, handler) }
        this.thread = thread
        this.listener = listener
        return true
    }

    fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
        thread?.quitSafely()
        thread = null
    }

    companion object {
        /**
         * 50 Hz (SENSOR_DELAY_GAME). A rep takes a second or more; fifty readings of it is plenty,
         * and well under the 200 Hz above which Android asks for a permission.
         */
        const val SAMPLING_PERIOD_US = 20_000

        private const val NANOS_PER_MS = 1_000_000L
    }
}
