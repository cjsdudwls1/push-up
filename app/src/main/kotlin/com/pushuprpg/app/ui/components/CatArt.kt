package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.WearSlot
import com.pushuprpg.core.survival.CatMood
import kotlin.math.roundToInt

/** The colours of one coat. Pattern colours are null for a coat that has none. */
data class CoatColors(
    val fur: Color,
    val earInner: Color,
    /** Nose, mouth and outline strokes — drawn on [muzzle] where there is one, else on [fur]. */
    val ink: Color,
    val whisker: Color,
    /** The eye: a dark eye on a light coat, a coloured iris on a dark one. */
    val eye: Color,
    /** A pupil inside [eye], for a coat whose eye is coloured. */
    val pupil: Color? = null,
    /** Tabby stripes. */
    val stripe: Color? = null,
    /** A lighter chin and cheeks — a tuxedo's white face, a tabby's pale muzzle. */
    val muzzle: Color? = null,
    /** A tuxedo's shirt front and socks. */
    val bib: Color? = null,
    /** Calico patches. */
    val patchA: Color? = null,
    val patchB: Color? = null,
    /**
     * A rim around the silhouette, for a coat that would otherwise vanish into the dark room the
     * cat is drawn in. Without it a tuxedo is two eyes and a shirt front.
     */
    val rim: Color? = null,
)

fun coatColors(coat: CatCoat): CoatColors = when (coat) {
    CatCoat.CREAM -> CoatColors(
        fur = Color(0xFFF3D9A8), earInner = Color(0xFFE0A88C), ink = Color(0xFF3A2A18),
        whisker = Color(0xFF3A2A18), eye = Color(0xFF3A2A18),
    )
    CatCoat.CHEESE -> CoatColors(
        fur = Color(0xFFF2B25C), earInner = Color(0xFFF0A08A), ink = Color(0xFF3A2A18),
        whisker = Color(0xFF3A2A18), eye = Color(0xFF3A2A18),
        stripe = Color(0xFFD27A26), muzzle = Color(0xFFFBE3BE),
    )
    CatCoat.MACKEREL -> CoatColors(
        fur = Color(0xFFABA79D), earInner = Color(0xFFD9A3A0), ink = Color(0xFF2A2724),
        whisker = Color(0xFF2A2724), eye = Color(0xFF2A2724),
        stripe = Color(0xFF55524C), muzzle = Color(0xFFE9E5DC),
    )
    CatCoat.TUXEDO -> CoatColors(
        fur = Color(0xFF2A2A2F), earInner = Color(0xFFE3A1A4), ink = Color(0xFF2A2724),
        whisker = Color(0xFFE8E4DA), eye = Color(0xFFC9D46A), pupil = Color(0xFF1A1A1A),
        muzzle = Color(0xFFF4F1EA), bib = Color(0xFFF4F1EA), rim = Color(0xFF6A6A74),
    )
    CatCoat.CALICO -> CoatColors(
        fur = Color(0xFFF7F2E8), earInner = Color(0xFFE7A3A6), ink = Color(0xFF3A2A18),
        whisker = Color(0xFF3A2A18), eye = Color(0xFF3A2A18),
        patchA = Color(0xFFE39A4C), patchB = Color(0xFF34302C),
    )
}

/**
 * 고양이, as pixel art: the sprite of the demo the owner approved (2026-11-10), 20 cells across and
 * 21 down, in the colours of its [coat]. Every picture of the cat draws this — the hub, the cards,
 * the share card and the run — so the whole app has the one cat.
 *
 * Its emotional read is in the eyes and a few cells, so it survives a glance:
 * - [mood] is the stage of fear [com.pushuprpg.core.survival.CatCompanion] decided: strained eyes
 *   and a bead of sweat once the ceiling is coming, and at the end crossed eyes, a tremble and sweat
 *   on both sides, with the paws up (만세) to hold the ceiling off.
 * - [cheer] is the moment after a push: the eyes close into a smile.
 * - [phase] is a free-running 0..1 clock over four seconds, for the sweat and the tremble; hold it at 0
 *   to keep the cat still, which is what reduced motion does.
 * - [holdUpTo] raises the paws to that height, as the run does with the ceiling coming down; null
 *   leaves them down outside a panic. They never reach higher than seven cells over the ears.
 * - [squash] is a push landing, 0..1.4 as the demo has it: the cat dips into the floor and back.
 * - [wear] is what it has on, drawn on the same cells (see [drawPixelThing]). A scene is not worn;
 *   see [drawCatScene]. A toy is not worn either, but it sits on the floor at its left,
 *   [TOY_OFFSET] heads from its middle, and stays put while the cat trembles.
 * - [toy] false leaves the toy out, for a picture framed close around the cat with no room beside it,
 *   or one that draws it itself with [drawCatToyBeside], behind something else.
 *
 * [alarm] and [tail] are kept for the callers that pass them; the sprite has no tail and its ears do
 * not move.
 */
@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawCat(
    centerX: Float,
    baseY: Float,
    scale: Float,
    coat: CatCoat = CatCoat.CREAM,
    mood: CatMood = CatMood.CALM,
    alarm: Float = 0f,
    cheer: Float = 0f,
    phase: Float = 0f,
    wear: Set<CatItem> = emptySet(),
    tail: Boolean = true,
    toy: Boolean = true,
    holdUpTo: Float? = null,
    squash: Float = 0f,
) {
    // The toy first, behind everything: where it meets the cat's side, the cat is in front.
    if (toy) drawCatToyBeside(wear, centerX, baseY, scale)

    val s = catCell(scale)
    val p = pixelPalette(coat)
    val ms = phase * PHASE_MS
    val happy = cheer > 0.35f
    val eyes = when {
        happy -> EyeLook.HAPPY
        mood == CatMood.PANIC -> EyeLook.PANIC
        mood == CatMood.SCARED || mood == CatMood.UNEASY -> EyeLook.STRAIN
        else -> EyeLook.NORMAL
    }
    val tremble = if (eyes == EyeLook.PANIC && phase != 0f) (if ((ms / 40f).toInt() % 2 == 0) 1f else -1f) * s / 4f else 0f
    val ox = centerX - SPRITE_COLS * s / 2f + tremble
    val top = baseY - SPRITE_ROWS * s + (squash * 2f).roundToInt() * s

    fun cell(col: Float, row: Float, w: Float, h: Float, color: Color) {
        val l = (ox + col * s).roundToInt().toFloat()
        val t = (top + row * s).roundToInt().toFloat()
        val r = (ox + (col + w) * s).roundToInt().toFloat()
        val b = (top + (row + h) * s).roundToInt().toFloat()
        if (r > l && b > t) drawRect(color, topLeft = Offset(l, t), size = Size(r - l, b - t))
    }

    // 만세: the paws over the head, pressed against the ceiling when it is close and held up when not.
    if (holdUpTo != null || mood == CatMood.PANIC) {
        val shoulder = 11f
        val reach = maxOf(((holdUpTo ?: Float.NEGATIVE_INFINITY) - top) / s, -7f)
        for (ax in floatArrayOf(-2f, 19f)) {
            cell(ax - 0.25f, reach, 3.5f, shoulder - reach, p.k)
            cell(ax + 0.25f, reach + 0.5f, 2.5f, shoulder - reach - 0.5f, p.o)
            cell(ax - 0.5f, reach - 0.5f, 4f, 2f, p.k)
            cell(ax, reach, 3f, 1f, p.l)
            cell(ax + 1f, reach + 0.25f, 1f, 0.5f, p.p)
        }
    }

    for (r in 0 until SPRITE_ROWS) {
        val line = CAT_SPRITE[r]
        var c = 0
        while (c < SPRITE_COLS) {
            val color = p.at(line[c], r, c)
            if (color == null) { c++; continue }
            // A run of one colour is one rectangle, so the edges between cells cannot show.
            var end = c + 1
            while (end < SPRITE_COLS && p.at(line[end], r, end) == color) end++
            cell(c.toFloat(), r.toFloat(), (end - c).toFloat(), 1f, color)
            c = end
        }
    }

    val look = if (p.iris != null && eyes == EyeLook.NORMAL) TUXEDO_EYE else eyes.grid
    for ((side, startCol) in listOf(0 to 3, 1 to 14)) {
        val grid = look[side]
        for (r in 0 until 3) for (c in 0 until 3) {
            val color = when (grid[r][c]) {
                'E' -> p.e
                'I' -> p.iris ?: p.e
                'W' -> Color.White
                else -> null
            } ?: continue
            cell((startCol + c).toFloat(), (6 + r).toFloat(), 1f, 1f, color)
        }
    }

    if (eyes == EyeLook.STRAIN || eyes == EyeLook.PANIC) {
        // A bead of sweat, running down beside the head.
        val d = (ms / 120f).toInt() % 6
        cell(20f, (3 + d % 3).toFloat(), 1f, 1f, SWEAT)
        if (eyes == EyeLook.PANIC) cell(-1f, (4 + (d + 2) % 3).toFloat(), 1f, 1f, SWEAT)
    }

    // What it has on, over everything, in the order a cat would put things on; each on the cat's cells.
    for (slot in listOf(WearSlot.NECK, WearSlot.FACE, WearSlot.HEAD)) {
        val item = wear.firstOrNull { it.slot == slot } ?: continue
        drawPixelThing(wornSprite(item), ox, top, s)
    }
}

/** How the eyes look, as two 3-by-3 grids: E the eye, W its glint. */
private enum class EyeLook(val grid: List<List<String>>) {
    NORMAL(listOf(listOf("EEW", "EEE", "EEE"), listOf("EEW", "EEE", "EEE"))),
    HAPPY(listOf(listOf(".E.", "E.E", "..."), listOf(".E.", "E.E", "..."))),
    STRAIN(listOf(listOf("...", "EEE", "..."), listOf("...", "EEE", "..."))),
    PANIC(listOf(listOf("E..", ".E.", "E.."), listOf("..E", ".E.", "..E"))),
}

/** A tuxedo's coloured iris around its pupil; E alone would vanish into the black fur. */
private val TUXEDO_EYE = listOf(listOf("IIW", "IEI", "III"), listOf("IIW", "IEI", "III"))

private val CAT_SPRITE = arrayOf(
    "..KK............KK..",
    ".KOOK..........KOOK.",
    ".KOPOK........KOPOK.",
    "KOOPOOKKKKKKKKOOPOOK",
    "KOOOOOOOSSSSOOOOOOOK",
    "KOOOOOOOOSSOOOOOOOOK",
    "KOOOOOOOOOOOOOOOOOOK",
    "KOOOOOOOOOOOOOOOOOOK",
    "KOOOOOOOOOOOOOOOOOOK",
    "KOOOOOOOOPPOOOOOOOOK",
    "KOPPOOOOKOOKOOOOPPOK",
    "KOOOOOOOOKKOOOOOOOOK",
    ".KOOOOOOOOOOOOOOOOK.",
    "..KKKOOOOOOOOOOKKK..",
    "....KOLLLLLLLLOK....",
    "...KOOLLLLLLLLOOK...",
    "...KOOLLLLLLLLOOK...",
    "...KOOOLLLLLLOOOK...",
    "...KOOOOOOOOOOOOK...",
    "...KLLLKKKKKKLLLK...",
    "....KKK......KKK....",
)
private const val SPRITE_COLS = 20
private const val SPRITE_ROWS = 21

/** [drawCat]'s [phase] runs once over this many milliseconds. */
private const val PHASE_MS = 4_000f

private val SWEAT = Color(0xFF7FD4FF)

/** One of the sprite's cells, in pixels, for a cat drawn at [scale]. */
internal fun catCell(scale: Float): Float = 4.6f * scale

/**
 * A coat as the sprite's letters: K the outline, O the fur, S the forehead mark and stripes, L the
 * muzzle, belly and paws, P the ears, nose and cheeks, E the eyes. Patches and stripes are cells of
 * the fur painted over, chosen by [at].
 */
private class PixelPalette(
    val k: Color,
    val o: Color,
    val s: Color,
    val l: Color,
    val p: Color,
    val e: Color,
    val iris: Color? = null,
    val coat: CatCoat,
) {
    fun at(ch: Char, r: Int, c: Int): Color? = when (ch) {
        'K' -> k
        'S' -> s
        'L' -> l
        'P' -> p
        'O' -> fur(r, c)
        else -> null
    }

    private fun fur(r: Int, c: Int): Color = when (coat) {
        CatCoat.CALICO -> when {
            r <= 5 && c <= 6 -> CALICO_DARK
            (r in 3..8 && c >= 13) || (r in 15..18 && c >= 12) -> CALICO_ORANGE
            else -> o
        }
        CatCoat.MACKEREL -> if ((r == 5 || r == 7) && (c in 1..2 || c in 17..18) || (r == 15 || r == 17) && (c == 4 || c == 15)) s else o
        CatCoat.TUXEDO -> if (r in 10..12 && c in 6..13) l else o
        else -> o
    }
}

private val CALICO_DARK = Color(0xFF34302C)
private val CALICO_ORANGE = Color(0xFFE39A4C)
private val OUTLINE = Color(0xFF2B1A12)

private fun pixelPalette(coat: CatCoat): PixelPalette = when (coat) {
    // The demo's own cat.
    CatCoat.CHEESE -> PixelPalette(OUTLINE, Color(0xFFFF9A2E), Color(0xFFE0761A), Color(0xFFFFD9A8), Color(0xFFFF8FAB), Color(0xFF1E1A2E), coat = coat)
    CatCoat.CREAM -> PixelPalette(OUTLINE, Color(0xFFF3D9A8), Color(0xFFDDB57A), Color(0xFFFFF3DC), Color(0xFFFF8FAB), Color(0xFF1E1A2E), coat = coat)
    CatCoat.MACKEREL -> PixelPalette(Color(0xFF1C1A18), Color(0xFFABA79D), Color(0xFF55524C), Color(0xFFE9E5DC), Color(0xFFE8A3A8), Color(0xFF1E1A2E), coat = coat)
    // Its outline is grey: a dark one would lose the black cat in the dark room.
    CatCoat.TUXEDO -> PixelPalette(Color(0xFF6A6A74), Color(0xFF2A2A2F), Color(0xFFF4F1EA), Color(0xFFF4F1EA), Color(0xFFE3A1A4), Color(0xFF1A1A1A), iris = Color(0xFFC9D46A), coat = coat)
    CatCoat.CALICO -> PixelPalette(OUTLINE, Color(0xFFF7F2E8), CALICO_ORANGE, Color(0xFFFFFDF8), Color(0xFFE7A3A6), Color(0xFF1E1A2E), coat = coat)
}

/**
 * The cat's things as pixel art. Each is drawn once with its own drawing, at [SUB] pixels to a cell of
 * the cat, and kept: the edge's half-covered pixels made whole or empty and an outline put round it,
 * as the sprite has. The cat's cells, and a thing's place on them, do not depend on how large the cat
 * is drawn, so one copy serves every picture.
 */
internal class PixelThing(val cols: Int, val rows: Int, val argb: IntArray, val leftCells: Float, val topCells: Float)

/** Pixels of a thing to one of the cat's cells. */
private const val SUB = 3

private val pixelThings = HashMap<Any, PixelThing>()

/**
 * A thing drawn by [draw] into a grid [colsCells] by [rowsCells] of the cat's cells, whose top left is
 * [leftCells], [topCells] from the sprite's; [draw] sees a cell as [SUB] pixels with the sprite's top
 * left at the origin.
 */
internal fun pixelThing(
    key: Any,
    leftCells: Float,
    topCells: Float,
    colsCells: Int,
    rowsCells: Int,
    draw: DrawScope.() -> Unit,
): PixelThing = pixelThings.getOrPut(key) {
    val w = colsCells * SUB
    val h = rowsCells * SUB
    val bitmap = ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        translate(-leftCells * SUB, -topCells * SUB) { draw() }
    }
    val px = IntArray(w * h)
    bitmap.readPixels(px)
    // Whole or empty, never half: a soft edge is what smooth drawing looks like.
    val solid = BooleanArray(w * h) { (px[it] ushr 24) >= 0x80 }
    val out = IntArray(w * h)
    val ink = OUTLINE.toArgb()
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        out[i] = when {
            solid[i] -> px[i] or (0xFF shl 24)
            (x > 0 && solid[i - 1]) || (x < w - 1 && solid[i + 1]) ||
                (y > 0 && solid[i - w]) || (y < h - 1 && solid[i + w]) -> ink
            else -> 0
        }
    }
    PixelThing(w, h, out, leftCells, topCells)
}

/** Draws [thing] on a cat whose sprite's top left is [ox], [top], with cells of [s] pixels. */
internal fun DrawScope.drawPixelThing(thing: PixelThing, ox: Float, top: Float, s: Float) {
    val unit = s / SUB
    val left = ox + thing.leftCells * s
    val upper = top + thing.topCells * s
    for (y in 0 until thing.rows) {
        val row = y * thing.cols
        var x = 0
        while (x < thing.cols) {
            val argb = thing.argb[row + x]
            if (argb == 0) { x++; continue }
            var end = x + 1
            while (end < thing.cols && thing.argb[row + end] == argb) end++
            val l = (left + x * unit).roundToInt().toFloat()
            val t = (upper + y * unit).roundToInt().toFloat()
            val r = (left + end * unit).roundToInt().toFloat()
            val b = (upper + (y + 1) * unit).roundToInt().toFloat()
            if (r > l && b > t) drawRect(Color(argb), topLeft = Offset(l, t), size = Size(r - l, b - t))
            x = end
        }
    }
}

/**
 * Where a worn thing sits on the sprite, in cells from its top left, as the drawings expect it: the
 * head's middle and size, the line of the eyes, and the chin.
 */
private const val HEAD_ROW = 9.5f
private const val HEAD_CELLS = 7f
private const val EYE_ROW = 7.5f
private const val CHIN_ROW = 13f

private fun wornSprite(item: CatItem): PixelThing =
    pixelThing(item, leftCells = -8f, topCells = -16f, colsCells = SPRITE_COLS + 16, rowsCells = SPRITE_ROWS + 22) {
        val cx = SPRITE_COLS * SUB / 2f
        val head = HEAD_CELLS * SUB
        when (item.slot) {
            WearSlot.NECK -> drawNeckItem(item, cx, CHIN_ROW * SUB, head)
            WearSlot.FACE -> drawFaceItem(item, cx, EYE_ROW * SUB, head)
            WearSlot.HEAD -> drawHeadItem(item, cx, HEAD_ROW * SUB, head)
            else -> Unit
        }
    }

/**
 * The toy among [wear], if there is one, on the floor at the left of a cat drawn at [centerX], [baseY]
 * and [scale]. [drawCat] draws it first, behind the cat. The run draws it in a layer behind the
 * ceiling instead, so the slab comes down over it, and tells [drawCat] to leave it out.
 */
fun DrawScope.drawCatToyBeside(wear: Set<CatItem>, centerX: Float, baseY: Float, scale: Float) {
    val toy = wear.firstOrNull { it.slot == WearSlot.TOY } ?: return
    val s = catCell(scale)
    // Its middle TOY_OFFSET heads left of the cat's, standing on the floor: within a head either side
    // and 4.4 heads up, as CatToys keeps it.
    val sprite = pixelThing(toy, leftCells = -12f, topCells = -32f, colsCells = 24, rowsCells = 34) {
        drawCatToy(toy, 0f, 0f, HEAD_CELLS * SUB)
    }
    drawPixelThing(sprite, centerX - TOY_OFFSET * HEAD_CELLS * s, baseY, s)
}

/**
 * How far left of the cat's middle its toy sits, in heads. The toy keeps within a head either side of
 * that, so from the floor it reaches 3.4 heads to the left of the cat's middle: inside every picture
 * that draws it, which all leave the cat four heads of room on that side.
 */
const val TOY_OFFSET = 2.4f

/** Colours the cat's things share, wherever they are drawn. */
internal val KNIT_RED = Color(0xFFD9534F)
internal val KNIT_RED_DARK = Color(0xFFB94340)
internal val GOLD = Color(0xFFFFC53D)
internal val GOLD_DARK = Color(0xFFC8932B)
internal val PINK = Color(0xFFFF6F91)
internal val PINK_DARK = Color(0xFFE0527A)
internal val BRAND = Color(0xFF7C6BFF)
internal val CREAM_WHITE = Color(0xFFF5F0E8)
internal val FRAME = Color(0xFF6B4A2B)


/**
 * Hearts after a push: [count] of them, drifting up and out from beside the ears and fading as
 * [cheer] runs down. Out to the sides rather than straight up, because straight up is where the
 * speech bubble is. Pixel hearts, on half the cat's cells.
 */
fun DrawScope.drawHearts(centerX: Float, headTopY: Float, scale: Float, count: Int, cheer: Float) {
    if (count <= 0 || cheer <= 0f) return
    val s = catCell(scale)
    val rise = (1f - cheer) * 12f * s
    val color = Color(0xFFFF6F91).copy(alpha = cheer.coerceIn(0f, 1f))
    // (side, how far out in cells, a head start upward in cells, size of a pixel in cells)
    val spots = listOf(
        listOf(1f, 11f, 0f, 0.5f),
        listOf(-1f, 11f, 1.5f, 0.45f),
        listOf(1f, 15f, 4f, 0.4f),
    )
    for (spot in spots.take(count)) {
        val (side, out, lead, unit) = spot
        val x = centerX + side * (out * s + rise * 0.45f)
        val y = headTopY + 4f * s - rise - lead * s
        drawPixelHeart(Offset(x, y), unit * s, color)
    }
}

private val HEART_SPRITE = arrayOf(
    ".XX.XX.",
    "XXXXXXX",
    "XXXXXXX",
    ".XXXXX.",
    "..XXX..",
    "...X...",
)

/** A heart seven pixels of [unit] across, centred on [center]. */
internal fun DrawScope.drawPixelHeart(center: Offset, unit: Float, color: Color) {
    val left = center.x - 3.5f * unit
    val top = center.y - 3f * unit
    for ((r, line) in HEART_SPRITE.withIndex()) {
        val from = line.indexOf('X')
        val to = line.lastIndexOf('X') + 1
        if (from < 0) continue
        // Each row is one run, except the top, whose two lobes are split.
        val runs = if (r == 0) listOf(1 to 3, 4 to 6) else listOf(from to to)
        for ((a, b) in runs) {
            val l = (left + a * unit).roundToInt().toFloat()
            val t = (top + r * unit).roundToInt().toFloat()
            val rr = (left + b * unit).roundToInt().toFloat()
            val bb = (top + (r + 1) * unit).roundToInt().toFloat()
            drawRect(color, topLeft = Offset(l, t), size = Size(rr - l, bb - t))
        }
    }
}

/** Where the top of the cat's head is — the tips of its ears — for placing what floats above it. */
@Suppress("UNUSED_PARAMETER")
fun catHeadTop(baseY: Float, scale: Float, mood: CatMood = CatMood.CALM): Float =
    baseY - SPRITE_ROWS * catCell(scale)
