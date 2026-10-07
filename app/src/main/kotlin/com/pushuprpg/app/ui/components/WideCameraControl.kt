package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import java.util.Locale

/**
 * 광각으로 찍기 on the run screen, by the owner's request (2026-10-07): a button over the camera that
 * opens a switch and, while it is on, a slider for how wide. The slider moves the open camera's zoom
 * as it is dragged and is saved when it is let go, so a drag is one write to the settings.
 *
 * [least] is the widest the open camera allows, null until it has opened; [zoom] is 0 for as wide as
 * it goes, otherwise the ratio picked.
 */
@Composable
fun WideCameraControl(
    on: Boolean,
    zoom: Float,
    least: Float?,
    onToggle: (Boolean) -> Unit,
    /** While the slider is dragged: the picture follows without anything being saved. */
    onZoomPreview: (Float) -> Unit,
    onZoomPicked: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val supported = least != null && least < SUPPORTED_BELOW
    // What 0 means on this phone, once it is known; until then the slider is not shown.
    val shown = if (zoom <= 0f || least == null) least ?: 1f else zoom.coerceIn(least, 1f)

    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = if (on && supported) stringResource(R.string.survival_wide_at, ratioLabel(shown))
            else stringResource(R.string.survival_wide),
            style = Type.labelL,
            color = if (on) Palette.TextPrimary else Palette.TextSecondary,
            modifier = Modifier
                .height(48.dp)
                .clip(CircleShape)
                .background(Palette.ScrimPanelHigh)
                .clickable { open = !open }
                .padding(horizontal = 18.dp, vertical = 13.dp),
        )
        if (open) {
            Spacer(Modifier.height(8.dp))
            WidePanel(on, shown, least, supported, onToggle, onZoomPreview, onZoomPicked)
        }
    }
}

@Composable
private fun WidePanel(
    on: Boolean,
    shown: Float,
    least: Float?,
    supported: Boolean,
    onToggle: (Boolean) -> Unit,
    onZoomPreview: (Float) -> Unit,
    onZoomPicked: (Float) -> Unit,
) {
    val colors = LocalGameColors.current
    // The thumb follows the finger; the setting is written once, when it is let go.
    var dragging by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(shown) }
    val value = if (dragging) draft else shown

    Column(
        Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.ScrimPanelHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.clickable { onToggle(!on) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_wide_camera),
                style = Type.bodyL,
                color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = on,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accept),
            )
        }
        when {
            !on || least == null -> Unit
            !supported -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.survival_wide_unsupported),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                )
            }
            else -> {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ratioLabel(least), style = Type.bodyM, color = Palette.TextSecondary)
                    Slider(
                        value = value,
                        onValueChange = {
                            dragging = true
                            draft = it
                            onZoomPreview(it)
                        },
                        onValueChangeFinished = {
                            dragging = false
                            // The far end is saved as "as wide as it goes", which stays right on
                            // another phone whose widest is not this one's.
                            onZoomPicked(if (draft - least < SNAP_TO_LEAST) 0f else draft)
                        },
                        valueRange = least..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.accept,
                            activeTrackColor = colors.accept,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(ratioLabel(1f), style = Type.bodyM, color = Palette.TextSecondary)
                }
                Text(
                    text = stringResource(R.string.survival_wide_hint),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ratioLabel(ratio: Float): String =
    stringResource(R.string.survival_wide_ratio, String.format(Locale.ROOT, "%.1f", ratio))

/** A camera whose widest is 0.95x or more is not offered a slider: there is nothing to slide. */
private const val SUPPORTED_BELOW = 0.95f

/** Let go this close to the widest, the pick is the widest. */
private const val SNAP_TO_LEAST = 0.02f
