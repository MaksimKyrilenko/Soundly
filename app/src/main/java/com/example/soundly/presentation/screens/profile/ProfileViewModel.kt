package com.example.soundly.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.data.local.PreferencesManager
import com.example.soundly.data.repository.AuthRepository
import com.example.soundly.data.repository.SyncRepository
import com.example.soundly.data.repository.UserStats
import com.example.soundly.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val userEmail: String? = null,
    val avatarUrl: String? = null,
    val stats: UserStats = UserStats(),
    val isDarkTheme: Boolean = false,
    val isAutoPlay: Boolean = true,
    val isShuffleEnabled: Boolean = false,
    val isSyncing: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        loadStats()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferencesManager.isLoggedIn,
                preferencesManager.userName,
                preferencesManager.userEmail,
                preferencesManager.userAvatar,
                preferencesManager.isDarkTheme,
                preferencesManager.isAutoPlay,
                preferencesManager.isShuffleEnabled
            ) { values ->
                ProfileUiState(
                    isLoggedIn = values[0] as Boolean,
                    userName = values[1] as String?,
                    userEmail = values[2] as String?,
                    avatarUrl = values[3] as String?,
                    isDarkTheme = values[4] as Boolean,
                    isAutoPlay = values[5] as Boolean,
                    isShuffleEnabled = values[6] as Boolean,
                    stats = _uiState.value.stats,
                    isSyncing = _uiState.value.isSyncing
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    private fun loadStats() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                val stats = syncRepository.getListeningStats()
                _uiState.update { it.copy(stats = stats) }
            }
        }
    }
    
    fun refreshStats() {
        loadStats()
    }
    
    fun syncData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                syncRepository.syncAll()
                loadStats()
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setDarkTheme(enabled)
        }
    }

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoPlay(enabled)
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShuffleEnabled(enabled)
        }
    }

    fun logout() {
        viewModelScope.launch {
            // Очищаем локальные плейлисты при выходе
            playlistRepository.clearLocalPlaylists()
            authRepository.signOut()
            _uiState.update { it.copy(stats = UserStats()) }
        }
    }
}
