package com.hebrewcal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFFD4AF37),   // Gold
    onPrimary        = Color(0xFF1A1400),
    primaryContainer = Color(0xFF3D3000),
    onPrimaryContainer = Color(0xFFFFDF6B),
    secondary        = Color(0xFF7EC8E3),   // Sky blue
    onSecondary      = Color(0xFF003546),
    background       = Color(0xFF121218),
    surface          = Color(0xFF1A1A25),
    surfaceVariant   = Color(0xFF222230),
    onSurface        = Color(0xFFE8D5B7),
    onSurfaceVariant = Color(0xFFAA9977)
)

private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF8B6914),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF6B),
    onPrimaryContainer = Color(0xFF261900),
    secondary        = Color(0xFF00639C),
    onSecondary      = Color(0xFFFFFFFF),
    background       = Color(0xFFFAF8F2),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFF3EFE3),
    onSurface        = Color(0xFF1C1B16),
    onSurfaceVariant = Color(0xFF4D4639)
)

@Composable
fun HebrewCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
