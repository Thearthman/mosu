package com.mosu.app.data.api

import com.mosu.app.data.api.model.SayobotDetailResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface SayobotApi {
    @GET("v2/beatmapinfo")
    suspend fun getBeatmapInfo(
        @Query("K") beatmapSetId: Long,
        @Query("T") type: Int = 0
    ): SayobotDetailResponse
}
