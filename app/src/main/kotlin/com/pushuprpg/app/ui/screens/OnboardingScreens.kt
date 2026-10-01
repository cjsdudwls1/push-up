package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.domain.ThemeMode
import com.pushuprpg.app.ui.components.CatCard
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.ThemeToggle
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type

/**
 * One screen, one idea, one button. A carousel here would only lose people before the workout.
 *
 * The theme toggle is the exception, and it sits in a corner rather than in the flow: this is the
 * first screen anyone sees, so it is where the look should be choosable, but it is not a question
 * anybody has to answer before starting.
 */
@Composable
fun OnboardingScreen(
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    catName: String = "",
    catCoat: CatCoat = CatCoat.CREAM,
    /** The cat's name, as it should be stored, and its coat. Called on every change. */
    onCatChange: (String, CatCoat) -> Unit = { _, _ -> },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = Type.headline,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.onboarding_body),
                style = Type.bodyL,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            // The cat is chosen here, once, and kept: by the owner's decision it is not asked again
            // before every run. 꾸미기 is where it changes.
            CatCard(name = catName, coat = catCoat, onChange = onCatChange)
            Spacer(Modifier.height(24.dp))
            // Before the first workout, not buried in settings: an app that tells people to exercise
            // says once, up front, that it is not medical advice and to stop if something hurts. In
            // body type and colour: as 13sp tertiary grey it read as fine print, and faintly at that.
            Text(
                text = stringResource(R.string.onboarding_health),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = stringResource(R.string.action_start), onClick = onContinue)
        }
        ThemeToggle(
            mode = themeMode,
            onToggle = onToggleTheme,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp),
        )
    }
}

/**
 * Camera rationale.
 *
 * The privacy line is the loudest thing here after the headline. It is the biggest objection a
 * camera fitness app faces, it happens to be entirely true — inference is on-device and nothing
 * leaves the phone — and saying it plainly before asking converts far better than a policy link.
 */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.permission_headline),
            style = Type.headline,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.permission_privacy),
            style = Type.titleM,
            color = colors.accept,
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(shape = RoundedCornerShape(18.dp), color = colors.acceptDim)
                .padding(18.dp),
        )
        if (permanentlyDenied) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_denied_body),
                style = Type.bodyL,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        if (permanentlyDenied) {
            PrimaryButton(
                text = stringResource(R.string.permission_open_settings),
                onClick = onOpenAppSettings,
            )
            // Asking stays. From Android 11 a dialog closed by a tap beside it reads exactly like
            // 다시 묻지 않음, and the system would still show it again. Where it truly will not, the
            // refusal comes straight back and this screen stays as it is.
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = stringResource(R.string.permission_grant),
                onClick = onRequestPermission,
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.permission_grant),
                onClick = onRequestPermission,
            )
        }
    }
}
