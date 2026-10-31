package com.example.universalmusic.providers.audius

import com.example.universalmusic.domain.model.MusicAlbum
import com.example.universalmusic.domain.model.MusicArtist
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.model.RightsMetadata
import javax.inject.Inject

class AudiusMapper @Inject constructor() {

    fun mapTrack(dto: AudiusTrackDto, fetchedAtEpochMs: Long): MusicTrack {
        val canonicalUrl = buildCanonicalUrl(dto.permalink, "/tracks/${dto.id}")
        val streamable = dto.isStreamable != false
        val artistHandle = dto.user?.handle?.let { "@$it" } ?: dto.user?.name ?: "Audius Artist"

        return MusicTrack(
            id = "${ProviderId.AUDIUS.value}:${dto.id}",
            providerId = ProviderId.AUDIUS,
            providerItemId = dto.id,
            title = dto.title.trim(),
            artistName = dto.user?.name?.trim(),
            artistId = dto.user?.id,
            albumTitle = null,
            albumId = null,
            artworkUrl = dto.artwork?.bestUrl()?.ensureHttps(),
            durationMs = dto.durationSeconds?.takeIf { it > 0 }?.times(1_000L),
            isrc = dto.isrc?.takeIf { it.isNotBlank() },
            releaseDate = dto.releaseDate?.takeIf { it.isNotBlank() },
            genres = listOfNotNull(dto.genre?.takeIf { it.isNotBlank() }),
            sourceUrl = canonicalUrl,
            playbackAvailability = if (streamable) PlaybackAvailability.FULL_STREAM else PlaybackAvailability.EXTERNAL_ONLY,
            rights = RightsMetadata(
                licenseName = dto.license ?: "Audius Open Audio Protocol",
                licenseUrl = "https://audius.co/legal/terms-of-use",
                attributionText = "Provided by Audius • $artistHandle",
                commercialUseAllowed = false,
                redistributionAllowed = false,
                evidenceUrl = canonicalUrl
            ),
            explicit = null,
            metadataFetchedAtEpochMs = fetchedAtEpochMs
        )
    }

    fun mapArtist(dto: AudiusUserDto): MusicArtist {
        val handlePath = dto.handle?.let { "/$it" } ?: "/users/${dto.id}"
        return MusicArtist(
            id = "${ProviderId.AUDIUS.value}:${dto.id}",
            providerId = ProviderId.AUDIUS,
            providerItemId = dto.id,
            name = dto.name.trim(),
            imageUrl = dto.profilePicture?.bestUrl()?.ensureHttps(),
            sourceUrl = buildCanonicalUrl(null, handlePath)
        )
    }

    fun mapAlbum(dto: AudiusPlaylistDto): MusicAlbum = MusicAlbum(
        id = "${ProviderId.AUDIUS.value}:${dto.id}",
        providerId = ProviderId.AUDIUS,
        providerItemId = dto.id,
        title = dto.playlistName.trim(),
        artistName = dto.user?.name?.trim(),
        artworkUrl = dto.artwork?.bestUrl()?.ensureHttps(),
        releaseDate = dto.releaseDate,
        sourceUrl = buildCanonicalUrl(dto.permalink, "/playlists/${dto.id}")
    )

    private fun buildCanonicalUrl(permalink: String?, fallbackPath: String): String {
        val raw = permalink?.takeIf { it.isNotBlank() } ?: fallbackPath
        return when {
            raw.startsWith("https://") -> raw
            raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
            raw.startsWith("/") -> "https://audius.co$raw"
            else -> "https://audius.co/$raw"
        }
    }

    private fun String.ensureHttps(): String =
        if (startsWith("http://")) "https://" + removePrefix("http://") else this
}
