package com.pushuprpg.core.sensor

import kotlinx.serialization.Serializable

/**
 * Which of the phone's motion sensors a [SensorSample] came from.
 *
 * Both are in the phone's own axes (x to the right of the screen, y up it, z out of it), as
 * Android reports them, and neither is rotated into the world: which way the phone sits in a
 * pocket or a hand is for the detector to work out, not for the source to guess.
 */
@Serializable
enum class SensorKind {
    /** Acceleration with gravity in it, m/s². At rest it reads about 9.81 along whichever axis is up. */
    ACCEL,

    /** Rate of turn about each axis, rad/s, right-handed. */
    GYRO,
}

/**
 * One reading of one motion sensor.
 *
 * The time is the sensor's own, in milliseconds on the phone's since-boot clock, and arrives with
 * the sample — :core reads no clock, so a recorded set replays in a JVM test exactly as it ran.
 */
@Serializable
data class SensorSample(
    val timestampMs: Long,
    val kind: SensorKind,
    val x: Float,
    val y: Float,
    val z: Float,
)

/** Where samples go: a detector, a recorder, or both. Called on the sensor thread. */
fun interface SensorSampleSink {
    fun onSample(sample: SensorSample)
}
