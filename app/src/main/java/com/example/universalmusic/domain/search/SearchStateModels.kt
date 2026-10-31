package com.example.universalmusic.domain.search

import com.example.universalmusic.domain.model.MusicAlbum
import com.example.universalmusic.domain.model.MusicArtist
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.model.TrackSourceGroup

enum class SearchCategory { ALL, TRACKS, ARTISTS, ALBUMS }

enum class SearchSortOption { RELEVANCE, PLAYABLE_FIRST, PROVIDER }

sealed interface ProviderSearchState {
    data object Idle : ProviderSearchState
    data object Loading : ProviderSearchState
    data class Success(val count: Int) : ProviderSearchState
    data class RateLimited(val retryAfterMs: Long?) : ProviderSearchState
    data class AuthenticationRequired(val message: String) : ProviderSearchState
    data class Failed(val safeMessage: String) : ProviderSearchState
    data object Disabled : ProviderSearchState
}

data class AggregatedSearchState(
    val query: String = "",
    val results: List<MusicTrack> = emptyList(),
    val trackGroups: List<TrackSourceGroup> = emptyList(),
    val artists: List<MusicArtist> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
    val providerStates: Map<ProviderId, ProviderSearchState> = emptyMap(),
    val isComplete: Boolean = true
)
