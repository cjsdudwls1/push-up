package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.components.ExerciseNotes
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.drawCat
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.game.Dungeon
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.survival.CatName

/**
 * Picks the movement for one dungeon run, on the way in.
 *
 * This replaces both a settings-screen picker and, before that, a build that tried to work the
 * exercise out from the camera. The camera version identified the movement correctly and was still
 * wrong: nine detectors raced and only the winner's reps reached the game, so every rep done before
 * the race resolved was discarded — measured at 0 of 6 pull-ups for someone who went straight from
 * the floor to the bar. A run has one movement. It is declared, not inferred.
 *
 * Entry is also simply the right place to ask. In settings the choice was made minutes before it
 * mattered and then silently applied to every later run; here it is made while the user is standing
 * in front of the phone deciding what they are about to do, which is the moment the camera-placement
 * line and the load warning are worth reading.
 *
 * One tap starts the run. The last choice is pre-expanded so the common case — the same movement as
 * yesterday — is that one tap and no reading.
 *
 * With [survival] it picks the movement for 고냥이 지켜줘 instead: no dungeon, so no cost to quote —
 * and it is where the cat is named and coloured, on the way in, which is the moment it matters.
 */
@Composable
fun ExercisePickScreen(
    /** The run being entered: its name, and the cost each row quotes. */
    dungeon: Dungeon?,
    initial: ExerciseType,
    onStart: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier,
    difficulty: Difficulty = Difficulty.STANDARD,
    survival: Boolean = false,
    catName: String = "",
    catCoat: CatCoat = CatCoat.CREAM,
    /** The cat's name, as it should be stored, and its coat. Called on every change. */
    onCatChange: (String, CatCoat) -> Unit = { _, _ -> },
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
                text = if (survival) stringResource(R.string.survival_title) else dungeon?.korean.orEmpty(),
                style = Type.labelL,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (survival) R.string.pick_survival_title else R.string.pick_exercise_title),
                style = Type.headline,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(if (survival) R.string.pick_survival_sub else R.string.pick_exercise_sub),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
        }

        if (survival) {
            item(key = "cat") {
                CatCard(name = catName, coat = catCoat, onChange = onCatChange)
                Spacer(Modifier.height(6.dp))
            }
        }

        items(ExerciseType.entries, key = { it.name }) { exercise ->
            ExerciseRow(
                exercise = exercise,
                expanded = exercise == expanded,
                isLast = exercise == initial,
                // Exact, not an estimate: under the volume model the enemy's health IS this count,
                // and Dungeon.repCost is the same function the run itself is priced by — so this is
                // the number of reps the user will actually perform, not a rounding of it.
                cost = dungeon?.repCost(difficulty, exercise) ?: 0,
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
    cost: Int,
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
            // Tapping a collapsed row opens it rather than starting the run. Nine movements on one
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
                Spacer(Modifier.width(6.dp))
            }
            if (cost > 0) {
                Text(
                    text = stringResource(
                        if (descriptor.kind == MovementKind.HOLD) R.string.pick_exercise_cost_seconds
                        else R.string.pick_exercise_cost_reps,
                        cost,
                    ),
                    style = Type.labelL,
                    color = Palette.TextPrimary,
                )
            }
        }

        if (expanded) {
            ExerciseNotes(exercise)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.pick_exercise_start),
                style = Type.labelL,
                color = Palette.TextOnBrand,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(shape = RoundedCornerShape(14.dp), color = Palette.Brand600)
                    .clickable(onClick = onStart)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

/**
 * 우리 고양이: a name and a coat.
 *
 * The field keeps its own text while the user types and hands each change on; reading it back from
 * the store instead would round-trip every keystroke through DataStore and jump the cursor.
 */
@Composable
private fun CatCard(name: String, coat: CatCoat, onChange: (String, CatCoat) -> Unit) {
    var typed by remember { mutableStateOf(name) }
    var picked by remember { mutableStateOf(coat) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(18.dp), color = Palette.Bg1)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(88.dp)) {
                drawCat(
                    centerX = size.width * 0.42f,
                    baseY = size.height * 0.96f,
                    scale = size.minDimension / 132f,
                    coat = picked,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cat_card_title),
                    style = Type.labelL,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { input ->
                        typed = CatName.clean(input)
                        onChange(CatName.finished(typed), picked)
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cat_name_label)) },
                    placeholder = { Text(stringResource(R.string.cat_default_name)) },
                    textStyle = Type.bodyL,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.cat_card_sub),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CatCoat.entries.forEach { option ->
                CoatChip(
                    coat = option,
                    selected = option == picked,
                    onClick = {
                        picked = option
                        onChange(CatName.finished(typed), option)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CoatChip(coat: CatCoat, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .cardSurface(shape = shape, color = if (selected) Palette.Bg3 else Palette.Bg2)
            .then(if (selected) Modifier.border(2.dp, Palette.Brand600, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(44.dp)) {
            drawCat(
                centerX = size.width * 0.42f,
                baseY = size.height * 0.97f,
                scale = size.minDimension / 132f,
                coat = coat,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(coatLabelRes(coat)),
            style = Type.labelM,
            color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun coatLabelRes(coat: CatCoat): Int = when (coat) {
    CatCoat.CREAM -> R.string.cat_coat_cream
    CatCoat.CHEESE -> R.string.cat_coat_cheese
    CatCoat.MACKEREL -> R.string.cat_coat_mackerel
    CatCoat.TUXEDO -> R.string.cat_coat_tuxedo
    CatCoat.CALICO -> R.string.cat_coat_calico
}
