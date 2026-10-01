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
import com.pushuprpg.core.progression.BurnProgress
import com.pushuprpg.core.progression.Calories
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Food
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.survival.CatSession
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
    CatItem.HEADBAND -> R.string.item_headband
    CatItem.BANDANA -> R.string.item_bandana
    CatItem.HEART_GLASSES -> R.string.item_heart_glasses
    CatItem.CHEF_HAT -> R.string.item_chef_hat
    CatItem.MEDAL -> R.string.item_medal
    CatItem.WITCH_HAT -> R.string.item_witch_hat
    CatItem.BEACH -> R.string.item_beach
    CatItem.GYM -> R.string.item_gym
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
    Gift.Kind.BURN -> gift.food?.let { stringResource(R.string.gift_why_burn, stringResource(foodNameRes(it))) }.orEmpty()
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
