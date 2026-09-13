package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.Entitlement
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.ui.components.*
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.progression.Levels
import com.pushuprpg.core.progression.RankProgress

data class HomeUiState(
    val progress: PlayerProgress = PlayerProgress(),
    val todayReps: Int = 0,
    val entitlement: Entitlement = Entitlement(),
    val loading: Boolean = true,
) {
    val nextDungeon: Int
        get() = (progress.highestDungeonCleared + 1).coerceAtMost(Dungeons.ALL.size)

    val streakJustBroke: Boolean
        get() = progress.streakDays == 0 && progress.bestStreakDays > 0
}

/**
 * The hub.
 *
 * Built around one question — "am I doing this today?" — so today's count and the streak come
 * first, and the single loud button below them resumes exactly where the player left off. Anything
 * that makes a user navigate before they can start exercising is a tax on the habit.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onStartDungeon: (Int) -> Unit,
    onDungeonSelect: () -> Unit,
    onSurvival: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val rank = RankProgress.of(state.progress.lifetimeReps)
    val xpNeeded = Levels.xpToNext(state.progress.level)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.home_today_reps, state.todayReps),
                    style = Type.headline,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Lv.${state.progress.level} · ${state.progress.playerClass.korean}",
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                )
            }
            StreakChip(days = state.progress.streakDays)
        }

        // A nudge only when there is something to nudge about. Saying it every day would make the
        // encouragement worthless.
        if (state.todayReps == 0) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(
                    if (state.streakJustBroke) R.string.home_streak_broken else R.string.home_nudge
                ),
                style = Type.bodyL,
                color = if (state.streakJustBroke) colors.accept else Palette.TextSecondary,
            )
        }

        Spacer(Modifier.height(22.dp))
        ProgressTrack(
            fraction = if (xpNeeded == 0) 1f else state.progress.xpIntoLevel.toFloat() / xpNeeded,
            color = Palette.Brand500,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        val dungeon = Dungeons.byIndex(state.nextDungeon)
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            supportingText = dungeon?.korean,
            onClick = { onStartDungeon(state.nextDungeon) },
        )

        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = stringResource(R.string.home_dungeons),
            onClick = onDungeonSelect,
        )

        Spacer(Modifier.height(26.dp))
        SectionHeader(text = stringResource(R.string.home_survival))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.survival_body),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = stringResource(R.string.survival_title),
            onClick = onSurvival,
        )

        Spacer(Modifier.height(26.dp))
        RankCard(rankProgress = rank, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.action_view_records),
                onClick = onRecords,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.action_settings),
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
