package com.example.soundly.player.audio

/**
 * Режимы воспроизведения аналогичные osu!
 */
enum class PlaybackEffect {
    /**
     * Нормальное воспроизведение (1.0x speed, 1.0x pitch)
     */
    NORMAL,
    
    /**
     * Double Time (DT) - ускорение на 50% с сохранением pitch
     * Speed: 1.5x, Pitch: 1.0x (time-stretch)
     */
    DOUBLE_TIME,
    
    /**
     * Half Time (HT) - замедление на 25% с сохранением pitch  
     * Speed: 0.75x, Pitch: 1.0x (time-stretch)
     */
    HALF_TIME,
    
    /**
     * Nightcore (NC) - ускорение + повышение pitch (как в osu!)
     * Speed: 1.5x, Pitch: 1.5x (без time-stretch, естественное ускорение)
     */
    NIGHTCORE,
    
    /**
     * Daycore - замедление + понижение pitch
     * Speed: 0.75x, Pitch: 0.75x (без time-stretch, естественное замедление)
     */
    DAYCORE,
    
    /**
     * Кастомный режим - пользовательские значения speed и pitch
     */
    CUSTOM
}

/**
 * Параметры воспроизведения
 */
data class PlaybackParams(
    val effect: PlaybackEffect = PlaybackEffect.NORMAL,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val preservePitch: Boolean = true // true = time-stretch, false = natural speed change
) {
    companion object {
        val NORMAL = PlaybackParams(PlaybackEffect.NORMAL, 1.0f, 1.0f, true)
        
        // osu! Double Time: 1.5x speed, pitch сохраняется
        val DOUBLE_TIME = PlaybackParams(PlaybackEffect.DOUBLE_TIME, 1.5f, 1.0f, true)
        
        // osu! Half Time: 0.75x speed, pitch сохраняется
        val HALF_TIME = PlaybackParams(PlaybackEffect.HALF_TIME, 0.75f, 1.0f, true)
        
        // osu! Nightcore: 1.5x speed + 1.5x pitch (естественное ускорение + немного выше)
        val NIGHTCORE = PlaybackParams(PlaybackEffect.NIGHTCORE, 1.5f, 1.5f, false)
        
        // Daycore: 0.75x speed + 0.75x pitch (естественное замедление)
        val DAYCORE = PlaybackParams(PlaybackEffect.DAYCORE, 0.75f, 0.75f, false)
        
        /**
         * Создать кастомные параметры только со скоростью (pitch сохраняется)
         */
        fun speedOnly(speed: Float) = PlaybackParams(
            effect = PlaybackEffect.CUSTOM,
            speed = speed.coerceIn(0.25f, 3.0f),
            pitch = 1.0f,
            preservePitch = true
        )
        
        /**
         * Создать кастомные параметры с изменением pitch пропорционально скорости
         */
        fun naturalSpeed(speed: Float) = PlaybackParams(
            effect = PlaybackEffect.CUSTOM,
            speed = speed.coerceIn(0.25f, 3.0f),
            pitch = speed.coerceIn(0.25f, 3.0f),
            preservePitch = false
        )
        
        /**
         * Создать полностью кастомные параметры
         */
        fun custom(speed: Float, pitch: Float, preservePitch: Boolean = true) = PlaybackParams(
            effect = PlaybackEffect.CUSTOM,
            speed = speed.coerceIn(0.25f, 3.0f),
            pitch = pitch.coerceIn(0.25f, 3.0f),
            preservePitch = preservePitch
        )
    }
    
    /**
     * Получить эффективный pitch для ExoPlayer
     * Если preservePitch = true и pitch = 1.0, ExoPlayer сам сделает time-stretch
     * Если preservePitch = false, pitch = speed (естественное изменение)
     */
    fun getEffectivePitch(): Float {
        return if (preservePitch && pitch == 1.0f) {
            1.0f // ExoPlayer's Sonic processor will handle time-stretch
        } else {
            pitch
        }
    }
}
