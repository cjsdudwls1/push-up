package com.pushuprpg.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Climb
import com.pushuprpg.core.progression.ClimbProgress
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.Landmark
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.survival.CatSession
import java.util.Locale

/** A place on the climb, by name. */
@StringRes
fun landmarkNameRes(landmark: Landmark): Int = when (landmark) {
    Landmark.CAT_TOWER -> R.string.landmark_cat_tower
    Landmark.APARTMENT_5F -> R.string.landmark_apartment_5f
    Landmark.APARTMENT_15F -> R.string.landmark_apartment_15f
    Landmark.LIBERTY -> R.string.landmark_liberty
    Landmark.SIXTY_THREE -> R.string.landmark_sixty_three
    Landmark.LOTTE_TOWER -> R.string.landmark_lotte_tower
    Landmark.BUKHANSAN -> R.string.landmark_bukhansan
    Landmark.HALLASAN -> R.string.landmark_hallasan
    Landmark.BAEKDUSAN -> R.string.landmark_baekdusan
    Landmark.FUJI -> R.string.landmark_fuji
    Landmark.KILIMANJARO -> R.string.landmark_kilimanjaro
    Landmark.EVEREST -> R.string.landmark_everest
    Landmark.SPACE -> R.string.landmark_space
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
}

/** What a gift was found for, said after the fact. */
@Composable
fun giftWhyText(gift: Gift): String = when (gift.kind) {
    Gift.Kind.FIRST_RUN -> stringResource(R.string.gift_why_first_run)
    Gift.Kind.SET -> stringResource(R.string.gift_why_set, gift.count)
    Gift.Kind.STREAK -> stringResource(R.string.gift_why_streak, gift.count)
    Gift.Kind.COMEBACK -> stringResource(R.string.gift_why_comeback)
    Gift.Kind.FULL_SESSION -> stringResource(R.string.gift_why_full_session, CatSession.LIVES)
    Gift.Kind.RECORDS -> stringResource(R.string.gift_why_records, gift.count)
    Gift.Kind.CLIMB -> gift.place?.let { stringResource(R.string.gift_why_climb, stringResource(landmarkNameRes(it))) }.orEmpty()
}

/**
 * A height as people say it: 2.1m under ten metres, whole metres with separators after, and
 * kilometres past ten of them.
 */
@Composable
fun metersText(meters: Float): String = when {
    meters >= 10_000f -> stringResource(R.string.climb_km, String.format(Locale.KOREA, "%.1f", meters / 1_000f))
    meters >= 10f -> stringResource(R.string.climb_m, String.format(Locale.KOREA, "%,d", meters.toInt()))
    else -> stringResource(R.string.climb_m, String.format(Locale.KOREA, "%.1f", meters))
}

/**
 * The climb: how high every rep so far has lifted the body, the place it last passed, and how many
 * of [exercise] reach the next one.
 *
 * It is the answer to "am I getting anywhere", which a lifetime count of reps gives only to someone
 * who knows what a count means. A height does not need explaining.
 */
@Composable
fun ClimbCard(progress: ClimbProgress, exercise: ExerciseType, modifier: Modifier = Modifier) {
    val colors = LocalGameColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.climb_title),
            style = Type.labelL,
            color = Palette.TextTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = metersText(progress.meters),
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            val reached = progress.reached
            Text(
                text = if (reached == null) {
                    stringResource(R.string.climb_ground)
                } else {
                    stringResource(R.string.climb_reached, stringResource(landmarkNameRes(reached)))
                },
                style = Type.bodyM,
                color = colors.accept,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, bottom = 4.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        ProgressTrack(fraction = progress.fraction, color = colors.accept)
        Spacer(Modifier.height(10.dp))
        val next = progress.next
        Text(
            text = if (next == null) {
                stringResource(R.string.climb_top)
            } else {
                stringResource(
                    R.string.climb_next,
                    stringResource(landmarkNameRes(next)),
                    metersText(progress.metersToNext),
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
                    if (hold) R.string.climb_next_seconds else R.string.climb_next_reps,
                    stringResource(exerciseLabelRes(exercise)),
                    Climb.toNext(progress, exercise),
                ),
                style = Type.bodyM,
                color = Palette.TextTertiary,
            )
        }
    }
}

/**
 * What one run did for the climb and the records, said where the run ends: a record broken, the
 * metres it added, the places it passed, and what the cat found. Nothing when it has not been
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
        if (growth.climbed > 0f) {
            Text(
                text = stringResource(R.string.growth_climbed, metersText(growth.climbed)),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        growth.passed.forEach { place ->
            Text(
                text = stringResource(R.string.growth_passed, stringResource(landmarkNameRes(place))),
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
