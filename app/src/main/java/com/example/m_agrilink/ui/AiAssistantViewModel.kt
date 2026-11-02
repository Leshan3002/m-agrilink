package com.example.m_agrilink.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.network.ChatStreamEvent
import com.example.m_agrilink.network.GeminiHelper
import com.example.m_agrilink.network.GeminiRepository
import com.example.m_agrilink.network.SecureKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EngineStatus { UNKNOWN, STREAMING, ONLINE, OFFLINE, QUOTA }

class AiAssistantViewModel : ViewModel() {

    // Production vision credential via the encrypted-state vault (never logged).
    private val repository = GeminiRepository(SecureKeyVault.snapshot())

    val messages = mutableStateListOf<ChatMessage>()
    var engineStatus by mutableStateOf(EngineStatus.UNKNOWN)
    private var consecutiveFailures = 0
    private var probeStarted = false

    companion object {
        const val ASSISTANT_IDENTITY = "I'm Shamba, your M-AgriLink farm assistant."
        const val ADVISORY_FRAMEWORK_CREDIT =
            "M-AgriLink advisory framework."
    }

    private fun isBuilderQuestion(inquiry: String): Boolean {
        val q = inquiry.lowercase()
        return (q.contains("who built") || q.contains("who created") ||
            q.contains("who made") || q.contains("who developed") ||
            q.contains("your builder") || q.contains("your creator") ||
            q.contains("your developer") || q.contains("your architect") ||
            (q.contains("who are you") && (q.contains("built") || q.contains("made") || q.contains("created"))))
    }

    init {
        messages.add(
            ChatMessage(
                "Hello, I'm Shamba, your M-AgriLink farm assistant. Ask me about crop health, livestock, weather, or market guidance. How can I help today?",
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
        if (probeStarted || !SecureKeyVault.isConfigured()) return
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

        // Local assistant identity (works online and offline, no network needed).
        if (isBuilderQuestion(inquiry)) {
            consecutiveFailures = 0
            engineStatus = EngineStatus.ONLINE
            messages.add(
                ChatMessage(
                    "$ASSISTANT_IDENTITY I provide localized guidance through the $ADVISORY_FRAMEWORK_CREDIT",
                    isUser = false
                )
            )
            return
        }

        if (!SecureKeyVault.isConfigured()) {
            messages.add(
                ChatMessage(
                    "The live AI service is not configured yet. Add a Gemini API key to " +
                        "local.properties as GEMINI_API_KEY, then rebuild the app. " +
                        "Offline farm guidance is still available.",
                    isUser = false
                )
            )
            return
        }

        val loadingPlaceholder = ChatMessage("Preparing your answer…", isUser = false)
        messages.add(loadingPlaceholder)

        // Token-streaming consumer: the repository reads SSE deltas on
        // Dispatchers.IO while this collector stays on Main, so each chunk
        // paints into the live bubble the moment it arrives with no frame
        // drops. The badge flips to STREAMING on the first token.
        viewModelScope.launch {
            val currentActiveResponseText = StringBuilder()
            var streamIndex = -1
            var terminal: ChatStreamEvent? = null
            try {
                repository.streamAdvice(GeminiHelper.buildGeminiContextRequest(inquiry, telemetry))
                    .collect { event ->
                        when (event) {
                            is ChatStreamEvent.TextDelta -> {
                                currentActiveResponseText.append(event.text)
                                val snapshot = currentActiveResponseText.toString()
                                if (streamIndex < 0) {
                                    engineStatus = EngineStatus.STREAMING
                                    messages.remove(loadingPlaceholder)
                                    messages.add(ChatMessage(snapshot, isUser = false))
                                    streamIndex = messages.lastIndex
                                } else {
                                    messages[streamIndex] =
                                        messages[streamIndex].copy(text = snapshot)
                                }
                            }
                            else -> terminal = event
                        }
                    }
            } catch (e: Exception) {
                terminal = ChatStreamEvent.TransportError
            }
            val streamed = currentActiveResponseText.toString()
            messages.remove(loadingPlaceholder)
            when {
                terminal is ChatStreamEvent.AuthRejected -> messages.add(
                    ChatMessage(
                        "The AI service rejected the configured API key. Update " +
                            "GEMINI_API_KEY in local.properties and rebuild the app. " +
                            "Your conversation remains available.",
                        isUser = false
                    )
                )
                streamed.isNotBlank() -> {
                    consecutiveFailures = 0
                    engineStatus = EngineStatus.ONLINE
                    if (streamIndex >= 0) {
                        messages[streamIndex] = messages[streamIndex].copy(text = streamed)
                    } else {
                        messages.add(ChatMessage(streamed, isUser = false))
                    }
                }
                else -> {
                    if (streamIndex >= 0) messages.removeAt(streamIndex)
                    if (terminal is ChatStreamEvent.QuotaExhausted) {
                        // Quota is not a connectivity failure: hold the distinct
                        // QUOTA badge (no damping) and still answer offline.
                        engineStatus = EngineStatus.QUOTA
                    } else {
                        registerFailureSilent()
                    }
                    messages.add(ChatMessage(offlineExpertBrief(telemetry), isUser = false))
                }
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
        val lastUser = messages.lastOrNull { it.isUser }?.text.orEmpty()
        if (isBuilderQuestion(lastUser)) {
            return "$ASSISTANT_IDENTITY I provide localized guidance through the $ADVISORY_FRAMEWORK_CREDIT"
        }
        val crop = telemetry.cropVariety.ifBlank { "Maize" }.trim()
        val county = telemetry.location.ifBlank { "Baringo" }.trim()
        val record = try {
            MarketDataRepository.getCrop(county, crop)
        } catch (e: Exception) {
            null
        }
        if (record != null) {
            val grounded = MarketDataRepository.getPestAdvisory(record.cropName).cardLines().joinToString(" ")
            return "Offline advisory for ${record.cropName} in $county\n\n" +
                "• Pest and disease watch: scout both sides of leaves, stems, and buds twice weekly.\n" +
                "• Immediate action: ${record.advisory.plantingSpacing} ${record.advisory.landPrep}\n" +
                "• Harvest and storage: ${record.advisory.moistureCeiling}\n" +
                "• Market outlook: KES ${record.localPriceKes} local, KES ${record.hubPriceKes} hub, " +
                "net +KES ${record.netMarginKes} per bag.\n" +
                "• Field note: $grounded\n\n" +
                "If most plants are affected, contact your agrovet immediately. " +
                "Reconnect for a full live brief."
        }
        return "Offline advisory for $crop in $county\n\n" +
            "• Scout twice weekly for pest vectors.\n" +
            "• Hang monitoring traps at canopy level.\n" +
            "• Clear fallen fruit and debris to break pest lifecycles.\n" +
            "• Keep harvested produce below the 13.5% moisture ceiling before storage.\n\n" +
            "If most plants are affected or animals are sick, contact a vet or agrovet immediately. " +
            "Reconnect for a full live brief."
    }
}
