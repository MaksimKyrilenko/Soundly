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
                return@withContext ExportResult.Error("Ошибка обработки аудио")
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
                artworkUri = sourceTrack.artworkUri,
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
            android.util.Log.d(TAG, "Processing with speed=${settings.speed}")
            
            val resampleFactor = settings.speed
            
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
                        
                        // Объединяем с остатком от предыдущего чанка
                        val combinedData = ShortArray(resampleBuffer.size + pcmData.size)
                        System.arraycopy(resampleBuffer, 0, combinedData, 0, resampleBuffer.size)
                        System.arraycopy(pcmData, 0, combinedData, resampleBuffer.size, pcmData.size)
                        
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
                sampleRate,
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
                            presentationTimeUs = (sampleCount * 1000000L) / sampleRate
                            
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
