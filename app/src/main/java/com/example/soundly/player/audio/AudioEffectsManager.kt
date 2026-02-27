package com.example.soundly.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import com.example.soundly.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Менеджер аудио эффектов - управляет реальными Android AudioFX
 * Equalizer, BassBoost, Virtualizer, PresetReverb
 */
@Singleton
class AudioEffectsManager @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "AudioEffectsManager"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var visualizer: Visualizer? = null
    
    private var audioSessionId: Int = 0
    
    // Audio visualization data
    private val _bassLevel = MutableStateFlow(0f)
    val bassLevel: StateFlow<Float> = _bassLevel.asStateFlow()
    
    private val _beatDetected = MutableStateFlow(false)
    val beatDetected: StateFlow<Boolean> = _beatDetected.asStateFlow()
    
    private val _waveformData = MutableStateFlow(ByteArray(0))
    val waveformData: StateFlow<ByteArray> = _waveformData.asStateFlow()
    
    private var lastBeatTime = 0L
    private val beatThreshold = 1.3f // Порог для определения бита
    private val beatCooldown = 300L // Минимальное время между битами (мс)
    
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _bands = MutableStateFlow(List(10) { 0f })
    val bands: StateFlow<List<Float>> = _bands.asStateFlow()
    
    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    private val _reverbEnabled = MutableStateFlow(false)
    val reverbEnabled: StateFlow<Boolean> = _reverbEnabled.asStateFlow()
    
    private val _reverbPreset = MutableStateFlow<Short>(PresetReverb.PRESET_NONE)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()
    
    private val _loudnessEnabled = MutableStateFlow(false)
    val loudnessEnabled: StateFlow<Boolean> = _loudnessEnabled.asStateFlow()
    
    private val _loudnessGain = MutableStateFlow(0)
    val loudnessGain: StateFlow<Int> = _loudnessGain.asStateFlow()
    
    private val _preampGain = MutableStateFlow(0f)
    val preampGain: StateFlow<Float> = _preampGain.asStateFlow()
    
    /**
     * Инициализация эффектов с audio session ID от ExoPlayer
     */
    fun initialize(sessionId: Int) {
        if (sessionId == 0) {
            Log.e(TAG, "Invalid audio session ID: 0")
            return
        }
        
        if (sessionId == audioSessionId && equalizer != null) {
            Log.d(TAG, "Already initialized with session $sessionId")
            return
        }
        
        release()
        audioSessionId = sessionId
        
        try {
            // Создаём эквалайзер
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
            }
            Log.d(TAG, "Equalizer created: ${equalizer?.numberOfBands} bands, range ${equalizer?.bandLevelRange?.get(0)} to ${equalizer?.bandLevelRange?.get(1)}")
            
            // Создаём Bass Boost
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = true
            }
            Log.d(TAG, "BassBoost created, strength range supported: ${bassBoost?.strengthSupported}")
            
            // Создаём Virtualizer
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = true
            }
            Log.d(TAG, "Virtualizer created, strength supported: ${virtualizer?.strengthSupported}")
            
            // Создаём Reverb
            try {
                presetReverb = PresetReverb(0, sessionId).apply {
                    enabled = false
                }
                Log.d(TAG, "PresetReverb created")
            } catch (e: Exception) {
                Log.w(TAG, "PresetReverb not supported: ${e.message}")
            }
            
            // Создаём LoudnessEnhancer (API 19+)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                        enabled = false
                    }
                    Log.d(TAG, "LoudnessEnhancer created")
                }
            } catch (e: Exception) {
                Log.w(TAG, "LoudnessEnhancer not supported: ${e.message}")
            }
            
            // Создаём Visualizer для анализа аудио
            try {
                visualizer = Visualizer(sessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1] // Максимальный размер
                    Log.d(TAG, "Visualizer capture size: $captureSize, range: ${Visualizer.getCaptureSizeRange().contentToString()}")
                    
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { 
                                _waveformData.value = it
                                analyzeBassAndBeat(it)
                            }
                        }
                        
                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            // FFT данные можно использовать для более точного анализа
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    enabled = true
                    Log.d(TAG, "Visualizer enabled successfully")
                }
                Log.d(TAG, "Visualizer created for audio analysis")
            } catch (e: Exception) {
                Log.e(TAG, "Visualizer not supported: ${e.message}", e)
            }
            
            // Загружаем сохранённые настройки
            loadSavedSettings()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio effects", e)
        }
    }
    
    private fun loadSavedSettings() {
        scope.launch {
            try {
                val enabled = preferencesManager.equalizerEnabled.first()
                val bandsString = preferencesManager.equalizerBands.first()
                val bassBoostValue = preferencesManager.bassBoost.first()
                val virtualizerValue = preferencesManager.virtualizer.first()
                
                _isEnabled.value = enabled
                
                // Парсим полосы эквалайзера
                val bandsList = bandsString.split(",").mapNotNull { it.toFloatOrNull() }
                if (bandsList.size == 10) {
                    _bands.value = bandsList
                    applyEqualizerBands(bandsList)
                }
                
                // Применяем Bass Boost
                setBassBoost(bassBoostValue)
                
                // Применяем Virtualizer
                setVirtualizer(virtualizerValue)
                
                Log.d(TAG, "Loaded settings: enabled=$enabled, bands=$bandsList, bass=$bassBoostValue, virtualizer=$virtualizerValue")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settings", e)
            }
        }
    }
    
    /**
     * Включить/выключить эквалайзер
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        
        scope.launch {
            preferencesManager.setEqualizerEnabled(enabled)
        }
        Log.d(TAG, "Effects enabled: $enabled")
    }
    
    /**
     * Установить значение полосы эквалайзера
     * @param bandIndex индекс полосы (0-9)
     * @param value значение в dB (-12 to +12)
     */
    fun setBand(bandIndex: Int, value: Float) {
        val newBands = _bands.value.toMutableList()
        newBands[bandIndex] = value.coerceIn(-12f, 12f)
        _bands.value = newBands
        
        applyEqualizerBand(bandIndex, value)
        saveBands()
    }
    
    /**
     * Установить все полосы эквалайзера
     */
    fun setAllBands(values: List<Float>) {
        if (values.size != 10) return
        
        _bands.value = values.map { it.coerceIn(-12f, 12f) }
        applyEqualizerBands(values)
        saveBands()
    }
    
    private fun applyEqualizerBand(bandIndex: Int, value: Float) {
        equalizer?.let { eq ->
            try {
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minLevel = range[0].toFloat()
                val maxLevel = range[1].toFloat()
                
                // Конвертируем dB в уровень эквалайзера (millibels)
                // Android использует millibels: 1 dB = 100 millibels
                val levelInMillibels = (value * 100).toInt().toShort()
                
                // Если у нас 10 полос UI и 10 полос в Android - прямой маппинг
                if (numBands == 10) {
                    eq.setBandLevel(bandIndex.toShort(), levelInMillibels)
                } else {
                    // Если полос меньше (обычно 5), маппим несколько UI полос на одну реальную
                    val targetBand = (bandIndex * numBands / 10).coerceIn(0, numBands - 1)
                    eq.setBandLevel(targetBand.toShort(), levelInMillibels)
                }
                
                Log.d(TAG, "Set band $bandIndex to ${value}dB (${levelInMillibels}mb) -> real band ${if (numBands == 10) bandIndex else (bandIndex * numBands / 10)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set band $bandIndex", e)
            }
        }
    }
    
    private fun applyEqualizerBands(values: List<Float>) {
        equalizer?.let { eq ->
            try {
                val numBands = eq.numberOfBands.toInt()
                
                Log.d(TAG, "Applying EQ bands: numBands=$numBands, values=$values")
                
                if (numBands == 10) {
                    // Прямой маппинг 1:1
                    for (i in 0 until 10) {
                        val levelInMillibels = (values[i] * 100).toInt().toShort()
                        eq.setBandLevel(i.toShort(), levelInMillibels)
                    }
                } else {
                    // Маппим 10 UI полос на меньшее количество реальных полос
                    for (i in 0 until numBands) {
                        val sourceIndex = (i * 10 / numBands).coerceIn(0, 9)
                        val value = values[sourceIndex]
                        val levelInMillibels = (value * 100).toInt().toShort()
                        eq.setBandLevel(i.toShort(), levelInMillibels)
                    }
                }
                
                Log.d(TAG, "Applied EQ bands successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply EQ bands", e)
            }
        }
    }
    
    private fun saveBands() {
        scope.launch {
            preferencesManager.setEqualizerBands(_bands.value.joinToString(","))
        }
    }
    
    /**
     * Установить Bass Boost (0-1000)
     */
    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clamped
        
        bassBoost?.let {
            try {
                it.setStrength(clamped.toShort())
                Log.d(TAG, "BassBoost set to $clamped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set bass boost", e)
            }
        }
        
        scope.launch {
            preferencesManager.setBassBoost(clamped)
        }
    }
    
    /**
     * Установить Virtualizer (0-1000)
     */
    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _virtualizerStrength.value = clamped
        
        virtualizer?.let {
            try {
                it.setStrength(clamped.toShort())
                Log.d(TAG, "Virtualizer set to $clamped")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set virtualizer", e)
            }
        }
        
        scope.launch {
            preferencesManager.setVirtualizer(clamped)
        }
    }
    
    /**
     * Установить пресет реверберации
     */
    fun setReverbPreset(preset: Short) {
        _reverbPreset.value = preset
        presetReverb?.let {
            try {
                it.preset = preset
                it.enabled = preset != PresetReverb.PRESET_NONE
                _reverbEnabled.value = preset != PresetReverb.PRESET_NONE
                Log.d(TAG, "Reverb preset set to $preset, enabled=${it.enabled}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set reverb preset", e)
            }
        }
    }
    
    /**
     * Включить/выключить реверберацию
     */
    fun setReverbEnabled(enabled: Boolean) {
        _reverbEnabled.value = enabled
        presetReverb?.let {
            try {
                it.enabled = enabled
                if (enabled && _reverbPreset.value == PresetReverb.PRESET_NONE) {
                    // Если включаем без пресета, ставим Medium Room
                    it.preset = PresetReverb.PRESET_MEDIUMROOM
                    _reverbPreset.value = PresetReverb.PRESET_MEDIUMROOM
                }
                Log.d(TAG, "Reverb enabled: $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set reverb enabled", e)
            }
        }
    }
    
    /**
     * Установить реверберацию по параметрам (маппим на пресеты)
     * roomSize: 0-1, decay: 0-1
     */
    fun setReverbParams(roomSize: Float, decay: Float) {
        // Маппим параметры на пресеты Android
        val preset = when {
            roomSize < 0.2f -> PresetReverb.PRESET_SMALLROOM
            roomSize < 0.4f -> PresetReverb.PRESET_MEDIUMROOM
            roomSize < 0.6f -> PresetReverb.PRESET_LARGEROOM
            roomSize < 0.8f -> PresetReverb.PRESET_MEDIUMHALL
            else -> PresetReverb.PRESET_LARGEHALL
        }
        setReverbPreset(preset)
    }
    
    /**
     * Включить/выключить Loudness Enhancer
     */
    fun setLoudnessEnabled(enabled: Boolean) {
        _loudnessEnabled.value = enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            loudnessEnhancer?.let {
                try {
                    it.enabled = enabled
                    Log.d(TAG, "LoudnessEnhancer enabled: $enabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set loudness enabled", e)
                }
            }
        }
    }
    
    /**
     * Установить усиление Loudness (в миллибелах, 0-1000)
     */
    fun setLoudnessGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 1000)
        _loudnessGain.value = clamped
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            loudnessEnhancer?.let {
                try {
                    it.setTargetGain(clamped)
                    if (!it.enabled && clamped > 0) {
                        it.enabled = true
                        _loudnessEnabled.value = true
                    }
                    Log.d(TAG, "LoudnessEnhancer gain set to $clamped mB")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set loudness gain", e)
                }
            }
        }
    }
    
    /**
     * Установить Preamp (применяется через все полосы эквалайзера)
     */
    fun setPreamp(gainDb: Float) {
        val clamped = gainDb.coerceIn(-6f, 6f)
        _preampGain.value = clamped
        
        // Применяем preamp как смещение ко всем полосам
        applyEqualizerWithPreamp()
        Log.d(TAG, "Preamp set to $clamped dB")
    }
    
    private fun applyEqualizerWithPreamp() {
        equalizer?.let { eq ->
            try {
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minLevel = range[0].toFloat()
                val maxLevel = range[1].toFloat()
                val preamp = _preampGain.value
                
                for (i in 0 until numBands) {
                    val sourceIndex = (i * 10 / numBands).coerceIn(0, 9)
                    val value = _bands.value[sourceIndex] + preamp
                    
                    val level = ((value + 12f) / 24f * (maxLevel - minLevel) + minLevel)
                        .toInt()
                        .coerceIn(minLevel.toInt(), maxLevel.toInt())
                        .toShort()
                    eq.setBandLevel(i.toShort(), level)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply EQ with preamp", e)
            }
        }
    }
    
    /**
     * Сбросить все настройки
     */
    fun reset() {
        setAllBands(List(10) { 0f })
        setBassBoost(0)
        setVirtualizer(0)
        setReverbPreset(PresetReverb.PRESET_NONE)
        setLoudnessEnabled(false)
        setLoudnessGain(0)
        setPreamp(0f)
        Log.d(TAG, "All effects reset")
    }
    
    /**
     * Анализ басов и битов из waveform данных
     */
    private fun analyzeBassAndBeat(waveform: ByteArray) {
        if (waveform.isEmpty()) return
        
        try {
            // Вычисляем RMS (Root Mean Square) для определения общей громкости
            var sum = 0.0
            for (i in waveform.indices) {
                val sample = (waveform[i].toInt() - 128) / 128.0
                sum += sample * sample
            }
            val rms = sqrt(sum / waveform.size)
            
            // Анализируем низкие частоты (басы) - первая треть waveform
            var bassSum = 0.0
            val bassRange = waveform.size / 3
            for (i in 0 until bassRange) {
                val sample = abs((waveform[i].toInt() - 128) / 128.0)
                bassSum += sample
            }
            val bassAvg = (bassSum / bassRange).toFloat()
            
            // Обновляем уровень басов (0.0 - 1.0)
            val newBassLevel = bassAvg.coerceIn(0f, 1f)
            _bassLevel.value = newBassLevel
            
            // Определение бита: резкий скачок громкости
            val currentTime = System.currentTimeMillis()
            if (rms > beatThreshold && currentTime - lastBeatTime > beatCooldown) {
                _beatDetected.value = true
                lastBeatTime = currentTime
                Log.d(TAG, "Beat detected! RMS: $rms, Bass: $newBassLevel")
                
                // Сбрасываем флаг бита через короткое время
                scope.launch {
                    delay(100)
                    _beatDetected.value = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing audio", e)
        }
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            presetReverb?.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing effects", e)
        }
        
        visualizer = null
        equalizer = null
        bassBoost = null
        virtualizer = null
        presetReverb = null
        loudnessEnhancer = null
        audioSessionId = 0
        
        Log.d(TAG, "Effects released")
    }
    
    /**
     * Получить audio session ID
     */
    fun getAudioSessionId(): Int = audioSessionId
}
