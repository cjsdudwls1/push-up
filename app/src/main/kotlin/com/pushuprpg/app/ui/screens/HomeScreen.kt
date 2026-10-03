package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.progression.StreakState
import com.pushuprpg.core.survival.CatSession
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
 * One question leads it — "am I doing this today?" — and one button answers it: 고양이 지키기, the
 * mode the tutorial already taught, which starts the movement it names at once; the picker is the
 * link under it, since the movement is the same one day after another. Under that, the answer to
 * the other question a habit needs,
 * "am I getting anywhere": the calories every rep so far has burned, the best set against the first,
 * and the week at a glance. The cat says how long it has been, glad whatever the answer.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    /** Starts 고양이 지켜줘 with [lastExercise], the movement the button names. */
    onPlayCat: () -> Unit,
    /** The picker, for another movement. */
    onChangeExercise: () -> Unit,
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
    val colors = LocalGameColors.current
    val name = catName.ifBlank { stringResource(R.string.cat_default_name) }
    val exerciseName = stringResource(exerciseLabelRes(lastExercise))

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreakChip(days = state.streakShown)
            Spacer(Modifier.weight(1f))
            ThemeToggle(mode = themeMode, onToggle = onToggleTheme)
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
            } else {
                welcomeText(state.welcome, monday = Weeks.mondayOf(state.today) == state.today)
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp),
        )
        // The cat is the way into 꾸미기: tapped, or by the pill beside it that counts what is new.
        val openWardrobe = stringResource(R.string.home_wardrobe_open)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            CatPortrait(
                coat = catCoat,
                wear = catWear,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClickLabel = openWardrobe, onClick = onWardrobe),
            )
            WardrobePill(
                newGifts = state.newGifts,
                onClick = onWardrobe,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_today_reps, state.todayReps),
            style = Type.headline,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // The nudge only when there is something to nudge about. Saying it every day would make the
        // encouragement worthless, and saying it after a plank tells someone who held one for
        // minutes that they have not started. A day begun short of the bar says what is left of it.
        //
        // A broken streak is never said, by the owner's decision: pointing at what was lost is how
        // a return turns into a goodbye. The cat says it is glad instead, and the chip shows what
        // the streak goes on from.
        val notStarted = state.todayReps == 0 && state.todayActiveMs == 0L
        val left = state.leftToday(lastExercise)
        if (notStarted || left > 0) {
            // The bar quoted is the real one, for the movement the user reaches for.
            val bar = Exercises.of(lastExercise)
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    notStarted && bar.kind == MovementKind.HOLD -> stringResource(R.string.home_nudge_hold, exerciseName, bar.streakBar)
                    notStarted -> stringResource(R.string.home_nudge, exerciseName, bar.streakBar)
                    state.streakShown == 0 && bar.kind == MovementKind.HOLD ->
                        stringResource(R.string.home_left_first_hold, exerciseName, left)
                    state.streakShown == 0 -> stringResource(R.string.home_left_first, exerciseName, left)
                    bar.kind == MovementKind.HOLD -> stringResource(R.string.home_left_hold, exerciseName, left)
                    else -> stringResource(R.string.home_left, exerciseName, left)
                },
                style = Type.bodyL,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = stringResource(R.string.home_cat_play),
            supportingText = stringResource(R.string.home_cat_play_sub, CatSession.LIVES),
            onClick = onPlayCat,
        )
        Spacer(Modifier.height(6.dp))
        val changeLabel = stringResource(R.string.home_change_exercise_label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .clip(CircleShape)
                .background(Palette.Bg2)
                .border(1.dp, Palette.StrokeSoft, CircleShape)
                .clickable(onClickLabel = changeLabel, role = Role.Button, onClick = onChangeExercise)
                .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = exerciseName,
                style = Type.labelL,
                color = Palette.TextPrimary,
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Palette.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        // The start of a week, until its first workout: how the last one went, once, and only if
        // there was anything in it — an empty week is not summed up as a row of zeros.
        val recap = state.lastWeekRecap
        if (recap != null && !recap.empty && state.thisWeek.activeDays == 0) {
            Spacer(Modifier.height(16.dp))
            RecapCard(recap = recap)
        }

        Spacer(Modifier.height(22.dp))
        CalorieCard(
            progress = state.burn,
            exercise = lastExercise,
            modifier = Modifier.clickable(onClick = onRecords),
        )

        // Every movement's best, swiped sideways, the one picked last first; a tap opens the records,
        // which is the only way in now that the button at the bottom is gone.
        val records = remember(state.records, lastExercise) {
            state.records.values.sortedWith(
                compareByDescending<MovementRecord> { it.exercise == lastExercise }.thenByDescending { it.total }
            )
        }
        if (records.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RecordPager(records = records, onClick = onRecords)
        }

        Spacer(Modifier.height(12.dp))
        WeekCard(thisWeek = state.thisWeek, lastWeek = state.lastWeek, today = state.today)
    }
}

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
 * Every movement's best one go, a card each, swiped sideways, by the owner's decision: one card for
 * the movement picked last left the others' records a screen away. The next card peeks in at the
 * edge, which is what says the row moves, and the dots say how many there are. A tap on any card
 * opens the records.
 */
@Composable
private fun RecordPager(records: List<MovementRecord>, onClick: () -> Unit) {
    val pager = rememberPagerState(pageCount = { records.size })
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = if (records.size > 1) 40.dp else 0.dp),
            pageSpacing = 10.dp,
            verticalAlignment = Alignment.Top,
            key = { records[it].exercise.name },
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            RecordCard(record = records[page], onClick = onClick)
        }
        if (records.size > 1) {
            Spacer(Modifier.height(8.dp))
            PageDots(
                count = records.size,
                current = pager.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Which of [count] cards is showing: the current one filled, the rest faint. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = LocalGameColors.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i == current) colors.accept else Palette.Bg3),
            )
        }
    }
}

/**
 * One movement's best one go, and how far it has come from the first: the number that says the body
 * is changing, where the calories say the work is adding up.
 */
@Composable
private fun RecordCard(record: MovementRecord, onClick: () -> Unit) {
    val colors = LocalGameColors.current
    val hold = Exercises.of(record.exercise).kind == MovementKind.HOLD
    val exerciseName = stringResource(exerciseLabelRes(record.exercise))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .clickable(onClickLabel = stringResource(R.string.action_view_records), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_record_title),
                style = Type.labelL,
                color = Palette.TextTertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (hold) R.string.home_record_value_hold else R.string.home_record_value,
                    exerciseName,
                    record.best,
                ),
                style = Type.titleM,
                color = Palette.TextPrimary,
            )
        }
        if (record.gain > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    if (hold) R.string.home_record_gain_hold else R.string.home_record_gain,
                    record.gain,
                ),
                style = Type.labelL,
                color = colors.accept,
            )
        }
    }
}

/**
 * The week at a glance: its days by name, lit on the days that had any work, and one line on how it
 * stands against last week — said only as a lead, never as a shortfall.
 */
@Composable
private fun WeekCard(thisWeek: WeekSummary, lastWeek: WeekSummary, today: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.week_title),
                style = Type.titleM,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.week_totals, thisWeek.activeDays, thisWeek.reps),
                style = Type.labelL,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        WeekDays(
            days = thisWeek.days,
            todayIndex = (today - thisWeek.start).toInt(),
            size = 32.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                lastWeek.reps > 0 && thisWeek.reps > lastWeek.reps ->
                    stringResource(R.string.week_ahead, thisWeek.reps - lastWeek.reps)
                lastWeek.activeDays > 0 -> stringResource(R.string.week_last, lastWeek.activeDays, lastWeek.reps)
                thisWeek.activeDays == 0 -> stringResource(R.string.week_empty)
                else -> stringResource(R.string.week_first)
            },
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, worked ->
            val isToday = i == todayIndex
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (worked) colors.accept else Palette.Bg3)
                    .then(if (isToday && !worked) Modifier.border(2.dp, colors.accept, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labels.getOrElse(i) { "" },
                    style = Type.labelM,
                    color = when {
                        worked -> Palette.TextOnAccent
                        isToday -> Palette.TextPrimary
                        else -> Palette.TextTertiary
                    },
                )
            }
        }
    }
}

/**
 * The pill over the cat that opens 꾸미기: plain, or lit with how many gifts are new. A new gift is
 * a surprise waiting, so it is shown and counted, and never called overdue.
 */
@Composable
private fun WardrobePill(newGifts: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fresh = newGifts > 0
    Text(
        text = if (fresh) stringResource(R.string.home_wardrobe_new, newGifts) else stringResource(R.string.home_wardrobe),
        style = Type.labelL,
        color = if (fresh) Palette.TextOnBrand else Palette.TextSecondary,
        maxLines = 1,
        // Opaque either way: it sits over the cat's picture, and over a scene when there is one.
        modifier = modifier
            .clip(CircleShape)
            .background(if (fresh) Palette.Brand600 else Palette.Bg2)
            .border(1.dp, if (fresh) Palette.Brand600 else Palette.StrokeSoft, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

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
