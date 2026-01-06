package com.example.soundly.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============== СВЕТЛАЯ ТЕМА НА ОСНОВЕ ПАЛИТРЫ ==============
private fun createLightColorScheme(palette: ColorPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.cardLight,
    onPrimaryContainer = palette.textPrimary,
    secondary = palette.accent,
    onSecondary = Color.White,
    secondaryContainer = palette.cardMid,
    onSecondaryContainer = palette.textPrimary,
    tertiary = palette.accentSecondary,
    onTertiary = Color.White,
    tertiaryContainer = palette.cardDark,
    onTertiaryContainer = palette.textPrimary,
    background = palette.backgroundBottom,
    onBackground = palette.textPrimary,
    surface = palette.cardLight,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.cardMid,
    onSurfaceVariant = palette.textSecondary,
    surfaceContainerLowest = palette.backgroundTop,
    surfaceContainerLow = palette.backgroundMid,
    surfaceContainer = palette.cardDark,
    surfaceContainerHigh = palette.cardMid,
    surfaceContainerHighest = palette.cardLight,
    outline = palette.textSecondary,
    outlineVariant = palette.textSecondary.copy(alpha = 0.5f)
)

// ============== СОЗДАНИЕ ТЁМНОЙ ТЕМЫ НА ОСНОВЕ ПАЛИТРЫ ==============
private fun createDarkColorScheme(palette: ColorPalette) = darkColorScheme(
    // Primary - яркий акцент
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.primaryDark,
    onPrimaryContainer = Color(0xFFEDE8FF),
    
    // Secondary - светлый вариант
    secondary = palette.primaryLight,
    onSecondary = Color(0xFF1E0A3C),
    secondaryContainer = palette.cardMid,
    onSecondaryContainer = Color(0xFFF0E6FF),
    
    // Tertiary - акцент
    tertiary = palette.accent,
    onTertiary = Color.White,
    tertiaryContainer = palette.cardDark,
    onTertiaryContainer = Color(0xFFD6E3FF),
    
    // Background - глубокий тёмный фон
    background = palette.backgroundBottom,
    onBackground = Color(0xFFFFFFFF),
    
    // Surface - поверхности карточек
    surface = palette.cardDark,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = palette.cardMid,
    onSurfaceVariant = Color(0xFFD0C9E0),
    
    // Surface containers для Material 3
    surfaceContainerLowest = palette.backgroundBottom,
    surfaceContainerLow = palette.backgroundMid,
    surfaceContainer = palette.cardDark,
    surfaceContainerHigh = palette.cardMid,
    surfaceContainerHighest = palette.cardLight,
    
    // Outline
    outline = Color(0xFF8580A0),
    outlineVariant = Color(0xFF4A4565),
    
    // Inverse
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = palette.backgroundBottom,
    inversePrimary = palette.primaryDeep,
    
    // Surface tint
    surfaceTint = palette.primary,
    
    // Error
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    // Scrim
    scrim = Color(0xFF000000)
)

@Composable
fun SoundlyTheme(
    darkTheme: Boolean = true,
    colorPalette: ColorPalette = ColorPalette.Purple,
    content: @Composable () -> Unit
) {
    // Используем isDark из палитры для определения темы
    val useDarkTheme = colorPalette.isDark
    val colorScheme = if (useDarkTheme) createDarkColorScheme(colorPalette) else createLightColorScheme(colorPalette)
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides useDarkTheme,
        LocalColorPalette provides colorPalette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
