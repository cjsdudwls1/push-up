package com.pushuprpg.app.ui.components

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pushuprpg.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

/**
 * Draws [onDraw] as pixel art: into a bitmap [pixelsAcross] pixels wide, then up to the full size
 * without smoothing. [onDraw] sees the full size and draws as it always did, so shapes made for the
 * screen come out as chunky pixels at the same places.
 */
@Composable
fun PixelCanvas(modifier: Modifier, pixelsAcross: Int, onDraw: DrawScope.() -> Unit) {
    val draw by rememberUpdatedState(onDraw)
    Spacer(
        modifier.drawWithCache {
            val k = size.width / pixelsAcross
            val w = pixelsAcross.coerceAtLeast(1)
            val h = ceil(size.height / k).toInt().coerceAtLeast(1)
            val bitmap = ImageBitmap(w, h)
            val canvas = GraphicsCanvas(bitmap)
            val scope = CanvasDrawScope()
            val full = size
            onDrawBehind {
                canvas.save()
                canvas.scale(1f / k, 1f / k)
                scope.draw(this, layoutDirection, canvas, full) {
                    drawRect(Color.Transparent, size = full, blendMode = BlendMode.Clear)
                    draw()
                }
                canvas.restore()
                drawImage(
                    image = bitmap,
                    dstSize = IntSize(size.width.roundToInt(), (h * k).roundToInt()),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    )
}

/** What happened that a burst is drawn for. Emitted by the survival screen's view model. */
enum class FxCue { HIT, PERFECT, COMBO5, COMBO10, COMBO20, MILESTONE, RECORD, COMBO_END }

/**
 * One sprite sheet: [frames] frames of [w] by [h] in [cols] columns, drawn [frameMs] apart, placed
 * by the pixel ([pivotX], [pivotY]). Sizes are in the art's own pixels, on a screen 240 of them wide.
 * Made with pixelfx (MIT); see docs/ART_CREDITS.md.
 */
internal enum class Sprite(
    @DrawableRes val res: Int,
    val frames: Int,
    val cols: Int,
    val w: Int,
    val h: Int,
    val pivotX: Int,
    val pivotY: Int,
    val frameMs: Float,
    /** Large enough to let go of once it has played, rather than keep for the whole session. */
    val big: Boolean = false,
) {
    HIT(R.drawable.fx_hit, 16, 16, 122, 71, 34, 56, 33.3f),
    PERFECT(R.drawable.fx_perfect, 24, 13, 152, 116, 76, 57, 66.7f),
    COMBO5(R.drawable.fx_combo5, 23, 10, 201, 98, 100, 49, 66.7f),
    COMBO10(R.drawable.fx_combo10, 23, 10, 203, 98, 102, 49, 66.7f),
    COMBO20(R.drawable.fx_combo20, 23, 9, 205, 98, 104, 49, 66.7f),
    COMBO_END(R.drawable.fx_comboend, 24, 23, 88, 76, 44, 33, 66.7f),
    MILESTONE(R.drawable.fx_milestone, 37, 10, 188, 64, 81, 31, 66.7f),
    RECORD(R.drawable.fx_record, 55, 10, 198, 235, 99, 96, 66.7f, big = true),
    CONFETTI(R.drawable.fx_confetti, 52, 10, 198, 94, 99, 81, 66.7f, big = true);

    val lengthMs: Float get() = frames * frameMs
}

internal class Burst(val sprite: Sprite, val delayMs: Long) {
    /** Frame time of the first frame it was seen on, once its sheet is loaded; -1 until then. */
    var bornNanos = -1L
}

/** The bursts on screen and the shake they make. One per screen; see [rememberPixelFx]. */
class PixelFxState internal constructor(private val resources: Resources) {
    internal val bursts = mutableStateListOf<Burst>()
    internal val sheets = mutableStateMapOf<Sprite, ImageBitmap>()
    internal var nowNanos by mutableLongStateOf(0L)
    private var shakeAmp = 0f
    private var shakeUntilNanos = 0L

    /** How far to move the scene this frame, in the art's pixels; zero when it is not shaking. */
    fun shake(): Offset {
        val now = nowNanos
        if (now >= shakeUntilNanos || shakeAmp == 0f) return Offset.Zero
        return Offset(
            (Random.nextFloat() * 2f - 1f) * shakeAmp,
            (Random.nextFloat() * 2f - 1f) * shakeAmp,
        )
    }

    private var squashFrom = 0f
    private var squashAtNanos = 0L

    /**
     * How far the cat is pressed into the floor at frame time [now]: 1 on a push landing, 1.4 on a
     * deep one, back up at six a second, as the demo's.
     */
    internal fun squash(now: Long): Float {
        if (squashFrom == 0f) return 0f
        val left = squashFrom - (now - squashAtNanos) / 1_000_000_000f * 6f
        return left.coerceIn(0f, squashFrom)
    }

    internal fun startSquash(amount: Float) {
        squashFrom = amount
        // Frame times are System.nanoTime's on Android, so a push lands on the next frame.
        squashAtNanos = System.nanoTime()
    }

    internal fun startShake(amp: Float, ms: Long) {
        shakeAmp = amp
        shakeUntilNanos = nowNanos + ms * 1_000_000L
    }

    internal suspend fun load(sprite: Sprite) {
        if (sheets.containsKey(sprite)) return
        val image = withContext(Dispatchers.Default) {
            val options = BitmapFactory.Options().apply { inScaled = false }
            BitmapFactory.decodeResource(resources, sprite.res, options)?.asImageBitmap()
        } ?: return
        sheets[sprite] = image
    }
}

@Composable
fun rememberPixelFx(cues: Flow<FxCue>, reduceMotion: Boolean): PixelFxState {
    val resources = LocalContext.current.resources
    val state = remember { PixelFxState(resources) }
    val calm by rememberUpdatedState(reduceMotion)

    // The ones every set sees, ready before the first push lands.
    LaunchedEffect(state) {
        for (sprite in listOf(Sprite.HIT, Sprite.PERFECT, Sprite.COMBO5)) state.load(sprite)
    }

    LaunchedEffect(state, cues) {
        cues.collect { cue ->
            // As the demo has it: under reduced motion no shaking and none of the large bursts.
            val (sprites, amp, ms) = when (cue) {
                FxCue.HIT -> Triple(listOf(Sprite.HIT), 1f, 90L)
                FxCue.PERFECT -> Triple(listOf(Sprite.PERFECT), 2f, 140L)
                FxCue.COMBO5 -> Triple(listOf(Sprite.COMBO5), 0f, 0L)
                FxCue.COMBO10 -> Triple(listOf(Sprite.COMBO10), 2f, 180L)
                FxCue.COMBO20 -> Triple(listOf(Sprite.COMBO20), 3f, 180L)
                FxCue.MILESTONE -> Triple(listOf(Sprite.MILESTONE), 0f, 0L)
                FxCue.RECORD -> Triple(listOf(Sprite.RECORD, Sprite.CONFETTI), 3f, 260L)
                FxCue.COMBO_END -> Triple(listOf(Sprite.COMBO_END), 3f, 300L)
            }
            when (cue) {
                FxCue.HIT -> state.startSquash(1f)
                FxCue.PERFECT -> state.startSquash(1.4f)
                else -> Unit
            }
            val shown = if (calm) sprites.filter { it in CALM_SPRITES } else sprites
            if (!calm && amp > 0f) state.startShake(amp, ms)
            for (sprite in shown) {
                launch {
                    state.load(sprite)
                    state.bursts += Burst(sprite, delayMs = if (sprite == Sprite.CONFETTI) 500L else 0L)
                }
            }
        }
    }

    // The clock, running only while something is playing or shaking.
    LaunchedEffect(state) {
        snapshotFlow { state.bursts.size }.collect {
            while (state.bursts.isNotEmpty()) {
                withFrameNanos { now ->
                    state.nowNanos = now
                    val done = state.bursts.filter { burst ->
                        if (burst.bornNanos < 0) burst.bornNanos = now
                        (now - burst.bornNanos) / 1_000_000f > burst.delayMs + burst.sprite.lengthMs
                    }
                    if (done.isNotEmpty()) {
                        state.bursts.removeAll(done)
                        for (sprite in done.map { it.sprite }.toSet()) {
                            if (sprite.big && state.bursts.none { it.sprite == sprite }) state.sheets.remove(sprite)
                        }
                    }
                }
            }
            // A last frame for the shake to settle on.
            withFrameNanos { state.nowNanos = it }
        }
    }
    return state
}

private val CALM_SPRITES = setOf(Sprite.HIT, Sprite.COMBO5, Sprite.COMBO10, Sprite.COMBO20, Sprite.COMBO_END)

/** The bursts, over the cat, placed where the demo had them in its room (see [RoomGeometry]). */
@Composable
fun PixelFxLayer(state: PixelFxState, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val now = state.nowNanos
        val g = RoomGeometry(size.width, size.height)
        val u = g.u
        val floorY = g.floorY
        val cx = g.cx
        for (burst in state.bursts) {
            val image = state.sheets[burst.sprite] ?: continue
            if (burst.bornNanos < 0) continue
            val age = (now - burst.bornNanos) / 1_000_000f - burst.delayMs
            if (age < 0f) continue
            val s = burst.sprite
            val frame = (age / s.frameMs).toInt().coerceAtMost(s.frames - 1)
            // Where the demo put each, on its 240 by 426 screen with the floor at 372.
            val at = when (s) {
                Sprite.HIT -> Offset(cx - 26f * u, floorY - 2f * u)
                Sprite.COMBO_END -> Offset(cx, floorY - 110f * u)
                Sprite.CONFETTI -> Offset(cx, floorY + 38f * u)
                Sprite.PERFECT -> Offset(cx, floorY - 157f * u)
                Sprite.MILESTONE -> Offset(cx, floorY - 244f * u)
                Sprite.RECORD -> Offset(cx, floorY - 192f * u)
                else -> Offset(cx, floorY - 202f * u)
            }
            drawImage(
                image = image,
                srcOffset = IntOffset((frame % s.cols) * s.w, (frame / s.cols) * s.h),
                srcSize = IntSize(s.w, s.h),
                dstOffset = IntOffset((at.x - s.pivotX * u).roundToInt(), (at.y - s.pivotY * u).roundToInt()),
                dstSize = IntSize((s.w * u).roundToInt(), (s.h * u).roundToInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}
