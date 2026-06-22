package com.mosu.app.utils

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.telephony.TelephonyManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class RegionInfo(
    val countryCode: String,
    val regionName: String,
    val source: String = "unknown"
)

object RegionUtils {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    suspend fun getDeviceRegion(context: Context? = null): RegionInfo? = withContext(Dispatchers.IO) {
        getDurableDeviceRegion(context)?.let { return@withContext it }

        val services = listOf(
            RegionService("https://ipapi.co/json/", listOf("country_code"), listOf("region"), "ipapi"),
            RegionService("https://ipinfo.io/json", listOf("country"), listOf("region"), "ipinfo"),
            RegionService(
                "https://forge.speedtest.cn/api/location/info",
                listOf("country_code", "countryCode", "country"),
                listOf("province", "region"),
                "speedtest"
            )
        )

        for (service in services) {
            val region = tryService(service)
            if (region != null && region.countryCode.isNotEmpty()) return@withContext region
        }

        readLocaleCountry()?.let { return@withContext it }

        return@withContext null
    }

    fun preferredMirrorFor(region: RegionInfo?): String {
        return if (region?.countryCode.equals("CN", ignoreCase = true)) "sayobot" else "nerinyan"
    }

    private fun getDurableDeviceRegion(context: Context?): RegionInfo? {
        readTelephonyCountry(context)?.let { return it }
        return readTimezoneCountry()
    }

    private fun readTelephonyCountry(context: Context?): RegionInfo? {
        val telephonyManager = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null

        return runCatching {
            listOf(
                "network" to telephonyManager.networkCountryIso,
                "sim" to telephonyManager.simCountryIso
            ).firstNotNullOfOrNull { (type, country) ->
                normalizeCountryCode(country)?.let { RegionInfo(it, "", "telephony_$type") }
            }
        }.getOrNull()
    }

    private fun readLocaleCountry(): RegionInfo? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val locales = LocaleList.getDefault()
                for (index in 0 until locales.size()) {
                    add(locales[index].country)
                }
            }
            add(Locale.getDefault().country)
        }

        return candidates.firstNotNullOfOrNull { country ->
            normalizeCountryCode(country)?.let { RegionInfo(it, "", "locale") }
        }
    }

    private fun readTimezoneCountry(): RegionInfo? {
        val timezone = TimeZone.getDefault().id
        val country = when (timezone) {
            "Asia/Shanghai", "Asia/Chongqing", "Asia/Harbin", "Asia/Urumqi" -> "CN"
            else -> null
        }
        return country?.let { RegionInfo(it, timezone, "timezone") }
    }

    private fun normalizeCountryCode(country: String?): String? {
        val normalized = country?.trim()?.uppercase(Locale.US).orEmpty()
        return normalized.takeIf { it.length == 2 && it.all { char -> char.isLetter() } }
    }

    private fun tryService(service: RegionService): RegionInfo? {
        val request = Request.Builder()
            .url(service.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val jsonStr = response.body?.string()
                android.util.Log.d("RegionUtils", "URL: ${service.url} | Code: ${response.code} | Response: $jsonStr")

                if (response.isSuccessful && jsonStr != null) {
                    val json = JSONObject(jsonStr)
                    val countryCode = service.codeKeys.firstNotNullOfOrNull { normalizeCountryCode(json.optString(it)) }
                    val regionName = service.nameKeys.firstNotNullOfOrNull { key ->
                        json.optString(key).takeIf { value -> value.isNotBlank() }
                    }
                        .orEmpty()
                    countryCode?.let { RegionInfo(it, regionName, service.source) }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RegionUtils", "Request failed for ${service.url}: ${e.message}")
            null
        }
    }

    private data class RegionService(
        val url: String,
        val codeKeys: List<String>,
        val nameKeys: List<String>,
        val source: String
    )
}
