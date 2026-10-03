package catpreview

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.components.catHeadTop
import com.pushuprpg.app.ui.components.drawCat
import com.pushuprpg.app.ui.components.drawCatPortrait
import com.pushuprpg.app.ui.components.drawCatToyBeside
import com.pushuprpg.app.ui.components.drawItemPicture
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.survival.CatMood
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import java.io.File

/**
 * Renders the cat with each of its things to PNG through Compose's own desktop Skia canvas: the same
 * drawing code the app runs ([drawItemPicture], [drawCatPortrait], [drawCat]), on a plain JVM.
 *
 * One sheet per thing — the wardrobe tile on a light and a dark coat, the hub's portrait, and the run's
 * cat calm and in a panic under the ceiling, which is when a face or a hat has the most to cover and a
 * tall toy meets the slab — and, when no thing is named, all of them together.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/cat-preview").apply { mkdirs() }
    // --parts also writes each sheet's hub portrait and run picture on their own, as WebP, for a page.
    val parts = "--parts" in args
    val wanted = args.drop(1).filter { it != "--parts" }.map { it.uppercase() }.toSet()
    val unknown = wanted - CatItem.entries.map { it.name }.toSet()
    require(unknown.isEmpty()) { "no such thing: $unknown" }
    val items = CatItem.entries.filter { wanted.isEmpty() || it.name in wanted }
    for (item in items) {
        sheet(item, File(out, item.name.lowercase() + ".png"))
        if (parts) parts(item, File(out, "parts").apply { mkdirs() })
    }
    if (wanted.isEmpty()) overview(CatItem.entries, File(out, "all.png"))
    println("wrote ${items.size} sheet(s) to ${out.path}")
}

private fun parts(item: CatItem, dir: File) {
    val name = item.name.lowercase()
    render(720, 380, File(dir, "$name-hub.webp"), webp = true) {
        drawRect(PAGE)
        drawCatPortrait(CatCoat.CREAM, wear = setOf(item), cheer = 0f, phase = 0.1f)
    }
    render(360, 360, File(dir, "$name-tile.webp"), webp = true) {
        drawRect(TILE)
        drawItemPicture(item, CatCoat.CHEESE)
    }
}

// The dark theme's own surfaces: the screen, and the wardrobe tile's card.
private val PAGE = Color(0xFF0E1117)
private val TILE = Color(0xFF161A23)
private val CAMERA = Color(0xFF3B4250)
private val FLOOR = Color(0xFF3A2A18)

private const val TILE_PX = 180
private const val GAP = 20

private fun sheet(item: CatItem, file: File) {
    val portraitW = 720
    val portraitH = 380
    val run = 380
    val width = GAP + TILE_PX + GAP + portraitW + GAP + run + GAP + run + GAP
    val height = GAP + 28 + portraitH + GAP
    render(width, height, file) { label ->
        drawRect(PAGE)
        label(item.name + " (" + item.slot.name.lowercase() + ")", GAP.toFloat(), GAP + 18f)
        val top = GAP + 28f
        // The wardrobe tile, on a light coat and on the coat that hides the most.
        tile(GAP.toFloat(), top) { drawItemPicture(item, CatCoat.CHEESE) }
        tile(GAP.toFloat(), top + TILE_PX + GAP) { drawItemPicture(item, CatCoat.TUXEDO) }
        // The hub's portrait, eyes open.
        var x = GAP + TILE_PX + GAP.toFloat()
        panel(x, top, portraitW, portraitH, PAGE) {
            drawCatPortrait(CatCoat.CREAM, wear = setOf(item), cheer = 0f, phase = 0.1f)
        }
        // The run's cat, over the camera, layered as the run layers it: the toy, then the ceiling,
        // then the cat. Calm, and then in a panic with the ceiling at its lowest, on its head.
        x += portraitW + GAP
        for (mood in listOf(CatMood.CALM, CatMood.PANIC)) {
            panel(x, top, run, portraitH, CAMERA) {
                val floor = size.height * 0.82f
                val scale = 1.55f
                val centerX = size.width * 0.6f
                val wear = setOf(item)
                drawCatToyBeside(wear, centerX, floor, scale)
                if (mood == CatMood.PANIC) ceiling(catHeadTop(floor, scale, CatMood.PANIC))
                drawCat(
                    centerX = centerX,
                    baseY = floor,
                    scale = scale,
                    coat = CatCoat.MACKEREL,
                    mood = mood,
                    alarm = if (mood == CatMood.PANIC) 1f else 0f,
                    phase = 0.13f,
                    wear = wear,
                    toy = false,
                )
                drawRect(FLOOR, topLeft = Offset(0f, floor), size = Size(size.width, size.height - floor))
            }
            x += run + GAP
        }
    }
}

private fun overview(items: List<CatItem>, file: File) {
    val columns = 8
    val cell = TILE_PX + 34
    val rows = (items.size + columns - 1) / columns
    render(GAP + columns * (TILE_PX + GAP), GAP + rows * (cell + GAP), file) { label ->
        drawRect(PAGE)
        items.forEachIndexed { i, item ->
            val x = GAP + (i % columns) * (TILE_PX + GAP).toFloat()
            val y = GAP + (i / columns) * (cell + GAP).toFloat()
            tile(x, y) { drawItemPicture(item, CatCoat.CREAM) }
            label(item.name.lowercase(), x + 4f, y + TILE_PX + 24f)
        }
    }
}

/** The slab with its teeth on [headTop], as the run draws it at the end of a life, danger full. */
private fun DrawScope.ceiling(headTop: Float) {
    val tooth = size.width / 14f
    val bottom = headTop - tooth * 0.55f
    val slab = Color(red = 0.92f, green = 0.16f, blue = 0.12f)
    drawRect(slab, size = Size(size.width, bottom))
    for (i in 0 until 14) {
        drawPath(
            Path().apply {
                moveTo(i * tooth, bottom)
                lineTo((i + 0.5f) * tooth, bottom + tooth * 0.55f)
                lineTo((i + 1f) * tooth, bottom)
                close()
            },
            color = slab,
        )
    }
}

/** A wardrobe tile: square, rounded, clipped. */
private fun DrawScope.tile(x: Float, y: Float, draw: DrawScope.() -> Unit) {
    panel(x, y, TILE_PX, TILE_PX, TILE, corner = TILE_PX * 10f / 64f, draw = draw)
}

private fun DrawScope.panel(
    x: Float,
    y: Float,
    w: Int,
    h: Int,
    background: Color,
    corner: Float = 24f,
    draw: DrawScope.() -> Unit,
) {
    val clip = Path().apply {
        addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(corner)))
    }
    clipPath(clip) {
        drawRect(background, topLeft = Offset(x, y), size = Size(w.toFloat(), h.toFloat()))
        val inner = CanvasDrawScope()
        drawContext.canvas.save()
        drawContext.canvas.translate(x, y)
        inner.draw(Density(1f), LayoutDirection.Ltr, drawContext.canvas, Size(w.toFloat(), h.toFloat())) { draw() }
        drawContext.canvas.restore()
    }
}

private fun render(
    width: Int,
    height: Int,
    file: File,
    webp: Boolean = false,
    draw: DrawScope.(label: (String, Float, Float) -> Unit) -> Unit,
) {
    val surface = Surface.makeRasterN32Premul(width, height)
    val skia = surface.canvas
    val canvas: Canvas = skia.asComposeCanvas()
    val typeface = FontMgr.default.matchFamilyStyle("DejaVu Sans", FontStyle.NORMAL)
    val font = Font(typeface, 16f)
    val ink = Paint().apply { color = 0xFFD8D4E0.toInt() }
    val label: (String, Float, Float) -> Unit = { text, x, y -> skia.drawString(text, x, y, font, ink) }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat())) {
        draw(label)
    }
    val image = surface.makeImageSnapshot()
    val data = if (webp) image.encodeToData(EncodedImageFormat.WEBP, 82) else image.encodeToData(EncodedImageFormat.PNG)
    file.writeBytes((data ?: error("could not encode ${file.name}")).bytes)
}
