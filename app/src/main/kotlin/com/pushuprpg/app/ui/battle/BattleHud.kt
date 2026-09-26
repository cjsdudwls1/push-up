package com.pushuprpg.app.ui.battle

import androidx.annotation.StringRes
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.ExerciseType
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
    /** Laid out on one line however wide it is, for a caller that fits it with [onTextLayout]. */
    singleLine: Boolean = false,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val maxLines = if (singleLine) 1 else Int.MAX_VALUE
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
                    softWrap = !singleLine,
                    maxLines = maxLines,
                    modifier = Modifier.offset(x = dx.dp, y = dy.dp),
                )
            }
            Text(
                text = text,
                style = style,
                color = color,
                softWrap = !singleLine,
                maxLines = maxLines,
                onTextLayout = onTextLayout,
            )
        }

        Legibility.PANEL -> Text(
            text = text,
            style = style,
            color = color,
            softWrap = !singleLine,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.ScrimPanel)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Legibility.SCRIM -> Text(
            text = text,
            style = style,
            color = color,
            softWrap = !singleLine,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            modifier = modifier,
        )
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
    /** The label's type: the monster's count is set larger, to be read from across the room. */
    labelStyle: TextStyle = Type.numeralM,
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
        // Nothing here under bodyM, the floor for text over the camera: a squat or a pull-up reads
        // it from two or three metres away.
        CameraText(text = name, style = Type.bodyM, color = Palette.TextSecondary)
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
        // One line, at whatever size fits it, as a result tile's value is: at the monster's size
        // 남은 120초 is wider than half a narrow phone, and wrapped it would stand the HUD a line
        // taller, and the gauge under it, down onto the counter. The line height stays, and the
        // figures are tabular, so a fit holds until the count gains or loses a digit.
        val text = label ?: "${format.format(hp)} / ${format.format(maxHp)}"
        var scale by remember(text.length) { mutableFloatStateOf(1f) }
        var fitted by remember(text.length) { mutableStateOf(false) }
        CameraText(
            text = text,
            style = labelStyle.copy(fontSize = labelStyle.fontSize * scale),
            color = Palette.TextPrimary,
            singleLine = true,
            onTextLayout = { layout ->
                if (layout.didOverflowWidth && scale > MIN_LABEL_SCALE) scale *= 0.9f else fitted = true
            },
            modifier = Modifier.drawWithContent { if (fitted) drawContent() },
        )
    }
}

/** The smallest a bar's label is shrunk to fit, against its own size. */
private const val MIN_LABEL_SCALE = 0.6f

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
        CameraText(text = stringResource(R.string.battle_hp_label), style = Type.bodyM, color = Palette.TextSecondary)
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
        CameraText(text = hp.toString(), style = Type.bodyM, color = Palette.TextSecondary)
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
 * The rep counter — or, for a hold, the seconds held, in [unitRes].
 *
 * Placed at the vertical centre of the screen rather than at the bottom, where the demo had it. At
 * the bottom of a pushup the user's own shoulders occlude the lower band of the screen and their
 * viewing angle foreshortens it badly — which is precisely the moment they most want to see the
 * number go up.
 */
@Composable
fun RepCounter(
    count: Int,
    @StringRes unitRes: Int,
    dimmed: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.alpha(if (dimmed) 0.35f else 1f),
        verticalAlignment = Alignment.Bottom,
    ) {
        CameraText(
            text = count.toString(),
            style = if (compact) Type.heroCountSmall else Type.heroCount,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.width(6.dp))
        CameraText(
            text = stringResource(unitRes),
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
    /** A short screen, where the warning keeps to two lines. */
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(240)),
        modifier = modifier,
    ) {
        // What to do, in the player's own style, and how much room is left to do it — a wind-up
        // with no instruction is only a threat. A short screen keeps the title and the count and
        // leaves the instruction to the voice, which says it as the wind-up starts: with a third
        // line the panel stood up over the counter, and the instruction is the line that wraps.
        val how = when {
            compact -> null
            Exercises.of(exercise).kind == MovementKind.HOLD ->
                stringResource(R.string.battle_ultimate_how_hold, answersNeeded)
            playerClass == PlayerClass.ARCHER -> stringResource(R.string.battle_ultimate_how_archer, answersNeeded)
            else -> stringResource(R.string.battle_ultimate_how_knight, answersNeeded)
        }
        // Dark ink on the bright red, about 6:1. White on it was under 3:1, and this is the line
        // read from the floor at an angle with a hit coming; the red stays bright, since it is also
        // what catches the eye mid-rep.
        val ink = Palette.TextOnAccent
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
                color = ink,
                textAlign = TextAlign.Center,
            )
            if (how != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = how,
                    style = Type.bodyL,
                    color = ink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.battle_ultimate_progress, repsLeft, answers, answersNeeded),
                style = Type.bodyM,
                color = ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

