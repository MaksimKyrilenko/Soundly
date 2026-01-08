package com.example.soundly.player.audio.dsp

import kotlin.math.*

/**
 * Preamp модуль с headroom
 */
class PreampModule {
    var enabled = false
    var gainDb = 0f
    
    fun reset() {
        gainDb = 0f
    }
    
    fun process(buffer: FloatArray): FloatArray {
        if (!enabled || gainDb == 0f) return buffer
        val gain = 10f.pow(gainDb / 20f)
        return buffer.map { (it * gain).coerceIn(-1f, 1f) }.toFloatArray()
    }
}

/**
 * 10-полосный параметрический эквалайзер
 */
class EqualizerModule {
    var enabled = false
    var sampleRate = 44100
    
    private val bands = FloatArray(10) { 0f }
    private val frequencies = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    private val filters = Array(10) { BiquadFilter() }
    private val fftBuffer = FloatArray(512)
    private var fftIndex = 0
    
    fun reset() {
        bands.fill(0f)
        filters.forEach { it.reset() }
    }
    
    fun setBand(index: Int, gainDb: Float) {
        if (index in 0..9) {
            bands[index] = gainDb.coerceIn(-12f, 12f)
            updateFilter(index)
        }
    }

    private fun updateFilter(index: Int) {
        val freq = frequencies[index].toFloat()
        val gain = bands[index]
        val q = 1.4f // Bandwidth
        filters[index].setPeakingEQ(sampleRate.toFloat(), freq, q, gain)
    }
    
    fun process(buffer: FloatArray, channelCount: Int): FloatArray {
        if (!enabled) return buffer
        
        val result = buffer.copyOf()
        
        // Применяем каждый фильтр
        for (i in 0 until 10) {
            if (abs(bands[i]) > 0.1f) {
                filters[i].process(result, channelCount)
            }
        }
        
        // Собираем данные для FFT
        for (i in buffer.indices step channelCount) {
            if (fftIndex < fftBuffer.size) {
                fftBuffer[fftIndex++] = buffer[i]
            }
        }
        if (fftIndex >= fftBuffer.size) fftIndex = 0
        
        return result
    }
    
    fun getFFTData(): FloatArray = fftBuffer.copyOf()
}

/**
 * Bass Engine: сабгармоники, сатурация, моно-бас
 */
class BassEngineModule {
    var enabled = false
    var sampleRate = 44100
    
    var subHarmonicsAmount = 0f
    var saturationAmount = 0f
    var saturationMode = BassEngineSaturationMode.SOFT
    var harmonicsAmount = 0f
    var kickBoostFrequency = 80
    var kickBoostAmount = 0f
    var monoBelow = 0 // Частота ниже которой бас в моно
    
    private val lowpassFilter = BiquadFilter()
    private val subOscPhase = FloatArray(2)
    
    fun reset() {
        subHarmonicsAmount = 0f
        saturationAmount = 0f
        harmonicsAmount = 0f
        kickBoostAmount = 0f
        monoBelow = 0
        subOscPhase.fill(0f)
    }
    
    fun process(buffer: FloatArray, channelCount: Int): FloatArray {
        if (!enabled) return buffer
        
        val result = buffer.copyOf()
        
        // Моно-бас
        if (monoBelow > 0 && channelCount == 2) {
            applyMonoBass(result, monoBelow)
        }
        
        // Сабгармоники
        if (subHarmonicsAmount > 0) {
            addSubHarmonics(result, channelCount)
        }
        
        // Сатурация баса
        if (saturationAmount > 0) {
            applyBassSaturation(result, channelCount)
        }
        
        return result
    }
    
    private fun applyMonoBass(buffer: FloatArray, freq: Int) {
        lowpassFilter.setLowpass(sampleRate.toFloat(), freq.toFloat(), 0.707f)
        
        for (i in buffer.indices step 2) {
            val left = buffer[i]
            val right = buffer.getOrElse(i + 1) { left }
            
            // Извлекаем низкие частоты
            val mono = (left + right) / 2f
            val lowFreq = lowpassFilter.processSample(mono)
            
            // Высокие частоты остаются стерео
            val highLeft = left - lowFreq
            val highRight = right - lowFreq
            
            buffer[i] = lowFreq + highLeft
            if (i + 1 < buffer.size) buffer[i + 1] = lowFreq + highRight
        }
    }
    
    private fun addSubHarmonics(buffer: FloatArray, channelCount: Int) {
        val amount = subHarmonicsAmount / 100f
        for (i in buffer.indices) {
            val ch = i % channelCount
            // Простой субгармонический синтез через деление частоты
            val sample = buffer[i]
            subOscPhase[ch] += sample * 0.5f
            if (subOscPhase[ch] > 1f) subOscPhase[ch] = -1f
            if (subOscPhase[ch] < -1f) subOscPhase[ch] = 1f
            buffer[i] = (sample + subOscPhase[ch] * amount * 0.3f).coerceIn(-1f, 1f)
        }
    }
    
    private fun applyBassSaturation(buffer: FloatArray, channelCount: Int) {
        val amount = saturationAmount / 100f
        for (i in buffer.indices) {
            val sample = buffer[i]
            buffer[i] = when (saturationMode) {
                BassEngineSaturationMode.SOFT -> softClip(sample, amount)
                BassEngineSaturationMode.TUBE -> tubeDistortion(sample, amount)
                BassEngineSaturationMode.TAPE -> tapeDistortion(sample, amount)
            }
        }
    }
    
    private fun softClip(x: Float, amount: Float): Float {
        val drive = 1f + amount * 3f
        val driven = x * drive
        return (2f / PI.toFloat()) * atan(driven)
    }
    
    private fun tubeDistortion(x: Float, amount: Float): Float {
        val drive = 1f + amount * 2f
        val driven = x * drive
        return if (driven >= 0) {
            1f - exp(-driven)
        } else {
            -1f + exp(driven)
        }
    }
    
    private fun tapeDistortion(x: Float, amount: Float): Float {
        val drive = 1f + amount * 1.5f
        val driven = x * drive
        return tanh(driven)
    }
}

enum class BassEngineSaturationMode { SOFT, TUBE, TAPE }


/**
 * Компрессор с makeup gain
 */
class CompressorModule {
    var enabled = false
    var sampleRate = 44100
    
    var threshold = -10f // dB
    var ratio = 4f
    var attack = 10f // ms
    var release = 100f // ms
    var makeupGain = 0f // dB
    
    private var envelope = 0f
    
    fun reset() {
        threshold = -10f
        ratio = 4f
        attack = 10f
        release = 100f
        makeupGain = 0f
        envelope = 0f
    }
    
    fun process(buffer: FloatArray): FloatArray {
        if (!enabled) return buffer
        
        val attackCoef = exp(-1f / (attack * sampleRate / 1000f))
        val releaseCoef = exp(-1f / (release * sampleRate / 1000f))
        val thresholdLinear = 10f.pow(threshold / 20f)
        val makeupLinear = 10f.pow(makeupGain / 20f)
        
        return buffer.map { sample ->
            val inputLevel = abs(sample)
            
            // Envelope follower
            envelope = if (inputLevel > envelope) {
                attackCoef * envelope + (1 - attackCoef) * inputLevel
            } else {
                releaseCoef * envelope + (1 - releaseCoef) * inputLevel
            }
            
            // Gain reduction
            val gainReduction = if (envelope > thresholdLinear) {
                val overDb = 20f * log10(envelope / thresholdLinear)
                val reducedDb = overDb * (1f - 1f / ratio)
                10f.pow(-reducedDb / 20f)
            } else {
                1f
            }
            
            (sample * gainReduction * makeupLinear).coerceIn(-1f, 1f)
        }.toFloatArray()
    }
}

/**
 * Стерео процессор: ширина, баланс
 */
class StereoProcessorModule {
    var enabled = false
    var width = 100f // 0-150%
    var balance = 0f // -100..100
    
    fun reset() {
        width = 100f
        balance = 0f
    }
    
    fun process(buffer: FloatArray): FloatArray {
        if (!enabled || buffer.size < 2) return buffer
        
        val result = buffer.copyOf()
        val widthFactor = width / 100f
        
        for (i in 0 until buffer.size - 1 step 2) {
            val left = buffer[i]
            val right = buffer[i + 1]
            
            // Mid-Side processing
            val mid = (left + right) / 2f
            val side = (left - right) / 2f
            
            // Применяем ширину
            val newSide = side * widthFactor
            
            // Обратно в L/R
            var newLeft = mid + newSide
            var newRight = mid - newSide
            
            // Баланс
            if (balance != 0f) {
                val balanceFactor = balance / 100f
                if (balanceFactor > 0) {
                    newLeft *= (1f - balanceFactor)
                } else {
                    newRight *= (1f + balanceFactor)
                }
            }
            
            result[i] = newLeft.coerceIn(-1f, 1f)
            result[i + 1] = newRight.coerceIn(-1f, 1f)
        }
        
        return result
    }
}

/**
 * Сатурация: tape, tube, soft clip
 */
class SaturationModule {
    var enabled = false
    var type = SaturationType.TAPE
    var amount = 0f // 0-100
    var warmth = 0f // 0-1, добавляет низкие
    
    fun reset() {
        type = SaturationType.TAPE
        amount = 0f
        warmth = 0f
    }
    
    fun process(buffer: FloatArray): FloatArray {
        if (!enabled || amount == 0f) return buffer
        
        val drive = 1f + (amount / 100f) * 3f
        
        return buffer.map { sample ->
            val driven = sample * drive
            val saturated = when (type) {
                SaturationType.TAPE -> tanh(driven * 0.8f)
                SaturationType.TUBE -> {
                    if (driven >= 0) 1f - exp(-driven)
                    else -1f + exp(driven)
                }
                SaturationType.SOFT_CLIP -> (2f / PI.toFloat()) * atan(driven)
                SaturationType.HARD_CLIP -> driven.coerceIn(-0.9f, 0.9f)
            }
            
            // Blend с оригиналом
            val blend = amount / 100f
            (sample * (1f - blend) + saturated * blend).coerceIn(-1f, 1f)
        }.toFloatArray()
    }
}

enum class SaturationType { TAPE, TUBE, SOFT_CLIP, HARD_CLIP }


/**
 * Простой реверб (Schroeder)
 */
class ReverbModule {
    var enabled = false
    var sampleRate = 44100
    
    var roomSize = 0.5f // 0-1
    var damping = 0.5f // 0-1
    var wetMix = 0.3f // 0-1
    var dryMix = 0.7f // 0-1
    
    private val combDelays = intArrayOf(1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116)
    private val allpassDelays = intArrayOf(225, 556, 441, 341)
    
    private val combBuffers = Array(8) { FloatArray(combDelays[it] * 2) }
    private val combIndices = IntArray(8)
    private val combFilters = FloatArray(8)
    
    private val allpassBuffers = Array(4) { FloatArray(allpassDelays[it] * 2) }
    private val allpassIndices = IntArray(4)
    
    fun reset() {
        roomSize = 0.5f
        damping = 0.5f
        wetMix = 0.3f
        dryMix = 0.7f
        combBuffers.forEach { it.fill(0f) }
        combIndices.fill(0)
        combFilters.fill(0f)
        allpassBuffers.forEach { it.fill(0f) }
        allpassIndices.fill(0)
    }
    
    fun process(buffer: FloatArray, channelCount: Int): FloatArray {
        if (!enabled) return buffer
        
        val result = buffer.copyOf()
        val feedback = roomSize * 0.84f + 0.1f
        val damp = damping * 0.4f
        
        for (i in buffer.indices step channelCount) {
            val input = if (channelCount == 2) {
                (buffer[i] + buffer.getOrElse(i + 1) { buffer[i] }) / 2f
            } else {
                buffer[i]
            }
            
            var wet = 0f
            
            // Comb filters
            for (c in 0 until 8) {
                val bufSize = combDelays[c]
                val idx = combIndices[c]
                val delayed = combBuffers[c][idx]
                
                // Lowpass filter in feedback
                combFilters[c] = delayed * (1f - damp) + combFilters[c] * damp
                
                combBuffers[c][idx] = input + combFilters[c] * feedback
                combIndices[c] = (idx + 1) % bufSize
                
                wet += delayed
            }
            wet /= 8f
            
            // Allpass filters
            for (a in 0 until 4) {
                val bufSize = allpassDelays[a]
                val idx = allpassIndices[a]
                val delayed = allpassBuffers[a][idx]
                
                val output = -wet + delayed
                allpassBuffers[a][idx] = wet + delayed * 0.5f
                allpassIndices[a] = (idx + 1) % bufSize
                
                wet = output
            }
            
            // Mix
            for (ch in 0 until channelCount) {
                if (i + ch < result.size) {
                    result[i + ch] = (buffer[i + ch] * dryMix + wet * wetMix).coerceIn(-1f, 1f)
                }
            }
        }
        
        return result
    }
}

/**
 * Dynamic EQ для подавления резонансов (антисвист)
 */
class DynamicEQModule {
    var enabled = false
    var sampleRate = 44100
    
    var targetFrequency = 7000f // Центр диапазона 5-9 kHz
    var bandwidth = 4000f
    var threshold = -6f // dB
    var reduction = -4f // dB максимальное подавление
    
    private val detector = BiquadFilter()
    private val notch = BiquadFilter()
    private var envelope = 0f
    
    fun reset() {
        targetFrequency = 7000f
        bandwidth = 4000f
        threshold = -6f
        reduction = -4f
        envelope = 0f
        detector.reset()
        notch.reset()
    }
    
    fun process(buffer: FloatArray, channelCount: Int): FloatArray {
        if (!enabled) return buffer
        
        val result = buffer.copyOf()
        val thresholdLinear = 10f.pow(threshold / 20f)
        val releaseCoef = exp(-1f / (50f * sampleRate / 1000f))
        
        // Настраиваем детектор на целевую частоту
        detector.setBandpass(sampleRate.toFloat(), targetFrequency, 2f)
        
        for (i in buffer.indices step channelCount) {
            val sample = buffer[i]
            
            // Детектируем уровень на целевой частоте
            val detected = abs(detector.processSample(sample))
            envelope = maxOf(detected, envelope * releaseCoef)
            
            // Вычисляем gain reduction
            val gainReduction = if (envelope > thresholdLinear) {
                val overDb = 20f * log10(envelope / thresholdLinear)
                val reducedDb = minOf(overDb, -reduction)
                10f.pow(-reducedDb / 20f)
            } else {
                1f
            }
            
            // Применяем notch filter с динамической глубиной
            if (gainReduction < 1f) {
                notch.setNotch(sampleRate.toFloat(), targetFrequency, 2f, (1f - gainReduction) * reduction)
                for (ch in 0 until channelCount) {
                    if (i + ch < result.size) {
                        result[i + ch] = notch.processSample(buffer[i + ch])
                    }
                }
            }
        }
        
        return result
    }
}

/**
 * Компенсация громкости по кривым Флетчера-Мансона
 */
class LoudnessCompensationModule {
    var enabled = false
    var targetLoudness = -14f // LUFS
    
    private var currentLoudness = -23f
    
    fun reset() {
        targetLoudness = -14f
        currentLoudness = -23f
    }
    
    fun process(buffer: FloatArray): FloatArray {
        if (!enabled) return buffer
        
        // Измеряем текущую громкость (упрощённый LUFS)
        var sumSquares = 0f
        for (sample in buffer) {
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / buffer.size)
        val measuredDb = if (rms > 0) 20f * log10(rms) else -60f
        
        // Плавное обновление
        currentLoudness = currentLoudness * 0.99f + measuredDb * 0.01f
        
        // Компенсация
        val compensation = targetLoudness - currentLoudness
        val gain = 10f.pow(compensation.coerceIn(-12f, 12f) / 20f)
        
        return buffer.map { (it * gain).coerceIn(-1f, 1f) }.toFloatArray()
    }
}

/**
 * Лимитер (brick wall)
 */
class LimiterModule {
    var enabled = false
    var sampleRate = 44100
    
    var ceiling = -0.1f // dB
    var release = 50f // ms
    
    var currentLevel = 0f
        private set
    
    private var gainReduction = 1f
    
    fun reset() {
        ceiling = -0.1f
        release = 50f
        gainReduction = 1f
        currentLevel = 0f
    }
    
    fun process(buffer: FloatArray): FloatArray {
        if (!enabled) return buffer
        
        val ceilingLinear = 10f.pow(ceiling / 20f)
        val releaseCoef = exp(-1f / (release * sampleRate / 1000f))
        
        val result = buffer.copyOf()
        var peakLevel = 0f
        
        for (i in buffer.indices) {
            val sample = buffer[i]
            val absLevel = abs(sample)
            peakLevel = maxOf(peakLevel, absLevel)
            
            // Вычисляем необходимое ограничение
            val targetGain = if (absLevel > ceilingLinear) {
                ceilingLinear / absLevel
            } else {
                1f
            }
            
            // Плавное изменение gain
            gainReduction = if (targetGain < gainReduction) {
                targetGain // Мгновенная атака
            } else {
                gainReduction + (1f - gainReduction) * (1f - releaseCoef)
            }
            
            result[i] = (sample * gainReduction).coerceIn(-ceilingLinear, ceilingLinear)
        }
        
        currentLevel = peakLevel
        return result
    }
}


/**
 * Biquad фильтр для EQ и других модулей
 */
class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f
    
    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
    
    fun setPeakingEQ(sampleRate: Float, freq: Float, q: Float, gainDb: Float) {
        val A = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)
        
        val a0 = 1f + alpha / A
        b0 = (1f + alpha * A) / a0
        b1 = (-2f * cosW0) / a0
        b2 = (1f - alpha * A) / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha / A) / a0
    }
    
    fun setLowpass(sampleRate: Float, freq: Float, q: Float) {
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)
        
        val a0 = 1f + alpha
        b0 = ((1f - cosW0) / 2f) / a0
        b1 = (1f - cosW0) / a0
        b2 = ((1f - cosW0) / 2f) / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha) / a0
    }
    
    fun setHighpass(sampleRate: Float, freq: Float, q: Float) {
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)
        
        val a0 = 1f + alpha
        b0 = ((1f + cosW0) / 2f) / a0
        b1 = (-(1f + cosW0)) / a0
        b2 = ((1f + cosW0) / 2f) / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha) / a0
    }
    
    fun setBandpass(sampleRate: Float, freq: Float, q: Float) {
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)
        
        val a0 = 1f + alpha
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha) / a0
    }
    
    fun setNotch(sampleRate: Float, freq: Float, q: Float, depth: Float = 1f) {
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q) * depth
        
        val a0 = 1f + alpha
        b0 = 1f / a0
        b1 = (-2f * cosW0) / a0
        b2 = 1f / a0
        a1 = (-2f * cosW0) / a0
        a2 = (1f - alpha) / a0
    }
    
    fun processSample(input: Float): Float {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }
    
    fun process(buffer: FloatArray, channelCount: Int) {
        for (i in buffer.indices) {
            buffer[i] = processSample(buffer[i])
        }
    }
}
