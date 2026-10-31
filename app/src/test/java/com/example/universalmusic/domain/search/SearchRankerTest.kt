package com.example.universalmusic.domain.search

import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.model.TrackSourceGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankerTest {

    private val ranker = SearchRanker()

    @Test
    fun `exact title match outranks partial match`() {
        val exact = group("1", "Sahar", PlaybackAvailability.FULL_STREAM)
        val partial = group("2", "Before Sahar Comes", PlaybackAvailability.FULL_STREAM)

        val ranked = ranker.rankGroups(listOf(partial, exact), "sahar")

        assertEquals("audius:1", ranked.first().primaryTrack.id)
    }

    @Test
    fun `playable first sorts by availability before relevance`() {
        val external = group("1", "Sahar", PlaybackAvailability.EXTERNAL_ONLY)
        val stream = group("2", "Sahar Night", PlaybackAvailability.FULL_STREAM)

        val ranked = ranker.rankGroups(listOf(external, stream), "sahar", SearchSortOption.PLAYABLE_FIRST)

        assertEquals("audius:2", ranked.first().primaryTrack.id)
    }

    private fun group(id: String, title: String, availability: PlaybackAvailability) = TrackSourceGroup(
        MusicTrack(
            id = "audius:$id", providerId = ProviderId.AUDIUS, providerItemId = id, title = title,
            artistName = "Artist", artistId = null, albumTitle = null, albumId = null, artworkUrl = null,
            durationMs = 200_000L, isrc = null, releaseDate = null, genres = emptyList(),
            sourceUrl = "https://audius.co/$id", playbackAvailability = availability, rights = null,
            explicit = null, metadataFetchedAtEpochMs = 0L
        )
    )
}
