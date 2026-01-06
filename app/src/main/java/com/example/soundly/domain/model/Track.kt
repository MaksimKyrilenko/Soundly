package com.example.soundly.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: Long = 0L,
    val artworkUri: String? = null,
    val uri: String,
    val isLocal: Boolean = true,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val dateAdded: Long = System.currentTimeMillis()
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val coverUri: String? = null,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val imageUri: String? = null,
    val trackCount: Int = 0
)

@Serializable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val year: Int? = null,
    val trackCount: Int = 0
)
