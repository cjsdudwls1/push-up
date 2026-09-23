package com.pushuprpg.app.ui.battle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.ExerciseType
import androidx.annotation.StringRes
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import java.text.NumberFormat
import java.util.Locale

/**
 * Text drawn over the camera image.
 *
 * A live camera frame can be any colour at any moment, so nothing readable can rely on contrast
 * with it. Every string over the preview goes through here and gets either a scrim behind it or an
 * ink outline around it — which is why this exists as a component rather than as a convention
 * people are asked to remember.
 */
@Composable
fun CameraText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Palette.TextPrimary,
    legibility: Legibility = Legibility.OUTLINE,
) {
    when (legibility) {
        Legibility.OUTLINE -> Box(modifier) {
            // A real stroke, offset in four directions, rather than a drop shadow: a shadow fails
            // over a bright background, which on a camera feed is half the time.
            val ink = Palette.OutlineInk
            val offsets = listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2, -2 to -2, 2 to 2, -2 to 2, 2 to -2)
            offsets.forEach { (dx, dy) ->
                Text(
                    text = text,
                    style = style,
                    color = ink,
                    modifier = Modifier.offset(x = dx.dp, y = dy.dp),
                )
            }
            Text(text = text, style = style, color = color)
        }

        Legibility.PANEL -> Text(
            text = text,
            style = style,
            color = color,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.ScrimPanel)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Legibility.SCRIM -> Text(text = text, style = style, color = color, modifier = modifier)
    }
}

enum class Legibility { OUTLINE, PANEL, SCRIM }

/** Vertical scrims so the top and bottom bands stay readable whatever is behind them. */
@Composable
fun EdgeScrims(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB8000000),
                        1f to Color.Transparent,
                    )
                )
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color(0x8C000000),
                    )
                )
        )
    }
}

/**
 * A combatant's health.
 *
 * The exact remaining number is shown, not just a bar. A bar answers "roughly how much is left";
 * mid-set the question the player actually has is "can I finish this in one more push", and only a
 * number answers that.
 */
@Composable
fun HealthBar(
    name: String,
    hp: Int,
    maxHp: Int,
    color: Color,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * What to print under the bar. Defaults to "hp / maxHp".
     *
     * Overridable because neither bar is health any more: a run is a count of reps, so the left one
     * reads as progress through the run and the right one as the reps this monster still owes.
     */
    label: String? = null,
) {
    val fraction by animateFloatAsState(
        targetValue = (hp.toFloat() / maxHp.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280),
        label = "hp",
    )
    val format = remember { NumberFormat.getIntegerInstance(Locale.KOREA) }

    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        CameraText(text = name, style = Type.labelL, color = Palette.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Palette.Bg0.copy(alpha = 0.75f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
                    .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
            )
        }
        Spacer(Modifier.height(3.dp))
        CameraText(
            text = label ?: "${format.format(hp)} / ${format.format(maxHp)}",
            style = Type.labelM,
            color = Palette.TextSecondary,
        )
    }
}

/**
 * The player's health, as a thin strip under the progress bar.
 *
 * Thin because it moves rarely — only a monster's ultimate that went unanswered touches it — and
 * the progress bar above it is what changes every rep.
 */
@Composable
fun HpStrip(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val colors = LocalGameColors.current
    val fraction by animateFloatAsState(
        targetValue = (hp.toFloat() / maxHp.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 360),
        label = "playerHp",
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CameraText(text = stringResource(R.string.battle_hp_label), style = Type.labelM, color = Palette.TextSecondary)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Palette.Bg0.copy(alpha = 0.75f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (fraction < 0.34f) colors.bossHp else colors.playerHp)
            )
        }
        Spacer(Modifier.width(6.dp))
        CameraText(text = hp.toString(), style = Type.labelM, color = Palette.TextSecondary)
    }
}

/** The combo pill. Colour and size both climb with the streak so it reads without being counted. */
@Composable
fun ComboPill(combo: Int, modifier: Modifier = Modifier) {
    val colors = LocalGameColors.current
    AnimatedVisibility(
        visible = combo >= 2,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val hot = (combo / 20f).coerceIn(0f, 1f)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(androidx.compose.ui.graphics.lerp(colors.combo, colors.comboHot, hot))
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.battle_combo, combo),
                style = Type.titleL,
                color = Palette.TextOnAccent,
            )
        }
    }
}

/**
 * The rep counter.
 *
 * Placed at the vertical centre of the screen rather than at the bottom, where the demo had it. At
 * the bottom of a pushup the user's own shoulders occlude the lower band of the screen and their
 * viewing angle foreshortens it badly — which is precisely the moment they most want to see the
 * number go up.
 */
@Composable
fun RepCounter(
    reps: Int,
    dimmed: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.alpha(if (dimmed) 0.35f else 1f),
        verticalAlignment = Alignment.Bottom,
    ) {
        CameraText(
            text = reps.toString(),
            style = if (compact) Type.heroCountSmall else Type.heroCount,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.width(6.dp))
        CameraText(
            text = stringResource(R.string.battle_unit_reps),
            style = Type.titleL,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
}

/** The boss wind-up warning. Red, unmissable, and early enough to physically respond to. */
@Composable
fun UltimateWarning(
    visible: Boolean,
    repsLeft: Int,
    answers: Int,
    answersNeeded: Int,
    playerClass: PlayerClass,
    exercise: ExerciseType,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(240)),
        modifier = modifier,
    ) {
        // What to do, in the player's own style, and how much room is left to do it — a wind-up
        // with no instruction is only a threat. A mage is told where to hold in *this* movement:
        // "at the bottom" means nothing to someone doing pull-ups, whose hard point is the top.
        val how = when {
            Exercises.of(exercise).kind == MovementKind.HOLD ->
                stringResource(R.string.battle_ultimate_how_hold, answersNeeded)
            playerClass == PlayerClass.ARCHER -> stringResource(R.string.battle_ultimate_how_archer, answersNeeded)
            playerClass == PlayerClass.MAGE -> stringResource(
                R.string.battle_ultimate_how_mage,
                answersNeeded,
                stringResource(mageHoldPointRes(exercise)),
            )
            else -> stringResource(R.string.battle_ultimate_how_knight, answersNeeded)
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Palette.Danger)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.battle_ultimate_incoming),
                style = Type.titleL,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = how,
                style = Type.labelL,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.battle_ultimate_progress, repsLeft, answers, answersNeeded),
                style = Type.labelM,
                color = Palette.TextPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Where a mage holds in [exercise]: the movement's hardest point, which is the detector's "deep"
 * end — the bottom of a pushup, a squat, a lunge or a dip, and the top of a pull-up.
 */
@StringRes
fun mageHoldPointRes(exercise: ExerciseType): Int = when (exercise) {
    ExerciseType.PUSHUP -> R.string.mage_hold_pushup
    ExerciseType.SQUAT -> R.string.mage_hold_squat
    ExerciseType.LUNGE -> R.string.mage_hold_lunge
    ExerciseType.PULL_UP -> R.string.mage_hold_pull_up
    ExerciseType.DIP -> R.string.mage_hold_dip
    ExerciseType.PLANK -> R.string.mage_hold_plank
}
