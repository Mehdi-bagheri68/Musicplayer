package com.example.universalmusic.providers.audius

import com.example.universalmusic.core.network.StreamUrlValidator
import com.example.universalmusic.domain.model.PlaybackAvailability
import com.example.universalmusic.domain.model.ProviderId
import com.example.universalmusic.domain.provider.PlaybackResolution
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AudiusMusicProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: AudiusMusicProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AudiusApiService::class.java)
        provider = AudiusMusicProvider(api, AudiusMapper(), StreamUrlValidator())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `searchTracks maps Audius JSON to MusicTrack and resolves stream`() = runTest {
        val body = """
            {"data":[{"id":"J5NMd","title":"Soulful Tehran","permalink":"/lindecis/soulful-tehran",
            "duration":221,"is_streamable":true,"genre":"Electronic","release_date":"2024-05-10T00:00:00Z",
            "license":"All rights reserved",
            "artwork":{"480x480":"https://cn3.mainnet.audiusindex.org/content/abc/480x480.jpg"},
            "user":{"id":"noBB7","name":"Lindecis","handle":"lindecis","is_verified":true}}]}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val page = provider.searchTracks("Tehran")
        assertEquals(1, page.items.size)

        val track = page.items.first()
        assertEquals("audius:J5NMd", track.id)
        assertEquals(ProviderId.AUDIUS, track.providerId)
        assertEquals("Lindecis", track.artistName)
        assertEquals(221_000L, track.durationMs)
        assertEquals(PlaybackAvailability.FULL_STREAM, track.playbackAvailability)
        assertEquals("https://audius.co/lindecis/soulful-tehran", track.sourceUrl)

        val resolution = provider.resolvePlayback(track)
        assertTrue(resolution is PlaybackResolution.Ready)
        val uri = (resolution as PlaybackResolution.Ready).descriptor.uri!!
        assertTrue(uri.startsWith("https://api.audius.co/v1/tracks/J5NMd/stream"))
    }
}
