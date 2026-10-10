package com.eplik.ioniq5.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    primaryContainer = AccentDim,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = Error,
)

private val LightColors = lightColorScheme(
    primary = LightAccent,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    error = Error,
)

// Õhukesed numbrid nagu auto näidikutel.
private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 44.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 1.sp),
)

@Composable
fun Ioniq5Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
