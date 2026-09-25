package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.pushuprpg.app.domain.FreeTier
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.ui.components.*
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass
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
 *
 * Where the player left off may be a dungeon that is not free. The loud button is then the one a
 * free player can press, the free dungeon again, and the way on sits under it saying what it opens
 * — it used to be the loud button, and led to the paywall without a word.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onChangeClass: () -> Unit,
    onStartDungeon: (Int) -> Unit,
    onRequestPaywall: () -> Unit,
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weighted so the count gives way on a narrow phone rather than pushing the streak and
            // the theme toggle off the edge.
            Column(Modifier.weight(1f)) {
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
            Spacer(Modifier.width(12.dp))
            StreakChip(days = state.progress.streakDays)
            Spacer(Modifier.width(8.dp))
            ThemeToggle(mode = themeMode, onToggle = onToggleTheme)
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
        val playable = FreeTier.canPlayDungeon(state.nextDungeon, state.entitlement)
        if (playable) {
            PrimaryButton(
                text = stringResource(
                    if (state.progress.highestDungeonCleared == 0) R.string.home_start_first
                    else R.string.action_continue
                ),
                supportingText = dungeon?.korean,
                onClick = { onStartDungeon(state.nextDungeon) },
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.home_replay, Dungeons.FREE_DUNGEON.korean),
                onClick = { onStartDungeon(FreeTier.FREE_DUNGEON_INDEX) },
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = dungeon?.korean.orEmpty(),
                supportingText = stringResource(R.string.home_continue_locked),
                onClick = onRequestPaywall,
            )
        }

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

        Spacer(Modifier.height(12.dp))
        ClassCard(playerClass = state.progress.playerClass, onClick = onChangeClass)

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

/**
 * The current class, and the way to a different one.
 *
 * On the hub rather than only in settings: the class-pick screen promises the choice can be
 * changed any time, and that promise is only kept if the way back is somewhere people look.
 */
@Composable
private fun ClassCard(playerClass: PlayerClass, onClick: () -> Unit) {
    val (nameRes, descRes) = classStrings(playerClass)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(nameRes),
                style = Type.titleM,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(descRes),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.home_class_change),
            style = Type.labelL,
            color = Palette.Brand400,
        )
    }
}
