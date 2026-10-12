package com.example.m_agrilink.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.m_agrilink.data.AppLocale
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.network.CropLiveLookup
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decodes a gallery Uri into a 1024px-capped ARGB_8888 software bitmap on the
 * calling (IO) thread. Returns null when the bytes cannot be decoded.
 */
private fun decodeUriForVision(resolver: ContentResolver, uri: Uri): Bitmap? {
    return try {
        val base = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(src)
        } else {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } ?: return null
        val longest = maxOf(base.width, base.height)
        val sized = if (longest > 1024) {
            val scale = 1024f / longest
            Bitmap.createScaledBitmap(
                base,
                (base.width * scale).toInt().coerceAtLeast(1),
                (base.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            base
        }
        if (sized.config == Bitmap.Config.ARGB_8888) {
            sized
        } else {
            sized.copy(Bitmap.Config.ARGB_8888, false) ?: sized
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Crop intent guard for the Target Crop field: plant names only.
 * Returns true for crop/plant/tree names, false for INVALID_INTENT
 * (questions). Blanks return true so they see no error before typing.
 * Internal logs keep inclusive they/their/them wording.
 */
internal const val SCANNER_CROP_INVALID_INTENT_MESSAGE =
    "⚠️ Input Error: This field strictly accepts crop, plant, or tree names only (e.g., Mango, Beans, Passion Fruit). For conversational questions or farming advice, please utilize the floating Ask Shamba AI assistant in the bottom right corner."

private val SCANNER_QUESTION_WORDS = Regex("\\b(how|why|what|where|who|when)\\b")
private val SCANNER_QUESTION_PHRASES = Regex("\\b(is\\s+it|can\\s+i)\\b")
private val SCANNER_KNOWN_PLANTS = listOf(
    "maize", "beans", "onions", "sorghum", "potato", "tomato", "cabbage",
    "kale", "sukuma", "carrot", "banana", "avocado", "coffee", "tea",
    "wheat", "rice", "sugarcane", "sunflower", "groundnut", "cowpea",
    "gram", "millet", "cassava", "mango", "orange", "pawpaw", "cotton",
    "barley", "passion fruit", "fruit", "tree", "plant", "crop"
)

private fun validateCropInputIntent(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return true
    val lower = trimmed.lowercase()
    if ('?' in trimmed) return false
    if (SCANNER_QUESTION_WORDS.containsMatchIn(lower)) return false
    if (SCANNER_QUESTION_PHRASES.containsMatchIn(lower)) return false
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.size > 4) {
        val hasPlantToken = SCANNER_KNOWN_PLANTS.any { lower.contains(it) }
        if (!hasPlantToken) return false
    }
    return true
}

/**
 * Kenyan-accented TTS binding for the scanner: Kiswahili mode speaks sw-KE,
 * otherwise Kenyan English (en-KE) at a clear .95f pace so they can follow
 * dense diagnostics out in the field.
 */
internal fun applyScannerTtsLocale(tts: TextToSpeech) {
    try {
        if (AppLocale.isSwahili) {
            tts.setLanguage(Locale("sw", "KE"))
        } else {
            val result = tts.setLanguage(Locale("en", "KE"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(java.util.Locale.US)
            }
        }
        tts.setSpeechRate(0.95f)
        tts.setPitch(1.0f)
    } catch (e: Exception) {
    }
}

/** Strip emoji so the voice reads words for them, not symbol names. */
internal fun scannerSpeakableText(s: String): String =
    s.replace(Regex("[\\p{So}\\p{Sk}•●▲▼★☆→➔*]+"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Minimalist horizontal media controller hub (text-only, no decorative icons).
 * Pause keeps a resume offset so they do not lose their place; Stop purges
 * the speech thread and resets playback flags.
 */
@Composable
private fun ScannerTtsMediaHub(
    textToSpeech: TextToSpeech?,
    textToRead: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var resumeOffset by remember(textToRead) { mutableStateOf(0) }
    val readable = remember(textToRead) { scannerSpeakableText(textToRead) }
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
                    applyScannerTtsLocale(tts)
                    tts.stop()
                    isPaused = false
                    isPlaying = true
                    playable.chunked(400).forEach { chunk ->
                        tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "scanner_media_hub")
                    }
                } catch (e: Exception) {
                    isPlaying = false
                }
            },
            enabled = textToSpeech != null && readable.isNotBlank() && !isPlaying
        ) {
            Text("▶️ Play Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text("⏸️ Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text("⏹️ Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Layer 6: all user-visible Shamba Scan copy resolves via AppStrings
 * ([str]/[tr]) so the top language menu toggles English ↔ Kiswahili
 * natively. Internal diagnostic logs keep inclusive they/their/them wording.
 */

/**
 * Phase 1 scanner shell: branded empty-state layout with a disabled input
 * dock. State machines, image_picker wiring, and vision dispatch land in
 * Phase 2 — every control here is intentionally non-functional for now.
 */
@Composable
fun AiScannerScreen(
    navController: NavController
) {
    // Phase 2 reactive chat state hooks.
    var cropInputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var chatMessagesList by remember { mutableStateOf(listOf<String>()) }
    // Phase 4 live vision state: loading flag + glass result bubble text.
    var visionLoading by remember { mutableStateOf(false) }
    var visionResult by remember { mutableStateOf<String?>(null) }
    // True when the last result is the network fallback (drives retry UI).
    // Kept separate from the text so the banner itself can re-resolve via
    // str() and toggle language without re-running the scan.
    var visionIsNetworkError by remember { mutableStateOf(false) }
    // Last submitted crop label, retained so the retry button can re-send
    // the same buffer without requiring re-typing.
    var lastCropName by remember { mutableStateOf("") }
    var langExpanded by remember { mutableStateOf(false) }
    // Intent guard: halt vision dispatch when they type a question instead of a plant name.
    val isCropIntentInvalid = remember(cropInputText) {
        cropInputText.isNotBlank() && !validateCropInputIntent(cropInputText)
    }
    val sendEnabled = cropInputText.isNotBlank() && !visionLoading && !isCropIntentInvalid

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveLookup = remember(context) { CropLiveLookup(context.applicationContext) }

    // Kenyan-accented speech engine for reading diagnostics aloud to them.
    var scannerTts: TextToSpeech? by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { applyScannerTtsLocale(it) }
            }
        }
        scannerTts = tts
        onDispose {
            try {
                tts?.stop()
            } catch (e: Exception) {
            }
            try {
                tts?.shutdown()
            } catch (e: Exception) {
            }
            scannerTts = null
        }
    }

    // Atomic vision pipeline: decode off-main, then one all-or-nothing AI
    // transaction. Partial/truncated payloads roll back to a clean inclusive
    // notice for them; only fully validated text commits to their screen
    // (already durably cached inside the transaction). Isolated IO sandboxing
    // keeps simultaneous requests from crossing state or blocking their UI.
    fun startVisionDiagnose(crop: String, uri: Uri) {
        visionLoading = true
        visionResult = null
        visionIsNetworkError = false
        scope.launch {
            val appContext = context.applicationContext
            val frame = withContext(Dispatchers.IO) {
                decodeUriForVision(context.contentResolver, uri)
            }
            if (frame == null) {
                // Rollback: flush partials, show clean guidance they can act on.
                visionResult = tr("sscan_decode_fail")
                visionIsNetworkError = false
                visionLoading = false
                return@launch
            }
            val safeCrop = crop.ifBlank { "crop" }
            val (text, _) = try {
                MarketDataRepository.runAiVisionTransaction(
                    appContext = appContext,
                    cropName = safeCrop,
                    county = "Kenya",
                    imageUri = uri.toString()
                ) {
                    liveLookup.visionDiagnose(
                        frame,
                        safeCrop,
                        "Kenya"
                    )
                }
            } catch (e: Exception) {
                // Rollback on unexpected failure: nothing partial survives.
                null to "OFFLINE"
            }
            if (text.isNullOrBlank()) {
                // Transaction rolled back: clean fallback notice, no fragments.
                visionResult = tr("sscan_network_error")
                visionIsNetworkError = true
            } else {
                // Commit: full payload renders; durability already anchored.
                visionResult = text
                visionIsNetworkError = false
            }
            visionLoading = false
        }
    }

    // Phase 3 docking gallery picker launcher (photo library, images only).
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF064E3B))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = str("sscan_back"),
                        tint = Color.White
                    )
                }
                Text(
                    text = str("sscan_title"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Box {
                    IconButton(onClick = { langExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = str("sscan_lang"),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        listOf("English", "Kiswahili").forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (AppLocale.language == lang) "✓ " else "") + lang
                                    )
                                },
                                onClick = {
                                    AppLocale.language = lang
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF064E3B))
                    // Tablet fix: respect system bars so the dock never hides
                    // under the navigation taskbar layer.
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = cropInputText,
                        onValueChange = { cropInputText = it },
                        enabled = true,
                        singleLine = true,
                        isError = isCropIntentInvalid,
                        label = { Text(str("sscan_crop_label")) },
                        placeholder = { Text(str("sscan_crop_placeholder")) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = true,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(str("sscan_add_photo"), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val crop = cropInputText.trim()
                            // Halt vision pipeline on INVALID_INTENT; inline banner below explains.
                            if (!validateCropInputIntent(crop)) return@Button
                            val uri = selectedImageUri
                            chatMessagesList = chatMessagesList + trf("sscan_prefix", crop)
                            lastCropName = crop
                            cropInputText = ""
                            if (uri == null) {
                                visionResult = tr("sscan_need_photo")
                                visionIsNetworkError = false
                                return@Button
                            }
                            startVisionDiagnose(crop, uri)
                        },
                        enabled = sendEnabled,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE6B325),
                            contentColor = Color(0xFF1B4332),
                            disabledContainerColor = Color(0xFFE6B325).copy(alpha = 0.35f),
                            disabledContentColor = Color(0xFF1B4332).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = str("sscan_send")
                        )
                    }
                }
                if (isCropIntentInvalid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDECEA), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFB71C1C), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = SCANNER_CROP_INVALID_INTENT_MESSAGE,
                            color = Color(0xFF7F1D1D),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF064E3B)
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF064E3B), Color(0xFF1B4332))
                    )
                )
                .padding(scaffoldPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                chatMessagesList.forEach { message ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE6B325)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1B4332),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (visionLoading || visionResult != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.14f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFE6B325)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = str("sscan_vision_title"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE6B325)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (visionLoading && visionResult == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFE6B325)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = str("sscan_analyzing"),
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = if (visionIsNetworkError) str("sscan_network_error") else (visionResult ?: ""),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = Color.White
                                    )
                                }
                                // Horizontal media hub: they can listen to their result hands-free.
                                if (!visionIsNetworkError && !visionResult.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ScannerTtsMediaHub(
                                        textToSpeech = scannerTts,
                                        textToRead = visionResult ?: ""
                                    )
                                }
                                if (visionIsNetworkError && visionResult != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            val retryUri = selectedImageUri
                                            if (retryUri != null && !visionLoading) {
                                                startVisionDiagnose(
                                                    lastCropName.ifBlank { "crop" },
                                                    retryUri
                                                )
                                            }
                                        },
                                        enabled = !visionLoading && selectedImageUri != null,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE6B325),
                                            contentColor = Color(0xFF1B4332),
                                            disabledContainerColor = Color(0xFFE6B325).copy(alpha = 0.35f),
                                            disabledContentColor = Color(0xFF1B4332).copy(alpha = 0.5f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = str("sscan_retry_desc"),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = str("sscan_retry"),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (visionLoading) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFFE6B325)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = str("sscan_analyzing"),
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = "\uD83E\uDD16\uD83C\uDF3F",
                                fontSize = 40.sp
                            )
                        }
                        if (selectedImageUri != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    Color(0xFFE6B325)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    AsyncImage(
                                        model = selectedImageUri,
                                        contentDescription = str("sscan_photo_desc"),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                    IconButton(
                                        onClick = { selectedImageUri = null },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.55f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = str("sscan_remove"),
                                            tint = Color(0xFFE6B325)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = str("sscan_welcome"),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
