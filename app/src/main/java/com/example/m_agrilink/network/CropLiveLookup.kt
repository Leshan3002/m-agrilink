package com.example.m_agrilink.network

import android.content.Context
import com.example.m_agrilink.BuildConfig
import com.example.m_agrilink.data.local.AgriLinkDatabase
import com.example.m_agrilink.data.local.CropAdvisory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class WikiSummary(
    val title: String?,
    val description: String?,
    val extract: String?
)

interface WikiApi {
    @GET("api/rest_v1/page/summary/{title}")
    fun summary(@Path("title") title: String): retrofit2.Call<WikiSummary>
}

data class OpenFarmResponse(val data: List<OpenFarmCrop>?)
data class OpenFarmCrop(val id: String?, val attributes: OpenFarmAttributes?)
data class OpenFarmAttributes(
    val name: String?,
    val description: String?,
    val sun_requirements: String?,
    val sowing_method: String?,
    val spread: Int?,
    val row_spacing: Int?,
    val height: Int?
)

interface OpenFarmApi {
    @GET("api/v1/crops/")
    fun search(@Query("filter") name: String): retrofit2.Call<OpenFarmResponse>
}

/**
 * Live brief for ANY crop: Room cache first (instant + offline),
 * then Gemini AI, OpenFarm growing guides, and Wikipedia.
 * Returns (briefText, source). Sources: CACHE, GEMINI, OPENFARM, WIKI, OFFLINE.
 */
class CropLiveLookup(context: Context) {

    private val dao = AgriLinkDatabase.getDatabase(context.applicationContext).farmerDao()

    private val gemini = GeminiRepository(BuildConfig.GEMINI_API_KEY)

    private fun timedRetrofit(baseUrl: String): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val wikiApi = timedRetrofit("https://en.wikipedia.org/").create(WikiApi::class.java)

    private val openFarmApi = timedRetrofit("https://openfarm.cc/").create(OpenFarmApi::class.java)

    suspend fun lookupCrop(
        crop: String,
        county: String,
        temp: Double,
        humidity: Int,
        wind: Double
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val key = crop.trim().lowercase()

        dao.getLiveBrief(key)?.let { return@withContext it.directives to "CACHE" }

        geminiBrief(crop.trim(), county, temp, humidity, wind)?.let { text ->
            dao.saveLiveBrief(CropAdvisory(cropName = key, seasonalMarker = "LIVE_BRIEF", directives = text))
            return@withContext text to "GEMINI"
        }

        openFarmBrief(crop.trim())?.let { text ->
            dao.saveLiveBrief(CropAdvisory(cropName = key, seasonalMarker = "LIVE_BRIEF", directives = text))
            return@withContext text to "OPENFARM"
        }

        wikiBrief(crop.trim())?.let { text ->
            dao.saveLiveBrief(CropAdvisory(cropName = key, seasonalMarker = "LIVE_BRIEF", directives = text))
            return@withContext text to "WIKI"
        }

        "No verified brief found for \"$crop\" yet. Check the spelling or reconnect and try again." to "OFFLINE"
    }

    private suspend fun geminiBrief(crop: String, county: String, temp: Double, humidity: Int, wind: Double): String? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return null
        return gemini.generateBrief(
            "You are a KALRO agronomist advising Kenyan smallholder farmers.",
            "Give a concise Kenyan field brief for $crop grown in $county " +
                "(current $temp°C, humidity $humidity%, wind $wind km/h). Cover: land prep, " +
                "planting spacing, top-dressing, key pests, and safe harvest moisture. " +
                "Keep it under 120 words, plain farmer-friendly English."
        )
    }

    /** Free OpenFarm growing guides: spacing, sowing, sun — no key needed. */
    private fun openFarmBrief(crop: String): String? {
        return try {
            val res = openFarmApi.search(crop).execute()
            if (!res.isSuccessful) return null
            val attrs = res.body()?.data?.firstOrNull()?.attributes ?: return null
            val specs = listOfNotNull(
                attrs.row_spacing?.let { "Row spacing: $it cm" },
                attrs.spread?.let { "Spread: $it cm" },
                attrs.sowing_method?.takeIf { it.isNotBlank() }?.let { "Sowing: $it" },
                attrs.sun_requirements?.takeIf { it.isNotBlank() }?.let { "Sun: $it" }
            )
            if (specs.isEmpty() && attrs.description.isNullOrBlank()) return null
            val cleanDesc = attrs.description
                ?.replace(Regex("<[^>]*>"), "")
                ?.takeIf { it.isNotBlank() }
                ?.take(600)
            buildString {
                append("🌱 ${attrs.name ?: crop} — OpenFarm growing guide")
                if (cleanDesc != null) {
                    append("\n\n")
                    append(cleanDesc)
                }
                if (specs.isNotEmpty()) {
                    append("\n\n• ")
                    append(specs.joinToString(" • "))
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun wikiBrief(crop: String): String? {        val attempts = listOf(crop, crop.replaceFirstChar { it.uppercase() }).distinct()
        for (title in attempts) {
            try {
                val res = wikiApi.summary(title).execute()
                if (res.isSuccessful) {
                    val body = res.body()
                    val extract = body?.extract?.takeIf { it.isNotBlank() } ?: continue
                    val header = "🌐 ${body.title ?: crop}" +
                        (body.description?.let { " ($it)" } ?: "")
                    return "$header\n\n$extract"
                }
            } catch (e: Exception) {
                // Try the next title variant, then fall through to OFFLINE.
            }
        }
        return null
    }
}
