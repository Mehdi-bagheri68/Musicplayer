package com.example.universalmusic.domain.model

@JvmInline
value class ProviderId(val value: String) {
    override fun toString(): String = value

    companion object {
        val AUDIUS = ProviderId("audius")
        val JAMENDO = ProviderId("jamendo")
        val ARCHIVE = ProviderId("archive")
        val MUSICBRAINZ = ProviderId("musicbrainz")
        val SOUNDCLOUD = ProviderId("soundcloud")
        val APPLE_MUSIC = ProviderId("applemusic")
    }
}

enum class PlaybackAvailability { FULL_STREAM, PREVIEW, OFFICIAL_PLAYER, EXTERNAL_ONLY, UNAVAILABLE }

data class RightsMetadata(
    val licenseName: String?,
    val licenseUrl: String?,
    val attributionText: String?,
    val commercialUseAllowed: Boolean?,
    val redistributionAllowed: Boolean?,
    val evidenceUrl: String?
)

data class MusicTrack(
    val id: String, // "${providerId.value}:$providerItemId"
    val providerId: ProviderId,
    val providerItemId: String,
    val title: String,
    val artistName: String?,
    val artistId: String?,
    val albumTitle: String?,
    val albumId: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val isrc: String?,
    val releaseDate: String?,
    val genres: List<String>,
    val sourceUrl: String,
    val playbackAvailability: PlaybackAvailability,
    val rights: RightsMetadata?,
    val explicit: Boolean?,
    val metadataFetchedAtEpochMs: Long
) {
    val isPlayableInApp: Boolean
        get() = playbackAvailability == PlaybackAvailability.FULL_STREAM ||
            playbackAvailability == PlaybackAvailability.PREVIEW
}

data class MusicArtist(
    val id: String,
    val providerId: ProviderId,
    val providerItemId: String,
    val name: String,
    val imageUrl: String?,
    val sourceUrl: String
)

data class MusicAlbum(
    val id: String,
    val providerId: ProviderId,
    val providerItemId: String,
    val title: String,
    val artistName: String?,
    val artworkUrl: String?,
    val releaseDate: String?,
    val sourceUrl: String
)

enum class PlaybackMode { MEDIA3_DIRECT_STREAM, OFFICIAL_PROVIDER_PLAYER, EXTERNAL_INTENT, PREVIEW_STREAM, NONE }

data class PlaybackDescriptor(
    val providerId: ProviderId,
    val trackId: String,
    val mode: PlaybackMode,
    val uri: String?,
    val mimeType: String?,
    val expiresAtEpochMs: Long?,
    val requiredHeaders: Map<String, String> = emptyMap(),
    val officialPlayerPayload: Map<String, String> = emptyMap()
)

data class ProviderCapabilities(
    val searchTracks: Boolean,
    val searchArtists: Boolean,
    val searchAlbums: Boolean,
    val searchPlaylists: Boolean,
    val directStream: Boolean,
    val preview: Boolean,
    val officialPlayer: Boolean,
    val requiresUserLogin: Boolean,
    val requiresSubscription: Boolean
)

data class Attribution(
    val providerId: ProviderId,
    val providerDisplayName: String,
    val attributionText: String,
    val sourceUrl: String,
    val licenseUrl: String? = null
)

/** A primary track plus conservative duplicate variants from other providers. */
data class TrackSourceGroup(
    val primaryTrack: MusicTrack,
    val alternatives: List<MusicTrack> = emptyList()
) {
    val allSources: List<MusicTrack>
        get() = listOf(primaryTrack) + alternatives
}
