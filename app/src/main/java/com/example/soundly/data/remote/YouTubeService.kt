package com.example.soundly.data.remote

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.example.soundly.SoundlyApp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
class YouTubeService @Inject constructor(
    private val newPipeService: NewPipeYouTubeService,
    @ApplicationContext private val context: Context
) {
    
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
            val videoId = extractVideoId(url) 
                ?: return@withContext Result.failure(Exception("Неверная ссылка YouTube"))
            
            if (!SoundlyApp.isYoutubeDLReady) {
                return@withContext Result.failure(Exception("Загрузчик YouTube еще не готов. Подождите несколько секунд."))
            }
            
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--no-playlist")
            request.addOption("--extractor-args", "youtube:player_client=web")
            
            val response = YoutubeDL.getInstance().execute(request)
            val jsonResponse = response.out
            
            if (jsonResponse.isBlank()) {
                return@withContext Result.failure(Exception("yt-dlp вернул пустой ответ"))
            }
            
            val jsonObject = json.parseToJsonElement(jsonResponse).jsonObject
            
            val title = jsonObject["title"]?.toString()?.trim('"') ?: "Без названия"
            val uploader = jsonObject["uploader"]?.toString()?.trim('"') ?: "Неизвестный исполнитель"
            val thumbnail = jsonObject["thumbnail"]?.toString()?.trim('"') 
                ?: "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
            val duration = jsonObject["duration"]?.toString()?.toLongOrNull() ?: 0L
            
            val videoInfo = VideoInfo(
                id = videoId,
                title = title,
                author = uploader,
                thumbnail = thumbnail,
                duration = duration,
                durationFormatted = formatDuration(duration)
            )
            
            Result.success(videoInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video info", e)
            Result.failure(Exception("Не удалось загрузить информацию: ${e.message}"))
        }
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

    
    fun downloadAudio(
        context: Context,
        videoInfo: VideoInfo,
        customTitle: String,
        customArtist: String
    ): Flow<DownloadProgress> = kotlinx.coroutines.flow.callbackFlow {
        try {
            if (!SoundlyApp.isYoutubeDLReady) {
                trySend(DownloadProgress.Error("Загрузчик YouTube еще не готов"))
                close()
                return@callbackFlow
            }
            
            trySend(DownloadProgress.Fetching("Подготовка к загрузке..."))
            
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
            
            val outputFile = File(musicDir, "$fileName.%(ext)s")
            
            trySend(DownloadProgress.Fetching("Начало загрузки..."))
            
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${videoInfo.id}")
            request.addOption("-x") // Извлечь аудио
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0") // Лучшее качество
            request.addOption("-o", outputFile.absolutePath)
            request.addOption("--no-playlist")
            request.addOption("--extractor-args", "youtube:player_client=web")
            
            // Запускаем в отдельном потоке чтобы не блокировать UI
            val downloadThread = Thread {
                try {
                    var lastProgress = 0f
                    YoutubeDL.getInstance().execute(request) { progress, _, line ->
                        if (progress > lastProgress) {
                            lastProgress = progress
                            trySend(DownloadProgress.Downloading(progress / 100f))
                        }
                    }
                    
                    trySend(DownloadProgress.Converting("Сохранение..."))
                    
                    // Находим скачанный файл
                    val downloadedFile = musicDir.listFiles()?.find { 
                        it.name.startsWith("$safeArtist - $safeTitle") && 
                        (it.extension == "mp3" || it.extension == "m4a" || it.extension == "webm")
                    }
                    
                    if (downloadedFile == null || !downloadedFile.exists()) {
                        trySend(DownloadProgress.Error("Файл не найден после загрузки"))
                        close()
                        return@Thread
                    }
                    
                    // Скачиваем обложку
                    var localThumbnailPath: String? = null
                    try {
                        val thumbnailDir = File(musicDir, ".thumbnails")
                        if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
                        
                        val thumbnailFile = File(thumbnailDir, "$fileName.jpg")
                        if (!thumbnailFile.exists() && videoInfo.thumbnail.isNotBlank()) {
                            URL(videoInfo.thumbnail).openStream().use { input ->
                                thumbnailFile.outputStream().use { output ->
                                    input.copyTo(output)
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
                        arrayOf(downloadedFile.absolutePath),
                        arrayOf("audio/*"),
                        null
                    )
                    
                    trySend(DownloadProgress.Success(downloadedFile.absolutePath, localThumbnailPath))
                    close()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Download error in thread", e)
                    trySend(DownloadProgress.Error("Ошибка загрузки: ${e.message}"))
                    close()
                }
            }
            
            downloadThread.start()
            
            // Ждем завершения потока
            awaitClose {
                if (downloadThread.isAlive) {
                    downloadThread.interrupt()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            trySend(DownloadProgress.Error("Ошибка загрузки: ${e.message}"))
            close()
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
