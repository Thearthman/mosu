package com.mosu.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://osu.ppy.sh/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Base client without auth
    private val baseClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "MosuAndroid/1.0 (com.mosu.app)")
                .build()
            chain.proceed(request)
        }
        .build()

    // Client with authenticator (set later)
    private var authenticatedClient: OkHttpClient? = null

    val api: OsuApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authenticatedClient ?: baseClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsuApi::class.java)
    }

    val okHttpClient: OkHttpClient
        get() = authenticatedClient ?: baseClient

    fun configureAuthenticator(authenticator: okhttp3.Authenticator) {
        authenticatedClient = baseClient.newBuilder()
            .authenticator(authenticator)
            .build()
    }
}

