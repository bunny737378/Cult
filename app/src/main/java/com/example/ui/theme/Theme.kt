package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CultDarkColorScheme = darkColorScheme(
    primary = CultLuminousCyan,
    onPrimary = CultDarkVoid,
    primaryContainer = CultElevatedCard,
    onPrimaryContainer = CultLuminousCyan,
    secondary = CultElectricViolet,
    onSecondary = CultTextPrimary,
    secondaryContainer = CultMidnightSurface,
    onSecondaryContainer = CultElectricViolet,
    tertiary = CultNeonAmber,
    onTertiary = CultDarkVoid,
    background = CultDarkVoid,
    onBackground = CultTextPrimary,
    surface = CultMidnightSurface,
    onSurface = CultTextPrimary,
    surfaceVariant = CultElevatedCard,
    onSurfaceVariant = CultTextSecondary,
    outline = CultGlassBorder,
    outlineVariant = CultGlassRim
)

@Composable
fun CultTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = CultDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = CultDarkVoid.toArgb()
                window.navigationBarColor = CultDarkVoid.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
