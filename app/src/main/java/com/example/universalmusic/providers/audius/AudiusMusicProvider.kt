package com.example.universalmusic.providers.audius

import com.example.universalmusic.BuildConfig
import com.example.universalmusic.core.network.RateLimiter
import com.example.universalmusic.core.network.StreamUrlValidator
import com.example.universalmusic.domain.error.ProviderErrorCode
import com.example.universalmusic.domain.error.ProviderException
import com.example.universalmusic.domain.model.Attribution
import com.example.universalmusic.domain.model.MusicAlbum
import com.example.universalmusic.domain.model.MusicArtist
import com.example.universalmusic.domain.model.MusicTrack
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.PlaybackDescriptor
import com.example.universalmusic.domain.model.PlaybackMode
import com.example.universalmusic.domain.model.ProviderCapabilities
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.provider.MusicProvider
import com.example.universalmusic.domain.provider.PlaybackResolution
import com.example.universalmusic.domain.provider.ProviderPage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiusMusicProvider @Inject constructor(
    private val apiService: AudiusApiService,
    private val mapper: AudiusMapper,
    private val streamUrlValidator: StreamUrlValidator
) : MusicProvider {

    override val id: ProviderId = ProviderId.AUDIUS
    override val displayName: String = "Audius"

    // Free tier: ~10 req/s -> 110 ms minimum interval.
    private val rateLimiter = RateLimiter(minIntervalMs = 110L)

    private val appName: String
        get() = BuildConfig.AUDIUS_APP_NAME.ifBlank { "UniversalMusic" }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        searchTracks = true,
        searchArtists = true,
        searchAlbums = true,
        searchPlaylists = true,
        directStream = true,
        preview = false,
        officialPlayer = false,
        requiresUserLogin = false,
        requiresSubscription = false
    )

    override suspend fun searchTracks(query: String, page: String?): ProviderPage<MusicTrack> = executeSafely {
        val offset = page?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        rateLimiter.acquire()
        val response = apiService.searchTracks(query = query, limit = PAGE_SIZE, offset = offset, appName = appName)
        val now = System.currentTimeMillis()
        pageOf(response.data.map { mapper.mapTrack(it, now) }, offset)
    }

    override suspend fun searchArtists(query: String, page: String?): ProviderPage<MusicArtist> = executeSafely {
        val offset = page?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        rateLimiter.acquire()
        val response = apiService.searchUsers(query = query, limit = PAGE_SIZE, offset = offset, appName = appName)
        pageOf(response.data.map(mapper::mapArtist), offset)
    }

    override suspend fun searchAlbums(query: String, page: String?): ProviderPage<MusicAlbum> = executeSafely {
        val offset = page?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        rateLimiter.acquire()
        val response = apiService.searchPlaylists(query = query, limit = PAGE_SIZE, offset = offset, appName = appName)
        pageOf(response.data.map(mapper::mapAlbum), offset)
    }

    override suspend fun resolvePlayback(track: MusicTrack): PlaybackResolution {
        if (track.providerId != id) return PlaybackResolution.Unavailable("Track does not belong to Audius")
        if (track.playbackAvailability != PlaybackAvailability.FULL_STREAM) {
            return PlaybackResolution.ExternalOnly(track.sourceUrl)
        }

        val encodedTrackId = URLEncoder.encode(track.providerItemId, Charsets.UTF_8.name())
        val encodedAppName = URLEncoder.encode(appName, Charsets.UTF_8.name())
        val streamUrl = "${AudiusApiService.BASE_URL}v1/tracks/$encodedTrackId/stream?app_name=$encodedAppName"

        if (!streamUrlValidator.isValidStreamUrl(id, streamUrl, "audio/mpeg")) {
            return PlaybackResolution.Unavailable("Stream URL failed security validation")
        }

        val headers = mutableMapOf<String, String>()
        if (BuildConfig.AUDIUS_API_KEY.isNotBlank()) headers["x-api-key"] = BuildConfig.AUDIUS_API_KEY

        return PlaybackResolution.Ready(
            PlaybackDescriptor(
                providerId = id,
                trackId = track.id,
                mode = PlaybackMode.MEDIA3_DIRECT_STREAM,
                uri = streamUrl,
                mimeType = "audio/mpeg",
                expiresAtEpochMs = System.currentTimeMillis() + STREAM_URL_TTL_MS,
                requiredHeaders = headers
            )
        )
    }

    override fun attributionFor(track: MusicTrack): Attribution {
        val artistPart = track.artistName?.let { " • $it" }.orEmpty()
        return Attribution(
            providerId = id,
            providerDisplayName = displayName,
            attributionText = track.rights?.attributionText ?: "Provided by Audius$artistPart",
            sourceUrl = track.sourceUrl,
            licenseUrl = track.rights?.licenseUrl
        )
    }

    private fun <T> pageOf(items: List<T>, offset: Int): ProviderPage<T> {
        val hasMore = items.size >= PAGE_SIZE
        return ProviderPage(items, if (hasMore) (offset + PAGE_SIZE).toString() else null, hasMore)
    }

    private suspend fun <T> executeSafely(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderException) {
            throw e
        } catch (e: HttpException) {
            val status = e.code()
            val retryAfterSeconds = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
            val code = when (status) {
                401, 403 -> ProviderErrorCode.AUTH_REQUIRED
                429 -> ProviderErrorCode.PROVIDER_RATE_LIMITED
                451 -> ProviderErrorCode.REGION_RESTRICTED
                else -> ProviderErrorCode.UNKNOWN_PROVIDER_ERROR
            }
            throw ProviderException(id, code, "Audius HTTP $status", status, retryAfterSeconds?.times(1_000L), e)
        } catch (e: SocketTimeoutException) {
            throw ProviderException(id, ProviderErrorCode.PROVIDER_TIMEOUT, "Audius request timed out", cause = e)
        } catch (e: SerializationException) {
            throw ProviderException(
                id, ProviderErrorCode.UNKNOWN_PROVIDER_ERROR, "Audius returned an unexpected response format", cause = e
            )
        } catch (e: IOException) {
            throw ProviderException(
                id, ProviderErrorCode.NETWORK_UNAVAILABLE, "Network error while contacting Audius", cause = e
            )
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val STREAM_URL_TTL_MS = 30 * 60 * 1_000L
    }
}
