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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.ui.components.CameraErrorCard
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.ModelErrorBanner
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.RunGrowthLines
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.catHeadTop
import com.pushuprpg.app.ui.components.drawCat
import com.pushuprpg.app.ui.components.drawHearts
import com.pushuprpg.app.ui.components.rememberModelStalled
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.app.ui.components.CameraText
import com.pushuprpg.app.ui.components.SkeletonOverlay
import com.pushuprpg.app.ui.components.FramingGuide
import com.pushuprpg.app.ui.components.PlacementBanner
import com.pushuprpg.app.ui.components.exerciseHintRes
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.RenderSkeleton
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.progression.SetBests
import com.pushuprpg.core.progression.SetOutcome
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatMood
import com.pushuprpg.core.survival.CatPhase
import com.pushuprpg.core.survival.CatSession
import com.pushuprpg.core.survival.CatSessionState
import com.pushuprpg.core.survival.LifeResult
import com.pushuprpg.core.survival.CatSpeech
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.SurvivalState
import kotlinx.coroutines.delay

/**
 * 고냥이 지켜줘.
 *
 * A deliberately warm, round register: no skeleton, no numbers on the run, no depth gauge — the
 * descending ceiling *is* the gauge. This is the mode people are shown first and
 * the one they send to a friend, so it must not look like sports science.
 *
 * A session is ten lives — ten sets — with a rest between them that the screen counts down; the
 * tutorial is one life. See [com.pushuprpg.core.survival.CatSession].
 */
@Composable
fun SurvivalScreen(
    state: CatSessionState,
    bestScore: Int,
    poseSource: PoseLandmarkerSource,
    exercise: ExerciseType,
    isTutorial: Boolean,
    onRetry: () -> Unit,
    onShare: (ShareCardData) -> Unit,
    /**
     * Leaves the run, banking what was done: outside the tutorial, the ending's 홈으로; in the
     * tutorial, the done card's button, and back once the run has started.
     */
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The close button and back while a session is on, outside the tutorial: the session ends where
     * it is and its ending is shown. Leaving, [onHome], is the ending's own button.
     */
    onClose: () -> Unit = onHome,
    /** The ending's 운동 바꾸기: leaves as [onHome] does, to the picker instead of the hub. */
    onChangeExercise: () -> Unit = onHome,
    /** The rest card's 건너뛰기: the next life now, waiting for the user as after a full rest. */
    onSkipRest: () -> Unit = {},
    /** The tutorial's way out before its run has started: 건너뛰기, and back, asked first, while the ceiling waits. */
    onSkip: () -> Unit = {},
    /** The pose model did not load, so nothing will ever count: the tutorial offers its skip at once. */
    modelFailed: Boolean = false,
    /** Reps the detector refused as not deep enough, for the tutorial's ending. */
    nearMisses: Int = 0,
    /** What the banked session changed — a record, places passed — once it is written. */
    growth: RunGrowth? = null,
    cat: CatView = CatView(),
    placement: Placement = Placement(),
    /** The skeleton while setting up, for lining up with the framing guide; null once started. */
    setupSkeleton: RenderSkeleton? = null,
    /** As the user typed it; blank is the default name. */
    catName: String = "",
    catCoat: CatCoat = CatCoat.CREAM,
    /** What the cat has on. A scene stays at home: under the ceiling is the only place this cat is. */
    catWear: Set<CatItem> = emptySet(),
    /** This movement's best per set before this session: what each life is played against. */
    setBests: SetBests = SetBests(),
    /** What the banked session did to the set bests; null until it is written. */
    setOutcome: SetOutcome? = null,
) {
    val hold = Exercises.of(exercise).kind == MovementKind.HOLD
    KeepScreenOn()
    val name = catName.ifBlank { stringResource(R.string.cat_default_name) }

    // Back leaves with what was done banked — no confirm, because the ceiling does not pause for
    // one. In the tutorial it ends the tutorial once the ceiling moves: the tutorial is the root of
    // the stack, and back used to close the app from it. While the ceiling waits it asks before
    // skipping, which is for good: an edge swipe made while standing the phone up skipped the
    // tutorial with no way back to it. Nothing moves while the question is up. 건너뛰기 does not ask.
    var confirmingSkip by remember { mutableStateOf(false) }
    val life = state.life
    val playing = state.phase == CatPhase.PLAYING
    val resting = state.phase == CatPhase.RESTING
    val waitingTutorial = isTutorial && !state.started
    // Outside the tutorial, back ends the session and shows its ending, as the close button does;
    // from the ending it leaves.
    val close: () -> Unit = if (isTutorial || state.phase == CatPhase.OVER) onHome else onClose
    BackHandler {
        if (waitingTutorial) confirmingSkip = !confirmingSkip else close()
    }

    // Long enough to look stuck: the tutorial offers its skip after this much waiting to start.
    var waitedForStart by rememberSaveable { mutableStateOf(false) }
    if (isTutorial) {
        LaunchedEffect(Unit) {
            delay(SKIP_OFFERED_AFTER_MS)
            waitedForStart = true
        }
    }

    // Raised by the camera and cleared by it once it opens; the card's retry binds it again.
    var cameraFailed by remember { mutableStateOf(false) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    val poseReady by poseSource.ready.collectAsState()
    // A model that never answers reports no error. The source moves a silent GPU to the CPU by
    // itself; silent there as well, it is taken for one that did not load, said the same way with a
    // retry, and the tutorial offers its skip for it.
    val modelStalled = rememberModelStalled(source = poseSource, cameraFailed = cameraFailed)
    val noModel = modelFailed || modelStalled

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF1A1208))) {

        CameraPreview(
            source = poseSource,
            modifier = Modifier.fillMaxSize(),
            attempt = cameraAttempt,
            onCameraError = { failed -> cameraFailed = failed },
        )

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

        // Until the run starts the lower part of the picture is the framing guide's: the ghost's head
        // and arms for a pushup sit where the cat and its bubble do, and the frame's foot below the
        // floor. So the cat and the floor are drawn faint underneath it, and the cat's waiting line
        // is said at the top instead of in a bubble over the ghost.
        val settingUp = playing && !life.started && life.alive

        // Resting, the ceiling is back up: a crushed cat is not what a minute of rest looks at.
        CeilingAndCat(
            state = if (resting) life.copy(height = 1f, intensity = 0f) else life,
            cat = cat,
            coat = catCoat,
            wear = catWear,
            faded = settingUp,
            modifier = Modifier.fillMaxSize(),
        )

        // Setting up: the skeleton and a ghost of the starting pose, so framing the phone is
        // matching lines rather than guessing. Gone the moment the run starts.
        if (setupSkeleton != null && playing && life.alive) {
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

        // Above the cat's head, where a speech bubble belongs. The canvas places the cat by the same
        // proportions, so this lands on it at any screen size.
        val bubbleBottom = catHeadTop(baseY = maxHeight.value * FLOOR_AT, scale = catScale(maxWidth.value, maxHeight.value), mood = cat.mood)
        CatBubbleSlot(
            speech = cat.speech.takeUnless { settingUp },
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
            // Outlined, as all text over the camera is, and none of it under bodyM: the warm wash
            // does not darken a bright room enough to read plain grey type off it.
            CameraText(
                text = stringResource(R.string.survival_score, state.totalScore),
                style = Type.numeralL,
                color = Palette.TextPrimary,
            )
            // Not in the tutorial: a best of 0 says nothing to someone who has never played, and it
            // has one life, which hearts would only count down. The best is this movement's own, and
            // says so — a pull-up's score is not a pushup's.
            if (!isTutorial) {
                if (bestScore > 0) {
                    Spacer(Modifier.height(2.dp))
                    CameraText(
                        text = stringResource(R.string.survival_best_of, stringResource(exerciseLabelRes(exercise)), bestScore),
                        style = Type.bodyM,
                        color = Palette.TextSecondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LivesRow(lives = state.lives, left = state.livesLeft)
                // The set against its own best, by the owner's decision: the number is what makes
                // breaking it worth chasing. Once the life has started; while setting up there is
                // nothing yet to count.
                if (playing && life.started) {
                    Spacer(Modifier.height(8.dp))
                    SetCounter(
                        set = state.ended.size + 1,
                        now = if (hold) (life.elapsedMs / 1000L).toInt() else life.reps,
                        best = setBests.at(state.ended.size),
                        hold = hold,
                    )
                }
            }
            // The ceiling waits for the user to be in position, and says so — a still ceiling with
            // no explanation reads as a broken one. The tutorial says it in its intro.
            if (settingUp) {
                Spacer(Modifier.height(10.dp))
                // The cat's words are its own, as in the bubble; only where they are shown moves.
                val catSays = cat.speech?.let { stringResource(R.string.cat_says, name, catLineText(it)) }
                if (isTutorial) {
                    TutorialIntro(catSays = catSays, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    CameraText(
                        text = stringResource(R.string.survival_waiting_with, stringResource(exerciseLabelRes(exercise))),
                        style = Type.bodyM.copy(textAlign = TextAlign.Center),
                        color = Palette.TextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    if (catSays != null) {
                        Spacer(Modifier.height(4.dp))
                        CameraText(
                            text = catSays,
                            style = Type.bodyM.copy(textAlign = TextAlign.Center),
                            color = Palette.TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
            // Where the phone and the user are, live: before the first rep, and again whenever the
            // camera loses them — which matters more here than anywhere, because the ceiling does
            // not wait. The tutorial never went through a picker, so this and its intro are its only
            // placement advice, and a phone put where the movement cannot be seen counts nothing.
            // Not while the camera will not open, where the card says why, nor with no model, whose
            // error says it; until the model's first frame it says the camera is getting ready.
            if (playing && life.alive && !cameraFailed && !noModel) {
                Spacer(Modifier.height(10.dp))
                PlacementBanner(
                    placement = placement,
                    exercise = exercise,
                    preparing = !poseReady,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        // Top left. Leaving mid-run keeps the run, as a game over does.
        if (!isTutorial) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, top = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.ScrimPanelHigh)
                    .clickable(onClick = close),
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
        // long enough to look stuck, or at once when the model never loaded or the camera will not
        // open, and nothing can count. Someone who cannot get onto the floor must not be held here
        // by it.
        if (isTutorial && !state.started && (waitedForStart || noModel || cameraFailed)) {
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

        if (modelStalled && !modelFailed) {
            ModelErrorBanner(Modifier.align(Alignment.BottomCenter), onRetry = poseSource::retry)
        }

        // Only while the run is on. After it, its own card has the floor, and the next run brings
        // this back if the camera is still out.
        if (cameraFailed && state.phase != CatPhase.OVER) {
            CameraErrorCard(
                onRetry = {
                    cameraFailed = false
                    cameraAttempt++
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (resting) {
            // The phone is across the room during a rest, so the countdown is the biggest thing here.
            RestCard(
                state = state,
                nextBest = setBests.at(state.ended.size),
                hold = hold,
                onSkip = onSkipRest,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (state.phase == CatPhase.OVER) {
            if (isTutorial) {
                TutorialDoneCard(
                    reps = state.totalReps,
                    nearMisses = nearMisses,
                    growth = growth,
                    onContinue = onHome,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                val movement = stringResource(exerciseLabelRes(exercise))
                SessionOverCard(
                    catName = name,
                    state = state,
                    bestScore = bestScore,
                    exercise = exercise,
                    growth = growth,
                    onRetry = onRetry,
                    onShare = {
                        onShare(
                            ShareCardData.Survival(
                                movement = movement,
                                score = state.totalScore,
                                best = maxOf(bestScore, state.totalScore),
                                reps = state.totalReps,
                                seconds = (state.ended.sumOf { it.survivedMs } / 1000).toInt(),
                            )
                        )
                    },
                    onHome = onHome,
                    onChangeExercise = onChangeExercise,
                    setOutcome = setOutcome,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Gone the moment the run starts: the user got into position, which answers it.
        if (confirmingSkip && waitingTutorial) {
            SkipConfirm(onSkip = onSkip, onStay = { confirmingSkip = false })
        }
    }
}

/**
 * Asked when back is pressed while the tutorial's ceiling waits, over the screen. Skipping is for
 * good — the tutorial does not open again — and the ceiling is still until
 * the user is in position, so there is nothing to lose by asking.
 */
@Composable
private fun BoxScope.SkipConfirm(onSkip: () -> Unit, onStay: () -> Unit) {
    Box(
        Modifier
            .matchParentSize()
            .background(Palette.ScrimPanelHigh)
            .clickable(onClick = onStay),
    )
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.tutorial_skip_confirm),
            style = Type.titleL,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(text = stringResource(R.string.run_resume), onClick = onStay)
        SecondaryButton(text = stringResource(R.string.action_skip), onClick = onSkip)
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
private fun CeilingAndCat(
    state: SurvivalState,
    cat: CatView,
    coat: CatCoat,
    modifier: Modifier = Modifier,
    wear: Set<CatItem> = emptySet(),
    /** Setting up: the cat and the floor are drawn faint, so the framing guide reads through them. */
    faded: Boolean = false,
) {
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

    // Tension is carried by colour as well as by position, so the danger reads peripherally.
    val danger = state.intensity

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val floorY = size.height * FLOOR_AT
            val topY = size.height * 0.10f
            val toothWidth = size.width / 14f
            // Height 0 — the life ends — is the teeth on the cat's head, crouched as it is by then,
            // not the slab on the floor. Drawn down to the floor, the ceiling went behind the cat
            // and the set played on with the cat already under it, and the hearts never fell.
            val headTop = catHeadTop(floorY, catScale(size.width, size.height), CatMood.PANIC)
            val lowest = headTop - toothWidth * 0.55f
            val travel = lowest - topY
            val ceilingBottom = topY + travel * (1f - state.height.coerceIn(0f, 1f))

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
        }

        // A layer of its own, so faint is one alpha over the whole cat rather than each shape of it.
        Canvas(Modifier.fillMaxSize().alpha(if (faded) SETUP_ALPHA else 1f)) {
            val floorY = size.height * FLOOR_AT
            val scale = catScale(size.width, size.height)
            drawCat(
                centerX = size.width / 2f,
                baseY = floorY,
                scale = scale,
                coat = coat,
                mood = cat.mood,
                alarm = danger,
                cheer = cat.cheer,
                phase = if (reduceMotion) 0f else phase,
                wear = wear,
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
}

/** Where the floor is, as a fraction of the screen's height. The bubble is placed by it too. */
private const val FLOOR_AT = 0.82f

/** How strongly the cat and the floor are drawn while setting up, under the framing guide. */
private const val SETUP_ALPHA = 0.4f

/** The screen width, in the cat's own units, that draws it at scale 1. */
private const val CAT_SCALE_WIDTH = 420f

/**
 * The screen height that draws it at scale 1. Never the tighter of the two on an upright phone, where
 * the cat is sized by the width as it always was; on a phone on its side — a plank's, see
 * FollowPhoneRotation — sized by the width the cat would fill half the height and leave the ceiling
 * nowhere to come down.
 */
private const val CAT_SCALE_HEIGHT = 600f

/** How large to draw the cat on a [width] by [height] screen: the canvas and the bubble agree on it. */
private fun catScale(width: Float, height: Float): Float =
    minOf(width / CAT_SCALE_WIDTH, height / CAT_SCALE_HEIGHT)

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
        CatLine.REST -> listOf(R.string.cat_line_rest_1, R.string.cat_line_rest_2)
        CatLine.REST_TEN -> listOf(R.string.cat_line_rest_ten)
        CatLine.AGAIN -> listOf(R.string.cat_line_again_1, R.string.cat_line_again_2)
        CatLine.RECORD -> listOf(R.string.cat_line_record_1, R.string.cat_line_record_2)
    }
    val id = wordings[speech.serial % wordings.size]
    return when (speech.line) {
        CatLine.MILESTONE, CatLine.COMBO -> stringResource(id, speech.arg)
        else -> stringResource(id)
    }
}

/**
 * The tutorial explaining itself, once, before the ceiling starts moving: where the phone goes,
 * then what happens. After that the mapping does the teaching — pushing up is pushing the ceiling
 * up, which needs no words.
 *
 * At the top, over the room rather than over the framing guide: it used to be an opaque card in
 * the middle of the screen, on top of the ghost and the skeleton that setting up is done by. And
 * the tutorial never goes through the picker, so where the phone goes was said nowhere — and side
 * on, where people tend to put it, is the one angle a pushup is not counted from.
 */
@Composable
private fun TutorialIntro(
    /** The cat's waiting line with its name, here rather than in a bubble over the framing ghost. */
    catSays: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.ScrimPanelHigh)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(exerciseHintRes(ExerciseType.PUSHUP)),
            style = Type.bodyL,
            color = Palette.Brand400,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.tutorial_intro_body),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (catSays != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = catSays,
                style = Type.bodyM,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The tutorial's ending.
 *
 * It reports the reps rather than the score, because telling a beginner their score before they
 * know what a good one is invites the wrong comparison — and then what comes next: three lives,
 * with a rest between them.
 *
 * Nothing counted is one of two runs, and only the card for a counted one says 준비 끝. Reps the
 * detector refused as not deep enough ([nearMisses]) were seen by the camera: the phone was fine and
 * the depth is what to find. Blaming the phone sent the user off to move it. With none of those
 * either, the phone's place is where to look, and the card says where it goes.
 */
@Composable
private fun TutorialDoneCard(
    reps: Int,
    nearMisses: Int,
    /** What the first run did: the first metres, the cat tower, and the cat's first find. */
    growth: RunGrowth?,
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
            text = stringResource(
                when {
                    reps > 0 -> R.string.tutorial_done_title
                    nearMisses > 0 -> R.string.tutorial_done_title_shallow
                    else -> R.string.tutorial_done_title_zero
                }
            ),
            style = Type.titleL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                reps > 0 -> stringResource(R.string.tutorial_done_reps, reps)
                nearMisses > 0 -> stringResource(R.string.tutorial_done_shallow)
                else -> stringResource(R.string.tutorial_done_zero)
            },
            style = Type.bodyL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (reps == 0 && nearMisses == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(exerciseHintRes(ExerciseType.PUSHUP)),
                style = Type.bodyM,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
        RunGrowthLines(
            growth = growth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tutorial_done_next, CatSession.LIVES),
            style = Type.bodyL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = stringResource(R.string.tutorial_continue), onClick = onContinue)
    }
}

/**
 * The set being played against its own best: 3세트 · 지금 9 / 최고 12. Past the best it turns the
 * record colour and says so; a set never played before has no best, and its first is the record.
 */
@Composable
private fun SetCounter(set: Int, now: Int, best: Int, hold: Boolean, modifier: Modifier = Modifier) {
    val beaten = best > 0 && now > best
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CameraText(
            text = stringResource(
                when {
                    best <= 0 && hold -> R.string.set_now_first_hold
                    best <= 0 -> R.string.set_now_first
                    hold -> R.string.set_now_best_hold
                    else -> R.string.set_now_best
                },
                set, now, best,
            ),
            style = Type.titleL,
            color = if (beaten) Palette.Deep else Palette.TextPrimary,
        )
        if (beaten) {
            CameraText(
                text = stringResource(R.string.set_record_broken),
                style = Type.bodyM,
                color = Palette.Deep,
            )
        }
    }
}

/** The session's lives as hearts: full for the ones left, faint for the ones spent. */
@Composable
private fun LivesRow(lives: Int, left: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.survival_lives_left, left)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(lives) { i ->
            CameraText(
                text = "\u2665",
                style = Type.titleM,
                color = if (i < left) HEART else Palette.TextDisabled,
            )
        }
    }
}

private val HEART = Color(0xFFFF6F91)

/**
 * The rest between two lives.
 *
 * It is not optional, by the owner's decision: a set to the edge of failure needs the rest before
 * the next one, and a game will not take it unless made to. So the countdown is the largest thing on
 * screen — the phone is across the room.
 *
 * It says what the next set has to beat: by the owner's later decision (2026-10-01) the numbers are
 * shown while training, each set against its own best. And it can be skipped, by the owner's decision
 * of 2026-10-02 — the button is last, under the advice to rest, so it is there without being the
 * first thing the card offers.
 */
@Composable
private fun RestCard(
    state: CatSessionState,
    /** The next set's best, 0 for a set never played. */
    nextBest: Int,
    hold: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seconds = ((state.restLeftMs + 999L) / 1000L).toInt()
    Column(
        // Narrowed on a phone on its side (a plank's), where full width is a banner, not a card; and
        // kept off the cutout, which is at the side there.
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(22.dp), color = Palette.Bg1)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.result_rest_title),
            style = Type.labelL,
            color = Palette.TextSecondary,
        )
        Text(
            text = stringResource(R.string.result_rest_time, seconds / 60, seconds % 60),
            style = Type.displayM,
            color = Palette.TextPrimary,
        )
        if (state.ended.lastOrNull()?.refunded == true) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.rest_refunded),
                style = Type.bodyM,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        LivesRow(lives = state.lives, left = state.livesLeft)
        Spacer(Modifier.height(10.dp))
        val next = state.ended.size + 1
        Text(
            text = when {
                nextBest <= 0 -> stringResource(R.string.rest_next_first, next)
                hold -> stringResource(R.string.rest_next_best_hold, next, nextBest)
                else -> stringResource(R.string.rest_next_best, next, nextBest)
            },
            style = Type.titleM,
            color = Palette.Deep,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.rest_tip),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.rest_after),
            style = Type.bodyM,
            color = Palette.TextTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            text = stringResource(R.string.rest_skip),
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Every life spent: the session's score, what each life made, and what the session did for the
 * calories and the records, which is the part that says the work is going somewhere.
 */
@Composable
private fun SessionOverCard(
    catName: String,
    state: CatSessionState,
    bestScore: Int,
    exercise: ExerciseType,
    growth: RunGrowth?,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onHome: () -> Unit,
    onChangeExercise: () -> Unit,
    setOutcome: SetOutcome?,
    modifier: Modifier = Modifier,
) {
    val hold = Exercises.of(exercise).kind == MovementKind.HOLD
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(22.dp), color = Palette.Bg1)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.session_over_title),
            style = Type.titleL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.cat_says, catName, stringResource(R.string.session_over_cat)),
            style = Type.bodyM,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.survival_score, state.totalScore),
            style = Type.displayM,
            color = Palette.Deep,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.survival_best_of,
                stringResource(exerciseLabelRes(exercise)),
                maxOf(bestScore, state.totalScore),
            ),
            style = Type.labelM,
            color = Palette.TextTertiary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(
                if (hold) R.string.session_chart_title_hold else R.string.session_chart_title,
                state.ended.size,
            ),
            style = Type.labelL,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        LivesChart(lives = state.ended, hold = hold, broken = setOutcome?.broken.orEmpty().toSet())
        if (setOutcome != null && setOutcome.churu > 0) {
            Spacer(Modifier.height(8.dp))
            if (setOutcome.broken.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.session_sets_broken, setOutcome.broken.size),
                    style = Type.titleM,
                    color = Palette.Deep,
                )
            }
            Text(
                text = stringResource(R.string.session_churu, setOutcome.churu),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }
        if (!hold) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.session_total_reps, state.totalReps),
                style = Type.titleM,
                color = Palette.TextPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        RunGrowthLines(growth = growth, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        PrimaryButton(text = stringResource(R.string.session_again), onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        // Another movement without going home for it.
        SecondaryButton(
            text = stringResource(R.string.home_change_exercise),
            onClick = onChangeExercise,
            modifier = Modifier.fillMaxWidth(),
        )
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

/**
 * What each life made, as bars: ten sets read at a glance, the fade across them included — which is
 * what sets taken to the edge look like, and nothing to apologise for. A life the camera gave back
 * is drawn faint.
 */
@Composable
private fun LivesChart(lives: List<LifeResult>, hold: Boolean, broken: Set<Int> = emptySet()) {
    val values = lives.map { if (hold) (it.survivedMs / 1000L).toInt() else it.reps }
    val top = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val count = lives.size.coerceAtLeast(1)
        val barWidth = minOf(36.dp, (maxWidth - gap * (count - 1)) / count)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            lives.forEachIndexed { i, life ->
                val value = values[i]
                Column(
                    modifier = Modifier.width(barWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A set that beat its own best is starred, in the record colour.
                    Text(
                        text = if (i in broken) "\u2605$value" else value.toString(),
                        style = Type.labelS,
                        color = if (i in broken) Palette.Deep else Palette.TextSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((CHART_HEIGHT * value / top).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (life.refunded) Palette.Bg3 else Palette.Deep),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = (i + 1).toString(),
                        style = Type.labelS,
                        color = Palette.TextTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The tallest bar, in dp. */
private const val CHART_HEIGHT = 64f
