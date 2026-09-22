package com.example.m_agrilink.network

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repository to handle AI communication with OpenAI using Retrofit.
 * Implements non-blocking asynchronous callback logic.
 */
class AgriAiRepository(private val apiKey: String) {

    // TASK 2: Base target configured as requested
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://openai.com/") // Base target as specified
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(OpenAIClientRoute::class.java)

    /**
     * Non-blocking asynchronous call to get AI advice based on telemetry context.
     * Uses dynamic telemetry injection via OpenAIHelper.
     */
    fun fetchSmartAssistantAdvice(
        userQuery: String,
        callback: (String?) -> Unit
    ) {
        // TASK 3: Payload construction with injected telemetry
        val request = OpenAIHelper.createSmartAssistantRequest(userQuery)
        val authHeader = "Bearer $apiKey"

        // Production-ready asynchronous execution
        api.getChatCompletion(authHeader = authHeader, request = request)
            .enqueue(object : Callback<ChatCompletionResponse> {
                override fun onResponse(
                    call: Call<ChatCompletionResponse>,
                    response: Response<ChatCompletionResponse>
                ) {
                    if (response.isSuccessful) {
                        val result = response.body()?.choices?.firstOrNull()?.message?.content
                        callback(result)
                    } else {
                        // Clean error handling for production
                        val errorMsg = "Error ${response.code()}: ${response.message()}"
                        callback(errorMsg)
                    }
                }

                override fun onFailure(call: Call<ChatCompletionResponse>, t: Throwable) {
                    // Fail-safe callback for network issues
                    callback("Network Failure: ${t.localizedMessage}")
                }
            })
    }
}
