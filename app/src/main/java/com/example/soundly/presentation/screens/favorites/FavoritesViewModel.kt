package com.example.soundly.presentation.screens.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.domain.model.Track
import com.example.soundly.domain.repository.TrackRepository
import com.example.soundly.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            trackRepository.getFavoriteTracks().collect { tracks ->
                _uiState.value = _uiState.value.copy(tracks = tracks)
            }
        }
    }

    fun playTrack(track: Track) {
        playerController.playTrack(track, _uiState.value.tracks)
    }

    fun playAll() {
        _uiState.value.tracks.firstOrNull()?.let { firstTrack ->
            playerController.playTrack(firstTrack, _uiState.value.tracks)
        }
    }

    fun shufflePlay() {
        val shuffled = _uiState.value.tracks.shuffled()
        shuffled.firstOrNull()?.let { firstTrack ->
            playerController.playTrack(firstTrack, shuffled)
        }
    }

    fun removeFromFavorites(trackId: String) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }
}
