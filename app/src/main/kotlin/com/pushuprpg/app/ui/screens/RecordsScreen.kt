package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.DailyTotal
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.ui.components.RankCard
import com.pushuprpg.app.ui.components.SectionHeader
import com.pushuprpg.app.ui.components.StatTile
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.progression.RankProgress
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * The records screen — the payoff for a habit product.
 *
 * One rule shapes all of it: a lost run is displayed exactly as prominently as a won one. Greying
 * out defeats, or marking them with a failure icon, would quietly contradict the promise the app
 * makes at the end of every run, and the promise is the reason people come back after a bad day.
 */
@Composable
fun RecordsScreen(
    progress: PlayerProgress,
    sessions: List<SessionRecord>,
    dailyTotals: List<DailyTotal>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val format = NumberFormat.getIntegerInstance(Locale.KOREA)

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
                    value = stringResource(R.string.records_combo_value, progress.bestCombo),
                    label = stringResource(R.string.records_best_combo),
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

        item { RankCard(rankProgress = RankProgress.of(progress.lifetimeReps)) }

        item {
            SectionHeader(text = stringResource(R.string.records_heat_title))
            Spacer(Modifier.height(10.dp))
            ActivityGrid(dailyTotals)
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
            items(sessions, key = { it.id }) { session -> SessionRow(session) }
        }
    }
}

/**
 * A contribution-style grid: thirteen weeks of volume, read as a shape rather than as numbers.
 *
 * A day is lit by reps or by time: a plank counts no reps, and a day spent holding one used to
 * stay dark.
 */
@Composable
private fun ActivityGrid(totals: List<DailyTotal>) {
    val colors = LocalGameColors.current
    val byDay = totals.associateBy { it.epochDay }
    val todayDate = LocalDate.now()
    val today = todayDate.toEpochDay()
    val weeks = 13
    // Every other row, as the calendar apps do: seven one-letter labels crowd a 13dp row.
    val weekdays = listOf(
        stringResource(R.string.records_weekday_mon), "",
        stringResource(R.string.records_weekday_wed), "",
        stringResource(R.string.records_weekday_fri), "", "",
    )

    // Each column must be one real week, so the grid starts on a Monday rather than on whatever
    // weekday happens to fall 90 days ago. Epoch day 0 was a Thursday, which is why the offset is
    // 3: (epochDay + 3) mod 7 gives 0 for a Monday.
    val mondayOffset = ((today + 3) % 7).toInt()
    val start = today - mondayOffset - (weeks - 1) * 7L
    val peakReps = totals.maxOfOrNull { it.reps }?.coerceAtLeast(1) ?: 1
    val peakMs = totals.maxOfOrNull { it.activeMs }?.coerceAtLeast(1L) ?: 1L

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (weekday in 0 until 7) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weekdays[weekday],
                    style = Type.labelS,
                    color = Palette.TextTertiary,
                    modifier = Modifier.width(18.dp),
                )
                for (week in 0 until weeks) {
                    val day = start + week * 7 + weekday
                    val total = byDay[day]
                    val worked = total != null && (total.reps > 0 || total.activeMs > 0L)
                    // The larger of the day's two shares, so a long hold reads as much as its reps would.
                    val share = if (total == null) 0f else maxOf(
                        total.reps.toFloat() / peakReps,
                        total.activeMs.toFloat() / peakMs,
                    )
                    val intensity = if (!worked) 0f else (0.25f + 0.75f * share).coerceAtMost(1f)
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (day > today) Palette.Bg1
                                else if (!worked) Palette.Bg3
                                else colors.accept.copy(alpha = intensity)
                            )
                    )
                }
            }
        }
    }
}

/**
 * One run, or one movement of a run that switched: what was done, when, and how much.
 *
 * The movement leads the line, since a run that switched banks a row per movement and those rows
 * share a dungeon name. A hold is told in seconds, which is what it counts. The table keeps no hold
 * time, so the seconds are the row's length, which for a hold is written as the time it was held.
 */
@Composable
private fun SessionRow(session: SessionRecord) {
    val colors = LocalGameColors.current
    val day = java.time.Instant.ofEpochMilli(session.startedAtMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val date = stringResource(R.string.records_date, day.monthValue, day.dayOfMonth)
    val hold = Exercises.of(session.exercise).kind == MovementKind.HOLD
    val seconds = session.durationMs / 1000
    val meta = listOfNotNull(
        stringResource(exerciseLabelRes(session.exercise)),
        date,
        if (hold) null else durationText(seconds),
        if (hold) null else stringResource(R.string.records_combo_value, session.maxCombo),
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = Dungeons.byIndex(session.dungeonIndex ?: 0)?.korean
                    ?: stringResource(R.string.survival_title),
                style = Type.titleM,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = meta,
                style = Type.labelM,
                color = Palette.TextTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (hold) {
                    stringResource(R.string.records_duration_s, seconds)
                } else {
                    stringResource(R.string.records_reps_value, session.reps)
                },
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            // 완료 / 도전, never 성공 / 실패. The run happened either way.
            Text(
                text = stringResource(
                    if (session.cleared) R.string.records_session_cleared else R.string.records_session_attempted
                ),
                style = Type.labelM,
                color = if (session.cleared) colors.accept else Palette.TextSecondary,
            )
        }
    }
}

/** A run's length in the largest units that read naturally: 42초, 3분 12초, 1시간 5분. */
@Composable
private fun durationText(seconds: Long): String = when {
    seconds >= 3_600 -> stringResource(R.string.records_duration_hm, seconds / 3_600, seconds % 3_600 / 60)
    seconds >= 60 -> stringResource(R.string.records_duration_ms, seconds / 60, seconds % 60)
    else -> stringResource(R.string.records_duration_s, seconds)
}
