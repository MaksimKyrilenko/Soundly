package com.example.soundly.domain.repository

import com.example.soundly.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun createPlaylist(name: String, description: String = ""): Playlist
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun reorderTracks(playlistId: String, trackIds: List<String>)
    suspend fun syncPlaylists()
    suspend fun clearLocalPlaylists()
}
