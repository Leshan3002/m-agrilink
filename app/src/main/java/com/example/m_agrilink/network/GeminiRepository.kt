package com.example.m_agrilink.network

import com.example.m_agrilink.ui.TelemetryContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Production-ready Repository layout for managing Google Gemini communication.
 */
class GeminiRepository(private val apiKey: String) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeminiClientRoute::class.java)

    // Time-boxed client so timeouts actually fire instead of hanging forever.
    private val syncApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiClientRoute::class.java)
    }

    // Live model first, recommended fallback second. Verified 2026-09-28:
    // gemini-3.1-flash-lite answers; gemini-3.5-flash-lite flaps with 503s.
    private val modelChain = listOf(
        "v1beta/models/gemini-3.1-flash-lite:generateContent",
        "v1beta/models/gemini-3.5-flash-lite:generateContent"
    )

    // HTTP codes worth one more attempt (free-tier capacity flaps).
    private val transientCodes = setOf(408, 429, 500, 502, 503, 504)
    private val retryBackoffMs = longArrayOf(2000L, 4000L)

    /**
     * Blocking variant for viewModelScope(Dispatchers.IO) callers.
     * Returns (candidates[0].content.parts[0].text, authRejected).
     * Walks the model chain with one backoff retry per model on transient
     * 429/5xx; authRejected is true only when Google refuses the key (400/401/403).
     */
    suspend fun getClimateSmartAdviceSync(userInquiry: String, telemetry: TelemetryContext): Pair<String?, Boolean> {
        val request = GeminiHelper.buildGeminiContextRequest(userInquiry, telemetry)
        return generateWithChain(request)
    }

    /** Free-form generation sharing the same chain (used by crop briefs). */
    suspend fun generateBrief(systemPrompt: String, userPrompt: String): String? {
        val request = GeminiRequest(
            systemInstruction = SystemInstruction(parts = listOf(Part(systemPrompt))),
            contents = listOf(Content(parts = listOf(Part(userPrompt))))
        )
        return generateWithChain(request).first
    }

    private suspend fun generateWithChain(request: GeminiRequest): Pair<String?, Boolean> {
        for (model in modelChain) {
            var attempt = 0
            while (attempt < 2) {
                try {
                    val response = syncApi.generateContent(model, apiKey, request).execute()
                    if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                        return null to true
                    }
                    if (response.isSuccessful) {
                        val text = response.body()?.candidates?.firstOrNull()
                            ?.content?.parts?.firstOrNull()?.text
                            ?.takeIf { it.isNotBlank() }
                        if (!text.isNullOrBlank()) return text to false
                        break
                    }
                    if (response.code() == 404) break // retired for this key: next model, no retry
                    if (response.code() !in transientCodes || attempt >= 1) break
                    delay(retryBackoffMs[attempt])
                    attempt++
                } catch (e: UnknownHostException) {
                    return null to false // dead network: no point trying more models
                } catch (e: SocketTimeoutException) {
                    if (attempt >= 1) break
                    delay(retryBackoffMs[attempt])
                    attempt++
                } catch (e: Exception) {
                    return null to false
                }
            }
        }
        return null to false
    }

    /**
     * Executes non-blocking asynchronous call with integrated telemetry context.
     */
    fun getClimateSmartAdvice(
        userInquiry: String, 
        telemetry: TelemetryContext, 
        callback: (String?) -> Unit
    ) {
        val request = GeminiHelper.buildGeminiContextRequest(userInquiry, telemetry)

        api.generateContent(modelChain.first(), apiKey = apiKey, request = request)
            .enqueue(object : Callback<GeminiResponse> {
                override fun onResponse(call: Call<GeminiResponse>, response: Response<GeminiResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        val contentText = responseBody?.candidates?.firstOrNull()
                            ?.content?.parts?.firstOrNull()?.text
                        callback(contentText)
                    } else {
                        callback("Server Error (${response.code()}).")
                    }
                }

                override fun onFailure(call: Call<GeminiResponse>, t: Throwable) {
                    callback("Network connection is weak. Please try again.")
                }
            })
    }
}
