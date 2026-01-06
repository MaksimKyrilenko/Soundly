package com.example.soundly.domain.model

import kotlinx.serialization.Serializable

/**
 * Настройки для экспорта аудио с применёнными эффектами
 */
@Serializable
data class AudioExportSettings(
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val preservePitch: Boolean = true,
    val bands: List<Float> = List(10) { 0f },
    val bassBoost: Float = 0f,
    val stereoWidth: Float = 100f,
    val isMono: Boolean = false,
    val preamp: Float = 0f,
    val playbackMode: PlaybackMode = PlaybackMode.NORMAL
) {
    /**
     * Генерирует суффикс для названия трека на основе настроек
     */
    fun generateNameSuffix(): String {
        val parts = mutableListOf<String>()
        
        // Режим воспроизведения
        when (playbackMode) {
            PlaybackMode.NIGHTCORE -> parts.add("Nightcore")
            PlaybackMode.DAYCORE -> parts.add("Daycore")
            PlaybackMode.DOUBLE_TIME -> parts.add("DT")
            PlaybackMode.HALF_TIME -> parts.add("HT")
            else -> {
                // Кастомные настройки
                if (speed != 1.0f) {
                    parts.add("${String.format("%.2f", speed)}x")
                }
                if (pitch != 1.0f && !preservePitch) {
                    parts.add("pitch ${String.format("%.2f", pitch)}x")
                }
            }
        }
        
        // Эквалайзер
        val hasEqChanges = bands.any { it != 0f }
        if (hasEqChanges) {
            parts.add("EQ")
        }
        
        // Бас
        if (bassBoost > 10f) {
            parts.add("Bass+")
        }
        
        // Стерео
        if (isMono) {
            parts.add("Mono")
        } else if (stereoWidth != 100f) {
            parts.add("Stereo ${stereoWidth.toInt()}%")
        }
        
        return if (parts.isEmpty()) "Custom" else parts.joinToString(" ")
    }
    
    /**
     * Проверяет, есть ли какие-либо изменения относительно оригинала
     */
    fun hasChanges(): Boolean {
        return speed != 1.0f ||
               pitch != 1.0f ||
               bands.any { it != 0f } ||
               bassBoost > 0f ||
               stereoWidth != 100f ||
               isMono ||
               preamp != 0f
    }
}
