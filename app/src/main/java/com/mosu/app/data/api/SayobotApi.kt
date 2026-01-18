package com.mosu.app.data.api

import com.mosu.app.data.api.model.SayobotDetailResponse
import com.mosu.app.data.api.model.SayobotSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SayobotApi {
    
    @GET("beatmaplist")
    suspend fun searchBeatmapsets(
        @Query("L") limit: Int = 25,
        @Query("O") offset: Int = 0,
        @Query("T") type: Int = 4, // 4 = search
        @Query("K") keyword: String? = null,
        @Query("M") mode: Int? = null,
        @Query("C") classFilter: Int? = null,
        @Query("G") genre: Int? = null,
        @Query("A") language: Int? = null
    ): SayobotSearchResponse

    @GET("v2/beatmapinfo")
    suspend fun getBeatmapsetDetail(
        @Query("K") sid: Long,
        @Query("T") type: Int = 0 // 0 = sid
    ): SayobotDetailResponse
}

