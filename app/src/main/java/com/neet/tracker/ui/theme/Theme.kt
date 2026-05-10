package com.neet.tracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Deep space dark palette
val DeepNavy = Color(0xFF050B18)
val CosmicBlue = Color(0xFF0A1628)
val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFF7C4DFF)
val NeonGold = Color(0xFFFFD700)
val NeonGreen = Color(0xFF00E676)
val NeonRed = Color(0xFFFF1744)
val NeonOrange = Color(0xFFFF6D00)
val GlassWhite = Color(0xFFFFFFFF)
val GlassSurface = Color(0x1AFFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val CardGradientStart = Color(0xFF0D1B2A)
val CardGradientEnd = Color(0xFF1A2744)
val StatusExpected = Color(0xFFFFD700)
val StatusCompleted = Color(0xFF00E676)
val StatusRevision = Color(0xFF7C4DFF)
val StatusCross = Color(0xFFFF1744)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DeepNavy,
    primaryContainer = Color(0xFF003545),
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = GlassWhite,
    secondaryContainer = Color(0xFF21005D),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = NeonGold,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = GlassWhite,
    surface = CosmicBlue,
    onSurface = GlassWhite,
    surfaceVariant = Color(0xFF1A2744),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = GlassBorder,
    error = NeonRed,
    onError = GlassWhite,
)

@Composable
fun NEETTrackerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NEETTypography,
        content = content
    )
}
