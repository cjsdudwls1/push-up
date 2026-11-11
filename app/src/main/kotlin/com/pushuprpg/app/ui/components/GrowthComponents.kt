package com.pushuprpg.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import com.pushuprpg.app.ui.theme.PixelCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.progression.BurnProgress
import com.pushuprpg.core.progression.Calories
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Food
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.progression.Gifts
import com.pushuprpg.core.progression.SessionTrend
import java.util.Locale

/** A food on the calorie ladder, by name. */
@StringRes
fun foodNameRes(food: Food): Int = when (food) {
    Food.BLUEBERRY -> R.string.food_blueberry
    Food.CHERRY_TOMATO -> R.string.food_cherry_tomato
    Food.CANDY -> R.string.food_candy
    Food.BANANA -> R.string.food_banana
    Food.CHOCO_PIE -> R.string.food_choco_pie
    Food.RICE -> R.string.food_rice
    Food.RAMEN -> R.string.food_ramen
    Food.JJAJANGMYEON -> R.string.food_jjajangmyeon
    Food.CHICKEN -> R.string.food_chicken
    Food.FIVE_CHICKENS -> R.string.food_five_chickens
    Food.RAMEN_BOX -> R.string.food_ramen_box
    Food.RICE_SACK -> R.string.food_rice_sack
    Food.RICE_BALE -> R.string.food_rice_bale
}

/** One of the cat's things, by name. */
@StringRes
fun itemNameRes(item: CatItem): Int = when (item) {
    CatItem.BELL -> R.string.item_bell
    CatItem.RIBBON -> R.string.item_ribbon
    CatItem.FLOWER -> R.string.item_flower
    CatItem.CITY -> R.string.item_city
    CatItem.BEANIE -> R.string.item_beanie
    CatItem.STRAW_HAT -> R.string.item_straw_hat
    CatItem.SCARF -> R.string.item_scarf
    CatItem.BOW_TIE -> R.string.item_bow_tie
    CatItem.GLASSES -> R.string.item_glasses
    CatItem.SUNGLASSES -> R.string.item_sunglasses
    CatItem.MOUNTAIN -> R.string.item_mountain
    CatItem.PARTY_HAT -> R.string.item_party_hat
    CatItem.CROWN -> R.string.item_crown
    CatItem.SNOW -> R.string.item_snow
    CatItem.SPACE -> R.string.item_space
    CatItem.YARN -> R.string.item_yarn
    CatItem.CAP -> R.string.item_cap
    CatItem.FISH_TOY -> R.string.item_fish_toy
    CatItem.WHISTLE -> R.string.item_whistle
    CatItem.PIRATE_HAT -> R.string.item_pirate_hat
    CatItem.GOGGLES -> R.string.item_goggles
    CatItem.FLOWER_CROWN -> R.string.item_flower_crown
    CatItem.BLUSH -> R.string.item_blush
    CatItem.NECKTIE -> R.string.item_necktie
    CatItem.TROPHY -> R.string.item_trophy
    CatItem.PEARLS -> R.string.item_pearls
    CatItem.GRAD_CAP -> R.string.item_grad_cap
    CatItem.AURORA -> R.string.item_aurora
    CatItem.CHERRY_BLOSSOM -> R.string.item_cherry_blossom
    CatItem.MONOCLE -> R.string.item_monocle
    CatItem.HALO -> R.string.item_halo
    CatItem.UNDERSEA -> R.string.item_undersea
    CatItem.HEADBAND -> R.string.item_headband
    CatItem.BANDANA -> R.string.item_bandana
    CatItem.HEART_GLASSES -> R.string.item_heart_glasses
    CatItem.CHEF_HAT -> R.string.item_chef_hat
    CatItem.MEDAL -> R.string.item_medal
    CatItem.WITCH_HAT -> R.string.item_witch_hat
    CatItem.BEACH -> R.string.item_beach
    CatItem.GYM -> R.string.item_gym
    CatItem.BALL -> R.string.item_ball
    CatItem.MUSTACHE -> R.string.item_mustache
    CatItem.MOUSE_TOY -> R.string.item_mouse_toy
    CatItem.SPORTS_TOWEL -> R.string.item_sports_towel
    CatItem.LEI -> R.string.item_lei
    CatItem.BOX -> R.string.item_box
    CatItem.STAR_GLASSES -> R.string.item_star_glasses
    CatItem.FROG_HAT -> R.string.item_frog_hat
    CatItem.HEADPHONES -> R.string.item_headphones
    CatItem.PARK -> R.string.item_park
    CatItem.CAT_TOWER -> R.string.item_cat_tower
    CatItem.STAGE -> R.string.item_stage
}

/** What a gift was found for, said after the fact. */
@Composable
fun giftWhyText(gift: Gift): String = when (gift.kind) {
    Gift.Kind.FIRST_RUN -> stringResource(R.string.gift_why_first_run)
    Gift.Kind.SET -> stringResource(R.string.gift_why_set, gift.count)
    Gift.Kind.STREAK -> stringResource(R.string.gift_why_streak, gift.count)
    Gift.Kind.COMEBACK -> stringResource(R.string.gift_why_comeback)
    Gift.Kind.FULL_SESSION -> stringResource(R.string.gift_why_full_session, Gifts.FULL_SESSION_SETS)
    Gift.Kind.RECORDS -> stringResource(R.string.gift_why_records, gift.count)
    Gift.Kind.BURN -> gift.food?.let { stringResource(R.string.gift_why_burn, stringResource(foodNameRes(it))) }.orEmpty()
    Gift.Kind.RUNS -> stringResource(R.string.gift_why_runs, gift.count)
    Gift.Kind.MOVEMENTS -> stringResource(R.string.gift_why_movements, gift.count)
}

/** Calories as people say them: 1.9kcal under ten, whole ones with separators after. */
@Composable
fun kcalText(kcal: Float): String = when {
    kcal >= 10f -> stringResource(R.string.burn_kcal, String.format(Locale.KOREA, "%,d", kcal.toInt()))
    else -> stringResource(R.string.burn_kcal, String.format(Locale.KOREA, "%.1f", kcal))
}

/**
 * The calories every rep so far has burned, the biggest food they add up to, and how many of
 * [exercise] burn off the next one.
 *
 * It is the answer to "am I getting anywhere", which a lifetime count of reps gives only to someone
 * who knows what a count means. A meal does not need explaining — by the owner's decision, after a
 * height did not land. It says it is an estimate, because the app does not know the user's weight.
 */
@Composable
fun CalorieCard(progress: BurnProgress, exercise: ExerciseType, modifier: Modifier = Modifier) {
    val colors = LocalGameColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.burn_title),
            style = Type.labelL,
            color = Palette.TextTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = kcalText(progress.kcal),
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            val reached = progress.reached
            Text(
                text = if (reached == null) {
                    stringResource(R.string.burn_none)
                } else {
                    stringResource(R.string.burn_reached, stringResource(foodNameRes(reached)))
                },
                style = Type.bodyM,
                color = colors.accept,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, bottom = 4.dp),
                textAlign = TextAlign.End,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(12.dp))
        ProgressTrack(fraction = progress.fraction, color = colors.accept)
        Spacer(Modifier.height(10.dp))
        val next = progress.next
        Text(
            text = if (next == null) {
                stringResource(R.string.burn_top, stringResource(foodNameRes(Food.entries.last())))
            } else {
                stringResource(
                    R.string.burn_next,
                    stringResource(foodNameRes(next)),
                    kcalText(progress.kcalToNext),
                )
            },
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
        if (next != null) {
            Spacer(Modifier.height(2.dp))
            val hold = Exercises.of(exercise).kind == MovementKind.HOLD
            Text(
                text = stringResource(
                    if (hold) R.string.burn_next_seconds else R.string.burn_next_reps,
                    stringResource(exerciseLabelRes(exercise)),
                    Calories.toNext(progress, exercise),
                ),
                style = Type.bodyM,
                color = Palette.TextTertiary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.burn_estimate, Calories.REFERENCE_KG.toInt()),
            style = Type.labelS,
            color = Palette.TextTertiary,
        )
    }
}

/**
 * What one run did for the calories and the records, said where the run ends: a record broken, the
 * calories it burned, the foods they passed, and what the cat found. Nothing when it has not been
 * banked yet.
 */
@Composable
fun RunGrowthLines(growth: RunGrowth?, modifier: Modifier = Modifier) {
    if (growth == null) return
    val colors = LocalGameColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        growth.records.forEach { record ->
            val hold = Exercises.of(record.exercise).kind == MovementKind.HOLD
            Text(
                text = stringResource(
                    if (hold) R.string.growth_record_hold else R.string.growth_record,
                    stringResource(exerciseLabelRes(record.exercise)),
                    record.now,
                    record.previous,
                ),
                style = Type.titleM,
                color = Palette.Deep,
                textAlign = TextAlign.Center,
            )
        }
        if (growth.burned > 0f) {
            Text(
                text = stringResource(R.string.growth_burned, kcalText(growth.burned)),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        growth.passed.forEach { food ->
            Text(
                text = stringResource(R.string.growth_food, stringResource(foodNameRes(food))),
                style = Type.bodyL,
                color = colors.accept,
                textAlign = TextAlign.Center,
            )
        }
        if (growth.gifts.isNotEmpty()) {
            val names = growth.gifts.map { stringResource(itemNameRes(it.item)) }
            Text(
                text = stringResource(R.string.growth_gift, names.joinToString(", ")),
                style = Type.titleM,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Each session's total, one movement at a time, as a line: the first thing under the hub's button,
 * by the owner's request (2026-11-06), with the calories under it. The five movements are chips
 * across the top and one tap switches the graph — no screen to open, no menu to find.
 *
 * A session is a 고양이 session of up to ten sets; its total is every rep in it, or every second held
 * for a plank ([com.pushuprpg.core.progression.Trends]). A rise is coloured; a dip is drawn as it was
 * and said in grey — the graph is there to show how it is going, not to scold.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionTrendCard(
    trends: Map<ExerciseType, SessionTrend>,
    selected: ExerciseType,
    onSelect: ((ExerciseType) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val trend = trends[selected] ?: SessionTrend(selected, emptyList(), 0)
    val hold = Exercises.of(selected).kind == MovementKind.HOLD
    val exerciseName = stringResource(exerciseLabelRes(selected))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.trend_title, exerciseName),
            style = Type.labelL,
            color = Palette.TextTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        // Its own chips only where nothing else picks the movement: on the hub, the dock's chips do.
        if (onSelect != null) {
            ExerciseChips(selected = selected, onSelect = onSelect)
        }
        Spacer(Modifier.height(16.dp))

        val latest = trend.latest
        if (latest == null) {
            Text(
                text = stringResource(R.string.trend_empty, exerciseName),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        // Wraps rather than crowds: at a large font the change goes under the number instead of
        // pushing it off the card.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(if (hold) R.string.trend_value_hold else R.string.trend_value, latest),
                style = Type.numeralL,
                color = Palette.TextPrimary,
                modifier = Modifier.align(Alignment.Bottom),
            )
            Text(
                text = stringResource(R.string.trend_latest_label),
                style = Type.bodyM,
                color = Palette.TextTertiary,
                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 5.dp),
            )
            Spacer(Modifier.weight(1f))
            val change = trend.change
            Text(
                text = when {
                    change == null -> stringResource(R.string.trend_first)
                    change > 0 -> stringResource(if (hold) R.string.trend_up_hold else R.string.trend_up, change)
                    // A drop reads as what last time was, not as a minus.
                    change < 0 -> stringResource(if (hold) R.string.trend_down_hold else R.string.trend_down, latest - change)
                    else -> stringResource(R.string.trend_same)
                },
                style = Type.labelL,
                color = if (change != null && change > 0) colors.accept else Palette.TextSecondary,
                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        TrendLine(
            points = trend.points,
            // The brand colour as text: cyan on dark, violet on light, readable on either.
            line = Palette.Brand400,
            label = stringResource(
                R.string.trend_graph_label,
                exerciseName,
                trend.points.joinToString(", "),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.trend_axis_sessions, trend.points.size),
                style = Type.labelS,
                color = Palette.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.trend_axis_latest),
                style = Type.labelS,
                color = Palette.TextTertiary,
            )
        }
    }
}

/**
 * The line itself: one dot a session, each with its total over it, the latest one filled. The range
 * hugs the values with a margin rather than starting at zero, so a session of 42 after one of 38 is
 * a visible step — every dot is labelled, so nothing is exaggerated by it.
 */
@Composable
private fun TrendLine(points: List<Int>, line: Color, label: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val valueStyle = Type.labelM.copy(color = Palette.TextSecondary)
    val latestStyle = Type.labelM.copy(color = Palette.TextPrimary)
    val grid = Palette.StrokeSoft
    val dotHole = Palette.Bg2
    Canvas(modifier.semantics { contentDescription = label }) {
        val top = 22.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val side = 14.dp.toPx()
        val lo = points.min()
        val hi = points.max()
        val margin = maxOf((hi - lo) * 0.2f, 2f)
        val min = (lo - margin).coerceAtLeast(0f)
        val max = hi + margin
        fun x(i: Int) = if (points.size == 1) size.width / 2f else side + (size.width - 2 * side) * i / (points.size - 1)
        fun y(v: Int) = bottom - (bottom - top) * (v - min) / (max - min)

        // A faint floor and a midline: enough to read the slope against, not a chart's worth of grid.
        listOf(bottom, (top + bottom) / 2f).forEach { gy ->
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        if (points.size > 1) {
            val path = Path().apply {
                points.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            // The area under the line, faintly, so the shape reads at a glance.
            val area = Path().apply {
                addPath(path)
                lineTo(x(points.lastIndex), bottom)
                lineTo(x(0), bottom)
                close()
            }
            drawPath(area, line.copy(alpha = 0.12f))
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Square, join = StrokeJoin.Miter))
        }
        points.forEachIndexed { i, v ->
            val last = i == points.lastIndex
            val c = Offset(x(i), y(v))
            // Square points, as pixel art has them.
            val r = (if (last) 6.dp else 4.5.dp).toPx()
            drawRect(line, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r))
            val hole = 2.dp.toPx()
            if (!last) drawRect(dotHole, topLeft = Offset(c.x - hole, c.y - hole), size = Size(2 * hole, 2 * hole))
            val text = measurer.measure(v.toString(), if (last) latestStyle else valueStyle)
            val tx = (c.x - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(tx, c.y - 10.dp.toPx() - text.size.height))
        }
    }
}
