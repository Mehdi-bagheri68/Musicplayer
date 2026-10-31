package com.example.universalmusic.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "universal_music_preferences"
)

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setProviderEnabled(providerId: String, enabled: Boolean)
    suspend fun setHistoryEnabled(enabled: Boolean)
    suspend fun setMaxResultsPerProvider(limit: Int)
    suspend fun recordCacheCleared(timestampEpochMs: Long)
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val ENABLED_PROVIDERS = stringSetPreferencesKey("enabled_provider_ids")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val MAX_RESULTS = intPreferencesKey("max_results_per_provider")
        val LAST_CACHE_CLEARED = longPreferencesKey("last_cache_cleared_ms")
    }

    override val userPreferences: Flow<UserPreferences> = context.userPreferencesDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserPreferences(
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                    ?: AppThemeMode.DARK,
                language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.SYSTEM,
                enabledProviderIds = prefs[Keys.ENABLED_PROVIDERS] ?: UserPreferences.DEFAULT_ENABLED_PROVIDERS,
                historyEnabled = prefs[Keys.HISTORY_ENABLED] ?: true,
                maxResultsPerProvider = (prefs[Keys.MAX_RESULTS] ?: 20).coerceIn(5, 50),
                lastCacheClearedEpochMs = prefs[Keys.LAST_CACHE_CLEARED] ?: 0L
            )
        }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        context.userPreferencesDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        context.userPreferencesDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        context.userPreferencesDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_PROVIDERS] ?: UserPreferences.DEFAULT_ENABLED_PROVIDERS
            prefs[Keys.ENABLED_PROVIDERS] = if (enabled) current + providerId else current - providerId
        }
    }

    override suspend fun setHistoryEnabled(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.HISTORY_ENABLED] = enabled }
    }

    override suspend fun setMaxResultsPerProvider(limit: Int) {
        context.userPreferencesDataStore.edit { it[Keys.MAX_RESULTS] = limit.coerceIn(5, 50) }
    }

    override suspend fun recordCacheCleared(timestampEpochMs: Long) {
        context.userPreferencesDataStore.edit { it[Keys.LAST_CACHE_CLEARED] = timestampEpochMs }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {
    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository
}
