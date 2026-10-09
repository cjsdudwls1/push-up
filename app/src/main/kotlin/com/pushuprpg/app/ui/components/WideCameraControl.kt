package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import java.util.Locale

/**
 * 광각으로 찍기 on the run screen: one button over the camera that turns it on and off, by the owner's
 * request (2026-10-09 — a button that opened a panel holding a switch was one step too many). On, it
 * films as wide as the camera goes and says how wide that is once the camera has opened.
 *
 * [least] is the widest the open camera allows, null until it has opened.
 */
@Composable
fun WideCameraControl(
    on: Boolean,
    least: Float?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val supported = least != null && least < SUPPORTED_BELOW
    Text(
        text = if (on && supported) {
            stringResource(
                R.string.survival_wide_at,
                stringResource(R.string.survival_wide_ratio, String.format(Locale.ROOT, "%.1f", least)),
            )
        } else {
            stringResource(R.string.survival_wide)
        },
        style = Type.labelL,
        color = if (on) Palette.TextPrimary else Palette.TextSecondary,
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (on) LocalGameColors.current.accept.copy(alpha = 0.55f) else Palette.ScrimPanelHigh)
            .clickable(role = Role.Switch) { onToggle(!on) }
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

/** A camera whose widest is 0.95x or more is not labelled with a ratio: it is not any wider. */
private const val SUPPORTED_BELOW = 0.95f
