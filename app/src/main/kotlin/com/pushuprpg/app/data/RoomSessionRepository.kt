package com.pushuprpg.app.data

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.app.domain.DailyTotal
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.domain.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Session history, backed by Room.
 *
 * [zone] is injectable so the day-boundary behaviour can be tested without touching the device
 * clock; production always passes the system zone.
 */
class RoomSessionRepository(
    private val dao: SessionDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : SessionRepository {

    override fun recent(limit: Int): Flow<List<SessionRecord>> =
        dao.recent(ExerciseType.entries.map { it.name }, limit.coerceAtLeast(1))
            .map { rows -> rows.map(SessionEntity::toRecord) }
            .flowOn(Dispatchers.IO)

    /**
     * The window is resolved per collection rather than once at construction, so a screen left open
     * across midnight starts reporting the new day's window on its next emission.
     */
    override fun dailyTotals(days: Int): Flow<List<DailyTotal>> = flow {
        val span = days.coerceAtLeast(1)
        val from = CalendarDays.today(zone) - (span - 1)
        emitAll(
            dao.dailyTotalsSince(from).map { rows ->
                rows.map { DailyTotal(epochDay = it.epochDay, reps = it.reps, activeMs = it.activeMs) }
            }
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun insert(record: SessionRecord): Long = withContext(Dispatchers.IO) {
        dao.insert(record.toEntity(CalendarDays.of(record.startedAtMs, zone)))
    }

    override suspend fun lifetimeReps(): Int = withContext(Dispatchers.IO) { dao.lifetimeReps() }

    override suspend fun repsOn(epochDay: Long): Int = withContext(Dispatchers.IO) { dao.repsOn(epochDay) }

    /** Reps logged today in local time — what the streak bar is measured against. */
    suspend fun repsToday(): Int = repsOn(CalendarDays.today(zone))

    suspend fun activeMsToday(): Long = withContext(Dispatchers.IO) {
        dao.activeMsOn(CalendarDays.today(zone))
    }

    fun lifetimeRepsFlow(): Flow<Int> = dao.lifetimeRepsFlow().flowOn(Dispatchers.IO)

    suspend fun sessionCount(): Int = withContext(Dispatchers.IO) { dao.sessionCount() }
}
