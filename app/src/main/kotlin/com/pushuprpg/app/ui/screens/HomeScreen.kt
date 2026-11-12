package com.pushuprpg.app.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.pushuprpg.app.ui.theme.PixelCircle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.pushuprpg.app.ui.theme.PixelShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.ui.components.*
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.BurnProgress
import com.pushuprpg.core.progression.Calories
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.MovementRecord
import com.pushuprpg.core.progression.SessionTrend
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.progression.StreakState
import com.pushuprpg.core.progression.Weeks
import com.pushuprpg.core.progression.Welcome
import com.pushuprpg.core.progression.WeekRecap
import com.pushuprpg.core.progression.WeekSummary

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
    /** Every rep so far, as calories burned and the food they add up to. */
    val burn: BurnProgress = Calories.progress(0f),
    /** Each movement's best one go and first one, for how far it has come. */
    val records: Map<ExerciseType, MovementRecord> = emptyMap(),
    /** Every movement's sessions as totals, for the graph; one per movement, done or not. */
    val trends: Map<ExerciseType, SessionTrend> = emptyMap(),
    val thisWeek: WeekSummary = EMPTY_WEEK,
    val lastWeek: WeekSummary = EMPTY_WEEK,
    /** Last week summed up, for the start of this one; null until loaded. */
    val lastWeekRecap: WeekRecap? = null,
    /** The day of the last banked run, or null before the first. */
    val lastWorkoutDay: Long? = null,
    /** Every gift the cat has found. */
    val gifts: Set<Gift> = emptySet(),
) {
    private val streak: StreakState
        get() = StreakState(progress.streakDays, progress.lastActiveEpochDay)

    /**
     * The streak as it stands today. The stored number is only rewritten when a day meets the bar,
     * so after a missed day it would still show the streak that was broken.
     */
    val streakShown: Int
        get() = Streak.shown(streak, today)

    /** What the cat says when the hub opens, by how long it has been since the last workout. */
    val welcome: Welcome
        get() = Welcome.of(lastWorkoutDay, today)

    /** Gifts found and not yet looked at in 꾸미기. */
    val newGifts: Int
        get() = gifts.count { it.name !in progress.giftsSeen }

    /** How much more of [exercise] keeps the streak today; zero once today has kept it. */
    fun leftToday(exercise: ExerciseType): Int = Streak.leftOn(streak, today, todayWork, exercise)
}

private val EMPTY_WEEK = WeekSummary(start = 0, days = List(7) { false }, reps = 0, activeMs = 0)

/**
 * The hub, built around the cat.
 *
 * One question leads it — "am I doing this today?" — and the dock pinned at the bottom answers it:
 * a chip per movement and one big button that says only the movement it starts (2026-11-11, the
 * owner: "바로 운동 종목명만 크게"; the picker screen is gone, the hub is the picker). The chip picked
 * drives the ring round the cat. Nothing else is under the cat (2026-11-11, by the owner's decision:
 * the graph, the week, the records and the calories were cards nobody would look at). The records
 * are one tap away in the top bar, and a record nearly reached or broken is told in the run itself.
 * The cat says how long it has been, glad whatever the answer.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    /** Starts 고양이 지켜줘 with the movement the button names. */
    onPlayCat: (ExerciseType) -> Unit,
    /** A chip was picked: remembered as the last choice. */
    onSelectExercise: (ExerciseType) -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    /** 꾸미기: the cat's name, coat and what it has found. */
    onWardrobe: () -> Unit,
    /** The movement picked last: whose bar the nudge quotes, whose record leads, what the button plays. */
    lastExercise: ExerciseType,
    catName: String,
    catCoat: CatCoat,
    catWear: Set<CatItem>,
    modifier: Modifier = Modifier,
) {
    val name = catName.ifBlank { stringResource(R.string.cat_default_name) }
    // Plain remember, not saveable or keyed on [lastExercise]: the stored choice arrives late, and a
    // chip tapped twice quickly would jump back. Coming back from a run builds the hub anew from it.
    var selected by remember { mutableStateOf(lastExercise) }
    val exerciseName = stringResource(exerciseLabelRes(selected))

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .bottomFade()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                // Room at the foot for the fade: it ends clear of the buttons, not under them.
                .padding(top = 20.dp, bottom = 40.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StreakChip(days = state.streakShown)
                Spacer(Modifier.weight(1f))
                // The way into 꾸미기 besides the cat itself, lit with how many finds are new.
                WardrobePill(newGifts = state.newGifts, onClick = onWardrobe)
                // The records where the theme was (it is in the settings, and not an everyday switch).
                Spacer(Modifier.width(8.dp))
                RoundIconButton(icon = PixelIcons.Bars, label = stringResource(R.string.action_view_records), onClick = onRecords)
                // Up here beside the theme, by the owner's decision: the bottom of the hub had a row of
                // two buttons under everything else, and 설정 is not something to scroll for.
                Spacer(Modifier.width(8.dp))
                SettingsButton(onClick = onSettings)
            }

            Spacer(Modifier.height(12.dp))
            CatSpeechBubble(
                name = name,
                // Once today's workout is in, a find not yet looked at is the news; a return is said first.
                text = if (state.newGifts > 0 && state.welcome == Welcome.Today) {
                    stringResource(R.string.home_welcome_gift)
                } else if (state.welcome == Welcome.Today) {
                    // Three ways to say thanks, a different one each day, so it is not the same line
                    // every time the hub opens.
                    when ((state.today % 3).toInt()) {
                        0 -> stringResource(R.string.home_welcome_today)
                        1 -> if (state.streakShown >= 2) {
                            stringResource(R.string.home_welcome_today_streak, state.streakShown)
                        } else {
                            stringResource(R.string.home_welcome_today_2)
                        }
                        else -> stringResource(R.string.home_welcome_today_2)
                    }
                } else {
                    welcomeText(state.welcome, monday = Weeks.mondayOf(state.today) == state.today)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 12.dp),
            )

            // The day's bar for the movement the button plays, as a ring round the cat (concept C,
            // 2026-10-09): full once today has kept the streak, whichever movement kept it.
            val bar = Exercises.of(selected)
            val left = state.leftToday(selected)
            TodayRing(
                fraction = if (left == 0) 1f else (bar.streakBar - left).toFloat() / bar.streakBar,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                val catDescription = stringResource(R.string.home_cat_description, name)
                CatPortrait(
                    coat = catCoat,
                    wear = catWear,
                    modifier = Modifier
                        .size(width = 148.dp, height = 104.dp)
                        .clickable(
                            onClickLabel = stringResource(R.string.home_wardrobe_open),
                            role = Role.Button,
                            onClick = onWardrobe,
                        )
                        .semantics { contentDescription = catDescription },
                )
                if (left > 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${bar.streakBar - left}",
                            style = Type.displayM,
                            color = Palette.TextPrimary,
                        )
                        Text(
                            text = " / ${bar.streakBar}",
                            style = Type.numeralM,
                            color = Palette.TextTertiary,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text = if (bar.kind == MovementKind.HOLD) {
                            stringResource(R.string.home_ring_caption_hold, exerciseName)
                        } else {
                            stringResource(R.string.home_ring_caption, exerciseName)
                        },
                        style = Type.labelL,
                        color = Palette.TextSecondary,
                    )
                } else {
                    // A day of only planks has no reps to count, so it says the time instead of 0개.
                    val seconds = (state.todayActiveMs / 1_000).toInt()
                    Text(
                        text = if (state.todayReps == 0 && seconds > 0) {
                            stringResource(R.string.home_today_time, seconds / 60, seconds % 60)
                        } else {
                            stringResource(R.string.home_today_reps, state.todayReps)
                        },
                        style = Type.titleL,
                        color = Palette.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.home_ring_done),
                        style = Type.labelL,
                        color = Palette.Brand400,
                    )
                }
            }

            // What is left of today's bar, once a day is under way. Before anything is done the cat's line
            // and the ring's 0 / 15 already say it, so a third sentence saying the same was dropped.
            //
            // A broken streak is never said, by the owner's decision: pointing at what was lost is how
            // a return turns into a goodbye. The cat says it is glad instead, and the chip shows what
            // the streak goes on from.
            val notStarted = state.todayReps == 0 && state.todayActiveMs == 0L
            if (!notStarted && left > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        state.streakShown == 0 && bar.kind == MovementKind.HOLD ->
                            stringResource(R.string.home_left_first_hold, exerciseName, left)
                        state.streakShown == 0 -> stringResource(R.string.home_left_first, exerciseName, left)
                        bar.kind == MovementKind.HOLD -> stringResource(R.string.home_left_hold, exerciseName, left)
                        else -> stringResource(R.string.home_left, exerciseName, left)
                    },
                    style = Type.labelL,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // The start of a week, until its first workout: how the last one went, once, and only if
            // there was anything in it — an empty week is not summed up as a row of zeros.
            val recap = state.lastWeekRecap
            if (recap != null && !recap.empty && state.thisWeek.activeDays == 0) {
                Spacer(Modifier.height(16.dp))
                RecapCard(recap = recap)
            }
        }

        // Pinned under everything else so it never scrolls away: which movement, and go.
        StartDock(
            selected = selected,
            onSelect = {
                selected = it
                onSelectExercise(it)
            },
            onStart = { onPlayCat(selected) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 16.dp),
        )
    }
}

/**
 * The scroll's bottom edge fades out in four steps over [FADE] rather than being cut off under the
 * buttons: a card going under them reads as more below, not as a card cut in half. Faded by alpha,
 * so it works over the moving stars as well as over a plain ground.
 */
private fun Modifier.bottomFade(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val band = FADE.toPx() / FADE_STEPS
        for (step in 0 until FADE_STEPS) {
            val keep = 1f - (step + 1f) / (FADE_STEPS + 1f)
            drawRect(
                Color.Black.copy(alpha = keep),
                topLeft = Offset(0f, size.height - FADE.toPx() + step * band),
                size = Size(size.width, band),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private val FADE = 28.dp
private const val FADE_STEPS = 4

/**
 * What the cat says as the hub opens. Glad whatever the gap, and more so the longer it was: a week
 * away is said gently, a month away asks for just one easy go. A Monday is a fresh start, and is
 * said as one — unless it has been a week or more, which the return itself says better.
 */
@Composable
private fun welcomeText(welcome: Welcome, monday: Boolean): String = when (welcome) {
    Welcome.First -> stringResource(R.string.home_welcome_first)
    Welcome.Today -> stringResource(R.string.home_welcome_today)
    Welcome.Yesterday ->
        if (monday) stringResource(R.string.home_welcome_monday) else stringResource(R.string.home_welcome_yesterday)
    is Welcome.Back -> when {
        welcome.days >= 30 -> stringResource(R.string.home_welcome_back_month, welcome.days)
        welcome.days >= 7 -> stringResource(R.string.home_welcome_back_week, welcome.days)
        monday -> stringResource(R.string.home_welcome_monday)
        else -> stringResource(R.string.home_welcome_back, welcome.days)
    }
}

/**
 * A week as its days, 월 to 일, each a circle with its name in it: filled on a day with any work,
 * ringed if it is today and nothing is done yet. Named rather than bare dots, which only someone who
 * built the card could read.
 */
@Composable
private fun WeekDays(
    days: List<Boolean>,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Which of the seven is today, if the week is this one. */
    todayIndex: Int = -1,
) {
    val colors = LocalGameColors.current
    val labels = stringArrayResource(R.array.weekday_short)
    // Dark text on the bright greens and blue of the dark theme, light text on the deep ones of the
    // light theme: dark on the light theme's green was 3.6:1, white on it is 5.4:1.
    val onAccept = if (colors.accept.luminance() > 0.25f) Palette.TextOnAccent else Color.White
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, worked ->
            val isToday = i == todayIndex
            Box(
                modifier = Modifier
                    .size(size)
                    .background(if (worked) colors.accept else Palette.Bg3, PixelCircle)
                    .then(if (isToday && !worked) Modifier.border(2.dp, colors.accept, PixelCircle) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labels.getOrElse(i) { "" },
                    style = Type.labelM,
                    color = when {
                        worked -> onAccept
                        isToday -> Palette.TextPrimary
                        else -> Palette.TextTertiary
                    },
                )
            }
        }
    }
}

/**
 * The pill at the top that opens 꾸미기: plain, or lit with how many gifts are new. A new gift is a
 * surprise waiting, so it is shown and counted, and never called overdue.
 */
@Composable
private fun WardrobePill(newGifts: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fresh = newGifts > 0
    Text(
        text = if (fresh) stringResource(R.string.home_wardrobe_new, newGifts) else stringResource(R.string.home_wardrobe),
        style = Type.labelL,
        color = if (fresh) Palette.OnCta else Palette.TextSecondary,
        maxLines = 1,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (fresh) Palette.Cta else Palette.Bg2, PixelCircle)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 14.dp),
    )
}

/**
 * A ring round [content] that fills with [fraction] of the day's bar: the hub's one gauge. The
 * track is always drawn, so an empty day reads as a ring to fill rather than as nothing.
 */
@Composable
private fun TodayRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val track = Palette.Bg3
    val fill = Palette.Brand400
    val inner = Palette.Bg2
    Box(modifier.size(224.dp), contentAlignment = Alignment.Center) {
        // The ring as pixel art (2026-11-10, the pixel look): cells on a 34-cell grid, two cells
        // thick, filled clockwise from the top; the inside a disc of the same cells.
        Canvas(Modifier.matchParentSize()) {
            val n = 34
            val cell = floor(size.minDimension / n)
            val origin = (size.minDimension - cell * n) / 2f
            val mid = n / 2f
            val outer = mid
            val ringIn = mid - 2.2f
            val disc = mid - 3.4f
            val done = fraction.coerceIn(0f, 1f)
            val gap = 1f.coerceAtMost(cell / 8f)
            for (i in 0 until n) for (j in 0 until n) {
                val dx = i + 0.5f - mid
                val dy = j + 0.5f - mid
                val d = sqrt(dx * dx + dy * dy)
                val color = when {
                    d <= disc -> inner
                    d in ringIn..outer -> {
                        // Clockwise from twelve o'clock, as a fraction of the way round.
                        val turn = ((atan2(dx, -dy) / (2f * PI.toFloat())) + 1f) % 1f
                        if (turn < done) fill else track
                    }
                    else -> continue
                }
                val inset = if (d <= disc) 0f else gap
                drawRect(
                    color,
                    topLeft = Offset(origin + i * cell + inset, origin + j * cell + inset),
                    size = Size(cell - 2 * inset, cell - 2 * inset),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * The bottom of the hub (2026-11-11, with Fable's review): the movement chips, the picked one's camera
 * line, and the start button. Choosing and starting are two separate taps, with the hint line between
 * them, so a chip tapped a little low does not start a set.
 */
@Composable
private fun StartDock(
    selected: ExerciseType,
    onSelect: (ExerciseType) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(exerciseLabelRes(selected))
    Column(modifier.semantics { isTraversalGroup = true }) {
        ExerciseChips(selected = selected, onSelect = onSelect)
        // Held at two lines' height, so switching movements does not make the dock jump.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            ExerciseNotes(selected)
        }
        Spacer(Modifier.height(4.dp))
        StartButton(
            text = name,
            description = stringResource(R.string.home_start_description, name),
            onClick = onStart,
        )
    }
}

/**
 * The start button: the movement's name, big, and nothing else, by the owner's request. A full-width
 * pixel slab in the call-to-action colour on its own darker edge, 6dp lower, so it reads as a key to
 * press; an outline steps out from it a size at a time, [RING_STEPS] sizes [RING_STEP_MS] apart, like
 * a charging shot. Under reduced motion it holds one faint outline still.
 */
@Composable
private fun StartButton(text: String, description: String, onClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val step = remember { mutableIntStateOf(0) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(RING_STEP_MS)
                step.intValue = (step.intValue + 1) % RING_STEPS
            }
        }
    }
    val cta = Palette.Cta
    val edge = Palette.CtaEdge
    val on = Palette.OnCta
    val shape = PixelShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BUTTON_H + REACH * 2)
            .drawWithCache {
                val stroke = Stroke(2.dp.toPx())
                val reach = REACH.toPx()
                val faceW = size.width - 2 * reach
                val faceH = (BUTTON_H - EDGE).toPx()
                fun outline(grow: Float) = grow to shape.createOutline(Size(faceW + 2 * grow, faceH + 2 * grow), layoutDirection, this)
                val moving = List(RING_STEPS) { outline(reach * (it + 1f) / RING_STEPS) }
                val still = outline(reach * 0.6f)
                onDrawBehind {
                    val (grow, o) = if (reduceMotion) still else moving[step.intValue]
                    val alpha = if (reduceMotion) 0.3f else 0.55f * (1f - step.intValue / RING_STEPS.toFloat())
                    translate(reach - grow, reach - grow) { drawOutline(o, cta.copy(alpha = alpha), style = stroke) }
                }
            }
            .padding(REACH)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.TopCenter,
    ) {
        // The edge: the face's own shape, lower by [EDGE], so only its bottom shows.
        Spacer(
            Modifier
                .padding(top = EDGE)
                .fillMaxWidth()
                .height(BUTTON_H - EDGE)
                .background(edge, shape),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(BUTTON_H - EDGE)
                .background(cta, shape),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PixelIcon(sprite = PixelIcons.Play, tint = on, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = Type.headline, color = on, maxLines = 1)
        }
    }
}

private val BUTTON_H = 72.dp
private val EDGE = 6.dp
private val REACH = 6.dp
private const val RING_STEPS = 5
private const val RING_STEP_MS = 400L

/**
 * Last week, summed up at the start of this one: its days, its reps, the calories it burned and the
 * records it broke. Shown until the first workout of the new week, which is the moment it is for.
 */
@Composable
private fun RecapCard(recap: WeekRecap) {
    val week = recap.summary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(color = Palette.BrandWash)
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.recap_title),
            style = Type.titleM,
            color = Palette.TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        WeekDays(days = week.days, size = 28.dp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.recap_totals, week.activeDays, week.reps),
            style = Type.bodyL,
            color = Palette.TextPrimary,
        )
        if (recap.kcal > 0f) {
            Text(
                text = stringResource(R.string.recap_burned, kcalText(recap.kcal)),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }
        if (recap.records > 0) {
            Text(
                text = stringResource(R.string.recap_records, recap.records),
                style = Type.bodyM,
                color = Palette.Deep,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.recap_next),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
    }
}
