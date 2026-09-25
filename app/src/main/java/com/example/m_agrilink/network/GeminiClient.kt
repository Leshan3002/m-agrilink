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
    @POST("v1beta/models/gemini-3.5-flash-lite:generateContent")
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
            You are Shamba, an expert agronomist advisor. Synthesize the user's inquiry considering their selected corridor hub (${telemetry.location}) and their active planted crop (${telemetry.cropVariety}). Provide a highly concise response with actionable steps.

            Farmer context:
            - 📍 Corridor hub: ${telemetry.location}
            - 🌡️ Temperature: ${telemetry.temp}
            - 💨 Wind: ${telemetry.wind}
            - 💧 Humidity: ${telemetry.humidity}
            - 🌾 Active planted crop: ${telemetry.cropVariety}

            Your strict instructions:
            - Detect whether the question is about CROPS, CATTLE/LIVESTOCK/POULTRY, or general farm management, and answer only that.
            - Reply in the farmer's language (English, Kiswahili, or a Sheng mix is fine).
            - Be warm and simple: short sentences, no jargon, max ~150 words.
            - Structure: one friendly line, then 2-4 emoji bullets (🌱 do this now • 🐄 animal care if relevant • ⚠️ danger signs • 💰 cheapest safe option).
            - Prefer KALRO/FAO-approved, low-cost remedies first. For emergencies (sick animal, severe outbreak), say clearly: call a vet or agrovet immediately.
            - Never mention bees unless the farmer asks about bees.
        """.trimIndent()

        return GeminiRequest(
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(parts = listOf(Part(text = userInquiry))))
        )
    }
}
