package com.example.soundly.player.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Production-ready Audio Reactive Controller
 * 
 * Управляет аудио-реактивной анимацией обложки трека.
 * Использует Visualizer API для анализа аудио в реальном времени.
 * 
 * Основные возможности:
 * - Анализ waveform и FFT данных
 * - Вычисление RMS и peak detection
 * - Low-pass фильтр для сглаживания
 * - Beat detection
 * - Оптимизация производительности
 * - Корректное управление жизненным циклом
 * 
 * @author Soundly Team
 */
class AudioReactiveController {
    
    companion object {
        private const val TAG = "AudioReactiveController"
        
        // Параметры Visualizer
        private const val CAPTURE_RATE_MILLIS = 50L // 20 FPS - оптимально для UI
        private const val MIN_CAPTURE_SIZE = 128
        
        // Параметры анализа
        private const val RMS_SMOOTHING_FACTOR = 0.3f // Low-pass filter коэффициент
        private const val BEAT_THRESHOLD = 1.4f // Порог для определения бита
        private const val BEAT_COOLDOWN_MS = 300L // Минимальное время между битами
        
        // Параметры анимации
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 1.08f
        private const val BASS_BOOST_MULTIPLIER = 0.15f
        private const val BEAT_BOOST = 0.08f
        
        // Limiter - защита от слишком сильных скачков
        private const val MAX_AMPLITUDE_CHANGE = 0.2f // Максимальное изменение за кадр
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Visualizer instance
    private var visualizer: Visualizer? = null
    private var audioSessionId: Int = 0
    
    // State flows для UI
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()
    
    private val _bassLevel = MutableStateFlow(0f)
    val bassLevel: StateFlow<Float> = _bassLevel.asStateFlow()
    
    private val _beatDetected = MutableStateFlow(false)
    val beatDetected: StateFlow<Boolean> = _beatDetected.asStateFlow()
    
    private val _scale = MutableStateFlow(MIN_SCALE)
    val scale: StateFlow<Float> = _scale.asStateFlow()
    
    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    // Внутреннее состояние
    private var smoothedRms = 0f
    private var smoothedBass = 0f
    private var lastBeatTime = 0L
    private var lastAmplitude = 0f
    private var updateJob: Job? = null
    
    // Настройки
    private var sensitivity = 1.0f // 0.5 - 2.0
    private var enableBeatDetection = true
    private var enableLimiter = true
    
    /**
     * Инициализация с audio session ID от ExoPlayer
     */
    fun initialize(sessionId: Int) {
        if (sessionId == 0) {
            Log.e(TAG, "Invalid audio session ID: 0")
            return
        }
        
        if (sessionId == audioSessionId && visualizer != null) {
            Log.d(TAG, "Already initialized with session $sessionId")
            return
        }
        
        release()
        audioSessionId = sessionId
        
        try {
            // Создаём Visualizer
            visualizer = Visualizer(sessionId).apply {
                // Устанавливаем оптимальный размер захвата
                val range = Visualizer.getCaptureSizeRange()
                captureSize = range[1].coerceAtLeast(MIN_CAPTURE_SIZE)
                
                Log.d(TAG, "Visualizer created: captureSize=$captureSize, range=${range.contentToString()}")
                
                // Устанавливаем listener для захвата данных
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { processWaveform(it) }
                        }
                        
                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            fft?.let { processFft(it) }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2, // Оптимальная частота
                    true, // waveform
                    true  // fft
                )
                
                enabled = true
                Log.d(TAG, "Visualizer enabled successfully")
            }
            
            // Запускаем обновление scale в UI потоке
            startScaleUpdates()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Visualizer", e)
            visualizer = null
        }
    }
    
    /**
     * Обработка waveform данных
     * Вычисляет RMS (Root Mean Square) для общей амплитуды
     */
    private fun processWaveform(waveform: ByteArray) {
        if (waveform.isEmpty() || !_isEnabled.value) return
        
        try {
            // Вычисляем RMS
            var sum = 0.0
            for (i in waveform.indices) {
                val sample = (waveform[i].toInt() - 128) / 128.0
                sum += sample * sample
            }
            val rms = sqrt(sum / waveform.size).toFloat()
            
            // Применяем low-pass фильтр для сглаживания
            smoothedRms = smoothedRms * (1f - RMS_SMOOTHING_FACTOR) + rms * RMS_SMOOTHING_FACTOR
            
            // Применяем limiter если включён
            val newAmplitude = if (enableLimiter) {
                val change = smoothedRms - lastAmplitude
                val limitedChange = change.coerceIn(-MAX_AMPLITUDE_CHANGE, MAX_AMPLITUDE_CHANGE)
                lastAmplitude + limitedChange
            } else {
                smoothedRms
            }
            
            lastAmplitude = newAmplitude
            _amplitude.value = (newAmplitude * sensitivity).coerceIn(0f, 1f)
            
            // Beat detection
            if (enableBeatDetection) {
                detectBeat(rms)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing waveform", e)
        }
    }
    
    /**
     * Обработка FFT данных
     * Анализирует низкие частоты (басы)
     */
    private fun processFft(fft: ByteArray) {
        if (fft.isEmpty() || !_isEnabled.value) return
        
        try {
            // Анализируем низкие частоты (басы) - первые 20% FFT
            val bassRange = (fft.size * 0.2).toInt()
            var bassSum = 0.0
            
            for (i in 0 until bassRange step 2) {
                val real = fft[i].toInt()
                val imag = if (i + 1 < fft.size) fft[i + 1].toInt() else 0
                val magnitude = sqrt((real * real + imag * imag).toDouble())
                bassSum += magnitude
            }
            
            val bassAvg = (bassSum / (bassRange / 2)).toFloat()
            val normalizedBass = (bassAvg / 128f).coerceIn(0f, 1f)
            
            // Применяем сглаживание
            smoothedBass = smoothedBass * 0.7f + normalizedBass * 0.3f
            _bassLevel.value = (smoothedBass * sensitivity).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FFT", e)
        }
    }
    
    /**
     * Определение битов музыки
     * Использует резкие скачки амплитуды
     */
    private fun detectBeat(currentRms: Float) {
        val currentTime = System.currentTimeMillis()
        
        // Проверяем превышение порога и cooldown
        if (currentRms > BEAT_THRESHOLD && 
            currentTime - lastBeatTime > BEAT_COOLDOWN_MS) {
            
            _beatDetected.value = true
            lastBeatTime = currentTime
            
            // Сбрасываем флаг через короткое время
            scope.launch {
                delay(100)
                _beatDetected.value = false
            }
            
            Log.d(TAG, "Beat detected! RMS: $currentRms")
        }
    }
    
    /**
     * Запуск обновления scale для UI
     * Работает в отдельной корутине для оптимизации
     */
    private fun startScaleUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive && _isEnabled.value) {
                try {
                    val currentAmplitude = _amplitude.value
                    val currentBass = _bassLevel.value
                    val isBeat = _beatDetected.value
                    
                    // Вычисляем целевой scale
                    val targetScale = MIN_SCALE + 
                        (currentBass * BASS_BOOST_MULTIPLIER) +
                        (if (isBeat) BEAT_BOOST else 0f)
                    
                    val finalScale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
                    _scale.value = finalScale
                    
                    // Оптимальная частота обновления UI
                    delay(CAPTURE_RATE_MILLIS)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating scale", e)
                }
            }
        }
    }
    
    /**
     * Включить/выключить эффект
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        
        if (enabled) {
            visualizer?.enabled = true
            startScaleUpdates()
        } else {
            visualizer?.enabled = false
            updateJob?.cancel()
            _scale.value = MIN_SCALE
            _amplitude.value = 0f
            _bassLevel.value = 0f
            _beatDetected.value = false
        }
        
        Log.d(TAG, "Audio reactive effect ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Установить чувствительность (0.5 - 2.0)
     */
    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.5f, 2.0f)
        Log.d(TAG, "Sensitivity set to $sensitivity")
    }
    
    /**
     * Включить/выключить beat detection
     */
    fun setBeatDetectionEnabled(enabled: Boolean) {
        enableBeatDetection = enabled
        Log.d(TAG, "Beat detection ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Включить/выключить limiter
     */
    fun setLimiterEnabled(enabled: Boolean) {
        enableLimiter = enabled
        Log.d(TAG, "Limiter ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Пауза (при onPause Activity)
     */
    fun pause() {
        visualizer?.enabled = false
        updateJob?.cancel()
        Log.d(TAG, "Paused")
    }
    
    /**
     * Возобновление (при onResume Activity)
     */
    fun resume() {
        if (_isEnabled.value && visualizer != null) {
            visualizer?.enabled = true
            startScaleUpdates()
            Log.d(TAG, "Resumed")
        }
    }
    
    /**
     * Полное освобождение ресурсов
     * ОБЯЗАТЕЛЬНО вызывать при уничтожении
     */
    fun release() {
        try {
            updateJob?.cancel()
            updateJob = null
            
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
            
            audioSessionId = 0
            smoothedRms = 0f
            smoothedBass = 0f
            lastAmplitude = 0f
            
            _amplitude.value = 0f
            _bassLevel.value = 0f
            _beatDetected.value = false
            _scale.value = MIN_SCALE
            
            Log.d(TAG, "Released")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
    
    /**
     * Получить текущий audio session ID
     */
    fun getAudioSessionId(): Int = audioSessionId
    
    /**
     * Проверка инициализации
     */
    fun isInitialized(): Boolean = visualizer != null && audioSessionId != 0
}
