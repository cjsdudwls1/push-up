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
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.progression.RankProgress
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
                    value = "×${progress.bestCombo}",
                    label = stringResource(R.string.records_best_combo),
                    accent = colors.combo,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = "${progress.totalActiveMs / 60_000}분",
                    label = stringResource(R.string.records_total_time),
                    accent = Palette.Info,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${progress.bestStreakDays}일",
                    label = stringResource(R.string.records_best_streak),
                    accent = colors.accept,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { RankCard(rankProgress = RankProgress.of(progress.lifetimeReps)) }

        item {
            SectionHeader(text = "최근 13주")
            Spacer(Modifier.height(10.dp))
            ActivityGrid(dailyTotals)
        }

        item { SectionHeader(text = "최근 기록") }

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

/** A contribution-style grid: thirteen weeks of volume, read as a shape rather than as numbers. */
@Composable
private fun ActivityGrid(totals: List<DailyTotal>) {
    val colors = LocalGameColors.current
    val byDay = totals.associateBy { it.epochDay }
    val todayDate = LocalDate.now()
    val today = todayDate.toEpochDay()
    val weeks = 13

    // Each column must be one real week, so the grid starts on a Monday rather than on whatever
    // weekday happens to fall 90 days ago. Epoch day 0 was a Thursday, which is why the offset is
    // 3: (epochDay + 3) mod 7 gives 0 for a Monday.
    val mondayOffset = ((today + 3) % 7).toInt()
    val start = today - mondayOffset - (weeks - 1) * 7L
    val peak = totals.maxOfOrNull { it.reps }?.coerceAtLeast(1) ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (weekday in 0 until 7) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = WEEKDAYS[weekday],
                    style = Type.labelS,
                    color = Palette.TextTertiary,
                    modifier = Modifier.width(18.dp),
                )
                for (week in 0 until weeks) {
                    val day = start + week * 7 + weekday
                    val reps = byDay[day]?.reps ?: 0
                    val intensity = if (reps == 0) 0f else (0.25f + 0.75f * reps / peak).coerceAtMost(1f)
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (day > today) Palette.Bg1
                                else if (reps == 0) Palette.Bg3
                                else colors.accept.copy(alpha = intensity)
                            )
                    )
                }
            }
        }
    }
}

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

@Composable
private fun SessionRow(session: SessionRecord) {
    val colors = LocalGameColors.current
    val date = java.time.Instant.ofEpochMilli(session.startedAtMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("M월 d일"))

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
                text = "$date · ${session.durationMs / 1000}초 · ×${session.maxCombo}",
                style = Type.labelM,
                color = Palette.TextTertiary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${session.reps}",
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            // 완료 / 도전, never 성공 / 실패. The run happened either way.
            Text(
                text = if (session.cleared) "완료" else "도전",
                style = Type.labelM,
                color = if (session.cleared) colors.accept else Palette.TextSecondary,
            )
        }
    }
}
