package com.pushuprpg.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pushuprpg.app.R

/**
 * The pixel faces, all under the SIL Open Font License (`docs/fonts/`). By the owner's request
 * (2026-11-10, "싹다") the whole app wears the pixel demo's look, type included.
 *
 * Galmuri11 is everything with Korean in it, in two weights: bold for numbers and headings, regular
 * for sentences. Cut down to the Latin letters and the 2,350 Hangul syllables of KS X 1001 plus
 * every character in `strings.xml` (anything else, an unusual syllable in a cat's name, falls back
 * to the phone's font). Silkscreen is the demo's HUD face, for the run's Latin-only labels (SEC,
 * BEST, xCOMBO, DEPTH); it has no Hangul, so it is never given Korean.
 */
val Galmuri = FontFamily(
    Font(R.font.galmuri11_regular, FontWeight.Normal),
    Font(R.font.galmuri11_bold, FontWeight.Bold),
)
val Silkscreen = FontFamily(Font(R.font.silkscreen_regular))

/**
 * Type scale.
 *
 * Sized for the actual viewing condition, which is unusual: the reader is lying on the floor about
 * a metre from the phone, looking up at it at a steep angle, and for half of every rep their own
 * shoulders are in the way. So the numbers that matter are very large, and nothing that matters is
 * below 15sp.
 *
 * Every numeric style carries tabular figures. Without them a counter jitters sideways on each
 * change, which at 120sp is impossible to ignore.
 */
object Type {
    private val tabular = "tnum"

    private fun numeric(
        size: Int, line: Int, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = Galmuri,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = tabular,
        textAlign = TextAlign.Center,
    )

    private fun heading(
        size: Int, line: Int, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = Galmuri,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

    private fun text(
        size: Int, line: Int, weight: FontWeight, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = Galmuri,
        // Two weights only: anything heavier than regular is the bold cut, never a synthesised one.
        fontWeight = if (weight >= FontWeight.SemiBold) FontWeight.Bold else FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

    /** The rep counter. Deliberately enormous — it is read at a glance, upside down, mid-effort. */
    val heroCount = numeric(120, 108, -0.03f)
    val heroCountSmall = numeric(88, 80, -0.03f)
    val displayL = numeric(66, 68, -0.02f)
    val displayM = numeric(46, 48, -0.02f)
    val numeralL = numeric(34, 38)
    val numeralM = numeric(19, 24)

    val headline = heading(28, 34, -0.01f)
    val titleL = heading(22, 28)
    val titleM = heading(20, 26)
    val bodyL = text(17, 25, FontWeight.Normal)

    /** The floor for anything drawn over the camera image. */
    val bodyM = text(15, 22, FontWeight.Normal)

    /** A text field's floating label, once it has floated. Not for prose; see AppTypography. */
    val bodyS = text(13, 18, FontWeight.Normal)
    val labelL = text(14, 18, FontWeight.SemiBold, 0.01f)
    val labelM = text(13, 16, FontWeight.SemiBold, 0.02f)

    /**
     * Only for the two gauge markers. They sit on solid colour bars with a black outline and are
     * recognised by position, not read as prose.
     */
    val labelS = text(11, 14, FontWeight.Bold, 0.06f)
}
