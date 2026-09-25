package com.example.m_agrilink.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m_agrilink.BuildConfig
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.network.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiAssistantViewModel : ViewModel() {

    // Free-tier Gemini key injected via local.properties -> BuildConfig.
    private val repository = GeminiRepository(BuildConfig.GEMINI_API_KEY)

    val messages = mutableStateListOf<ChatMessage>()

    init {
        messages.add(
            ChatMessage(
                "Habari! 👋 I'm Shamba, your farm friend. Tell me how your crops, " +
                    "cattle or farm are behaving — in English or Kiswahili — and I'll " +
                    "suggest what to do next. 👇 Or tap a quick question below!",
                isUser = false
            )
        )
    }

    fun sendMessage(inquiry: String, telemetry: TelemetryContext) {
        if (inquiry.isBlank()) return

        messages.add(ChatMessage(inquiry, isUser = true))

        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            messages.add(
                ChatMessage(
                    "⚠️ My AI brain isn't connected yet — the app owner needs to add a " +
                        "free Gemini key (Google AI Studio) into local.properties as " +
                        "GEMINI_API_KEY. Then I can answer anything! 🌱",
                    isUser = false
                )
            )
            return
        }

        val loadingPlaceholder = ChatMessage("🤔 Thinking about ${telemetry.location}...", isUser = false)
        messages.add(loadingPlaceholder)

        // Network runs off the main thread; bubbles update back on Main.
        viewModelScope.launch {
            val (liveText, authRejected) = withContext(Dispatchers.IO) {
                repository.getClimateSmartAdviceSync(inquiry, telemetry)
            }
            messages.remove(loadingPlaceholder)
            when {
                !liveText.isNullOrBlank() -> messages.add(ChatMessage(liveText, isUser = false))
                authRejected -> messages.add(
                    ChatMessage(
                        "🔑 Google rejected my API key (invalid or expired). The app owner " +
                            "needs to paste a fresh free key from Google AI Studio into " +
                            "local.properties as GEMINI_API_KEY and rebuild — real keys start " +
                            "with \"AIza\" and are 39 characters. Your farm questions are safe " +
                            "with me meanwhile! 🌱",
                        isUser = false
                    )
                )
                else -> messages.add(ChatMessage(offlineBrief(telemetry), isUser = false))
            }
        }
    }

    /** Timeout/offline fallback: local MarketDataRepository brief for the active crop. */
    private fun offlineBrief(telemetry: TelemetryContext): String {
        val crop = telemetry.cropVariety.ifBlank { "Maize" }
        val county = telemetry.location.ifBlank { "Baringo" }
        val record = try {
            MarketDataRepository.getCrop(county, crop)
        } catch (e: Exception) {
            null
        }
        return if (record != null) {
            "📴 Offline mode — the network timed out, but here is your saved $crop guide for $county:\n" +
                "🚜 Immediate Action: ${record.advisory.plantingSpacing}\n" +
                "🌾 Safe Harvesting: ${record.advisory.moistureCeiling}"
        } else {
            "Hmm, my network tripped on a stone! 🪨 I couldn't reach the advisory service and " +
                "have no saved guide for \"$crop\" yet. Reconnect and ask again — your question is safe with me. 🌧️"
        }
    }
}
