package com.pushuprpg.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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
        size: Int, line: Int, weight: FontWeight, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
        fontFeatureSettings = tabular,
        textAlign = TextAlign.Center,
    )

    private fun text(
        size: Int, line: Int, weight: FontWeight, tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

    /** The rep counter. Deliberately enormous — it is read at a glance, upside down, mid-effort. */
    val heroCount = numeric(120, 108, FontWeight.ExtraBold, -0.03f)
    val heroCountSmall = numeric(88, 80, FontWeight.ExtraBold, -0.03f)
    val displayL = numeric(66, 68, FontWeight.ExtraBold, -0.02f)
    val displayM = numeric(46, 48, FontWeight.ExtraBold, -0.02f)
    val numeralL = numeric(34, 38, FontWeight.ExtraBold)
    val numeralM = numeric(19, 24, FontWeight.Bold)

    val headline = text(28, 34, FontWeight.Bold, -0.01f)
    val titleL = text(22, 28, FontWeight.Bold)
    val titleM = text(20, 26, FontWeight.SemiBold)
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
