package com.mosu.app.data.api

import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.OsuTokenResponse
import com.mosu.app.data.api.model.OsuUserCompact
import com.mosu.app.data.api.model.SearchResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

import okhttp3.ResponseBody
import retrofit2.http.Streaming

interface OsuApi {
    
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun getToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String,
        @Field("redirect_uri") redirectUri: String
    ): OsuTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String,
        @Field("refresh_token") refreshToken: String
    ): OsuTokenResponse

    @GET("api/v2/me")
    suspend fun getMe(
        @Header("Authorization") authHeader: String
    ): OsuUserCompact

    @GET("api/v2/users/{user_id}/beatmapsets/most_played")
    suspend fun getUserMostPlayed(
        @Header("Authorization") authHeader: String,
        @Path("user_id") userId: String,
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): List<BeatmapPlaycount>

    @GET("api/v2/users/{user_id}/scores/recent")
    suspend fun getUserRecentScores(
        @Header("Authorization") authHeader: String,
        @Path("user_id") userId: String,
        @Query("limit") limit: Int = 50,
        // Do NOT use Boolean here directly unless you have a custom converter.
        @Query("include_fails") includeFails: Int = 1,
        @Query("mode") mode: String = "osu"
        // Curosr for scrolling behavior should be implemented in the fetching method in future
    ): List<com.mosu.app.data.api.model.RecentScore>


    @GET("api/v2/users/{user_id}/beatmapsets/favourite")
    suspend fun getUserFavoriteBeatmapsets(
        @Header("Authorization") authHeader: String,
        @Path("user_id") userId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<com.mosu.app.data.api.model.BeatmapsetCompact>

    @GET("api/v2/beatmapsets/search")
    suspend fun searchBeatmapsets(
        @Header("Authorization") authHeader: String,
        @Query("played") played: String? = null,
        @Query("q") query: String? = null,
        @Query("g") genre: Int? = null,
        @Query("s") status: String? = null,
        @Query("cursor_string") cursorString: String? = null
    ): SearchResponse

    @GET("api/v2/beatmapsets/{beatmapset_id}")
    suspend fun getBeatmapsetDetail(
        @Header("Authorization") authHeader: String,
        @Path("beatmapset_id") beatmapsetId: Long
    ): com.mosu.app.data.api.model.BeatmapsetDetail
}

