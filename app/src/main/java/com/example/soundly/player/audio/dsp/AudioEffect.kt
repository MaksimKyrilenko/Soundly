package com.example.soundly.player.audio.dsp

import androidx.compose.ui.graphics.Color

/**
 * Аудио эффекты с параметрами
 */
sealed class AudioEffect {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val icon: String
    abstract val color: Long
    abstract val speedRange: ClosedFloatingPointRange<Float>
    abstract val pitchLinked: Boolean
    
    /**
     * Без эффекта
     */
    object None : AudioEffect() {
        override val id = "none"
        override val name = "Normal"
        override val description = "Обычное воспроизведение"
        override val icon = "🎵"
        override val color = 0xFF6B7280L
        override val speedRange = 1f..1f
        override val pitchLinked = false
    }
    
    /**
     * 🎧 Chillcore
     * Скорость: ~0.85x, лёгкое понижение тона, тёплый low-pass, мягкое стерео, tape-сатурация
     */
    data class Chillcore(
        val intensity: Float = 0.7f
    ) : AudioEffect() {
        override val id = "chillcore"
        override val name = "Chillcore"
        override val description = "Расслабленный, тёплый звук"
        override val icon = "🎧"
        override val color = 0xFF8B5CF6L // Purple
        override val speedRange = 0.8f..0.9f
        override val pitchLinked = true
        
        val speed: Float get() = 0.85f
        val pitch: Float get() = 0.85f
    }
    
    /**
     * 🌊 Slowed + Reverb
     * Скорость: 0.7-0.8x, pitch связан, длинный реверб, бас в моно
     */
    data class SlowedReverb(
        val intensity: Float = 0.7f,
        val reverbAmount: Float = 0.3f
    ) : AudioEffect() {
        override val id = "slowed_reverb"
        override val name = "Slowed + Reverb"
        override val description = "Замедленный с эхом"
        override val icon = "🌊"
        override val color = 0xFF06B6D4L // Cyan
        override val speedRange = 0.7f..0.85f
        override val pitchLinked = true
        
        val speed: Float get() = 0.75f
        val pitch: Float get() = 0.75f
    }
    
    /**
     * ⚡ Hypercore
     * Скорость: 1.25-1.4x, pitch с формантами, усиление транзиентов, мультибэнд-компрессия
     */
    data class Hypercore(
        val intensity: Float = 0.7f
    ) : AudioEffect() {
        override val id = "hypercore"
        override val name = "Hypercore"
        override val description = "Энергичный, быстрый"
        override val icon = "⚡"
        override val color = 0xFFF59E0BL // Amber
        override val speedRange = 1.25f..1.4f
        override val pitchLinked = false // Pitch сохраняется (формант-коррекция)
        
        val speed: Float get() = 1.3f
        val pitch: Float get() = 1.0f // Pitch сохраняется
    }
    
    /**
     * 🌌 Phonk Mode
     * Саббас-синтез, soft clipping, EQ с провалом в середине
     */
    data class PhonkMode(
        val intensity: Float = 0.7f,
        val vinylNoise: Boolean = false
    ) : AudioEffect() {
        override val id = "phonk"
        override val name = "Phonk"
        override val description = "Глубокий бас, тёмный звук"
        override val icon = "🌌"
        override val color = 0xFFEF4444L // Red
        override val speedRange = 0.85f..1.0f
        override val pitchLinked = true
        
        val speed: Float get() = 0.9f
        val pitch: Float get() = 0.9f
    }
    
    /**
     * 🚀 Hardstyle Boost
     * Акцент на кик, гармонический бас, агрессивная loudness
     */
    data class HardstyleBoost(
        val intensity: Float = 0.7f
    ) : AudioEffect() {
        override val id = "hardstyle"
        override val name = "Hardstyle"
        override val description = "Мощный кик, агрессивный бас"
        override val icon = "🚀"
        override val color = 0xFFEC4899L // Pink
        override val speedRange = 1.0f..1.1f
        override val pitchLinked = false
        
        val speed: Float get() = 1.0f
        val pitch: Float get() = 1.0f
    }
    
    /**
     * Кастомный эффект с ручными настройками
     */
    data class Custom(
        val preampGain: Float? = null,
        val eqBands: List<Float>? = null,
        val bassAmount: Float? = null,
        val stereoWidth: Float? = null,
        val saturationAmount: Float? = null,
        val reverbMix: Float? = null,
        val customSpeed: Float = 1f,
        val customPitch: Float = 1f
    ) : AudioEffect() {
        override val id = "custom"
        override val name = "Custom"
        override val description = "Пользовательские настройки"
        override val icon = "🎛️"
        override val color = 0xFF10B981L // Emerald
        override val speedRange = 0.25f..3f
        override val pitchLinked = false
    }
    
    companion object {
        /**
         * Все доступные эффекты
         */
        val allEffects: List<AudioEffect> = listOf(
            None,
            Chillcore(),
            SlowedReverb(),
            Hypercore(),
            PhonkMode(),
            HardstyleBoost()
        )
        
        /**
         * Получить эффект по ID
         */
        fun fromId(id: String): AudioEffect {
            return when (id) {
                "none" -> None
                "chillcore" -> Chillcore()
                "slowed_reverb" -> SlowedReverb()
                "hypercore" -> Hypercore()
                "phonk" -> PhonkMode()
                "hardstyle" -> HardstyleBoost()
                else -> None
            }
        }
    }
}

/**
 * Параметры эффекта для UI
 */
data class EffectParams(
    val effect: AudioEffect,
    val intensity: Float = 0.7f,
    val speed: Float = 1f,
    val pitch: Float = 1f
) {
    companion object {
        val DEFAULT = EffectParams(AudioEffect.None)
    }
}
