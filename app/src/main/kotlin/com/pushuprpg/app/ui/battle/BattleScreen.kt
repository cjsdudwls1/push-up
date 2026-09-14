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
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.detect.PoseQuality
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
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()

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

        CameraPreview(source = poseSource, modifier = Modifier.fillMaxSize())

        if (!audioOnly) {
            SkeletonOverlay(
                skeleton = state.render,
                depth = state.depth,
                countEnter = state.countEnter,
                deepEnter = state.deepEnter,
                flare = animatedFlare,
                modifier = Modifier.fillMaxSize(),
            )
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

        QualityBanner(
            quality = state.quality,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
        )
    }
}

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
        Row(verticalAlignment = Alignment.Top) {
            HealthBar(
                name = stringResource(R.string.battle_player_label),
                hp = state.playerHp,
                maxHp = state.playerMaxHp,
                color = colors.playerHp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            HealthBar(
                name = state.enemyName,
                hp = state.enemyHp,
                maxHp = state.enemyMaxHp,
                color = colors.bossHp,
                alignEnd = true,
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
        UltimateWarning(visible = state.ultimateIncoming)
        Spacer(Modifier.height(10.dp))
        AlertSlot(state)
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
            AlertKey.DEEP_STRIKE -> stringResource(R.string.battle_deep_strike)
            AlertKey.QUALITY_LOST -> stringResource(R.string.quality_paused_notice)
            AlertKey.QUALITY_RECOVERED -> stringResource(R.string.quality_recovered)
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

/**
 * Says why the game is waiting.
 *
 * The boss stops attacking whenever tracking is lost, and the user has to be told that — silence
 * here would look like the app had simply stopped counting their work.
 */
@Composable
private fun QualityBanner(quality: PoseQuality, modifier: Modifier = Modifier) {
    val message = when (quality) {
        PoseQuality.OK -> null
        PoseQuality.NO_SUBJECT -> stringResource(R.string.quality_no_subject)
        PoseQuality.LOW_CONFIDENCE -> stringResource(R.string.quality_low_confidence)
        PoseQuality.OUT_OF_FRAME -> stringResource(R.string.quality_out_of_frame)
        PoseQuality.UNSTABLE_CAMERA -> stringResource(R.string.quality_unstable_camera)
        PoseQuality.SUBJECT_SWITCH -> stringResource(R.string.quality_subject_switch)
        PoseQuality.TORSO_ROTATED -> stringResource(R.string.quality_torso_rotated)
        PoseQuality.IMPLAUSIBLE_RATE -> stringResource(R.string.quality_implausible_rate)
    }

    // Same reason as AlertSlot: without this, recovering from a lost track collapses the banner to
    // an empty pill and fades that instead of the sentence.
    var lastMessage by remember { mutableStateOf("") }
    if (message != null) lastMessage = message

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300)),
        modifier = modifier,
    ) {
        Text(
            text = lastMessage,
            style = Type.bodyL,
            color = Palette.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.ScrimPanelHigh)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
