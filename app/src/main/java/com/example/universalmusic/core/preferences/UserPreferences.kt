package com.example.universalmusic.core.preferences

enum class AppThemeMode { DARK, LIGHT, SYSTEM }

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    PERSIAN("fa")
}

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val enabledProviderIds: Set<String> = DEFAULT_ENABLED_PROVIDERS,
    val historyEnabled: Boolean = true,
    val maxResultsPerProvider: Int = 20,
    val lastCacheClearedEpochMs: Long = 0L
) {
    companion object {
        val DEFAULT_ENABLED_PROVIDERS: Set<String> = setOf("audius", "jamendo", "archive", "musicbrainz")
    }
}
