package com.mosu.app.data.repository

import com.mosu.app.data.api.RetrofitClient
import com.mosu.app.data.api.model.BeatmapPlaycount
import com.mosu.app.data.api.model.BeatmapsetCompact
import com.mosu.app.data.api.model.OsuTokenResponse

import com.mosu.app.data.api.model.OsuUserCompact

class OsuRepository {
    private val api = RetrofitClient.api
    
    // In a real app, these should be in a secure config or BuildConfig
    // For this MVP, we will need the user to provide them or hardcode them temporarily for testing
    // PLEASE REPLACE THESE WITH YOUR ACTUAL VALUES FOR TESTING
    private val clientId = "46495"
    private val clientSecret = "r2GtbG4jEEJBkXM4LhEo10tqTUVrdnx3aSfOergh"
    private val redirectUri = "mosu://callback"

    suspend fun exchangeCodeForToken(code: String): OsuTokenResponse {
        return api.getToken(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            grantType = "authorization_code",
            redirectUri = redirectUri
        )
    }

    suspend fun getMe(accessToken: String): OsuUserCompact {
        return api.getMe("Bearer $accessToken")
    }

    suspend fun getUserMostPlayed(accessToken: String, userId: String): List<BeatmapPlaycount> {
        return api.getUserMostPlayed("Bearer $accessToken", userId)
    }

    suspend fun getPlayedBeatmaps(accessToken: String): List<BeatmapsetCompact> {
        // Try to filter by 'played' status
        val response = api.searchBeatmapsets(
            authHeader = "Bearer $accessToken",
            played = "played"
        )
        return response.beatmapsets
    }
}

