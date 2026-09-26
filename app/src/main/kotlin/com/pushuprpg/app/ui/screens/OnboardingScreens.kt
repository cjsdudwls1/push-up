package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.ThemeToggle
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass

/**
 * One screen, one idea, one button. A carousel here would only lose people before the workout.
 *
 * The theme toggle is the exception, and it sits in a corner rather than in the flow: this is the
 * first screen anyone sees, so it is where the look should be choosable, but it is not a question
 * anybody has to answer before starting.
 */
@Composable
fun OnboardingScreen(
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = Type.headline,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.onboarding_body),
                style = Type.bodyL,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            // Before the first workout, not buried in settings: an app that tells people to exercise
            // says once, up front, that it is not medical advice and to stop if something hurts. In
            // body type and colour: as 13sp tertiary grey it read as fine print, and faintly at that.
            Text(
                text = stringResource(R.string.onboarding_health),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.action_start), onClick = onContinue)
        }
        ThemeToggle(
            mode = themeMode,
            onToggle = onToggleTheme,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp),
        )
    }
}

/** A class's name and its one line about the training it rewards, as string resources. */
internal fun classStrings(playerClass: PlayerClass): Pair<Int, Int> = when (playerClass) {
    PlayerClass.KNIGHT -> R.string.class_knight to R.string.class_knight_desc
    PlayerClass.ARCHER -> R.string.class_archer to R.string.class_archer_desc
}

/**
 * Class selection.
 *
 * The cards describe a way of training rather than a stat spread, because that is what the choice
 * actually is — the knight (근비대) is for people who like slow, deep reps, the archer (수행능력)
 * for people who like pace. There are two; the mage was folded into the knight. Framing it as
 * numbers would make it a min-max puzzle instead of a question about their own body.
 *
 * The same screen changes the class later: pass the player's [current] class and it is marked,
 * and the copy says what a change keeps. Null means onboarding, where nothing has been chosen.
 * A change costs nothing: level, XP and records belong to the player rather than the class. What
 * it changes is the fight — each class's [PlayerClass.repCostScale] sets how many reps a monster
 * costs, and [com.pushuprpg.core.game.ClassStyle] which reps are whole.
 *
 * Each card also says what the first dungeon asks with that class, at [difficulty], because that
 * is what the choice decides first: the same dungeon is more than twice as many pushups for one as
 * for the other, and that was said nowhere before the first fight.
 */
@Composable
fun ClassPickScreen(
    capacity: Float,
    /** The difficulty the first dungeon's count on each card is quoted at. */
    difficulty: Difficulty,
    onPick: (PlayerClass) -> Unit,
    modifier: Modifier = Modifier,
    current: PlayerClass? = null,
) {
    val colors = LocalGameColors.current
    // Fewer, slower reps a fight: the gentler start for someone with few reps in them yet.
    val recommended = if (capacity < 8f) PlayerClass.KNIGHT else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 60.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(
                if (current == null) R.string.class_pick_title else R.string.class_change_title
            ),
            style = Type.headline,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (current == null) R.string.class_pick_body else R.string.class_change_body
            ),
            style = Type.bodyL,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(22.dp))

        PlayerClass.entries.forEach { playerClass ->
            val (nameRes, descRes) = classStrings(playerClass)
            val isCurrent = playerClass == current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .cardSurface(
                        shape = RoundedCornerShape(18.dp),
                        borderColor = if (isCurrent) Palette.Brand500 else Palette.StrokeSoft,
                    )
                    .clickable { onPick(playerClass) }
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(nameRes),
                        style = Type.titleL,
                        color = Palette.TextPrimary,
                    )
                    // One pill per card. Where you are now outranks a recommendation.
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Pill(text = stringResource(R.string.class_current), tint = Palette.Brand400)
                    } else if (playerClass == recommended) {
                        Spacer(Modifier.width(8.dp))
                        Pill(text = stringResource(R.string.difficulty_recommended), tint = colors.accept)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(descRes),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                // The count the run will ask for, from the call every screen that quotes one uses.
                Text(
                    text = stringResource(
                        R.string.class_first_dungeon,
                        Dungeons.FREE_DUNGEON.repCost(difficulty, ExerciseType.PUSHUP, playerClass),
                    ),
                    style = Type.labelL,
                    color = Palette.TextPrimary,
                )
            }
        }
    }
}

/**
 * Camera rationale.
 *
 * The privacy line is the loudest thing here after the headline. It is the biggest objection a
 * camera fitness app faces, it happens to be entirely true — inference is on-device and nothing
 * leaves the phone — and saying it plainly before asking converts far better than a policy link.
 */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_headline),
            style = Type.headline,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.permission_privacy),
            style = Type.titleM,
            color = colors.accept,
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(shape = RoundedCornerShape(18.dp), color = colors.acceptDim)
                .padding(18.dp),
        )
        if (permanentlyDenied) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_denied_body),
                style = Type.bodyL,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        if (permanentlyDenied) {
            PrimaryButton(
                text = stringResource(R.string.permission_open_settings),
                onClick = onOpenAppSettings,
            )
            // Asking stays. From Android 11 a dialog closed by a tap beside it reads exactly like
            // 다시 묻지 않음, and the system would still show it again. Where it truly will not, the
            // refusal comes straight back and this screen stays as it is.
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = stringResource(R.string.permission_grant),
                onClick = onRequestPermission,
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.permission_grant),
                onClick = onRequestPermission,
            )
        }
    }
}
