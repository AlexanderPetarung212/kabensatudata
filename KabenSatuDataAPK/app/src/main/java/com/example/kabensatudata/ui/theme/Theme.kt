package com.example.kabensatudata.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TealColorScheme = lightColorScheme(
    primary = Color(0xFF00B894),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F8F5),
    onPrimaryContainer = Color(0xFF005C4A),
    secondary = Color(0xFF6C7A89),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1A1D29),
    tertiary = Color(0xFFFF9F43),
    onTertiary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1D29),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D29),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF6C7A89),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
    outline = Color(0xFFE2E8F0)
)

@Composable
fun KabenSatuDataTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = TealColorScheme

    // Make status bar match our teal header
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color(0xFF009B7D).toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}