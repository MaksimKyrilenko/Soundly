package com.example.soundly.player.audio.dsp

import kotlin.math.*

/**
 * Модульный DSP-пайплайн для обработки аудио в реальном времени
 * 
 * Порядок обработки:
 * Вход → Preamp → Time/Pitch → EQ → Bass Engine → Compressor → Stereo → Saturation → Limiter → Выход
 */
class DSPPipeline {
    
    // Модули пайплайна
    private val preamp = PreampModule()
    private val equalizer = EqualizerModule()
    private val bassEngine = BassEngineModule()
    private val compressor = CompressorModule()
    private val stereoProcessor = StereoProcessorModule()
    private val saturation = SaturationModule()
    private val limiter = LimiterModule()
    private val reverb = ReverbModule()
    private val dynamicEQ = DynamicEQModule()
    private val loudnessCompensation = LoudnessCompensationModule()
    
    // Текущий эффект
    private var currentEffect: AudioEffect = AudioEffect.None
    private var effectIntensity: Float = 1.0f
    
    // Параметры
    var sampleRate: Int = 44100
        set(value) {
            field = value
            initializeModules()
        }
    
    init {
        initializeModules()
    }
    
    private fun initializeModules() {
        equalizer.sampleRate = sampleRate
        bassEngine.sampleRate = sampleRate
        compressor.sampleRate = sampleRate
        reverb.sampleRate = sampleRate
        dynamicEQ.sampleRate = sampleRate
        limiter.sampleRate = sampleRate
    }
    
    /**
     * Применить эффект с заданной интенсивностью
     */
    fun setEffect(effect: AudioEffect, intensity: Float = 1.0f) {
        currentEffect = effect
        effectIntensity = intensity.coerceIn(0f, 1f)
        configureForEffect(effect, intensity)
    }
    
    /**
     * Конфигурация модулей под конкретный эффект
     */
    private fun configureForEffect(effect: AudioEffect, intensity: Float) {
        // Сброс всех модулей
        resetModules()
        
        when (effect) {
            is AudioEffect.Chillcore -> configureChillcore(intensity)
            is AudioEffect.SlowedReverb -> configureSlowedReverb(intensity)
            is AudioEffect.Hypercore -> configureHypercore(intensity)
            is AudioEffect.PhonkMode -> configurePhonkMode(intensity)
            is AudioEffect.HardstyleBoost -> configureHardstyleBoost(intensity)
            is AudioEffect.Custom -> configureCustom(effect)
            AudioEffect.None -> { /* Всё выключено */ }
        }
    }
    
    private fun resetModules() {
        preamp.reset()
        equalizer.reset()
        bassEngine.reset()
        compressor.reset()
        stereoProcessor.reset()
        saturation.reset()
        reverb.reset()
        limiter.reset()
    }
    
    /**
     * 🎧 Chillcore
     * Скорость: ~0.85x, лёгкое понижение тона, тёплый low-pass, мягкое стерео, tape-сатурация
     */
    private fun configureChillcore(intensity: Float) {
        preamp.apply {
            enabled = true
            gainDb = -2f * intensity // Headroom
        }
        
        equalizer.apply {
            enabled = true
            // Тёплый low-pass: срезаем высокие, поднимаем низкие
            setBand(0, 2f * intensity)   // 31 Hz
            setBand(1, 1.5f * intensity) // 62 Hz
            setBand(2, 1f * intensity)   // 125 Hz
            setBand(7, -2f * intensity)  // 4k Hz
            setBand(8, -4f * intensity)  // 8k Hz
            setBand(9, -6f * intensity)  // 16k Hz
        }
        
        stereoProcessor.apply {
            enabled = true
            width = 110f + (20f * intensity) // Мягкое расширение
        }
        
        saturation.apply {
            enabled = true
            type = SaturationType.TAPE
            amount = 15f * intensity
            warmth = 0.6f * intensity
        }
        
        limiter.enabled = true
    }
    
    /**
     * 🌊 Slowed + Reverb
     * Скорость: 0.7-0.8x, pitch связан, длинный реверб, бас в моно
     */
    private fun configureSlowedReverb(intensity: Float) {
        preamp.apply {
            enabled = true
            gainDb = -3f * intensity
        }
        
        reverb.apply {
            enabled = true
            roomSize = 0.7f + (0.25f * intensity)
            damping = 0.3f
            wetMix = 0.25f * intensity
            dryMix = 1f - (0.1f * intensity)
        }
        
        bassEngine.apply {
            enabled = true
            monoBelow = 120 // Бас в моно
        }
        
        equalizer.apply {
            enabled = true
            setBand(0, 1f * intensity)
            setBand(1, 0.5f * intensity)
        }
        
        limiter.enabled = true
    }
    
    /**
     * ⚡ Hypercore
     * Скорость: 1.25-1.4x, pitch с формантами, усиление транзиентов, мультибэнд-компрессия
     */
    private fun configureHypercore(intensity: Float) {
        preamp.apply {
            enabled = true
            gainDb = -4f * intensity // Больше headroom для компрессии
        }
        
        compressor.apply {
            enabled = true
            threshold = -12f
            ratio = 4f + (2f * intensity)
            attack = 5f  // Быстрая атака для транзиентов
            release = 50f
            makeupGain = 3f * intensity
        }
        
        equalizer.apply {
            enabled = true
            // Усиление presence и clarity
            setBand(5, 2f * intensity)  // 1k Hz
            setBand(6, 3f * intensity)  // 2k Hz
            setBand(7, 2f * intensity)  // 4k Hz
        }
        
        dynamicEQ.apply {
            enabled = true
            // Антисвист: подавление резонансов 5-9 kHz
            targetFrequency = 7000f
            bandwidth = 4000f
            threshold = -6f
            reduction = -4f * intensity
        }
        
        limiter.enabled = true
    }
    
    /**
     * 🌌 Phonk Mode
     * Саббас-синтез, soft clipping, EQ с провалом в середине, виниловый шум
     */
    private fun configurePhonkMode(intensity: Float) {
        preamp.apply {
            enabled = true
            gainDb = -3f * intensity
        }
        
        bassEngine.apply {
            enabled = true
            subHarmonicsAmount = 40f * intensity
            saturationAmount = 30f * intensity
            saturationMode = BassEngineSaturationMode.TUBE
        }
        
        equalizer.apply {
            enabled = true
            // Провал в середине (scooped mids)
            setBand(0, 4f * intensity)   // 31 Hz - саббас
            setBand(1, 3f * intensity)   // 62 Hz
            setBand(2, 2f * intensity)   // 125 Hz
            setBand(3, -2f * intensity)  // 250 Hz - провал
            setBand(4, -3f * intensity)  // 500 Hz - провал
            setBand(5, -2f * intensity)  // 1k Hz - провал
            setBand(6, 1f * intensity)   // 2k Hz
            setBand(7, 2f * intensity)   // 4k Hz
            setBand(8, 1f * intensity)   // 8k Hz
        }
        
        saturation.apply {
            enabled = true
            type = SaturationType.SOFT_CLIP
            amount = 25f * intensity
        }
        
        limiter.enabled = true
    }
    
    /**
     * 🚀 Hardstyle Boost
     * Акцент на кик (60-120 Гц), гармонический бас, бас в моно, агрессивная loudness
     */
    private fun configureHardstyleBoost(intensity: Float) {
        preamp.apply {
            enabled = true
            gainDb = -6f * intensity // Много headroom
        }
        
        bassEngine.apply {
            enabled = true
            kickBoostFrequency = 90 // Центр кика
            kickBoostAmount = 6f * intensity
            harmonicsAmount = 30f * intensity
            monoBelow = 150 // Бас в моно
        }
        
        equalizer.apply {
            enabled = true
            setBand(1, 4f * intensity)   // 62 Hz - кик
            setBand(2, 5f * intensity)   // 125 Hz - кик
            setBand(3, 2f * intensity)   // 250 Hz
            setBand(6, 2f * intensity)   // 2k Hz - attack
            setBand(7, 1f * intensity)   // 4k Hz
        }
        
        compressor.apply {
            enabled = true
            threshold = -8f
            ratio = 6f + (2f * intensity)
            attack = 1f  // Очень быстрая атака
            release = 30f
            makeupGain = 4f * intensity
        }
        
        loudnessCompensation.apply {
            enabled = true
            targetLoudness = -8f // Агрессивная громкость
        }
        
        limiter.apply {
            enabled = true
            ceiling = -0.3f
        }
    }
    
    private fun configureCustom(effect: AudioEffect.Custom) {
        effect.preampGain?.let { preamp.gainDb = it; preamp.enabled = true }
        effect.eqBands?.let { bands ->
            equalizer.enabled = true
            bands.forEachIndexed { i, v -> equalizer.setBand(i, v) }
        }
        effect.bassAmount?.let { bassEngine.subHarmonicsAmount = it; bassEngine.enabled = true }
        effect.stereoWidth?.let { stereoProcessor.width = it; stereoProcessor.enabled = true }
        effect.saturationAmount?.let { saturation.amount = it; saturation.enabled = true }
        effect.reverbMix?.let { reverb.wetMix = it; reverb.enabled = true }
        limiter.enabled = true
    }
    
    /**
     * Обработка стерео буфера (interleaved: L, R, L, R, ...)
     */
    fun process(buffer: FloatArray, channelCount: Int = 2): FloatArray {
        if (currentEffect == AudioEffect.None) return buffer
        
        var processed = buffer.copyOf()
        
        // 1. Preamp + Headroom
        if (preamp.enabled) {
            processed = preamp.process(processed)
        }
        
        // 2. Эквалайзер
        if (equalizer.enabled) {
            processed = equalizer.process(processed, channelCount)
        }
        
        // 3. Dynamic EQ (антисвист)
        if (dynamicEQ.enabled) {
            processed = dynamicEQ.process(processed, channelCount)
        }
        
        // 4. Bass Engine
        if (bassEngine.enabled) {
            processed = bassEngine.process(processed, channelCount)
        }
        
        // 5. Компрессор
        if (compressor.enabled) {
            processed = compressor.process(processed)
        }
        
        // 6. Стерео обработка
        if (stereoProcessor.enabled && channelCount == 2) {
            processed = stereoProcessor.process(processed)
        }
        
        // 7. Реверб
        if (reverb.enabled) {
            processed = reverb.process(processed, channelCount)
        }
        
        // 8. Сатурация
        if (saturation.enabled) {
            processed = saturation.process(processed)
        }
        
        // 9. Loudness Compensation
        if (loudnessCompensation.enabled) {
            processed = loudnessCompensation.process(processed)
        }
        
        // 10. Лимитер (всегда последний)
        if (limiter.enabled) {
            processed = limiter.process(processed)
        }
        
        return processed
    }
    
    /**
     * Получить текущий уровень громкости (для визуализации)
     */
    fun getCurrentLevel(): Float = limiter.currentLevel
    
    /**
     * Получить данные FFT для визуализации
     */
    fun getFFTData(): FloatArray = equalizer.getFFTData()
}
