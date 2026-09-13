package com.pushuprpg.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.progression.Rank
import com.pushuprpg.core.progression.RankProgress

/** The one card radius the hub screens use, so nothing drifts by a few dp between screens. */
val CardShape: Shape = RoundedCornerShape(20.dp)

/**
 * Card chrome as a modifier rather than a wrapper composable, so callers stay free to be a Row, a
 * Column or a clickable — every hub surface is a different shape of the same card.
 */
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
        Text(text = text, style = Type.titleM, maxLines = 1)
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
