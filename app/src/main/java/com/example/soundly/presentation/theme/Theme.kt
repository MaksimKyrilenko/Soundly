package com.example.soundly.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1E),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1E),
    surfaceVariant = Color(0xFFF3EDF7),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB794F6),
    onPrimary = Color(0xFF1E0A3C),
    primaryContainer = Color(0xFF5B3D8A),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFD4BBFF),
    onSecondary = Color(0xFF2D1F4A),
    secondaryContainer = Color(0xFF453666),
    onSecondaryContainer = Color(0xFFF0E6FF),
    tertiary = Color(0xFFFFB4C6),
    onTertiary = Color(0xFF3E1929),
    tertiaryContainer = Color(0xFF5A2D3F),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF0D0B12),
    onBackground = Color(0xFFECE6F0),
    surface = Color(0xFF151319),
    onSurface = Color(0xFFECE6F0),
    surfaceVariant = Color(0xFF252230),
    onSurfaceVariant = Color(0xFFD0C8DC),
    outline = Color(0xFF8A8494),
    outlineVariant = Color(0xFF3D3948),
    inverseSurface = Color(0xFFECE6F0),
    inverseOnSurface = Color(0xFF1C1B1E),
    inversePrimary = Color(0xFF6750A4),
    surfaceTint = Color(0xFFB794F6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun SoundlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
