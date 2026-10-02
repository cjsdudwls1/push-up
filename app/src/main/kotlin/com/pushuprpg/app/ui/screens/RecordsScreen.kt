package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.ui.components.CalorieCard
import com.pushuprpg.app.ui.components.SectionHeader
import com.pushuprpg.app.ui.components.StatTile
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.durationText
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.progression.Calories
import com.pushuprpg.core.progression.MovementRecord
import com.pushuprpg.core.progression.Records
import com.pushuprpg.core.progression.SessionFacts
import androidx.compose.runtime.remember
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The records screen — the payoff for a habit product.
 *
 * One rule shapes all of it: a lost run is displayed exactly as prominently as a won one. Greying
 * out defeats, or marking them with a failure icon, would quietly contradict the promise the app
 * makes at the end of every run, and the promise is the reason people come back after a bad day.
 * So a workout says what was done and how much, and nothing about how it ended.
 *
 * The thirteen-week grid that stood above the recent workouts was taken out, by the owner's decision
 * (2026-10-02); the week card on the hub says the week.
 */
@Composable
fun RecordsScreen(
    progress: PlayerProgress,
    sessions: List<SessionRecord>,
    /** Every run, for the calories, each movement's records and the days trained. */
    facts: List<SessionFacts>,
    /** The movement picked last, whose count to the next food the calorie card quotes. */
    exercise: ExerciseType,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val format = NumberFormat.getIntegerInstance(Locale.KOREA)
    val burn = remember(facts) { Calories.progress(Calories.of(facts)) }
    // Most done first: the movement someone actually trains leads.
    val records = remember(facts) { Records.of(facts).values.sortedByDescending { it.total } }
    val daysTrained = remember(facts) { facts.map { it.epochDay }.distinct().size }
    // Newest day first and newest first within it, as the list comes: groupBy keeps the order.
    val days = remember(sessions) {
        val zone = ZoneId.systemDefault()
        sessions.groupBy { Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }.toList()
    }
    val today = LocalDate.now()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.records_title),
                style = Type.headline,
                color = Palette.TextPrimary,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = format.format(progress.lifetimeReps),
                    label = stringResource(R.string.records_lifetime_reps),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stringResource(R.string.records_days_value, daysTrained),
                    label = stringResource(R.string.records_days_trained),
                    accent = colors.combo,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = stringResource(R.string.records_minutes_value, progress.totalActiveMs / 60_000),
                    label = stringResource(R.string.records_total_time),
                    accent = Palette.Info,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stringResource(R.string.records_days_value, progress.bestStreakDays),
                    label = stringResource(R.string.records_best_streak),
                    accent = colors.accept,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { CalorieCard(progress = burn, exercise = exercise) }

        // Each movement's best one go against its first: the number that says the body is changing.
        if (records.isNotEmpty()) {
            item { SectionHeader(text = stringResource(R.string.records_movements_title)) }
            items(records, key = { "record-" + it.exercise.name }) { record -> MovementRecordRow(record) }
        }

        item { SectionHeader(text = stringResource(R.string.records_recent_title)) }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = Type.bodyL,
                    color = Palette.TextSecondary,
                )
            }
        } else {
            items(days, key = { "day-" + it.first.toEpochDay() }) { (day, rows) -> DayGroup(day, rows, today) }
        }
    }
}

/**
 * One day's workouts under the day they were done, by the owner's decision: 오늘, 어제, or the date
 * and its weekday as the heading, and each workout a line in the card under it. The day is read once,
 * not on every card, and a day of three sets of one movement reads as one day.
 */
@Composable
private fun DayGroup(day: LocalDate, sessions: List<SessionRecord>, today: LocalDate) {
    val weekdays = stringArrayResource(R.array.weekday_short)
    val weekday = weekdays.getOrElse(day.dayOfWeek.value - 1) { "" }
    val label = when {
        day == today -> stringResource(R.string.records_day_today)
        day == today.minusDays(1) -> stringResource(R.string.records_day_yesterday)
        day.year == today.year -> stringResource(R.string.records_day_date, day.monthValue, day.dayOfMonth, weekday)
        else -> stringResource(R.string.records_day_date_year, day.year, day.monthValue, day.dayOfMonth, weekday)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = Type.labelL,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(shape = RoundedCornerShape(16.dp)),
        ) {
            sessions.forEachIndexed { i, session ->
                if (i > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Palette.StrokeSoft,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SessionRow(session)
            }
        }
    }
}

/**
 * One workout, under its day: the movement and how much of it, and the time it began.
 *
 * Nothing names the mode, by the owner's decision: with the dungeons gone every workout is 고냥이
 * 지켜줘, and a row played in a dungeon before then reads and counts like any other. A hold is told in
 * how long it was held, which the table keeps as the row's length.
 */
@Composable
private fun SessionRow(session: SessionRecord) {
    val hold = Exercises.of(session.exercise).kind == MovementKind.HOLD
    val time = remember(session.startedAtMs) {
        Instant.ofEpochMilli(session.startedAtMs).atZone(ZoneId.systemDefault()).format(TIME_OF_DAY)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(exerciseLabelRes(session.exercise)),
                style = Type.titleM,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = time,
                style = Type.labelM,
                color = Palette.TextTertiary,
            )
        }
        Text(
            text = if (hold) {
                durationText(session.durationMs / 1000)
            } else {
                stringResource(R.string.records_reps_value, session.reps)
            },
            style = Type.numeralL,
            color = Palette.TextPrimary,
        )
    }
}

/** 오후 3:12. */
private val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** One movement's records: its best one go, its first, and everything done with it. */
@Composable
private fun MovementRecordRow(record: MovementRecord) {
    val colors = LocalGameColors.current
    val hold = Exercises.of(record.exercise).kind == MovementKind.HOLD
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(exerciseLabelRes(record.exercise)),
                style = Type.titleM,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(
                    if (hold) R.string.records_movement_line_hold else R.string.records_movement_line,
                    record.first,
                    record.total,
                ),
                style = Type.labelM,
                color = Palette.TextTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(if (hold) R.string.records_duration_s else R.string.records_reps_value, record.best),
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            if (record.gain > 0) {
                Text(
                    text = stringResource(
                        if (hold) R.string.home_record_gain_hold else R.string.home_record_gain,
                        record.gain,
                    ),
                    style = Type.labelM,
                    color = colors.accept,
                )
            }
        }
    }
}
