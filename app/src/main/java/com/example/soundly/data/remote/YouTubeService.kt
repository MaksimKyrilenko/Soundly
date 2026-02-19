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
class YouTubeService @Inject constructor(
    private val newPipeService: NewPipeYouTubeService
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
            // Используем NewPipe вместо noembed
            newPipeService.getVideoInfo(url)
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
