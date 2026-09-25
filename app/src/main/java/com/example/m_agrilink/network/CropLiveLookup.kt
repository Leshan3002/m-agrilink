package com.example.m_agrilink.network

import android.content.Context
import com.example.m_agrilink.BuildConfig
import com.example.m_agrilink.data.local.AgriLinkDatabase
import com.example.m_agrilink.data.local.CropAdvisory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class WikiSummary(
    val title: String?,
    val description: String?,
    val extract: String?
)

interface WikiApi {
    @GET("api/rest_v1/page/summary/{title}")
    fun summary(@Path("title") title: String): retrofit2.Call<WikiSummary>
}

/**
 * Live brief for ANY crop: Room cache first (instant + offline),
 * then Gemini AI, then Wikipedia. Returns (briefText, source).
 * Sources: CACHE, GEMINI, WIKI, OFFLINE.
 */
class CropLiveLookup(context: Context) {

    private val dao = AgriLinkDatabase.getDatabase(context.applicationContext).farmerDao()

    private val geminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeminiClientRoute::class.java)

    private val wikiApi = Retrofit.Builder()
        .baseUrl("https://en.wikipedia.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WikiApi::class.java)

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

        wikiBrief(crop.trim())?.let { text ->
            dao.saveLiveBrief(CropAdvisory(cropName = key, seasonalMarker = "LIVE_BRIEF", directives = text))
            return@withContext text to "WIKI"
        }

        "No verified brief found for \"$crop\" yet. Check the spelling or reconnect and try again." to "OFFLINE"
    }

    private fun geminiBrief(crop: String, county: String, temp: Double, humidity: Int, wind: Double): String? {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) return null
        val prompt = "Give a concise Kenyan field brief for $crop grown in $county " +
            "(current $temp°C, humidity $humidity%, wind $wind km/h). Cover: land prep, " +
            "planting spacing, top-dressing, key pests, and safe harvest moisture. " +
            "Keep it under 120 words, plain farmer-friendly English."
        val request = GeminiRequest(
            systemInstruction = SystemInstruction(
                parts = listOf(Part("You are a KALRO agronomist advising Kenyan smallholder farmers."))
            ),
            contents = listOf(Content(parts = listOf(Part(prompt))))
        )
        return try {
            val res = geminiApi.generateContent(BuildConfig.GEMINI_API_KEY, request).execute()
            res.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun wikiBrief(crop: String): String? {
        val attempts = listOf(crop, crop.replaceFirstChar { it.uppercase() }).distinct()
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
