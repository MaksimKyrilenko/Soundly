package com.example.soundly.domain.repository

import com.example.soundly.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getAllTracks(): Flow<List<Track>>
    fun getLocalTracks(): Flow<List<Track>>
    fun getFavoriteTracks(): Flow<List<Track>>
    fun searchTracks(query: String): Flow<List<Track>>
    fun getTopTracks(limit: Int = 10): Flow<List<Track>>
    suspend fun getTrackById(id: String): Track?
    suspend fun toggleFavorite(trackId: String)
    suspend fun incrementPlayCount(trackId: String)
    suspend fun scanLocalMusic(): List<Track>
    suspend fun insertTrack(track: Track)
    suspend fun deleteTrack(trackId: String)
    suspend fun updateTrack(trackId: String, title: String, artist: String, album: String)
}
