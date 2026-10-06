package com.example.m_agrilink.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * M-AgriLink Core Engine — Engineered and Directed by Lead System Architect Levis Lekesio.
 *
 * Live national input telemetry: pulls seasonal subsidized pricing
 * (fertilizer / certified seed baselines) matching current Ministry of
 * Agriculture directives. Any network exception — unknown host, timeout,
 * malformed payload — fails closed to null so the planner thread-safely
 * falls back to the cached 2026 pricing baselines.
 *
 * NOTE: [SUBSIDY_BASE_URL] is a reserved, non-routable placeholder
 * (RFC 2606 `.invalid` never resolves, so the attempt fails fast with no
 * traffic sent anywhere). Point it at the live Ministry endpoint the
 * moment an official URL is issued; until then the cached baselines are
 * the source of truth by design.
 */
data class SubsidyPriceDto(
    val key: String = "",
    val priceKes: Int = 0
)

data class SubsidyPriceResponse(
    val updated: String = "",
    val prices: List<SubsidyPriceDto> = emptyList()
)

interface SubsidyPriceService {
    @GET("subsidy-prices")
    suspend fun getPrices(): SubsidyPriceResponse
}

object SaccoTelemetry {

    const val SUBSIDY_BASE_URL = "https://subsidy-telemetry.invalid/v1/"

    /** Override keys accepted from the live payload (unknown keys are ignored). */
    val KNOWN_KEYS: Set<String> = setOf(
        "mango_seedling",
        "mango_compost_50kg",
        "mango_trap",
        "beans_seed_2kg",
        "beans_npk_50kg",
        "maize_seed_2kg",
        "maize_seed_10kg",
        "maize_dap_50kg",
        "maize_compost_50kg"
    )

    suspend fun fetchBaselines(): Map<String, Int>? = fetchFeed()?.first

    suspend fun fetchFeed(): Pair<Map<String, Int>, String>? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()
            val service = Retrofit.Builder()
                .baseUrl(SUBSIDY_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SubsidyPriceService::class.java)
            val response = service.getPrices()
            val overrides = response.prices
                .filter { it.key in KNOWN_KEYS && it.priceKes > 0 }
                .associate { it.key to it.priceKes }
            if (overrides.isEmpty()) null else overrides to response.updated
        } catch (e: Exception) {
            null
        }
    }
}
