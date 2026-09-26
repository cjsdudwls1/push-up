package com.pushuprpg.app.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.components.CameraErrorCard
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.ModelErrorBanner
import com.pushuprpg.app.ui.components.rememberModelStalled
import com.pushuprpg.app.ui.components.exerciseHintRes
import com.pushuprpg.app.ui.components.FramingGuide
import com.pushuprpg.app.ui.components.PlacementBanner
import com.pushuprpg.app.ui.components.PrimaryButton
import com.pushuprpg.app.ui.components.SecondaryButton
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.run.Toast
import kotlin.math.sin

/**
 * The battle screen.
 *
 * Laid out for an unusual reading condition: the user is on the floor about a metre from the phone,
 * looking up at a steep angle, and for half of every rep their own shoulders cover the bottom of
 * the screen. So the counter sits at the vertical centre rather than the bottom, type is very
 * large, and nothing that matters lives in the lowest band.
 */
@Composable
fun BattleScreen(
    state: BattleState,
    playerClass: PlayerClass,
    poseSource: PoseLandmarkerSource,
    sessionBestDepth: Float,
    gaugeOnRight: Boolean,
    showGaugeNumber: Boolean,
    audioOnly: Boolean,
    onQuit: () -> Unit,
    onSwitchExercise: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier,
    /** The pose model did not load, and the app says so along the foot of the screen. */
    modelFailed: Boolean = false,
) {
    KeepScreenOn()
    val poseReady by poseSource.ready.collectAsState()

    var picking by remember { mutableStateOf(false) }
    // Where to put the phone for the movement just switched to — the one thing worth reading at
    // that moment — shown for a few seconds and then out of the way.
    var placementFor by remember { mutableStateOf<ExerciseType?>(null) }
    LaunchedEffect(placementFor) {
        if (placementFor != null) {
            kotlinx.coroutines.delay(PLACEMENT_HINT_MS)
            placementFor = null
        }
    }

    // Leaving asks first once the run has something in it, by the X and the back gesture alike: an
    // edge swipe made while straightening the phone used to pop the screen and the run with it.
    var confirmingQuit by remember { mutableStateOf(false) }
    val requestQuit: () -> Unit = { if (state.workDone) confirmingQuit = true else onQuit() }
    BackHandler {
        when {
            confirmingQuit -> confirmingQuit = false
            picking -> picking = false
            else -> requestQuit()
        }
    }

    val reduceMotion = LocalReduceMotion.current
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 700

    // Flares the overlay briefly when a rep lands, so the confirmation is visible even to someone
    // who cannot look directly at the screen mid-rep. Not with 모션 줄이기, which promises fewer
    // flashes: the count, the sound and the bar still say it.
    var flare by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.reps) {
        if (state.reps > 0 && !reduceMotion) {
            flare = 1f
            kotlinx.coroutines.delay(180)
            flare = 0f
        }
    }
    val animatedFlare by animateFloatAsState(flare, tween(140), label = "flare")

    // Shake is applied to the HUD only, never to the preview. Shaking live video of the real world
    // is nauseating and reads as a bug rather than as impact.
    val shakeOffset = if (reduceMotion) 0f else state.shake * 10f
    val shakePhase = state.reps * 7 + state.enemyHp

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        // Mirroring follows the camera that actually bound, not the one asked for. A device with
        // no usable front camera falls back to the back one, and mirroring that would put the
        // skeleton on the wrong side of the body.
        var mirrored by remember { mutableStateOf(true) }
        // Raised by the camera and cleared by it once it opens; the card's retry binds it again.
        var cameraFailed by remember { mutableStateOf(false) }
        var cameraAttempt by remember { mutableIntStateOf(0) }
        CameraPreview(
            source = poseSource,
            modifier = Modifier.fillMaxSize(),
            attempt = cameraAttempt,
            onCameraBound = { front -> mirrored = front },
            onCameraError = { failed -> cameraFailed = failed },
        )
        // A model that never answers reports no error. The source moves a silent GPU to the CPU by
        // itself; silent there as well, it is taken for one that did not load, and said the same
        // way, with a retry.
        val modelStalled = rememberModelStalled(source = poseSource, cameraFailed = cameraFailed)
        val noModel = modelFailed || modelStalled

        if (!audioOnly) {
            SkeletonOverlay(
                skeleton = state.render,
                depth = state.depth,
                countEnter = state.countEnter,
                deepEnter = state.deepEnter,
                flare = animatedFlare,
                mirrored = mirrored,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Setting up: a frame to fill and a ghost of the starting pose, over the live skeleton,
        // until the detector arms. Lining the lines up is easier than reading how to.
        val settingUp = state.reps == 0 && state.placement.advice.let {
            it != null && it != PlacementAdvice.READY
        }
        if (!audioOnly && settingUp) {
            FramingGuide(exercise = state.exercise)
        }

        EdgeScrims()

        // 절전 집중 모드: the screen goes almost black and the game continues entirely by ear. It
        // is the accessibility mode, the battery fix and the honest test of the audio design, all
        // at once.
        if (audioOnly) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)))
        }

        // The HUD's own layer, shared by its messages at the foot of the screen: they shake and dim
        // with it.
        val hudLayer = Modifier
            .graphicsLayer {
                if (shakeOffset > 0f) {
                    translationX = sin(shakePhase.toFloat()) * shakeOffset
                    translationY = sin(shakePhase * 1.7f) * shakeOffset * 0.6f
                }
            }
            .alpha(if (audioOnly) 0.12f else 1f)
        Box(Modifier.fillMaxSize().then(hudLayer)) {
            BattleHudLayout(
                state = state,
                playerClass = playerClass,
                sessionBestDepth = sessionBestDepth,
                gaugeOnRight = gaugeOnRight,
                showGaugeNumber = showGaugeNumber,
                compact = compact,
                // Only before anything is done: a plank counts no reps, and its seconds must not
                // go out mid-hold because the coach has something to say.
                settingUp = settingUp && !state.workDone,
            )
        }

        // Close sits top-left, away from where a hand lands when pushing up.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, top = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Palette.ScrimPanelHigh)
                .clickable(onClick = requestQuit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = Palette.TextPrimary,
            )
        }

        // Where the phone and the user are, said live while it matters and silent when it does
        // not. When tracking drops mid-fight this is also what explains the boss standing still.
        // Not while the camera will not open: the card says why, and a placement line under it
        // would only send the user looking in the wrong place. Nor with no model, whose error takes
        // this place; until the model's first frame it says the camera is getting ready. The
        // switch hint is placement advice too, and gives way the same.
        val placementShown = !cameraFailed && !noModel
        val placementSpeaking = placementShown && (!poseReady || state.placement.advice != null)
        // Tracking lost and found are the placement line's to explain while it is up. The wind-up's
        // words are the warning's own title, up for as long as the wind-up lasts: as a toast as well
        // they were said twice, and after it they would announce one that is over.
        val alert = state.alert?.takeUnless {
            it.textKey == AlertKey.ULTIMATE_INCOMING ||
                (placementSpeaking && (it.textKey == AlertKey.QUALITY_LOST || it.textKey == AlertKey.QUALITY_RECOVERED))
        }
        // Everything said along the foot of the screen, in one column and a fixed order. The lines
        // used to be placed apart by hand, and a tracking notice or the combo landed on 아래쪽이
        // 잘려요 at the moment it most needed reading.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = BOTTOM_MARGIN),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = hudLayer,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UltimateWarning(
                    visible = state.ultimateIncoming,
                    repsLeft = state.ultimateRepsLeft,
                    answers = state.ultimateAnswers,
                    answersNeeded = com.pushuprpg.core.game.Encounter.ANSWERS_TO_BLOCK,
                    playerClass = playerClass,
                    exercise = state.exercise,
                )
                AlertSlot(alert)
                ComboPill(combo = state.combo)
            }
            // The lowest band is the placement line's even while it is silent. The HUD's messages
            // stay above it, where the user's own shoulders do not cover them at the bottom of a rep.
            Column(
                modifier = Modifier.heightIn(min = PLACEMENT_BAND),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
            ) {
                if (placementShown) {
                    placementFor?.let { exercise ->
                        Text(
                            text = stringResource(exerciseLabelRes(exercise)) + " · " + stringResource(exerciseHintRes(exercise)),
                            style = Type.bodyM,
                            color = Palette.TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Palette.ScrimPanelHigh)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                    PlacementBanner(
                        placement = state.placement,
                        exercise = state.exercise,
                        preparing = !poseReady,
                    )
                }
            }
        }

        // Top right, opposite the close button: switching is a between-sets act, done standing in
        // front of the phone, so it can sit where the hands are not.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(end = 16.dp, top = 8.dp)
                .height(48.dp)
                .clip(CircleShape)
                .background(Palette.ScrimPanelHigh)
                .clickable { picking = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(exerciseLabelRes(state.exercise)),
                style = Type.labelL,
                color = Palette.TextPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(R.string.battle_switch_title),
                tint = Palette.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (modelStalled && !modelFailed) {
            ModelErrorBanner(Modifier.align(Alignment.BottomCenter), onRetry = poseSource::retry)
        }

        if (cameraFailed) {
            CameraErrorCard(
                onRetry = {
                    cameraFailed = false
                    cameraAttempt++
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (picking) {
            ExerciseSwitcher(
                current = state.exercise,
                onPick = { picked ->
                    picking = false
                    if (picked != state.exercise) {
                        onSwitchExercise(picked)
                        placementFor = picked
                    }
                },
                onDismiss = { picking = false },
            )
        }

        if (confirmingQuit) {
            QuitConfirm(
                reps = state.reps,
                heldSeconds = (state.heldMs / 1000L).toInt(),
                onQuit = onQuit,
                onResume = { confirmingQuit = false },
            )
        }
    }
}

/**
 * Asked before a run with something in it ends.
 *
 * Quitting loses nothing — the run is banked either way, and the line says so — so this only asks
 * whether it was meant. The fight carries on underneath: nothing in a dungeon hurts over time.
 */
@Composable
private fun BoxScope.QuitConfirm(
    reps: Int,
    heldSeconds: Int,
    onQuit: () -> Unit,
    onResume: () -> Unit,
) {
    Box(
        Modifier
            .matchParentSize()
            .background(Palette.ScrimPanelHigh)
            .clickable(onClick = onResume),
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
            text = stringResource(R.string.battle_paused_title),
            style = Type.titleL,
            color = Palette.TextPrimary,
        )
        Text(
            // A plank counts no reps; what it keeps is the time held.
            text = if (reps > 0) stringResource(R.string.battle_quit_confirm, reps)
            else stringResource(R.string.battle_quit_confirm_hold, heldSeconds),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        PrimaryButton(text = stringResource(R.string.battle_resume), onClick = onResume)
        SecondaryButton(text = stringResource(R.string.battle_quit), onClick = onQuit)
    }
}

/**
 * Every movement, two to a row, over the camera.
 *
 * Big targets and nothing to read but names: this is opened standing up between sets, often with
 * chalky or sweaty hands, and the only decision is which movement is next.
 */
@Composable
private fun BoxScope.ExerciseSwitcher(
    current: ExerciseType,
    onPick: (ExerciseType) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .matchParentSize()
            .background(Palette.ScrimPanelHigh)
            .clickable(onClick = onDismiss),
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
            text = stringResource(R.string.battle_switch_title),
            style = Type.titleL,
            color = Palette.TextPrimary,
        )
        Text(
            text = stringResource(R.string.battle_switch_sub),
            style = Type.bodyM,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        ExerciseType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { exercise ->
                    val isCurrent = exercise == current
                    Text(
                        text = stringResource(exerciseLabelRes(exercise)) +
                            if (isCurrent) " · " + stringResource(R.string.battle_switch_current) else "",
                        style = Type.labelL,
                        color = if (isCurrent) Palette.TextOnAccent else Palette.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) Palette.Brand500 else Palette.Bg3)
                            .clickable { onPick(exercise) }
                            .padding(vertical = 16.dp),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.action_close),
            style = Type.labelL,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(vertical = 14.dp),
        )
    }
}

private const val PLACEMENT_HINT_MS = 7_000L

/** How much of a struck monster's white flash 모션 줄이기 keeps. */
private const val REDUCED_HURT_FLASH = 0.3f

/** From the navigation bar to the lowest line said along the foot of the screen. */
private val BOTTOM_MARGIN = 24.dp

/**
 * The band the placement line keeps at the foot of the screen, said or silent: one line of advice
 * and the parts it names.
 */
private val PLACEMENT_BAND = 72.dp

@Composable
private fun BoxScope.BattleHudLayout(
    state: BattleState,
    playerClass: PlayerClass,
    sessionBestDepth: Float,
    gaugeOnRight: Boolean,
    showGaugeNumber: Boolean,
    compact: Boolean,
    settingUp: Boolean,
) {
    val colors = LocalGameColors.current
    // While setting up, the middle of the screen is the ghost's: a 0 at 120sp and an idle gauge sat
    // over the pose being lined up, and the fighters over its feet. They come back once the
    // detector has armed.
    val reduceMotion = LocalReduceMotion.current
    val shown by animateFloatAsState(
        targetValue = if (settingUp) 0f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 300),
        label = "setup",
    )
    // A hold counts no reps: what it has done is the time held, the same seconds the run banks and
    // the unit its total is counted in. The seconds held while the last monster falls are banked
    // too, and the result screen shows them, but they are past the total: the HUD stops at it
    // rather than reading 37초 / 36.
    val hold = Exercises.of(state.exercise).kind == MovementKind.HOLD
    val done = if (hold) (state.heldMs / 1000L).toInt().coerceAtMost(state.runTotalReps) else state.reps
    // How far down the HUD reaches, status bar included, so the gauge can start under it.
    var hudHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { hudHeightPx = it.height }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
    ) {
        // The two combatants face each other across the top, as they did in the demo, but they are
        // drawn rather than pasted — the player's figure does the rep with the user.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 116.dp else 140.dp)
                .alpha(0.5f + 0.5f * shown),
            verticalAlignment = Alignment.Bottom,
        ) {
            Fighter(
                playerClass = playerClass,
                state = state.playerAnim,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(Modifier.width(12.dp))
            Monster(
                visual = MonsterVisual(
                    id = state.enemyId,
                    isBoss = state.enemyIsBoss,
                    // A struck monster flashes white; with 모션 줄이기 it only pales a little.
                    hurt = if (reduceMotion) state.enemyHurt * REDUCED_HURT_FLASH else state.enemyHurt,
                    telegraph = state.telegraphCharge,
                    death = state.enemyDeath,
                    timeMs = state.elapsedMs,
                ),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(6.dp))
        // The big bars are counts, not health: the left is what the user has done, the right what
        // this monster still owes, both in the movement they chose. Health is the thin strip under
        // the left one — only a monster's unanswered ultimate touches it, and never a rest.
        val unit = if (hold) R.string.battle_count_seconds else R.string.battle_count_reps
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                HealthBar(
                    name = stringResource(R.string.battle_player_label),
                    hp = done,
                    maxHp = state.runTotalReps.coerceAtLeast(1),
                    color = colors.playerHp,
                    label = stringResource(unit, done) + " / " + state.runTotalReps,
                )
                Spacer(Modifier.height(4.dp))
                HpStrip(hp = state.playerHp, maxHp = state.playerMaxHp)
            }
            Spacer(Modifier.width(20.dp))
            HealthBar(
                name = state.enemyName,
                hp = state.enemyHp,
                maxHp = state.enemyMaxHp,
                color = colors.bossHp,
                alignEnd = true,
                label = stringResource(
                    if (hold) R.string.battle_remaining_seconds else R.string.battle_remaining,
                    state.enemyHp,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
        FloorPips(state.floorIndex, state.floorCount)
    }

    // The counter, dead centre. Dimmed while tracking is lost so the user can tell at a glance
    // that the game is waiting for them rather than ignoring them.
    RepCounter(
        count = done,
        unitRes = if (hold) R.string.battle_unit_seconds else R.string.battle_unit_reps,
        dimmed = state.paused,
        compact = compact,
        modifier = Modifier.align(Alignment.Center).alpha(shown),
    )

    DamageNumbers(
        damages = state.damages,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(bottom = 200.dp),
    )

    // Between the HUD and the placement line's band. It was 62% of the screen's height, centred,
    // whatever the HUD above took, and reached up into the monster's bar and its 남은 N개. The
    // bottom is the band's fixed top rather than the column above it: the gauge is watched the
    // whole set, and its lines must not move each time a toast comes or goes.
    DepthGauge(
        depth = state.depth,
        countEnter = state.countEnter,
        deepEnter = state.deepEnter,
        sessionBest = sessionBestDepth,
        showNumber = showGaugeNumber,
        modifier = Modifier
            .align(if (gaugeOnRight) Alignment.CenterEnd else Alignment.CenterStart)
            .alpha(shown)
            .padding(top = with(density) { hudHeightPx.toDp() })
            .navigationBarsPadding()
            .padding(bottom = BOTTOM_MARGIN + PLACEMENT_BAND)
            .padding(horizontal = 6.dp)
            .fillMaxHeight(),
    )
}

/** Which floor of the dungeon, as pips rather than "2 / 3" — read faster, no numerals to parse. */
@Composable
private fun FloorPips(floorIndex: Int, floorCount: Int) {
    val colors = LocalGameColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(floorCount) { i ->
            Box(
                Modifier
                    .height(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            i < floorIndex -> colors.accept
                            i == floorIndex -> Palette.TextPrimary
                            else -> Palette.TextDisabled.copy(alpha = 0.5f)
                        }
                    )
            )
        }
    }
}

/**
 * Damage numbers rise and fade — with 모션 줄이기 they only fade, where they appeared. A deep hit
 * adds a second number rather than replacing the first.
 */
@Composable
private fun DamageNumbers(
    damages: List<com.pushuprpg.core.run.FloatingDamage>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val reduceMotion = LocalReduceMotion.current
    Box(modifier) {
        damages.takeLast(4).forEach { damage ->
            key(damage.id) {
                // Derived from the number's own id so it stays put: using its position in the
                // window made every surviving number slide sideways as new ones arrived.
                val lane = (damage.id % 3L).toInt() - 1
                val rise = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(damage.id) { rise.animateTo(1f, tween(900)) }
                CameraText(
                    text = damage.amount.toString(),
                    style = if (damage.crit || damage.deep) Type.displayL else Type.displayM,
                    color = when {
                        damage.crit -> Palette.DeepSoft
                        damage.deep -> colors.deep
                        else -> Palette.TextPrimary
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            x = (lane * 26).dp,
                            y = if (reduceMotion) 0.dp else (-60 * rise.value).dp,
                        )
                        .alpha(1f - rise.value * rise.value),
                )
            }
        }
    }
}

@Composable
private fun AlertSlot(alert: Toast?) {
    // The content lambda keeps recomposing through the exit animation, by which point the live
    // alert is already null. Holding the last one means the message fades out as itself rather
    // than flickering into whatever the fallback happens to be.
    var shown by remember { mutableStateOf<Toast?>(null) }
    if (alert != null) shown = alert

    AnimatedVisibility(
        visible = alert != null,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(220)),
    ) {
        val current = shown
        val key = current?.textKey ?: AlertKey.IDLE
        val text = when (key) {
            AlertKey.BOOTSTRAP -> stringResource(R.string.battle_bootstrap)
            AlertKey.CALIBRATED -> stringResource(R.string.battle_calibrated)
            AlertKey.SHALLOW_TWICE -> stringResource(R.string.battle_shallow_twice)
            AlertKey.SHALLOW_FOUR -> stringResource(R.string.battle_shallow_four)
            AlertKey.SHALLOW_PULL -> stringResource(R.string.battle_shallow_pull)
            AlertKey.COMBO_BROKEN -> stringResource(R.string.battle_combo_broken)
            AlertKey.COMBO_MILESTONE -> stringResource(R.string.battle_combo_milestone, current?.arg ?: 0)
            AlertKey.IDLE -> stringResource(R.string.battle_idle)
            AlertKey.BOSS_LOW_HP -> stringResource(R.string.battle_boss_low_hp)
            AlertKey.ULTIMATE_INCOMING -> stringResource(R.string.battle_ultimate_incoming)
            AlertKey.ULTIMATE_BLOCKED -> stringResource(R.string.battle_ultimate_blocked)
            AlertKey.ULTIMATE_HIT -> stringResource(R.string.battle_ultimate_hit, current?.arg ?: 0)
            AlertKey.DEEP_STRIKE -> stringResource(R.string.battle_deep_strike)
            AlertKey.QUALITY_LOST -> stringResource(R.string.quality_paused_notice)
            AlertKey.QUALITY_RECOVERED -> stringResource(R.string.quality_recovered)
            AlertKey.NOT_SPLIT -> stringResource(R.string.battle_not_split)
            AlertKey.STYLE_TOO_QUICK -> stringResource(R.string.battle_style_too_quick)
            AlertKey.STYLE_NOT_FULL -> stringResource(R.string.battle_style_not_full)
            AlertKey.STYLE_LAGGING -> stringResource(R.string.battle_style_lagging)
        }
        Text(
            text = text,
            style = Type.bodyL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.ScrimPanelHigh)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

