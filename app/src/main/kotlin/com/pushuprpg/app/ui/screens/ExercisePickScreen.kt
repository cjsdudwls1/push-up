package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.components.CatCard
import com.pushuprpg.app.ui.components.ExerciseNotes
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.survival.CatSession

/**
 * Picks the movement for 고냥이 지켜줘.
 *
 * A run has one movement, and it is declared, not inferred. An early build worked the exercise out
 * from the camera: it identified the movement correctly and was still wrong, because nine detectors
 * raced and only the winner's reps reached the game — measured at 0 of 6 pull-ups for someone who
 * went straight from the floor to the bar.
 *
 * It is reached from the hub's 운동 바꾸기: the hub's own button starts the movement picked last, by
 * the owner's decision, since it is the same one day after another. Choosing here is done standing in
 * front of the phone, which is the moment the one-line camera placement is worth reading; the rest of
 * the placement is said live, once the phone is down. The last choice is pre-expanded, so the same
 * movement as yesterday is one tap and no reading. The cat can be named and coloured here as well as
 * in 꾸미기.
 */
@Composable
fun ExercisePickScreen(
    initial: ExerciseType,
    onStart: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier,
    catName: String = "",
    catCoat: CatCoat = CatCoat.CREAM,
    /** The cat's name, as it should be stored, and its coat. Called on every change. */
    onCatChange: (String, CatCoat) -> Unit = { _, _ -> },
    /** What the cat has on, for its picture beside the name. */
    catWear: Set<CatItem> = emptySet(),
) {
    var expanded by remember { mutableStateOf(initial) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.survival_title),
                style = Type.labelL,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.pick_survival_title),
                style = Type.headline,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.pick_survival_sub, CatSession.LIVES),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
        }

        item(key = "cat") {
            CatCard(name = catName, coat = catCoat, onChange = onCatChange, wear = catWear)
            Spacer(Modifier.height(6.dp))
        }

        items(ExerciseType.entries, key = { it.name }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                expanded = exercise == expanded,
                isLast = exercise == initial,
                onExpand = { expanded = exercise },
                onStart = { onStart(exercise) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseType,
    expanded: Boolean,
    isLast: Boolean,
    onExpand: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalGameColors.current
    val descriptor = Exercises.of(exercise)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(
                shape = RoundedCornerShape(18.dp),
                color = if (expanded) Palette.Bg3 else Palette.Bg1,
            )
            // Tapping a collapsed row opens it rather than starting the run. Six movements on one
            // screen means a mis-tap is likely, and a mis-tap that begins a set with the wrong
            // detector loaded costs the whole set.
            .clickable { if (expanded) onStart() else onExpand() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(exerciseLabelRes(exercise)),
                style = Type.bodyL,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // A plank is counted in seconds, not reps, and that changes what the screen will show
            // during the run — worth saying before the choice, not after.
            if (descriptor.kind == MovementKind.HOLD) {
                Pill(text = stringResource(R.string.pick_exercise_hold), tint = Palette.TextSecondary)
                Spacer(Modifier.width(6.dp))
            }
            if (isLast) {
                Pill(text = stringResource(R.string.pick_exercise_last), tint = colors.accept)
            }
        }

        if (expanded) {
            ExerciseNotes(exercise)
            Spacer(Modifier.height(12.dp))
            // The app's one start button, not a lookalike drawn for this card.
            PrimaryButton(text = stringResource(R.string.pick_exercise_start), onClick = onStart)
        }
    }
}
