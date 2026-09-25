package com.pushuprpg.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.catHeadTop
import com.pushuprpg.app.ui.components.drawCat
import com.pushuprpg.app.ui.components.drawHearts
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.app.ui.battle.SkeletonOverlay
import com.pushuprpg.app.ui.components.FramingGuide
import com.pushuprpg.app.ui.components.PlacementBanner
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.RenderSkeleton
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatName
import com.pushuprpg.core.survival.CatSpeech
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.SurvivalState
import kotlinx.coroutines.delay

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
    exercise: ExerciseType,
    isTutorial: Boolean,
    onRetry: () -> Unit,
    onShare: (ShareCardData) -> Unit,
    /**
     * Leaves the run. Outside the tutorial: 홈으로, the close button and back, all banking what was
     * done. In the tutorial, its ending: the done card's button, and back once the run has started.
     */
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /** The tutorial's way out before its run has started: 건너뛰기, and back while the ceiling waits. */
    onSkip: () -> Unit = {},
    /** The pose model did not load, so nothing will ever count: the tutorial offers its skip at once. */
    modelFailed: Boolean = false,
    cat: CatView = CatView(),
    placement: Placement = Placement(),
    /** The skeleton while setting up, for lining up with the framing guide; null once started. */
    setupSkeleton: RenderSkeleton? = null,
    /** As the user typed it; blank is the default name. */
    catName: String = "",
    catCoat: CatCoat = CatCoat.CREAM,
) {
    KeepScreenOn()
    val name = catName.ifBlank { stringResource(R.string.cat_default_name) }

    // Back leaves with what was done banked — no confirm, because the ceiling does not pause for
    // one. In the tutorial it skips while the ceiling waits and ends the tutorial once it moves:
    // the tutorial is the root of the stack, and back used to close the app from it.
    BackHandler(onBack = if (isTutorial && !state.started) onSkip else onHome)

    // Long enough to look stuck: the tutorial offers its skip after this much waiting to start.
    var waitedForStart by rememberSaveable { mutableStateOf(false) }
    if (isTutorial) {
        LaunchedEffect(Unit) {
            delay(SKIP_OFFERED_AFTER_MS)
            waitedForStart = true
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF1A1208))) {

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

        // Setting up: the skeleton and a ghost of the starting pose, so framing the phone is
        // matching lines rather than guessing. Gone the moment the run starts.
        if (setupSkeleton != null && state.alive) {
            val lines = Exercises.of(exercise).config
            SkeletonOverlay(
                skeleton = setupSkeleton,
                depth = 0f,
                countEnter = lines.countEnter,
                deepEnter = lines.deepEnter,
                flare = 0f,
                modifier = Modifier.fillMaxSize(),
            )
            FramingGuide(exercise = exercise)
        }

        CeilingAndCat(state = state, cat = cat, coat = catCoat, modifier = Modifier.fillMaxSize())

        // Above the cat's head, where a speech bubble belongs. The canvas places the cat by the same
        // proportions, so this lands on it at any screen size.
        val bubbleBottom = catHeadTop(baseY = maxHeight.value * FLOOR_AT, scale = maxWidth.value / CAT_SCALE_WIDTH, mood = cat.mood)
        CatBubbleSlot(
            speech = cat.speech,
            name = name,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (maxHeight.value - bubbleBottom).dp + 6.dp)
                .padding(horizontal = 32.dp),
        )

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
                    text = stringResource(R.string.survival_waiting_with, stringResource(exerciseLabelRes(exercise))),
                    style = Type.bodyM,
                    color = Palette.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            // Where the phone and the user are, live: before the first rep, and again whenever the
            // camera loses them — which matters more here than anywhere, because the ceiling does
            // not wait. The tutorial never went through a picker, so this is its only placement
            // advice, and a phone put where the movement cannot be seen counts nothing.
            if (state.alive) {
                Spacer(Modifier.height(10.dp))
                PlacementBanner(
                    placement = placement,
                    exercise = exercise,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        // Top left, as in a dungeon. Leaving mid-run keeps the run, as a game over does.
        if (!isTutorial) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, top = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.ScrimPanelHigh)
                    .clickable(onClick = onHome),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Palette.TextPrimary,
                )
            }
        }

        // The tutorial's way out, where a run's close button sits: once the wait to start has gone on
        // long enough to look stuck, or at once when the model never loaded and nothing can count.
        // Someone who cannot get onto the floor must not be held here by it.
        if (isTutorial && !state.started && (waitedForStart || modelFailed)) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, top = 8.dp)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Palette.ScrimPanelHigh)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_skip),
                    style = Type.labelL,
                    color = Palette.TextPrimary,
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
                    catName = name,
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
private fun CeilingAndCat(state: SurvivalState, cat: CatView, coat: CatCoat, modifier: Modifier = Modifier) {
    // The cat's own clock, for the tail's sway and a frightened tremble. Read only inside the draw
    // lambda, so it redraws the canvas without recomposing the screen. Still under reduced motion.
    val reduceMotion = LocalReduceMotion.current
    val clock = rememberInfiniteTransition(label = "cat")
    val phase by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4_000, easing = LinearEasing)),
        label = "cat-phase",
    )

    Canvas(modifier) {
        val floorY = size.height * FLOOR_AT
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
                path = Path().apply {
                    moveTo(i * toothWidth, ceilingBottom)
                    lineTo((i + 0.5f) * toothWidth, ceilingBottom + toothWidth * 0.55f)
                    lineTo((i + 1f) * toothWidth, ceilingBottom)
                    close()
                },
                color = slab,
            )
        }

        val scale = size.width / CAT_SCALE_WIDTH
        drawCat(
            centerX = size.width / 2f,
            baseY = floorY,
            scale = scale,
            coat = coat,
            mood = cat.mood,
            alarm = danger,
            cheer = cat.cheer,
            phase = if (reduceMotion) 0f else phase,
        )
        drawHearts(
            centerX = size.width / 2f,
            headTopY = catHeadTop(floorY, scale, cat.mood),
            scale = scale,
            count = cat.hearts,
            cheer = cat.cheer,
        )

        drawRect(
            color = Color(0xFF3A2A18),
            topLeft = Offset(0f, floorY),
            size = Size(size.width, size.height - floorY),
        )
    }
}

/** Where the floor is, as a fraction of the screen's height. The bubble is placed by it too. */
private const val FLOOR_AT = 0.82f

/** The screen width, in the cat's own units, that draws it at scale 1. */
private const val CAT_SCALE_WIDTH = 420f

/** How long the tutorial waits for its run to start before it offers 건너뛰기. */
private const val SKIP_OFFERED_AFTER_MS = 10_000L

/**
 * The speech bubble, popping in for each new line and gone between them.
 *
 * Words matter here more than anywhere else in the app: a cat that says 살려 줘요 is a cat the user
 * pushes harder for. The name tag is the user's own name for it, which is why naming it is offered
 * at all.
 */
@Composable
private fun CatBubbleSlot(speech: CatSpeech?, name: String, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = speech,
        transitionSpec = {
            if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.85f)) togetherWith fadeOut(tween(160))
        },
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier,
        label = "cat-bubble",
    ) { shown ->
        if (shown != null) CatBubble(name = name, text = catLineText(shown))
    }
}

@Composable
private fun CatBubble(name: String, text: String, modifier: Modifier = Modifier) {
    val paper = Color(0xFFFFF8EC)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .background(paper, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = name, style = Type.labelM, color = Color(0xFFB0662A))
            Text(text = text, style = Type.bodyL, color = Color(0xFF3A2A18), textAlign = TextAlign.Center)
        }
        Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                color = paper,
            )
        }
    }
}

/** The words for a line, rotating through its wordings so the same moment is not said the same way twice running. */
@Composable
private fun catLineText(speech: CatSpeech): String {
    val wordings = when (speech.line) {
        CatLine.WAITING -> listOf(R.string.cat_line_waiting_1, R.string.cat_line_waiting_2)
        CatLine.HELLO -> listOf(R.string.cat_line_hello_1, R.string.cat_line_hello_2)
        CatLine.CALM -> listOf(R.string.cat_line_calm_1, R.string.cat_line_calm_2, R.string.cat_line_calm_3)
        CatLine.NEAR_MISS -> listOf(R.string.cat_line_near_miss_1, R.string.cat_line_near_miss_2)
        CatLine.UNEASY -> listOf(R.string.cat_line_uneasy_1, R.string.cat_line_uneasy_2)
        CatLine.MILESTONE -> listOf(R.string.cat_line_milestone_1, R.string.cat_line_milestone_2)
        CatLine.COMBO -> listOf(R.string.cat_line_combo_1, R.string.cat_line_combo_2)
        CatLine.SCARED -> listOf(R.string.cat_line_scared_1, R.string.cat_line_scared_2)
        CatLine.PANIC -> listOf(R.string.cat_line_panic_1, R.string.cat_line_panic_2)
        CatLine.SAVED -> listOf(R.string.cat_line_saved_1, R.string.cat_line_saved_2, R.string.cat_line_saved_3)
    }
    val id = wordings[speech.serial % wordings.size]
    return when (speech.line) {
        CatLine.MILESTONE, CatLine.COMBO -> stringResource(id, speech.arg)
        else -> stringResource(id)
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
    catName: String,
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
            text = stringResource(R.string.survival_gameover, catName + CatName.subjectParticle(catName)),
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
