package com.example.soundly.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soundly_preferences")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER = intPreferencesKey("virtualizer")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val PRESERVE_PITCH = booleanPreferencesKey("preserve_pitch")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
    }

    val isDarkTheme: Flow<Boolean> = dataStore.data.map { it[DARK_THEME] ?: true }
    val isAutoPlay: Flow<Boolean> = dataStore.data.map { it[AUTO_PLAY] ?: true }
    val isShuffleEnabled: Flow<Boolean> = dataStore.data.map { it[SHUFFLE_ENABLED] ?: false }
    val repeatMode: Flow<Int> = dataStore.data.map { it[REPEAT_MODE] ?: 0 }
    val equalizerPreset: Flow<String> = dataStore.data.map { it[EQUALIZER_PRESET] ?: "Flat" }
    val equalizerBands: Flow<String> = dataStore.data.map { it[EQUALIZER_BANDS] ?: "0,0,0,0,0,0,0,0,0,0" }
    val equalizerEnabled: Flow<Boolean> = dataStore.data.map { it[EQUALIZER_ENABLED] ?: true }
    val bassBoost: Flow<Int> = dataStore.data.map { it[BASS_BOOST] ?: 0 }
    val virtualizer: Flow<Int> = dataStore.data.map { it[VIRTUALIZER] ?: 0 }
    val playbackSpeed: Flow<Float> = dataStore.data.map { it[PLAYBACK_SPEED] ?: 1.0f }
    val playbackPitch: Flow<Float> = dataStore.data.map { it[PLAYBACK_PITCH] ?: 1.0f }
    val preservePitch: Flow<Boolean> = dataStore.data.map { it[PRESERVE_PITCH] ?: true }
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { it[IS_LOGGED_IN] ?: false }
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val userEmail: Flow<String?> = dataStore.data.map { it[USER_EMAIL] }
    val userName: Flow<String?> = dataStore.data.map { it[USER_NAME] }
    val userAvatar: Flow<String?> = dataStore.data.map { it[USER_AVATAR] }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun setAutoPlay(enabled: Boolean) {
        dataStore.edit { it[AUTO_PLAY] = enabled }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        dataStore.edit { it[REPEAT_MODE] = mode }
    }

    suspend fun setEqualizerPreset(preset: String) {
        dataStore.edit { it[EQUALIZER_PRESET] = preset }
    }

    suspend fun setEqualizerBands(bands: String) {
        dataStore.edit { it[EQUALIZER_BANDS] = bands }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[EQUALIZER_ENABLED] = enabled }
    }

    suspend fun setBassBoost(value: Int) {
        dataStore.edit { it[BASS_BOOST] = value }
    }

    suspend fun setVirtualizer(value: Int) {
        dataStore.edit { it[VIRTUALIZER] = value }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[PLAYBACK_SPEED] = speed }
    }

    suspend fun setPlaybackPitch(pitch: Float) {
        dataStore.edit { it[PLAYBACK_PITCH] = pitch }
    }

    suspend fun setPreservePitch(preserve: Boolean) {
        dataStore.edit { it[PRESERVE_PITCH] = preserve }
    }

    suspend fun setPlaybackSettings(speed: Float, pitch: Float, preservePitch: Boolean) {
        dataStore.edit {
            it[PLAYBACK_SPEED] = speed
            it[PLAYBACK_PITCH] = pitch
            it[PRESERVE_PITCH] = preservePitch
        }
    }

    suspend fun setUserData(id: String, email: String, name: String, avatar: String?) {
        dataStore.edit {
            it[USER_ID] = id
            it[USER_EMAIL] = email
            it[USER_NAME] = name
            avatar?.let { url -> it[USER_AVATAR] = url }
            it[IS_LOGGED_IN] = true
        }
    }

    suspend fun clearUserData() {
        dataStore.edit {
            it.remove(USER_ID)
            it.remove(USER_EMAIL)
            it.remove(USER_NAME)
            it.remove(USER_AVATAR)
            it[IS_LOGGED_IN] = false
        }
    }
}
