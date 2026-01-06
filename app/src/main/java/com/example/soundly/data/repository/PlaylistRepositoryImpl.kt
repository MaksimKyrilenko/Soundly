package com.example.soundly.data.repository

import com.example.soundly.data.local.dao.PlaylistDao
import com.example.soundly.data.local.entity.PlaylistEntity
import com.example.soundly.data.remote.SupabaseClientProvider
import com.example.soundly.data.remote.dto.PlaylistDto
import com.example.soundly.domain.model.Playlist
import com.example.soundly.domain.repository.PlaylistRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val authRepository: AuthRepository
) : PlaylistRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val postgrest = SupabaseClientProvider.postgrest

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getPlaylistById(id: String): Playlist? =
        playlistDao.getPlaylistById(id)?.toDomain()

    override suspend fun createPlaylist(name: String, description: String): Playlist {
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(playlist.toEntity())
        
        // Sync to cloud if logged in
        syncPlaylistToCloud(playlist)
        
        return playlist
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        val updated = playlist.copy(updatedAt = System.currentTimeMillis())
        playlistDao.updatePlaylist(updated.toEntity())
        
        // Sync to cloud
        syncPlaylistToCloud(updated)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylistById(playlistId)
        
        // Delete from cloud
        deletePlaylistFromCloud(playlistId)
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        val trackIds = json.decodeFromString<List<String>>(playlist.trackIds).toMutableList()
        if (!trackIds.contains(trackId)) {
            trackIds.add(trackId)
            playlistDao.updatePlaylistTracks(playlistId, json.encodeToString(trackIds))
            
            // Sync to cloud
            getPlaylistById(playlistId)?.let { syncPlaylistToCloud(it) }
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        val trackIds = json.decodeFromString<List<String>>(playlist.trackIds).toMutableList()
        trackIds.remove(trackId)
        playlistDao.updatePlaylistTracks(playlistId, json.encodeToString(trackIds))
        
        // Sync to cloud
        getPlaylistById(playlistId)?.let { syncPlaylistToCloud(it) }
    }

    override suspend fun reorderTracks(playlistId: String, trackIds: List<String>) {
        playlistDao.updatePlaylistTracks(playlistId, json.encodeToString(trackIds))
        
        // Sync to cloud
        getPlaylistById(playlistId)?.let { syncPlaylistToCloud(it) }
    }

    override suspend fun syncPlaylists() {
        val userId = authRepository.getCurrentUserId() ?: return
        
        try {
            // Get cloud playlists
            val cloudPlaylists = postgrest.from("playlists")
                .select { filter { eq("user_id", userId) } }
                .decodeList<PlaylistDto>()
            
            // Get local playlists
            val localPlaylists = playlistDao.getAllPlaylistsOnce()
            
            // Merge: cloud wins for conflicts, add new from both sides
            val cloudIds = cloudPlaylists.map { it.id }.toSet()
            val localIds = localPlaylists.map { it.id }.toSet()
            
            // Update local with cloud data
            cloudPlaylists.forEach { cloudPlaylist ->
                val entity = PlaylistEntity(
                    id = cloudPlaylist.id ?: UUID.randomUUID().toString(),
                    name = cloudPlaylist.name,
                    description = cloudPlaylist.description ?: "",
                    coverUri = cloudPlaylist.coverUrl,
                    trackIds = json.encodeToString(cloudPlaylist.trackIds),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isSynced = true
                )
                playlistDao.insertPlaylist(entity)
            }
            
            // Upload local-only playlists to cloud
            localPlaylists.filter { it.id !in cloudIds && !it.isSynced }.forEach { local ->
                uploadPlaylistToCloud(local.toDomain())
                playlistDao.markAsSynced(local.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "Sync failed", e)
        }
    }
    
    private suspend fun syncPlaylistToCloud(playlist: Playlist) {
        val userId = authRepository.getCurrentUserId() ?: return
        try {
            val dto = PlaylistDto(
                id = playlist.id,
                userId = userId,
                name = playlist.name,
                description = playlist.description,
                coverUrl = playlist.coverUri,
                trackIds = playlist.trackIds
            )
            postgrest.from("playlists").upsert(dto)
            playlistDao.markAsSynced(playlist.id)
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "Cloud sync failed", e)
        }
    }
    
    private suspend fun uploadPlaylistToCloud(playlist: Playlist) {
        val userId = authRepository.getCurrentUserId() ?: return
        try {
            val dto = PlaylistDto(
                id = playlist.id,
                userId = userId,
                name = playlist.name,
                description = playlist.description,
                coverUrl = playlist.coverUri,
                trackIds = playlist.trackIds
            )
            postgrest.from("playlists").insert(dto)
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "Upload failed", e)
        }
    }
    
    private suspend fun deletePlaylistFromCloud(playlistId: String) {
        val userId = authRepository.getCurrentUserId() ?: return
        try {
            postgrest.from("playlists").delete {
                filter { 
                    eq("id", playlistId)
                    eq("user_id", userId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "Cloud delete failed", e)
        }
    }

    override suspend fun clearLocalPlaylists() {
        playlistDao.clearAll()
    }

    private fun PlaylistEntity.toDomain() = Playlist(
        id = id,
        name = name,
        description = description,
        coverUri = coverUri,
        trackIds = try {
            json.decodeFromString(trackIds)
        } catch (e: Exception) {
            emptyList()
        },
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced
    )

    private fun Playlist.toEntity() = PlaylistEntity(
        id = id,
        name = name,
        description = description,
        coverUri = coverUri,
        trackIds = json.encodeToString(trackIds),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isSynced = isSynced
    )
}
