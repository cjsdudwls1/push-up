package com.pushuprpg.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.survival.SurvivalState

/**
 * 고냥이 지켜줘.
 *
 * A deliberately different register from the dungeon: warm, round, no skeleton, no HP numbers, no
 * depth gauge — the descending ceiling *is* the gauge. This is the mode people are shown first and
 * the one they send to a friend, so it must not look like sports science.
 */
@Composable
fun SurvivalScreen(
    state: SurvivalState,
    bestScore: Int,
    poseSource: PoseLandmarkerSource,
    isTutorial: Boolean,
    onRetry: () -> Unit,
    onShare: (Int) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()

    Box(modifier.fillMaxSize().background(Color(0xFF1A1208))) {

        CameraPreview(source = poseSource, modifier = Modifier.fillMaxSize())

        // Warm wash over the camera: the room becomes a cosy room rather than a lab.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC2A1B0A),
                        0.55f to Color(0x552A1B0A),
                        1f to Color(0xAA1A1208),
                    )
                )
        )

        CeilingAndCat(state = state, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.survival_score, state.score),
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.survival_best, bestScore),
                style = Type.labelM,
                color = Palette.TextSecondary,
            )
        }

        // The tutorial explains itself once, before the ceiling starts moving. After that the
        // mapping does the teaching: pushing up is pushing the ceiling up, which needs no words.
        if (isTutorial && state.reps == 0 && state.alive) {
            IntroCard(modifier = Modifier.align(Alignment.Center))
        }

        if (!state.alive) {
            if (isTutorial) {
                TutorialDoneCard(
                    reps = state.reps,
                    onContinue = onHome,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                GameOverCard(
                    score = state.score,
                    bestScore = bestScore,
                    onRetry = onRetry,
                    onShare = onShare,
                    onHome = onHome,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * The ceiling, the cat, and the space between them.
 *
 * Everything is drawn from shapes: there are no art assets yet, and a mode whose whole appeal is
 * how quickly it reads does not need them. The gap between the slab and the floor is the only
 * information on screen, which is exactly the point.
 */
@Composable
private fun CeilingAndCat(state: SurvivalState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val floorY = size.height * 0.82f
        val topY = size.height * 0.10f
        val travel = floorY - topY
        val ceilingBottom = topY + travel * (1f - state.height.coerceIn(0f, 1f))

        // Tension is carried by colour as well as by position, so the danger reads peripherally.
        val danger = state.intensity
        val slab = Color(
            red = 0.42f + 0.5f * danger,
            green = 0.34f - 0.18f * danger,
            blue = 0.28f - 0.16f * danger,
            alpha = 1f,
        )

        drawRect(
            color = slab,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, ceilingBottom),
        )
        // Teeth along the underside: menace without needing a texture.
        val toothWidth = size.width / 14f
        for (i in 0 until 14) {
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(i * toothWidth, ceilingBottom)
                    lineTo((i + 0.5f) * toothWidth, ceilingBottom + toothWidth * 0.55f)
                    lineTo((i + 1f) * toothWidth, ceilingBottom)
                    close()
                },
                color = slab,
            )
        }

        drawCat(centerX = size.width / 2f, baseY = floorY, scale = size.width / 420f, alarm = danger)

        drawRect(
            color = Color(0xFF3A2A18),
            topLeft = Offset(0f, floorY),
            size = Size(size.width, size.height - floorY),
        )
    }
}

/** A cat, in circles. Round enough to be worth protecting. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCat(
    centerX: Float,
    baseY: Float,
    scale: Float,
    alarm: Float,
) {
    val body = 46f * scale
    val head = 30f * scale
    val fur = Color(0xFFF3D9A8)
    val dark = Color(0xFF3A2A18)

    drawRoundRect(
        color = fur,
        topLeft = Offset(centerX - body, baseY - body * 1.1f),
        size = Size(body * 2f, body * 1.1f),
        cornerRadius = CornerRadius(body * 0.6f),
    )
    drawCircle(color = fur, radius = head, center = Offset(centerX, baseY - body * 1.15f - head * 0.6f))

    // Ears flatten as the ceiling closes in — the whole emotional read of the mode.
    val earSpread = head * 0.62f
    val earHeight = head * (0.85f - 0.45f * alarm)
    listOf(-1f, 1f).forEach { side ->
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                val ex = centerX + side * earSpread
                val ey = baseY - body * 1.15f - head * 1.2f
                moveTo(ex - head * 0.28f, ey + head * 0.4f)
                lineTo(ex + side * head * 0.1f, ey - earHeight * 0.5f)
                lineTo(ex + head * 0.28f, ey + head * 0.4f)
                close()
            },
            color = fur,
        )
    }

    val eyeY = baseY - body * 1.15f - head * 0.7f
    val eyeR = head * (0.13f + 0.07f * alarm)
    listOf(-1f, 1f).forEach { side ->
        drawCircle(color = dark, radius = eyeR, center = Offset(centerX + side * head * 0.34f, eyeY))
    }
}

@Composable
private fun IntroCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(22.dp), color = Palette.Bg1)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.tutorial_intro_title),
            style = Type.titleL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.tutorial_intro_body),
            style = Type.bodyL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The tutorial's ending.
 *
 * It reports the reps rather than the score, because the number that matters here is the one the
 * game will use to size every dungeon from now on — and because telling a beginner their score
 * before they know what a good one is invites the wrong comparison.
 */
@Composable
private fun TutorialDoneCard(
    reps: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(22.dp), color = Palette.Bg1)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.tutorial_done_title),
            style = Type.titleL,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.tutorial_done_body, reps),
            style = Type.bodyL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = stringResource(R.string.tutorial_continue), onClick = onContinue)
    }
}

@Composable
private fun GameOverCard(
    score: Int,
    bestScore: Int,
    onRetry: () -> Unit,
    onShare: (Int) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(22.dp), color = Palette.Bg1)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.survival_gameover),
            style = Type.titleL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.survival_score, score),
            style = Type.displayM,
            color = Palette.Deep,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.survival_best, bestScore),
            style = Type.labelM,
            color = Palette.TextTertiary,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.survival_share),
                onClick = { onShare(score) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.action_home),
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
