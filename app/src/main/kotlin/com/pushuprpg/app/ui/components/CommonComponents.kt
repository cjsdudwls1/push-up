package com.pushuprpg.app.ui.components

import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.progression.Rank
import com.pushuprpg.core.progression.RankProgress
import java.util.WeakHashMap

/** The one card radius the hub screens use, so nothing drifts by a few dp between screens. */
val CardShape: Shape = RoundedCornerShape(20.dp)

/**
 * Card chrome as a modifier rather than a wrapper composable, so callers stay free to be a Row, a
 * Column or a clickable — every hub surface is a different shape of the same card.
 *
 * Composable only because its default colours follow the theme; it holds no state.
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape = CardShape,
    color: Color = Palette.Bg2,
    borderColor: Color = Palette.StrokeSoft,
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, borderColor, shape)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Type.labelL,
        color = Palette.TextTertiary,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/**
 * A filled bar drawn by hand.
 *
 * The stock indicator animates and indeterminate-shimmers by default; these bars sit behind a
 * "reduce motion" setting and read as a static quantity, so a plain box is both calmer and exact.
 */
@Composable
fun ProgressTrack(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    trackColor: Color = Palette.Bg3,
) {
    val filled = fraction.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor),
    ) {
        if (filled > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(filled)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

/** The big-number-and-caption tile: one fact, read at a glance. */
@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color = Palette.TextPrimary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .cardSurface(shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = Type.numeralL,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = Type.labelM,
            color = Palette.TextTertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rank tiers share the four colours the palette already defines for loot rarity. */
@Composable
@ReadOnlyComposable
fun rankColour(rank: Rank): Color = when (rank) {
    Rank.SEEDLING, Rank.TRAINEE -> Palette.TierCommon
    Rank.WARRIOR, Rank.VETERAN -> Palette.TierRare
    Rank.ELITE, Rank.CHAMPION, Rank.MASTER -> Palette.TierEpic
    Rank.GRANDMASTER, Rank.LEGEND, Rank.IMMORTAL -> Palette.TierLegend
}

@Composable
fun RankCard(rankProgress: RankProgress, modifier: Modifier = Modifier) {
    val tier = rankColour(rankProgress.rank)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tier),
            )
            Spacer(Modifier.width(10.dp))
            // The rank name takes whatever is left after the lifetime count, rather than the two
            // sharing the row by weight — a weighted split caps each at half the width, so a longer
            // rank name like 그랜드마스터 ellipsises while empty space sits beside it.
            Text(
                text = rankProgress.rank.korean,
                style = Type.titleL,
                color = tier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.rank_lifetime, rankProgress.lifetimeReps),
                style = Type.labelL,
                color = Palette.TextSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(16.dp))
        ProgressTrack(fraction = rankProgress.fraction, color = tier)
        Spacer(Modifier.height(10.dp))
        val next = rankProgress.next
        Text(
            text = if (next == null) {
                stringResource(R.string.rank_max)
            } else {
                stringResource(R.string.result_rank_to_next, next.korean, rankProgress.repsToNext)
            },
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Brand500,
            contentColor = Palette.TextOnAccent,
            disabledContainerColor = Palette.Bg3,
            disabledContentColor = Palette.TextDisabled,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = text, style = Type.titleM, maxLines = 1)
            if (supportingText != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = supportingText,
                    style = Type.labelM,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A second, smaller line: where the button leads when its label alone would not say. */
    supportingText: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Palette.StrokeHard),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Palette.Bg2,
            contentColor = Palette.TextPrimary,
            disabledContainerColor = Palette.Bg2,
            disabledContentColor = Palette.TextDisabled,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = text, style = Type.titleM, maxLines = 1)
            if (supportingText != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = supportingText,
                    style = Type.labelM,
                    color = LocalContentColor.current.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The streak pill. Zero days is drawn in the muted state rather than hidden — a streak the user
 * cannot see is a streak they cannot start.
 */
@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    val alive = days > 0
    val dot = if (alive) LocalGameColors.current.combo else Palette.TextTertiary
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (alive) Palette.Bg3 else Palette.Bg2)
            .border(1.dp, if (alive) dot.copy(alpha = 0.40f) else Palette.StrokeSoft, CircleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_streak, days),
            style = Type.labelL,
            color = if (alive) Palette.TextPrimary else Palette.TextSecondary,
            maxLines = 1,
        )
    }
}

/**
 * Dark or light, as one tap.
 *
 * The icon is the mode a tap switches *to* — a sun on the dark screen, a moon on the light one —
 * and the spoken label says so in words, because an icon alone can be read either way.
 */
@Composable
fun ThemeToggle(mode: ThemeMode, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val toLight = mode == ThemeMode.DARK
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Palette.Bg2)
            .border(1.dp, Palette.StrokeSoft, CircleShape)
            .clickable(role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (toLight) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = stringResource(
                if (toLight) R.string.theme_switch_to_light else R.string.theme_switch_to_dark
            ),
            tint = Palette.TextPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A small tinted label: 잠김, 클리어, 무료. One per card, never a row of them. */
@Composable
fun Pill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Type.labelM,
        color = tint,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Holds the screen awake for as long as this composable is in the tree.
 *
 * Scoped rather than set on the window: during a set the user's hands are on the floor and nothing
 * will touch the screen, but a phone left open on the hub and put in a pocket should still sleep.
 *
 * Counted per view, because every screen shares the one root view: in a transition the screen
 * coming in holds it before the one going out lets go, and the last to let go used to turn it off.
 * The rest before the next dungeon and the dungeon it led into ran with the screen free to sleep,
 * and a phone that slept paused the rest and stopped the camera mid-set.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        screenOnHolds[view] = (screenOnHolds[view] ?: 0) + 1
        view.keepScreenOn = true
        onDispose {
            val left = (screenOnHolds[view] ?: 1) - 1
            if (left > 0) screenOnHolds[view] = left else screenOnHolds.remove(view)
            view.keepScreenOn = left > 0
        }
    }
}

/** How many [KeepScreenOn]s each view has in its tree. Main thread only, as composition is. */
private val screenOnHolds = WeakHashMap<View, Int>()

/**
 * A run's length in the largest units that read naturally: 42초, 3분 12초, 1시간 5분.
 *
 * One for every screen that says how long a run took: the result screen said 214초 of the run the
 * records called 3분 34초.
 */
@Composable
fun durationText(seconds: Long): String = when {
    seconds >= 3_600 -> stringResource(R.string.records_duration_hm, seconds / 3_600, seconds % 3_600 / 60)
    seconds >= 60 -> stringResource(R.string.records_duration_ms, seconds / 60, seconds % 60)
    else -> stringResource(R.string.records_duration_s, seconds)
}

/**
 * The Korean name of an exercise, as a string resource.
 *
 * Shared rather than written out at each call site: the settings picker and the battle HUD must
 * never disagree about what a movement is called, and a new exercise should not be able to ship
 * with a name in one place and an enum constant in the other.
 */
@StringRes
fun exerciseLabelRes(exercise: ExerciseType): Int = when (exercise) {
    ExerciseType.PUSHUP -> R.string.exercise_pushup
    ExerciseType.SQUAT -> R.string.exercise_squat
    ExerciseType.PLANK -> R.string.exercise_plank
    ExerciseType.PULL_UP -> R.string.exercise_pull_up
    ExerciseType.LUNGE -> R.string.exercise_lunge
    ExerciseType.DIP -> R.string.exercise_dip
}

/**
 * Where to put the phone for [exercise], in one line.
 *
 * One line because the rest is said live: [PlacementBanner] watches the camera and tells the user
 * what to move while the phone is actually in their hands. A paragraph here was read before the
 * set and forgotten by the time the phone was on the floor.
 */
@StringRes
fun exerciseHintRes(exercise: ExerciseType): Int = when (exercise) {
    ExerciseType.PUSHUP -> R.string.exercise_hint_pushup
    ExerciseType.SQUAT -> R.string.exercise_hint_squat
    ExerciseType.PLANK -> R.string.exercise_hint_plank
    ExerciseType.PULL_UP -> R.string.exercise_hint_pull_up
    ExerciseType.LUNGE -> R.string.exercise_hint_lunge
    ExerciseType.DIP -> R.string.exercise_hint_dip
}

/** The expanded picker row: where the phone goes, and whether the movement is still being tried out. */
@Composable
fun ExerciseNotes(exercise: ExerciseType) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(exerciseHintRes(exercise)),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        // Honest in two words rather than a sentence: no real body has confirmed the count yet.
        if (!Exercises.of(exercise).validatedOnDevice) {
            Spacer(Modifier.width(8.dp))
            Pill(text = stringResource(R.string.exercise_unvalidated), tint = Palette.TextTertiary)
        }
    }
}
