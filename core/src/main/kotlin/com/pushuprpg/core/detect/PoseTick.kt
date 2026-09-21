package com.pushuprpg.core.detect

/**
 * Everything the UI needs for one pose frame, as one immutable value.
 *
 * The render loop runs at its own rate and simply reads the latest tick, extrapolating the gauge
 * between pose updates using [depthVelocity]. That separation is what keeps the game smooth when
 * inference slows down on a hot or cheap phone.
 */
data class PoseTick(
    val tMs: Long,
    /** 0..100, the value the 깊이 gauge shows. */
    val depth: Float,
    /** Depth points per second; positive while descending. */
    val depthVelocity: Float,
    val depthSource: DepthSource,
    val phase: RepPhase,
    val quality: PoseQuality,
    val repCount: Int,
    val combo: Int,
    val maxCombo: Int,
    val calibration: CalibrationSnapshot,
    val render: RenderSkeleton,
    /** Empty on most frames, never null. */
    val events: List<RepEvent> = emptyList(),
    /**
     * Landmarks this movement reads that the tracker cannot currently see, so the user can be
     * told which part of them is out of shot rather than only that something is. Empty when
     * everything needed is visible.
     */
    val missing: List<Int> = emptyList(),
)

/**
 * A stateful, single-threaded exercise detector.
 *
 * Implementations must be pure with respect to the outside world: no clock reads, no randomness,
 * no Android types. All time comes from [com.pushuprpg.core.pose.PoseFrame.timestampMs]. That is
 * what makes a whole session replayable from a recorded landmark trace in a plain JVM test.
 */
interface RepDetector {
    val config: DetectorConfig

    fun onFrame(frame: com.pushuprpg.core.pose.PoseFrame): PoseTick

    fun reset()

    fun snapshotCalibration(): CalibrationSnapshot

    fun restoreCalibration(snapshot: CalibrationSnapshot)

    fun sessionSummary(): SessionSummary

    /** Folds this session's calibration into a persisted profile. */
    fun updatedProfile(previous: UserProfile): UserProfile

    /** How much of the body the overlay should draw. Changing it never affects detection. */
    var skeletonMode: SkeletonMode
}
