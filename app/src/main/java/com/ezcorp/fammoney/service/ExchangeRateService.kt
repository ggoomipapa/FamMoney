package com.ezcorp.fammoney.service

import android.util.Log
import com.ezcorp.fammoney.BuildConfig
import com.ezcorp.fammoney.di.AppModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExchangeRateService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val API_KEY = BuildConfig.EXCHANGE_RATE_API_KEY
    private val BASE_URL = "https://v6.exchangerate-api.com/v6/"

    // In a real app, you might want to store and manage a cache of rates
    // to avoid hitting the API too often and for offline support.
    // For simplicity, we'll fetch live every time for now.

    suspend fun getExchangeRate(baseCurrency: String, targetCurrency: String): Double? {
        if (API_KEY.isEmpty()) {
            Log.e("ExchangeRateService", "API Key is not set for ExchangeRate-API.com")
            return null
        }
        if (baseCurrency == targetCurrency) {
            return 1.0
        }

        val url = "$BASE_URL$API_KEY/latest/$baseCurrency"
        val request = Request.Builder().url(url).build()

        return withContext(Dispatchers.IO) {
            try {
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val apiResponse = json.decodeFromString<ExchangeRateApiResponse>(responseBody)
                        if (apiResponse.result == "success") {
                            apiResponse.conversion_rates?.get(targetCurrency)
                        } else {
                            Log.e("ExchangeRateService", "API Error: ${apiResponse.error_type}")
                            null
                        }
                    } else {
                        Log.e("ExchangeRateService", "Empty response body from ExchangeRate-API")
                        null
                    }
                } else {
                    Log.e("ExchangeRateService", "HTTP Error: ${response.code} - ${response.message}")
                    null
                }
            } catch (e: IOException) {
                Log.e("ExchangeRateService", "Network error fetching exchange rate: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e("ExchangeRateService", "Error parsing exchange rate response: ${e.message}")
                null
            }
        }
    }
}

@Serializable
data class ExchangeRateApiResponse(
    val result: String,
    val documentation: String? = null,
    val terms_of_use: String? = null,
    val time_last_update_unix: Long? = null,
    val time_last_update_utc: String? = null,
    val time_next_update_unix: Long? = null,
    val time_next_update_utc: String? = null,
    val base_code: String? = null,
    val conversion_rates: Map<String, Double>? = null,
    val error_type: String? = null // For API error messages
)
