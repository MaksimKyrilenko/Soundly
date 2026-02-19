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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
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
        private const val DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L // 10 минут
        
        // Разные конфигурации для fallback
        private val DOWNLOAD_CONFIGS = listOf(
            // Без указания player_client - пусть yt-dlp сам выберет
            DownloadConfig(null, "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best"),
            // Попытка с web client и менее строгим форматом
            DownloadConfig("web", "bestaudio"),
            // Попытка скачать любой формат
            DownloadConfig(null, "best[height<=480]"), // Низкое качество видео как fallback
        )
    }
    
    private data class DownloadConfig(
        val playerClient: String?, // null = не указывать player_client
        val format: String
    )
    
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                isLenient = true
            })
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    // Используем пул потоков для yt-dlp вместо создания новых потоков каждый раз
    private val executorService = Executors.newFixedThreadPool(2)
    
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
            
            Log.d(TAG, "Getting video info for: $videoId")
            
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            request.addOption("--no-playlist")
            request.addOption("--extractor-args", "youtube:player_client=web")
            request.addOption("--socket-timeout", "30")
            
            // Запускаем в executor чтобы не блокировать
            val result = suspendCancellableCoroutine<Result<VideoInfo>> { continuation ->
                val future = executorService.submit {
                    try {
                        val response = YoutubeDL.getInstance().execute(request)
                        val jsonResponse = response.out
                        
                        if (jsonResponse.isBlank()) {
                            continuation.resume(Result.failure(Exception("yt-dlp вернул пустой ответ")))
                            return@submit
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
                        
                        Log.d(TAG, "Video info retrieved: $title by $uploader")
                        continuation.resume(Result.success(videoInfo))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get video info", e)
                        continuation.resume(Result.failure(Exception("Не удалось загрузить информацию: ${e.message}")))
                    }
                }
                
                continuation.invokeOnCancellation {
                    future.cancel(true)
                }
            }
            
            result
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
    ): Flow<DownloadProgress> = callbackFlow {
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
            
            Log.d(TAG, "Starting download for: ${videoInfo.id}")
            Log.d(TAG, "Output path: ${outputFile.absolutePath}")
            
            // Пробуем разные конфигурации если первая не сработает
            var lastError: Exception? = null
            var downloadSuccess = false
            
            // Используем ExecutorService вместо создания нового потока
            val future = executorService.submit {
                for ((index, config) in DOWNLOAD_CONFIGS.withIndex()) {
                    try {
                        val clientInfo = config.playerClient ?: "auto"
                        Log.d(TAG, "Trying player_client=$clientInfo, format=${config.format} (attempt ${index + 1}/${DOWNLOAD_CONFIGS.size})")
                        
                        if (index > 0) {
                            trySend(DownloadProgress.Fetching("Попытка ${index + 1}..."))
                        }
                        
                        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${videoInfo.id}")
                        
                        // Скачиваем аудио БЕЗ конвертации (ffmpeg недоступен)
                        request.addOption("-f", config.format) // Формат аудио
                        request.addOption("-o", outputFile.absolutePath)
                        request.addOption("--no-playlist")
                        
                        // Добавляем player_client только если указан
                        if (config.playerClient != null) {
                            request.addOption("--extractor-args", "youtube:player_client=${config.playerClient}")
                        }
                        
                        request.addOption("--socket-timeout", "30")
                        request.addOption("--retries", "2")
                        request.addOption("--no-check-certificate")
                        
                        var lastProgress = 0f
                        var lastLogTime = System.currentTimeMillis()
                        
                        YoutubeDL.getInstance().execute(request) { progress, _, line ->
                            // Логируем прогресс каждые 2 секунды
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime > 2000) {
                                Log.d(TAG, "Download progress: $progress% - $line")
                                lastLogTime = now
                            }
                            
                            if (progress > lastProgress) {
                                lastProgress = progress
                                val sent = trySend(DownloadProgress.Downloading(progress / 100f))
                                if (!sent.isSuccess) {
                                    Log.w(TAG, "Failed to send progress update")
                                }
                            }
                        }
                        
                        Log.d(TAG, "yt-dlp execution completed with $clientInfo")
                        downloadSuccess = true
                        break // Успех - выходим из цикла
                        
                    } catch (e: Exception) {
                        val clientInfo = config.playerClient ?: "auto"
                        Log.w(TAG, "Failed with player_client=$clientInfo: ${e.message}")
                        lastError = e
                        
                        // Если это не последняя попытка, продолжаем
                        if (index < DOWNLOAD_CONFIGS.size - 1) {
                            Thread.sleep(1000) // Небольшая задержка перед следующей попыткой
                            continue
                        }
                    }
                }
                
                if (!downloadSuccess) {
                    Log.e(TAG, "All player_client attempts failed", lastError)
                    trySend(DownloadProgress.Error("Не удалось скачать: ${lastError?.message ?: "все попытки провалились"}"))
                    close()
                    return@submit
                }
                
                trySend(DownloadProgress.Converting("Сохранение..."))
                
                // Находим скачанный файл (может быть m4a, webm, opus, mp4 и т.д.)
                val downloadedFile = musicDir.listFiles()?.find { 
                    it.name.startsWith("$safeArtist - $safeTitle") && 
                    (it.extension == "m4a" || it.extension == "webm" || it.extension == "opus" || 
                     it.extension == "mp3" || it.extension == "aac" || it.extension == "mp4")
                }
                
                if (downloadedFile == null || !downloadedFile.exists()) {
                    Log.e(TAG, "Downloaded file not found. Files in dir: ${musicDir.listFiles()?.joinToString { it.name }}")
                    trySend(DownloadProgress.Error("Файл не найден после загрузки"))
                    close()
                    return@submit
                }
                
                Log.d(TAG, "File downloaded: ${downloadedFile.absolutePath} (${downloadedFile.length()} bytes)")
                
                // Скачиваем обложку
                var localThumbnailPath: String? = null
                try {
                    val thumbnailDir = File(musicDir, ".thumbnails")
                    if (!thumbnailDir.exists()) thumbnailDir.mkdirs()
                    
                    val thumbnailFile = File(thumbnailDir, "$fileName.jpg")
                    if (!thumbnailFile.exists() && videoInfo.thumbnail.isNotBlank()) {
                        Log.d(TAG, "Downloading thumbnail from: ${videoInfo.thumbnail}")
                        URL(videoInfo.thumbnail).openStream().use { input ->
                            thumbnailFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Thumbnail saved: ${thumbnailFile.absolutePath}")
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
                
                Log.d(TAG, "Download completed successfully")
                trySend(DownloadProgress.Success(downloadedFile.absolutePath, localThumbnailPath))
                close()
            }
            
            // Ждем завершения с таймаутом
            awaitClose {
                Log.d(TAG, "Download flow closed, cancelling if needed")
                if (!future.isDone) {
                    Log.w(TAG, "Cancelling download task")
                    future.cancel(true)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download setup error", e)
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
