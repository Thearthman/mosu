package com.mosu.app.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RegionInfo(val countryCode: String, val regionName: String)

object RegionUtils {
    private val client = OkHttpClient()

    suspend fun getDeviceRegion(): RegionInfo? = withContext(Dispatchers.IO) {
        // Try Service 1: ipapi.co (International)
        val region = tryService("https://ipapi.co/json/", "country_code", "region")
        if (region != null && region.countryCode.isNotEmpty()) return@withContext region

        // Try Service 2: speedtest.cn (Domestic - very reliable in CN)
        android.util.Log.d("RegionUtils", "Falling back to speedtest.cn...")
        val fallback = tryService("https://forge.speedtest.cn/api/location/info", "country_code", "province")
        if (fallback != null && fallback.countryCode.isNotEmpty()) return@withContext fallback

        return@withContext null
    }

    private fun tryService(url: String, codeKey: String, nameKey: String): RegionInfo? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val jsonStr = response.body?.string()
            android.util.Log.d("RegionUtils", "URL: $url | Code: ${response.code} | Response: $jsonStr")
            
            if (response.isSuccessful && jsonStr != null) {
                val json = JSONObject(jsonStr)
                RegionInfo(
                    countryCode = json.optString(codeKey),
                    regionName = json.optString(nameKey)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("RegionUtils", "Request failed for $url: ${e.message}")
            null
        }
    }
}
