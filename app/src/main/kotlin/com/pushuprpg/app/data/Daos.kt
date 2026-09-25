package com.pushuprpg.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row of the streak calendar. Column names are aliased in SQL to match these properties. */
data class DailyTotalRow(
    val epochDay: Long,
    val reps: Int,
    val activeMs: Long,
)

/**
 * One movement's total on one day, for the streak bar. [exercise] is the stored name, read as a
 * plain string so a row naming a movement since taken out can be left out rather than miscounted.
 */
data class MovementTotalRow(
    val exercise: String,
    val reps: Int,
    val activeMs: Long,
)

@Dao
interface SessionDao {

    /**
     * The latest sessions of the movements the app still offers. A row naming one that was taken
     * out (the weighted movements) stays in the table — its reps are still in every total — but is
     * not listed, because the converter can only read it back as some other movement.
     */
    @Query(
        "SELECT * FROM sessions WHERE exercise IN (:movements) ORDER BY startedAtMs DESC, id DESC LIMIT :limit"
    )
    fun recent(movements: List<String>, limit: Int): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT epochDay AS epochDay,
               COALESCE(SUM(reps), 0) AS reps,
               COALESCE(SUM(durationMs), 0) AS activeMs
        FROM sessions
        WHERE epochDay >= :fromEpochDay
        GROUP BY epochDay
        ORDER BY epochDay ASC
        """
    )
    fun dailyTotalsSince(fromEpochDay: Long): Flow<List<DailyTotalRow>>

    @Query(
        """
        SELECT exercise AS exercise,
               COALESCE(SUM(reps), 0) AS reps,
               COALESCE(SUM(durationMs), 0) AS activeMs
        FROM sessions
        WHERE epochDay = :epochDay
        GROUP BY exercise
        """
    )
    suspend fun movementTotalsOn(epochDay: Long): List<MovementTotalRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT COALESCE(SUM(reps), 0) FROM sessions")
    suspend fun lifetimeReps(): Int

    /** Same sum as [lifetimeReps], kept live for the rank card on the home screen. */
    @Query("SELECT COALESCE(SUM(reps), 0) FROM sessions")
    fun lifetimeRepsFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(reps), 0) FROM sessions WHERE epochDay = :epochDay")
    suspend fun repsOn(epochDay: Long): Int

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM sessions WHERE epochDay = :epochDay")
    suspend fun activeMsOn(epochDay: Long): Long

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int
}
