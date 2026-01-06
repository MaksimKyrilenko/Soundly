package com.example.soundly.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Высококачественный аудио процессор для time-stretching и pitch-shifting
 * Реализует алгоритм WSOLA (Waveform Similarity Overlap-Add) для минимальных артефактов
 * 
 * Особенности:
 * - Time-stretching с сохранением pitch (как DT в osu!)
 * - Pitch-shifting с изменением тона (как NC в osu!)
 * - Кроссфейд между фрагментами для устранения щелчков
 * - Оптимизирован для real-time обработки (задержка < 100ms)
 */
@UnstableApi
class HighQualityAudioProcessor : AudioProcessor {
    
    companion object {
        // Размер окна анализа (50-100ms при 44100Hz = 2205-4410 samples)
        private const val WINDOW_SIZE_MS = 80
        private const val OVERLAP_RATIO = 0.5f // 50% перекрытие для плавности
        private const val CROSSFADE_SAMPLES = 256 // Кроссфейд для устранения щелчков
        private const val MAX_SEEK_RANGE = 512 // Диапазон поиска лучшего совпадения
    }
    
    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    
    private var speed = 1.0f
    private var pitch = 1.0f
    private var preservePitch = true
    
    private var windowSize = 0
    private var hopSizeInput = 0
    private var hopSizeOutput = 0
    
    private var inputBuffer: FloatArray = FloatArray(0)
    private var outputBuffer: FloatArray = FloatArray(0)
    private var overlapBuffer: FloatArray = FloatArray(0)
    
    private var inputBufferPos = 0
    private var outputBufferPos = 0
    private var outputBufferSize = 0
    
    private var pendingOutputBuffer = ByteBuffer.allocate(0)
    private var isActive = false
    private var inputEnded = false
    
    // Hann window для плавного перехода
    private var hannWindow: FloatArray = FloatArray(0)
    
    fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 3.0f)
        updateParameters()
    }
    
    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.25f, 3.0f)
        updateParameters()
    }
    
    fun setPreservePitch(preserve: Boolean) {
        this.preservePitch = preserve
        updateParameters()
    }
    
    fun setPlaybackParams(params: PlaybackParams) {
        this.speed = params.speed
        this.pitch = params.pitch
        this.preservePitch = params.preservePitch
        updateParameters()
    }
    
    private fun updateParameters() {
        if (inputFormat == AudioFormat.NOT_SET) return
        
        val sampleRate = inputFormat.sampleRate
        windowSize = (sampleRate * WINDOW_SIZE_MS / 1000)
        
        // Для time-stretch: изменяем hop size
        // hopSizeOutput / hopSizeInput = speed
        hopSizeInput = (windowSize * (1 - OVERLAP_RATIO)).toInt()
        hopSizeOutput = (hopSizeInput / speed).toInt().coerceAtLeast(1)
        
        // Создаём Hann window
        hannWindow = FloatArray(windowSize) { i ->
            (0.5 * (1 - kotlin.math.cos(2 * Math.PI * i / (windowSize - 1)))).toFloat()
        }
        
        isActive = speed != 1.0f || pitch != 1.0f
    }
    
    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        
        inputFormat = inputAudioFormat
        
        // Если pitch изменяется, нужно ресемплировать
        val outputSampleRate = if (!preservePitch && pitch != 1.0f) {
            (inputAudioFormat.sampleRate * pitch).toInt()
        } else {
            inputAudioFormat.sampleRate
        }
        
        outputFormat = AudioFormat(
            outputSampleRate,
            inputAudioFormat.channelCount,
            inputAudioFormat.encoding
        )
        
        updateParameters()
        
        // Инициализируем буферы
        val bufferSize = windowSize * 4 * inputAudioFormat.channelCount
        inputBuffer = FloatArray(bufferSize)
        outputBuffer = FloatArray(bufferSize)
        overlapBuffer = FloatArray(windowSize * inputAudioFormat.channelCount)
        
        inputBufferPos = 0
        outputBufferPos = 0
        outputBufferSize = 0
        
        return outputFormat
    }
    
    override fun isActive(): Boolean = isActive
    
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive) {
            pendingOutputBuffer = inputBuffer.duplicate()
            return
        }
        
        val channelCount = inputFormat.channelCount
        val bytesPerSample = if (inputFormat.encoding == C.ENCODING_PCM_16BIT) 2 else 4
        
        // Конвертируем входные данные в float
        while (inputBuffer.hasRemaining()) {
            val sample = if (bytesPerSample == 2) {
                inputBuffer.short.toFloat() / 32768f
            } else {
                inputBuffer.float
            }
            
            if (inputBufferPos < this.inputBuffer.size) {
                this.inputBuffer[inputBufferPos++] = sample
            }
            
            // Когда накопили достаточно данных, обрабатываем
            if (inputBufferPos >= windowSize * channelCount) {
                processWindow()
            }
        }
    }
    
    private fun processWindow() {
        val channelCount = inputFormat.channelCount
        
        // WSOLA: находим лучшую позицию для склейки
        val bestOffset = if (outputBufferSize > 0) {
            findBestOverlapPosition()
        } else {
            0
        }
        
        // Применяем окно и добавляем с перекрытием
        for (i in 0 until windowSize) {
            val windowValue = hannWindow[i]
            
            for (ch in 0 until channelCount) {
                val inputIdx = (bestOffset + i) * channelCount + ch
                val outputIdx = outputBufferPos + i * channelCount + ch
                
                if (inputIdx < inputBufferPos && outputIdx < outputBuffer.size) {
                    val inputSample = inputBuffer[inputIdx] * windowValue
                    
                    // Overlap-add с предыдущим окном
                    if (i < overlapBuffer.size / channelCount) {
                        val overlapIdx = i * channelCount + ch
                        outputBuffer[outputIdx] = overlapBuffer[overlapIdx] + inputSample
                    } else {
                        outputBuffer[outputIdx] = inputSample
                    }
                }
            }
        }
        
        // Сохраняем хвост для следующего перекрытия
        val overlapStart = (windowSize - hopSizeOutput) * channelCount
        for (i in 0 until min(overlapBuffer.size, hopSizeOutput * channelCount)) {
            val srcIdx = overlapStart + i
            if (srcIdx < windowSize * channelCount) {
                overlapBuffer[i] = outputBuffer[outputBufferPos + srcIdx]
            }
        }
        
        outputBufferSize += hopSizeOutput * channelCount
        outputBufferPos += hopSizeOutput * channelCount
        
        // Сдвигаем входной буфер
        val shift = hopSizeInput * channelCount
        if (shift < inputBufferPos) {
            System.arraycopy(inputBuffer, shift, inputBuffer, 0, inputBufferPos - shift)
            inputBufferPos -= shift
        } else {
            inputBufferPos = 0
        }
    }
    
    /**
     * WSOLA: поиск лучшей позиции для минимизации артефактов
     * Ищем позицию с максимальной корреляцией с предыдущим фрагментом
     */
    private fun findBestOverlapPosition(): Int {
        val channelCount = inputFormat.channelCount
        var bestOffset = 0
        var bestCorrelation = Float.MIN_VALUE
        
        val searchRange = min(MAX_SEEK_RANGE, inputBufferPos / channelCount - windowSize)
        if (searchRange <= 0) return 0
        
        for (offset in 0 until searchRange) {
            var correlation = 0f
            val compareLength = min(CROSSFADE_SAMPLES, overlapBuffer.size / channelCount)
            
            for (i in 0 until compareLength) {
                for (ch in 0 until channelCount) {
                    val inputIdx = (offset + i) * channelCount + ch
                    val overlapIdx = i * channelCount + ch
                    
                    if (inputIdx < inputBufferPos && overlapIdx < overlapBuffer.size) {
                        correlation += inputBuffer[inputIdx] * overlapBuffer[overlapIdx]
                    }
                }
            }
            
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestOffset = offset
            }
        }
        
        return bestOffset
    }
    
    override fun queueEndOfStream() {
        inputEnded = true
        // Обрабатываем оставшиеся данные
        if (inputBufferPos > 0) {
            processWindow()
        }
    }
    
    override fun getOutput(): ByteBuffer {
        if (!isActive) {
            val output = pendingOutputBuffer
            pendingOutputBuffer = ByteBuffer.allocate(0)
            return output
        }
        
        if (outputBufferSize == 0) {
            return ByteBuffer.allocate(0).order(ByteOrder.nativeOrder())
        }
        
        val bytesPerSample = if (outputFormat.encoding == C.ENCODING_PCM_16BIT) 2 else 4
        val outputBytes = outputBufferSize * bytesPerSample
        
        val output = ByteBuffer.allocate(outputBytes).order(ByteOrder.nativeOrder())
        
        for (i in 0 until outputBufferSize) {
            val sample = outputBuffer[i].coerceIn(-1f, 1f)
            if (bytesPerSample == 2) {
                output.putShort((sample * 32767).toInt().toShort())
            } else {
                output.putFloat(sample)
            }
        }
        
        output.flip()
        
        // Сбрасываем выходной буфер
        outputBufferPos = 0
        outputBufferSize = 0
        
        return output
    }
    
    override fun isEnded(): Boolean = inputEnded && outputBufferSize == 0
    
    override fun flush() {
        inputBufferPos = 0
        outputBufferPos = 0
        outputBufferSize = 0
        inputEnded = false
        overlapBuffer.fill(0f)
        pendingOutputBuffer = ByteBuffer.allocate(0)
    }
    
    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        speed = 1.0f
        pitch = 1.0f
        isActive = false
    }
}
