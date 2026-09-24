package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.BodySide

/**
 * Which leg goes forward on the next lunge. Large, because it is read from across the room between
 * reps; and heard as well, through the voice. Nothing before the first rep.
 */
@Composable
fun NextLegChip(next: BodySide?, modifier: Modifier = Modifier) {
    if (next == null) return
    val leg = stringResource(if (next == BodySide.LEFT) R.string.leg_left else R.string.leg_right)
    Text(
        text = stringResource(R.string.next_leg, leg),
        style = Type.titleM,
        color = Palette.TextPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.ScrimPanelHigh)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
