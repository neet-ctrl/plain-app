package com.neet.tracker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Deep Space Neon Palette ──────────────────────────────────────────────────
val DeepNavy      = Color(0xFF040B16)
val CosmicBlue    = Color(0xFF080F20)
val NeonCyan      = Color(0xFF00E5FF)
val NeonPurple    = Color(0xFF7C4DFF)
val NeonGold      = Color(0xFFFFD700)
val NeonGreen     = Color(0xFF00E676)
val NeonRed       = Color(0xFFFF1744)
val NeonOrange    = Color(0xFFFF6D00)
val NeonPink      = Color(0xFFFF4081)
val NeonTeal      = Color(0xFF1DE9B6)
val NeonIndigo    = Color(0xFF536DFE)
val GlassWhite    = Color(0xFFFFFFFF)
val GlassSurface  = Color(0x1AFFFFFF)
val GlassBorder   = Color(0x33FFFFFF)
val CardGradientStart = Color(0xFF0D1B2A)
val CardGradientEnd   = Color(0xFF1A2744)

// ─── Status Colors ────────────────────────────────────────────────────────────
val StatusExpected  = Color(0xFFFFD700)   // Gold / Yellow
val StatusCompleted = Color(0xFF00E676)   // Green
val StatusRevision  = Color(0xFF7C4DFF)   // Purple
val StatusCross     = Color(0xFFFF1744)   // Red

// ─── Highlight Colors (for rich text) ─────────────────────────────────────────
val HighlightYellow = Color(0xFFFFEB3B)
val HighlightGreen  = Color(0xFF69F0AE)
val HighlightBlue   = Color(0xFF40C4FF)
val HighlightPink   = Color(0xFFFF80AB)
val HighlightOrange = Color(0xFFFFAB40)

private val DarkColorScheme = darkColorScheme(
    primary              = NeonCyan,
    onPrimary            = DeepNavy,
    primaryContainer     = Color(0xFF003545),
    onPrimaryContainer   = NeonCyan,
    secondary            = NeonPurple,
    onSecondary          = GlassWhite,
    secondaryContainer   = Color(0xFF21005D),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary             = NeonGold,
    onTertiary           = DeepNavy,
    background           = DeepNavy,
    onBackground         = GlassWhite,
    surface              = CosmicBlue,
    onSurface            = GlassWhite,
    surfaceVariant       = Color(0xFF1A2744),
    onSurfaceVariant     = Color(0xFFB0BEC5),
    outline              = GlassBorder,
    error                = NeonRed,
    onError              = GlassWhite,
)

@Composable
fun NEETTrackerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = DarkColorScheme, typography = NEETTypography, content = content)
}
