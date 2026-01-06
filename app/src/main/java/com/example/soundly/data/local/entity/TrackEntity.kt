package com.example.soundly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.soundly.domain.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val artworkUri: String?,
    val uri: String,
    val isLocal: Boolean,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val dateAdded: Long = System.currentTimeMillis()
) {
    fun toDomain() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        artworkUri = artworkUri,
        uri = uri,
        isLocal = isLocal,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        dateAdded = dateAdded
    )

    companion object {
        fun fromDomain(track: Track) = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            duration = track.duration,
            artworkUri = track.artworkUri,
            uri = track.uri,
            isLocal = track.isLocal,
            isFavorite = track.isFavorite,
            playCount = track.playCount,
            lastPlayedAt = track.lastPlayedAt,
            dateAdded = track.dateAdded
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val coverUri: String?,
    val trackIds: String, // JSON array
    val createdAt: Long,
    val updatedAt: Long,
    val isSynced: Boolean
)

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAt: Long,
    val duration: Long
)
