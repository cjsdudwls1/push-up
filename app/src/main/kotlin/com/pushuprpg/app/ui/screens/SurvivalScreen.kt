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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.share.ShareCardData
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
    onShare: (ShareCardData) -> Unit,
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
            // The ceiling waits for the user to be in position, and says so — a still ceiling with
            // no explanation reads as a broken one.
            if (!state.started && state.alive) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.survival_waiting),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                // Where the phone goes, said before the first rep. The dungeons show this on the
                // way in, but survival — and the tutorial, which is survival — never went through
                // that screen, and a phone put side-on tracks the pose but can count nothing.
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.exercise_hint_pushup),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp),
                )
            }
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
                    onShare = {
                        onShare(
                            ShareCardData.Survival(
                                score = state.score,
                                best = maxOf(bestScore, state.score),
                                reps = state.reps,
                                seconds = (state.elapsedMs / 1000).toInt(),
                            )
                        )
                    },
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

/**
 * 고냥이.
 *
 * The same cat the share card draws, and that is the reason it is not just three circles: this is
 * the mascot, and the first thing anyone who has not installed the app ever sees. The parts that
 * make it read as a cat rather than a snowman — tall swept ears, whiskers, a tail — cost a handful
 * of draw calls and are worth every one.
 *
 * [alarm] is the only thing that moves: ears flatten and eyes widen as the ceiling closes in. That
 * is the whole emotional read of the mode, and it is carried by shape so it survives greyscale.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCat(
    centerX: Float,
    baseY: Float,
    scale: Float,
    alarm: Float,
) {
    val body = 46f * scale
    val head = 30f * scale
    val headY = baseY - body * 1.05f - head * 0.72f
    val fur = Color(0xFFF3D9A8)
    val ear = Color(0xFFE0A88C)
    val dark = Color(0xFF3A2A18)

    // Tail first: the body edge hides where it joins.
    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX + body * 0.82f, baseY - body * 0.18f)
            cubicTo(
                centerX + body * 1.62f, baseY - body * 0.30f,
                centerX + body * 1.55f, baseY - body * 0.74f,
                centerX + body * 1.48f, baseY - body * 0.98f,
            )
            cubicTo(
                centerX + body * 1.36f, baseY - body * 1.46f,
                centerX + body * 1.16f, baseY - body * 1.44f,
                centerX + body * 0.98f, baseY - body * 1.40f,
            )
        },
        color = fur,
        style = Stroke(width = head * 0.30f, cap = StrokeCap.Round),
    )

    drawRoundRect(
        color = fur,
        topLeft = Offset(centerX - body, baseY - body * 1.05f),
        size = Size(body * 2f, body * 1.05f),
        cornerRadius = CornerRadius(body * 0.62f),
    )

    // Ears before the head, so their bases vanish under it. Flattening is clamped so an alarmed cat
    // still has ears — a cat with none reads as a bug rather than as fear.
    val lift = 1f - 0.42f * alarm
    listOf(-1f, 1f).forEach { side ->
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX + side * head * 0.16f, headY - head * 0.62f)
                lineTo(centerX + side * head * (0.74f + 0.30f * alarm), headY - head * 1.58f * lift)
                lineTo(centerX + side * head * 1.00f, headY - head * 0.34f)
                close()
            },
            color = fur,
        )
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX + side * head * 0.36f, headY - head * 0.66f)
                lineTo(centerX + side * head * (0.71f + 0.28f * alarm), headY - head * 1.28f * lift)
                lineTo(centerX + side * head * 0.84f, headY - head * 0.52f)
                close()
            },
            color = ear,
        )
    }

    drawCircle(color = fur, radius = head, center = Offset(centerX, headY))

    val eyeY = headY - head * 0.10f
    val eyeR = head * (0.15f + 0.07f * alarm)
    listOf(-1f, 1f).forEach { side ->
        drawCircle(color = dark, radius = eyeR, center = Offset(centerX + side * head * 0.36f, eyeY))
    }

    val noseY = eyeY + head * 0.30f
    drawPath(
        path = androidx.compose.ui.graphics.Path().apply {
            moveTo(centerX - head * 0.10f, noseY)
            lineTo(centerX + head * 0.10f, noseY)
            lineTo(centerX, noseY + head * 0.11f)
            close()
        },
        color = dark,
    )

    val stroke = head * 0.055f
    val mouthY = noseY + head * 0.12f
    drawLine(
        color = dark,
        start = Offset(centerX, mouthY),
        end = Offset(centerX - head * 0.14f, mouthY + head * 0.12f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = dark,
        start = Offset(centerX, mouthY),
        end = Offset(centerX + head * 0.14f, mouthY + head * 0.12f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )

    listOf(-1f, 1f).forEach { side ->
        val from = centerX + side * head * 0.42f
        drawLine(
            color = dark,
            start = Offset(from, noseY - head * 0.04f),
            end = Offset(from + side * head * 0.72f, noseY - head * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = dark,
            start = Offset(from, noseY + head * 0.12f),
            end = Offset(from + side * head * 0.76f, noseY + head * 0.16f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
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
    onShare: () -> Unit,
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
                onClick = onShare,
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
