package com.example.soundly.data.remote

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeYouTubeService @Inject constructor() {
    
    companion object {
        private const val TAG = "NewPipeYouTubeService"
        
        @Volatile
        private var isInitialized = false
    }
    
    init {
        if (!isInitialized) {
            try {
                // Инициализируем NewPipe с нашим downloader
                NewPipe.init(NewPipeDownloader.getInstance())
                isInitialized = true
                Log.d(TAG, "NewPipe initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NewPipe", e)
            }
        }
    }
    
    suspend fun getVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(url) 
                ?: return@withContext Result.failure(Exception("Неверная ссылка YouTube"))
            
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            
            val videoInfo = VideoInfo(
                id = videoId,
                title = extractor.name ?: "Без названия",
                author = extractor.uploaderName ?: "Неизвестный исполнитель",
                thumbnail = extractor.thumbnails?.maxByOrNull { it.height }?.url 
                    ?: "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                duration = extractor.length,
                durationFormatted = formatDuration(extractor.length)
            )
            
            Result.success(videoInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video info", e)
            Result.failure(Exception("Ошибка получения информации: ${e.message}"))
        }
    }
    
    fun downloadAudio(
        context: Context,
        videoInfo: VideoInfo,
        customTitle: String,
        customArtist: String
    ): Flow<DownloadProgress> = flow {
        try {
            emit(DownloadProgress.Fetching("Получение информации о видео..."))
            
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=${videoInfo.id}")
            extractor.fetchPage()
            
            // Получаем лучший аудио поток
            val audioStreams = extractor.audioStreams
            if (audioStreams.isEmpty()) {
                emit(DownloadProgress.Error("Аудио недоступно для этого видео"))
                return@flow
            }
            
            // Выбираем лучший аудио поток (самый высокий битрейт)
            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate } 
                ?: audioStreams.first()
            
            emit(DownloadProgress.Fetching("Начало загрузки..."))
            
            // Создаем папку для загрузок
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Soundly"
            )
            if (!musicDir.exists()) musicDir.mkdirs()
            
            // Очищаем имя файла
            val safeTitle = customTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeArtist = customArtist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeArtist - $safeTitle"
            
            // Определяем расширение файла
            val extension = when {
                bestAudio.format?.suffix != null -> bestAudio.format?.suffix
                bestAudio.format?.mimeType?.contains("mp4") == true -> "m4a"
                bestAudio.format?.mimeType?.contains("webm") == true -> "webm"
                else -> "m4a"
            }
            
            val outputFile = File(musicDir, "$fileName.$extension")
            
            emit(DownloadProgress.Downloading(0f))
            
            // Скачиваем файл
            withContext(Dispatchers.IO) {
                val connection = URL(bestAudio.url).openConnection()
                val contentLength = connection.contentLength
                
                connection.getInputStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            if (contentLength > 0) {
                                val progress = (totalBytesRead.toFloat() / contentLength)
                                emit(DownloadProgress.Downloading(progress))
                            }
                        }
                    }
                }
            }
            
            emit(DownloadProgress.Converting("Сохранение..."))
            
            // Скачиваем обложку
            var localThumbnailPath: String? = null
            try {
                val thumbnailDir = File(musicDir, ".thumbnails")
                if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
                
                val thumbnailFile = File(thumbnailDir, "$fileName.jpg")
                if (!thumbnailFile.exists() && videoInfo.thumbnail.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        URL(videoInfo.thumbnail).openStream().use { input ->
                            thumbnailFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                if (thumbnailFile.exists()) {
                    localThumbnailPath = thumbnailFile.absolutePath
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download thumbnail: ${e.message}")
            }
            
            // Сканируем файл
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                arrayOf("audio/*"),
                null
            )
            
            emit(DownloadProgress.Success(outputFile.absolutePath, localThumbnailPath))
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            emit(DownloadProgress.Error("Ошибка загрузки: ${e.message}"))
        }
    }
    
    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/v/|youtube\\.com/shorts/)([a-zA-Z0-9_-]{11})"),
            Regex("youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})"),
            Regex("^([a-zA-Z0-9_-]{11})$")
        )
        
        for (pattern in patterns) {
            pattern.find(url)?.let { match ->
                return match.groupValues[1]
            }
        }
        return null
    }
    
    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
}
