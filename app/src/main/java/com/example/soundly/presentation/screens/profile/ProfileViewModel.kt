package com.example.soundly.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.data.local.PreferencesManager
import com.example.soundly.data.repository.AuthRepository
import com.example.soundly.data.repository.SyncRepository
import com.example.soundly.data.repository.UserStats
import com.example.soundly.domain.repository.PlaylistRepository
import com.example.soundly.domain.repository.TrackRepository
import com.example.soundly.presentation.theme.ColorPalette
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
    val colorPalette: String = "purple",
    val isAutoPlay: Boolean = true,
    val isShuffleEnabled: Boolean = false,
    val isSyncing: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        loadStats()
        observeLocalFavorites()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferencesManager.isLoggedIn,
                preferencesManager.userName,
                preferencesManager.userEmail,
                preferencesManager.userAvatar,
                preferencesManager.isDarkTheme,
                preferencesManager.colorPalette,
                preferencesManager.isAutoPlay,
                preferencesManager.isShuffleEnabled
            ) { values ->
                ProfileUiState(
                    isLoggedIn = values[0] as Boolean,
                    userName = values[1] as String?,
                    userEmail = values[2] as String?,
                    avatarUrl = values[3] as String?,
                    isDarkTheme = values[4] as Boolean,
                    colorPalette = values[5] as String,
                    isAutoPlay = values[6] as Boolean,
                    isShuffleEnabled = values[7] as Boolean,
                    stats = _uiState.value.stats,
                    isSyncing = _uiState.value.isSyncing
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
    
    private fun observeLocalFavorites() {
        viewModelScope.launch {
            trackRepository.getFavoriteTracks().collect { favorites ->
                _uiState.update { state ->
                    state.copy(
                        stats = state.stats.copy(favoritesCount = favorites.size)
                    )
                }
            }
        }
    }
    
    private fun loadStats() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                val stats = syncRepository.getListeningStats()
                // Получаем реальное количество избранных из локальной БД
                trackRepository.getFavoriteTracks().first().let { favorites ->
                    _uiState.update { 
                        it.copy(stats = stats.copy(favoritesCount = favorites.size)) 
                    }
                }
            }
        }
    }
    
    fun refreshStats() {
        loadStats()
    }
    
    fun updateUserName(newName: String) {
        viewModelScope.launch {
            preferencesManager.setUserName(newName)
            // Также обновляем в облаке если залогинен
            if (authRepository.isLoggedIn()) {
                syncRepository.updateProfile(newName, _uiState.value.avatarUrl)
            }
        }
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
    
    fun setColorPalette(paletteId: String) {
        viewModelScope.launch {
            preferencesManager.setColorPalette(paletteId)
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
