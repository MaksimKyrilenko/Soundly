package com.example.soundly.player.audio

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.soundly.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер эффектов воспроизведения
 * Управляет speed/pitch параметрами как в osu!
 * Сохраняет настройки между сессиями и треками
 */
@Singleton
class PlaybackEffectManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _currentParams = MutableStateFlow(PlaybackParams.NORMAL)
    val currentParams: StateFlow<PlaybackParams> = _currentParams.asStateFlow()
    
    private val _currentEffect = MutableStateFlow(PlaybackEffect.NORMAL)
    val currentEffect: StateFlow<PlaybackEffect> = _currentEffect.asStateFlow()
    
    private val _preservePitch = MutableStateFlow(true)
    val preservePitch: StateFlow<Boolean> = _preservePitch.asStateFlow()
    
    private var player: ExoPlayer? = null
    private var isInitialized = false
    
    init {
        // Загружаем сохранённые настройки при старте
        scope.launch {
            loadSavedSettings()
        }
    }
    
    private suspend fun loadSavedSettings() {
        val speed = preferencesManager.playbackSpeed.first()
        val pitch = preferencesManager.playbackPitch.first()
        val preserve = preferencesManager.preservePitch.first()
        
        _preservePitch.value = preserve
        _currentParams.value = PlaybackParams.custom(speed, pitch, preserve)
        _currentEffect.value = determineEffect(speed, pitch, preserve)
        
        isInitialized = true
        
        // Применяем к плееру если он уже подключён
        applyCurrentParams()
        
        android.util.Log.d("PlaybackEffectManager", "Loaded settings: speed=$speed, pitch=$pitch, preserve=$preserve")
    }
    
    private fun determineEffect(speed: Float, pitch: Float, preserve: Boolean): PlaybackEffect {
        return when {
            speed == 1.0f && pitch == 1.0f -> PlaybackEffect.NORMAL
            speed == 1.5f && pitch == 1.5f && !preserve -> PlaybackEffect.NIGHTCORE
            speed == 0.75f && pitch == 0.75f && !preserve -> PlaybackEffect.DAYCORE
            speed == 1.5f && pitch == 1.0f && preserve -> PlaybackEffect.DOUBLE_TIME
            speed == 0.75f && pitch == 1.0f && preserve -> PlaybackEffect.HALF_TIME
            else -> PlaybackEffect.CUSTOM
        }
    }
    
    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        // Применяем сохранённые настройки к новому плееру
        applyCurrentParams()
    }
    
    fun detachPlayer() {
        player = null
    }
    
    /**
     * Применить предустановленный эффект
     */
    fun setEffect(effect: PlaybackEffect) {
        val params = when (effect) {
            PlaybackEffect.NORMAL -> {
                _preservePitch.value = true
                PlaybackParams.NORMAL
            }
            PlaybackEffect.DOUBLE_TIME -> {
                _preservePitch.value = true
                PlaybackParams.DOUBLE_TIME
            }
            PlaybackEffect.HALF_TIME -> {
                _preservePitch.value = true
                PlaybackParams.HALF_TIME
            }
            PlaybackEffect.NIGHTCORE -> {
                _preservePitch.value = false
                PlaybackParams.NIGHTCORE
            }
            PlaybackEffect.DAYCORE -> {
                _preservePitch.value = false
                PlaybackParams.DAYCORE
            }
            PlaybackEffect.CUSTOM -> _currentParams.value
        }
        
        _currentEffect.value = effect
        _currentParams.value = params
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Установить скорость (pitch меняется в зависимости от preservePitch)
     */
    fun setSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        val newPitch = if (_preservePitch.value) 1.0f else clampedSpeed
        
        val params = PlaybackParams.custom(clampedSpeed, newPitch, _preservePitch.value)
        _currentEffect.value = PlaybackEffect.CUSTOM
        _currentParams.value = params
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Установить pitch отдельно
     */
    fun setPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.25f, 3.0f)
        val params = PlaybackParams.custom(_currentParams.value.speed, clampedPitch, false)
        _preservePitch.value = false
        _currentEffect.value = PlaybackEffect.CUSTOM
        _currentParams.value = params
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Переключить режим сохранения pitch
     */
    fun setPreservePitch(preserve: Boolean) {
        _preservePitch.value = preserve
        val currentSpeed = _currentParams.value.speed
        val newPitch = if (preserve) 1.0f else currentSpeed
        
        val params = PlaybackParams.custom(currentSpeed, newPitch, preserve)
        _currentParams.value = params
        _currentEffect.value = determineEffect(currentSpeed, newPitch, preserve)
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Установить только скорость (pitch сохраняется) - Time Stretch
     */
    fun setSpeedOnly(speed: Float) {
        val params = PlaybackParams.speedOnly(speed)
        _preservePitch.value = true
        _currentEffect.value = PlaybackEffect.CUSTOM
        _currentParams.value = params
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Установить естественное изменение скорости (pitch меняется пропорционально)
     */
    fun setNaturalSpeed(speed: Float) {
        val params = PlaybackParams.naturalSpeed(speed)
        _preservePitch.value = false
        _currentEffect.value = PlaybackEffect.CUSTOM
        _currentParams.value = params
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Установить кастомные параметры
     */
    fun setCustomParams(speed: Float, pitch: Float, preservePitch: Boolean = true) {
        val params = PlaybackParams.custom(speed, pitch, preservePitch)
        _preservePitch.value = preservePitch
        _currentEffect.value = PlaybackEffect.CUSTOM
        _currentParams.value = params
        applyCurrentParams()
        saveSettings()
    }
    
    /**
     * Сбросить к нормальному воспроизведению
     */
    fun reset() {
        setEffect(PlaybackEffect.NORMAL)
    }
    
    /**
     * Сохранить настройки
     */
    private fun saveSettings() {
        scope.launch {
            val params = _currentParams.value
            preferencesManager.setPlaybackSettings(params.speed, params.pitch, _preservePitch.value)
            android.util.Log.d("PlaybackEffectManager", "Saved settings: speed=${params.speed}, pitch=${params.pitch}, preserve=${_preservePitch.value}")
        }
    }
    
    /**
     * Применить текущие параметры к плееру
     */
    private fun applyCurrentParams() {
        val params = _currentParams.value
        
        player?.let { exo ->
            val playbackParams = PlaybackParameters(params.speed, params.pitch)
            exo.playbackParameters = playbackParams
            
            android.util.Log.d("PlaybackEffectManager", 
                "Applied: effect=${_currentEffect.value}, speed=${params.speed}, pitch=${params.pitch}, preservePitch=${_preservePitch.value}")
        }
    }
    
    fun getSpeed(): Float = _currentParams.value.speed
    fun getPitch(): Float = _currentParams.value.pitch
    fun isTimeStretchActive(): Boolean = _preservePitch.value
    
    fun getEffectDescription(): String {
        val params = _currentParams.value
        return when (_currentEffect.value) {
            PlaybackEffect.NORMAL -> "Обычное воспроизведение"
            PlaybackEffect.DOUBLE_TIME -> "Double Time (1.5x, pitch сохранён)"
            PlaybackEffect.HALF_TIME -> "Half Time (0.75x, pitch сохранён)"
            PlaybackEffect.NIGHTCORE -> "Nightcore (1.5x + высокий тон)"
            PlaybackEffect.DAYCORE -> "Daycore (0.75x + низкий тон)"
            PlaybackEffect.CUSTOM -> {
                if (_preservePitch.value) {
                    "Скорость ${String.format("%.2f", params.speed)}x (pitch сохранён)"
                } else {
                    "Скорость ${String.format("%.2f", params.speed)}x, тон ${String.format("%.2f", params.pitch)}x"
                }
            }
        }
    }
}
