package com.catchpro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = CatchProPrimary,
    onPrimary = CatchProSurface,
    primaryContainer = CatchProPrimarySoft,
    onPrimaryContainer = CatchProPrimaryDark,
    secondary = CatchProSecondary,
    onSecondary = CatchProSurface,
    tertiary = CatchProAccent,
    background = CatchProSurface,
    onBackground = CatchProText,
    surface = CatchProSurface,
    surfaceVariant = CatchProSurfaceSoft,
    onSurface = CatchProText,
    onSurfaceVariant = CatchProMuted,
    outline = CatchProLine,
    outlineVariant = CatchProPrimaryLine,
)

private val DarkColors = darkColorScheme(
    primary = CatchProAccent,
    onPrimary = CatchProText,
    primaryContainer = CatchProPrimaryDark,
    onPrimaryContainer = CatchProSurface,
    secondary = CatchProSecondary,
    tertiary = CatchProPrimary,
    surface = Color(0xFF111827),
    onSurface = CatchProSurface,
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFD1D5DB),
)

private val CatchProShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun CatchProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CatchProTypography,
        shapes = CatchProShapes,
        content = content,
    )
}

