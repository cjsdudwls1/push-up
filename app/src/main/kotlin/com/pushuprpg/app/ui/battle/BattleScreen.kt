package com.pushuprpg.app.ui.battle

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.pose.CameraPreview
import com.pushuprpg.app.pose.PoseLandmarkerSource
import com.pushuprpg.app.ui.components.KeepScreenOn
import com.pushuprpg.app.ui.components.exerciseHintRes
import com.pushuprpg.app.ui.components.FramingGuide
import com.pushuprpg.app.ui.components.NextLegChip
import com.pushuprpg.app.ui.components.PlacementBanner
import com.pushuprpg.app.ui.components.exerciseLabelRes
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.BodySide
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
) {
    KeepScreenOn()

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

    val reduceMotion = LocalReduceMotion.current
    val configuration = LocalConfiguration.current
    val compact = configuration.screenHeightDp < 700

    // Flares the overlay briefly when a rep lands, so the confirmation is visible even to someone
    // who cannot look directly at the screen mid-rep.
    var flare by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.reps) {
        if (state.reps > 0) {
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
        CameraPreview(
            source = poseSource,
            modifier = Modifier.fillMaxSize(),
            onCameraBound = { front -> mirrored = front },
        )

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

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (shakeOffset > 0f) {
                        translationX = sin(shakePhase.toFloat()) * shakeOffset
                        translationY = sin(shakePhase * 1.7f) * shakeOffset * 0.6f
                    }
                }
                .alpha(if (audioOnly) 0.12f else 1f)
        ) {
            BattleHudLayout(
                state = state,
                playerClass = playerClass,
                sessionBestDepth = sessionBestDepth,
                gaugeOnRight = gaugeOnRight,
                showGaugeNumber = showGaugeNumber,
                compact = compact,
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
                .clickable(onClick = onQuit),
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
        PlacementBanner(
            placement = state.placement,
            exercise = state.exercise,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
        )

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

        placementFor?.let { exercise ->
            Text(
                text = stringResource(exerciseLabelRes(exercise)) + " · " + stringResource(exerciseHintRes(exercise)),
                style = Type.bodyM,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 120.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.ScrimPanelHigh)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
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

@Composable
private fun BoxScope.BattleHudLayout(
    state: BattleState,
    playerClass: PlayerClass,
    sessionBestDepth: Float,
    gaugeOnRight: Boolean,
    showGaugeNumber: Boolean,
    compact: Boolean,
) {
    val colors = LocalGameColors.current

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
    ) {
        // The two combatants face each other across the top, as they did in the demo, but they are
        // drawn rather than pasted — the player's figure does the rep with the user.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 116.dp else 140.dp),
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
                    hurt = state.enemyHurt,
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
        val hold = Exercises.of(state.exercise).kind == MovementKind.HOLD
        val unit = if (hold) R.string.battle_count_seconds else R.string.battle_count_reps
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                HealthBar(
                    name = stringResource(R.string.battle_player_label),
                    hp = state.reps,
                    maxHp = state.runTotalReps.coerceAtLeast(1),
                    color = colors.playerHp,
                    label = stringResource(unit, state.reps) + " / " + state.runTotalReps,
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
        reps = state.reps,
        dimmed = state.paused,
        compact = compact,
        modifier = Modifier.align(Alignment.Center),
    )

    DamageNumbers(
        damages = state.damages,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(bottom = 200.dp),
    )

    DepthGauge(
        depth = state.depth,
        countEnter = state.countEnter,
        deepEnter = state.deepEnter,
        sessionBest = sessionBestDepth,
        showNumber = showGaugeNumber,
        modifier = Modifier
            .align(if (gaugeOnRight) Alignment.CenterEnd else Alignment.CenterStart)
            .padding(horizontal = 6.dp)
            .fillMaxHeight(0.62f),
    )

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UltimateWarning(
            visible = state.ultimateIncoming,
            repsLeft = state.ultimateRepsLeft,
            answers = state.ultimateAnswers,
            answersNeeded = com.pushuprpg.core.game.Encounter.ANSWERS_TO_BLOCK,
            playerClass = playerClass,
            exercise = state.exercise,
        )
        Spacer(Modifier.height(10.dp))
        AlertSlot(state)
        NextLegChip(next = state.nextFront, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        ComboPill(combo = state.combo)
    }
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

/** Damage numbers rise and fade. A deep hit adds a second number rather than replacing the first. */
@Composable
private fun DamageNumbers(
    damages: List<com.pushuprpg.core.run.FloatingDamage>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
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
                            y = (-60 * rise.value).dp,
                        )
                        .alpha(1f - rise.value * rise.value),
                )
            }
        }
    }
}

@Composable
private fun AlertSlot(state: BattleState) {
    val alert = state.alert
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
            AlertKey.SAME_LEG -> stringResource(
                R.string.battle_same_leg,
                stringResource(if (current?.arg == BodySide.LEFT.ordinal) R.string.leg_left else R.string.leg_right),
            )
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

