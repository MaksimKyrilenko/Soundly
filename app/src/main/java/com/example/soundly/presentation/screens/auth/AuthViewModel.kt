package com.example.soundly.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soundly.data.repository.AuthRepository
import com.example.soundly.data.repository.AuthResult
import com.example.soundly.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password, error = null)
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, error = null)
    }

    fun login() {
        val state = _uiState.value
        
        if (state.email.isBlank()) {
            _uiState.value = state.copy(error = "Введите email")
            return
        }
        
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = "Введите пароль")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            
            when (val result = authRepository.signIn(state.email, state.password)) {
                is AuthResult.Success -> {
                    // Синхронизируем плейлисты после входа
                    try {
                        playlistRepository.syncPlaylists()
                    } catch (e: Exception) {
                        android.util.Log.e("AuthVM", "Sync failed", e)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        
        if (state.name.isBlank()) {
            _uiState.value = state.copy(error = "Введите имя")
            return
        }
        
        if (state.email.isBlank()) {
            _uiState.value = state.copy(error = "Введите email")
            return
        }
        
        if (!state.email.contains("@")) {
            _uiState.value = state.copy(error = "Неверный формат email")
            return
        }
        
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = "Введите пароль")
            return
        }
        
        if (state.password.length < 6) {
            _uiState.value = state.copy(error = "Пароль должен быть не менее 6 символов")
            return
        }
        
        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(error = "Пароли не совпадают")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            
            when (val result = authRepository.signUp(state.email, state.password, state.name)) {
                is AuthResult.Success -> {
                    // Синхронизируем плейлисты после регистрации
                    try {
                        playlistRepository.syncPlaylists()
                    } catch (e: Exception) {
                        android.util.Log.e("AuthVM", "Sync failed", e)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
}
