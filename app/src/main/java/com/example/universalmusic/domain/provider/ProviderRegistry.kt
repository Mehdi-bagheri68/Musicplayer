package com.example.universalmusic.domain.provider

import com.example.universalmusic.domain.model.ProviderCapabilities
import com.example.universalmusic.domain.model.ProviderId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRegistry @Inject constructor(
    private val activeProviders: Set<@JvmSuppressWildcards MusicProvider>
) {
    fun getAllActiveProviders(): List<MusicProvider> = activeProviders.sortedBy { it.displayName }

    fun getEnabledProviders(enabledIds: Set<String>): List<MusicProvider> =
        activeProviders.filter { it.id.value in enabledIds }.sortedBy { it.displayName }

    fun getProvider(id: ProviderId): MusicProvider? = activeProviders.firstOrNull { it.id == id }

    fun getBlockedNoBackendProviders(): List<ProviderInfo> = listOf(
        blocked(
            ProviderId.SOUNDCLOUD, "SoundCloud",
            "SoundCloud OAuth requires a confidential client_secret, which cannot be safely embedded in a client-only APK.",
            requiresSubscription = false
        ),
        blocked(
            ProviderId.APPLE_MUSIC, "Apple Music",
            "Apple Music API requires signing JWT developer tokens with a private .p8 key on a backend server.",
            requiresSubscription = true
        )
    )

    private fun blocked(id: ProviderId, name: String, description: String, requiresSubscription: Boolean) =
        ProviderInfo(
            id = id,
            displayName = name,
            status = ProviderCatalogStatus.BLOCKED_NO_BACKEND,
            statusDescription = description,
            capabilities = ProviderCapabilities(
                searchTracks = false, searchArtists = false, searchAlbums = false, searchPlaylists = false,
                directStream = false, preview = false, officialPlayer = false,
                requiresUserLogin = true, requiresSubscription = requiresSubscription
            )
        )
}
