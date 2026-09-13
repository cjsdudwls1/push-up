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

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SessionEntity>>

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
