package com.example.soundly.presentation.screens.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.data.remote.DownloadProgress
import com.example.soundly.data.remote.VideoInfo
import com.example.soundly.data.remote.YouTubeService
import com.example.soundly.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadItem(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val filePath: String? = null
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

data class DownloadUiState(
    val url: String = "",
    val videoInfo: VideoInfo? = null,
    val customTitle: String = "",
    val customArtist: String = "",
    val isFetching: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val statusMessage: String = "",
    val recentDownloads: List<DownloadItem> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val showBrowserFallback: Boolean = false,
    val browserFallbackUrl: String? = null
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTubeService: YouTubeService,
    private val trackRepository: TrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    fun onUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(
            url = url, 
            error = null,
            videoInfo = null,
            customTitle = "",
            customArtist = ""
        )
        
        // Автоматически получаем информацию при вставке ссылки
        if (url.isNotBlank() && isValidYoutubeUrl(url)) {
            fetchVideoInfo()
        }
    }
    
    fun onTitleChange(title: String) {
        _uiState.value = _uiState.value.copy(customTitle = title)
    }
    
    fun onArtistChange(artist: String) {
        _uiState.value = _uiState.value.copy(customArtist = artist)
    }

    fun fetchVideoInfo() {
        val url = _uiState.value.url
        if (url.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetching = true, error = null)
            
            youTubeService.getVideoInfo(url).fold(
                onSuccess = { info ->
                    // Пытаемся разделить название на исполнителя и трек
                    val (artist, title) = parseTitle(info.title, info.author)
                    
                    _uiState.value = _uiState.value.copy(
                        isFetching = false,
                        videoInfo = info,
                        customTitle = title,
                        customArtist = artist
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isFetching = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    private fun parseTitle(title: String, channelName: String): Pair<String, String> {
        // Пробуем найти разделитель " - " в названии
        val separators = listOf(" - ", " – ", " — ", " | ")
        
        for (sep in separators) {
            if (title.contains(sep)) {
                val parts = title.split(sep, limit = 2)
                if (parts.size == 2) {
                    return Pair(parts[0].trim(), parts[1].trim())
                }
            }
        }
        
        // Убираем типичные суффиксы из названия
        val cleanTitle = title
            .replace(Regex("\\s*\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Official.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*Official\\s*(Video|Audio|Music\\s*Video)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Lyrics?.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[Lyrics?.*?\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*HD\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*HQ\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        
        // Убираем " - Topic" из названия канала
        val cleanChannel = channelName
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .trim()
        
        return Pair(cleanChannel, cleanTitle)
    }

    fun downloadFromYoutube() {
        val state = _uiState.value
        val videoInfo = state.videoInfo
        
        if (videoInfo == null) {
            _uiState.value = state.copy(error = "Сначала получите информацию о видео")
            return
        }
        
        if (state.customTitle.isBlank()) {
            _uiState.value = state.copy(error = "Введите название трека")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true, 
                error = null,
                successMessage = null
            )
            
            youTubeService.downloadAudio(
                context = context,
                videoInfo = videoInfo,
                customTitle = state.customTitle,
                customArtist = state.customArtist.ifBlank { "Неизвестный исполнитель" }
            ).collect { progress ->
                when (progress) {
                    is DownloadProgress.Fetching -> {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = progress.message
                        )
                    }
                    is DownloadProgress.Downloading -> {
                        _uiState.value = _uiState.value.copy(
                            downloadProgress = progress.progress,
                            statusMessage = "Загрузка: ${(progress.progress * 100).toInt()}%"
                        )
                    }
                    is DownloadProgress.Converting -> {
                        _uiState.value = _uiState.value.copy(
                            statusMessage = progress.message
                        )
                    }
                    is DownloadProgress.Success -> {
                        val downloadItem = DownloadItem(
                            id = System.currentTimeMillis().toString(),
                            title = state.customTitle,
                            artist = state.customArtist,
                            thumbnail = progress.thumbnailPath ?: videoInfo.thumbnail,
                            url = state.url,
                            status = DownloadStatus.COMPLETED,
                            progress = 1f,
                            filePath = progress.filePath
                        )
                        
                        // Получаем длительность из файла
                        val duration = try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(progress.filePath)
                            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            retriever.release()
                            durationStr?.toLongOrNull() ?: 0L
                        } catch (e: Exception) {
                            android.util.Log.e("DownloadVM", "Failed to get duration", e)
                            0L
                        }
                        
                        // Конвертируем путь в file:// URI для плеера (с тремя слэшами)
                        val fileUri = "file://${progress.filePath}"
                        
                        // Используем локальный путь к обложке если есть, иначе URL
                        val artworkUri = if (progress.thumbnailPath != null) {
                            "file://${progress.thumbnailPath}"
                        } else {
                            videoInfo.thumbnail
                        }
                        
                        // Добавляем трек напрямую в базу данных с метаданными
                        // Используем стабильный ID на основе пути файла (совпадает с ID при сканировании MediaStore)
                        viewModelScope.launch {
                            try {
                                val trackId = "soundly_${progress.filePath.hashCode()}"
                                val track = com.example.soundly.domain.model.Track(
                                    id = trackId,
                                    title = state.customTitle,
                                    artist = state.customArtist.ifBlank { "Неизвестный исполнитель" },
                                    album = "YouTube",
                                    duration = duration,
                                    artworkUri = artworkUri,
                                    uri = fileUri,
                                    isLocal = true,
                                    dateAdded = System.currentTimeMillis()
                                )
                                trackRepository.insertTrack(track)
                                // Не вызываем scanLocalMusic() - трек уже добавлен с правильными метаданными
                            } catch (e: Exception) {
                                android.util.Log.e("DownloadVM", "Failed to add track to DB", e)
                            }
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 0f,
                            url = "",
                            videoInfo = null,
                            customTitle = "",
                            customArtist = "",
                            statusMessage = "",
                            recentDownloads = listOf(downloadItem) + _uiState.value.recentDownloads.take(9),
                            successMessage = "Трек сохранён!"
                        )
                    }
                    is DownloadProgress.Error -> {
                        // Показываем fallback с браузером
                        val fallbackUrl = youTubeService.getWebDownloadUrl(videoInfo.id)
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadProgress = 0f,
                            statusMessage = "",
                            error = progress.message,
                            showBrowserFallback = true,
                            browserFallbackUrl = fallbackUrl
                        )
                    }
                }
            }
        }
    }
    
    fun dismissBrowserFallback() {
        _uiState.value = _uiState.value.copy(
            showBrowserFallback = false,
            browserFallbackUrl = null
        )
    }
    
    fun getWebDownloadUrl(): String? {
        val videoId = _uiState.value.videoInfo?.id ?: return null
        return youTubeService.getWebDownloadUrl(videoId)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
    
    fun clearVideoInfo() {
        _uiState.value = _uiState.value.copy(
            url = "",
            videoInfo = null,
            customTitle = "",
            customArtist = "",
            error = null,
            showBrowserFallback = false,
            browserFallbackUrl = null
        )
    }

    private fun isValidYoutubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }
}
