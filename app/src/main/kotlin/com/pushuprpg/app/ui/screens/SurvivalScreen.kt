package com.pushuprpg.app.ui.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlin.math.ceil
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.pushuprpg.app.ui.components.FxCue
import com.pushuprpg.app.ui.components.PixelLives
import com.pushuprpg.app.ui.components.PixelRoom
import com.pushuprpg.app.ui.components.RoomGeometry
import com.pushuprpg.app.ui.theme.Galmuri
import com.pushuprpg.app.ui.theme.Silkscreen
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.pushuprpg.app.ui.components.PixelFxLayer
import com.pushuprpg.app.ui.components.rememberPixelFx
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pushuprpg.app.ui.theme.PixelCircle
import com.pushuprpg.app.ui.theme.PixelShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.domain.HapticStrength
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.share.ShareCardData
import com.pushuprpg.app.ui.components.CameraErrorCard
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.ModelErrorBanner
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.PixelIcon
import com.pushuprpg.app.ui.components.PixelIcons
import com.pushuprpg.app.ui.components.RoundIconButton
import com.pushuprpg.app.ui.components.RunGrowthLines
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.SurvivalDepthGauge
import com.pushuprpg.app.ui.components.WideCameraControl
import com.pushuprpg.app.ui.components.cardSurface
import com.pushuprpg.app.ui.components.catHeadTop
import com.pushuprpg.app.ui.components.drawCat
import com.pushuprpg.app.ui.components.drawCatToyBeside
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
import com.pushuprpg.core.progression.WearSlot
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatMood
import com.pushuprpg.core.survival.CatPhase
import com.pushuprpg.core.survival.CatSession
import com.pushuprpg.core.survival.CatSessionState
import com.pushuprpg.core.survival.SetResult
import com.pushuprpg.core.survival.CatSpeech
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.SurvivalState
import kotlinx.coroutines.delay

/**
 * 고양이 지켜줘.
 *
 * A deliberately warm, round register: no skeleton and no numbers on the run — the descending
 * ceiling is the gauge of the run. How deep a rep is, though, the ceiling cannot say, so a plain bar
 * at the side does (owner's request, 2026-11-10). This is the mode people are shown first and the
 * one they send to a friend, so it must not look like sports science.
 *
 * A session is ten lives — ten sets — with a rest between them that the screen counts down, over
 * when they are lost or when the user ends it with the close button or back; the tutorial is one set. See
 * [com.pushuprpg.core.survival.CatSession].
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
    /** The rest card's 건너뛰기: the next set now, waiting for the user as after a full rest. */
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
    /** How deep the user is now, 0..100; read only where the gauge draws. 0 when there is none. */
    depth: () -> Float = { 0f },
    /** The detector's 1회 and 깊게 thresholds, on the same scale. */
    depthLines: Pair<Float, Float> = 70f to 88f,
    /** The pixel bursts: a push landing, a deep one, a combo, a record. See PixelFx. */
    fx: Flow<FxCue> = emptyFlow(),
    /** As the user typed it; blank is the default name. */
    catName: String = "",
    catCoat: CatCoat = CatCoat.CREAM,
    /** What the cat has on. A scene stays at home: under the ceiling is the only place this cat is. */
    catWear: Set<CatItem> = emptySet(),
    /** 광각으로 찍기: the widest front camera, zoomed out. Switched here or in the settings. */
    wideCamera: Boolean = false,
    /** How wide: 0 for as wide as it goes, otherwise a ratio saved by an earlier build. */
    wideZoom: Float = 0f,
    onWideCameraChange: (Boolean) -> Unit = {},
    /** This movement's best per set before this session: what each set is played against. */
    setBests: SetBests = SetBests(),
    /** What the banked session did to the set bests; null until it is written. */
    setOutcome: SetOutcome? = null,
    /** The settings, for the gear's panel; null hides the gear. */
    settings: AppSettings? = null,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onPreviewHaptic: (HapticStrength) -> Unit = {},
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
    // The gear's panel. Composed after the handler above, so back closes the panel first.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

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
    // The widest the open camera goes, once it has said.
    var wideLeast by remember { mutableStateOf<Float?>(null) }
    val poseReady by poseSource.ready.collectAsState()
    // A model that never answers reports no error. The source moves a silent GPU to the CPU by
    // itself; silent there as well, it is taken for one that did not load, said the same way with a
    // retry, and the tutorial offers its skip for it.
    val modelStalled = rememberModelStalled(source = poseSource, cameraFailed = cameraFailed)
    val noModel = modelFailed || modelStalled

    BoxWithConstraints(modifier.fillMaxSize().background(ROOM_DARK)) {

        // The pose is read from the camera, so it always runs; its picture shows only while
        // setting up, which is lining up with it, by the owner's choice (2026-11-10): a set is
        // played in the pixel demo's dark room. The room covers it the rest of the time.
        CameraPreview(
            source = poseSource,
            modifier = Modifier.fillMaxSize(),
            attempt = cameraAttempt,
            wide = wideCamera,
            wideZoom = wideZoom,
            onWideRange = { wideLeast = it },
            onCameraError = { failed -> cameraFailed = failed },
        )

        // Until the run starts the lower part of the picture is the framing guide's: the ghost's head
        // and arms for a pushup sit where the cat does, so the cat is drawn faint underneath it.
        val settingUp = playing && !life.started && life.alive
        if (settingUp) Box(Modifier.fillMaxSize().background(SETUP_WASH))

        val reduceMotion = LocalReduceMotion.current
        val pixelFx = rememberPixelFx(fx, reduceMotion)
        val density = LocalDensity.current
        val room = RoomGeometry(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
        val u = room.u

        // Resting, the ceiling is back up: a crushed cat is not what a minute of rest looks at.
        PixelRoom(
            state = if (resting) life.copy(height = 1f, intensity = 0f) else life,
            cat = cat,
            coat = catCoat,
            wear = catWear,
            fx = pixelFx,
            setUp = settingUp,
            holdUp = life.started && !resting,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shake = pixelFx.shake()
                    translationX = shake.x.roundToInt() * u
                    translationY = shake.y.roundToInt() * u
                },
        )
        PixelFxLayer(pixelFx, modifier = Modifier.fillMaxSize())

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

        if (playing && life.started && life.alive && !hold) {
            // Where the demo has it: its right edge four of the art's pixels in from the room's.
            SurvivalDepthGauge(
                depth = depth,
                countAt = depthLines.first,
                deepAt = depthLines.second,
                u = u,
                modifier = Modifier.offset {
                    IntOffset(room.x(188f).roundToInt(), room.y(96f).roundToInt())
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The demo's HUD: the pixel face at whole multiples of the art's pixels, with a hard
            // one-pixel shadow. Galmuri is drawn on an 11-pixel grid, so 11u is one font pixel a pixel.
            val hud = remember(u, density) { PixelHud(u, density) }
            Text(
                text = stringResource(R.string.survival_score, state.totalScore),
                style = hud.big,
                color = HUD_TEXT,
            )
            // Not in the tutorial: a best of 0 says nothing to someone who has never played. The best
            // is this movement's own, and says so — a pull-up's score is not a pushup's.
            if (!isTutorial) {
                if (bestScore > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.survival_best_of, stringResource(exerciseLabelRes(exercise)), bestScore),
                        style = hud.small,
                        color = HUD_DIM,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val livesSaid = stringResource(R.string.survival_lives_left, state.livesLeft)
                PixelLives(
                    left = state.livesLeft,
                    u = u,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (12f * u).toDp() })
                        .semantics { contentDescription = livesSaid },
                )
                // The set against its own best, by the owner's decision: the number is what makes
                // breaking it worth chasing. Once the set has started; while setting up there is
                // nothing yet to count.
                if (playing && life.started) {
                    Spacer(Modifier.height(8.dp))
                    SetCounter(
                        set = state.ended.size + 1,
                        now = if (hold) (life.elapsedMs / 1000L).toInt() else life.reps,
                        best = setBests.at(state.ended.size),
                        hold = hold,
                        hud = hud,
                    )
                    // The demo's combo, gold once it is long or the ceiling is close.
                    if (!hold && life.combo > 0) {
                        Text(
                            text = stringResource(R.string.survival_combo, life.combo),
                            style = hud.combo,
                            color = if (life.intensity > 0.6f || life.combo >= 20) Color(0xFFFFE066) else Color(0xFFFFC27A),
                        )
                    }
                }
            }
            // The ceiling waits for the user to be in position, and says so — a still ceiling with
            // no explanation reads as a broken one. The tutorial says it in its intro.
            if (settingUp) {
                Spacer(Modifier.height(10.dp))
                if (isTutorial) {
                    TutorialIntro(modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    CameraText(
                        text = stringResource(R.string.survival_waiting_with, stringResource(exerciseLabelRes(exercise))),
                        style = Type.bodyM.copy(textAlign = TextAlign.Center),
                        color = Palette.TextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
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
                    .clip(PixelCircle)
                    .background(Palette.ScrimPanelHigh)
                    .clickable(onClick = close),
                contentAlignment = Alignment.Center,
            ) {
                PixelIcon(
                    sprite = PixelIcons.Close,
                    tint = Palette.TextPrimary,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Top right, across from the close button: 광각 on and off in one tap, and the gear. The
        // camera switches without the run stopping; the ceiling does not wait for it, as it waits
        // for nothing. The gear is there at every moment of a run, by the owner's request
        // (2026-11-06): the sound, the music, the buzz and the rest, changed where the run is.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!cameraFailed && state.phase != CatPhase.OVER) {
                WideCameraControl(
                    on = wideCamera,
                    least = wideLeast,
                    onToggle = { on ->
                        wideLeast = null
                        onWideCameraChange(on)
                    },
                )
            }
            if (settings != null) {
                RoundIconButton(
                    icon = PixelIcons.Gear,
                    label = stringResource(R.string.action_settings),
                    onClick = { settingsOpen = true },
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
                    .clip(PixelCircle)
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

        if (settingsOpen && settings != null) {
            RunSettingsSheet(
                settings = settings,
                onChange = onSettingsChange,
                onPreviewHaptic = onPreviewHaptic,
                // Said only while a set is on: then the ceiling is moving behind the panel.
                ceilingMoving = playing && state.started,
                onDismiss = { settingsOpen = false },
            )
        }
    }
}

/**
 * The gear's panel, over the bottom of the run. Nothing pauses for it — the ceiling never does, by
 * the owner's decision — so it says so while a set is on, and closes with a tap above it, its
 * button or back. What it changes is heard at once: the run reads the settings live.
 */
@Composable
private fun BoxScope.RunSettingsSheet(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onPreviewHaptic: (HapticStrength) -> Unit,
    ceilingMoving: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .matchParentSize()
            .background(Palette.ScrimPanel)
            .clickable(onClickLabel = stringResource(R.string.action_close), onClick = onDismiss),
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .clip(PixelShape(topStart = 24.dp, topEnd = 24.dp, bottomEnd = 0.dp, bottomStart = 0.dp))
            .background(Palette.Bg1)
            // Taps inside the panel stay in it rather than falling through to the scrim's close:
            // a pointer handler, even an empty one, makes the panel the one hit.
            .pointerInput(Unit) {}
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.run_settings_title),
            style = Type.titleM,
            color = Palette.TextPrimary,
        )
        if (ceilingMoving) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.run_settings_ceiling),
                style = Type.bodyM,
                color = Palette.TextSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        RunSettingsPanel(
            settings = settings,
            onChange = onChange,
            onPreviewHaptic = onPreviewHaptic,
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = stringResource(R.string.action_close), onClick = onDismiss)
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

/** How long the tutorial waits for its run to start before it offers 건너뛰기. */
private const val SKIP_OFFERED_AFTER_MS = 10_000L

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(PixelShape(18.dp))
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
    }
}

/**
 * The tutorial's ending.
 *
 * It reports the reps rather than the score, because telling a beginner their score before they
 * know what a good one is invites the wrong comparison — and then what comes next: as many sets as
 * they like, with a rest between them.
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
            .cardSurface(shape = PixelShape(22.dp), color = Palette.Bg1)
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
private fun SetCounter(set: Int, now: Int, best: Int, hold: Boolean, hud: PixelHud, modifier: Modifier = Modifier) {
    val beaten = best > 0 && now > best
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(
                when {
                    best <= 0 && hold -> R.string.set_now_first_hold
                    best <= 0 -> R.string.set_now_first
                    hold -> R.string.set_now_best_hold
                    else -> R.string.set_now_best
                },
                set, now, best,
            ),
            style = hud.small,
            color = if (beaten) Palette.Deep else HUD_TEXT,
        )
        if (beaten) {
            Text(
                text = stringResource(R.string.set_record_broken),
                style = hud.small,
                color = Palette.Deep,
            )
        }
    }
}

/** The demo's HUD faces, in whole multiples of one of the art's pixels ([u]). */
private class PixelHud(u: Float, private val density: Density) {
    private val shadow = Shadow(color = Color(0xFF07081A), offset = Offset(u, u), blurRadius = 0f)
    private fun face(px: Float, family: FontFamily, weight: FontWeight) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = with(density) { px.toSp() },
        lineHeight = with(density) { (px * 1.15f).toSp() },
        textAlign = TextAlign.Center,
        shadow = shadow,
    )
    val big = face(22f * u, Galmuri, FontWeight.Bold)
    val small = face(11f * u, Galmuri, FontWeight.Bold)
    val combo = face(16f * u, Silkscreen, FontWeight.Normal)
}

private val HUD_TEXT = Color(0xFFFFF4E0)
private val HUD_DIM = Color(0xFFA9AED6)
private val ROOM_DARK = Color(0xFF0A0B22)

/** Over the camera while setting up: dark enough for the HUD, light enough to line up by. */
private val SETUP_WASH = Color(0x8C07081A)

/**
 * The rest between two sets.
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
        // Narrowed on a phone on its side (a plank's or a pushup's), where full width is a banner,
        // not a card; and kept off the cutout, which is at the side there.
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .cardSurface(shape = PixelShape(28.dp), color = Palette.Bg1)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.result_rest_title),
            style = Type.labelL,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        // The countdown inside a ring that empties with it — the hub's ring, read from across the room.
        val left = if (state.restLengthMs > 0L) state.restLeftMs.toFloat() / state.restLengthMs else 0f
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            val track = Palette.Bg3
            val ring = Palette.Brand500
            Canvas(Modifier.matchParentSize()) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (left > 0f) {
                    drawArc(
                        ring, -90f, 360f * left.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Text(
                text = stringResource(R.string.result_rest_time, seconds / 60, seconds % 60),
                style = Type.displayM,
                color = Palette.TextPrimary,
            )
        }
        Spacer(Modifier.height(14.dp))
        val heartU = with(LocalDensity.current) { 1.4.dp.toPx() }
        PixelLives(
            left = state.livesLeft,
            u = heartU,
            modifier = Modifier.fillMaxWidth().height(18.dp),
        )
        Spacer(Modifier.height(10.dp))
        val next = state.ended.size + 1
        Text(
            text = when {
                nextBest <= 0 -> stringResource(R.string.rest_next_first, next)
                hold -> stringResource(R.string.rest_next_best_hold, next, nextBest)
                else -> stringResource(R.string.rest_next_best, next, nextBest)
            },
            style = Type.titleM,
            color = Palette.Brand400,
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
        // Outlined, not filled: it is there, but resting is what the card asks for.
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            shape = PixelCircle,
            border = BorderStroke(2.dp, Palette.Brand500),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Palette.BrandWash,
                contentColor = Palette.Brand500,
            ),
        ) {
            Text(text = stringResource(R.string.rest_skip), style = Type.titleM, maxLines = 1)
        }
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
            .cardSurface(shape = PixelShape(22.dp), color = Palette.Bg1)
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
        SetsChart(sets = state.ended, hold = hold, broken = setOutcome?.broken.orEmpty().toSet())
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
 * What each set made, as bars: the sets read at a glance, the fade across them included — which is
 * what sets taken to the edge look like, and nothing to apologise for. A session has as many as the
 * user played; past what fits with a number over each bar, the bars scroll rather than shrink.
 */
@Composable
private fun SetsChart(sets: List<SetResult>, hold: Boolean, broken: Set<Int> = emptySet()) {
    val values = sets.map { if (hold) (it.survivedMs / 1000L).toInt() else it.reps }
    val top = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val count = sets.size.coerceAtLeast(1)
        val fit = (maxWidth - gap * (count - 1)) / count
        val barWidth = fit.coerceIn(MIN_BAR_WIDTH, 36.dp)
        Row(
            modifier = if (fit < MIN_BAR_WIDTH) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            sets.forEachIndexed { i, _ ->
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
                            .clip(PixelShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 0.dp, bottomStart = 0.dp))
                            .background(Palette.Deep),
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

/** The narrowest a bar is drawn: a three-figure count over it still reads. */
private val MIN_BAR_WIDTH = 22.dp
