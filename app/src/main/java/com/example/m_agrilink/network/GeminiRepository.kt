package com.example.m_agrilink.network

import com.example.m_agrilink.ui.TelemetryContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiClientRoute::class.java)
    }

    /**
     * Blocking variant for viewModelScope(Dispatchers.IO) callers.
     * Returns (candidates[0].content.parts[0].text, authRejected).
     * authRejected is true only when Google refuses the key (HTTP 400/401/403),
     * so the UI can tell a bad key apart from a bad network.
     */
    fun getClimateSmartAdviceSync(userInquiry: String, telemetry: TelemetryContext): Pair<String?, Boolean> {
        return try {
            val request = GeminiHelper.buildGeminiContextRequest(userInquiry, telemetry)
            val response = syncApi.generateContent(apiKey = apiKey, request = request).execute()
            if (response.code() == 400 || response.code() == 401 || response.code() == 403) {
                return null to true
            }
            if (!response.isSuccessful) return null to false
            val text = response.body()?.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
                ?.takeIf { it.isNotBlank() }
            text to false
        } catch (e: Exception) {
            null to false
        }
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

        api.generateContent(apiKey = apiKey, request = request)
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
