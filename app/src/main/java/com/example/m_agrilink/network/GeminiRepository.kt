package com.example.m_agrilink.network

import com.example.m_agrilink.ui.TelemetryContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Production-ready Repository layout for managing Google Gemini communication.
 */
class GeminiRepository(private val apiKey: String) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GeminiClientRoute::class.java)

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
