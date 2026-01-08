package com.example.soundly.player.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Анализатор спектра в реальном времени
 * Использует Android Visualizer API
 */
@Singleton
class SpectrumAnalyzer @Inject constructor() {
    
    companion object {
        private const val TAG = "SpectrumAnalyzer"
        private const val CAPTURE_SIZE = 256 // FFT size
        private const val BAND_COUNT = 32    // Количество полос для отображения
    }
    
    private var visualizer: Visualizer? = null
    private var audioSessionId: Int = 0
    
    private val _fftData = MutableStateFlow(FloatArray(BAND_COUNT))
    val fftData: StateFlow<FloatArray> = _fftData.asStateFlow()
    
    private val _waveformData = MutableStateFlow(ByteArray(CAPTURE_SIZE))
    val waveformData: StateFlow<ByteArray> = _waveformData.asStateFlow()
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _peakLevel = MutableStateFlow(0f)
    val peakLevel: StateFlow<Float> = _peakLevel.asStateFlow()
    
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()
    
    /**
     * Инициализация с audio session ID от ExoPlayer
     */
    fun initialize(sessionId: Int) {
        if (sessionId == audioSessionId && visualizer != null) return
        
        release()
        audioSessionId = sessionId
        
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = CAPTURE_SIZE
                
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform?.let { 
                                _waveformData.value = it.copyOf()
                                calculateLevels(it)
                            }
                        }
                        
                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            fft?.let { processFFT(it) }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,  // waveform
                    true   // fft
                )
            }
            Log.d(TAG, "Visualizer initialized with session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize visualizer", e)
        }
    }
    
    /**
     * Включить/выключить анализатор
     */
    fun setEnabled(enabled: Boolean) {
        try {
            visualizer?.enabled = enabled
            _isEnabled.value = enabled
            Log.d(TAG, "Visualizer enabled: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set visualizer enabled", e)
        }
    }
    
    /**
     * Обработка FFT данных в полосы для отображения
     */
    private fun processFFT(fft: ByteArray) {
        val bands = FloatArray(BAND_COUNT)
        val n = fft.size / 2
        
        // Логарифмическое распределение частот по полосам
        for (i in 0 until BAND_COUNT) {
            val startBin = (n * Math.pow(i.toDouble() / BAND_COUNT, 2.0)).toInt()
            val endBin = (n * Math.pow((i + 1).toDouble() / BAND_COUNT, 2.0)).toInt()
            
            var sum = 0f
            var count = 0
            
            for (j in startBin until minOf(endBin + 1, n)) {
                // FFT data is in pairs: real, imaginary
                val real = fft[j * 2].toFloat()
                val imag = fft[j * 2 + 1].toFloat()
                val magnitude = kotlin.math.sqrt(real * real + imag * imag)
                sum += magnitude
                count++
            }
            
            if (count > 0) {
                // Нормализация и логарифмическое масштабирование
                val avg = sum / count
                bands[i] = (kotlin.math.log10(avg.coerceAtLeast(1f) + 1f) / 2.5f).coerceIn(0f, 1f)
            }
        }
        
        _fftData.value = bands
    }
    
    /**
     * Расчёт уровней громкости
     */
    private fun calculateLevels(waveform: ByteArray) {
        var peak = 0f
        var sumSquares = 0f
        
        for (sample in waveform) {
            val normalized = (sample.toInt() and 0xFF) / 128f - 1f
            val abs = kotlin.math.abs(normalized)
            if (abs > peak) peak = abs
            sumSquares += normalized * normalized
        }
        
        _peakLevel.value = peak
        _rmsLevel.value = kotlin.math.sqrt(sumSquares / waveform.size)
    }
    
    /**
     * Получить данные для конкретных частотных диапазонов
     */
    fun getBandLevels(): BandLevels {
        val fft = _fftData.value
        return BandLevels(
            subBass = fft.take(2).average().toFloat(),      // 20-60 Hz
            bass = fft.slice(2..5).average().toFloat(),     // 60-250 Hz
            lowMid = fft.slice(6..10).average().toFloat(),  // 250-500 Hz
            mid = fft.slice(11..16).average().toFloat(),    // 500-2k Hz
            highMid = fft.slice(17..23).average().toFloat(),// 2k-6k Hz
            high = fft.slice(24..31).average().toFloat()    // 6k-20k Hz
        )
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
            _isEnabled.value = false
            Log.d(TAG, "Visualizer released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release visualizer", e)
        }
    }
}

/**
 * Уровни по частотным диапазонам
 */
data class BandLevels(
    val subBass: Float = 0f,  // 20-60 Hz
    val bass: Float = 0f,     // 60-250 Hz
    val lowMid: Float = 0f,   // 250-500 Hz
    val mid: Float = 0f,      // 500-2k Hz
    val highMid: Float = 0f,  // 2k-6k Hz
    val high: Float = 0f      // 6k-20k Hz
)
