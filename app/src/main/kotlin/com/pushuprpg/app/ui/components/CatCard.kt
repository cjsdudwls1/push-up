package com.pushuprpg.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.survival.CatName

/**
 * 우리 고양이: a name and a coat, on the survival picker on the way in and in 꾸미기.
 *
 * The field keeps its own text while the user types and hands each change on; reading it back from
 * the store instead would round-trip every keystroke through DataStore and jump the cursor.
 *
 * [wear] dresses the picture beside the name. With [showCat] false there is no picture, for a
 * screen that already shows the cat large.
 */
@Composable
fun CatCard(
    name: String,
    coat: CatCoat,
    onChange: (String, CatCoat) -> Unit,
    modifier: Modifier = Modifier,
    wear: Set<CatItem> = emptySet(),
    showCat: Boolean = true,
) {
    var typed by remember { mutableStateOf(name) }
    var picked by remember { mutableStateOf(coat) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(18.dp), color = Palette.Bg1)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showCat) {
                Canvas(Modifier.size(88.dp)) {
                    drawCat(
                        centerX = size.width * 0.42f,
                        baseY = size.height * 0.96f,
                        scale = size.minDimension / 132f,
                        coat = picked,
                        wear = wear,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
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

@StringRes
fun coatLabelRes(coat: CatCoat): Int = when (coat) {
    CatCoat.CREAM -> R.string.cat_coat_cream
    CatCoat.CHEESE -> R.string.cat_coat_cheese
    CatCoat.MACKEREL -> R.string.cat_coat_mackerel
    CatCoat.TUXEDO -> R.string.cat_coat_tuxedo
    CatCoat.CALICO -> R.string.cat_coat_calico
}
