package com.pushuprpg.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalGameColors = staticCompositionLocalOf { GameColors.Default }

/** Set when the user has asked for less movement, or the system animator scale is zero. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Kept so [AlwaysDark] can pick the right dark game colours without being told. */
private val LocalColourBlindSafe = staticCompositionLocalOf { false }

private val DarkScheme = darkColorScheme(
    primary = Palette.Brand500,
    onPrimary = Palette.TextOnAccent,
    primaryContainer = Palette.Brand600,
    onPrimaryContainer = ThemeColors.Dark.textPrimary,
    secondary = Palette.Accept,
    onSecondary = Palette.TextOnAccent,
    background = ThemeColors.Dark.bg1,
    onBackground = ThemeColors.Dark.textPrimary,
    surface = ThemeColors.Dark.bg2,
    onSurface = ThemeColors.Dark.textPrimary,
    surfaceVariant = ThemeColors.Dark.bg3,
    onSurfaceVariant = ThemeColors.Dark.textSecondary,
    outline = ThemeColors.Dark.strokeHard,
    outlineVariant = ThemeColors.Dark.strokeSoft,
    error = Palette.Danger,
    onError = ThemeColors.Dark.textPrimary,
)

/**
 * Mostly the dark scheme's roles on light surfaces. Two differ on purpose: a checked switch's
 * thumb is `onPrimary` and sits on the light theme's deep green, so it has to be light; and an
 * unchecked thumb is `outline`, which would vanish into a light track at the dark stroke weight.
 */
private val LightScheme = lightColorScheme(
    primary = Palette.Brand600,
    onPrimary = Palette.TextOnBrand,
    primaryContainer = Palette.Brand500,
    onPrimaryContainer = Palette.TextOnAccent,
    secondary = GameColors.DefaultLight.accept,
    onSecondary = Palette.TextOnBrand,
    background = ThemeColors.Light.bg1,
    onBackground = ThemeColors.Light.textPrimary,
    surface = ThemeColors.Light.bg2,
    onSurface = ThemeColors.Light.textPrimary,
    surfaceVariant = ThemeColors.Light.bg3,
    onSurfaceVariant = ThemeColors.Light.textSecondary,
    surfaceContainerHighest = ThemeColors.Light.bg3,
    outline = Color(0xFF8792A5),
    outlineVariant = ThemeColors.Light.strokeSoft,
    error = GameColors.DefaultLight.bossHp,
    onError = Palette.TextOnBrand,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography(
    displayLarge = Type.displayL,
    displayMedium = Type.displayM,
    headlineLarge = Type.headline,
    titleLarge = Type.titleL,
    titleMedium = Type.titleM,
    bodyLarge = Type.bodyL,
    bodyMedium = Type.bodyM,
    // Never left to Material's default. A text field animates its label from bodyLarge to
    // bodySmall, and interpolating our em letter-spacing with the default's sp throws ("Cannot
    // perform operation for Em and Sp") the moment the field is focused.
    bodySmall = Type.bodyS,
    labelLarge = Type.labelL,
    labelMedium = Type.labelM,
    labelSmall = Type.labelS,
)

/**
 * The app theme: the user's dark or light choice for the menus.
 *
 * Dark is the default, and the run ignores the choice altogether — see [AlwaysDark]. The system
 * setting is not consulted: the app had one look for long enough that following the system would
 * turn it white under people who never asked, the first time they opened it after an update.
 */
@Composable
fun PushupRpgTheme(
    darkTheme: Boolean = true,
    colourBlindSafe: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColourBlindSafe provides colourBlindSafe,
        LocalReduceMotion provides reduceMotion,
    ) {
        Themed(darkTheme = darkTheme, content = content)
    }
}

/**
 * Pins everything inside it to the dark theme, whatever the user chose.
 *
 * For the run. The battle and survival screens draw over the live camera, where a light surface
 * is unreadable, and the result screen closes a run in the same dark it was played in rather
 * than flashing white between the last rep and the summary.
 */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) {
    Themed(darkTheme = true, content = content)
}

@Composable
private fun Themed(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalThemeColors provides if (darkTheme) ThemeColors.Dark else ThemeColors.Light,
        LocalGameColors provides GameColors.of(darkTheme, LocalColourBlindSafe.current),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
