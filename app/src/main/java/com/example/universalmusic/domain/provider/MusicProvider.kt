package com.example.universalmusic.domain.provider

import com.example.universalmusic.domain.model.Attribution
import com.example.universalmusic.domain.model.MusicAlbum
import com.example.universalmusic.domain.model.MusicArtist
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackDescriptor
import com.example.universalmusic.domain.model.ProviderCapabilities
import com.example.universalmusic.domain.model.ProviderId

interface MusicProvider {
    val id: ProviderId
    val displayName: String

    suspend fun capabilities(): ProviderCapabilities
    suspend fun searchTracks(query: String, page: String? = null): ProviderPage<MusicTrack>
    suspend fun searchArtists(query: String, page: String? = null): ProviderPage<MusicArtist>
    suspend fun searchAlbums(query: String, page: String? = null): ProviderPage<MusicAlbum>
    suspend fun resolvePlayback(track: MusicTrack): PlaybackResolution
    fun attributionFor(track: MusicTrack): Attribution
}

data class ProviderPage<T>(
    val items: List<T>,
    val nextPageToken: String?,
    val hasMore: Boolean
)

sealed interface PlaybackResolution {
    data class Ready(val descriptor: PlaybackDescriptor) : PlaybackResolution
    data class PreviewOnly(val descriptor: PlaybackDescriptor) : PlaybackResolution
    data class OfficialPlayerRequired(val payload: Map<String, String>) : PlaybackResolution
    data class ExternalOnly(val url: String) : PlaybackResolution
    data class Unavailable(val reason: String) : PlaybackResolution
}

enum class ProviderCatalogStatus { APPROVED, APPROVED_METADATA_ONLY, CONDITIONAL, BLOCKED_NO_BACKEND }

data class ProviderInfo(
    val id: ProviderId,
    val displayName: String,
    val status: ProviderCatalogStatus,
    val statusDescription: String,
    val capabilities: ProviderCapabilities
)
