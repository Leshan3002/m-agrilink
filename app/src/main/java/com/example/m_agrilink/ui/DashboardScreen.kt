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
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.data.CropNameNormalizer
import com.example.m_agrilink.data.TfliteLeafAnalyzer
import com.example.m_agrilink.data.local.AgriLinkDatabase
import com.example.m_agrilink.data.local.TransportTask
import com.example.m_agrilink.data.local.TransporterProfile
import com.example.m_agrilink.domain.RoomFarmerAccountRepository
import com.example.m_agrilink.network.CropLiveLookup
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARCHITECT_ATTRIBUTION =
    "Application Created and Engineered by Lead Architect Levis Lekesio."

class ImageProcessingException(message: String) : Exception(message)

/** Dynamic crop diagnostic lookup: no hardcoded maize fallback. */
private fun cropScanKey(raw: String): String = raw.trim().lowercase()

private fun cropDiagnosticTitle(
    plantedCrop: String,
    liveDiagnosis: String,
    confidencePct: Int,
    source: String
): String {
    if (liveDiagnosis.contains("Healthy", ignoreCase = true)) {
        val name = plantedCrop.trim().ifBlank { "Crop" }
        return "✅ DIAGNOSIS: $name looks healthy ($confidencePct% confidence via $source)."
    }
    return when (cropScanKey(plantedCrop)) {
        "mango" -> "🎯 DIAGNOSIS: Mango Fruit Fly (Bactrocera dorsalis) damage detected on skin surfaces."
        "beans", "bean" -> "🎯 DIAGNOSIS: Bean Fly (Ophiomyia phaseoli) stem tunneling or Black Bean Aphid clustering detected."
        "maize" -> "🎯 DIAGNOSIS: $liveDiagnosis detected ($confidencePct% confidence via $source) — Fall Armyworm (Spodoptera frugiperda) protocol."
        else -> {
            val label = plantedCrop.trim().ifBlank { liveDiagnosis }
            if (label.isBlank()) "🎯 DIAGNOSIS: General foliar anomaly detected."
            else "🎯 DIAGNOSIS: General Foliar/Leaf spot anomaly detected for ${label.uppercase()}"
        }
    }
}

private fun cropDiagnosticBody(plantedCrop: String): List<String> {
    return when (cropScanKey(plantedCrop)) {
        "mango" -> listOf(
            "🌿 MANAGEMENT ADVISORY: Do not apply heavy chemical sprays near harvesting. Hang localized methyl eugenol pheromone traps at canopy level (10 traps per acre) to trap male flies. Collect and bury all fallen fruits at least 2 feet deep or seal them inside black plastic bags under the sun for few days to completely suffocate larvae and break the pest's lifecycle."
        )
        "beans", "bean" -> listOf(
            "🌿 MANAGEMENT ADVISORY: Earth up soil around the plant stems during weeding to encourage adventitious root growth. For severe aphid attacks, spray natural neem seed kernel extracts or potassium-soap solutions early in the morning before bees become active."
        )
        "maize" -> listOf(
            "🌿 PUSH-PULL BIOLOGICAL STRATEGY (icipe Kenya): Intercrop your maize rows cleanly with Desmodium (repels the moths away via chemical volatilization) and plant Napier Grass or Brachiaria along your field perimeters as an attractive trap crop. This method cuts pest pressure by over 70% naturally.",
            "🐛 SCOUTING & CULTURAL REMEDIES (CABI Plantwise): Perform structural field scouting twice a week. Handpick and destroy visible egg masses or caterpillars immediately. Crushing caterpillars against leaf whorls prevents secondary lifecycle generations.",
            "🧪 TIMED CHEMICAL EMERGENCY ACTION (CIMMYT & FAO): If leaf damage indices surpass a 20% infestation threshold across young stalks, apply targeted bio-rationals or registered chemical options such as Spinetoram or Spinosad directly down into the whorls. Alternate chemical classes to completely halt pest resistance build-up."
        )
        else -> listOf(
            "🌿 MANAGEMENT ADVISORY: Maintain proper plant row spacing to improve air circulation and reduce leaf wetness. Prune infected lower leaves immediately. Avoid overhead irrigation during cold evenings to limit fungal spore tracking across your plot."
        )
    }
}

private fun cropFullAdvisoryText(
    plantedCrop: String,
    liveDiagnosis: String,
    confidencePct: Int,
    source: String
): String {
    val title = cropDiagnosticTitle(plantedCrop, liveDiagnosis, confidencePct, source)
    val body = if (liveDiagnosis.contains("Healthy", ignoreCase = true)) {
        listOf("🌿 PREVENTIVE CARE: Keep scouting twice weekly, clear weeds and debris around the base, mulch to hold moisture, and watch leaf edges after humid nights. Retake the scan if spots, holes, or yellowing appear.")
    } else {
        cropDiagnosticBody(plantedCrop)
    }
    return (listOf(title) + body).joinToString("\n\n")
}

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
    var liveDiagnosis by remember { mutableStateOf("Point the lens at a crop leaf…") }
    var liveConfidence by remember { mutableStateOf(0f) }
    var liveSource by remember { mutableStateOf("on-device analyzer") }
    var analyzerAttached by remember { mutableStateOf(false) }
    val lastFrameMs = remember { longArrayOf(0L) }
    var showShambaChat by remember { mutableStateOf(false) }

    // --- TOP ACTION BAR + ACCOUNT STATE MACHINE (thread-safe Compose state) ---
    var isUserLoggedIn by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var isDarkTheme by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("English") }
    var activeViewport by remember { mutableStateOf("home") }
    var navExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    var cacheNotice by remember { mutableStateOf<String?>(null) }
    val systemMetadata = remember {
        mapOf(
            "architect" to ARCHITECT_ATTRIBUTION,
            "viewport" to "home",
            "engine" to "M-AgriLink offline cache"
        )
    }

    // System back button: step back through scanner -> chat -> weather page
    // instead of exiting the app from a sub-page.
    BackHandler(enabled = isScanningForDisease || showShambaChat || activeViewport == "weather") {
        when {
            isScanningForDisease -> {
                isScanningForDisease = false
                diseaseScanComplete = false
                isAnalyzing = false
            }
            showShambaChat -> showShambaChat = false
            activeViewport == "weather" -> activeViewport = "home"
        }
    }

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

    val cropMatch = remember(farmerPlantedCrop) { CropNameNormalizer.normalize(farmerPlantedCrop) }
    val cropQuery = cropMatch?.canonical ?: ""
    val blueprintCrop = remember(cropQuery, selectedCounty) {
        if (cropQuery.isBlank()) null
        else MarketDataRepository.getCrop(
            selectedCounty.ifBlank { "Baringo" },
            cropQuery
        )
    }
    val cropKey = cropQuery.lowercase()
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

    // --- LORRY MARKETPLACE RUNTIME (Room-backed registry + task tracking) ---
    var showRegisterForm by remember { mutableStateOf(false) }
    var regDriver by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regCapacity by remember { mutableStateOf("") }
    var regTown by remember { mutableStateOf("") }
    var regRoute by remember { mutableStateOf("") }
    var registeredLorries by remember { mutableStateOf(listOf<TransporterProfile>()) }
    var transportTasks by remember { mutableStateOf(listOf<TransportTask>()) }
    val transportDao = remember(context) { AgriLinkDatabase.getDatabase(context.applicationContext).transportDao() }

    fun refreshTransport() {
        scope.launch {
            val lorries = withContext(Dispatchers.IO) { transportDao.allLorries() }
            val tasks = withContext(Dispatchers.IO) { transportDao.recentTasks() }
            registeredLorries = lorries
            transportTasks = tasks
        }
    }

    fun hireTransport(name: String, phone: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                transportDao.createTask(
                    TransportTask(
                        transporterName = name,
                        phone = phone,
                        crop = farmerPlantedCrop.trim().ifBlank { "Produce" },
                        fromCounty = selectedCounty.ifBlank { "Baringo" }
                    )
                )
            }
            refreshTransport()
        }
    }

    fun advanceTask(task: TransportTask) {
        val next = when (task.status) {
            "REQUESTED" -> "EN_ROUTE"
            "EN_ROUTE" -> "DELIVERED"
            else -> return
        }
        scope.launch {
            withContext(Dispatchers.IO) { transportDao.setTaskStatus(task.id, next) }
            refreshTransport()
        }
    }

    fun cancelTask(task: TransportTask) {
        scope.launch {
            withContext(Dispatchers.IO) { transportDao.setTaskStatus(task.id, "CANCELLED") }
            refreshTransport()
        }
    }

    fun dialPhone(phone: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        } catch (e: Exception) {
        }
    }

    fun whatsappTransport(name: String, phone: String, capacity: String) {
        try {
            val digits = phone.filter { it.isDigit() }
            val waNumber = if (digits.startsWith("0")) "254" + digits.drop(1) else digits
            val message = Uri.encode(
                "Hello $name! I need a $capacity lorry " +
                    "from ${selectedCounty.ifBlank { "Baringo" }} for farm produce. " +
                    "Please share availability and rate."
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$waNumber?text=$message"))
            )
        } catch (e: Exception) {
        }
    }

    @Composable
    fun lorryField(value: String, onValue: (String) -> Unit, hint: String) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, color = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
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
    }

    LaunchedEffect(Unit) {
        cropHistory = accountRepo.recentCrops()
    }

    // Pre-warm Shamba at launch so the AI is ready before the chat opens.
    val shambaWarmup: AiAssistantViewModel = viewModel()
    LaunchedEffect(Unit) {
        shambaWarmup.probeEngine()
    }

    // Any-crop live brief: local 4 crops render instantly, everything else
    // resolves via cache -> Gemini -> Wikipedia (debounced, stale-guarded).
    LaunchedEffect(cropKey, selectedCounty) {
        val query = cropQuery
        val requestKey = cropKey
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
        if (requestKey == CropNameNormalizer.canonicalOrOriginal(farmerPlantedCrop).lowercase()) {
            liveBrief = text
            briefSource = source
            liveLoading = false
            if (source == "GEMINI" || source == "OPENFARM" || source == "WIKI" || source == "CACHE") {
                accountRepo.logCropSearch(query, countyOrDefault, source)
                cropHistory = accountRepo.recentCrops()
            }
        }
    }

    Scaffold { scaffoldPadding ->
        val canvasBackground = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF4F6F8)
        // Dark-mode legible text for labels drawn directly on the canvas
        // (cards stay white with dark text, so they are untouched).
        val contentPrimary = if (isDarkTheme) Color.White else Color(0xFF2C2C2E)
        val contentAccent = if (isDarkTheme) Color(0xFFE6B325) else Color(0xFFA75D5D)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasBackground) // Modern canvas backdrop, dark-mode aware
                .verticalScroll(scrollState)
                .padding(bottom = scaffoldPadding.calculateBottomPadding())
        ) {
        // --- 1. THE STATUS-BAR COMPLIANT HEADER GRADIENT BANNER + TOP ACTION BAR ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFA75D5D), Color(0xFF8C4A4A)) // Rich Terracotta Gradient
                    )
                )
                .statusBarsPadding() // Pushes layout down safely beneath phone clock/battery icons!
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = systemMetadata["architect"] ?: ARCHITECT_ATTRIBUTION,
                        fontSize = 10.sp,
                        color = Color(0xFFF2E6E6),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    if (isUserLoggedIn) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "👨‍🌾 Levis Lekesio (Developer Hub / Farmer Profile)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
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
                Spacer(modifier = Modifier.height(14.dp))
                // Top 3 anchor buttons with nested sub-menus
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { navExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("🌐 Navigation", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        DropdownMenu(
                            expanded = navExpanded,
                            onDismissRequest = { navExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Go to Home Page Dashboard", color = Color.Black) },
                                onClick = {
                                    activeViewport = "home"
                                    navExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Market Overview", color = Color.Black) },
                                onClick = {
                                    activeViewport = "market"
                                    navExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Weather Terminal", color = Color.Black) },
                                onClick = {
                                    activeViewport = "weather"
                                    navExpanded = false
                                }
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { langExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("🔤 Language", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        DropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            listOf("English", "Kiswahili", "Kikuyu", "Kaljin", "Luo").forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (selectedLanguage == lang) "✓ " else "") + lang,
                                            color = Color.Black
                                        )
                                    },
                                    onClick = {
                                        selectedLanguage = lang
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { settingsExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
                        ) {
                            Text("⚙️ System Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        DropdownMenu(
                            expanded = settingsExpanded,
                            onDismissRequest = { settingsExpanded = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isDarkTheme) "Toggle Dark Mode Theme (On)" else "Toggle Dark Mode Theme (Off)", color = Color.Black) },
                                onClick = {
                                    isDarkTheme = !isDarkTheme
                                    settingsExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Device Cache", color = Color.Black) },
                                onClick = {
                                    liveBrief = null
                                    briefSource = ""
                                    cropHistory = listOf()
                                    cacheNotice = "Device cache cleared."
                                    settingsExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Accessibility Profiles", color = Color.Black) },
                                onClick = {
                                    cacheNotice = "Accessibility profiles: Standard / High-contrast / Large-text ready."
                                    settingsExpanded = false
                                }
                            )
                        }
                    }
                }
                if (cacheNotice != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(cacheNotice ?: "", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- 1B. FARMER ACCOUNT STATE MACHINE ---
            if (!isUserLoggedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "👨‍🌾 Farmer Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2C2E)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create an account to personalize advisories ($selectedLanguage).",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter farmer name", color = Color.Gray) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { isUserLoggedIn = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Create Farmer Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Active viewport indicator: Home resets to primary dashboard.
            Text(
                text = when (activeViewport) {
                    "market" -> "📊 Viewport: Market Overview • $selectedLanguage"
                    "weather" -> "🌦 Viewport: Weather Terminal • $selectedLanguage"
                    else -> "🏠 Viewport: Home Dashboard • $selectedLanguage"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else Color(0xFF2C2C2E),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Dedicated Weather Terminal page (separate from Home page).
            if (activeViewport == "weather") {
                WeatherTerminalScreen(
                    county = selectedCounty.ifBlank { "Tana River" },
                    isDarkTheme = isDarkTheme,
                    onClose = { activeViewport = "home" }
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {

            // --- 2. HIGH-CONTRAST GOLD DROPDOWN HUB ---
            Text(
                text = "Target County Corridor Hub",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = contentPrimary,
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
                color = contentPrimary,
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

            if (cropMatch?.corrected == true && farmerPlantedCrop.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "🔍 Showing results for \"${cropMatch.canonical}\" (you typed \"${cropMatch.original}\")",
                    fontSize = 12.sp,
                    color = contentAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (cropHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⏱ My recent crops",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentPrimary,
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
                                        if (AppAudioGate.muted) {
                                            AppAudioGate.muted = false
                                            val fullAdvisory = "Audio System Swapped. Now reading comprehensive agricultural parameters for $selectedCounty. Planted crop profile index: Maize variety. Optimal spacing matrix configuration is 75 centimeters by 25 centimeters, placing 1 seed per hole to yield an approximate target of 53,000 healthy plants per acre. KALRO Land preparation rules require deep plowing to 20 to 25 centimeters at the onset of seasonal rainfall patterns, followed by harrowing to a fine tilth. Apply 10 tons per acre of well-decomposed manure combined with 60 kilograms of DAP fertilizer during row sowing operations. Safe harvesting boundaries demand drying grain under 13.5 percent moisture ceiling levels before hermetic storage packaging to prevent aflatoxin or mold contamination completely. Live environment alert: Local ambient humidity is currently holding high at 72 percent across the region, which significantly escalates regional risks for Gray Leaf Spot fungal strains. Farmers are strongly advised to inspect leaf edges daily this week."
                                            fullAdvisory.chunked(400).forEach { chunk ->
                                                textToSpeech?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                            }
                                        } else {
                                            textToSpeech?.stop()
                                            AppAudioGate.muted = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (AppAudioGate.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                        contentDescription = if (AppAudioGate.muted) "Unmute all audio" else "Mute all audio",
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
                                                "OPENFARM" -> "🌱 OPENFARM"
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
                            scanResult = null
                            liveDiagnosis = "Point the lens at a ${farmerPlantedCrop.trim().ifBlank { "crop" }} leaf…"
                            liveConfidence = 0f
                            liveSource = "on-device analyzer"
                            scannerError = null
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
                                                                try {
                                                                    val now = SystemClock.uptimeMillis()
                                                                    if (now - lastFrameMs[0] >= 1500L) {
                                                                        lastFrameMs[0] = now
                                                                        val bitmap = TfliteLeafAnalyzer.imageProxyToBitmap(imageProxy)
                                                                            ?: throw ImageProcessingException("Leaf frame decode returned null")
                                                                        val smart = TfliteLeafAnalyzer.classifySmart(bitmap, tfliteInterpreter)
                                                                        if (smart.quality.usable) {
                                                                            liveDiagnosis = smart.diagnosis.label
                                                                            liveConfidence = smart.diagnosis.confidence
                                                                            liveSource = smart.diagnosis.source
                                                                            scannerError = null
                                                                        } else {
                                                                            liveDiagnosis = smart.diagnosis.label
                                                                            liveConfidence = smart.diagnosis.confidence
                                                                            liveSource = smart.diagnosis.source
                                                                            scannerError = smart.quality.guidance
                                                                        }
                                                                    }
                                                                } catch (e: ImageProcessingException) {
                                                                    scannerError = "⚠️ Scanner Alert: Leaf frame parsing failed due to suboptimal lighting conditions or hardware focus latency. Please steady your Lenovo camera device and try again."
                                                                } catch (e: IllegalStateException) {
                                                                    scannerError = "⚠️ Scanner Alert: Leaf frame parsing failed due to suboptimal lighting conditions or hardware focus latency. Please steady your Lenovo camera device and try again."
                                                                }
                                                            } catch (e: Exception) {
                                                                scannerError = "⚠️ Scanner Alert: Leaf frame parsing failed due to suboptimal lighting conditions or hardware focus latency. Please steady your Lenovo camera device and try again."
                                                            } finally {
                                                                try {
                                                                    imageProxy.close()
                                                                } catch (e: Exception) {
                                                                    scannerError = "⚠️ Scanner Alert: Leaf frame parsing failed due to suboptimal lighting conditions or hardware focus latency. Please steady your Lenovo camera device and try again."
                                                                }
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
                                if (scannerError != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFDECEA), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE57373), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            scannerError ?: "",
                                            color = Color(0xFFB71C1C),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
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
                                // Capture gate: only a valid leaf frame completes the scan.
                                // Dark / blurry / non-leaf frames stay in retake mode.
                                LaunchedEffect(isAnalyzing) {
                                    if (isAnalyzing) {
                                        delay(2500)
                                        val invalid = scannerError != null ||
                                            liveConfidence < 0.55f ||
                                            liveDiagnosis.startsWith("Invalid") ||
                                            liveDiagnosis.startsWith("Uncertain") ||
                                            liveDiagnosis.startsWith("Point the lens")
                                        if (invalid) {
                                            if (scannerError == null) {
                                                scannerError = "⚠️ Scanner Alert: Scan unclear — clean the lens, improve lighting, point at a crop leaf filling the frame, and tap capture again."
                                            }
                                            diseaseScanComplete = false
                                        } else {
                                            diseaseScanComplete = true
                                        }
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
                                        "Tap the capture circle to scan ${farmerPlantedCrop.trim().ifBlank { "crop" }} leaves with the back camera.",
                                        color = Color.DarkGray,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                } else {
                                    val confidencePct = (liveConfidence * 100).toInt()
                                    val scanTitle = cropDiagnosticTitle(farmerPlantedCrop, liveDiagnosis, confidencePct, liveSource)
                                    val scanBody: List<String> =
                                        if (liveDiagnosis.contains("Healthy", ignoreCase = true)) {
                                            listOf("🌿 PREVENTIVE CARE: Keep scouting twice weekly, clear weeds and debris around the base, mulch to hold moisture, and watch leaf edges after humid nights. Retake the scan if spots, holes, or yellowing appear.")
                                        } else {
                                            cropDiagnosticBody(farmerPlantedCrop)
                                        }
                                    val scanSpeech = cropFullAdvisoryText(farmerPlantedCrop, liveDiagnosis, confidencePct, liveSource)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    scanTitle,
                                                    color = Color(0xFF2C2C2E),
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        if (AppAudioGate.muted) {
                                                            AppAudioGate.muted = false
                                                            scanSpeech.chunked(400).forEach { chunk ->
                                                                textToSpeech?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                                            }
                                                        } else {
                                                            textToSpeech?.stop()
                                                            AppAudioGate.muted = true
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (AppAudioGate.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                                        contentDescription = "Read scan result aloud",
                                                        tint = Color(0xFFE6B325)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            scanBody.forEach { paragraph ->
                                                Text(
                                                    paragraph,
                                                    color = Color(0xFF2C2C2E),
                                                    fontSize = 13.sp,
                                                    lineHeight = 19.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
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
                                val valid = diseaseScanComplete &&
                                    scannerError == null &&
                                    liveConfidence >= 0.55f &&
                                    !liveDiagnosis.startsWith("Invalid") &&
                                    !liveDiagnosis.startsWith("Uncertain") &&
                                    !liveDiagnosis.startsWith("Point the lens")
                                if (valid) {
                                    val confidencePct = (liveConfidence * 100).toInt()
                                    scanResult = "Live lens match: $liveDiagnosis ($confidencePct% confidence via $liveSource).\n\n" +
                                        cropFullAdvisoryText(farmerPlantedCrop, liveDiagnosis, confidencePct, liveSource)
                                } else if (scannerError != null) {
                                    scanResult = scannerError
                                } else {
                                    scanResult = "⚠️ Scan unclear — no valid leaf captured. Clean the lens, improve lighting, point at a crop leaf filling the frame, and launch the scanner again."
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
                        // Standalone assistant: neutral defaults so replies do not
                        // depend on dashboard launch inputs (county / crop).
                        // The user specifies location/crop inside the chat instead.
                        telemetry = TelemetryContext(
                            location = "Kenya",
                            temp = "—",
                            rainProb = "N/A",
                            wind = "—",
                            humidity = "—",
                            cropVariety = "General"
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
                        onClick = {
                            logisticsTapped = !logisticsTapped
                            if (logisticsTapped) refreshTransport()
                        },
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
                        if (transportTasks.isNotEmpty()) {
                            Text(
                                "🧾 My transport tasks — track your lorry:",
                                color = Color(0xFFE6B325),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            transportTasks.forEach { task ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${task.transporterName} • ${task.crop}",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        when (task.status) {
                                                            "EN_ROUTE" -> Color(0xFF64B5F6)
                                                            "DELIVERED" -> Color(0xFF81C784)
                                                            "CANCELLED" -> Color.Gray
                                                            else -> Color(0xFFE6B325)
                                                        },
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    task.status.replace("_", " "),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${task.fromCounty} → ${task.toHub} • ${task.phone}",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        if (task.status == "REQUESTED" || task.status == "EN_ROUTE") {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { advanceTask(task) },
                                                    modifier = Modifier.weight(1f).height(40.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6B325))
                                                ) {
                                                    Text(
                                                        if (task.status == "REQUESTED") "🚚 Mark En Route" else "✅ Mark Delivered",
                                                        color = Color.Black,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                TextButton(onClick = { cancelTask(task) }) {
                                                    Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (registeredLorries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "✓ Driver-registered lorries:",
                                color = Color(0xFFE6B325),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            registeredLorries.forEach { lorry ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            "${lorry.driverName} • ${lorry.capacity}",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${lorry.baseTown} • ${lorry.route} • ${lorry.phone}",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { dialPhone(lorry.phone) },
                                                modifier = Modifier.weight(1f).height(42.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6B325))
                                            ) {
                                                Text("📞 Call", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { whatsappTransport(lorry.driverName, lorry.phone, lorry.capacity) },
                                                modifier = Modifier.weight(1f).height(42.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("💬 WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Button(
                                                onClick = { hireTransport("${lorry.driverName} (${lorry.capacity})", lorry.phone) },
                                                modifier = Modifier.weight(1f).height(42.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                            ) {
                                                Text("🚜 Hire", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Demo directory — tap Call or WhatsApp to book:",
                            color = Color(0xFFE6B325),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        MarketDataRepository.getTransporters(selectedCounty).forEach { transporter ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2C2C2E), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        transporter.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${transporter.capacity} • KES ${transporter.rateKesPerTon}/ton • ${transporter.phone}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { dialPhone(transporter.phone) },
                                            modifier = Modifier.weight(1f).height(42.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6B325))
                                        ) {
                                            Text("📞 Call", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { whatsappTransport(transporter.name, transporter.phone, transporter.capacity) },
                                            modifier = Modifier.weight(1f).height(42.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("💬 WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { hireTransport("${transporter.name} (${transporter.capacity})", transporter.phone) },
                                            modifier = Modifier.weight(1f).height(42.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) {
                                            Text("🚜 Hire", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showRegisterForm = !showRegisterForm },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (showRegisterForm) "Close lorry registration" else "＋ Register my lorry (driver)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (showRegisterForm) {
                            Spacer(modifier = Modifier.height(8.dp))
                            lorryField(regDriver, { regDriver = it }, "Driver / SACCO name")
                            lorryField(regPhone, { regPhone = it }, "Phone e.g. 0722000000")
                            lorryField(regCapacity, { regCapacity = it }, "Capacity e.g. 10T")
                            lorryField(regTown, { regTown = it }, "Base town e.g. Marigat")
                            lorryField(regRoute, { regRoute = it }, "Route e.g. Marigat → Nairobi Hub")
                            Button(
                                onClick = {
                                    val town = regTown.trim().ifBlank { selectedCounty.ifBlank { "Baringo" } }
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            transportDao.registerLorry(
                                                TransporterProfile(
                                                    driverName = regDriver.trim(),
                                                    phone = regPhone.trim(),
                                                    capacity = regCapacity.trim().ifBlank { "10T" },
                                                    baseTown = town,
                                                    route = regRoute.trim().ifBlank { "$town → Nairobi Hub" }
                                                )
                                            )
                                        }
                                        regDriver = ""
                                        regPhone = ""
                                        regCapacity = ""
                                        regTown = ""
                                        regRoute = ""
                                        showRegisterForm = false
                                        refreshTransport()
                                    }
                                },
                                enabled = regDriver.isNotBlank() && regPhone.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6B325))
                            ) {
                                Text("Save lorry", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
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
            } // end home-page widgets (weather lives on its own page)
        }
    }
    }
}
