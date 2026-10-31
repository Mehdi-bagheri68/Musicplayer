package com.example.universalmusic.providers.audius

import retrofit2.http.GET
import retrofit2.http.Query

/** Audius REST API v1 (https://docs.audius.co/api). */
interface AudiusApiService {

    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("app_name") appName: String
    ): AudiusResponseDto<AudiusTrackDto>

    @GET("v1/users/search")
    suspend fun searchUsers(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("app_name") appName: String
    ): AudiusResponseDto<AudiusUserDto>

    @GET("v1/playlists/search")
    suspend fun searchPlaylists(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("app_name") appName: String
    ): AudiusResponseDto<AudiusPlaylistDto>

    companion object {
        const val BASE_URL = "https://api.audius.co/"
    }
}
