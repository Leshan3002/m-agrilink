package com.example.m_agrilink.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.m_agrilink.network.CropLiveLookup
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
    val sendEnabled = cropInputText.isNotBlank() && !visionLoading

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveLookup = remember(context) { CropLiveLookup(context.applicationContext) }

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
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "🤖 Shamba Scan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
        bottomBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF064E3B))
                    // Tablet fix: respect system bars so the dock never hides
                    // under the navigation taskbar layer.
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = cropInputText,
                    onValueChange = { cropInputText = it },
                    enabled = true,
                    singleLine = true,
                    label = { Text("What crop are you scanning?") },
                    placeholder = { Text("Target crop (e.g., Passion Fruit)...") },
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
                    Text("📁 Add Photo", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val crop = cropInputText.trim()
                        val uri = selectedImageUri
                        chatMessagesList = chatMessagesList + "Scanning crop: $crop"
                        cropInputText = ""
                        if (uri == null) {
                            visionResult = "They need to add a photo first — their words alone cannot be diagnosed. They can tap 📁 Add Photo and retry."
                            return@Button
                        }
                        visionLoading = true
                        visionResult = null
                        scope.launch {
                            val frame = withContext(Dispatchers.IO) {
                                decodeUriForVision(context.contentResolver, uri)
                            }
                            if (frame == null) {
                                visionResult = "Their photo could not be decoded. They can pick a different image and retry."
                                visionLoading = false
                                return@launch
                            }
                            val (text, _) = try {
                                liveLookup.visionDiagnose(
                                    frame,
                                    crop.ifBlank { "crop" },
                                    "Kenya"
                                )
                            } catch (e: Exception) {
                                null to "OFFLINE"
                            }
                            visionResult = text
                                ?: "Vision service unreachable — connection timed out or dropped. Their photo is kept; they can retry when the signal returns."
                            visionLoading = false
                        }
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
                        contentDescription = "Send"
                    )
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
                                text = "🔬 Gemini Vision",
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
                                        text = "Analyzing their photo…",
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
                                        text = visionResult ?: "",
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = Color.White
                                    )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Black.copy(alpha = 0.35f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        Color(0xFFE6B325)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    AsyncImage(
                                        model = selectedImageUri,
                                        contentDescription = "Selected crop photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                IconButton(onClick = { selectedImageUri = null }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove photo",
                                        tint = Color(0xFFE6B325)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Welcome to Shamba Scan. Please enter your crop details and add a photo to begin your dynamic agronomic analysis.",
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
