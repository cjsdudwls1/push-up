package com.pushuprpg.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type

/**
 * Said over a camera screen whose camera would not open.
 *
 * Another app holding the camera, or a policy turning it off, used to leave a black preview with
 * nothing on it, which reads as the app being broken rather than as something the user can fix.
 * The retry binds the camera again; one that opens by itself takes the card away too.
 */
@Composable
fun CameraErrorCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(22.dp), color = Palette.Bg1)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.error_camera),
            style = Type.bodyL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(text = stringResource(R.string.error_camera_retry), onClick = onRetry)
    }
}
