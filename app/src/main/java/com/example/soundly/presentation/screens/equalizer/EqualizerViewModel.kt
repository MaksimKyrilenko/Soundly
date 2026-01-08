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
    // Новые эффекты
    val reverb: ReverbSettings = ReverbSettings(),
    val compressor: CompressorSettings = CompressorSettings(),
    val noiseGate: NoiseGateSettings = NoiseGateSettings(),
    val deEsser: DeEsserSettings = DeEsserSettings(),
    val subBass: SubBassSettings = SubBassSettings(),
    // Пользовательские пресеты
    val userPresets: List<UserPreset> = emptyList(),
    val showSavePresetDialog: Boolean = false,
    val newPresetName: String = "",
    // Анализатор спектра
    val spectrumEnabled: Boolean = false,
    val spectrumData: FloatArray = FloatArray(32),
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
            // Новые эффекты
            PlaybackMode.CHILLCORE -> {
                playbackEffectManager.setCustomParams(0.85f, 0.85f, false)
                _uiState.value = _uiState.value.copy(
                    stereoWidth = 120f,
                    bassEnhancerAmount = 20f
                )
            }
            PlaybackMode.SLOWED_REVERB -> {
                playbackEffectManager.setCustomParams(0.75f, 0.75f, false)
                _uiState.value = _uiState.value.copy(
                    bassEnhancerAmount = 30f
                )
            }
            PlaybackMode.HYPERCORE -> {
                playbackEffectManager.setCustomParams(1.3f, 1.0f, true)
            }
            PlaybackMode.PHONK -> {
                playbackEffectManager.setCustomParams(0.9f, 0.9f, false)
                _uiState.value = _uiState.value.copy(
                    bassEnhancerAmount = 60f,
                    bassEnhancerMode = BassEnhancerMode.HARD,
                    // Phonk EQ: boost bass, cut mids
                    bands = listOf(6f, 5f, 3f, -2f, -3f, -2f, 1f, 2f, 1f, 0f)
                )
            }
            PlaybackMode.HARDSTYLE -> {
                playbackEffectManager.setCustomParams(1.0f, 1.0f, true)
                _uiState.value = _uiState.value.copy(
                    bassEnhancerAmount = 70f,
                    bassEnhancerMode = BassEnhancerMode.HARD,
                    bassEnhancerFrequency = 80,
                    // Hardstyle EQ: kick focus
                    bands = listOf(4f, 6f, 5f, 2f, 0f, 0f, 2f, 1f, 0f, 0f)
                )
            }
        }
        _uiState.value = _uiState.value.copy(playbackMode = mode)
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

    // ==================== ADVANCED EFFECTS ====================
    
    /**
     * Настройки реверберации
     */
    fun setReverb(settings: ReverbSettings) {
        _uiState.value = _uiState.value.copy(reverb = settings)
    }
    
    fun toggleReverb() {
        val current = _uiState.value.reverb
        _uiState.value = _uiState.value.copy(reverb = current.copy(enabled = !current.enabled))
    }
    
    fun setReverbRoomSize(size: Float) {
        val current = _uiState.value.reverb
        _uiState.value = _uiState.value.copy(reverb = current.copy(roomSize = size.coerceIn(0f, 1f)))
    }
    
    fun setReverbDecay(decay: Float) {
        val current = _uiState.value.reverb
        _uiState.value = _uiState.value.copy(reverb = current.copy(decay = decay.coerceIn(0f, 1f)))
    }
    
    fun setReverbWetDry(mix: Float) {
        val current = _uiState.value.reverb
        _uiState.value = _uiState.value.copy(reverb = current.copy(wetDryMix = mix.coerceIn(0f, 1f)))
    }
    
    /**
     * Настройки компрессора
     */
    fun setCompressor(settings: CompressorSettings) {
        _uiState.value = _uiState.value.copy(compressor = settings)
    }
    
    fun toggleCompressor() {
        val current = _uiState.value.compressor
        _uiState.value = _uiState.value.copy(compressor = current.copy(enabled = !current.enabled))
    }
    
    fun setCompressorThreshold(threshold: Float) {
        val current = _uiState.value.compressor
        _uiState.value = _uiState.value.copy(compressor = current.copy(threshold = threshold.coerceIn(-60f, 0f)))
    }
    
    fun setCompressorRatio(ratio: Float) {
        val current = _uiState.value.compressor
        _uiState.value = _uiState.value.copy(compressor = current.copy(ratio = ratio.coerceIn(1f, 20f)))
    }
    
    fun setCompressorAttack(attack: Float) {
        val current = _uiState.value.compressor
        _uiState.value = _uiState.value.copy(compressor = current.copy(attack = attack.coerceIn(0.1f, 100f)))
    }
    
    fun setCompressorRelease(release: Float) {
        val current = _uiState.value.compressor
        _uiState.value = _uiState.value.copy(compressor = current.copy(release = release.coerceIn(10f, 1000f)))
    }
    
    /**
     * Настройки Noise Gate
     */
    fun setNoiseGate(settings: NoiseGateSettings) {
        _uiState.value = _uiState.value.copy(noiseGate = settings)
    }
    
    fun toggleNoiseGate() {
        val current = _uiState.value.noiseGate
        _uiState.value = _uiState.value.copy(noiseGate = current.copy(enabled = !current.enabled))
    }
    
    fun setNoiseGateThreshold(threshold: Float) {
        val current = _uiState.value.noiseGate
        _uiState.value = _uiState.value.copy(noiseGate = current.copy(threshold = threshold.coerceIn(-80f, 0f)))
    }
    
    fun setNoiseGateAttack(attack: Float) {
        val current = _uiState.value.noiseGate
        _uiState.value = _uiState.value.copy(noiseGate = current.copy(attack = attack.coerceIn(0.1f, 50f)))
    }
    
    fun setNoiseGateRelease(release: Float) {
        val current = _uiState.value.noiseGate
        _uiState.value = _uiState.value.copy(noiseGate = current.copy(release = release.coerceIn(10f, 500f)))
    }
    
    /**
     * Настройки De-Esser
     */
    fun setDeEsser(settings: DeEsserSettings) {
        _uiState.value = _uiState.value.copy(deEsser = settings)
    }
    
    fun toggleDeEsser() {
        val current = _uiState.value.deEsser
        _uiState.value = _uiState.value.copy(deEsser = current.copy(enabled = !current.enabled))
    }
    
    fun setDeEsserFrequency(freq: Float) {
        val current = _uiState.value.deEsser
        _uiState.value = _uiState.value.copy(deEsser = current.copy(frequency = freq.coerceIn(4000f, 10000f)))
    }
    
    fun setDeEsserThreshold(threshold: Float) {
        val current = _uiState.value.deEsser
        _uiState.value = _uiState.value.copy(deEsser = current.copy(threshold = threshold.coerceIn(-40f, 0f)))
    }
    
    fun setDeEsserReduction(reduction: Float) {
        val current = _uiState.value.deEsser
        _uiState.value = _uiState.value.copy(deEsser = current.copy(reduction = reduction.coerceIn(0f, 12f)))
    }
    
    /**
     * Настройки Sub-Bass
     */
    fun setSubBass(settings: SubBassSettings) {
        _uiState.value = _uiState.value.copy(subBass = settings)
    }
    
    fun toggleSubBass() {
        val current = _uiState.value.subBass
        _uiState.value = _uiState.value.copy(subBass = current.copy(enabled = !current.enabled))
    }
    
    fun setSubBassAmount(amount: Float) {
        val current = _uiState.value.subBass
        _uiState.value = _uiState.value.copy(subBass = current.copy(amount = amount.coerceIn(0f, 100f)))
    }
    
    fun setSubBassFrequency(freq: Int) {
        val current = _uiState.value.subBass
        _uiState.value = _uiState.value.copy(subBass = current.copy(frequency = freq.coerceIn(40, 120)))
    }
    
    fun toggleSubHarmonics() {
        val current = _uiState.value.subBass
        _uiState.value = _uiState.value.copy(subBass = current.copy(subHarmonics = !current.subHarmonics))
    }
    
    fun setSubHarmonicsAmount(amount: Float) {
        val current = _uiState.value.subBass
        _uiState.value = _uiState.value.copy(subBass = current.copy(subAmount = amount.coerceIn(0f, 100f)))
    }
    
    // ==================== SPECTRUM ANALYZER ====================
    
    fun toggleSpectrum() {
        _uiState.value = _uiState.value.copy(spectrumEnabled = !_uiState.value.spectrumEnabled)
    }
    
    fun updateSpectrumData(data: FloatArray) {
        _uiState.value = _uiState.value.copy(spectrumData = data)
    }
    
    // ==================== USER PRESETS ====================
    
    fun showSavePresetDialog() {
        _uiState.value = _uiState.value.copy(showSavePresetDialog = true, newPresetName = "")
    }
    
    fun hideSavePresetDialog() {
        _uiState.value = _uiState.value.copy(showSavePresetDialog = false)
    }
    
    fun setNewPresetName(name: String) {
        _uiState.value = _uiState.value.copy(newPresetName = name)
    }
    
    fun saveUserPreset(name: String, trackId: String? = null, playlistId: String? = null) {
        val state = _uiState.value
        val preset = EqualizerPresetV2(
            id = "user_${System.currentTimeMillis()}",
            name = name,
            bands = state.bands,
            bassEnhancer = state.bassEnhancerAmount,
            bassEnhancerFrequency = state.bassEnhancerFrequency,
            bassEnhancerMode = state.bassEnhancerMode,
            stereoWidth = state.stereoWidth,
            loudnessEnabled = state.loudnessEnabled,
            reverb = state.reverb,
            compressor = state.compressor,
            noiseGate = state.noiseGate,
            deEsser = state.deEsser,
            subBass = state.subBass
        )
        
        val userPreset = UserPreset(
            id = preset.id,
            name = name,
            preset = preset,
            linkedTrackId = trackId,
            linkedPlaylistId = playlistId
        )
        
        val updatedPresets = _uiState.value.userPresets + userPreset
        _uiState.value = _uiState.value.copy(
            userPresets = updatedPresets,
            showSavePresetDialog = false
        )
        
        // Сохраняем в preferences
        viewModelScope.launch {
            saveUserPresetsToStorage(updatedPresets)
        }
    }
    
    fun loadUserPreset(preset: UserPreset) {
        selectPreset(preset.preset)
        _uiState.value = _uiState.value.copy(
            reverb = preset.preset.reverb,
            compressor = preset.preset.compressor,
            noiseGate = preset.preset.noiseGate,
            deEsser = preset.preset.deEsser,
            subBass = preset.preset.subBass
        )
    }
    
    fun deleteUserPreset(presetId: String) {
        val updatedPresets = _uiState.value.userPresets.filter { it.id != presetId }
        _uiState.value = _uiState.value.copy(userPresets = updatedPresets)
        
        viewModelScope.launch {
            saveUserPresetsToStorage(updatedPresets)
        }
    }
    
    private suspend fun saveUserPresetsToStorage(presets: List<UserPreset>) {
        // Сериализуем пресеты в JSON и сохраняем
        val json = presets.joinToString(";") { p ->
            "${p.id}|${p.name}|${p.preset.bands.joinToString(",")}"
        }
        preferencesManager.setUserPresets(json)
    }

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
        
        // Логируем настройки экспорта
        android.util.Log.d("EqualizerViewModel", "Export settings: speed=${settings.speed}, pitch=${settings.pitch}, preservePitch=${settings.preservePitch}, mode=${settings.playbackMode}")
        android.util.Log.d("EqualizerViewModel", "UI State: speed=${_uiState.value.playbackSpeed}, pitch=${_uiState.value.pitch}, preservePitch=${_uiState.value.preservePitch}")
        
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
