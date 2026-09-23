package com.pushuprpg.core.detect

/**
 * The movements the game can be played with.
 *
 * The behaviour of each one lives in [Exercises], not here: adding an entry without adding its
 * descriptor fails loudly at first use rather than producing a selectable exercise that counts
 * nothing. Persisted by `name`, so entries may be appended freely but never renamed or reordered.
 *
 * Bodyweight only, by the owner's decision. Curl, overhead press, bench press and hinge were
 * removed; every stored name is read back tolerantly, so a record or setting that still names one
 * of them falls back rather than failing to load.
 */
enum class ExerciseType {
    PUSHUP, SQUAT, PLANK, PULL_UP, LUNGE, DIP
}

/**
 * Where the rep is in its cycle.
 *
 * A strike can only ever be reached from [READY_TOP], which is what structurally prevents
 * bottom-pumping: a second count requires a genuine lockout, not a validation rule applied
 * after the fact.
 */
enum class RepPhase { IDLE, LOST, READY_TOP, DESCENDING, BOTTOM, ASCENDING }

/** 얕음 / 인정 / 깊게 — the three bands the depth gauge draws. */
enum class RepGrade { SHALLOW, COUNTED, DEEP }

enum class CalibrationState { BOOTSTRAP, REVALIDATING, CONVERGED }

enum class DepthSource { PRIMARY, ELBOW_FALLBACK, NONE }

/**
 * Why counting is or is not currently possible.
 *
 * Anything other than [OK] pauses the game clock as well as the counter. A user must never lose
 * health to a boss because the tracker lost them.
 */
enum class PoseQuality {
    OK,
    NO_SUBJECT,
    LOW_CONFIDENCE,
    OUT_OF_FRAME,
    UNSTABLE_CAMERA,
    SUBJECT_SWITCH,
    TORSO_ROTATED,
    IMPLAUSIBLE_RATE,
}

enum class AbandonReason { HOVERED, STALLED_BOTTOM, SLOW_ASCENT, TOO_FAST, INCONSISTENT, QUALITY_LOST }

enum class FormHint { LOCKOUT, HIPS_SAG, HIPS_PIKE, ASYMMETRY, TEMPO }

/** One finished rep, kept for stats, leaderboards and plausibility scoring. */
data class RepRecord(
    val repIndex: Int,
    val grade: RepGrade,
    val maxDepth: Float,
    val descentMs: Int,
    val bottomMs: Int,
    val ascentMs: Int,
    val asymmetry: Float,
    val meanConfidence: Float,
    val depthSource: DepthSource,
    val qualityFlags: Set<PoseQuality> = emptySet(),
)

data class CalibrationSnapshot(
    val exercise: ExerciseType,
    val top: Float,
    val bottom: Float,
    val bottomBest: Float,
    val state: CalibrationState,
    val completedReps: Int,
)

/**
 * A persisted per-user, per-exercise range profile.
 *
 * Seeding session 2 from this is what makes the gauge trustworthy on rep 1 instead of rep 4.
 */
data class UserProfile(
    val topEwma: Float,
    val botEwma: Float,
    val sessionCount: Int,
) {
    val isEmpty: Boolean get() = sessionCount == 0

    companion object {
        fun empty(): UserProfile = UserProfile(0f, 0f, 0)
    }
}

data class SessionSummary(
    val exercise: ExerciseType,
    val repCount: Int,
    val maxCombo: Int,
    val shallowCount: Int,
    val durationMs: Long,
    val meanDepth: Float,
    /** 1 − flaggedReps/totalReps. Below 0.85 a session is kept but kept off leaderboards. */
    val plausibility: Float,
    val records: List<RepRecord>,
    val holdMs: Long = 0,
    val longestUnbrokenMs: Long = 0,
    val qualityAvg: Float = 0f,
)
