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
import kotlinx.coroutines.flow.Flow
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
            // Пробуем NewPipe
            val newPipeResult = newPipeService.getVideoInfo(url)
            if (newPipeResult.isSuccess) {
                return@withContext newPipeResult
            }
            
            // Fallback на yt-dlp если NewPipe не сработал
            Log.w(TAG, "NewPipe failed: ${newPipeResult.exceptionOrNull()?.message}, trying yt-dlp fallback")
            val videoId = extractVideoId(url) 
                ?: return@withContext Result.failure(Exception("Неверная ссылка YouTube"))
            
            try {
                if (!SoundlyApp.isYoutubeDLReady) {
                    return@withContext Result.failure(Exception("Загрузчик YouTube еще не готов. Подождите несколько секунд."))
                }
                
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context)
                    Log.d(TAG, "yt-dlp updated successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update yt-dlp: ${e.message}")
                }
                
                val request = YoutubeDLRequest(url)
                request.addOption("--dump-json")
                request.addOption("--no-playlist")
                request.addOption("--extractor-args", "youtube:player_client=android,web")
                
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
                Log.e(TAG, "yt-dlp fallback failed", e)
                Result.failure(Exception("Не удалось загрузить информацию: ${e.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "All methods failed", e)
            Result.failure(Exception("Ошибка получения информации: ${e.message}"))
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
    ): Flow<DownloadProgress> = flow {
        try {
            // Используем NewPipe для загрузки
            newPipeService.downloadAudio(context, videoInfo, customTitle, customArtist)
                .collect { progress ->
                    emit(progress)
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
