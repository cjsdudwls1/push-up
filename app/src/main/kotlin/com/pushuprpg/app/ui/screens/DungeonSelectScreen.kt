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
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.game.CombatResolver
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.game.Dungeon
import com.pushuprpg.core.game.Dungeons

/**
 * Dungeon list.
 *
 * Each card shows what the run will actually cost *this* player — not an abstract difficulty
 * rating but a number of reps, computed from their own measured capacity. That is the only figure
 * someone deciding whether they have twenty minutes and the energy can act on.
 */
@Composable
fun DungeonSelectScreen(
    highestCleared: Int,
    capacity: Float,
    difficulty: Difficulty,
    entitlement: Entitlement,
    onDifficultyChange: (Difficulty) -> Unit,
    onStart: (Int) -> Unit,
    onRequestPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
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
            DifficultyRow(
                selected = difficulty,
                recommended = Difficulty.recommendedFor(capacity),
                onSelect = onDifficultyChange,
            )
        }

        items(Dungeons.ALL, key = { it.index }) { dungeon ->
            val unlocked = dungeon.index <= highestCleared + 1
            val paid = FreeTier.canPlayDungeon(dungeon.index, entitlement)
            DungeonCard(
                dungeon = dungeon,
                expectedReps = CombatResolver.expectedReps(
                    dungeon.standardRepCost, difficulty, capacity
                ),
                cleared = dungeon.index <= highestCleared,
                unlocked = unlocked,
                paid = paid,
                onClick = {
                    when {
                        !unlocked -> Unit
                        !paid -> onRequestPaywall()
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = difficulty.korean,
                    style = Type.labelL,
                    color = if (active) Palette.TextPrimary else Palette.TextSecondary,
                )
                if (difficulty == recommended) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.difficulty_recommended),
                        style = Type.labelS,
                        color = colors.accept,
                    )
                }
            }
        }
    }
}

@Composable
private fun DungeonCard(
    dungeon: Dungeon,
    expectedReps: Int,
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
                cleared -> Pill(text = "클리어", tint = colors.accept)
                !unlocked -> Pill(text = "잠김", tint = Palette.TextTertiary)
                !paid -> Pill(text = "구독 필요", tint = Palette.Brand400)
                dungeon.index == FreeTier.FREE_DUNGEON_INDEX -> Pill(text = "무료", tint = colors.accept)
                else -> Unit
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (unlocked) {
                stringResource(R.string.dungeon_estimated_reps, expectedReps) +
                    " · " + stringResource(
                        R.string.dungeon_recommended_level,
                        dungeon.recommendedLevel.first,
                        dungeon.recommendedLevel.last,
                    )
            } else {
                stringResource(R.string.dungeon_locked)
            },
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
    }
}
