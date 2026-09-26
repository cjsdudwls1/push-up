package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.Entitlement
import com.pushuprpg.app.domain.FreeTier
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.SectionHeader
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.game.Dungeon
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass

/**
 * Dungeon list.
 *
 * Each card shows what the run costs, as a number of reps of the movement the user last chose —
 * named, and in seconds for a hold.
 * That is the only figure someone deciding whether they have twenty minutes and the energy can act
 * on — and under the volume model it is exact rather than an estimate, because the rep count IS the
 * enemy's health. It no longer depends on a measured capacity, so it says the same thing to a
 * beginner and to an athlete.
 */
@Composable
fun DungeonSelectScreen(
    highestCleared: Int,
    /** The movement the count is quoted in — whatever was picked on the way into the last run. */
    exercise: ExerciseType,
    /** Still measured, and still used to recommend a difficulty. It no longer sizes an enemy. */
    capacity: Float,
    difficulty: Difficulty,
    /** The class the count is quoted for: a 기사's fight is fewer reps than a 궁수's. */
    playerClass: PlayerClass,
    entitlement: Entitlement,
    onDifficultyChange: (Difficulty) -> Unit,
    onStart: (Int) -> Unit,
    /** A locked dungeon was tapped; the paywall names it. */
    onRequestPaywall: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exerciseName = stringResource(exerciseLabelRes(exercise))
    val inSeconds = Exercises.of(exercise).kind == MovementKind.HOLD

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_dungeons),
                style = Type.headline,
                color = Palette.TextPrimary,
            )
        }

        item {
            SectionHeader(text = stringResource(R.string.difficulty_section))
            DifficultyRow(
                selected = difficulty,
                recommended = Difficulty.recommendedFor(capacity),
                onSelect = onDifficultyChange,
            )
        }

        items(Dungeons.ALL, key = { it.index }) { dungeon ->
            val unlocked = dungeon.index <= highestCleared + 1
            val paid = FreeTier.canPlayDungeon(dungeon.index, entitlement)
            val cost = dungeon.repCost(difficulty, exercise, playerClass)
            DungeonCard(
                dungeon = dungeon,
                // Named, and in the movement's own unit: a plank's quote is seconds.
                estimate = if (inSeconds) {
                    stringResource(R.string.dungeon_estimated_seconds, exerciseName, cost)
                } else {
                    stringResource(R.string.dungeon_estimated_reps, exerciseName, cost)
                },
                cleared = dungeon.index <= highestCleared,
                unlocked = unlocked,
                paid = paid,
                onClick = {
                    when {
                        !unlocked -> Unit
                        !paid -> onRequestPaywall(dungeon.index)
                        else -> onStart(dungeon.index)
                    }
                },
            )
        }
    }
}

@Composable
private fun DifficultyRow(
    selected: Difficulty,
    recommended: Difficulty,
    onSelect: (Difficulty) -> Unit,
) {
    val colors = LocalGameColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Difficulty.entries.forEach { difficulty ->
            val active = difficulty == selected
            Column(
                modifier = Modifier
                    .cardSurface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) Palette.Brand600 else Palette.Bg2,
                    )
                    .clickable { onSelect(difficulty) }
                    // A touch target, whether or not the chip carries its 추천 line.
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = difficulty.korean,
                    style = Type.labelL,
                    color = if (active) Palette.TextOnBrand else Palette.TextSecondary,
                )
                if (difficulty == recommended) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.difficulty_recommended),
                        style = Type.labelS,
                        // On the selected chip the fill is brand purple, and the light theme's
                        // deep green all but disappears into it.
                        color = if (active) Palette.TextOnBrand else colors.accept,
                    )
                }
            }
        }
    }
}

@Composable
private fun DungeonCard(
    dungeon: Dungeon,
    /** What the run asks, already worded: 푸쉬업 18개 예상. */
    estimate: String,
    cleared: Boolean,
    unlocked: Boolean,
    paid: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalGameColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(18.dp))
            .clickable(enabled = unlocked, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${dungeon.index}. ${dungeon.korean}",
                style = Type.titleM,
                color = if (unlocked) Palette.TextPrimary else Palette.TextDisabled,
            )
            when {
                cleared -> Pill(text = stringResource(R.string.dungeon_cleared_badge), tint = colors.accept)
                !unlocked -> Pill(text = stringResource(R.string.dungeon_locked_badge), tint = Palette.TextTertiary)
                !paid -> Pill(text = stringResource(R.string.dungeon_locked_subscription), tint = Palette.Brand400)
                dungeon.index == FreeTier.FREE_DUNGEON_INDEX ->
                    Pill(text = stringResource(R.string.dungeon_free_badge), tint = colors.accept)
                else -> Unit
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (unlocked) {
                stringResource(
                    R.string.dungeon_meta,
                    estimate,
                    stringResource(
                        R.string.dungeon_recommended_level,
                        dungeon.recommendedLevel.first,
                        dungeon.recommendedLevel.last,
                    ),
                )
            } else {
                stringResource(R.string.dungeon_locked)
            },
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
    }
}
