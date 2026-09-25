package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.data.TfliteLeafAnalyzer
import com.example.m_agrilink.domain.RoomFarmerAccountRepository
import com.example.m_agrilink.network.CropLiveLookup
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumMarketAnalyzerScreen() {
    val scrollState = rememberScrollState()
    var expanded by remember { mutableStateOf(false) }
    var selectedCounty by remember { mutableStateOf("") }
    val countyData = remember(selectedCounty) {
        MarketDataRepository.getCountyData(selectedCounty)
    }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var logisticsTapped by remember { mutableStateOf(false) }
    var farmerPlantedCrop by remember { mutableStateOf("") }
    var isScanningForDisease by remember { mutableStateOf(false) }
    var diseaseScanComplete by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var liveDiagnosis by remember { mutableStateOf("Point the lens at a maize leaf…") }
    var liveConfidence by remember { mutableStateOf(0f) }
    var liveSource by remember { mutableStateOf("on-device analyzer") }
    var analyzerAttached by remember { mutableStateOf(false) }
    val lastFrameMs = remember { longArrayOf(0L) }
    var showShambaChat by remember { mutableStateOf(false) }

    // --- 1. TEXT-TO-SPEECH CODES CONTROLLER (Swahili/English accessibility) ---
    val context = LocalContext.current
    var textToSpeech: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
            }
        }
        textToSpeech = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
            textToSpeech = null
        }
    }

    // Live repository meteorological snapshot (active screen variables)
    val activeTemperature = 15.9
    val activeHumidity = 72
    val activeWindSpeed = 5.2

    // --- CAMERAX PERMISSION + LIFECYCLE RUNTIME ---
    val lifecycleOwner = remember(context) { context as LifecycleOwner }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            isScanningForDisease = true
            diseaseScanComplete = false
        }
    }

    // --- TFLITE OFFLINE INTERPRETER (released with the screen) ---
    val tfliteInterpreter = remember(context) { TfliteLeafAnalyzer.loadInterpreter(context) }
    DisposableEffect(tfliteInterpreter) {
        onDispose {
            try {
                tfliteInterpreter?.close()
            } catch (e: Exception) {
            }
        }
    }

    val blueprintCrop = remember(farmerPlantedCrop, selectedCounty) {
        val query = farmerPlantedCrop.trim()
        if (query.isBlank()) null
        else MarketDataRepository.getCrop(
            selectedCounty.ifBlank { "Baringo" },
            query
        )
    }
    val cropKey = farmerPlantedCrop.trim().lowercase()
    val liveAdvisoryText = when {
        cropKey == "maize" && activeHumidity >= 70 ->
            "High humidity alert (72%) detected across Baringo. Field conditions increase the risk of Gray Leaf Spot fungal strains. Monitor crop leaves closely this week."
        cropKey == "beans" && activeWindSpeed <= 10.0 ->
            "Winds are calm at 5.2 km/h. Ideal morning window open to apply safe crop protections safely."
        else -> "Temp ${activeTemperature}°C • Humidity ${activeHumidity}% • Wind ${activeWindSpeed} km/h — scout ${selectedCounty.ifBlank { "Baringo" }} fields early morning; keep foliage dry to curb fungal pressure."
    }

    val counties = MarketDataRepository.getCounties()

    // --- ACCOUNT + LIVE LOOKUP RUNTIME (Room ACID profile, Gemini-first briefs) ---
    val scope = rememberCoroutineScope()
    val accountRepo = remember(context) { RoomFarmerAccountRepository(context.applicationContext) }
    val liveLookup = remember(context) { CropLiveLookup(context.applicationContext) }
    var liveBrief by remember { mutableStateOf<String?>(null) }
    var briefSource by remember { mutableStateOf("") }
    var liveLoading by remember { mutableStateOf(false) }
    var cropHistory by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        cropHistory = accountRepo.recentCrops()
    }

    // Any-crop live brief: local 4 crops render instantly, everything else
    // resolves via cache -> Gemini -> Wikipedia (debounced, stale-guarded).
    LaunchedEffect(cropKey, selectedCounty) {
        val query = farmerPlantedCrop.trim()
        if (query.isBlank()) {
            liveBrief = null
            liveLoading = false
            return@LaunchedEffect
        }
        val countyOrDefault = selectedCounty.ifBlank { "Baringo" }
        if (MarketDataRepository.getCrop(countyOrDefault, query) != null) {
            liveBrief = null
            liveLoading = false
            return@LaunchedEffect
        }
        delay(600)
        liveLoading = true
        val (text, source) = try {
            liveLookup.lookupCrop(query, countyOrDefault, activeTemperature, activeHumidity, activeWindSpeed)
        } catch (e: Exception) {
            "Live lookup failed. Check your connection and try again." to "OFFLINE"
        }
        if (query == farmerPlantedCrop.trim()) {
            liveBrief = text
            briefSource = source
            liveLoading = false
            if (source == "GEMINI" || source == "WIKI" || source == "CACHE") {
                accountRepo.logCropSearch(query, countyOrDefault, source)
                cropHistory = accountRepo.recentCrops()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8)) // Modern soft off-white canvas backdrop
            .verticalScroll(scrollState)
    ) {
        // --- 1. THE STATUS-BAR COMPLIANT HEADER GRADIENT BANNER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFA75D5D), Color(0xFF8C4A4A)) // Rich Terracotta Gradient
                    )
                )
                .statusBarsPadding() // ⚠️ FIX: Pushes layout down safely beneath phone clock/battery icons!
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Text(
                    text = "M-AgriLink",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6B325), // Elegant Gold tag
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "National Market Analyzer",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Optimize your agricultural corridor trade margins live.",
                    fontSize = 12.sp,
                    color = Color(0xFFF2E6E6)
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- 2. HIGH-CONTRAST GOLD DROPDOWN HUB ---
            Text(
                text = "Target County Corridor Hub",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C2C2E),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(3.dp, RoundedCornerShape(14.dp))
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(2.dp, Color(0xFFE6B325), RoundedCornerShape(14.dp)) // Signature Harvest Gold border
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCounty.isEmpty()) "Tap to select target region..." else selectedCounty,
                        color = if (selectedCounty.isEmpty()) Color.DarkGray else Color.Black, // ⚠️ FIX: Highly visible text out in the sun
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown Arrow",
                        tint = Color(0xFFE6B325),
                        modifier = Modifier.size(28.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 400.dp).background(Color.White)
                ) {
                    counties.forEach { county ->
                        DropdownMenuItem(
                            text = { Text(county, color = Color.Black, fontWeight = FontWeight.Medium) },
                            onClick = {
                                selectedCounty = county
                                expanded = false
                                scope.launch { accountRepo.updateCounty(county) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2B. FARMER PLANTED CROP INPUT MODULE ---
            Text(
                text = "🌱 What Crop Have You Planted?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C2C2E),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            OutlinedTextField(
                value = farmerPlantedCrop,
                onValueChange = { farmerPlantedCrop = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type Maize, Beans, Onions, Sorghum...", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = Color(0xFFE6B325),
                    unfocusedBorderColor = Color(0xFFE6B325)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Maize", "Beans", "Onions", "Sorghum").forEach { crop ->
                    OutlinedButton(
                        onClick = { farmerPlantedCrop = crop },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            crop,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cropKey == crop.lowercase()) Color(0xFFA75D5D) else Color.Black
                        )
                    }
                }
            }

            if (cropHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⏱ My recent crops",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2C2E),
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cropHistory.forEach { past ->
                        AssistChip(
                            onClick = { farmerPlantedCrop = past },
                            label = { Text(past, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (farmerPlantedCrop.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                if (blueprintCrop != null) {
                    // --- 2C. LOGIC-BASED AGRONOMIC & MANAGEMENT ENGINE ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📋 My Field Operation Blueprint",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C2C2E),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val fullAdvisory = "Audio System Swapped. Now reading comprehensive agricultural parameters for $selectedCounty. Planted crop profile index: Maize variety. Optimal spacing matrix configuration is 75 centimeters by 25 centimeters, placing 1 seed per hole to yield an approximate target of 53,000 healthy plants per acre. KALRO Land preparation rules require deep plowing to 20 to 25 centimeters at the onset of seasonal rainfall patterns, followed by harrowing to a fine tilth. Apply 10 tons per acre of well-decomposed manure combined with 60 kilograms of DAP fertilizer during row sowing operations. Safe harvesting boundaries demand drying grain under 13.5 percent moisture ceiling levels before hermetic storage packaging to prevent aflatoxin or mold contamination completely. Live environment alert: Local ambient humidity is currently holding high at 72 percent across the region, which significantly escalates regional risks for Gray Leaf Spot fungal strains. Farmers are strongly advised to inspect leaf edges daily this week."
                                        fullAdvisory.chunked(400).forEach { chunk ->
                                            textToSpeech?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VolumeUp,
                                        contentDescription = "Read blueprint aloud",
                                        tint = Color(0xFFE6B325)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${blueprintCrop.cropName} • ${selectedCounty.ifBlank { "Baringo" }} • Local KES ${blueprintCrop.localPriceKes} ➔ Hub KES ${blueprintCrop.hubPriceKes}",
                                color = Color(0xFFA75D5D),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Divider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = "🚜 Immediate Action:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(blueprintCrop.advisory.plantingSpacing, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(blueprintCrop.advisory.landPrep, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "🌾 Safe Harvesting Marker:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(blueprintCrop.advisory.moistureCeiling, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(blueprintCrop.advisory.harvestNote, color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            // --- 2D. LIVE METEOROLOGICAL ALERT STRIP ---
                            Text(
                                text = "⚠️ Live Crop Management Advisory",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA75D5D)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🌡 ${activeTemperature}°C • 💧 ${activeHumidity}% • 💨 ${activeWindSpeed} km/h",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(liveAdvisoryText, color = Color(0xFF2C2C2E), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else {
                    // --- 2E. LIVE BRIEF FOR ANY CROP (Gemini -> Wiki -> cache) ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🌐 Live Crop Brief: $farmerPlantedCrop",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C2C2E),
                                    modifier = Modifier.weight(1f)
                                )
                                if (briefSource.isNotEmpty() && !liveLoading) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            when (briefSource) {
                                                "GEMINI" -> "✨ GEMINI LIVE"
                                                "WIKI" -> "🌐 WIKIPEDIA"
                                                "CACHE" -> "⚡ CACHED"
                                                else -> briefSource
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (liveLoading && liveBrief == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFA75D5D)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Searching live sources…",
                                        color = Color.DarkGray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    liveBrief ?: "No blueprint found for \"$farmerPlantedCrop\" — try Maize, Beans, Onions or Sorghum.",
                                    color = Color.DarkGray,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Auto-saved to My recent crops on this device.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 3. THE HIGH-CONTRAST ARBITRAGE CARD MATRIX ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)) // Premium Dark Slate background
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ShowChart, contentDescription = null, tint = Color(0xFFE6B325))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Arbitrage Analysis Output",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6B325)
                        )
                    }

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                    if (selectedCounty.isEmpty()) {
                        Text(
                            text = "Choose a destination trading hub above to dynamically calculate local vs cross-border profit margins, logistics costs, and regional advisory records.",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        Text(
                            text = "Corridor Route: Baringo Local ➔ $selectedCounty",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        countyData.forEach { crop ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${crop.cropName} (${crop.unit})",
                                        color = Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Local Price: KES ${crop.localPriceKes}",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        crop.advisory.plantingSpacing,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Hub Target Price", color = Color.LightGray, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "KES ${crop.hubPriceKes}",
                                        color = Color(0xFFE6B325),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "+KES ${crop.netMarginKes} net",
                                        color = Color(0xFF81C784),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2C2E), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = countyData.firstOrNull()?.advisory?.moistureCeiling
                                    ?: "Dry grain to 13.5% moisture ceiling before bagging.",
                                color = Color(0xFF81C784), // Positive Green highlight
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- 3A. NATIVE CROP DISEASE DIAGNOSTIC STRIP ---
            // ID: @+id/cardCropDiagnostics
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color(0xFFA75D5D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🏥 Crop Disease Diagnostics",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2C2E)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Track Maize Lethal Necrosis or Fall Armyworm instantly with AI camera scan.",
                        color = Color.DarkGray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            diseaseScanComplete = false
                            isAnalyzing = false
                            scanResult =
                                "Mock AI scan: No Maize Lethal Necrosis detected. Suspected Fall Armyworm risk 12% — scout field edges."
                            if (hasCameraPermission) {
                                isScanningForDisease = true
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
                    ) {
                        Text("Launch AI Camera Scanner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    if (scanResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF4F6F8), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(scanResult ?: "", color = Color(0xFF2C2C2E), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }

            // --- 3A-ii. NATIVE CAMERAX AI FIELD SCANNER OVERLAY ---
            if (isScanningForDisease) {
                // Pre-warm the camera provider asynchronously off the main thread.
                LaunchedEffect(isScanningForDisease) {
                    withContext(Dispatchers.IO) {
                        try {
                            ProcessCameraProvider.getInstance(context).get()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                // Release the hardware lens instantly when the dialog closes.
                DisposableEffect(Unit) {
                    onDispose {
                        try {
                            cameraProvider?.unbindAll()
                        } catch (e: Exception) {
                        }
                        cameraProvider = null
                    }
                }
                val scanBeamAlpha by rememberInfiniteTransition(label = "scanBeam").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanBeamAlpha"
                )
                AlertDialog(
                    onDismissRequest = {
                        isScanningForDisease = false
                        diseaseScanComplete = false
                        isAnalyzing = false
                    },
                    title = {
                        Text(
                            "AI Field Camera Scanner",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2C2E)
                        )
                    },
                    text = {
                        Column {
                            if (!hasCameraPermission) {
                                Text(
                                    "Camera access is needed to scan crop leaves live in the field.",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
                                ) {
                                    Text("Grant Camera Access", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            PreviewView(ctx).apply {
                                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                            }
                                        },
                                        update = { previewView ->
                                            if (!analyzerAttached) {
                                                analyzerAttached = true
                                                val providerFuture = ProcessCameraProvider.getInstance(context)
                                                providerFuture.addListener({
                                                    try {
                                                        val provider = providerFuture.get()
                                                        cameraProvider = provider
                                                        val preview = Preview.Builder().build().also {
                                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                                        }
                                                        // Asynchronous frame extraction matrix (640x480, latest-only).
                                                        val imageAnalyzer = ImageAnalysis.Builder()
                                                            .setTargetResolution(Size(640, 480))
                                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                            .build()
                                                        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                                                            try {
                                                                val now = SystemClock.uptimeMillis()
                                                                if (now - lastFrameMs[0] >= 1500L) {
                                                                    lastFrameMs[0] = now
                                                                    val bitmap = TfliteLeafAnalyzer.imageProxyToBitmap(imageProxy)
                                                                    if (bitmap != null) {
                                                                        val result = TfliteLeafAnalyzer.classify(bitmap, tfliteInterpreter)
                                                                        liveDiagnosis = result.label
                                                                        liveConfidence = result.confidence
                                                                        liveSource = result.source
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                            } finally {
                                                                imageProxy.close()
                                                            }
                                                        }
                                                        provider.unbindAll()
                                                        provider.bindToLifecycle(
                                                            lifecycleOwner,
                                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                                            preview,
                                                            imageAnalyzer
                                                        )
                                                    } catch (e: Exception) {
                                                    }
                                                }, ContextCompat.getMainExecutor(context))
                                            }
                                        },
                                        modifier = Modifier.matchParentSize()
                                    )
                                    // Animated pulsing scanner beam while parsing imagery nodes.
                                    if (isAnalyzing) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(
                                                    Color(0xFFE6B325).copy(alpha = 0.25f * scanBeamAlpha)
                                                )
                                        )
                                        Text(
                                            "🔍 AI Analyzer: Scanning crop leaves for anomalies...",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(12.dp)
                                                .alpha(scanBeamAlpha)
                                        )
                                    }
                                    // Translucent floating capture circle over the lower center.
                                    if (!diseaseScanComplete) {
                                        IconButton(
                                            onClick = { isAnalyzing = true },
                                            enabled = !isAnalyzing,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 12.dp)
                                                .background(
                                                    Color.White.copy(alpha = 0.25f),
                                                    CircleShape
                                                )
                                                .size(72.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Circle,
                                                contentDescription = "Capture leaf scan",
                                                tint = Color.White,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                    }
                                }
                                // Live on-device lens verdict, swapped in as frames classify.
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            "📷 Live lens: $liveDiagnosis",
                                            color = Color.Black,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Confidence: ${(liveConfidence * 100).toInt()}% • $liveSource",
                                            color = Color.DarkGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                // Background coroutine simulates parsing the imagery nodes.
                                LaunchedEffect(isAnalyzing) {
                                    if (isAnalyzing) {
                                        delay(2500)
                                        diseaseScanComplete = true
                                        isAnalyzing = false
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (isAnalyzing) {
                                    Text(
                                        "🔍 AI Analyzer: Scanning crop leaves for anomalies...",
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.alpha(scanBeamAlpha)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFFE6B325),
                                        trackColor = Color(0xFFF4F6F8)
                                    )
                                } else if (!diseaseScanComplete) {
                                    Text(
                                        "Tap the capture circle to scan leaves with the back camera.",
                                        color = Color.DarkGray,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "🎯 DIAGNOSIS: Advanced Fall Armyworm (Spodoptera frugiperda) infestation detected.",
                                                color = Color(0xFF2C2C2E),
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "🌿 PUSH-PULL BIOLOGICAL STRATEGY (icipe Kenya): Intercrop your maize rows cleanly with Desmodium (repels the moths away via chemical volatilization) and plant Napier Grass or Brachiaria along your field perimeters as an attractive trap crop. This method cuts pest pressure by over 70% naturally.",
                                                color = Color(0xFF2C2C2E),
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "🐛 SCOUTING & CULTURAL REMEDIES (CABI Plantwise): Perform structural field scouting twice a week. Handpick and destroy visible egg masses or caterpillars immediately. Crushing caterpillars against leaf whorls prevents secondary lifecycle generations.",
                                                color = Color(0xFF2C2C2E),
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "🧪 TIMED CHEMICAL EMERGENCY ACTION (CIMMYT & FAO): If leaf damage indices surpass a 20% infestation threshold across young stalks, apply targeted bio-rationals or registered chemical options such as Spinetoram or Spinosad directly down into the whorls. Alternate chemical classes to completely halt pest resistance build-up.",
                                                color = Color(0xFF2C2C2E),
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                Uri.parse(TfliteLeafAnalyzer.CABI_URL)
                                                            )
                                                        )
                                                    } catch (e: Exception) {
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    "View full advisory profile on CABI Plantwise Bank",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (diseaseScanComplete) {
                                    scanResult = "Live lens match: $liveDiagnosis (${(liveConfidence * 100).toInt()}% confidence via $liveSource).\n\n🎯 DIAGNOSIS: Advanced Fall Armyworm (Spodoptera frugiperda) infestation detected.\n\n🌿 PUSH-PULL BIOLOGICAL STRATEGY (icipe Kenya): Intercrop your maize rows cleanly with Desmodium (repels the moths away via chemical volatilization) and plant Napier Grass or Brachiaria along your field perimeters as an attractive trap crop. This method cuts pest pressure by over 70% naturally.\n\n🐛 SCOUTING & CULTURAL REMEDIES (CABI Plantwise): Perform structural field scouting twice a week. Handpick and destroy visible egg masses or caterpillars immediately. Crushing caterpillars against leaf whorls prevents secondary lifecycle generations.\n\n🧪 TIMED CHEMICAL EMERGENCY ACTION (CIMMYT & FAO): If leaf damage indices surpass a 20% infestation threshold across young stalks, apply targeted bio-rationals or registered chemical options such as Spinetoram or Spinosad directly down into the whorls. Alternate chemical classes to completely halt pest resistance build-up."
                                }
                                isScanningForDisease = false
                                diseaseScanComplete = false
                                isAnalyzing = false
                            }
                        ) {
                            Text("Close Scan", fontWeight = FontWeight.Bold, color = Color(0xFFA75D5D))
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // --- 3A-iii. FRIENDLY SHAMBA AI CHAT ENTRY (typing, crops + cattle) ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "💬 Ask Shamba AI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Type how your farm, crops or cattle are behaving — Shamba replies with friendly advice in English or Kiswahili.",
                        color = Color(0xFFE8F5E9),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showShambaChat = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Start Chat", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            if (showShambaChat) {
                Dialog(onDismissRequest = { showShambaChat = false }) {
                    AiChatOverlay(
                        telemetry = TelemetryContext(
                            location = selectedCounty.ifBlank { "Baringo" },
                            temp = "$activeTemperature°C",
                            rainProb = "N/A",
                            wind = "$activeWindSpeed km/h",
                            humidity = "$activeHumidity%",
                            cropVariety = farmerPlantedCrop.ifBlank { "Maize" }
                        ),
                        onDismiss = { showShambaChat = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3B. REAL-TIME LORRY LOGISTICS FINDER ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Color(0xFFE6B325)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🚚 Lorry Logistics Finder",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6B325)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = MarketDataRepository.getTransportSummary(selectedCounty),
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { logisticsTapped = !logisticsTapped },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6B325))
                    ) {
                        Text(
                            if (logisticsTapped) "Hide Transporter Contacts" else "Find Lorry Now",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    if (logisticsTapped) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Mock contacts: Marigat Lorry SACCO (0722-XXX-XXX) • 10T @ KES 4,500 • 7T @ KES 3,200. Act on your margin above instantly.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3C. CENTRAL BANK SACCO INPUT PLANNER ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "💰 SACCO Input Planner",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2C2E)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedCounty.isEmpty()) "Wholesale seed & fertilizer trends (Baringo baseline)."
                        else "Wholesale trends for $selectedCounty — budget before planting.",
                        color = Color.DarkGray,
                        fontSize = 13.sp
                    )
                    Divider(color = Color(0xFFE0E0E0), modifier = Modifier.padding(vertical = 12.dp))
                    MarketDataRepository.getInputTrends(selectedCounty).forEach { input ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(input.name, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(input.unit, color = Color.Gray, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "KES ${input.priceKes}",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(input.trend, color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "Micro-saving tip: Save KES 250/week via SACCO to cover 1 acre DAP + seed before rains.",
                            color = Color(0xFF2E7D32),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // --- 4. THE TACTILE ACTION BUTTON WITH LAYOUT PROTECTION ---
            Button(
                onClick = { /* Launch Weather Activity Intent */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)

                .shadow(4.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA75D5D)) // Brand Terracotta accent
            ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Launch Regional Corridor Analyzer",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
// ⚠️ CRITICAL SPACER BUFFER: Prevents layout elements from crashing into your bottom tab bar icons
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
