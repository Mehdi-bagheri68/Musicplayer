package com.example.universalmusic.domain.search

import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDeduplicatorTest {

    private val deduplicator = TrackDeduplicator()

    @Test
    fun `merges identical ISRC across providers and keeps FULL_STREAM as primary`() {
        val mb = track(ProviderId.MUSICBRAINZ, "mb-1", "Morghe Sahar", "Shajarian", 480_000L, "IRA019000012", PlaybackAvailability.EXTERNAL_ONLY)
        val au = track(ProviderId.AUDIUS, "au-1", "مرغ سحر", "شجریان", 481_200L, "IRA019000012", PlaybackAvailability.FULL_STREAM)

        val groups = deduplicator.groupDuplicates(listOf(mb, au))

        assertEquals(1, groups.size)
        assertEquals(au.id, groups.first().primaryTrack.id)
        assertEquals(listOf(mb.id), groups.first().alternatives.map { it.id })
    }

    @Test
    fun `never merges original recording with a remix`() {
        val studio = track(ProviderId.AUDIUS, "1", "Tehran Nights", "Aria", 200_000L, null, PlaybackAvailability.FULL_STREAM)
        val remix = track(ProviderId.JAMENDO, "2", "Tehran Nights (Club Remix)", "Aria", 200_500L, null, PlaybackAvailability.FULL_STREAM)

        assertEquals(2, deduplicator.groupDuplicates(listOf(studio, remix)).size)
    }

    @Test
    fun `merges same title artist and near duration without ISRC`() {
        val a = track(ProviderId.AUDIUS, "1", "Tehran Nights", "Aria", 200_000L, null, PlaybackAvailability.FULL_STREAM)
        val b = track(ProviderId.JAMENDO, "2", "tehran nights", "ARIA", 201_500L, null, PlaybackAvailability.PREVIEW)

        assertEquals(1, deduplicator.groupDuplicates(listOf(a, b)).size)
    }

    private fun track(
        providerId: ProviderId, itemId: String, title: String, artist: String,
        durationMs: Long, isrc: String?, availability: PlaybackAvailability
    ) = MusicTrack(
        id = "${providerId.value}:$itemId", providerId = providerId, providerItemId = itemId,
        title = title, artistName = artist, artistId = "artist-1", albumTitle = null, albumId = null,
        artworkUrl = "https://audius.co/art.jpg", durationMs = durationMs, isrc = isrc, releaseDate = "2024",
        genres = emptyList(), sourceUrl = "https://audius.co/track/$itemId", playbackAvailability = availability,
        rights = null, explicit = false, metadataFetchedAtEpochMs = 1_700_000_000_000L
    )
}
