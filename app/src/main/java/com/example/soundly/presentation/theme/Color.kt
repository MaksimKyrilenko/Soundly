package com.example.soundly.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============== COMPOSITION LOCAL ДЛЯ ТЕМЫ ==============
val LocalIsDarkTheme = compositionLocalOf { true }
val LocalColorPalette = compositionLocalOf { ColorPalette.Purple }

// ============== ЦВЕТОВЫЕ ПАЛИТРЫ ==============
enum class ColorPalette(
    val id: String,
    val displayName: String,
    val description: String,
    val isDark: Boolean,
    val primary: Color,
    val primaryLight: Color,
    val primaryDeep: Color,
    val primaryDark: Color,
    val accent: Color,
    val accentSecondary: Color,
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val cardLight: Color,
    val cardMid: Color,
    val cardDark: Color,
    val navBarTop: Color,
    val navBarBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color
) {
    // Оригинальная фиолетовая
    Purple(
        id = "purple",
        displayName = "Фиолетовый",
        description = "Оригинальная тема Soundly",
        isDark = true,
        primary = Color(0xFF8B7CF7),
        primaryLight = Color(0xFFB4A7FF),
        primaryDeep = Color(0xFF6B5DD3),
        primaryDark = Color(0xFF4A3DB0),
        accent = Color(0xFF5B8DEF),
        accentSecondary = Color(0xFFEC4899),
        backgroundTop = Color(0xFF3D3075),
        backgroundMid = Color(0xFF251E50),
        backgroundBottom = Color(0xFF0D0B1A),
        cardLight = Color(0xFF3D3075),
        cardMid = Color(0xFF352A65),
        cardDark = Color(0xFF2D2560),
        navBarTop = Color(0xFF1E1A45),
        navBarBottom = Color(0xFF252050),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xB3FFFFFF)
    ),
    
    // DARK NEON (Night / Phonk / Bass)
    DarkNeon(
        id = "dark_neon",
        displayName = "Neon",
        description = "Phonk, Bass, Night vibe",
        isDark = true,
        primary = Color(0xFF8B5CF6),
        primaryLight = Color(0xFFA78BFA),
        primaryDeep = Color(0xFF7C3AED),
        primaryDark = Color(0xFF6D28D9),
        accent = Color(0xFF22C55E),
        accentSecondary = Color(0xFFEC4899),
        backgroundTop = Color(0xFF181B23),
        backgroundMid = Color(0xFF12151C),
        backgroundBottom = Color(0xFF0B0E14),
        cardLight = Color(0xFF2A2F3A),
        cardMid = Color(0xFF1F232D),
        cardDark = Color(0xFF181B23),
        navBarTop = Color(0xFF12151C),
        navBarBottom = Color(0xFF0B0E14),
        textPrimary = Color(0xFFE6E6E6),
        textSecondary = Color(0xFF9CA3AF)
    ),
    
    // DARK MINIMAL (Spotify-like)
    Spotify(
        id = "spotify",
        displayName = "Minimal",
        description = "Spotify-style минимализм",
        isDark = true,
        primary = Color(0xFF1DB954),
        primaryLight = Color(0xFF1ED760),
        primaryDeep = Color(0xFF1AA34A),
        primaryDark = Color(0xFF168D40),
        accent = Color(0xFFF59E0B),
        accentSecondary = Color(0xFF3B82F6),
        backgroundTop = Color(0xFF1F1F1F),
        backgroundMid = Color(0xFF181818),
        backgroundBottom = Color(0xFF121212),
        cardLight = Color(0xFF2A2A2A),
        cardMid = Color(0xFF242424),
        cardDark = Color(0xFF1F1F1F),
        navBarTop = Color(0xFF181818),
        navBarBottom = Color(0xFF121212),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3)
    ),
    
    // DARK GRADIENT UI
    Gradient(
        id = "gradient",
        displayName = "Gradient",
        description = "Яркие градиенты",
        isDark = true,
        primary = Color(0xFF7C3AED),
        primaryLight = Color(0xFFA78BFA),
        primaryDeep = Color(0xFF6D28D9),
        primaryDark = Color(0xFF5B21B6),
        accent = Color(0xFFEC4899),
        accentSecondary = Color(0xFF22D3EE),
        backgroundTop = Color(0xFF1A1A1A),
        backgroundMid = Color(0xFF141414),
        backgroundBottom = Color(0xFF0F0F0F),
        cardLight = Color(0xFF262626),
        cardMid = Color(0xFF1F1F1F),
        cardDark = Color(0xFF1A1A1A),
        navBarTop = Color(0xFF141414),
        navBarBottom = Color(0xFF0F0F0F),
        textPrimary = Color(0xFFF9FAFB),
        textSecondary = Color(0xFF9CA3AF)
    ),
    
    // AMOLED PURE BLACK
    Amoled(
        id = "amoled",
        displayName = "AMOLED",
        description = "Чистый чёрный для OLED",
        isDark = true,
        primary = Color(0xFF3B82F6),
        primaryLight = Color(0xFF60A5FA),
        primaryDeep = Color(0xFF2563EB),
        primaryDark = Color(0xFF1D4ED8),
        accent = Color(0xFF22C55E),
        accentSecondary = Color(0xFFEF4444),
        backgroundTop = Color(0xFF0A0A0A),
        backgroundMid = Color(0xFF050505),
        backgroundBottom = Color(0xFF000000),
        cardLight = Color(0xFF1A1A1A),
        cardMid = Color(0xFF141414),
        cardDark = Color(0xFF0A0A0A),
        navBarTop = Color(0xFF0A0A0A),
        navBarBottom = Color(0xFF000000),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFA1A1AA)
    ),
    
    // WARM CLUB (Trap / Hip-Hop)
    WarmClub(
        id = "warm_club",
        displayName = "Club",
        description = "Trap, Hip-Hop, клубный стиль",
        isDark = true,
        primary = Color(0xFFF97316),
        primaryLight = Color(0xFFFB923C),
        primaryDeep = Color(0xFFEA580C),
        primaryDark = Color(0xFFC2410C),
        accent = Color(0xFFF43F5E),
        accentSecondary = Color(0xFFFBBF24),
        backgroundTop = Color(0xFF1F1411),
        backgroundMid = Color(0xFF180F0C),
        backgroundBottom = Color(0xFF120C0A),
        cardLight = Color(0xFF2A1A16),
        cardMid = Color(0xFF241612),
        cardDark = Color(0xFF1F1411),
        navBarTop = Color(0xFF180F0C),
        navBarBottom = Color(0xFF120C0A),
        textPrimary = Color(0xFFF5F5F4),
        textSecondary = Color(0xFFA8A29E)
    ),
    
    // LIGHT AUDIOPHILE (Hi-Fi / Premium)
    LightPremium(
        id = "light_premium",
        displayName = "Light",
        description = "Светлая премиум тема",
        isDark = false,
        primary = Color(0xFFEF4444),
        primaryLight = Color(0xFFF87171),
        primaryDeep = Color(0xFFDC2626),
        primaryDark = Color(0xFFB91C1C),
        accent = Color(0xFF6366F1),
        accentSecondary = Color(0xFF8B5CF6),
        backgroundTop = Color(0xFFFFFFFF),
        backgroundMid = Color(0xFFF9FAFB),
        backgroundBottom = Color(0xFFF3F4F6),
        cardLight = Color(0xFFFFFFFF),
        cardMid = Color(0xFFF9FAFB),
        cardDark = Color(0xFFF3F4F6),
        navBarTop = Color(0xFFFFFFFF),
        navBarBottom = Color(0xFFF3F4F6),
        textPrimary = Color(0xFF111827),
        textSecondary = Color(0xFF6B7280)
    ),
    
    // Синяя (оригинальная)
    Blue(
        id = "blue",
        displayName = "Синий",
        description = "Классический синий",
        isDark = true,
        primary = Color(0xFF5B9CF7),
        primaryLight = Color(0xFF8BBFFF),
        primaryDeep = Color(0xFF3D7DD3),
        primaryDark = Color(0xFF2A5DB0),
        accent = Color(0xFF4ECDC4),
        accentSecondary = Color(0xFFF59E0B),
        backgroundTop = Color(0xFF2A4075),
        backgroundMid = Color(0xFF1A2850),
        backgroundBottom = Color(0xFF0A0D1A),
        cardLight = Color(0xFF2A4075),
        cardMid = Color(0xFF253565),
        cardDark = Color(0xFF1E2D60),
        navBarTop = Color(0xFF152040),
        navBarBottom = Color(0xFF1A2550),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xB3FFFFFF)
    ),
    
    // Зелёная (оригинальная)
    Green(
        id = "green",
        displayName = "Зелёный",
        description = "Природный зелёный",
        isDark = true,
        primary = Color(0xFF4CAF50),
        primaryLight = Color(0xFF81C784),
        primaryDeep = Color(0xFF388E3C),
        primaryDark = Color(0xFF2E7D32),
        accent = Color(0xFF00BCD4),
        accentSecondary = Color(0xFFFFC107),
        backgroundTop = Color(0xFF2D5030),
        backgroundMid = Color(0xFF1A3520),
        backgroundBottom = Color(0xFF0A150A),
        cardLight = Color(0xFF2D5030),
        cardMid = Color(0xFF254528),
        cardDark = Color(0xFF1E3D20),
        navBarTop = Color(0xFF152515),
        navBarBottom = Color(0xFF1A3018),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xB3FFFFFF)
    ),
    
    // Красная (оригинальная)
    Red(
        id = "red",
        displayName = "Красный",
        description = "Энергичный красный",
        isDark = true,
        primary = Color(0xFFEF5350),
        primaryLight = Color(0xFFFF8A80),
        primaryDeep = Color(0xFFD32F2F),
        primaryDark = Color(0xFFC62828),
        accent = Color(0xFFFF9800),
        accentSecondary = Color(0xFFFFEB3B),
        backgroundTop = Color(0xFF5D2A2A),
        backgroundMid = Color(0xFF3D1A1A),
        backgroundBottom = Color(0xFF1A0A0A),
        cardLight = Color(0xFF5D2A2A),
        cardMid = Color(0xFF4D2525),
        cardDark = Color(0xFF3D1E1E),
        navBarTop = Color(0xFF2D1515),
        navBarBottom = Color(0xFF351A1A),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xB3FFFFFF)
    );
    
    companion object {
        fun fromId(id: String): ColorPalette {
            return entries.find { it.id == id } ?: Purple
        }
    }
}

// ============== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ДЛЯ ГРАДИЕНТОВ ==============

@Composable
fun backgroundGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.verticalGradient(
        colors = listOf(
            palette.backgroundTop,
            palette.backgroundMid,
            palette.backgroundBottom
        )
    )
}

@Composable
fun playerGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.verticalGradient(
        colors = listOf(
            palette.primary.copy(alpha = 0.3f),
            palette.backgroundMid,
            palette.backgroundBottom
        )
    )
}

@Composable
fun cardGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.horizontalGradient(
        colors = listOf(
            palette.cardDark,
            palette.cardMid,
            palette.cardDark
        )
    )
}

@Composable
fun navBarGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.verticalGradient(
        colors = listOf(
            palette.navBarTop,
            palette.navBarBottom
        )
    )
}

fun backgroundGradient(palette: ColorPalette): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            palette.backgroundTop,
            palette.backgroundMid,
            palette.backgroundBottom
        )
    )
}

fun cardGradient(palette: ColorPalette): Brush {
    return Brush.horizontalGradient(
        colors = listOf(
            palette.cardDark,
            palette.cardMid,
            palette.cardDark
        )
    )
}

@Composable
fun dialogGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.verticalGradient(
        colors = listOf(
            palette.cardLight,
            palette.cardMid,
            palette.cardDark
        )
    )
}

@Composable
fun miniPlayerGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.horizontalGradient(
        colors = listOf(
            palette.cardDark,
            palette.cardMid,
            palette.cardLight,
            palette.cardMid,
            palette.cardDark
        )
    )
}

@Composable
fun trackCardGradient(): Brush {
    val palette = LocalColorPalette.current
    return Brush.horizontalGradient(
        colors = listOf(
            palette.cardDark,
            palette.cardMid,
            palette.cardDark
        )
    )
}

// ============== LEGACY CONSTANTS (для обратной совместимости) ==============
val DarkCardGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF2D2560),
        Color(0xFF352A65),
        Color(0xFF2D2560)
    )
)

val CardGlassPurple = Color(0xFF352A65)

val DarkBackgroundGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF3D3075),
        Color(0xFF251E50),
        Color(0xFF0D0B1A)
    )
)

val ProgressGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF8B7CF7),
        Color(0xFFB4A7FF)
    )
)

val PurplePrimary = Color(0xFF8B7CF7)
val PurpleLight = Color(0xFFB4A7FF)
