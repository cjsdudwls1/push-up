package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import kotlinx.coroutines.delay

/**
 * The pose model's error, along the foot of the screen on a fixed dark scrim that runs under the
 * navigation bar, with the line above it.
 *
 * One place for it, because it is said from two: over whichever screen is up when the model
 * reports an error, and by a camera screen whose model has gone quiet ([rememberModelStalled]).
 */
@Composable
fun ModelErrorBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.error_model_load),
        style = Type.bodyM,
        color = Palette.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.ScrimPanelHigh)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/**
 * Whether a camera screen's pose model has gone quiet: the camera is open, and still no result
 * has come [MODEL_STALL_MS] after the screen did.
 *
 * The model counts as ready on its first result, empty or not, which is normally a second or two
 * in. A GPU delegate that builds and then never answers reports nothing, so no error ever came,
 * and 카메라를 준비하고 있어요 stayed up for good — in the tutorial with no way past it. After this
 * long it is a model that did not load, and is said as one.
 *
 * Only while the camera is open: a camera that will not open has its own card, and its retry
 * starts the wait again.
 */
@Composable
fun rememberModelStalled(ready: Boolean, cameraFailed: Boolean): Boolean {
    var stalled by remember { mutableStateOf(false) }
    LaunchedEffect(ready, cameraFailed) {
        stalled = false
        if (!ready && !cameraFailed) {
            delay(MODEL_STALL_MS)
            stalled = true
        }
    }
    return stalled
}

/** How long a camera screen waits for the model's first result before saying it did not load. */
private const val MODEL_STALL_MS = 10_000L
