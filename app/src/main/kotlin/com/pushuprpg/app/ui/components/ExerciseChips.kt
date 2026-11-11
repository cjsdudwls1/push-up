package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.PixelCircle
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises

/**
 * One chip per movement, the picked one filled in the call-to-action colour and underlined, so it is
 * told by more than colour. Shared out across the row when the five fit; at a large font, where they
 * do not, the row scrolls sideways rather than squeezing a name.
 */
@Composable
fun ExerciseChips(
    selected: ExerciseType,
    onSelect: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val count = ExerciseType.entries.size
        val fits = maxWidth >= (CHIP_MIN * LocalDensity.current.fontScale) * count + GAP * (count - 1)
        Row(
            horizontalArrangement = Arrangement.spacedBy(GAP),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                .selectableGroup(),
        ) {
            ExerciseType.entries.forEach { exercise ->
                val active = exercise == selected
                val label = stringResource(exerciseLabelRes(exercise))
                val described = if (Exercises.of(exercise).validatedOnDevice) {
                    label
                } else {
                    stringResource(R.string.home_exercise_chip_unvalidated, label)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .then(if (fits) Modifier.weight(1f) else Modifier.widthIn(min = CHIP_MIN))
                        .background(if (active) Palette.Cta else Palette.Bg3, PixelCircle)
                        .selectable(selected = active, role = Role.RadioButton) { onSelect(exercise) }
                        .semantics { contentDescription = described }
                        // A touch target: a full 48dp tall.
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = label,
                        style = Type.labelM,
                        color = if (active) Palette.OnCta else Palette.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Spacer(
                        Modifier
                            .width(16.dp)
                            .height(3.dp)
                            .background(if (active) Palette.OnCta else Color.Transparent),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

private val CHIP_MIN = 58.dp
private val GAP = 6.dp
