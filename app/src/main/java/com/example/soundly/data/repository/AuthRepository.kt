package com.example.soundly.data.repository

import com.example.soundly.data.local.PreferencesManager
import com.example.soundly.data.remote.SupabaseClientProvider
import com.example.soundly.data.remote.dto.ProfileDto
import com.example.soundly.domain.model.User
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    private val auth = SupabaseClientProvider.auth
    private val postgrest = SupabaseClientProvider.postgrest

    suspend fun signUp(email: String, password: String, name: String): AuthResult {
        return try {
            val result = auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive(name))
                }
            }

            // Пробуем получить сессию - если email confirmation отключен, сессия будет
            val session = auth.currentSessionOrNull()
            val userId = session?.user?.id
            
            if (userId != null) {
                // Сессия есть - сохраняем данные
                val user = User(
                    id = userId,
                    email = email,
                    name = name
                )
                preferencesManager.setUserData(userId, email, name, null)
                AuthResult.Success(user)
            } else {
                // Email confirmation включен - автоматически логиним после регистрации
                signIn(email, password)
            }
        } catch (e: Exception) {
            // Если пользователь уже существует, пробуем войти
            if (e.message?.contains("already registered") == true) {
                return signIn(email, password)
            }
            AuthResult.Error(e.message ?: "Ошибка регистрации")
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val session = auth.currentSessionOrNull()
            val userId = session?.user?.id ?: throw Exception("Не удалось получить пользователя")

            // Получаем профиль из базы
            val profile = try {
                postgrest.from("profiles")
                    .select {
                        filter { eq("id", userId) }
                    }
                    .decodeSingleOrNull<ProfileDto>()
            } catch (e: Exception) {
                null
            }

            val user = User(
                id = userId,
                email = email,
                name = profile?.name ?: email.substringBefore("@"),
                avatarUrl = profile?.avatarUrl
            )

            preferencesManager.setUserData(userId, email, user.name, user.avatarUrl)
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Ошибка входа")
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            preferencesManager.clearUserData()
        } catch (e: Exception) {
            preferencesManager.clearUserData()
        }
    }

    suspend fun updateProfile(name: String, avatarUrl: String?): AuthResult {
        return try {
            val userId = auth.currentUserOrNull()?.id ?: throw Exception("Пользователь не авторизован")

            postgrest.from("profiles")
                .update({
                    set("name", name)
                    avatarUrl?.let { set("avatar_url", it) }
                }) {
                    filter { eq("id", userId) }
                }

            val email = auth.currentUserOrNull()?.email ?: ""
            preferencesManager.setUserData(userId, email, name, avatarUrl)

            AuthResult.Success(User(userId, email, name, avatarUrl))
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Ошибка обновления профиля")
        }
    }

    fun getCurrentUserId(): String? = auth.currentUserOrNull()?.id

    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null

    suspend fun refreshSession() {
        try {
            auth.refreshCurrentSession()
        } catch (e: Exception) {
            // Session expired
        }
    }
}
