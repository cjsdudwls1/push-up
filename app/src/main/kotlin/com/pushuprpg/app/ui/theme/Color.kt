package com.pushuprpg.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * There is no light theme, deliberately. A live camera feed sits behind the whole game, most of it
 * is used in a dim room on the floor, and a light surface over video is unreadable — so a light
 * variant would be both worse and twice the QA.
 *
 * Colour is never decoration here: every game state is also carried by luminance, by shape, and by
 * sound, so the screen stays playable in greyscale and audible with the screen off.
 */
object Palette {
    // Surfaces
    val Bg0 = Color(0xFF07080C)
    val Bg1 = Color(0xFF0E1117)
    val Bg2 = Color(0xFF161A23)
    val Bg3 = Color(0xFF1F2430)
    val StrokeSoft = Color(0x1AFFFFFF)
    val StrokeHard = Color(0xFF2E3542)

    // Text
    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xFFA9B3C4)
    val TextTertiary = Color(0xFF6E7A8E)
    val TextOnAccent = Color(0xFF0B0D12)
    val TextDisabled = Color(0xFF4A5464)

    // Brand
    val Brand400 = Color(0xFF9A8CFF)
    val Brand500 = Color(0xFF7C6BFF)
    val Brand600 = Color(0xFF5E4BE0)
    val BrandWash = Color(0x1F7C6BFF)

    // Game semantics
    val Accept = Color(0xFF35E08A)
    val AcceptDim = Color(0x2935E08A)
    val Deep = Color(0xFFFFC53D)
    val DeepDim = Color(0x38FFC53D)
    val DeepSoft = Color(0xFFFFE9A8)
    val Shallow = Color(0xFFFF8A5C)
    val Combo = Color(0xFFFF9F43)
    val ComboHot = Color(0xFFFF6B35)
    val PlayerHp = Color(0xFF45DD8B)
    val BossHp = Color(0xFFFF4D5E)
    val BossHpDark = Color(0xFFC4213A)
    val BossGhost = Color(0x8CFFD2D6)
    val Danger = Color(0xFFFF4D5E)
    val Warn = Color(0xFFFF9F43)
    val Info = Color(0xFF35C3FF)
    val Idle = Color(0xFF8FA3BF)

    // Over-camera
    val ScrimPanel = Color(0x9E0B0E14)
    val ScrimPanelHigh = Color(0xC70B0E14)
    val OutlineInk = Color(0x9E000000)

    // Rank tiers
    val TierCommon = Color(0xFFC6CEDB)
    val TierRare = Color(0xFF4DA3FF)
    val TierEpic = Color(0xFFB48CFF)
    val TierLegend = Color(0xFFFFC53D)
}

/**
 * The four tokens that carry a red/green distinction, swapped for a blue/yellow pair.
 *
 * Only these four change: the rest of the palette already separates on luminance, so a full second
 * palette would be maintenance for nothing.
 */
data class GameColors(
    val accept: Color,
    val acceptDim: Color,
    val deep: Color,
    val deepDim: Color,
    val shallow: Color,
    val idle: Color,
    val playerHp: Color,
    val bossHp: Color,
    val combo: Color,
    val comboHot: Color,
) {
    companion object {
        val Default = GameColors(
            accept = Palette.Accept,
            acceptDim = Palette.AcceptDim,
            deep = Palette.Deep,
            deepDim = Palette.DeepDim,
            shallow = Palette.Shallow,
            idle = Palette.Idle,
            playerHp = Palette.PlayerHp,
            bossHp = Palette.BossHp,
            combo = Palette.Combo,
            comboHot = Palette.ComboHot,
        )

        /** Blue/yellow, for red-green colour vision deficiency. */
        val BlueYellow = Default.copy(
            accept = Color(0xFF4DA3FF),
            acceptDim = Color(0x294DA3FF),
            shallow = Color(0xFFFF7A3D),
            playerHp = Color(0xFF4DA3FF),
        )
    }
}
