package com.example.universalmusic.providers.di

import com.example.universalmusic.BuildConfig
import com.example.universalmusic.domain.provider.MusicProvider
import com.example.universalmusic.providers.audius.AudiusApiService
import com.example.universalmusic.providers.audius.AudiusMusicProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProvidersBindingModule {
    @Binds
    @IntoSet
    abstract fun bindAudiusProvider(provider: AudiusMusicProvider): MusicProvider
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidersNetworkModule {

    @Provides
    @Singleton
    fun provideAudiusApiService(baseOkHttpClient: OkHttpClient, json: Json): AudiusApiService {
        // Provider-scoped client so the API key is only sent to Audius.
        val client = baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder().header("Accept", "application/json")
                if (BuildConfig.AUDIUS_API_KEY.isNotBlank()) builder.header("x-api-key", BuildConfig.AUDIUS_API_KEY)
                chain.proceed(builder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(AudiusApiService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AudiusApiService::class.java)
    }
}
