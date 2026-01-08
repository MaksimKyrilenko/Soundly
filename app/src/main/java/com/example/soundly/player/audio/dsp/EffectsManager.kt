package com.example.soundly.player.audio.dsp

import com.example.soundly.data.local.PreferencesManager
import com.example.soundly.player.audio.PlaybackEffectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер аудио эффектов
 * Управляет DSP пайплайном и синхронизирует с PlaybackEffectManager
 */
@Singleton
class EffectsManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val playbackEffectManager: PlaybackEffectManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val dspPipeline = DSPPipeline()
    
    private val _currentEffect = MutableStateFlow<AudioEffect>(AudioEffect.None)
    val currentEffect: StateFlow<AudioEffect> = _currentEffect.asStateFlow()
    
    private val _effectIntensity = MutableStateFlow(0.7f)
    val effectIntensity: StateFlow<Float> = _effectIntensity.asStateFlow()
    
    private val _isProcessingEnabled = MutableStateFlow(false)
    val isProcessingEnabled: StateFlow<Boolean> = _isProcessingEnabled.asStateFlow()
    
    // Данные для визуализации
    private val _currentLevel = MutableStateFlow(0f)
    val currentLevel: StateFlow<Float> = _currentLevel.asStateFlow()
    
    private val _fftData = MutableStateFlow(FloatArray(512))
    val fftData: StateFlow<FloatArray> = _fftData.asStateFlow()
    
    init {
        loadSavedEffect()
    }
    
    private fun loadSavedEffect() {
        scope.launch {
            // TODO: Load from preferences
        }
    }

    /**
     * Установить эффект
     */
    fun setEffect(effect: AudioEffect, intensity: Float = 0.7f) {
        _currentEffect.value = effect
        _effectIntensity.value = intensity
        _isProcessingEnabled.value = effect != AudioEffect.None
        
        // Настраиваем DSP пайплайн
        dspPipeline.setEffect(effect, intensity)
        
        // Синхронизируем speed/pitch с PlaybackEffectManager
        when (effect) {
            is AudioEffect.Chillcore -> {
                playbackEffectManager.setCustomParams(effect.speed, effect.pitch, false)
            }
            is AudioEffect.SlowedReverb -> {
                playbackEffectManager.setCustomParams(effect.speed, effect.pitch, false)
            }
            is AudioEffect.Hypercore -> {
                playbackEffectManager.setCustomParams(effect.speed, effect.pitch, true) // Pitch сохраняется
            }
            is AudioEffect.PhonkMode -> {
                playbackEffectManager.setCustomParams(effect.speed, effect.pitch, false)
            }
            is AudioEffect.HardstyleBoost -> {
                playbackEffectManager.setCustomParams(effect.speed, effect.pitch, true)
            }
            is AudioEffect.Custom -> {
                playbackEffectManager.setCustomParams(effect.customSpeed, effect.customPitch, false)
            }
            AudioEffect.None -> {
                playbackEffectManager.reset()
            }
        }
        
        saveEffect()
    }
    
    /**
     * Установить интенсивность текущего эффекта
     */
    fun setIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        _effectIntensity.value = clamped
        
        // Обновляем эффект с новой интенсивностью
        val effect = when (val current = _currentEffect.value) {
            is AudioEffect.Chillcore -> current.copy(intensity = clamped)
            is AudioEffect.SlowedReverb -> current.copy(intensity = clamped)
            is AudioEffect.Hypercore -> current.copy(intensity = clamped)
            is AudioEffect.PhonkMode -> current.copy(intensity = clamped)
            is AudioEffect.HardstyleBoost -> current.copy(intensity = clamped)
            else -> current
        }
        
        _currentEffect.value = effect
        dspPipeline.setEffect(effect, clamped)
        saveEffect()
    }
    
    /**
     * Сбросить эффект
     */
    fun resetEffect() {
        setEffect(AudioEffect.None)
    }
    
    /**
     * Обработать аудио буфер
     */
    fun processAudio(buffer: FloatArray, channelCount: Int = 2): FloatArray {
        if (!_isProcessingEnabled.value) return buffer
        
        val processed = dspPipeline.process(buffer, channelCount)
        
        // Обновляем данные для визуализации
        _currentLevel.value = dspPipeline.getCurrentLevel()
        _fftData.value = dspPipeline.getFFTData()
        
        return processed
    }
    
    /**
     * Установить sample rate
     */
    fun setSampleRate(sampleRate: Int) {
        dspPipeline.sampleRate = sampleRate
    }
    
    private fun saveEffect() {
        scope.launch {
            // TODO: Save to preferences
        }
    }
    
    /**
     * Получить все доступные эффекты
     */
    fun getAvailableEffects(): List<AudioEffect> = AudioEffect.allEffects
    
    /**
     * Получить параметры текущего эффекта для UI
     */
    fun getCurrentEffectParams(): EffectParams {
        val effect = _currentEffect.value
        val intensity = _effectIntensity.value
        
        val (speed, pitch) = when (effect) {
            is AudioEffect.Chillcore -> effect.speed to effect.pitch
            is AudioEffect.SlowedReverb -> effect.speed to effect.pitch
            is AudioEffect.Hypercore -> effect.speed to effect.pitch
            is AudioEffect.PhonkMode -> effect.speed to effect.pitch
            is AudioEffect.HardstyleBoost -> effect.speed to effect.pitch
            is AudioEffect.Custom -> effect.customSpeed to effect.customPitch
            AudioEffect.None -> 1f to 1f
        }
        
        return EffectParams(effect, intensity, speed, pitch)
    }
}
