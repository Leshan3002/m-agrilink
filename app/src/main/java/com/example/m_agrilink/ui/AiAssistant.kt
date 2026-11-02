package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.m_agrilink.data.AppLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MODULE 2: AI CONTEXTUAL ASSISTANT
 */

// 1. Data Models
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = java.util.UUID.randomUUID().toString()
)

data class TelemetryContext(
    val location: String,
    val temp: String,
    val rainProb: String,
    val wind: String,
    val humidity: String,
    val cropVariety: String = "N/A",
    val maizePrice: String = "N/A",
    val beeProduct: String = "N/A",
    val beeProductPrice: String = "N/A"
)

/**
 * Structural Strategy:
 * Jetpack Compose is used for the AI Assistant overlay.
 * It allows for a lightweight, state-driven UI that remains independent of the
 * main dashboard's scroll state while sharing the same ViewModel context.
 */

/**
 * Kenyan-accented TTS binding for chat readout: Kiswahili mode speaks sw-KE,
 * otherwise Kenyan English (en-KE) at a clear .95f pace so they can follow
 * dense advisories. Thread-isolated callers invoke this on the main thread;
 * speech itself streams off-main. Telemetry wording uses they/their/them.
 */
internal fun applyChatTtsLocale(tts: TextToSpeech) {
    try {
        if (AppLocale.isSwahili) {
            tts.setLanguage(Locale("sw", "KE"))
        } else {
            val result = tts.setLanguage(Locale("en", "KE"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
        tts.setSpeechRate(0.95f)
        tts.setPitch(1.0f)
    } catch (e: Exception) {
    }
}

/** Strip emoji so the voice reads words for them, not symbol names. */
internal fun chatSpeakableText(s: String): String =
    s.replace(ChatLinkPattern, " ").replace(Regex("[\\p{So}\\p{Sk}•●▲▼★☆→➔*]+"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Kenyan media controller hub: minimalist text-only Play / Pause / Stop row
 * under assistant bubbles. Pause calls tts.stop() but keeps a resume offset
 * so they do not lose their place; Stop purges the thread and resets flags.
 */
@Composable
private fun ChatAudioHub(
    textToSpeech: TextToSpeech?,
    textToRead: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var resumeOffset by remember(textToRead) { mutableStateOf(0) }
    val readable = remember(textToRead) { chatSpeakableText(textToRead) }
    val playable = remember(readable, resumeOffset) {
        if (resumeOffset in 1 until readable.length) readable.drop(resumeOffset) else readable
    }
    DisposableEffect(textToSpeech) {
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) {
                isPlaying = false
                isPaused = false
                resumeOffset = 0
            }
            override fun onError(utteranceId: String?) {
                isPlaying = false
                isPaused = false
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                resumeOffset = start
            }
        }
        try {
            textToSpeech?.setOnUtteranceProgressListener(listener)
        } catch (e: Exception) {
        }
        onDispose { }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                val tts = textToSpeech ?: return@TextButton
                if (playable.isBlank()) return@TextButton
                try {
                    applyChatTtsLocale(tts)
                    tts.stop()
                    isPaused = false
                    isPlaying = true
                    playable.chunked(400).forEach { chunk ->
                        tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "chat_media_hub")
                    }
                } catch (e: Exception) {
                    isPlaying = false
                }
            },
            enabled = textToSpeech != null && readable.isNotBlank() && !isPlaying
        ) {
            Text("Listen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        }
        TextButton(
            onClick = {
                try {
                    textToSpeech?.stop()
                } catch (e: Exception) {
                }
                isPlaying = false
                isPaused = true
            },
            enabled = isPlaying
        ) {
            Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
        }
        TextButton(
            onClick = {
                try {
                    textToSpeech?.stop()
                } catch (e: Exception) {
                }
                isPlaying = false
                isPaused = false
                resumeOffset = 0
            },
            enabled = isPlaying || isPaused
        ) {
            Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
        }
    }
}
@Composable
fun AiChatOverlay(
    telemetry: TelemetryContext,
    viewModel: AiAssistantViewModel = viewModel(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    showHeader: Boolean = true
) {
    var inputText by remember { mutableStateOf("") }
    val messages = viewModel.messages
    var voiceError by remember { mutableStateOf<String?>(null) }

    fun submitInquiry(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        inputText = ""
        voiceError = null
        viewModel.sendMessage(clean, telemetry)
    }

    Surface(
        modifier = modifier
            .then(
                if (fullScreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().fillMaxHeight(0.8f)
                }
            )
            .imePadding(),
        shape = if (fullScreen) {
            RoundedCornerShape(0.dp)
        } else {
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        },
        tonalElevation = 4.dp,
        color = Color(0xFFF8FAF7)
    ) {
        // Chat voice-over engine (released with the overlay).
        val chatContext = LocalContext.current
        var chatTts: TextToSpeech? by remember { mutableStateOf(null) }

        DisposableEffect(chatContext) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(chatContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { applyChatTtsLocale(it) }
                }
            }
            chatTts = tts
            onDispose {
                tts?.stop()
                tts?.shutdown()
                chatTts = null
            }
        }

        // Stop voice-over when the app goes to background so audio never
        // leaks behind the chat and resume is instant.
        val chatLifecycleOwner = remember(chatContext) { chatContext as? LifecycleOwner }
        DisposableEffect(chatLifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    try {
                        chatTts?.stop()
                    } catch (e: Exception) {
                    }
                }
            }
            try {
                chatLifecycleOwner?.lifecycle?.addObserver(observer)
            } catch (e: Exception) {
            }
            onDispose {
                try {
                    chatLifecycleOwner?.lifecycle?.removeObserver(observer)
                } catch (e: Exception) {
                }
            }
        }

        // Voice input: speech-to-text so farmers can speak instead of typing.
        val speechLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val heard = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull { it.isNotBlank() }
                if (heard != null) submitInquiry(heard)
                else voiceError = "Didn't catch that — try speaking again."
            } else {
                voiceError = "Voice input cancelled."
            }
        }
        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your farm question…")
                    }
                    speechLauncher.launch(intent)
                } catch (e: Exception) {
                    voiceError = "Voice input not available on this device."
                }
            } else {
                voiceError = "Microphone permission needed for voice input."
            }
        }
        fun startVoiceInput() {
            voiceError = null
            val granted = ContextCompat.checkSelfPermission(
                chatContext, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your farm question…")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                voiceError = "Voice input not available on this device."
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Header: identity band — avatar mark, title + role caption, Close.
            if (showHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF064E3B), CircleShape)
                    ) {
                        Text(
                            text = "S",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "M-AgriLink Assistant",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B4332),
                            fontSize = 18.sp
                        )
                        Text(
                            "Shamba • Farm advisory",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color(0xFF1B4332), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Engine status micro-badge: Live Sync vs Offline Shield Active.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (dot, label) = when (viewModel.engineStatus) {
                    EngineStatus.ONLINE -> Color(0xFF2E7D32) to "Online"
                    EngineStatus.STREAMING -> Color(0xFF2E7D32) to "Shamba is responding…"
                    EngineStatus.QUOTA -> Color(0xFFE6B325) to "Quota paused"
                    EngineStatus.OFFLINE -> Color(0xFFE6B325) to "Offline mode"
                    else -> Color.Gray to "Connecting…"
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dot, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label,
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (viewModel.engineStatus == EngineStatus.OFFLINE) {
                    TextButton(onClick = { viewModel.retryConnection() }) {
                        Text(
                            "Retry",
                            fontSize = 12.sp,
                            color = Color(0xFFA75D5D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Chat Messages: state-tracked column with auto-scroll injection —
            // every new question or streamed token chunk pins the viewport to
            // the latest bubble so the conversation stays in view hands-free.
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(
                        message = message,
                        textToSpeech = chatTts,
                        onSpeak = { text ->
                            if (AppAudioGate.muted) {
                                AppAudioGate.muted = false
                                val tts = chatTts
                                if (tts != null) {
                                    try {
                                        applyChatTtsLocale(tts)
                                    } catch (e: Exception) {
                                    }
                                    val spoken = chatSpeakableText(text)
                                    spoken.chunked(400).forEach { chunk ->
                                        tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                    }
                                }
                            } else {
                                try {
                                    chatTts?.stop()
                                } catch (e: Exception) {
                                }
                                AppAudioGate.muted = true
                            }
                        }
                    )
                }
            }

            // Suggested questions help farmers start without typing.
            if (messages.none { it.isUser }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Mango management",
                        "Milk production",
                        "Bean protection",
                        "Livestock health"
                    ).forEach { query ->
                        OutlinedButton(
                            onClick = { submitInquiry(query) },
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFFB7C9B7)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                query,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1B4332)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Input Area: type or tap mic to speak instead of typing.
            if (voiceError != null) {
                Text(
                    voiceError ?: "",
                    fontSize = 12.sp,
                    color = Color(0xFFB71C1C),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            // Quota notice: high-contrast card pinned above the input bar.
            // The field stays fully interactive; nothing is gated or dropped.
            if (viewModel.engineStatus == EngineStatus.QUOTA) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    border = BorderStroke(1.dp, Color(0xFFE6B325))
                ) {
                    Text(
                        text = "Live AI answers are temporarily paused because the free-tier quota was reached. You can keep chatting; offline guidance remains available.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1B4332),
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about crops, livestock, weather…") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitInquiry(inputText) }),
                    trailingIcon = {
                        IconButton(onClick = { startVoiceInput() }) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Voice input",
                                tint = Color(0xFF2E7D32)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2E7D32),
                        unfocusedBorderColor = Color(0xFFD1D5DB),
                        focusedTextColor = Color(0xFF212121),
                        unfocusedTextColor = Color(0xFF212121),
                        focusedPlaceholderColor = Color(0xFF6B7280),
                        unfocusedPlaceholderColor = Color(0xFF6B7280),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { submitInquiry(inputText) },
                    enabled = inputText.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B4332),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

// --- Clickable learn-more links (YouTube + Wikipedia profiles) ---
private val ChatLinkPattern = Regex("(https?://[^\\s]+)")

private fun extractChatLinks(text: String): List<String> =
    ChatLinkPattern.findAll(text)
        .map { it.value.trimEnd('.', ',', ')', ']', '"', '\'', ';', ':') }
        .filter { it.contains('.') }
        .distinct()
        .toList()

private fun chatLinkSite(url: String): String = when {
    url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) -> "youtube"
    url.contains("wikipedia.org", ignoreCase = true) -> "wikipedia"
    else -> "web"
}

private fun chatLinkTopic(url: String): String {
    return try {
        val decoded = java.net.URLDecoder.decode(url, "UTF-8")
        val queryKeys = listOf("search_query=", "search=")
        for (key in queryKeys) {
            val idx = decoded.indexOf(key)
            if (idx >= 0) {
                val raw = decoded.substring(idx + key.length).substringBefore("&").trim()
                if (raw.isNotBlank()) return raw.replace("+", " ").take(80)
            }
        }
        ""
    } catch (e: Exception) {
        ""
    }
}

private fun openChatLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
    }
}

@Composable
private fun LinkPreviewCard(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val site = chatLinkSite(url)
    val topic = remember(url) { chatLinkTopic(url) }
    val (badgeColor, title, domain) = when (site) {
        "youtube" -> Triple(
            Color(0xFFFF0000),
            if (topic.isNotBlank()) "YouTube: $topic" else "Watch video guide on YouTube",
            "youtube.com"
        )
        "wikipedia" -> Triple(
            Color(0xFF1E3A8A),
            if (topic.isNotBlank()) "Wikipedia: $topic" else "Read more on Wikipedia",
            "wikipedia.org"
        )
        else -> Triple(Color(0xFF616161), url, url.substringBefore("/"))
    }
    Card(
        onClick = { openChatLink(context, url) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (site == "wikipedia") Icons.Filled.Language else Icons.Filled.PlayArrow,
                    contentDescription = if (site == "youtube") "YouTube" else if (site == "wikipedia") "Wikipedia" else "Open link",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = domain + if (site == "youtube") " • Video" else if (site == "wikipedia") " • Article" else "",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open link",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    textToSpeech: TextToSpeech? = null,
    onSpeak: ((String) -> Unit)? = null
) {
    val isUser = message.isUser
    val bubbleColor = if (isUser) Color(0xFF064E3B) else Color.White
    val bubbleBorder = if (isUser) Color(0xFF064E3B) else Color(0xFFE2E8E2)
    val bubbleText = if (isUser) Color.White else Color(0xFF1F2933)
    val linkTint = if (isUser) Color(0xFFBBDEFB) else Color(0xFF1B5E20)
    val links = remember(message.text) { extractChatLinks(message.text) }
    val timeLabel = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(32.dp)
                    .background(Color(0xFF064E3B), CircleShape)
            ) {
                Text(
                    text = "S",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .then(if (isUser) Modifier.fillMaxWidth(0.84f) else Modifier.weight(1f))
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .background(bubbleColor, RoundedCornerShape(16.dp))
                .border(1.dp, bubbleBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (isUser || links.isEmpty()) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = bubbleText
                    )
                }
            } else {
                val linkOpener = LocalContext.current
                val linkedText = remember(message.text) {
                    buildAnnotatedString {
                        var cursor = 0
                        ChatLinkPattern.findAll(message.text).forEach { match ->
                            val cleaned = match.value.trimEnd('.', ',', ')', ']', '"', '\'', ';', ':')
                            val offsetInMatch = match.value.indexOf(cleaned)
                            val start = match.range.first + offsetInMatch
                            if (start > cursor) append(message.text.substring(cursor, start))
                            pushStringAnnotation(tag = "URL", annotation = cleaned)
                            pushStyle(
                                SpanStyle(
                                    color = linkTint,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            append(cleaned)
                            pop()
                            pop()
                            val trailing = match.value.substring(offsetInMatch + cleaned.length)
                            if (trailing.isNotEmpty()) append(trailing)
                            cursor = match.range.last + 1
                        }
                        if (cursor < message.text.length) append(message.text.substring(cursor))
                    }
                }
                ClickableText(
                    text = linkedText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = bubbleText
                    ),
                    onClick = { offset ->
                        linkedText.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()?.let { openChatLink(linkOpener, it.item) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSpeak != null && !isUser) {
                    IconButton(
                        onClick = { onSpeak(message.text) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (AppAudioGate.muted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (AppAudioGate.muted) "Unmute all audio" else "Mute all audio",
                            tint = Color(0xFF1B5E20),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timeLabel,
                    fontSize = 10.sp,
                    color = if (isUser) Color(0xFFB9D6C4) else Color(0xFF8A8A8A)
                )
            }

            if (!isUser) {
                if (textToSpeech != null && message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    ChatAudioHub(
                        textToSpeech = textToSpeech,
                        textToRead = message.text
                    )
                }
                if (links.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    links.forEach { url ->
                        LinkPreviewCard(url = url)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
