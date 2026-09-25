package com.pushuprpg.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.RankCard
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.exerciseHintRes
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.progression.RankProgress
import com.pushuprpg.core.run.Outcome
import com.pushuprpg.core.run.Stars

/**
 * The end-of-run screen.
 *
 * Its most important element is not the verdict — it is the banner above it. A fitness game's
 * defeat screen is the moment a user decides whether the last ten minutes were wasted, so the first
 * thing they read is that their reps were recorded either way. On a loss, that banner *is* the
 * verdict; the word 실패 appears nowhere in this app.
 */
@Composable
fun ResultScreen(
    outcome: Outcome,
    dungeonName: String,
    lifetimeReps: Int,
    level: Int,
    levelsGained: Int,
    hasNextDungeon: Boolean,
    /**
     * The next dungeon is not free to this player. 다음 던전 then leads to the paywall, so it is not
     * the loud button and it says so; see [com.pushuprpg.app.domain.FreeTier].
     */
    nextLocked: Boolean,
    onNextDungeon: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onRecords: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Seconds left of the rest before the next dungeon starts by itself, or null when it will not.
     * See [com.pushuprpg.app.domain.AppSettings.autoNextRestSeconds].
     */
    restLeftSeconds: Int? = null,
    nextDungeonName: String = "",
    onStartNextNow: () -> Unit = {},
    onCancelAutoNext: () -> Unit = {},
    /** Whose way the reps were measured against, for the line under the stars. */
    playerClass: PlayerClass = PlayerClass.KNIGHT,
) {
    val colors = LocalGameColors.current
    val rank = RankProgress.of(lifetimeReps)
    // What the run did, in each movement's own unit: the reps counted, and the seconds held.
    val heldMs = outcome.segments
        .filter { Exercises.of(it.exercise).kind == MovementKind.HOLD }
        .sumOf { it.holdMs }
    val lastExercise = outcome.segments.lastOrNull()?.exercise ?: ExerciseType.PUSHUP
    // The camera counted nothing at all. Said plainly, with where the phone goes, instead of a star
    // for depth nobody measured and a banner saying 0개 were kept.
    val nothingCounted = !outcome.cleared && outcome.reps == 0 && heldMs < 1_000L
    // A run that only held is told in seconds, as its HUD counted it, not as the reps it never made.
    val inSeconds = outcome.reps == 0 && heldMs >= 1_000L
    // The tile is named after what was done: the movement, or 개수 when there was more than one.
    val worked = outcome.segments
        .filter { if (inSeconds) it.holdMs >= 1_000L else it.reps > 0 }
        .ifEmpty { outcome.segments }
    val tileLabel = worked.map { it.exercise }.distinct().singleOrNull()
        ?.let { stringResource(exerciseLabelRes(it)) }
        ?: stringResource(R.string.records_label_reps)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to if (outcome.cleared) Color(0xFF1A1208) else Palette.Bg0,
                    1f to Palette.Bg0,
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (outcome.cleared) R.string.result_cleared else R.string.result_defeat
            ),
            style = Type.headline,
            color = if (outcome.cleared) colors.deep else Palette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                outcome.cleared -> stringResource(R.string.result_cleared_sub, dungeonName)
                nothingCounted -> stringResource(
                    if (Exercises.of(lastExercise).kind == MovementKind.HOLD) R.string.result_nothing_counted_hold
                    else R.string.result_nothing_counted
                )
                else -> stringResource(R.string.result_defeat_sub)
            },
            style = Type.bodyL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )

        if (nothingCounted) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(exerciseHintRes(lastExercise)),
                style = Type.bodyM,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Bg2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        } else {
            // Stars grade the depth of counted reps, so a run with none — a hold — has none to show.
            if (outcome.reps > 0) {
                Spacer(Modifier.height(18.dp))
                StarRow(outcome.stars, cleared = outcome.cleared)
            }
            // How many reps were done the class's way — whole reps — out of all of them. Only for
            // counted movements: a hold has no way to do it or not.
            if (outcome.reps > 0 && outcome.segments.any { it.holdMs == 0L }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (playerClass == PlayerClass.ARCHER) R.string.result_style_archer else R.string.result_style_knight,
                        outcome.styleReps, outcome.reps,
                    ),
                    style = Type.labelL,
                    color = colors.deep,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))
            ReassuranceBanner(
                text = if (inSeconds) {
                    stringResource(R.string.result_banner_hold, (heldMs / 1000).toInt())
                } else {
                    stringResource(R.string.result_banner, outcome.reps)
                },
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile(
                value = if (inSeconds) {
                    stringResource(R.string.result_time_value, heldMs / 1000)
                } else {
                    outcome.reps.toString()
                },
                label = tileLabel,
                accent = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            ResultTile(
                value = stringResource(R.string.result_combo_value, outcome.maxCombo),
                label = stringResource(R.string.result_tile_combo),
                accent = colors.combo,
                modifier = Modifier.weight(1f),
            )
            ResultTile(
                value = stringResource(R.string.result_time_value, outcome.durationMs / 1000),
                label = stringResource(R.string.result_tile_time),
                accent = Palette.Info,
                modifier = Modifier.weight(1f),
            )
        }

        if (outcome.deepReps > 0) {
            Spacer(Modifier.height(10.dp))
            ResultTile(
                value = outcome.deepReps.toString(),
                label = stringResource(R.string.result_tile_deep),
                accent = colors.deep,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (levelsGained > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.result_level_up, level),
                style = Type.titleL,
                color = Palette.Brand400,
            )
        }

        Spacer(Modifier.height(20.dp))
        RankCard(rankProgress = rank)

        // Where the run stopped, as a fact: the monster and what it still owed, in the fight's own
        // unit. It used to promise the next try would break it faster, and nothing carries over —
        // the next try asks exactly what the entry screen quotes. A monster that fell as the run
        // ended owes nothing, and is said to have fallen rather than to have 0개 left.
        if (!outcome.cleared && !nothingCounted && outcome.enemyName.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    outcome.enemyLeft <= 0 -> stringResource(R.string.result_fell, outcome.enemyName)
                    Exercises.of(lastExercise).kind == MovementKind.HOLD ->
                        stringResource(R.string.result_left_hold, outcome.enemyName, outcome.enemyLeft)
                    else -> stringResource(R.string.result_left, outcome.enemyName, outcome.enemyLeft)
                },
                style = Type.bodyM,
                color = colors.deep,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))
        if (restLeftSeconds != null) {
            // The phone is across the room during a rest, so the countdown is the largest thing on
            // the screen, and the screen stays on for it.
            KeepScreenOn()
            RestCard(
                secondsLeft = restLeftSeconds,
                nextDungeonName = nextDungeonName,
                onStartNow = onStartNextNow,
                onCancel = onCancelAutoNext,
            )
            Spacer(Modifier.height(10.dp))
        } else if (outcome.cleared && hasNextDungeon && !nextLocked) {
            PrimaryButton(
                text = stringResource(R.string.action_next_dungeon),
                onClick = onNextDungeon,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            if (outcome.cleared && hasNextDungeon) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = stringResource(R.string.action_next_dungeon),
                    supportingText = stringResource(R.string.dungeon_locked_subscription),
                    onClick = onNextDungeon,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // Sharing sits directly under the primary action rather than beside 기록/홈, because a loss
        // is worth posting here too and burying it next to navigation says otherwise.
        SecondaryButton(
            text = stringResource(R.string.result_share),
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.action_view_records),
                onClick = onRecords,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.action_home),
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The rest between two dungeons: how long is left, and a way to cut it short or call it off. */
@Composable
private fun RestCard(
    secondsLeft: Int,
    nextDungeonName: String,
    onStartNow: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Bg2)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.result_rest_title),
            style = Type.labelL,
            color = Palette.TextSecondary,
        )
        Text(
            text = stringResource(R.string.result_rest_time, secondsLeft / 60, secondsLeft % 60),
            style = Type.displayM,
            color = Palette.TextPrimary,
        )
        Text(
            text = stringResource(R.string.result_rest_body, nextDungeonName),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = stringResource(R.string.result_rest_now),
            onClick = onStartNow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            text = stringResource(R.string.result_rest_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The line that has to arrive before the verdict does. */
@Composable
private fun ReassuranceBanner(text: String) {
    val colors = LocalGameColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.acceptDim)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = colors.accept,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = Type.bodyM,
            color = Palette.TextPrimary,
        )
    }
}

@Composable
private fun ResultTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Bg2)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = Type.numeralL, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(text = label, style = Type.labelM, color = Palette.TextTertiary)
    }
}

/**
 * The run's depth grade, one to three.
 *
 * The other half of the volume model. Reps decide which monster falls and every accepted rep is
 * worth exactly one, so this is what going deeper actually buys — and it is shown on the clear
 * screen rather than during the set, because chasing a star mid-rep is how form goes.
 *
 * Dim stars are drawn rather than omitted, so the grade reads as "two of three" instead of "two".
 */
@Composable
private fun StarRow(stars: Stars, cleared: Boolean) {
    val colors = LocalGameColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Text(
                    text = "\u2605",
                    style = Type.displayM,
                    color = if (i < stars.count) colors.deep else Palette.TextDisabled,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                when (stars) {
                    Stars.THREE -> R.string.result_stars_three
                    Stars.TWO -> R.string.result_stars_two
                    // 개수는 다 채웠어요 is only true of a run that did.
                    Stars.ONE -> if (cleared) R.string.result_stars_one else R.string.result_stars_one_defeat
                }
            ),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
