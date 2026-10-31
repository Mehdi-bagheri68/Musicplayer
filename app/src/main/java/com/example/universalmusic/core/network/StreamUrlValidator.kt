package com.example.universalmusic.core.network

import com.example.universalmusic.domain.model.ProviderId
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 1. HTTPS only. 2. Host must match the provider allowlist. 3. Optional MIME check.
 */
@Singleton
class StreamUrlValidator @Inject constructor() {

    private val allowedHostSuffixesByProvider: Map<ProviderId, List<String>> = mapOf(
        ProviderId.AUDIUS to listOf("audius.co", "audiusindex.org", "blockdaemon.com", "figment.io", "alchemy.com"),
        ProviderId.JAMENDO to listOf("jamendo.com"),
        ProviderId.ARCHIVE to listOf("archive.org")
    )

    private val allowedAudioMimeTypes: Set<String> = setOf(
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/aac", "audio/ogg",
        "application/ogg", "application/vnd.apple.mpegurl", "application/x-mpegurl"
    )

    fun isValidStreamUrl(providerId: ProviderId, rawUrl: String?, mimeType: String? = null): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.ROOT) != "https") return false
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
        val allowed = allowedHostSuffixesByProvider[providerId] ?: return false
        if (allowed.none { host == it || host.endsWith(".$it") }) return false
        if (!mimeType.isNullOrBlank()) {
            val mime = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
            if (mime !in allowedAudioMimeTypes) return false
        }
        return true
    }
}
