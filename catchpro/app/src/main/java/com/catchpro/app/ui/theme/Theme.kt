package com.catchpro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = CatchProPrimary,
    secondary = CatchProSecondary,
    tertiary = CatchProAccent,
    surface = CatchProSurface,
    onSurface = CatchProText,
)

private val DarkColors = darkColorScheme(
    primary = CatchProSecondary,
    secondary = CatchProAccent,
)

@Composable
fun CatchProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CatchProTypography,
        content = content,
    )
}
