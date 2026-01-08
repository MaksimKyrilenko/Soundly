package com.example.soundly.player.audio.dsp

import kotlin.math.*

/**
 * Простая реверберация на основе Schroeder алгоритма
 */
class ReverbProcessor(
    private var sampleRate: Int = 44100
) {
    private var roomSize: Float = 0.5f
    private var decay: Float = 0.5f
    private var wetDryMix: Float = 0.3f
    private var preDelay: Float = 0.02f
    
    // Comb filters (4 параллельных)
    private val combDelays = intArrayOf(1557, 1617, 1491, 1422)
    private val combBuffers = Array(4) { FloatArray(combDelays[it] + 1000) }
    private val combIndices = IntArray(4)
    
    // Allpass filters (2 последовательных)
    private val allpassDelays = intArrayOf(225, 556)
    private val allpassBuffers = Array(2) { FloatArray(allpassDelays[it] + 200) }
    private val allpassIndices = IntArray(2)
    
    // Pre-delay buffer
    private var preDelayBuffer = FloatArray(sampleRate / 10) // max 100ms
    private var preDelayIndex = 0
    
    fun setParameters(roomSize: Float, decay: Float, wetDryMix: Float, preDelay: Float) {
        this.roomSize = roomSize.coerceIn(0f, 1f)
        this.decay = decay.coerceIn(0f, 1f)
        this.wetDryMix = wetDryMix.coerceIn(0f, 1f)
        this.preDelay = preDelay.coerceIn(0f, 0.1f)
    }
    
    fun process(input: Float): Float {
        // Pre-delay
        val preDelaySamples = (preDelay * sampleRate).toInt().coerceIn(1, preDelayBuffer.size - 1)
        val delayedInput = preDelayBuffer[(preDelayIndex - preDelaySamples + preDelayBuffer.size) % preDelayBuffer.size]
        preDelayBuffer[preDelayIndex] = input
        preDelayIndex = (preDelayIndex + 1) % preDelayBuffer.size
        
        // Comb filters (параллельно)
        var combSum = 0f
        val feedback = 0.7f + decay * 0.28f
        
        for (i in 0 until 4) {
            val delayLength = (combDelays[i] * (0.8f + roomSize * 0.4f)).toInt()
            val delayedSample = combBuffers[i][(combIndices[i] - delayLength + combBuffers[i].size) % combBuffers[i].size]
            val newSample = delayedInput + delayedSample * feedback
            combBuffers[i][combIndices[i]] = newSample
            combIndices[i] = (combIndices[i] + 1) % combBuffers[i].size
            combSum += delayedSample
        }
        combSum /= 4f
        
        // Allpass filters (последовательно)
        var allpassOut = combSum
        for (i in 0 until 2) {
            val delayLength = (allpassDelays[i] * (0.9f + roomSize * 0.2f)).toInt()
            val delayed = allpassBuffers[i][(allpassIndices[i] - delayLength + allpassBuffers[i].size) % allpassBuffers[i].size]
            val newSample = -0.5f * allpassOut + delayed
            allpassBuffers[i][allpassIndices[i]] = allpassOut + 0.5f * delayed
            allpassIndices[i] = (allpassIndices[i] + 1) % allpassBuffers[i].size
            allpassOut = newSample
        }
        
        // Mix
        return input * (1f - wetDryMix) + allpassOut * wetDryMix
    }
    
    fun reset() {
        combBuffers.forEach { it.fill(0f) }
        allpassBuffers.forEach { it.fill(0f) }
        preDelayBuffer.fill(0f)
        combIndices.fill(0)
        allpassIndices.fill(0)
        preDelayIndex = 0
    }
}

/**
 * Динамический компрессор
 */
class CompressorProcessor(
    private var sampleRate: Int = 44100
) {
    private var threshold: Float = -20f  // dB
    private var ratio: Float = 4f
    private var attackMs: Float = 10f
    private var releaseMs: Float = 100f
    private var makeupGain: Float = 0f   // dB
    
    private var envelope: Float = 0f
    private var attackCoeff: Float = 0f
    private var releaseCoeff: Float = 0f
    
    init {
        updateCoefficients()
    }
    
    fun setParameters(threshold: Float, ratio: Float, attack: Float, release: Float, makeupGain: Float) {
        this.threshold = threshold.coerceIn(-60f, 0f)
        this.ratio = ratio.coerceIn(1f, 20f)
        this.attackMs = attack.coerceIn(0.1f, 100f)
        this.releaseMs = release.coerceIn(10f, 1000f)
        this.makeupGain = makeupGain.coerceIn(0f, 24f)
        updateCoefficients()
    }
    
    private fun updateCoefficients() {
        attackCoeff = exp(-1f / (attackMs * sampleRate / 1000f))
        releaseCoeff = exp(-1f / (releaseMs * sampleRate / 1000f))
    }
    
    fun process(input: Float): Float {
        val inputAbs = abs(input)
        val inputDb = if (inputAbs > 0.00001f) 20f * log10(inputAbs) else -100f
        
        // Envelope follower
        val coeff = if (inputAbs > envelope) attackCoeff else releaseCoeff
        envelope = coeff * envelope + (1f - coeff) * inputAbs
        
        val envelopeDb = if (envelope > 0.00001f) 20f * log10(envelope) else -100f
        
        // Gain computation
        val gainDb = if (envelopeDb > threshold) {
            threshold + (envelopeDb - threshold) / ratio - envelopeDb
        } else {
            0f
        }
        
        // Apply gain + makeup
        val totalGainDb = gainDb + makeupGain
        val gain = 10f.pow(totalGainDb / 20f)
        
        return input * gain
    }
    
    fun reset() {
        envelope = 0f
    }
}

/**
 * Noise Gate
 */
class NoiseGateProcessor(
    private var sampleRate: Int = 44100
) {
    private var threshold: Float = -40f  // dB
    private var attackMs: Float = 1f
    private var releaseMs: Float = 50f
    private var range: Float = -80f      // dB
    
    private var envelope: Float = 0f
    private var gateGain: Float = 0f
    private var attackCoeff: Float = 0f
    private var releaseCoeff: Float = 0f
    
    init {
        updateCoefficients()
    }
    
    fun setParameters(threshold: Float, attack: Float, release: Float, range: Float) {
        this.threshold = threshold.coerceIn(-80f, 0f)
        this.attackMs = attack.coerceIn(0.1f, 50f)
        this.releaseMs = release.coerceIn(10f, 500f)
        this.range = range.coerceIn(-80f, 0f)
        updateCoefficients()
    }
    
    private fun updateCoefficients() {
        attackCoeff = exp(-1f / (attackMs * sampleRate / 1000f))
        releaseCoeff = exp(-1f / (releaseMs * sampleRate / 1000f))
    }
    
    fun process(input: Float): Float {
        val inputAbs = abs(input)
        val inputDb = if (inputAbs > 0.00001f) 20f * log10(inputAbs) else -100f
        
        // Envelope follower
        val coeff = if (inputAbs > envelope) attackCoeff else releaseCoeff
        envelope = coeff * envelope + (1f - coeff) * inputAbs
        
        val envelopeDb = if (envelope > 0.00001f) 20f * log10(envelope) else -100f
        
        // Gate state
        val targetGain = if (envelopeDb > threshold) {
            1f
        } else {
            10f.pow(range / 20f)
        }
        
        // Smooth gain transition
        val gainCoeff = if (targetGain > gateGain) {
            1f - attackCoeff
        } else {
            1f - releaseCoeff
        }
        gateGain = gateGain + gainCoeff * (targetGain - gateGain)
        
        return input * gateGain
    }
    
    fun reset() {
        envelope = 0f
        gateGain = 1f
    }
}

/**
 * De-Esser - убирает резкие сибилянты
 */
class DeEsserProcessor(
    private var sampleRate: Int = 44100
) {
    private var centerFreq: Float = 6000f
    private var threshold: Float = -20f
    private var reduction: Float = 6f
    
    // Bandpass filter coefficients
    private var b0: Float = 0f
    private var b1: Float = 0f
    private var b2: Float = 0f
    private var a1: Float = 0f
    private var a2: Float = 0f
    
    // Filter state
    private var x1: Float = 0f
    private var x2: Float = 0f
    private var y1: Float = 0f
    private var y2: Float = 0f
    
    // Envelope
    private var envelope: Float = 0f
    
    init {
        updateFilter()
    }
    
    fun setParameters(frequency: Float, threshold: Float, reduction: Float) {
        this.centerFreq = frequency.coerceIn(4000f, 10000f)
        this.threshold = threshold.coerceIn(-40f, 0f)
        this.reduction = reduction.coerceIn(0f, 12f)
        updateFilter()
    }
    
    private fun updateFilter() {
        // Bandpass filter design (Q = 2)
        val omega = 2f * PI.toFloat() * centerFreq / sampleRate
        val alpha = sin(omega) / (2f * 2f) // Q = 2
        
        val cosOmega = cos(omega)
        val a0 = 1f + alpha
        
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = -2f * cosOmega / a0
        a2 = (1f - alpha) / a0
    }
    
    fun process(input: Float): Float {
        // Bandpass filter to detect sibilants
        val filtered = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = filtered
        
        // Envelope follower
        val filteredAbs = abs(filtered)
        envelope = 0.99f * envelope + 0.01f * filteredAbs
        
        val envelopeDb = if (envelope > 0.00001f) 20f * log10(envelope) else -100f
        
        // Gain reduction
        val gainReduction = if (envelopeDb > threshold) {
            val overDb = envelopeDb - threshold
            val reductionDb = min(overDb, reduction)
            10f.pow(-reductionDb / 20f)
        } else {
            1f
        }
        
        return input * gainReduction
    }
    
    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        envelope = 0f
    }
}

/**
 * Sub-Bass Generator - генерирует октаву ниже
 */
class SubBassGenerator(
    private var sampleRate: Int = 44100
) {
    private var amount: Float = 0.5f
    private var frequency: Int = 80
    private var subHarmonicsEnabled: Boolean = true
    private var subAmount: Float = 0.3f
    
    // Lowpass filter for bass extraction
    private var lpX1: Float = 0f
    private var lpX2: Float = 0f
    private var lpY1: Float = 0f
    private var lpY2: Float = 0f
    
    // Octave divider state
    private var lastSample: Float = 0f
    private var phase: Float = 0f
    private var currentSign: Int = 1
    
    fun setParameters(amount: Float, frequency: Int, subHarmonics: Boolean, subAmount: Float) {
        this.amount = amount.coerceIn(0f, 1f)
        this.frequency = frequency.coerceIn(40, 120)
        this.subHarmonicsEnabled = subHarmonics
        this.subAmount = subAmount.coerceIn(0f, 1f)
    }
    
    fun process(input: Float): Float {
        // Lowpass filter to extract bass
        val cutoff = frequency.toFloat()
        val omega = 2f * PI.toFloat() * cutoff / sampleRate
        val alpha = sin(omega) / (2f * 0.707f)
        
        val cosOmega = cos(omega)
        val a0 = 1f + alpha
        val b0 = (1f - cosOmega) / 2f / a0
        val b1 = (1f - cosOmega) / a0
        val b2 = b0
        val a1 = -2f * cosOmega / a0
        val a2 = (1f - alpha) / a0
        
        val bassSignal = b0 * input + b1 * lpX1 + b2 * lpX2 - a1 * lpY1 - a2 * lpY2
        lpX2 = lpX1
        lpX1 = input
        lpY2 = lpY1
        lpY1 = bassSignal
        
        var output = input + bassSignal * amount
        
        // Sub-harmonics generation (octave divider)
        if (subHarmonicsEnabled) {
            // Zero-crossing detection for octave division
            if ((lastSample <= 0 && bassSignal > 0) || (lastSample >= 0 && bassSignal < 0)) {
                currentSign = -currentSign
            }
            lastSample = bassSignal
            
            // Generate sub-octave
            val subOctave = bassSignal * currentSign * 0.5f
            output += subOctave * subAmount
        }
        
        return output
    }
    
    fun reset() {
        lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
        lastSample = 0f
        phase = 0f
        currentSign = 1
    }
}
