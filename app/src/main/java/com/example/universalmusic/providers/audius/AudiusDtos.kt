package com.example.universalmusic.providers.audius

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudiusResponseDto<T>(
    @SerialName("data") val data: List<T> = emptyList()
)

@Serializable
data class AudiusTrackDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("permalink") val permalink: String? = null,
    @SerialName("duration") val durationSeconds: Long? = null,
    @SerialName("is_streamable") val isStreamable: Boolean? = true,
    @SerialName("genre") val genre: String? = null,
    @SerialName("mood") val mood: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("isrc") val isrc: String? = null,
    @SerialName("license") val license: String? = null,
    @SerialName("artwork") val artwork: AudiusArtworkDto? = null,
    @SerialName("user") val user: AudiusUserDto? = null
)

@Serializable
data class AudiusArtworkDto(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null
) {
    fun bestUrl(): String? = medium ?: large ?: small
}

@Serializable
data class AudiusUserDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("handle") val handle: String? = null,
    @SerialName("profile_picture") val profilePicture: AudiusArtworkDto? = null,
    @SerialName("is_verified") val isVerified: Boolean? = false
)

@Serializable
data class AudiusPlaylistDto(
    @SerialName("id") val id: String,
    @SerialName("playlist_name") val playlistName: String,
    @SerialName("permalink") val permalink: String? = null,
    @SerialName("is_album") val isAlbum: Boolean? = false,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("artwork") val artwork: AudiusArtworkDto? = null,
    @SerialName("user") val user: AudiusUserDto? = null
)
