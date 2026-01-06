package com.example.soundly.presentation.screens.home

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

data class HomeUiState(
    val tracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val popularTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isInitialLoadDone: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val playerState: StateFlow<PlayerState> = playerController.playerState

    private val searchQuery = MutableStateFlow("")

    init {
        playerController.initialize()
        loadTracksOnce()
        loadPlaylists()
        loadPopularTracks()
        observeSearch()
    }

    private fun loadTracksOnce() {
        // Only scan if not already loaded
        if (!_uiState.value.isInitialLoadDone) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    trackRepository.scanLocalMusic()
                    _uiState.update { it.copy(isInitialLoadDone = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message) }
                }
            }
        }

        // Always observe tracks from database
        viewModelScope.launch {
            trackRepository.getLocalTracks().collect { tracks ->
                _uiState.update { it.copy(tracks = tracks, isLoading = false) }
            }
        }

        viewModelScope.launch {
            trackRepository.getFavoriteTracks().collect { favorites ->
                _uiState.update { it.copy(favoriteTracks = favorites) }
            }
        }
    }
    
    private fun loadPopularTracks() {
        viewModelScope.launch {
            trackRepository.getTopTracks(50).collect { topTracks ->
                _uiState.update { it.copy(popularTracks = topTracks) }
            }
        }
    }
    
    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        trackRepository.getLocalTracks()
                    } else {
                        trackRepository.searchTracks(query)
                    }
                }
                .collect { tracks ->
                    _uiState.update { it.copy(tracks = tracks) }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchQuery.value = query
    }

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun playTrack(track: Track) {
        val queue = when (_uiState.value.selectedTab) {
            1 -> _uiState.value.popularTracks
            2 -> _uiState.value.favoriteTracks
            else -> _uiState.value.tracks
        }
        playerController.playTrack(track, queue)
        
        // Increment play count for statistics
        viewModelScope.launch {
            trackRepository.incrementPlayCount(track.id)
        }
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

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }
    
    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun updateTrack(trackId: String, title: String, artist: String, album: String) {
        viewModelScope.launch {
            trackRepository.updateTrack(trackId, title, artist, album)
        }
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            trackRepository.deleteTrack(trackId)
        }
    }

    fun refreshTracks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                trackRepository.scanLocalMusic()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }
    
    fun initialLoadIfNeeded() {
        if (!_uiState.value.isInitialLoadDone && _uiState.value.tracks.isEmpty()) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    trackRepository.scanLocalMusic()
                    _uiState.update { it.copy(isInitialLoadDone = true) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message) }
                } finally {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
