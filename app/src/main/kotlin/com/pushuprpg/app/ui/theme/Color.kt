package com.pushuprpg.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * The menus follow the user's dark/light choice; the run does not. A live camera feed sits behind
 * the battle and survival screens, most of it is used in a dim room on the floor, and a light
 * surface over video is unreadable — so everything from the first rep to the result screen is
 * wrapped in [AlwaysDark], and the choice only ever reaches screens with nothing behind them.
 *
 * The surface and text tokens below read the current [ThemeColors], the way
 * `MaterialTheme.colorScheme` does, so a call site cannot tell which theme it is in and does not
 * need to. The rest are fixed: they sit on brand or game colour, or over the camera, where the
 * surface under them never changes.
 *
 * Colour is never decoration here: every game state is also carried by luminance, by shape, and by
 * sound, so the screen stays playable in greyscale and audible with the screen off.
 */
object Palette {
    // Surfaces
    val Bg0: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.bg0
    val Bg1: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.bg1
    val Bg2: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.bg2
    val Bg3: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.bg3
    val StrokeSoft: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.strokeSoft
    val StrokeHard: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.strokeHard

    // Text
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.textTertiary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.textDisabled
    val TextOnAccent = Color(0xFF0B0D12)

    /** Text on [Brand600], which is dark enough to want light text in either theme. */
    val TextOnBrand = Color(0xFFF2F5FA)

    // Brand
    /** Brand colour used as text — a link, a level-up line. Deepens on a light surface. */
    val Brand400: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.brandText
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
    val Info: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.info
    val Idle = Color(0xFF8FA3BF)

    // Over-camera
    val ScrimPanel = Color(0x9E0B0E14)
    val ScrimPanelHigh = Color(0xC70B0E14)
    val OutlineInk = Color(0x9E000000)

    // Rank tiers. Read as text on the hub, so they deepen on a light surface like everything else.
    val TierCommon: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.tierCommon
    val TierRare: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.tierRare
    val TierEpic: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.tierEpic
    val TierLegend: Color @Composable @ReadOnlyComposable get() = LocalThemeColors.current.tierLegend
}

/**
 * The tokens that change between dark and light: the surfaces, the text on them, and the few
 * accents that are read as text on them.
 *
 * Every light value was chosen by contrast rather than by eye — at least 4.3:1 on every surface
 * it can land on, which is above what the dark theme's tertiary text already manages. The
 * disabled tone is the exception, in both themes, because it is meant to recede.
 */
@Immutable
data class ThemeColors(
    val isDark: Boolean,
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val bg3: Color,
    val strokeSoft: Color,
    val strokeHard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val brandText: Color,
    val info: Color,
    val tierCommon: Color,
    val tierRare: Color,
    val tierEpic: Color,
    val tierLegend: Color,
) {
    companion object {
        val Dark = ThemeColors(
            isDark = true,
            bg0 = Color(0xFF07080C),
            bg1 = Color(0xFF0E1117),
            bg2 = Color(0xFF161A23),
            bg3 = Color(0xFF1F2430),
            strokeSoft = Color(0x1AFFFFFF),
            strokeHard = Color(0xFF2E3542),
            textPrimary = Color(0xFFF2F5FA),
            textSecondary = Color(0xFFA9B3C4),
            textTertiary = Color(0xFF6E7A8E),
            textDisabled = Color(0xFF4A5464),
            brandText = Color(0xFF9A8CFF),
            info = Color(0xFF35C3FF),
            tierCommon = Color(0xFFC6CEDB),
            tierRare = Color(0xFF4DA3FF),
            tierEpic = Color(0xFFB48CFF),
            tierLegend = Color(0xFFFFC53D),
        )

        val Light = ThemeColors(
            isDark = false,
            bg0 = Color(0xFFE9ECF2),
            bg1 = Color(0xFFF3F5F9),
            bg2 = Color(0xFFFFFFFF),
            bg3 = Color(0xFFE4E8EF),
            strokeSoft = Color(0x1A0B0D12),
            strokeHard = Color(0xFFD2D8E2),
            textPrimary = Color(0xFF11141B),
            textSecondary = Color(0xFF465164),
            textTertiary = Color(0xFF5F6B7E),
            textDisabled = Color(0xFF9AA3B2),
            brandText = Color(0xFF5A47D6),
            info = Color(0xFF0A6E9E),
            tierCommon = Color(0xFF5F6B7E),
            tierRare = Color(0xFF1C62C4),
            tierEpic = Color(0xFF6E43CC),
            tierLegend = Color(0xFF8F5A00),
        )
    }
}

val LocalThemeColors = staticCompositionLocalOf { ThemeColors.Dark }

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

        /**
         * The same meanings for a light surface.
         *
         * The dark theme's greens and golds are bright because they sit on near-black; on white
         * they fall under 2:1 and a "cleared" label disappears. These keep the hue and give up
         * brightness until they read as text again. They only ever reach the menus — the run is
         * always dark.
         */
        val DefaultLight = GameColors(
            accept = Color(0xFF0B7A45),
            acceptDim = Color(0x1F0B7A45),
            deep = Color(0xFF8F5A00),
            deepDim = Color(0x2E8F5A00),
            shallow = Color(0xFFB33A0B),
            idle = Color(0xFF5F6B7E),
            playerHp = Color(0xFF0B7A45),
            bossHp = Color(0xFFC8243A),
            combo = Color(0xFFA94F00),
            comboHot = Color(0xFFB33A0B),
        )

        val BlueYellowLight = DefaultLight.copy(
            accept = Color(0xFF1C62C4),
            acceptDim = Color(0x1F1C62C4),
            shallow = Color(0xFFB33A0B),
            playerHp = Color(0xFF1C62C4),
        )

        fun of(dark: Boolean, colourBlindSafe: Boolean): GameColors = when {
            dark && colourBlindSafe -> BlueYellow
            dark -> Default
            colourBlindSafe -> BlueYellowLight
            else -> DefaultLight
        }
    }
}
