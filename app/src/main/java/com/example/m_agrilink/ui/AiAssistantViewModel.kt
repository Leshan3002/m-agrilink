package com.example.m_agrilink.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.m_agrilink.network.GeminiRepository

class AiAssistantViewModel : ViewModel() {

    // Free-tier Gemini API Key
    private val repository = GeminiRepository("AIzaSyFakeKeyPlaceholderForProduction")

    val messages = mutableStateListOf<ChatMessage>()
    
    fun sendMessage(inquiry: String, telemetry: TelemetryContext) {
        if (inquiry.isBlank()) return

        messages.add(ChatMessage(inquiry, isUser = true))

        val loadingPlaceholder = ChatMessage("Synthesizing data for ${telemetry.location}...", isUser = false)
        messages.add(loadingPlaceholder)

        repository.getClimateSmartAdvice(
            userInquiry = inquiry,
            telemetry = telemetry,
            callback = { response ->
                messages.remove(loadingPlaceholder)
                val aiResponse = response ?: "Unable to receive advice. Check bandwidth."
                messages.add(ChatMessage(aiResponse, isUser = false))
            }
        )
    }
}
