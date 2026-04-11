package com.example.dystopia.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkGrayPrimary,
    secondary = DarkGraySecondary,
    background = DarkGrayBackground,
    surface = DarkGraySurface,
    surfaceVariant = DarkGraySurfaceVariant,
    onPrimary = DarkGrayOnPrimary,
    onSecondary = DarkGrayOnSecondary,
    onBackground = DarkGrayOnBackground,
    onSurface = DarkGrayOnSurface,
    error = DarkGrayError
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface
)

@Composable
fun DystopiaTheme(
    darkTheme: Boolean = true, // ✅ По умолчанию тёмная
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}