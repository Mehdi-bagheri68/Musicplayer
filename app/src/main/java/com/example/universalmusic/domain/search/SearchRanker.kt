package com.example.universalmusic.domain.search

import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.model.TrackSourceGroup
import java.util.Locale
import javax.inject.Inject

/** Transparent, deterministic ranker: relevance + playability + metadata completeness. */
class SearchRanker @Inject constructor() {

    fun rankGroups(
        groups: List<TrackSourceGroup>,
        query: String,
        sortOption: SearchSortOption = SearchSortOption.RELEVANCE,
        preferredProviders: Set<ProviderId> = emptySet()
    ): List<TrackSourceGroup> {
        val normalizedQuery = normalize(query)
        val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        fun score(g: TrackSourceGroup) = computeScore(g.primaryTrack, normalizedQuery, tokens, preferredProviders)

        return when (sortOption) {
            SearchSortOption.RELEVANCE -> groups.sortedByDescending { score(it) }
            SearchSortOption.PLAYABLE_FIRST -> groups.sortedWith(
                compareByDescending<TrackSourceGroup> { availabilityScore(it.primaryTrack.playbackAvailability) }
                    .thenByDescending { score(it) }
            )
            SearchSortOption.PROVIDER -> groups.sortedWith(
                compareBy<TrackSourceGroup> { it.primaryTrack.providerId.value }
                    .thenByDescending { score(it) }
            )
        }
    }

    internal fun computeScore(
        track: MusicTrack,
        normalizedQuery: String,
        queryTokens: List<String>,
        preferredProviders: Set<ProviderId>
    ): Int {
        var score = 0
        val title = normalize(track.title)
        val artist = normalize(track.artistName.orEmpty())

        if (normalizedQuery.isNotEmpty()) {
            when {
                title == normalizedQuery -> score += 60
                title.startsWith(normalizedQuery) -> score += 45
                title.contains(normalizedQuery) -> score += 30
            }
            when {
                artist == normalizedQuery -> score += 45
                artist.isNotEmpty() && artist.contains(normalizedQuery) -> score += 25
            }
            if (queryTokens.isNotEmpty()) {
                val matched = queryTokens.count { title.contains(it) || artist.contains(it) }
                score += (matched * 20) / queryTokens.size
            }
        }

        score += availabilityScore(track.playbackAvailability)

        if (!track.artworkUrl.isNullOrBlank()) score += 6
        if (!track.artistName.isNullOrBlank()) score += 5
        if ((track.durationMs ?: 0L) > 0L) score += 5
        if (!track.isrc.isNullOrBlank()) score += 4

        if (track.providerId in preferredProviders) score += 10
        return score
    }

    private fun availabilityScore(a: PlaybackAvailability): Int = when (a) {
        PlaybackAvailability.FULL_STREAM -> 50
        PlaybackAvailability.PREVIEW -> 30
        PlaybackAvailability.OFFICIAL_PLAYER -> 20
        PlaybackAvailability.EXTERNAL_ONLY -> 10
        PlaybackAvailability.UNAVAILABLE -> 0
    }

    private fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u200C', ' ')
        .trim()
}
