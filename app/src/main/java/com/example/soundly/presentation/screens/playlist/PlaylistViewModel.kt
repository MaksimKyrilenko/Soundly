package com.example.soundly.presentation.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.domain.model.PlayerState
import com.example.soundly.domain.model.Playlist
import com.example.soundly.domain.model.Track
import com.example.soundly.domain.repository.PlaylistRepository
import com.example.soundly.domain.repository.TrackRepository
import com.example.soundly.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val availableTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _detailUiState = MutableStateFlow(PlaylistDetailUiState())
    val detailUiState: StateFlow<PlaylistDetailUiState> = _detailUiState.asStateFlow()

    val playerState: StateFlow<PlayerState> = playerController.playerState

    init {
        // Initialize player controller to ensure playback works
        playerController.initialize()
        loadPlaylists()
        loadAvailableTracks()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }
    
    private fun loadAvailableTracks() {
        viewModelScope.launch {
            trackRepository.getLocalTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(availableTracks = tracks)
                _detailUiState.value = _detailUiState.value.copy(allTracks = tracks)
            }
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
        }
    }
    
    fun createPlaylistWithTracks(name: String, description: String = "", trackIds: List<String>, coverUri: String? = null) {
        viewModelScope.launch {
            android.util.Log.d("PlaylistViewModel", "createPlaylistWithTracks: name=$name, coverUri=$coverUri, tracks=${trackIds.size}")
            val playlist = playlistRepository.createPlaylist(name, description)
            // Update cover if provided
            if (coverUri != null) {
                val updated = playlist.copy(coverUri = coverUri)
                android.util.Log.d("PlaylistViewModel", "Updating playlist with coverUri: $coverUri")
                playlistRepository.updatePlaylist(updated)
            }
            trackIds.forEach { trackId ->
                playlistRepository.addTrackToPlaylist(playlist.id, trackId)
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun loadPlaylistDetail(playlistId: String) {
        viewModelScope.launch {
            android.util.Log.d("PlaylistViewModel", "loadPlaylistDetail: playlistId=$playlistId")
            _detailUiState.value = _detailUiState.value.copy(isLoading = true)
            val playlist = playlistRepository.getPlaylistById(playlistId)
            android.util.Log.d("PlaylistViewModel", "loadPlaylistDetail: playlist=${playlist?.name}, trackIds=${playlist?.trackIds?.size}")
            if (playlist != null) {
                val tracks = playlist.trackIds.mapNotNull { trackId ->
                    val track = trackRepository.getTrackById(trackId)
                    android.util.Log.d("PlaylistViewModel", "loadPlaylistDetail: trackId=$trackId, found=${track != null}")
                    track
                }
                android.util.Log.d("PlaylistViewModel", "loadPlaylistDetail: loaded ${tracks.size} tracks")
                _detailUiState.value = _detailUiState.value.copy(
                    playlist = playlist,
                    tracks = tracks,
                    isLoading = false
                )
            } else {
                android.util.Log.e("PlaylistViewModel", "loadPlaylistDetail: playlist not found!")
                _detailUiState.value = _detailUiState.value.copy(
                    isLoading = false,
                    error = "Плейлист не найден"
                )
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
            loadPlaylistDetail(playlistId)
        }
    }
    
    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            trackIds.forEach { trackId ->
                playlistRepository.addTrackToPlaylist(playlistId, trackId)
            }
            loadPlaylistDetail(playlistId)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            loadPlaylistDetail(playlistId)
        }
    }

    fun reorderTracks(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            playlistRepository.reorderTracks(playlistId, trackIds)
            // Update local state immediately for smooth UX
            _detailUiState.value = _detailUiState.value.copy(
                tracks = trackIds.mapNotNull { trackId ->
                    _detailUiState.value.tracks.find { it.id == trackId }
                }
            )
        }
    }
    
    fun updatePlaylistInfo(playlistId: String, name: String, description: String, coverUri: String? = null) {
        viewModelScope.launch {
            // Сначала пробуем из detailUiState, если там есть плейлист
            val playlist = _detailUiState.value.playlist?.takeIf { it.id == playlistId }
                ?: playlistRepository.getPlaylistById(playlistId)
            
            if (playlist != null) {
                val updated = playlist.copy(
                    name = name,
                    description = description,
                    coverUri = coverUri ?: playlist.coverUri,
                    updatedAt = System.currentTimeMillis()
                )
                playlistRepository.updatePlaylist(updated)
                
                // Обновляем detailUiState если это тот же плейлист
                if (_detailUiState.value.playlist?.id == playlistId) {
                    _detailUiState.value = _detailUiState.value.copy(playlist = updated)
                }
            }
        }
    }

    fun syncPlaylists() {
        viewModelScope.launch {
            playlistRepository.syncPlaylists()
        }
    }

    fun playPlaylist(startIndex: Int = 0) {
        val tracks = _detailUiState.value.tracks
        android.util.Log.d("PlaylistViewModel", "playPlaylist called!")
        android.util.Log.d("PlaylistViewModel", "playPlaylist: detailUiState.tracks.size=${tracks.size}")
        android.util.Log.d("PlaylistViewModel", "playPlaylist: startIndex=$startIndex")
        if (tracks.isEmpty()) {
            android.util.Log.e("PlaylistViewModel", "playPlaylist: NO TRACKS TO PLAY!")
            return
        }
        val track = tracks.getOrElse(startIndex) { tracks.first() }
        android.util.Log.d("PlaylistViewModel", "playPlaylist: Playing track: ${track.title}, uri=${track.uri}")
        playerController.playTrack(track, tracks)
    }

    fun playPlaylistShuffled() {
        val originalTracks = _detailUiState.value.tracks
        android.util.Log.d("PlaylistViewModel", "playPlaylistShuffled called!")
        android.util.Log.d("PlaylistViewModel", "playPlaylistShuffled: detailUiState.tracks.size=${originalTracks.size}")
        if (originalTracks.isEmpty()) {
            android.util.Log.e("PlaylistViewModel", "playPlaylistShuffled: NO TRACKS TO PLAY!")
            return
        }
        val tracks = originalTracks.shuffled()
        android.util.Log.d("PlaylistViewModel", "playPlaylistShuffled: Playing shuffled track: ${tracks.first().title}")
        playerController.playTrack(tracks.first(), tracks)
    }

    fun playTrack(track: Track) {
        val tracks = _detailUiState.value.tracks
        android.util.Log.d("PlaylistViewModel", "playTrack called!")
        android.util.Log.d("PlaylistViewModel", "playTrack: track=${track.title}, uri=${track.uri}")
        android.util.Log.d("PlaylistViewModel", "playTrack: queue size=${tracks.size}")
        playerController.playTrack(track, tracks)
    }

    fun playPause() {
        playerController.playPause()
    }

    fun playNext() {
        playerController.next()
    }

    fun playPrevious() {
        playerController.previous()
    }

    fun updateTrack(trackId: String, title: String, artist: String, album: String) {
        viewModelScope.launch {
            trackRepository.updateTrack(trackId, title, artist, album)
            // Reload to reflect changes
            _detailUiState.value.playlist?.let { loadPlaylistDetail(it.id) }
        }
    }
}
