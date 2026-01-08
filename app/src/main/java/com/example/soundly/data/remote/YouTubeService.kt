package com.example.soundly.data.remote

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.example.soundly.SoundlyApp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VideoInfo(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val thumbnail: String = "",
    val duration: Long = 0,
    val durationFormatted: String = ""
)

sealed class DownloadProgress {
    data class Fetching(val message: String) : DownloadProgress()
    data class Downloading(val progress: Float) : DownloadProgress()
    data class Converting(val message: String) : DownloadProgress()
    data class Success(val filePath: String, val thumbnailPath: String? = null) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}

@Singleton
class YouTubeService @Inject constructor() {
    
    companion object {
        private const val TAG = "YouTubeService"
    }
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            })
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    fun extractVideoId(url: String): String? {
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
    
    suspend fun getVideoInfo(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(url) ?: return@withContext Result.failure(Exception("Неверная ссылка YouTube"))
            
            // Используем noembed.com для быстрого получения базовой информации
            val response = client.get("https://noembed.com/embed?url=https://www.youtube.com/watch?v=$videoId")
            val responseText = response.bodyAsText()
            
            val noembedData = json.decodeFromString<NoembedResponse>(responseText)
            
            if (noembedData.error != null) {
                return@withContext Result.failure(Exception(noembedData.error))
            }
            
            val videoInfo = VideoInfo(
                id = videoId,
                title = noembedData.title ?: "Без названия",
                author = noembedData.authorName ?: "Неизвестный исполнитель",
                thumbnail = noembedData.thumbnailUrl ?: "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                duration = 0,
                durationFormatted = ""
            )
            
            Result.success(videoInfo)
        } catch (e: Exception) {
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
            emit(DownloadProgress.Fetching("Подготовка загрузчика..."))
            
            // Ждём инициализации YoutubeDL (максимум 30 секунд)
            var waitTime = 0
            while (!SoundlyApp.isYoutubeDLReady && SoundlyApp.initError == null && waitTime < 30000) {
                kotlinx.coroutines.delay(500)
                waitTime += 500
                if (waitTime % 5000 == 0) {
                    emit(DownloadProgress.Fetching("Инициализация... ${waitTime / 1000}с"))
                }
            }
            
            if (!SoundlyApp.isYoutubeDLReady) {
                val error = SoundlyApp.initError ?: "Таймаут инициализации"
                Log.e(TAG, "YoutubeDL not ready: $error")
                emit(DownloadProgress.Error("Загрузчик недоступен: $error"))
                return@flow
            }
            
            emit(DownloadProgress.Fetching("Получение аудио..."))
            
            // Используем приватную папку приложения (не требует разрешений)
            val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Downloads")
            if (!musicDir.exists()) musicDir.mkdirs()
            
            // Очищаем имя файла
            val safeTitle = customTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeArtist = customArtist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$safeArtist - $safeTitle"
            
            val videoUrl = "https://www.youtube.com/watch?v=${videoInfo.id}"
            
            val request = YoutubeDLRequest(videoUrl)
            // Скачиваем лучший аудио формат без конвертации
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio")
            request.addOption("-o", "${musicDir.absolutePath}/$fileName.%(ext)s")
            request.addOption("--no-playlist")
            // Не используем post-processing чтобы избежать проблем с FFmpeg
            
            emit(DownloadProgress.Downloading(0f))
            
            var lastProgress = 0f
            var downloadError: String? = null
            
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                        val currentProgress = progress / 100f
                        if (currentProgress > lastProgress) {
                            lastProgress = currentProgress
                        }
                        Log.d(TAG, "Progress: $progress%, ETA: ${etaInSeconds}s, Line: $line")
                    }
                }
            } catch (e: Exception) {
                // Сохраняем ошибку, но проверим файлы
                downloadError = e.message
                Log.w(TAG, "Download exception (checking files anyway): ${e.message}")
            }
            
            emit(DownloadProgress.Downloading(1f))
            emit(DownloadProgress.Converting("Сохранение..."))
            
            // Ищем скачанный файл (может быть webm, m4a, mp3 и т.д.)
            val possibleFiles = musicDir.listFiles { file -> 
                file.name.startsWith(fileName) && file.isFile && file.length() > 0
            }
            
            if (!possibleFiles.isNullOrEmpty()) {
                val downloadedFile = possibleFiles.first()
                Log.d(TAG, "Found downloaded file: ${downloadedFile.name}, size: ${downloadedFile.length()}")
                
                // Копируем в публичную папку Music/Soundly
                val publicMusicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Soundly"
                )
                if (!publicMusicDir.exists()) publicMusicDir.mkdirs()
                
                val publicFile = File(publicMusicDir, downloadedFile.name)
                try {
                    downloadedFile.copyTo(publicFile, overwrite = true)
                    downloadedFile.delete()
                    
                    // Скачиваем обложку локально - сохраняем с именем аудиофайла
                    var localThumbnailPath: String? = null
                    try {
                        val thumbnailDir = File(publicMusicDir, ".thumbnails")
                        if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
                        
                        // Используем имя аудиофайла (без расширения) для обложки
                        val audioFileName = publicFile.name.substringBeforeLast(".")
                        val thumbnailFile = File(thumbnailDir, "$audioFileName.jpg")
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
                    
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(publicFile.absolutePath),
                        arrayOf("audio/*"),
                        null
                    )
                    emit(DownloadProgress.Success(publicFile.absolutePath, localThumbnailPath))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy to public dir: ${e.message}")
                    emit(DownloadProgress.Success(downloadedFile.absolutePath, null))
                }
            } else if (downloadError != null) {
                emit(DownloadProgress.Error("Ошибка: $downloadError"))
            } else {
                emit(DownloadProgress.Error("Файл не найден после скачивания"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            emit(DownloadProgress.Error("Ошибка: ${e.message}"))
        }
    }
    
    fun getWebDownloadUrl(videoId: String): String {
        return "https://y2mate.nu/youtube-mp3/$videoId"
    }
}

@Serializable
data class NoembedResponse(
    val title: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val error: String? = null
)
