package com.example.m_agrilink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

/**
 * MODULE 2: AI CONTEXTUAL ASSISTANT
 */

// 1. Data Models
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
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
@Composable
fun AiChatOverlay(
    telemetry: TelemetryContext,
    viewModel: AiAssistantViewModel = viewModel(),
    onDismiss: () -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        color = Color.White
    ) {
        // Chat voice-over engine (released with the overlay).
        val chatContext = LocalContext.current
        var chatTts: TextToSpeech? by remember { mutableStateOf(null) }

        DisposableEffect(chatContext) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(chatContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.setLanguage(Locale.US)
                    tts?.setSpeechRate(0.95f)
                    tts?.setPitch(1.0f)
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "M-AgriLink AI Assistant",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    fontSize = 18.sp
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }

            // Engine status micro-badge: Live Sync vs Offline Shield Active.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (dot, label) = when (viewModel.engineStatus) {
                    EngineStatus.ONLINE -> Color(0xFF2E7D32) to "Live Sync"
                    EngineStatus.OFFLINE -> Color(0xFFE6B325) to "Offline Shield Active"
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
                            "↻ Retry",
                            fontSize = 12.sp,
                            color = Color(0xFFA75D5D),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Chat Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    messages,
                    key = { it.timestamp.toString() + it.text.hashCode() }
                ) { message ->
                    var bubbleVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { bubbleVisible = true }
                    AnimatedVisibility(
                        visible = bubbleVisible,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        ChatBubble(
                            message = message,
                            onSpeak = { text ->
                                if (AppAudioGate.muted) {
                                    AppAudioGate.muted = false
                                    val spoken = text.replace(ChatLinkPattern, "").replace(Regex("[ \\t]+"), " ").trim()
                                    spoken.chunked(400).forEach { chunk ->
                                        chatTts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                    }
                                } else {
                                    chatTts?.stop()
                                    AppAudioGate.muted = true
                                }
                            }
                        )
                    }
                }
            }

            // Friendly quick questions (tap instead of typing)
            if (messages.none { it.isUser }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "🌽 Holes in my maize leaves",
                        "🐄 My cow gives less milk",
                        "🌱 Yellow leaves on beans",
                        "🐔 My chickens are coughing"
                    ).forEach { suggestion ->
                        OutlinedButton(onClick = { viewModel.sendMessage(suggestion, telemetry) }) {
                            Text(suggestion, fontSize = 12.sp)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your farm... or tap 🎤 to speak") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2E7D32),
                        unfocusedBorderColor = Color.LightGray
                    )
                )
                IconButton(onClick = { startVoiceInput() }) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice input",
                        tint = Color(0xFF2E7D32)
                    )
                }
                IconButton(
                    onClick = { submitInquiry(inputText) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color(0xFF2E7D32)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                    text = domain + if (site == "youtube") " • ▶ video" else if (site == "wikipedia") " • 🌐 article" else "",
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
    onSpeak: ((String) -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (message.isUser) Color(0xFFE8F5E9) else Color(0xFFF5F5F5),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val links = remember(message.text) { extractChatLinks(message.text) }
                    if (message.isUser || links.isEmpty()) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.text,
                                fontSize = 14.sp
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
                                    val end = start + cleaned.length
                                    if (start > cursor) append(message.text.substring(cursor, start))
                                    pushStringAnnotation(tag = "URL", annotation = cleaned)
                                    pushStyle(
                                        SpanStyle(
                                            color = Color(0xFF1E3A8A),
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
                            style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                            onClick = { offset ->
                                linkedText.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.let { openChatLink(linkOpener, it.item) }
                            }
                        )
                    }
                    if (onSpeak != null && !message.isUser) {
                        IconButton(onClick = { onSpeak(message.text) }) {
                            Icon(
                                imageVector = if (AppAudioGate.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (AppAudioGate.muted) "Unmute all audio" else "Mute all audio",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (!message.isUser) {
                    val links = remember(message.text) { extractChatLinks(message.text) }
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
}
