package com.moodcamera.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MoodColorScheme = darkColorScheme(
    primary = MoodAccent,
    onPrimary = MoodBlack,
    primaryContainer = MoodAccentContainer,
    secondary = MoodAccentDim,
    background = MoodBlack,
    surface = MoodSurface,
    surfaceVariant = MoodSurfaceVariant,
    onBackground = MoodOnSurface,
    onSurface = MoodOnSurface,
    onSurfaceVariant = MoodOnSurfaceVariant,
    error = MoodError,
    outline = MoodOnSurfaceVariant
)

@Composable
fun MoodSnapTheme(content: @Composable () -> Unit) {
    val colorScheme = MoodColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
