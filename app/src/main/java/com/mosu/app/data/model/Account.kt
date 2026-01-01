package com.mosu.app.data.model

import com.mosu.app.data.api.model.OsuUserCompact

data class Account(
    val id: String, // Unique identifier for the account
    val clientId: String,
    val clientSecret: String,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long? = null,
    val userInfo: OsuUserCompact? = null
) {
    val isLoggedIn: Boolean
        get() = accessToken != null && userInfo != null

    val isTokenExpired: Boolean
        get() = tokenExpiry != null && System.currentTimeMillis() >= tokenExpiry
}
