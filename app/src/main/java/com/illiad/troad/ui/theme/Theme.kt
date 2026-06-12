package com.illiad.troad.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ----------------------------------------------------
// Color Scheme Architecture Mappings
// ----------------------------------------------------

private val DarkColorScheme = darkColorScheme(
    primary = OceanPrimaryDark,       // Clear Wave Blue (#0284C7)
    secondary = OceanSecondaryDark,   // Warm Coral Action Node (#FF70A6)
    tertiary = OceanTertiaryDark,     // Soft Sand-Gold Highlights (#FFE4A7)

    background = OceanBackgroundDark, // Deep Midnight Base (#020617)
    surface = OceanSurfaceDark,       // Deep Ocean Navy Card Anchor (#1E3A8A)

    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,         // Dark text for better contrast on bright gold
    onBackground = Color(0xFFF8FAFC), // High-visibility off-white text (90% opacity fallback)
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = OceanPrimaryLight,       // Muted Wave Blue
    secondary = OceanSecondaryLight,   // High-contrast Coral
    tertiary = OceanTertiaryLight,     // Deepened Sand-Gold

    background = OceanBackgroundLight, // Light canvas (#F8FAFC)
    surface = OceanSurfaceLight,       // Slate surface gray (#E2E8F0)

    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF020617), // Deep midnight text for contrast
    onSurface = Color(0xFF020617)
)

@Composable
fun TroadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false if you want your custom CSS brand identity to override Android's dynamic system themes
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Ensure this matches your project's Typography val
        content = content
    )
}
