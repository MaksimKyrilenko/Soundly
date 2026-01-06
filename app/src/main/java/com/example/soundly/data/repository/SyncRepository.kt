package com.example.soundly.data.repository

import com.example.soundly.data.local.dao.PlaylistDao
import com.example.soundly.data.local.dao.TrackDao
import com.example.soundly.data.remote.SupabaseClientProvider
import com.example.soundly.data.remote.dto.FavoriteDto
import com.example.soundly.data.remote.dto.ListeningHistoryDto
import com.example.soundly.data.remote.dto.PlaylistDto
import com.example.soundly.data.remote.dto.ProfileDto
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao
) {
    private val postgrest = SupabaseClientProvider.postgrest

    // ===== FULL SYNC =====
    
    suspend fun syncAll() {
        if (!authRepository.isLoggedIn()) return
        
        syncFavorites()
        syncPlaylists()
    }

    // ===== PROFILE =====
    
    suspend fun getProfile(): ProfileDto? {
        val userId = authRepository.getCurrentUserId() ?: return null
        return try {
            postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun updateProfile(name: String, avatarUrl: String?): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return try {
            postgrest.from("profiles").upsert(
                ProfileDto(id = userId, name = name, avatarUrl = avatarUrl)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ===== PLAYLISTS =====

    suspend fun getCloudPlaylists(): List<PlaylistDto> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            postgrest.from("playlists")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun uploadPlaylist(playlist: PlaylistDto): Boolean {
        return try {
            postgrest.from("playlists").upsert(playlist)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteCloudPlaylist(playlistId: String): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return try {
            postgrest.from("playlists").delete {
                filter { 
                    eq("id", playlistId)
                    eq("user_id", userId)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun syncPlaylists() {
        val userId = authRepository.getCurrentUserId() ?: return
        try {
            // Get cloud playlists
            val cloudPlaylists = getCloudPlaylists()
            
            // Get local unsynced playlists
            val unsyncedPlaylists = playlistDao.getUnsyncedPlaylists()
            
            // Upload unsynced to cloud
            unsyncedPlaylists.forEach { local ->
                val dto = PlaylistDto(
                    id = local.id,
                    userId = userId,
                    name = local.name,
                    description = local.description,
                    coverUrl = local.coverUri,
                    trackIds = try {
                        kotlinx.serialization.json.Json.decodeFromString(local.trackIds)
                    } catch (e: Exception) {
                        emptyList()
                    }
                )
                if (uploadPlaylist(dto)) {
                    playlistDao.markAsSynced(local.id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Playlist sync failed", e)
        }
    }

    // ===== FAVORITES =====

    suspend fun getCloudFavorites(): List<FavoriteDto> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            postgrest.from("favorites")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addCloudFavorite(trackId: String): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return try {
            postgrest.from("favorites").insert(
                FavoriteDto(userId = userId, trackId = trackId)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeCloudFavorite(trackId: String): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return try {
            postgrest.from("favorites").delete {
                filter {
                    eq("user_id", userId)
                    eq("track_id", trackId)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun syncFavorites() {
        try {
            val cloudFavorites = getCloudFavorites()
            
            // Update local favorites from cloud
            cloudFavorites.forEach { favorite ->
                trackDao.updateFavorite(favorite.trackId, true)
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Favorites sync failed", e)
        }
    }

    // ===== LISTENING HISTORY =====

    suspend fun getCloudHistory(limit: Int = 100): List<ListeningHistoryDto> {
        val userId = authRepository.getCurrentUserId() ?: return emptyList()
        return try {
            postgrest.from("listening_history")
                .select {
                    filter { eq("user_id", userId) }
                    order("played_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addToCloudHistory(trackId: String, duration: Long): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return try {
            postgrest.from("listening_history").insert(
                ListeningHistoryDto(
                    userId = userId,
                    trackId = trackId,
                    duration = duration
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearCloudHistory(): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return try {
            postgrest.from("listening_history").delete {
                filter { eq("user_id", userId) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // ===== STATISTICS =====
    
    suspend fun getListeningStats(): UserStats {
        val userId = authRepository.getCurrentUserId() ?: return UserStats()
        return try {
            val history = postgrest.from("listening_history")
                .select { filter { eq("user_id", userId) } }
                .decodeList<ListeningHistoryDto>()
            
            val favorites = getCloudFavorites()
            val playlists = getCloudPlaylists()
            
            UserStats(
                totalPlays = history.size,
                totalListeningTime = history.sumOf { it.duration },
                uniqueTracks = history.map { it.trackId }.distinct().size,
                favoritesCount = favorites.size,
                playlistsCount = playlists.size
            )
        } catch (e: Exception) {
            UserStats()
        }
    }
}

data class UserStats(
    val totalPlays: Int = 0,
    val totalListeningTime: Long = 0,
    val uniqueTracks: Int = 0,
    val favoritesCount: Int = 0,
    val playlistsCount: Int = 0
)
