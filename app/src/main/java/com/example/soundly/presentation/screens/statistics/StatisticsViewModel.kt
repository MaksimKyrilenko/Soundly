package com.example.soundly.presentation.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.domain.model.Track
import com.example.soundly.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val topTracks: List<Track> = emptyList(),
    val totalListenTime: Long = 0L,
    val totalTracksPlayed: Int = 0,
    val favoriteGenre: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val trackRepository: TrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            trackRepository.getTopTracks(10).collect { tracks ->
                val totalPlays = tracks.sumOf { it.playCount }
                val totalTime = tracks.sumOf { it.duration * it.playCount }
                
                _uiState.value = _uiState.value.copy(
                    topTracks = tracks,
                    totalTracksPlayed = totalPlays,
                    totalListenTime = totalTime
                )
            }
        }
    }

    fun clearHistory() {
        // TODO: Implement clear history
    }
}
