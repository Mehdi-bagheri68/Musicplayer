package com.example.universalmusic.domain.usecase

import com.example.universalmusic.core.common.IoDispatcher
import com.example.universalmusic.core.preferences.UserPreferencesRepository
import com.example.universalmusic.domain.error.ProviderErrorCode
import com.example.universalmusic.domain.error.ProviderException
import com.example.universalmusic.domain.model.MusicAlbum
import com.example.universalmusic.domain.model.MusicArtist
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.provider.MusicProvider
import com.example.universalmusic.domain.provider.ProviderRegistry
import com.example.universalmusic.domain.search.AggregatedSearchState
import com.example.universalmusic.domain.search.ProviderSearchState
import com.example.universalmusic.domain.search.SearchCategory
import com.example.universalmusic.domain.search.SearchRanker
import com.example.universalmusic.domain.search.SearchSortOption
import com.example.universalmusic.domain.search.TrackDeduplicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Concurrent fan-out search: bounded concurrency, progressive partial results,
 * per-provider failure isolation.
 */
class SearchMusicUseCase @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val preferencesRepository: UserPreferencesRepository,
    private val deduplicator: TrackDeduplicator,
    private val ranker: SearchRanker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    operator fun invoke(
        rawQuery: String,
        category: SearchCategory = SearchCategory.ALL,
        sortOption: SearchSortOption = SearchSortOption.RELEVANCE
    ): Flow<AggregatedSearchState> = channelFlow {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            send(AggregatedSearchState(query = query, isComplete = true))
            return@channelFlow
        }

        val prefs = preferencesRepository.userPreferences.first()
        val allProviders = providerRegistry.getAllActiveProviders()
        val enabledProviders = allProviders.filter { it.id.value in prefs.enabledProviderIds }

        val stateMutex = Mutex()
        val providerStates = mutableMapOf<ProviderId, ProviderSearchState>()
        for (p in allProviders) {
            providerStates[p.id] =
                if (p.id.value in prefs.enabledProviderIds) ProviderSearchState.Loading else ProviderSearchState.Disabled
        }

        if (enabledProviders.isEmpty()) {
            send(AggregatedSearchState(query = query, providerStates = providerStates.toMap(), isComplete = true))
            return@channelFlow
        }

        val tracksAcc = mutableListOf<MusicTrack>()
        val artistsAcc = mutableListOf<MusicArtist>()
        val albumsAcc = mutableListOf<MusicAlbum>()

        send(AggregatedSearchState(query = query, providerStates = providerStates.toMap(), isComplete = false))

        // Must be called while holding stateMutex.
        suspend fun ProducerScope<AggregatedSearchState>.emitSnapshot(isComplete: Boolean) {
            val ranked = ranker.rankGroups(deduplicator.groupDuplicates(tracksAcc), query, sortOption)
            send(
                AggregatedSearchState(
                    query = query,
                    results = ranked.map { it.primaryTrack },
                    trackGroups = ranked,
                    artists = artistsAcc.toList(),
                    albums = albumsAcc.toList(),
                    providerStates = providerStates.toMap(),
                    isComplete = isComplete
                )
            )
        }

        val semaphore = Semaphore(MAX_CONCURRENT_PROVIDERS)
        val producer = this

        supervisorScope {
            enabledProviders.forEach { provider ->
                launch {
                    semaphore.withPermit {
                        try {
                            withTimeout(PROVIDER_SEARCH_TIMEOUT_MS) {
                                val caps = provider.capabilities()
                                coroutineScope {
                                    val wantTracks = (category == SearchCategory.ALL || category == SearchCategory.TRACKS) && caps.searchTracks
                                    val wantArtists = (category == SearchCategory.ALL || category == SearchCategory.ARTISTS) && caps.searchArtists
                                    val wantAlbums = (category == SearchCategory.ALL || category == SearchCategory.ALBUMS) && caps.searchAlbums

                                    val tracksD = if (wantTracks) async { provider.searchTracks(query).items } else null
                                    val artistsD = if (wantArtists) async { softList { provider.searchArtists(query).items } } else null
                                    val albumsD = if (wantAlbums) async { softList { provider.searchAlbums(query).items } } else null

                                    val tracks = tracksD?.await().orEmpty()
                                    val artists = artistsD?.await().orEmpty()
                                    val albums = albumsD?.await().orEmpty()

                                    stateMutex.withLock {
                                        tracksAcc.addAll(tracks)
                                        artistsAcc.addAll(artists)
                                        albumsAcc.addAll(albums)
                                        providerStates[provider.id] =
                                            ProviderSearchState.Success(tracks.size + artists.size + albums.size)
                                        with(producer) { emitSnapshot(isComplete = false) }
                                    }
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            // Must be caught BEFORE CancellationException: it is a subclass.
                            recordFailure(stateMutex, providerStates, provider, e) {
                                with(producer) { emitSnapshot(isComplete = false) }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            recordFailure(stateMutex, providerStates, provider, e) {
                                with(producer) { emitSnapshot(isComplete = false) }
                            }
                        }
                    }
                }
            }
        }

        stateMutex.withLock { emitSnapshot(isComplete = true) }
    }.flowOn(ioDispatcher)

    private suspend fun recordFailure(
        mutex: Mutex,
        states: MutableMap<ProviderId, ProviderSearchState>,
        provider: MusicProvider,
        error: Throwable,
        emit: suspend () -> Unit
    ) {
        mutex.withLock {
            states[provider.id] = mapThrowableToProviderState(provider, error)
            emit()
        }
    }

    /** Secondary lookups (artists/albums) must never fail the provider, but must not swallow cancellation. */
    private suspend fun <T> softList(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        emptyList()
    }

    private fun mapThrowableToProviderState(provider: MusicProvider, t: Throwable): ProviderSearchState = when (t) {
        is ProviderException -> when (t.code) {
            ProviderErrorCode.PROVIDER_RATE_LIMITED -> ProviderSearchState.RateLimited(t.retryAfterMs)
            ProviderErrorCode.AUTH_REQUIRED -> ProviderSearchState.AuthenticationRequired(t.safeMessage)
            else -> ProviderSearchState.Failed(t.safeMessage)
        }
        is TimeoutCancellationException -> ProviderSearchState.Failed("${provider.displayName} timed out")
        else -> ProviderSearchState.Failed("${provider.displayName} is temporarily unavailable")
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        private const val MAX_CONCURRENT_PROVIDERS = 4
        private const val PROVIDER_SEARCH_TIMEOUT_MS = 12_000L
    }
}
