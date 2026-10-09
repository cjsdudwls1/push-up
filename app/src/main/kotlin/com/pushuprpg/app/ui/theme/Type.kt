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
 * The two faces, both under the SIL Open Font License (`docs/fonts/`), both cut down to the Latin
 * letters and the 2,350 Hangul syllables of KS X 1001 plus every character in `strings.xml`
 * (anything else, an unusual syllable in a cat's name, falls back to the phone's font).
 *
 * Orbit is the HUD: every number and every heading, the counter's 120sp included. Its geometric
 * figures are what the ring and the start button were drawn around (concept C, 2026-10-09). Gowun
 * Dodum is the voice: everything read as a sentence, where Orbit's square letters tire the eye.
 * Each ships one weight; a heavier style asks for a synthesised one, which is kept for Gowun Dodum's
 * small labels and not asked of Orbit, whose own weight is already heavy.
 */
val Orbit = FontFamily(Font(R.font.orbit_regular))
val GowunDodum = FontFamily(Font(R.font.gowun_dodum_regular))

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
        fontFamily = Orbit,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = tabular,
        textAlign = TextAlign.Center,
    )

    private fun heading(
        size: Int, line: Int, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = Orbit,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

    private fun text(
        size: Int, line: Int, weight: FontWeight, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = GowunDodum,
        fontWeight = weight,
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
