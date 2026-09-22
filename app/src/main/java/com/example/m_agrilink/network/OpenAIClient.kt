package com.example.m_agrilink.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * TASK 2: RECONSTRUCT THE OPENAI CHAT completions ROUTER
 */
interface OpenAIClientRoute {
    /**
     * Base target: POST https://openai.com
     * Note: In production Retrofit setup, the base URL should be set to "https://api.openai.com/"
     */
    @POST("v1/chat/completions")
    fun getChatCompletion(
        @Header("Authorization") authHeader: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatCompletionRequest
    ): Call<ChatCompletionResponse>
}

// Data models for OpenAI API Communication
data class ChatCompletionRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    val temperature: Double = 0.3
)

data class Message(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

/**
 * TASK 3: SYSTEM CONTEXT INJECTION & TELEMETRY SYNTHESIS
 */
object OpenAIHelper {

    /**
     * Constructs the request payload by synthesizing active dashboard telemetry 
     * into rigid system role instructions.
     */
    fun createSmartAssistantRequest(userQuery: String): ChatCompletionRequest {
        
        // Dynamic telemetry injection based on specified environment
        val location = "Marigat, Baringo County, Kenya"
        val weather = "26°C, 15% Rain Probability (KAOP Data)"
        val cropInfo = "Maize (Variety: Katumani) (KALRO Data)"
        val marketPrices = "Maize wholesale price KES 4,200 per 90kg bag, Beans KES 11,500 (KAMIS Data)"

        val systemInstructions = """
            You are a smart agricultural AI assistant. 
            Active Dashboard Telemetry:
            - Location: $location
            - Temperature/Weather: $weather
            - Selected Crop: $cropInfo
            - Commodity Price index: $marketPrices

            Instructions: You MUST format all responses into three distinct bulleted sections:
            1. 📊 Data Synthesis: Breakdown what the weather numbers mean for the farm right now.
            2. 🌱 Agronomic Advice: Actionable field guidance on planting/managing the specified seed variety under current weather.
            3. 💰 Financial Planning: Strategic advice on whether to sell, hold, or transport crops based on current KAMIS market prices.
        """.trimIndent()

        return ChatCompletionRequest(
            messages = listOf(
                Message(role = "system", content = systemInstructions),
                Message(role = "user", content = userQuery)
            )
        )
    }
}
