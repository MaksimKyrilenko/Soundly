package com.example.soundly.presentation.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.domain.model.PlayerState
import com.example.soundly.domain.model.Playlist
import com.example.soundly.domain.repository.PlaylistRepository
import com.example.soundly.domain.repository.TrackRepository
import com.example.soundly.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState
    
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    
    init {
        loadPlaylists()
    }
    
    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { list ->
                _playlists.value = list
            }
        }
    }

    fun playPause() {
        playerController.playPause()
    }

    fun next() {
        playerController.next()
    }

    fun previous() {
        playerController.previous()
    }

    fun seekTo(position: Long) {
        playerController.seekTo(position)
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeat() {
        playerController.toggleRepeat()
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }
    
    fun addToPlaylist(trackId: String, playlistId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }
    
    fun createPlaylistAndAddTrack(name: String, trackId: String) {
        viewModelScope.launch {
            val playlist = playlistRepository.createPlaylist(name)
            playlistRepository.addTrackToPlaylist(playlist.id, trackId)
        }
    }
}
