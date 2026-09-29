package com.example.m_agrilink.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m_agrilink.BuildConfig
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.network.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException

enum class EngineStatus { UNKNOWN, ONLINE, OFFLINE }

class AiAssistantViewModel : ViewModel() {

    // Free-tier Gemini key injected via local.properties -> BuildConfig.
    private val repository = GeminiRepository(BuildConfig.GEMINI_API_KEY)

    val messages = mutableStateListOf<ChatMessage>()
    var engineStatus by mutableStateOf(EngineStatus.UNKNOWN)
    private var consecutiveFailures = 0
    private var probeStarted = false

    init {
        messages.add(
            ChatMessage(
                "Habari! 👋 I'm Shamba, your farm friend. Tell me how your crops, " +
                    "cattle or farm are behaving — in English or Kiswahili — and I'll " +
                    "suggest what to do next. 👇 Or tap a quick question below!",
                isUser = false
            )
        )
        probeEngine()
    }

    /**
     * One cheap probe per session so the badge opens truthful instead of grey.
     * Single failures never flap the badge (see registerFailure damping).
     */
    fun probeEngine() {
        if (probeStarted || BuildConfig.GEMINI_API_KEY.isBlank()) return
        probeStarted = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    !repository.generateBrief(
                        "You are a connectivity probe.",
                        "Reply with exactly: online."
                    ).isNullOrBlank()
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                consecutiveFailures = 0
                engineStatus = EngineStatus.ONLINE
            } else {
                registerFailureSilent()
            }
        }
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
            try {
                val (liveText, authRejected) = withContext(Dispatchers.IO) {
                    repository.getClimateSmartAdviceSync(inquiry, telemetry)
                }
                messages.remove(loadingPlaceholder)
                when {
                    !liveText.isNullOrBlank() -> {
                        consecutiveFailures = 0
                        engineStatus = EngineStatus.ONLINE
                        messages.add(ChatMessage(liveText, isUser = false))
                    }
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
                    else -> {
                        registerFailureSilent()
                        messages.add(ChatMessage(offlineExpertBrief(telemetry), isUser = false))
                    }
                }
            } catch (e: UnknownHostException) {
                messages.remove(loadingPlaceholder)
                registerFailureSilent()
                messages.add(ChatMessage(offlineExpertBrief(telemetry), isUser = false))
            } catch (e: Exception) {
                messages.remove(loadingPlaceholder)
                registerFailureSilent()
                messages.add(ChatMessage(offlineExpertBrief(telemetry), isUser = false))
            }
        }
    }

    /**
     * Flap damping: a lone transient failure keeps the last confirmed badge;
     * only 2 consecutive failures downgrade to OFFLINE. Success resets instantly.
     */
    private fun registerFailureSilent() {
        consecutiveFailures++
        if (consecutiveFailures >= 2) engineStatus = EngineStatus.OFFLINE
    }

    /**
     * Manual re-probe from the Offline Shield header button.
     * Resets damping so one good answer restores Live Sync immediately.
     */
    fun retryConnection() {
        consecutiveFailures = 0
        engineStatus = EngineStatus.UNKNOWN
        probeStarted = false
        probeEngine()
    }

    /**
     * Zero-failure offline engine: structured expert brief built from the local
     * MarketDataRepository for the farmer's active county + crop. Known crops
     * get pricing + agronomy; anything else gets a contextual KALRO template.
     */
    private fun offlineExpertBrief(telemetry: TelemetryContext): String {
        val crop = telemetry.cropVariety.ifBlank { "Maize" }.trim()
        val county = telemetry.location.ifBlank { "Baringo" }.trim()
        val record = try {
            MarketDataRepository.getCrop(county, crop)
        } catch (e: Exception) {
            null
        }
        if (record != null) {
            return "🤖 Shamba Assistant (Offline Mode): I've detected you are managing " +
                "${record.cropName} in $county with limited connectivity. " +
                "🚜 Immediate Action: ${record.advisory.plantingSpacing} " +
                "${record.advisory.landPrep} " +
                "🌾 Safe Harvesting: ${record.advisory.moistureCeiling} " +
                "💰 Local market: KES ${record.localPriceKes} → hub KES ${record.hubPriceKes} " +
                "(net +KES ${record.netMarginKes} per bag)."
        }
        return "🤖 Shamba Assistant (Offline Mode): I've detected you are managing a " +
            "$crop orchard in $county with limited connectivity. Based on localized KALRO " +
            "data, your main priority right now is scouting twice weekly for pest vectors, " +
            "hanging monitoring traps at canopy level, and clearing fallen fruits and debris " +
            "to break pest lifecycles. Hold harvested produce under the 13.5% moisture ceiling " +
            "before storage. Reconnect for a full live brief. 🌱"
    }
}
