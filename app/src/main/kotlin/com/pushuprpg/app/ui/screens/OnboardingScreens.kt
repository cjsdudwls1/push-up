package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.components.Pill
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.game.PlayerClass

/** One screen, one idea, one button. A carousel here would only lose people before the workout. */
@Composable
fun OnboardingScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.action_start), onClick = onContinue)
    }
}

/**
 * Class selection.
 *
 * The cards describe a way of training rather than a stat spread, because that is what the choice
 * actually is — the knight is for people who like slow heavy reps, the mage for people who would
 * rather hold a position than do many, the archer for people who like pace. Framing it as numbers
 * would make it a min-max puzzle instead of a question about their own body.
 */
@Composable
fun ClassPickScreen(
    capacity: Float,
    onPick: (PlayerClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    // Isometric holds turn time under tension into damage, which is the one route that works for
    // someone who cannot yet do many reps at all.
    val recommended = if (capacity < 8f) PlayerClass.MAGE else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 60.dp, bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.class_pick_title),
            style = Type.headline,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.class_pick_body),
            style = Type.bodyL,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(22.dp))

        PlayerClass.entries.forEach { playerClass ->
            val (nameRes, descRes) = when (playerClass) {
                PlayerClass.KNIGHT -> R.string.class_knight to R.string.class_knight_desc
                PlayerClass.MAGE -> R.string.class_mage to R.string.class_mage_desc
                PlayerClass.ARCHER -> R.string.class_archer to R.string.class_archer_desc
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .cardSurface(shape = RoundedCornerShape(18.dp))
                    .clickable { onPick(playerClass) }
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(nameRes),
                        style = Type.titleL,
                        color = Palette.TextPrimary,
                    )
                    if (playerClass == recommended) {
                        Spacer(Modifier.width(8.dp))
                        Pill(text = stringResource(R.string.difficulty_recommended), tint = colors.accept)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(descRes),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                )
            }
        }
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
        } else {
            PrimaryButton(
                text = stringResource(R.string.permission_grant),
                onClick = onRequestPermission,
            )
        }
    }
}
