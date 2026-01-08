package com.mosu.app.data.api

import com.mosu.app.data.SettingsManager
import com.mosu.app.data.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val OSU_BASE_URL = "https://osu.ppy.sh/"
    private const val SAYOBOT_BASE_URL = "https://api.sayobot.cn/"

    private var apiSource = "osu"

    fun setApiSource(source: String) {
        apiSource = source
    }

    fun getApiSource(): String = apiSource

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

    // Client with authenticator and interceptor (set later)
    private var authenticatedClient: OkHttpClient? = null

    val api: OsuApi
        get() = Retrofit.Builder()
            .baseUrl(OSU_BASE_URL)
            .client(authenticatedClient ?: baseClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsuApi::class.java)

    val sayobotApi: SayobotApi
        get() = Retrofit.Builder()
            .baseUrl(SAYOBOT_BASE_URL)
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SayobotApi::class.java)

    val okHttpClient: OkHttpClient
        get() = authenticatedClient ?: baseClient

    // Provide access to base client for token refresh operations
    fun getBaseClient(): OkHttpClient = baseClient

    fun configureAuthentication(
        authenticator: okhttp3.Authenticator,
        tokenManager: TokenManager,
        settingsManager: SettingsManager
    ) {
        val authInterceptor = AuthInterceptor(tokenManager, settingsManager)

        authenticatedClient = baseClient.newBuilder()
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .build()
    }
}

