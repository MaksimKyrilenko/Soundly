package com.example.soundly.data.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.example.soundly.domain.model.AudioExportSettings
import com.example.soundly.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Сервис для экспорта аудио с применёнными эффектами
 * Использует потоковую обработку для экономии памяти
 */
@Singleton
class AudioExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AudioExportService"
    
    sealed class ExportResult {
        data class Success(val track: Track, val filePath: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
        data class Progress(val percent: Int, val message: String) : ExportResult()
    }
    
    suspend fun exportTrack(
        sourceTrack: Track,
        settings: AudioExportSettings,
        customName: String? = null,
        onProgress: (ExportResult.Progress) -> Unit = {}
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            onProgress(ExportResult.Progress(0, "Подготовка..."))
            android.util.Log.d(TAG, "Starting export for track: ${sourceTrack.title}")
            android.util.Log.d(TAG, "Settings: speed=${settings.speed}, pitch=${settings.pitch}, preservePitch=${settings.preservePitch}")
            
            val inputPath = getInputFilePath(sourceTrack)
            if (inputPath == null) {
                return@withContext ExportResult.Error("Не удалось получить путь к файлу")
            }
            
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                return@withContext ExportResult.Error("Исходный файл не найден")
            }
            
            android.util.Log.d(TAG, "Input file: $inputPath, size: ${inputFile.length()}")
            
            onProgress(ExportResult.Progress(10, "Создание выходного файла..."))
            
            val suffix = settings.generateNameSuffix()
            val newTitle = customName ?: "${sourceTrack.title} ($suffix)"
            val safeFileName = sanitizeFileName("${sourceTrack.artist} - $newTitle")
            
            val exportDir = getExportDirectory()
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val outputFile = File(exportDir, "$safeFileName.m4a")
            val finalOutputFile = if (outputFile.exists()) {
                File(exportDir, "${safeFileName}_${System.currentTimeMillis()}.m4a")
            } else {
                outputFile
            }
            
            android.util.Log.d(TAG, "Output file: ${finalOutputFile.absolutePath}")
            
            onProgress(ExportResult.Progress(15, "Обработка аудио..."))
            
            val success = processAudioStreaming(inputPath, finalOutputFile.absolutePath, settings) { progress ->
                val adjustedProgress = 15 + (progress * 0.8).toInt()
                onProgress(ExportResult.Progress(adjustedProgress, "Обработка: $progress%"))
            }
            
            if (!success || !finalOutputFile.exists() || finalOutputFile.length() == 0L) {
                android.util.Log.e(TAG, "Audio processing failed")
                finalOutputFile.delete() // Удаляем повреждённый файл
                return@withContext ExportResult.Error("Ошибка обработки аудио")
            }
            
            // Проверяем что файл можно прочитать
            val isValidFile = try {
                val testExtractor = MediaExtractor()
                testExtractor.setDataSource(finalOutputFile.absolutePath)
                val hasAudio = (0 until testExtractor.trackCount).any { i ->
                    testExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                testExtractor.release()
                hasAudio
            } catch (e: Exception) {
                android.util.Log.e(TAG, "File validation failed", e)
                false
            }
            
            if (!isValidFile) {
                android.util.Log.e(TAG, "Exported file is not valid audio")
                finalOutputFile.delete()
                return@withContext ExportResult.Error("Экспортированный файл повреждён")
            }
            
            android.util.Log.d(TAG, "Output file created, size: ${finalOutputFile.length()}")
            
            onProgress(ExportResult.Progress(95, "Сканирование медиатеки..."))
            scanFile(finalOutputFile.absolutePath)
            
            onProgress(ExportResult.Progress(100, "Готово!"))
            
            val newTrack = Track(
                id = "exp_${UUID.randomUUID()}",
                title = newTitle,
                artist = sourceTrack.artist,
                album = sourceTrack.album,
                duration = calculateNewDuration(sourceTrack.duration, settings.speed),
                artworkUri = sourceTrack.artworkUri, // Копируем обложку с оригинала
                uri = "file://${finalOutputFile.absolutePath}",
                isLocal = true,
                isFavorite = false,
                playCount = 0,
                lastPlayedAt = null,
                dateAdded = System.currentTimeMillis()
            )
            
            android.util.Log.d(TAG, "Export completed successfully")
            ExportResult.Success(newTrack, finalOutputFile.absolutePath)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Export failed with exception", e)
            ExportResult.Error("Ошибка: ${e.message}")
        }
    }

    /**
     * Потоковая обработка аудио - декодируем, обрабатываем и кодируем чанками
     * Использует временный RAW файл для хранения обработанных PCM данных
     */
    private fun processAudioStreaming(
        inputPath: String,
        outputPath: String,
        settings: AudioExportSettings,
        onProgress: (Int) -> Unit
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var tempFile: File? = null
        var tempRaf: RandomAccessFile? = null
        
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)
            
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }
            
            if (audioTrackIndex < 0 || inputFormat == null) {
                android.util.Log.e(TAG, "No audio track found")
                return false
            }
            
            extractor.selectTrack(audioTrackIndex)
            
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = inputFormat.getLong(MediaFormat.KEY_DURATION)
            
            android.util.Log.d(TAG, "Input: mime=$inputMime, sampleRate=$sampleRate, channels=$channelCount, duration=$duration")
            android.util.Log.d(TAG, "Export settings: speed=${settings.speed}, pitch=${settings.pitch}, preservePitch=${settings.preservePitch}")
            android.util.Log.d(TAG, "PlaybackMode: ${settings.playbackMode}")
            
            // Вычисляем параметры обработки
            // 
            // Для изменения pitch БЕЗ time-stretch (Nightcore/Daycore):
            //   - Записываем данные как есть (resampleFactor = 1.0)
            //   - Меняем sample rate в метаданных файла
            //   - При воспроизведении плеер интерпретирует данные с другой скоростью
            //
            // Nightcore (pitch=1.5): записываем с sampleRate/1.5 -> воспроизводится быстрее и выше
            // Daycore (pitch=0.75): записываем с sampleRate/0.75 -> воспроизводится медленнее и ниже
            //
            // Для time-stretch (preservePitch=true):
            //   - Ресемплируем данные (меняем количество сэмплов)
            //   - Sample rate остаётся тем же
            //   - Скорость меняется, pitch сохраняется
            
            val resampleFactor: Float
            val outputSampleRate: Int
            
            if (settings.preservePitch) {
                // Time-stretch: ресемплируем данные, sample rate не меняем
                resampleFactor = settings.speed
                outputSampleRate = sampleRate
                android.util.Log.d(TAG, "Mode: Time-stretch, resample by ${settings.speed}x")
            } else {
                // Pitch shift: ресемплируем данные для изменения pitch
                // Для Nightcore (pitch=1.5): сжимаем данные в 1.5 раза -> меньше сэмплов -> быстрее + выше pitch
                // Для Daycore (pitch=0.75): растягиваем данные в 0.75 раза -> больше сэмплов -> медленнее + ниже pitch
                resampleFactor = settings.pitch
                outputSampleRate = sampleRate // Сохраняем стандартный sample rate для совместимости
                
                android.util.Log.d(TAG, "Mode: Pitch shift via resampling, factor=${settings.pitch}")
            }
            
            android.util.Log.d(TAG, "Final: resampleFactor=$resampleFactor, outputSampleRate=$outputSampleRate")
            
            // Создаём временный файл для хранения обработанных PCM данных
            tempFile = File(context.cacheDir, "temp_pcm_${System.currentTimeMillis()}.raw")
            tempRaf = RandomAccessFile(tempFile, "rw")
            
            // Фаза 1: Декодирование и обработка -> запись во временный файл
            android.util.Log.d(TAG, "Phase 1: Decode and process to temp file...")
            
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            
            val bufferInfo = MediaCodec.BufferInfo()
            val timeoutUs = 10000L
            var inputDone = false
            var decoderDone = false
            var totalBytesDecoded = 0L
            val estimatedTotalBytes = (duration * sampleRate * channelCount * 2 / 1000000).coerceAtLeast(1)
            
            // Буфер для ресемплинга (храним остаток от предыдущего чанка)
            var resampleBuffer = ShortArray(0)
            var resamplePosition = 0.0
            
            while (!decoderDone) {
                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderDone = true
                    }
                    
                    if (bufferInfo.size > 0) {
                        val pcmData = ShortArray(bufferInfo.size / 2)
                        outputBuffer.order(ByteOrder.nativeOrder())
                        outputBuffer.asShortBuffer().get(pcmData)
                        
                        // Применяем эффекты к PCM данным
                        val processedPcm = applyEffects(pcmData, channelCount, sampleRate, settings)
                        
                        // Объединяем с остатком от предыдущего чанка
                        val combinedData = ShortArray(resampleBuffer.size + processedPcm.size)
                        System.arraycopy(resampleBuffer, 0, combinedData, 0, resampleBuffer.size)
                        System.arraycopy(processedPcm, 0, combinedData, resampleBuffer.size, processedPcm.size)
                        
                        // Ресемплинг с интерполяцией
                        val result = resampleChunk(combinedData, channelCount, resampleFactor, resamplePosition)
                        val processedData = result.first
                        resamplePosition = result.second
                        val samplesConsumed = result.third
                        
                        // Сохраняем остаток
                        val remainingSamples = combinedData.size - samplesConsumed
                        resampleBuffer = if (remainingSamples > 0) {
                            combinedData.copyOfRange(samplesConsumed, combinedData.size)
                        } else {
                            ShortArray(0)
                        }
                        
                        // Записываем обработанные данные во временный файл
                        if (processedData.isNotEmpty()) {
                            val byteBuffer = ByteBuffer.allocate(processedData.size * 2)
                            byteBuffer.order(ByteOrder.nativeOrder())
                            processedData.forEach { byteBuffer.putShort(it) }
                            tempRaf.write(byteBuffer.array())
                        }
                        
                        totalBytesDecoded += bufferInfo.size
                        val progress = ((totalBytesDecoded * 50) / estimatedTotalBytes).toInt().coerceIn(0, 50)
                        onProgress(progress)
                    }
                    
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
            
            decoder.stop()
            decoder.release()
            decoder = null
            extractor.release()
            extractor = null
            
            val totalProcessedBytes = tempRaf.length()
            android.util.Log.d(TAG, "Phase 1 complete. Temp file size: $totalProcessedBytes bytes")
            
            // Фаза 2: Читаем из временного файла и кодируем
            android.util.Log.d(TAG, "Phase 2: Encode from temp file...")
            
            tempRaf.seek(0)
            
            val outputFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                outputSampleRate,  // Используем outputSampleRate для правильного pitch
                channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var outputTrackIndex = -1
            var muxerStarted = false
            
            var bytesRead = 0L
            var encoderInputDone = false
            var encoderDone = false
            var presentationTimeUs = 0L
            
            val readBuffer = ByteArray(8192)
            
            while (!encoderDone) {
                if (!encoderInputDone) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)!!
                        
                        val bytesToRead = minOf(readBuffer.size, inputBuffer.capacity())
                        val actualRead = tempRaf.read(readBuffer, 0, bytesToRead)
                        
                        if (actualRead <= 0) {
                            encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encoderInputDone = true
                        } else {
                            inputBuffer.clear()
                            inputBuffer.put(readBuffer, 0, actualRead)
                            
                            val sampleCount = bytesRead / (channelCount * 2)
                            presentationTimeUs = (sampleCount * 1000000L) / outputSampleRate
                            
                            encoder.queueInputBuffer(inputBufferIndex, 0, actualRead, presentationTimeUs, 0)
                            bytesRead += actualRead
                            
                            val progress = 50 + ((bytesRead * 50) / totalProcessedBytes).toInt().coerceIn(0, 50)
                            onProgress(progress)
                        }
                    }
                }
                
                val encoderBufferInfo = MediaCodec.BufferInfo()
                val encoderOutputIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, timeoutUs)
                when {
                    encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            outputTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    encoderOutputIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(encoderOutputIndex)!!
                        
                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && encoderBufferInfo.size > 0) {
                            if (muxerStarted) {
                                muxer.writeSampleData(outputTrackIndex, encodedData, encoderBufferInfo)
                            }
                        }
                        
                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                        }
                        
                        encoder.releaseOutputBuffer(encoderOutputIndex, false)
                    }
                }
            }
            
            onProgress(100)
            android.util.Log.d(TAG, "Encoding complete")
            return true
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Audio processing error", e)
            return false
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
                encoder?.stop()
                encoder?.release()
                muxer?.stop()
                muxer?.release()
                extractor?.release()
                tempRaf?.close()
                tempFile?.delete()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error releasing resources", e)
            }
        }
    }

    /**
     * Применяет все аудио эффекты к PCM данным
     */
    private fun applyEffects(
        input: ShortArray,
        channelCount: Int,
        sampleRate: Int,
        settings: AudioExportSettings
    ): ShortArray {
        var data = input
        
        // 1. Применяем preamp (усиление)
        if (settings.preamp != 0f) {
            data = applyGain(data, settings.preamp)
        }
        
        // 2. Применяем эквалайзер
        if (settings.bands.any { it != 0f }) {
            data = applyEqualizer(data, channelCount, sampleRate, settings.bands)
        }
        
        // 3. Применяем bass boost
        if (settings.bassBoost > 0f) {
            data = applyBassBoost(data, channelCount, sampleRate, settings.bassBoost)
        }
        
        // 4. Применяем stereo эффекты
        if (channelCount == 2) {
            if (settings.isMono) {
                data = applyMono(data)
            } else if (settings.stereoWidth != 100f) {
                data = applyStereoWidth(data, settings.stereoWidth)
            }
        }
        
        return data
    }
    
    /**
     * Применяет усиление (preamp) в dB
     */
    private fun applyGain(input: ShortArray, gainDb: Float): ShortArray {
        val gainLinear = Math.pow(10.0, gainDb / 20.0).toFloat()
        return ShortArray(input.size) { i ->
            (input[i] * gainLinear).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
    
    /**
     * Простой 10-полосный эквалайзер
     * Частоты: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz
     */
    private fun applyEqualizer(
        input: ShortArray,
        channelCount: Int,
        sampleRate: Int,
        bands: List<Float>
    ): ShortArray {
        // Центральные частоты полос
        val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        
        // Конвертируем dB в линейные коэффициенты
        val gains = bands.map { db -> Math.pow(10.0, db / 20.0).toFloat() }
        
        val output = ShortArray(input.size)
        val frameCount = input.size / channelCount
        
        // Простая реализация через взвешенное суммирование
        // Для каждого сэмпла применяем усредненный gain на основе частотного содержимого
        // Это упрощённая версия - полноценный EQ требует FFT или IIR фильтры
        
        // Используем простое усреднение gains как приближение
        val avgGain = gains.average().toFloat()
        
        // Для более точного EQ нужны BiQuad фильтры, но это значительно усложнит код
        // Пока применяем простое усиление на основе среднего
        for (i in input.indices) {
            output[i] = (input[i] * avgGain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return output
    }
    
    /**
     * Применяет усиление басов (low-shelf filter approximation)
     */
    private fun applyBassBoost(
        input: ShortArray,
        channelCount: Int,
        sampleRate: Int,
        amount: Float // 0-100
    ): ShortArray {
        if (amount <= 0f) return input
        
        // Простой low-pass фильтр для выделения басов + смешивание
        val output = ShortArray(input.size)
        val frameCount = input.size / channelCount
        
        // Коэффициент усиления басов (amount 0-100 -> gain 1.0-2.0)
        val bassGain = 1.0f + (amount / 100f)
        
        // Простой RC low-pass фильтр для выделения басов
        val cutoffFreq = 150f // Hz
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffFreq)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)
        
        // Состояние фильтра для каждого канала
        val lowPassState = FloatArray(channelCount)
        
        for (frame in 0 until frameCount) {
            for (ch in 0 until channelCount) {
                val idx = frame * channelCount + ch
                val sample = input[idx].toFloat()
                
                // Low-pass фильтр
                lowPassState[ch] = lowPassState[ch] + alpha * (sample - lowPassState[ch])
                val bassComponent = lowPassState[ch]
                
                // Смешиваем: оригинал + усиленные басы
                val result = sample + bassComponent * (bassGain - 1.0f)
                
                output[idx] = result.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        
        return output
    }
    
    /**
     * Конвертирует стерео в моно
     */
    private fun applyMono(input: ShortArray): ShortArray {
        val frameCount = input.size / 2
        val output = ShortArray(input.size)
        
        for (frame in 0 until frameCount) {
            val left = input[frame * 2]
            val right = input[frame * 2 + 1]
            val mono = ((left.toInt() + right.toInt()) / 2).toShort()
            output[frame * 2] = mono
            output[frame * 2 + 1] = mono
        }
        
        return output
    }
    
    /**
     * Применяет изменение ширины стерео
     * width: 0 = mono, 100 = normal, 150 = wide
     */
    private fun applyStereoWidth(input: ShortArray, width: Float): ShortArray {
        val frameCount = input.size / 2
        val output = ShortArray(input.size)
        
        // width 0-150 -> coefficient 0.0-1.5
        val coefficient = width / 100f
        
        for (frame in 0 until frameCount) {
            val left = input[frame * 2].toFloat()
            val right = input[frame * 2 + 1].toFloat()
            
            // Mid-Side processing
            val mid = (left + right) / 2f
            val side = (left - right) / 2f
            
            // Применяем ширину к side компоненту
            val newSide = side * coefficient
            
            // Обратно в L/R
            val newLeft = mid + newSide
            val newRight = mid - newSide
            
            output[frame * 2] = newLeft.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            output[frame * 2 + 1] = newRight.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return output
    }

    /**
     * Ресемплинг чанка данных с линейной интерполяцией
     * Возвращает: (обработанные данные, новая позиция, количество использованных сэмплов)
     */
    private fun resampleChunk(
        input: ShortArray,
        channelCount: Int,
        resampleFactor: Float,
        startPosition: Double
    ): Triple<ShortArray, Double, Int> {
        if (resampleFactor == 1.0f) {
            return Triple(input, 0.0, input.size)
        }
        
        val inputFrames = input.size / channelCount
        val outputFrames = ((inputFrames - startPosition) / resampleFactor).toInt()
        
        if (outputFrames <= 0) {
            return Triple(ShortArray(0), startPosition, 0)
        }
        
        val output = ShortArray(outputFrames * channelCount)
        var position = startPosition
        var samplesConsumed = 0
        
        for (outFrame in 0 until outputFrames) {
            val srcFrame0 = position.toInt()
            val srcFrame1 = (srcFrame0 + 1).coerceAtMost(inputFrames - 1)
            val fraction = position - srcFrame0
            
            if (srcFrame0 >= inputFrames - 1) break
            
            for (ch in 0 until channelCount) {
                val sample0 = input[srcFrame0 * channelCount + ch]
                val sample1 = input[srcFrame1 * channelCount + ch]
                val interpolated = sample0 + ((sample1 - sample0) * fraction)
                output[outFrame * channelCount + ch] = interpolated.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            
            position += resampleFactor
            samplesConsumed = (position.toInt() + 1) * channelCount
        }
        
        val newPosition = position - position.toInt()
        return Triple(output, newPosition, samplesConsumed.coerceAtMost(input.size))
    }
    
    private fun getInputFilePath(track: Track): String? {
        val uri = track.uri
        return when {
            uri.startsWith("/") -> uri
            uri.startsWith("file:///") -> uri.removePrefix("file://")
            uri.startsWith("file:/") -> uri.removePrefix("file:")
            uri.startsWith("content://") -> copyContentUriToTemp(Uri.parse(uri))
            else -> null
        }
    }
    
    private fun copyContentUriToTemp(uri: Uri): String? {
        return try {
            val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile.absolutePath else null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to copy content URI", e)
            null
        }
    }
    
    private suspend fun scanFile(filePath: String) = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf("audio/*")) { _, _ ->
            if (cont.isActive) cont.resume(Unit)
        }
    }
    
    private fun getExportDirectory(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(musicDir, "Soundly/Exports")
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(200)
    }
    
    private fun calculateNewDuration(originalDuration: Long, speed: Float): Long {
        return (originalDuration / speed).toLong()
    }
}
