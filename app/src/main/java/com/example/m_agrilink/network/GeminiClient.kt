package com.example.m_agrilink.network

import com.example.m_agrilink.ui.TelemetryContext
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 2. GEMINI FREE TIER RETROFIT CLIENT
 * Model path is dynamic so the repository can fail over across models.
 */
interface GeminiClientRoute {
    @POST
    fun generateContent(
        @Url modelPath: String,
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
    val text: String? = null,
    val inlineData: Blob? = null
)

data class Blob(
    val mimeType: String,
    val data: String
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
            - Be warm and simple: short sentences, no jargon, max ~220 words.
            - Give the COMPLETE answer, not a partial one. Structure: one friendly line, then cover what it is, what to do right now (2-4 emoji bullets: 🌱 do this now • 🐄 animal care if relevant • ⚠️ danger signs • 💰 cheapest safe option), how to prevent it next time, and when to call a vet/agrovet immediately for emergencies.
            - Never leave the farmer hanging: if the question is vague, answer the most likely case AND ask one short follow-up for the missing detail.
            - Prefer KALRO/FAO-approved, low-cost remedies first. For emergencies (sick animal, severe outbreak), say clearly: call a vet or agrovet immediately.
            - Never mention bees unless the farmer asks about bees.
            - Ground every pest/disease remedy with trailing source footnotes in this exact shape: "💡 Cultural Remedy [Source: CABI Plantwise Bank]: ..." / "🌱 Ecological Strategy [Source: icipe Kenya]: ..." / "🧪 Safe Chemical Action [Source: UN FAO & KALRO]: ...".
            - End every reply with one satisfaction check line: "Does this fully answer your question? Tell me what is still unclear and I will explain further."
        """.trimIndent()

        return GeminiRequest(
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt))),
            contents = listOf(Content(parts = listOf(Part(text = userInquiry))))
        )
    }
}
