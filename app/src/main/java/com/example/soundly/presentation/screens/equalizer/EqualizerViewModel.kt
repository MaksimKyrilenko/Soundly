package com.example.soundly.presentation.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.data.export.AudioExportService
import com.example.soundly.data.local.PreferencesManager
import com.example.soundly.domain.model.*
import com.example.soundly.domain.repository.TrackRepository
import com.example.soundly.player.PlayerController
import com.example.soundly.player.audio.PlaybackEffect
import com.example.soundly.player.audio.PlaybackEffectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние экспорта трека
 */
sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val progress: Int, val message: String) : ExportState()
    data class Success(val track: Track, val message: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

data class EqualizerUiState(
    val mode: EqualizerMode = EqualizerMode.SIMPLE,
    val isEnabled: Boolean = true,
    val currentPresetId: String = "flat",
    val bands: List<Float> = List(10) { 0f },
    val preamp: Float = 0f,
    val autoGainEnabled: Boolean = true,
    val bassEnhancerAmount: Float = 0f,
    val bassEnhancerFrequency: Int = 80,
    val bassEnhancerMode: BassEnhancerMode = BassEnhancerMode.SOFT,
    val stereoWidth: Float = 100f,
    val isMono: Boolean = false,
    val loudnessEnabled: Boolean = false,
    val balanceL: Float = 0f,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val playbackMode: PlaybackMode = PlaybackMode.NORMAL,
    val preservePitch: Boolean = true,
    val isComparing: Boolean = false,
    val presets: List<EqualizerPresetV2> = builtInPresetsV2,
    val isCalibrating: Boolean = false,
    val calibrationStep: Int = 0,
    val calibrationHeadphoneType: HeadphoneType = HeadphoneType.TWS,
    val calibrationBassLevel: Int = 0,
    val calibrationHighsLevel: Int = 0,
    // Состояние экспорта
    val exportState: ExportState = ExportState.Idle,
    val showExportDialog: Boolean = false,
    val exportCustomName: String = ""
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val playerController: PlayerController,
    private val playbackEffectManager: PlaybackEffectManager,
    private val audioExportService: AudioExportService,
    private val trackRepository: TrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        
        // Подписываемся на изменения параметров воспроизведения из PlaybackEffectManager
        viewModelScope.launch {
            playbackEffectManager.currentParams.collect { params ->
                _uiState.value = _uiState.value.copy(
                    playbackSpeed = params.speed,
                    pitch = params.pitch
                )
            }
        }
        
        viewModelScope.launch {
            playbackEffectManager.preservePitch.collect { preserve ->
                _uiState.value = _uiState.value.copy(preservePitch = preserve)
            }
        }
        
        viewModelScope.launch {
            playbackEffectManager.currentEffect.collect { effect ->
                _uiState.value = _uiState.value.copy(playbackMode = mapEffectToMode(effect))
            }
        }
    }
    
    private fun mapEffectToMode(effect: PlaybackEffect): PlaybackMode {
        return when (effect) {
            PlaybackEffect.NORMAL -> PlaybackMode.NORMAL
            PlaybackEffect.NIGHTCORE -> PlaybackMode.NIGHTCORE
            PlaybackEffect.DAYCORE -> PlaybackMode.DAYCORE
            PlaybackEffect.DOUBLE_TIME -> PlaybackMode.DOUBLE_TIME
            PlaybackEffect.HALF_TIME -> PlaybackMode.HALF_TIME
            PlaybackEffect.CUSTOM -> PlaybackMode.SPEED_ONLY
        }
    }

    private fun loadSettings() {
        viewModelScope.launch { preferencesManager.equalizerEnabled.collect { _uiState.value = _uiState.value.copy(isEnabled = it) } }
        viewModelScope.launch { preferencesManager.equalizerPreset.collect { _uiState.value = _uiState.value.copy(currentPresetId = it) } }
        viewModelScope.launch { 
            preferencesManager.equalizerBands.collect { bandsString ->
                val bands = bandsString.split(",").mapNotNull { it.toFloatOrNull() }
                if (bands.size == 10) _uiState.value = _uiState.value.copy(bands = bands)
            }
        }
        viewModelScope.launch { preferencesManager.bassBoost.collect { _uiState.value = _uiState.value.copy(bassEnhancerAmount = it.toFloat() / 10f) } }
        viewModelScope.launch { preferencesManager.virtualizer.collect { _uiState.value = _uiState.value.copy(stereoWidth = 100f + (it.toFloat() / 20f)) } }
    }

    fun toggleMode() { _uiState.value = _uiState.value.copy(mode = if (_uiState.value.mode == EqualizerMode.SIMPLE) EqualizerMode.PRO else EqualizerMode.SIMPLE) }
    fun toggleEnabled() { 
        val new = !_uiState.value.isEnabled
        _uiState.value = _uiState.value.copy(isEnabled = new)
        viewModelScope.launch { preferencesManager.setEqualizerEnabled(new) }
    }

    fun selectPreset(preset: EqualizerPresetV2) {
        _uiState.value = _uiState.value.copy(
            currentPresetId = preset.id, bands = preset.bands, bassEnhancerAmount = preset.bassEnhancer,
            bassEnhancerFrequency = preset.bassEnhancerFrequency, bassEnhancerMode = preset.bassEnhancerMode,
            stereoWidth = preset.stereoWidth, loudnessEnabled = preset.loudnessEnabled
        )
    }

    fun setBandValue(index: Int, value: Float) {
        val newBands = _uiState.value.bands.toMutableList()
        newBands[index] = value.coerceIn(-12f, 12f)
        _uiState.value = _uiState.value.copy(bands = newBands, currentPresetId = "custom")
    }
    fun resetBand(index: Int) { setBandValue(index, 0f) }
    fun setPreamp(value: Float) { _uiState.value = _uiState.value.copy(preamp = value.coerceIn(-6f, 6f)) }
    fun toggleAutoGain() { _uiState.value = _uiState.value.copy(autoGainEnabled = !_uiState.value.autoGainEnabled) }

    fun setBassEnhancerAmount(amount: Float) { _uiState.value = _uiState.value.copy(bassEnhancerAmount = amount.coerceIn(0f, 100f)) }
    fun setBassEnhancerFrequency(freq: Int) { _uiState.value = _uiState.value.copy(bassEnhancerFrequency = freq) }
    fun setBassEnhancerMode(mode: BassEnhancerMode) { _uiState.value = _uiState.value.copy(bassEnhancerMode = mode) }

    fun setStereoWidth(width: Float) { _uiState.value = _uiState.value.copy(stereoWidth = width.coerceIn(0f, 150f)) }
    fun toggleMono() { _uiState.value = _uiState.value.copy(isMono = !_uiState.value.isMono) }
    fun toggleLoudness() { _uiState.value = _uiState.value.copy(loudnessEnabled = !_uiState.value.loudnessEnabled) }
    fun setBalance(value: Float) { _uiState.value = _uiState.value.copy(balanceL = value.coerceIn(-100f, 100f)) }

    fun setPlaybackSpeed(speed: Float) { playbackEffectManager.setSpeed(speed) }

    /**
     * Устанавливает режим воспроизведения
     */
    fun setPlaybackMode(mode: PlaybackMode) {
        when (mode) {
            PlaybackMode.NIGHTCORE -> playbackEffectManager.setEffect(PlaybackEffect.NIGHTCORE)
            PlaybackMode.DAYCORE -> playbackEffectManager.setEffect(PlaybackEffect.DAYCORE)
            PlaybackMode.NORMAL -> playbackEffectManager.setEffect(PlaybackEffect.NORMAL)
            PlaybackMode.DOUBLE_TIME -> playbackEffectManager.setEffect(PlaybackEffect.DOUBLE_TIME)
            PlaybackMode.HALF_TIME -> playbackEffectManager.setEffect(PlaybackEffect.HALF_TIME)
            PlaybackMode.SPEED_ONLY -> {
                // Переключаемся в кастомный режим с текущими значениями
                playbackEffectManager.setSpeed(_uiState.value.playbackSpeed)
            }
        }
    }

    /**
     * Устанавливает скорость
     */
    fun setSpeed(speed: Float) {
        playbackEffectManager.setSpeed(speed)
    }

    /**
     * Устанавливает питч отдельно
     */
    fun setPitch(pitch: Float) {
        playbackEffectManager.setPitch(pitch)
    }

    /**
     * Переключает режим сохранения питча
     */
    fun setPreservePitch(preserve: Boolean) {
        playbackEffectManager.setPreservePitch(preserve)
    }

    /**
     * Сбрасывает настройки воспроизведения
     */
    fun resetPlayback() {
        playbackEffectManager.reset()
    }

    /**
     * Устанавливает скорость в режиме SPEED_ONLY (без изменения питча)
     */
    fun setSpeedOnly(speed: Float) {
        playbackEffectManager.setSpeedOnly(speed)
    }

    /**
     * Устанавливает кастомные параметры скорости и питча
     */
    fun setCustomPlaybackParameters(speed: Float, pitch: Float) {
        playbackEffectManager.setCustomParams(speed, pitch, preservePitch = false)
    }

    fun startCompare() { _uiState.value = _uiState.value.copy(isComparing = true) }
    fun endCompare() { _uiState.value = _uiState.value.copy(isComparing = false) }

    fun startCalibration() { _uiState.value = _uiState.value.copy(isCalibrating = true, calibrationStep = 0) }
    fun setCalibrationHeadphoneType(type: HeadphoneType) { _uiState.value = _uiState.value.copy(calibrationHeadphoneType = type, calibrationStep = 1) }
    fun setCalibrationBassLevel(level: Int) { _uiState.value = _uiState.value.copy(calibrationBassLevel = level, calibrationStep = 2) }
    fun setCalibrationHighsLevel(level: Int) { _uiState.value = _uiState.value.copy(calibrationHighsLevel = level, calibrationStep = 3) }
    fun setCalibrationVolumeLevel(level: Int) {
        val state = _uiState.value
        val (bands, bassEnhancer, bassFreq) = generateCalibration(state.calibrationHeadphoneType, state.calibrationBassLevel, state.calibrationHighsLevel, level)
        _uiState.value = state.copy(isCalibrating = false, bands = bands, bassEnhancerAmount = bassEnhancer, bassEnhancerFrequency = bassFreq, currentPresetId = "calibrated", loudnessEnabled = level == -1)
    }
    fun cancelCalibration() { _uiState.value = _uiState.value.copy(isCalibrating = false, calibrationStep = 0) }

    fun resetToFlat() { selectPreset(builtInPresetsV2.first { it.id == "flat" }) }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            preferencesManager.setEqualizerPreset(state.currentPresetId)
            preferencesManager.setEqualizerBands(state.bands.joinToString(","))
            preferencesManager.setBassBoost((state.bassEnhancerAmount * 10).toInt())
            preferencesManager.setVirtualizer(((state.stereoWidth - 100f) * 20).toInt())
            // Настройки speed/pitch сохраняются автоматически в PlaybackEffectManager
        }
    }
    
    // ==================== EXPORT FUNCTIONS ====================
    
    /**
     * Получает текущий трек из плеера
     */
    fun getCurrentTrack(): Track? {
        return playerController.playerState.value.currentTrack
    }
    
    /**
     * Показывает диалог экспорта
     */
    fun showExportDialog() {
        val currentTrack = getCurrentTrack()
        if (currentTrack == null) {
            _uiState.value = _uiState.value.copy(
                exportState = ExportState.Error("Нет воспроизводимого трека")
            )
            return
        }
        
        val settings = getCurrentExportSettings()
        if (!settings.hasChanges()) {
            _uiState.value = _uiState.value.copy(
                exportState = ExportState.Error("Нет изменений для экспорта")
            )
            return
        }
        
        val suggestedName = "${currentTrack.title} (${settings.generateNameSuffix()})"
        _uiState.value = _uiState.value.copy(
            showExportDialog = true,
            exportCustomName = suggestedName
        )
    }
    
    /**
     * Скрывает диалог экспорта
     */
    fun hideExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = false)
    }
    
    /**
     * Обновляет кастомное название для экспорта
     */
    fun setExportCustomName(name: String) {
        _uiState.value = _uiState.value.copy(exportCustomName = name)
    }
    
    /**
     * Сбрасывает состояние экспорта
     */
    fun resetExportState() {
        _uiState.value = _uiState.value.copy(exportState = ExportState.Idle)
    }
    
    /**
     * Получает текущие настройки для экспорта
     */
    fun getCurrentExportSettings(): AudioExportSettings {
        val state = _uiState.value
        return AudioExportSettings(
            speed = state.playbackSpeed,
            pitch = state.pitch,
            preservePitch = state.preservePitch,
            bands = state.bands,
            bassBoost = state.bassEnhancerAmount,
            stereoWidth = state.stereoWidth,
            isMono = state.isMono,
            preamp = state.preamp,
            playbackMode = state.playbackMode
        )
    }
    
    /**
     * Экспортирует текущий трек с применёнными настройками
     */
    fun exportCurrentTrack(customName: String? = null) {
        val currentTrack = getCurrentTrack()
        if (currentTrack == null) {
            _uiState.value = _uiState.value.copy(
                exportState = ExportState.Error("Нет воспроизводимого трека"),
                showExportDialog = false
            )
            return
        }
        
        val settings = getCurrentExportSettings()
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showExportDialog = false,
                exportState = ExportState.Exporting(0, "Начинаем экспорт...")
            )
            
            val result = audioExportService.exportTrack(
                sourceTrack = currentTrack,
                settings = settings,
                customName = customName?.takeIf { it.isNotBlank() }
            ) { progress ->
                _uiState.value = _uiState.value.copy(
                    exportState = ExportState.Exporting(progress.percent, progress.message)
                )
            }
            
            when (result) {
                is AudioExportService.ExportResult.Success -> {
                    // Добавляем новый трек в библиотеку
                    trackRepository.insertTrack(result.track)
                    
                    _uiState.value = _uiState.value.copy(
                        exportState = ExportState.Success(
                            track = result.track,
                            message = "Трек \"${result.track.title}\" успешно создан!"
                        )
                    )
                }
                is AudioExportService.ExportResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        exportState = ExportState.Error(result.message)
                    )
                }
                is AudioExportService.ExportResult.Progress -> {
                    // Уже обрабатывается в callback
                }
            }
        }
    }
    
    /**
     * Проверяет, можно ли экспортировать (есть трек и есть изменения)
     */
    fun canExport(): Boolean {
        val currentTrack = getCurrentTrack()
        val settings = getCurrentExportSettings()
        return currentTrack != null && settings.hasChanges()
    }
}
