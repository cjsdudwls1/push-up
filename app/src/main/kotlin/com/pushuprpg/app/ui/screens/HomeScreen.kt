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
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.ui.components.*
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.progression.Levels
import com.pushuprpg.core.progression.RankProgress
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.progression.StreakState

data class HomeUiState(
    val progress: PlayerProgress = PlayerProgress(),
    val todayReps: Int = 0,
    /** Time spent in runs today, every mode. A plank counts no reps, so this is what shows it. */
    val todayActiveMs: Long = 0,
    /** Today's work per movement, in the unit of each one's streak bar. */
    val todayWork: Map<ExerciseType, Int> = emptyMap(),
    val loading: Boolean = true,
    /** Today, as an epoch day: what the streak is read against. */
    val today: Long = 0,
) {
    val nextDungeon: Int
        get() = (progress.highestDungeonCleared + 1).coerceAtMost(Dungeons.ALL.size)

    private val streak: StreakState
        get() = StreakState(progress.streakDays, progress.lastActiveEpochDay)

    /**
     * The streak as it stands today. The stored number is only rewritten when a day meets the bar,
     * so after a missed day it would still show the streak that was broken.
     */
    val streakShown: Int
        get() = Streak.shown(streak, today)

    val streakJustBroke: Boolean
        get() = Streak.broken(streak, today)

    /** How much more of [exercise] keeps the streak today; zero once today has kept it. */
    fun leftToday(exercise: ExerciseType): Int = Streak.leftOn(streak, today, todayWork, exercise)
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
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onChangeClass: () -> Unit,
    onStartDungeon: (Int) -> Unit,
    onDungeonSelect: () -> Unit,
    onSurvival: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    /** The movement picked last, whose bar the nudge quotes. */
    lastExercise: ExerciseType,
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
            StreakChip(days = state.streakShown)
            Spacer(Modifier.width(8.dp))
            ThemeToggle(mode = themeMode, onToggle = onToggleTheme)
        }

        // A broken streak is said until a day meets the bar again, whatever else today holds: a run
        // short of the bar leaves it broken, and the line hid behind the first minute of it. The
        // nudge only when there is something to nudge about. Saying it every day would make the
        // encouragement worthless, and saying it after a plank tells someone who held one for
        // minutes that they have not started. A day begun short of the bar says what is left of it:
        // the nudge used to go with the first run, and eight squats of fifteen left no word of the
        // seven more.
        val notStarted = state.todayReps == 0 && state.todayActiveMs == 0L
        val left = state.leftToday(lastExercise)
        if (state.streakJustBroke || notStarted || left > 0) {
            // The bar quoted is the real one, for the movement the user reaches for.
            val bar = Exercises.of(lastExercise)
            val name = stringResource(exerciseLabelRes(lastExercise))
            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    state.streakJustBroke && state.streakShown > 0 ->
                        stringResource(R.string.home_streak_broken_kept, state.streakShown)
                    state.streakJustBroke -> stringResource(R.string.home_streak_broken)
                    notStarted && bar.kind == MovementKind.HOLD -> stringResource(R.string.home_nudge_hold, name, bar.streakBar)
                    notStarted -> stringResource(R.string.home_nudge, name, bar.streakBar)
                    state.streakShown == 0 && bar.kind == MovementKind.HOLD ->
                        stringResource(R.string.home_left_first_hold, name, left)
                    state.streakShown == 0 -> stringResource(R.string.home_left_first, name, left)
                    bar.kind == MovementKind.HOLD -> stringResource(R.string.home_left_hold, name, left)
                    else -> stringResource(R.string.home_left, name, left)
                },
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
        // Named, because the rank card below has a bar of its own and the two measure different
        // things: this one is the level, which XP moves.
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (xpNeeded == 0) {
                stringResource(R.string.home_max_level)
            } else {
                stringResource(R.string.home_xp_to_next, (xpNeeded - state.progress.xpIntoLevel).coerceAtLeast(0))
            },
            style = Type.labelM,
            color = Palette.TextTertiary,
        )

        Spacer(Modifier.height(24.dp))
        // The dungeon after the furthest clear, which that clear opened. With every one cleared it
        // is the last again, and the button says so rather than offering a way on there is not.
        val dungeon = Dungeons.byIndex(state.nextDungeon)
        val cleared = state.progress.highestDungeonCleared
        val allCleared = cleared >= Dungeons.ALL.size
        PrimaryButton(
            text = when {
                allCleared -> stringResource(R.string.home_replay, dungeon?.korean.orEmpty())
                cleared == 0 -> stringResource(R.string.home_start_first)
                else -> stringResource(R.string.action_continue)
            },
            supportingText = dungeon?.korean?.takeUnless { allCleared },
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
