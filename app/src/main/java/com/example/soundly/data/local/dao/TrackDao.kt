package com.example.soundly.data.local.dao

import androidx.room.*
import com.example.soundly.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>
    
    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isLocal = 1 ORDER BY dateAdded DESC")
    fun getLocalTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY playCount DESC LIMIT :limit")
    fun getTopTracks(limit: Int = 10): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: String)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album WHERE id = :trackId")
    suspend fun updateTrackInfo(trackId: String, title: String, artist: String, album: String)
}
