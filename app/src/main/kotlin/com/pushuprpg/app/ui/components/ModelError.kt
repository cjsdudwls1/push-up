package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type

/**
 * The pose model's error, along the foot of the screen on a fixed dark scrim that runs under the
 * navigation bar, with the line above it.
 *
 * One place for it, because it is said from two: over whichever screen is up when the model
 * reports an error, and by a camera screen whose model has gone quiet ([rememberModelStalled]).
 * The second comes with [onRetry], because there is something to try: set the model up again. It
 * used to say to open the app again, which set up the same silent GPU again.
 */
@Composable
fun ModelErrorBanner(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.ScrimPanelHigh)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (onRetry != null) R.string.error_model_stalled else R.string.error_model_load),
            style = Type.bodyM,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = stringResource(R.string.error_model_retry), onClick = onRetry)
        }
    }
}

/**
 * Whether a camera screen's pose model has gone quiet: frames have gone to it and no result has
 * come back, on the CPU. A GPU that does the same is moved to the CPU by [PoseLandmarkerSource]
 * itself, which watches the frames it hands over; see [PoseLandmarkerSource.stalled].
 *
 * The model counts as ready on its first result, empty or not, which is normally a second or two
 * in. A model that never answers reports nothing, so no error ever came, and
 * 카메라를 준비하고 있어요 stayed up for good — in the tutorial with no way past it. Once it has
 * stalled it is said as a model that did not load.
 *
 * Only while the camera is open: a camera that will not open has its own card, and its retry
 * starts the watch again.
 */
@Composable
fun rememberModelStalled(source: PoseLandmarkerSource, cameraFailed: Boolean): Boolean {
    val stalled by source.stalled.collectAsState()
    return stalled && !cameraFailed
}
