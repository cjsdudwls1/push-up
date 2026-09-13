package com.pushuprpg.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.core.detect.ExerciseType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One finished run.
 *
 * [epochDay] is denormalised from [startedAtMs] at insert time rather than derived in SQL. SQLite's
 * date functions work in UTC, so a 07:00 KST session would land on the previous calendar day and
 * the streak would break for a user who trains in the morning.
 */
@Entity(
    tableName = "sessions",
    indices = [Index("epochDay"), Index("startedAtMs")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val epochDay: Long,
    val durationMs: Long,
    val exercise: ExerciseType,
    val reps: Int,
    val maxCombo: Int,
    val deepReps: Int,
    val meanDepth: Float,
    val dungeonIndex: Int?,
    val cleared: Boolean,
    val xpEarned: Int,
    val plausibility: Float,
)

/** Local-calendar day arithmetic, in one place so the streak and the history agree on "today". */
object CalendarDays {

    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()
}

class Converters {

    @TypeConverter
    fun exerciseToString(value: ExerciseType): String = value.name

    /**
     * An unknown name means a row written by a newer build. Falling back beats crashing the
     * history screen on a downgrade.
     */
    @TypeConverter
    fun stringToExercise(value: String): ExerciseType =
        ExerciseType.entries.firstOrNull { it.name == value } ?: ExerciseType.PUSHUP
}

fun SessionEntity.toRecord(): SessionRecord = SessionRecord(
    id = id,
    startedAtMs = startedAtMs,
    durationMs = durationMs,
    exercise = exercise,
    reps = reps,
    maxCombo = maxCombo,
    deepReps = deepReps,
    meanDepth = meanDepth,
    dungeonIndex = dungeonIndex,
    cleared = cleared,
    xpEarned = xpEarned,
    plausibility = plausibility,
)

fun SessionRecord.toEntity(epochDay: Long): SessionEntity = SessionEntity(
    id = id,
    startedAtMs = startedAtMs,
    epochDay = epochDay,
    durationMs = durationMs,
    exercise = exercise,
    reps = reps,
    maxCombo = maxCombo,
    deepReps = deepReps,
    meanDepth = meanDepth,
    dungeonIndex = dungeonIndex,
    cleared = cleared,
    xpEarned = xpEarned,
    plausibility = plausibility,
)
