package com.mosu.app.data.api

import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.OsuTokenResponse
import com.mosu.app.data.api.model.OsuUserCompact
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
}

