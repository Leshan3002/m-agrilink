package com.example.m_agrilink.network

import com.example.m_agrilink.ui.TelemetryContext
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 2. GEMINI FREE TIER RETROFIT CLIENT
 * Retrofit Interface targeting the free-tier API completions endpoint.
 */
interface GeminiClientRoute {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Call<GeminiResponse>
}

data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: SystemInstruction? = null,
    val generationConfig: GenerationConfig = GenerationConfig()
)

data class SystemInstruction(
    val parts: List<Part>
)

data class Content(
    val role: String = "user",
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Double = 0.3
)

data class GeminiResponse(
    val candidates: List<Candidate>?
)

data class Candidate(
    val content: ResponseContent?
)

data class ResponseContent(
    val parts: List<Part>?
)

object GeminiHelper {

    /**
     * MODULE 3: CONTEXT-AWARE AI ASSISTANT OVERLAY
     * Injects live Open-Meteo telemetry and Room cached market prices into Gemini instructions.
     */
    fun buildGeminiContextRequest(userInquiry: String, telemetry: TelemetryContext): GeminiRequest {
        val systemPrompt = """
            You are the M-AgriLink National AI Assistant.
            Current Real-Time Contextual Data Layer:
            - 🌍 Location: ${telemetry.location}
            - 🌡️ Temperature: ${telemetry.temp}
            - 🌧️ Rain Probability: ${telemetry.rainProb}
            - 💨 Wind Speed: ${telemetry.wind}
            - 💧 Humidity: ${telemetry.humidity}
            - 📈 Market Index (Maize): ${telemetry.maizePrice}
            - 🌱 Primary Crop Variety: ${telemetry.cropVariety}

            Your instructions:
            You MUST format your entire response strictly into these user-facing bullet blocks using emojis:
            📊 Live Telemetry Synthesis: Summarize the current weather and its impact on the region.
            🌱 Predictive Smart Agronomy: Provide specific crop management advice based on rain probability and variety.
            💰 Localized Financial Planning: Advise on trade strategy based on the maize price floor and corridor margins.
        """.trimIndent()

        return GeminiRequest(
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(parts = listOf(Part(text = userInquiry))))
        )
    }
}
