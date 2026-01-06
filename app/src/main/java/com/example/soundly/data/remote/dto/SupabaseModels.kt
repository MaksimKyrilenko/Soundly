package com.example.soundly.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PlaylistDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("track_ids") val trackIds: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class FavoriteDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ListeningHistoryDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("played_at") val playedAt: String? = null,
    val duration: Long = 0
)
