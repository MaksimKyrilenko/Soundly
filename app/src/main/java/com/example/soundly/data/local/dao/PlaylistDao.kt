package com.example.soundly.data.local.dao

import androidx.room.*
import com.example.soundly.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    suspend fun getAllPlaylistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isSynced = 0")
    suspend fun getUnsyncedPlaylists(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET trackIds = :trackIds, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistTracks(playlistId: String, trackIds: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET isSynced = :isSynced WHERE id = :playlistId")
    suspend fun updateSyncStatus(playlistId: String, isSynced: Boolean)
    
    @Query("UPDATE playlists SET isSynced = 1 WHERE id = :playlistId")
    suspend fun markAsSynced(playlistId: String)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String)
    
    @Query("DELETE FROM playlists")
    suspend fun clearAll()
}
