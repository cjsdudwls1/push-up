package com.pushuprpg.app.ui.battle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.core.anim.AnimClip
import com.pushuprpg.core.anim.AnimState
import com.pushuprpg.core.game.PlayerClass
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Characters drawn from shapes rather than from sprite sheets.
 *
 * There is no art budget and no animator, and a geometric figure that moves correctly reads far
 * better than a static sticker that does not — the demo's problem was never resolution, it was that
 * the avatar did the same thing every rep regardless of what the user did.
 *
 * The rig is deliberately tiny: a handful of joint positions in a unit-height local space, a pose
 * that is just angles and offsets, and linear interpolation between named poses. Everything else —
 * class identity, weapon, colour, impact flash — is decoration layered on the same skeleton.
 */
private data class Pose(
    /** Torso lean in degrees; positive leans toward the enemy. */
    val lean: Float,
    /** 0..1, how much the knees are bent and the whole figure compressed. */
    val crouch: Float,
    /** Lead arm angle in degrees, measured from straight down. */
    val armSwing: Float,
    /** Weapon angle relative to the lead arm. */
    val weaponAngle: Float,
    /** Forward step, in character heights. */
    val lunge: Float,
    /** Vertical bob, in character heights; negative is up. */
    val bob: Float,
    /** Overall scale, for the squash on a heavy landing. */
    val scale: Float = 1f,
)

private fun lerp(a: Pose, b: Pose, t: Float) = Pose(
    lean = a.lean + (b.lean - a.lean) * t,
    crouch = a.crouch + (b.crouch - a.crouch) * t,
    armSwing = a.armSwing + (b.armSwing - a.armSwing) * t,
    weaponAngle = a.weaponAngle + (b.weaponAngle - a.weaponAngle) * t,
    lunge = a.lunge + (b.lunge - a.lunge) * t,
    bob = a.bob + (b.bob - a.bob) * t,
    scale = a.scale + (b.scale - a.scale) * t,
)

private val IDLE = Pose(lean = 4f, crouch = 0.06f, armSwing = 18f, weaponAngle = -28f, lunge = 0f, bob = 0f)

/** The bottom of the user's own rep: coiled, weapon drawn back. */
private val WINDUP = Pose(lean = -12f, crouch = 0.34f, armSwing = -42f, weaponAngle = -74f, lunge = -0.05f, bob = 0.04f)

private val SWING_MID = Pose(lean = 22f, crouch = 0.10f, armSwing = 96f, weaponAngle = 46f, lunge = 0.09f, bob = -0.02f)
private val SWING_END = Pose(lean = 12f, crouch = 0.16f, armSwing = 62f, weaponAngle = 18f, lunge = 0.04f, bob = 0f)
private val HEAVY_MID = Pose(lean = 30f, crouch = 0.04f, armSwing = 124f, weaponAngle = 66f, lunge = 0.16f, bob = -0.06f, scale = 1.06f)
private val CRIT_MID = Pose(lean = 36f, crouch = 0f, armSwing = 142f, weaponAngle = 84f, lunge = 0.22f, bob = -0.12f, scale = 1.12f)
private val SKILL_MID = Pose(lean = -6f, crouch = 0.02f, armSwing = -96f, weaponAngle = -30f, lunge = -0.04f, bob = -0.10f, scale = 1.08f)
private val HURT = Pose(lean = -22f, crouch = 0.26f, armSwing = -8f, weaponAngle = -60f, lunge = -0.10f, bob = 0.03f)
private val VICTORY = Pose(lean = -6f, crouch = 0f, armSwing = -128f, weaponAngle = -150f, lunge = 0f, bob = -0.06f)
private val DEFEAT = Pose(lean = 8f, crouch = 0.72f, armSwing = 6f, weaponAngle = -6f, lunge = 0f, bob = 0.16f, scale = 0.94f)

/**
 * Resolves the pose for this frame.
 *
 * A swing eases out toward its contact point and settles back, which is what gives a hit weight;
 * a linear sweep reads as a diagram moving.
 */
private fun poseFor(state: AnimState): Pose {
    val p = state.progress
    return when (state.clip) {
        // Idling is where the wind-up blend lives: the avatar coils in step with the user's descent.
        AnimClip.IDLE -> lerp(IDLE, WINDUP, easeInOut(state.windup))

        AnimClip.LIGHT_1, AnimClip.LIGHT_2, AnimClip.LIGHT_3 -> swing(SWING_MID, p)
        AnimClip.HEAVY -> swing(HEAVY_MID, p)
        AnimClip.CRIT -> swing(CRIT_MID, p)
        AnimClip.SKILL -> swing(SKILL_MID, p, contact = 0.55f)
        AnimClip.HURT -> lerp(HURT, IDLE, easeOut(p))
        AnimClip.VICTORY -> lerp(IDLE, VICTORY, easeOut((p * 2f).coerceAtMost(1f)))
        AnimClip.DEFEAT -> lerp(IDLE, DEFEAT, easeOut(p))
    }
}

private fun swing(peak: Pose, progress: Float, contact: Float = 0.38f): Pose =
    if (progress < contact) {
        // Wind back first, then accelerate through: the anticipation is what sells the impact.
        val t = progress / contact
        lerp(lerp(IDLE, WINDUP, 0.55f), peak, easeIn(t))
    } else {
        val t = (progress - contact) / (1f - contact)
        lerp(peak, SWING_END, easeOut(t))
    }

private fun easeIn(t: Float) = t * t
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)
private fun easeInOut(t: Float) = t * t * (3f - 2f * t)

/** Class identity, entirely in colour and silhouette. */
private data class Kit(
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val skin: Color = Color(0xFFF0C9A0),
)

private fun kitFor(playerClass: PlayerClass) = when (playerClass) {
    PlayerClass.KNIGHT -> Kit(Color(0xFFC3CBDA), Color(0xFF7C879B), Color(0xFF4DA3FF))
    PlayerClass.ARCHER -> Kit(Color(0xFF6FCF97), Color(0xFF3E8E63), Color(0xFFE9F7C8))
}

@Composable
fun Fighter(
    playerClass: PlayerClass,
    state: AnimState,
    modifier: Modifier = Modifier,
) {
    val kit = kitFor(playerClass)
    val pose = poseFor(state)
    Canvas(modifier) {
        drawFighter(playerClass, kit, pose, state, facingRight = true)
    }
}

private fun DrawScope.drawFighter(
    playerClass: PlayerClass,
    kit: Kit,
    pose: Pose,
    state: AnimState,
    facingRight: Boolean,
) {
    val h = size.height * 0.86f * pose.scale
    val dir = if (facingRight) 1f else -1f
    val footX = size.width / 2f - dir * h * 0.06f + dir * h * pose.lunge
    val footY = size.height * 0.96f + h * pose.bob

    // Joints in local space, measured up from the feet.
    val hip = Offset(footX, footY - h * 0.46f * (1f - pose.crouch * 0.45f))
    val leanRad = Math.toRadians((pose.lean * dir).toDouble()).toFloat()
    val torsoLen = h * 0.30f
    val shoulder = Offset(
        hip.x + sin(leanRad) * torsoLen,
        hip.y - cos(leanRad) * torsoLen,
    )
    val headR = h * 0.085f
    val head = Offset(
        shoulder.x + sin(leanRad) * headR * 1.35f,
        shoulder.y - cos(leanRad) * headR * 1.35f,
    )

    val kneeDrop = h * 0.24f
    val kneeOut = h * 0.05f * (1f + pose.crouch)
    val leftFoot = Offset(footX - h * 0.07f, footY)
    val rightFoot = Offset(footX + h * 0.09f, footY)
    val leftKnee = Offset(hip.x - kneeOut, hip.y + kneeDrop * (1f - pose.crouch * 0.3f))
    val rightKnee = Offset(hip.x + kneeOut * 1.2f, hip.y + kneeDrop * (1f - pose.crouch * 0.3f))

    // A soft contact shadow keeps the figure from floating over the camera image.
    drawOval(
        color = Color.Black.copy(alpha = 0.32f),
        topLeft = Offset(footX - h * 0.15f, footY - h * 0.015f),
        size = Size(h * 0.30f, h * 0.045f),
    )

    limb(leftFoot, leftKnee, h * 0.055f, h * 0.065f, kit.secondary)
    limb(leftKnee, hip, h * 0.065f, h * 0.075f, kit.secondary)
    limb(rightFoot, rightKnee, h * 0.055f, h * 0.065f, kit.secondary)
    limb(rightKnee, hip, h * 0.065f, h * 0.075f, kit.secondary)

    // Torso, drawn as a tapered block so the lean is legible from across a room.
    limb(hip, shoulder, h * 0.135f, h * 0.115f, kit.primary)

    val armSwingRad = Math.toRadians(((pose.armSwing - 90f) * dir).toDouble()).toFloat()
    val upperArm = h * 0.155f
    val forearm = h * 0.145f
    val elbow = Offset(
        shoulder.x + cos(armSwingRad) * upperArm * dir.let { 1f },
        shoulder.y + sin(armSwingRad) * upperArm,
    )
    val weaponRad = armSwingRad + Math.toRadians((pose.weaponAngle * dir).toDouble()).toFloat()
    val hand = Offset(
        elbow.x + cos(weaponRad) * forearm,
        elbow.y + sin(weaponRad) * forearm,
    )

    // Off hand, held back, so the figure does not read as one-armed.
    val offRad = armSwingRad + Math.toRadians((-52f * dir).toDouble()).toFloat()
    val offHand = Offset(
        shoulder.x + cos(offRad) * (upperArm + forearm) * 0.82f,
        shoulder.y + sin(offRad) * (upperArm + forearm) * 0.82f,
    )
    limb(shoulder, offHand, h * 0.055f, h * 0.045f, kit.secondary)

    drawWeaponTrail(playerClass, kit, shoulder, hand, state, h)

    limb(shoulder, elbow, h * 0.062f, h * 0.055f, kit.primary)
    limb(elbow, hand, h * 0.055f, h * 0.045f, kit.skin)

    drawHead(playerClass, kit, head, headR, leanRad, dir)
    drawWeapon(playerClass, kit, hand, weaponRad, h, dir)

    if (state.impact > 0.01f) {
        drawCircle(
            color = kit.accent.copy(alpha = 0.22f * state.impact),
            radius = h * (0.26f + 0.18f * state.impact),
            center = hand,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawHead(
    playerClass: PlayerClass,
    kit: Kit,
    head: Offset,
    r: Float,
    leanRad: Float,
    dir: Float,
) {
    when (playerClass) {
        PlayerClass.KNIGHT -> {
            drawCircle(kit.primary, r * 1.08f, head)
            // Visor slit, the one detail that makes a circle read as a helmet.
            drawRoundRect(
                color = Color(0xFF1B2230),
                topLeft = Offset(head.x - r * 0.15f + dir * r * 0.30f, head.y - r * 0.22f),
                size = Size(r * 0.75f, r * 0.30f),
                cornerRadius = CornerRadius(r * 0.12f),
            )
            drawRoundRect(
                color = kit.accent,
                topLeft = Offset(head.x - r * 0.28f, head.y - r * 1.55f),
                size = Size(r * 0.56f, r * 0.62f),
                cornerRadius = CornerRadius(r * 0.2f),
            )
        }
        PlayerClass.ARCHER -> {
            drawCircle(kit.skin, r * 0.95f, head)
            // Hood.
            drawPath(
                Path().apply {
                    moveTo(head.x - r * 1.05f, head.y + r * 0.35f)
                    lineTo(head.x - r * 0.55f, head.y - r * 1.25f)
                    lineTo(head.x + r * 0.9f, head.y - r * 0.85f)
                    lineTo(head.x + r * 1.0f, head.y + r * 0.2f)
                    close()
                },
                kit.primary,
            )
        }
    }
}

private fun DrawScope.drawWeapon(
    playerClass: PlayerClass,
    kit: Kit,
    hand: Offset,
    angleRad: Float,
    h: Float,
    dir: Float,
) {
    val tip = Offset(
        hand.x + cos(angleRad) * h * 0.34f,
        hand.y + sin(angleRad) * h * 0.34f,
    )
    when (playerClass) {
        PlayerClass.KNIGHT -> {
            limb(hand, tip, h * 0.034f, h * 0.012f, Color(0xFFE9EDF5))
            val guard = Offset(
                hand.x + cos(angleRad + 1.57f) * h * 0.05f,
                hand.y + sin(angleRad + 1.57f) * h * 0.05f,
            )
            val guard2 = Offset(
                hand.x - cos(angleRad + 1.57f) * h * 0.05f,
                hand.y - sin(angleRad + 1.57f) * h * 0.05f,
            )
            limb(guard, guard2, h * 0.02f, h * 0.02f, kit.accent)
        }
        PlayerClass.ARCHER -> {
            // A bow is an arc, which no straight limb can fake.
            val perpX = cos(angleRad + 1.57f)
            val perpY = sin(angleRad + 1.57f)
            val top = Offset(hand.x + perpX * h * 0.16f, hand.y + perpY * h * 0.16f)
            val bottom = Offset(hand.x - perpX * h * 0.16f, hand.y - perpY * h * 0.16f)
            val bulge = Offset(
                hand.x + cos(angleRad) * h * 0.10f,
                hand.y + sin(angleRad) * h * 0.10f,
            )
            drawPath(
                Path().apply {
                    moveTo(top.x, top.y)
                    quadraticTo(bulge.x, bulge.y, bottom.x, bottom.y)
                },
                color = Color(0xFF8A6A44),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.018f),
            )
            drawLine(kit.accent.copy(alpha = 0.8f), top, bottom, strokeWidth = h * 0.007f)
        }
    }
}

/** A motion trail behind the weapon while a swing is landing. */
private fun DrawScope.drawWeaponTrail(
    playerClass: PlayerClass,
    kit: Kit,
    shoulder: Offset,
    hand: Offset,
    state: AnimState,
    h: Float,
) {
    if (state.impact <= 0.02f || !state.clip.isAttack) return
    val steps = 5
    for (i in 1..steps) {
        val t = i / (steps + 1f)
        val p = Offset(
            shoulder.x + (hand.x - shoulder.x) * (0.55f + 0.45f * t),
            shoulder.y + (hand.y - shoulder.y) * (0.55f + 0.45f * t),
        )
        drawCircle(
            color = kit.accent.copy(alpha = 0.13f * state.impact * (1f - t)),
            radius = h * 0.09f * (1f - t * 0.4f),
            center = p,
            blendMode = BlendMode.Plus,
        )
    }
}

/** A tapered segment, matching the skeleton overlay's treatment so the two read as one language. */
private fun DrawScope.limb(
    from: Offset,
    to: Offset,
    fromWidth: Float,
    toWidth: Float,
    color: Color,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    if (length < 0.5f) return
    val nx = -dy / length
    val ny = dx / length
    val h1 = fromWidth / 2f
    val h2 = toWidth / 2f

    drawPath(
        Path().apply {
            moveTo(from.x + nx * h1, from.y + ny * h1)
            lineTo(to.x + nx * h2, to.y + ny * h2)
            lineTo(to.x - nx * h2, to.y - ny * h2)
            lineTo(from.x - nx * h1, from.y - ny * h1)
            close()
        },
        color,
    )
    drawCircle(color, h1, from)
    drawCircle(color, h2, to)
}
