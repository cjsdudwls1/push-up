package com.pushuprpg.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalGameColors = staticCompositionLocalOf { GameColors.Default }

/** Set when the user has asked for less movement, or the system animator scale is zero. */
val LocalReduceMotion = staticCompositionLocalOf { false }

private val DarkScheme = darkColorScheme(
    primary = Palette.Brand500,
    onPrimary = Palette.TextOnAccent,
    primaryContainer = Palette.Brand600,
    onPrimaryContainer = Palette.TextPrimary,
    secondary = Palette.Accept,
    onSecondary = Palette.TextOnAccent,
    background = Palette.Bg1,
    onBackground = Palette.TextPrimary,
    surface = Palette.Bg2,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.Bg3,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.StrokeHard,
    outlineVariant = Palette.StrokeSoft,
    error = Palette.Danger,
    onError = Palette.TextPrimary,
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
    labelLarge = Type.labelL,
    labelMedium = Type.labelM,
    labelSmall = Type.labelS,
)

/**
 * The app theme. Always dark — see [Palette] for why there is no light variant.
 *
 * [isSystemInDarkTheme] is deliberately ignored rather than consulted and overridden, so nobody
 * later mistakes this for an oversight.
 */
@Composable
fun PushupRpgTheme(
    colourBlindSafe: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val gameColors = if (colourBlindSafe) GameColors.BlueYellow else GameColors.Default
    CompositionLocalProvider(
        LocalGameColors provides gameColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = DarkScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
