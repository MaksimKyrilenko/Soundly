package com.example.soundly.presentation.screens.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.player.PlayerController
import com.example.soundly.player.audio.dsp.AudioEffect
import com.example.soundly.player.audio.dsp.EffectsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EffectsUiState(
    val currentEffect: AudioEffect = AudioEffect.None,
    val intensity: Float = 0.7f,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val bassLevel: Float = 0f,
    val currentLevel: Float = 0f,
    val isPlaying: Boolean = false,
    val availableEffects: List<AudioEffect> = AudioEffect.allEffects
)

@HiltViewModel
class EffectsViewModel @Inject constructor(
    private val effectsManager: EffectsManager,
    private val playerController: PlayerController
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EffectsUiState())
    val uiState: StateFlow<EffectsUiState> = _uiState.asStateFlow()
    
    init {
        // Подписываемся на состояние эффектов
        viewModelScope.launch {
            effectsManager.currentEffect.collect { effect ->
                _uiState.value = _uiState.value.copy(currentEffect = effect)
                updateSpeedPitchFromEffect(effect)
            }
        }
        
        viewModelScope.launch {
            effectsManager.effectIntensity.collect { intensity ->
                _uiState.value = _uiState.value.copy(intensity = intensity)
            }
        }
        
        viewModelScope.launch {
            effectsManager.currentLevel.collect { level ->
                _uiState.value = _uiState.value.copy(currentLevel = level)
            }
        }
        
        // Подписываемся на состояние плеера
        viewModelScope.launch {
            playerController.playerState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isPlaying = state.isPlaying,
                    speed = state.playbackSpeed,
                    pitch = state.pitch
                )
            }
        }
    }
    
    private fun updateSpeedPitchFromEffect(effect: AudioEffect) {
        val (speed, pitch) = when (effect) {
            is AudioEffect.Chillcore -> effect.speed to effect.pitch
            is AudioEffect.SlowedReverb -> effect.speed to effect.pitch
            is AudioEffect.Hypercore -> effect.speed to effect.pitch
            is AudioEffect.PhonkMode -> effect.speed to effect.pitch
            is AudioEffect.HardstyleBoost -> effect.speed to effect.pitch
            is AudioEffect.Custom -> effect.customSpeed to effect.customPitch
            AudioEffect.None -> 1f to 1f
        }
        _uiState.value = _uiState.value.copy(speed = speed, pitch = pitch)
    }
    
    fun setEffect(effect: AudioEffect) {
        effectsManager.setEffect(effect, _uiState.value.intensity)
    }
    
    fun setIntensity(intensity: Float) {
        effectsManager.setIntensity(intensity)
    }
    
    fun setSpeed(speed: Float) {
        val currentEffect = _uiState.value.currentEffect
        if (currentEffect is AudioEffect.Custom || currentEffect == AudioEffect.None) {
            // Для кастомного эффекта обновляем напрямую
            val newEffect = AudioEffect.Custom(
                customSpeed = speed,
                customPitch = if (currentEffect is AudioEffect.Custom) currentEffect.customPitch else _uiState.value.pitch
            )
            effectsManager.setEffect(newEffect, _uiState.value.intensity)
        }
        _uiState.value = _uiState.value.copy(speed = speed)
    }
    
    fun setPitch(pitch: Float) {
        val currentEffect = _uiState.value.currentEffect
        if (currentEffect is AudioEffect.Custom || currentEffect == AudioEffect.None) {
            val newEffect = AudioEffect.Custom(
                customSpeed = if (currentEffect is AudioEffect.Custom) currentEffect.customSpeed else _uiState.value.speed,
                customPitch = pitch
            )
            effectsManager.setEffect(newEffect, _uiState.value.intensity)
        }
        _uiState.value = _uiState.value.copy(pitch = pitch)
    }
    
    fun resetEffect() {
        effectsManager.resetEffect()
    }
}
