package com.example.m_agrilink.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
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
import java.io.ByteArrayOutputStream
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

    private val gemini = GeminiRepository(SecureKeyVault.snapshot())

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
        if (!SecureKeyVault.isConfigured()) return null
        return gemini.generateBrief(
            "You are a KALRO agronomist advising Kenyan smallholder farmers.",
            "Give a concise Kenyan field brief for $crop grown in $county " +
                "(current $temp°C, humidity $humidity%, wind $wind km/h). Cover: land prep, " +
                "planting spacing, top-dressing, key pests, and safe harvest moisture. " +
                "Keep it under 120 words, plain farmer-friendly English."
        )
    }

    companion object {
        /**
         * Honest-vision engineering prompt. Non-agricultural frames must be
         * declined with the exact no-tissue message — never force-fit the
         * user-specified crop onto a desktop, room, person, or blank object.
         */
        const val VISION_SYSTEM_PROMPT =
            "You are the professional diagnostic engine of M-AgriLink. Analyze the " +
                "attached image byte array alongside the user-specified crop type. If the " +
                "image displays agricultural plant tissue, leaves, crops, or fruits, output " +
                "a structured, itemized analysis detailing the specific disease anomalies " +
                "and immediate ecological or certified control guidelines. If the image does " +
                "not contain an agricultural plant subject (e.g., a desktop computer screen, " +
                "a blank background, a room, or an unrelated object), ignore any " +
                "pre-selected crop type text and output exactly: 'No agricultural plant " +
                "tissue or crop anomalies detected in the provided image. Please capture or " +
                "upload a clear, well-lit close-up photo of your crop foliage for analysis.'"
    }

    /** Downscales to a bounded JPEG and base64-encodes it for the vision call. */
    private fun bitmapToBase64(frame: Bitmap): String {
        val maxSide = 1024
        val scale = (maxOf(frame.width, frame.height) / maxSide.toFloat()).coerceAtLeast(1f)
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                frame,
                (frame.width / scale).toInt(),
                (frame.height / scale).toInt(),
                true
            )
        } else {
            frame
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (scaled !== frame) {
            try {
                scaled.recycle()
            } catch (e: Exception) {
            }
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Live Gemini vision cognition over the raw captured/uploaded frame.
     * Key is read from the vault at call time (never hardcoded, never logged);
     * returns (verdict, source) with source GEMINI_VISION on success, OFFLINE
     * when the key is missing or the network is unreachable.
     */
    suspend fun visionDiagnose(
        frame: Bitmap,
        crop: String,
        county: String
    ): Pair<String?, String> = withContext(Dispatchers.IO) {
        if (!SecureKeyVault.isConfigured()) return@withContext null to "OFFLINE"
        val payload = try {
            bitmapToBase64(frame)
        } catch (e: Exception) {
            return@withContext null to "OFFLINE"
        }
        val text = gemini.analyzeImage(
            imageBase64 = payload,
            mimeType = "image/jpeg",
            systemPrompt = VISION_SYSTEM_PROMPT,
            userPrompt = "User-specified crop type: $crop. County: $county. " +
                "Give a structured, itemized analysis of what is visible."
        )
        if (text.isNullOrBlank()) null to "OFFLINE" else text to "GEMINI_VISION"
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
