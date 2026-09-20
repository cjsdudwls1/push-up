package com.pushuprpg.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
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
    onNextDungeon: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onRecords: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val rank = RankProgress.of(lifetimeReps)

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
            text = if (outcome.cleared) {
                stringResource(R.string.result_cleared_sub, dungeonName)
            } else {
                stringResource(R.string.result_defeat_sub)
            },
            style = Type.bodyL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))
        StarRow(outcome.stars)

        Spacer(Modifier.height(18.dp))
        ReassuranceBanner(reps = outcome.reps)

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultTile(
                value = outcome.reps.toString(),
                label = stringResource(R.string.result_tile_reps),
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
        RankCardLocal(rank = rank)

        // Losing still leaves a mark on the enemy, and saying so turns a failed attempt into
        // visible progress rather than a wasted one.
        if (!outcome.cleared && outcome.crackFraction > 0f) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.result_crack),
                style = Type.bodyM,
                color = colors.deep,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))
        if (outcome.cleared && hasNextDungeon) {
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

/** The line that has to arrive before the verdict does. */
@Composable
private fun ReassuranceBanner(reps: Int) {
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
            text = stringResource(R.string.result_banner, reps),
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

@Composable
private fun RankCardLocal(rank: RankProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Bg2)
            .padding(16.dp),
    ) {
        Text(text = rank.rank.korean, style = Type.titleL, color = Palette.TierLegend)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { rank.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Palette.TierLegend,
            trackColor = Palette.Bg3,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = rank.next?.let {
                stringResource(R.string.result_rank_to_next, it.korean, rank.repsToNext)
            } ?: stringResource(R.string.rank_max),
            style = Type.labelM,
            color = Palette.TextTertiary,
        )
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
private fun StarRow(stars: Stars) {
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
                    Stars.ONE -> R.string.result_stars_one
                }
            ),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
