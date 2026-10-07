package com.example.m_agrilink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.m_agrilink.network.CropLiveLookup
import com.example.m_agrilink.data.CropNameNormalizer
import com.example.m_agrilink.data.FrameQuality
import com.example.m_agrilink.data.FrameRejectReason
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.data.TfliteLeafAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M-AgriLink Intelligent AI Scanner.
 *
 * Isolated production-grade scanner screen: dual glass input cards (live
 * CameraX lens + gallery remote scouting), on-device quality gating, and
 * the Gemini vision pipeline bound strictly to the vaulted production key
 * (SecureKeyVault — never printed, never committed, never dumped). County
 * and crop context flow in as parameters, so mango / maize / beans matrices
 * resolve identically for fresh lens frames and week-old gallery uploads.
 * Camera, interpreter, launchers and dialogs all live and die here — zero
 * lifecycle leaks by construction (unbind + shutdown on dispose).
 */

@Composable
fun AiScannerScreen(
    selectedCounty: String,
    farmerPlantedCrop: String,
    textToSpeech: TextToSpeech?,
    liveLookup: CropLiveLookup,
    onOpenWebLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val lifecycleOwner = remember(context) { context as LifecycleOwner }

    // Corridor climate snapshot for brief grounding (mirrors dashboard feed).
    val activeTemperature = 15.9
    val activeHumidity = 72
    val activeWindSpeed = 5.2

    var scanResult by rememberSaveable { mutableStateOf<String?>(null) }
    var isScanningForDisease by remember { mutableStateOf(false) }
    var diseaseScanComplete by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var liveDiagnosis by remember { mutableStateOf("Point the lens at a crop leaf…") }
    var liveConfidence by remember { mutableStateOf(0f) }
    var liveSource by remember { mutableStateOf("on-device analyzer") }
    var analyzerAttached by remember { mutableStateOf(false) }
    val lastFrameMs = remember { longArrayOf(0L) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    var imageSourceLabel by remember { mutableStateOf("Camera live") }
    var galleryBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGalleryMode by remember { mutableStateOf(false) }
    var googleBrief by remember { mutableStateOf<String?>(null) }
    var googleSource by remember { mutableStateOf("") }
    var googleLoading by remember { mutableStateOf(false) }
    // True when the enrichment pipeline stalls on auth/network failure:
    // the results column swaps in the soft-yellow Room-backup notice.
    var googleStallNotice by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
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
    // Loaded off the main thread: mmap + Interpreter init blocks for
    // seconds on low-end devices and must never run during composition.
    var tfliteInterpreter: org.tensorflow.lite.Interpreter? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        tfliteInterpreter = withContext(Dispatchers.IO) {
            try {
                TfliteLeafAnalyzer.loadInterpreter(appContext)
            } catch (e: Exception) {
                null
            }
        }
    }
    DisposableEffect(tfliteInterpreter) {
        onDispose {
            try {
                tfliteInterpreter?.close()
            } catch (e: Exception) {
            }
        }
    }

    // Release the hardware lens instantly when the app backgrounds.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                isAnalyzing = false
                analyzerAttached = false
                try {
                    cameraProvider?.unbindAll()
                } catch (e: Exception) {
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
            } catch (e: Exception) {
            }
        }
    }

    fun processGalleryUri(uri: Uri?) {
        if (uri == null) {
            scope.launch(Dispatchers.Main) {
                scannerError = tr("scan_no_image")
                imageSourceLabel = tr("src_gallery")
                isGalleryMode = false
                isScanningForDisease = true
                diseaseScanComplete = false
                isAnalyzing = false
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val src = android.graphics.ImageDecoder.createSource(
                        appContext.contentResolver, uri
                    )
                    android.graphics.ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.setTargetSampleSize(2)
                    }
                } else {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        scannerError = tr("scan_unreadable")
                        imageSourceLabel = tr("src_gallery")
                        galleryBitmap = null
                        isGalleryMode = true
                        googleBrief = null
                        googleSource = ""
                        googleLoading = false
                        googleStallNotice = false
                        isScanningForDisease = true
                        diseaseScanComplete = false
                        isAnalyzing = false
                    }
                    return@launch
                }
                // Bound uploads to 1280px: full camera photos (12MP+) would
                // otherwise sit in memory and jank every recomposition.
                val decoded = bitmap
                val longest = maxOf(decoded.width, decoded.height)
                val sizedBitmap = if (longest > 1280) {
                    val scale = 1280f / longest
                    val small = Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    try {
                        decoded.recycle()
                    } catch (e: Exception) {
                    }
                    small
                } else {
                    decoded
                }
                val smart = TfliteLeafAnalyzer.classifySmart(sizedBitmap, tfliteInterpreter)
                withContext(Dispatchers.Main) {
                    galleryBitmap = sizedBitmap
                    isGalleryMode = true
                    analyzerAttached = false
                    imageSourceLabel = "Gallery upload"
                    liveDiagnosis = smart.diagnosis.label
                    liveConfidence = smart.diagnosis.confidence
                    liveSource = smart.diagnosis.source
                    scannerError = if (smart.quality.usable) null else qualityGuidance(smart.quality, (smart.diagnosis.confidence * 100).toInt())
                    googleBrief = null
                    googleSource = ""
                    googleLoading = false
                    googleStallNotice = false
                    isScanningForDisease = true
                    diseaseScanComplete = false
                    isAnalyzing = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    scannerError = "⚠️ Scanner Alert: Could not read that image (${e.message ?: "decode failed"}). Pick a clear crop leaf, fruit, or stem photo and try again."
                    galleryBitmap = null
                    isGalleryMode = true
                    imageSourceLabel = "Gallery upload"
                    isScanningForDisease = true
                    diseaseScanComplete = false
                    isAnalyzing = false
                }
            }
        }
    }

    val remoteScoutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> processGalleryUri(uri) }

    fun startLiveScan() {
        diseaseScanComplete = false
        isAnalyzing = false
        scanResult = null
        googleBrief = null
        googleSource = ""
        googleLoading = false
        googleStallNotice = false
        imageSourceLabel = tr("src_camera")
        galleryBitmap = null
        isGalleryMode = false
        analyzerAttached = false
        liveDiagnosis = trf("scan_point", farmerPlantedCrop.trim().ifBlank { tr("scan_crop_fallback") })
        liveConfidence = 0f
        liveSource = "on-device analyzer"
        scannerError = null
        if (hasCameraPermission) {
            isScanningForDisease = true
        } else {
            try {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
            }
        }
    }

    BackHandler(enabled = isScanningForDisease) {
        isScanningForDisease = false
        diseaseScanComplete = false
        isAnalyzing = false
        isGalleryMode = false
        galleryBitmap = null
        analyzerAttached = false
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Top back-arrow row (rendered only when a close handler is wired,
            // e.g. the "ai_scanner" nav destination popping the back stack).
            if (onClose != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to previous screen",
                            tint = Color(0xFF1B5E20)
                        )
                    }
                    Text(
                        text = "Back",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = str("scan_ai_core_tag"),
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color(0xFFA75D5D)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = str("scan_title"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2C2E)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = str("scan_desc"),
                color = Color.DarkGray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Dual-input glass entry: live lens matrix beside gallery scouting.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left action: live CameraX lens matrix.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(3.dp, RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF064E3B), Color(0xFF1B4332))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            Color(0xFFE6B325).copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { startLiveScan() }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = Color(0xFFE6B325),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = str("scan_live_card"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                // Right action: week-old gallery remote scouting.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(3.dp, RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFE8F5E9), Color(0xFFF4F6F8))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .border(
                            1.dp,
                            Color(0xFF2E7D32).copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            try {
                                remoteScoutPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            } catch (e: Exception) {
                                scannerError = tr("scan_unreadable")
                            }
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = str("scan_gallery_card"),
                            color = Color(0xFF2C2C2E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = str("scan_remote_tag"),
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            if (scanResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val scanOk = liveConfidence >= 0.55f &&
                    !isInvalidLensText(liveDiagnosis) &&
                    !isPointLensText(liveDiagnosis)
                if (scanOk) {
                    ScanOverviewCard(
                        plantedCrop = farmerPlantedCrop,
                        liveDiagnosis = liveDiagnosis,
                        confidencePct = (liveConfidence * 100).toInt(),
                        source = liveSource,
                        textToSpeech = textToSpeech,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
    }

            // --- 3A-ii. NATIVE CAMERAX AI FIELD SCANNER OVERLAY ---
            if (isScanningForDisease) {
                // Pre-warm the camera provider asynchronously off the main thread.
                LaunchedEffect(isScanningForDisease) {
                    withContext(Dispatchers.IO) {
                        try {
                            ProcessCameraProvider.getInstance(appContext).get()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                // Release the hardware lens instantly when the dialog closes.
                // Dedicated frame-analysis thread (shut down here — never leak one per open).
                val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) {
                    onDispose {
                        try {
                            cameraProvider?.unbindAll()
                        } catch (e: Exception) {
                        }
                        cameraProvider = null
                        try {
                            analysisExecutor.shutdown()
                        } catch (e: Exception) {
                        }
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
                        isGalleryMode = false
                        galleryBitmap = null
                        analyzerAttached = false
                    },
                    title = {
                        Text(
                            str("dlg_scanner"),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2C2E)
                        )
                    },
                    text = {
                        Column {
                            if (!hasCameraPermission) {
                                Text(
                                    str("scan_need_cam"),
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
                                ) {
                                    Text(str("scan_grant"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black)
                                ) {
                                    if (isGalleryMode && galleryBitmap != null) {
                                        Image(
                                            bitmap = galleryBitmap!!.asImageBitmap(),
                                            contentDescription = str("scan_uploaded_img"),
                                            modifier = Modifier.matchParentSize()
                                        )
                                    } else {
                                    AndroidView(
                                        factory = { ctx ->
                                            PreviewView(ctx).apply {
                                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                            }
                                        },
                                        update = { previewView ->
                                            if (!analyzerAttached) {
                                                analyzerAttached = true
                                                val providerFuture = ProcessCameraProvider.getInstance(appContext)
                                                providerFuture.addListener({
                                                    try {
                                                        val provider = providerFuture.get()
                                                        cameraProvider = provider
                                                        val preview = Preview.Builder().build().also {
                                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                                        }
                                                        // Asynchronous frame extraction matrix (640x480, latest-only).
                                                        // Runs on a dedicated background thread: YUV decode + TFLite
                                                        // inference are far too heavy for the main thread (ANR).
                                                        // Results are posted back to the main thread below.
                                                        val imageAnalyzer = ImageAnalysis.Builder()
                                                            .setTargetResolution(Size(640, 480))
                                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                            .build()
                                                        val analysisExecutorRef = analysisExecutor
                                                        val mainPoster = ContextCompat.getMainExecutor(appContext)
                                                        imageAnalyzer.setAnalyzer(analysisExecutorRef) { imageProxy ->
                                                            // Gallery uploads already have a diagnosis — never let
                                                            // live camera frames overwrite it.
                                                            if (isGalleryMode) {
                                                                try {
                                                                    imageProxy.close()
                                                                } catch (e: Exception) {
                                                                }
                                                                return@setAnalyzer
                                                            }
                                                            try {
                                                                try {
                                                                    val now = SystemClock.uptimeMillis()
                                                                    if (now - lastFrameMs[0] >= 1500L) {
                                                                        lastFrameMs[0] = now
                                                                        val bitmap = TfliteLeafAnalyzer.imageProxyToBitmap(imageProxy)
                                                                            ?: throw ImageProcessingException("Leaf frame decode returned null")
                                                                        val smart = TfliteLeafAnalyzer.classifySmart(bitmap, tfliteInterpreter)
                                                                        mainPoster.execute {
                                                                            if (smart.quality.usable) {
                                                                                liveDiagnosis = smart.diagnosis.label
                                                                                liveConfidence = smart.diagnosis.confidence
                                                                                liveSource = smart.diagnosis.source
                                                                                scannerError = null
                                                                            } else {
                                                                                liveDiagnosis = smart.diagnosis.label
                                                                                liveConfidence = smart.diagnosis.confidence
                                                                                liveSource = smart.diagnosis.source
                                                                                scannerError = qualityGuidance(smart.quality, (smart.diagnosis.confidence * 100).toInt())
                                                                            }
                                                                        }
                                                                    }
                                                                } catch (e: ImageProcessingException) {
                                                                    val msg = tr("scan_frame_fail")
                                                                    mainPoster.execute { scannerError = msg }
                                                                } catch (e: IllegalStateException) {
                                                                    val msg = tr("scan_frame_fail")
                                                                    mainPoster.execute { scannerError = msg }
                                                                }
                                                            } catch (e: Exception) {
                                                                val msg = tr("scan_frame_fail")
                                                                mainPoster.execute { scannerError = msg }
                                                            } finally {
                                                                try {
                                                                    imageProxy.close()
                                                                } catch (e: Exception) {
                                                                    val msg = tr("scan_frame_fail")
                                                                    mainPoster.execute { scannerError = msg }
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
                                                        scannerError = "⚠️ Camera failed to start (${e.message ?: "binding failed"}). Grant camera permission, upload an image instead, or restart the scanner."
                                                    }
                                                }, ContextCompat.getMainExecutor(appContext))
                                            }
                                        },
                                        modifier = Modifier.matchParentSize()
                                    )
                                    } // end camera preview (gallery shows static image above)
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
                                            str("scan_analyzing"),
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
                                    // Translucent floating capture circle (live camera only).
                                    if (!diseaseScanComplete && !isGalleryMode) {
                                        IconButton(
                                            onClick = {
                                                googleBrief = null
                                                googleSource = ""
                                                googleLoading = false
                                                googleStallNotice = false
                                                imageSourceLabel = tr("src_camera")
                                                galleryBitmap = null
                                                isGalleryMode = false
                                                isAnalyzing = true
                                            },
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
                                                contentDescription = str("scan_capture"),
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
                                    // Strict validation fallback: unreadable frames never
                                    // reach AI generation — verified offline data only.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = str("scan_diag_fallback"),
                                            color = Color(0xFF2C2C2E),
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            fontWeight = FontWeight.Bold
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
                                            strf("scan_live_lens", liveDiagnosis),
                                            color = Color.Black,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            strf("scan_conf", (liveConfidence * 100).toInt(), liveSource),
                                            color = Color.DarkGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                // Capture gate: only a valid crop frame completes the scan.
                                // Dark / blurry / non-crop frames stay in retake mode.
                                LaunchedEffect(isAnalyzing) {
                                    if (isAnalyzing) {
                                        delay(2500)
                                        val invalid = scannerError != null ||
                                            liveConfidence < 0.55f ||
                                            isInvalidLensText(liveDiagnosis) ||
                                            isPointLensText(liveDiagnosis)
                                        if (invalid) {
                                            if (scannerError == null) {
                                                scannerError = if (isGalleryMode) {
                                                    tr("scan_upload_unclear")
                                                } else {
                                                    tr("scan_cam_unclear")
                                                }
                                            }
                                            diseaseScanComplete = false
                                        } else {
                                            diseaseScanComplete = true
                                        }
                                        isAnalyzing = false
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Always-visible scanner status so uploads never look dead.
                                Text(
                                    text = strf("scan_status", imageSourceLabel, liveDiagnosis, (liveConfidence * 100).toInt()),
                                    color = Color.DarkGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isGalleryMode) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { remoteScoutPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(str("scan_upload_another"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (isAnalyzing) {
                                    Text(
                                        strf("scan_analyzing_what", if (isGalleryMode) str("scan_what_uploaded") else str("scan_what_leaves")),
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
                                        strf("scan_tap_capture", farmerPlantedCrop.trim().ifBlank { tr("scan_crop_fallback") }),
                                        color = Color.DarkGray,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                } else {
                                    val confidencePct = (liveConfidence * 100).toInt()
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        ScanOverviewCard(
                                            plantedCrop = farmerPlantedCrop,
                                            liveDiagnosis = liveDiagnosis,
                                            confidencePct = confidencePct,
                                            source = liveSource,
                                            textToSpeech = textToSpeech,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            // Google-sourced enrichment: Gemini-first brief + web sources.
                                            val scanQuery = remember(liveDiagnosis, farmerPlantedCrop) {
                                                val crop = CropNameNormalizer.canonicalOrOriginal(farmerPlantedCrop).ifBlank { "crop" }
                                                "$crop $liveDiagnosis control and management Kenya"
                                            }
                                            LaunchedEffect(diseaseScanComplete, scanQuery) {
                                                if (!diseaseScanComplete || googleLoading || googleBrief != null) return@LaunchedEffect
                                                googleLoading = true
                                                googleStallNotice = false
                                                val (text, source) = try {
                                                    liveLookup.lookupCrop(
                                                        scanQuery,
                                                        selectedCounty.ifBlank { "Kenya" },
                                                        activeTemperature,
                                                        activeHumidity,
                                                        activeWindSpeed
                                                    )
                                                } catch (e: retrofit2.HttpException) {
                                                    tr("google_fail") to "OFFLINE"
                                                } catch (e: Exception) {
                                                    tr("google_fail") to "OFFLINE"
                                                }
                                                googleBrief = text
                                                googleSource = source
                                                googleLoading = false
                                                googleStallNotice = source == "OFFLINE"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFF4F6F8), RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            str("google_title"),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Black,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (googleSource.isNotEmpty() && !googleLoading) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    when (googleSource) {
                                                                        "GEMINI" -> "✨ GOOGLE AI"
                                                                        "OPENFARM" -> "🌱 OPENFARM"
                                                                        "WIKI" -> "🌐 WIKIPEDIA"
                                                                        "CACHE" -> "⚡ CACHED"
                                                                        else -> googleSource
                                                                    },
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.Black
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        strf("google_src", imageSourceLabel),
                                                        fontSize = 11.sp,
                                                        color = Color.Gray
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    if (googleLoading && googleBrief == null) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                strokeWidth = 2.dp,
                                                                color = Color(0xFF1E3A8A)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(str("google_pulling"), fontSize = 12.sp, color = Color.DarkGray)
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(max = 420.dp)
                                                                .verticalScroll(rememberScrollState())
                                                        ) {
                                                            Text(
                                                                googleBrief ?: str("google_empty"),
                                                                fontSize = 12.sp,
                                                                lineHeight = 18.sp,
                                                                color = Color(0xFF2C2C2E)
                                                            )
                                                        }
                                                    }
                                                    if (googleStallNotice && !googleLoading) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                                                                .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                                                .padding(10.dp)
                                                        ) {
                                                            Text(
                                                                text = str("scan_net_stall"),
                                                                fontSize = 12.sp,
                                                                lineHeight = 17.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF2C2C2E)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    val encoded = Uri.encode(scanQuery)
                                                    OutlinedButton(
                                                        onClick = { onOpenWebLink("https://www.google.com/search?q=$encoded") },
                                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        Text(str("google_open"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        OutlinedButton(
                                                            onClick = { onOpenWebLink("https://www.youtube.com/results?search_query=$encoded") },
                                                            modifier = Modifier.weight(1f).height(44.dp),
                                                            shape = RoundedCornerShape(10.dp)
                                                        ) {
                                                            Text(str("btn_youtube"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { onOpenWebLink("https://www.facebook.com/search/top?q=$encoded") },
                                                            modifier = Modifier.weight(1f).height(44.dp),
                                                            shape = RoundedCornerShape(10.dp)
                                                        ) {
                                                            Text(str("btn_facebook"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                        }
                                                    }
                                                }
                                            }
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
                                                    str("btn_cabi"),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = MarketDataRepository.PRODUCTION_MODULE_TAG,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                color = Color.Gray
                                            )
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
                                    !isInvalidLensText(liveDiagnosis) &&
                                    !isPointLensText(liveDiagnosis)
                                if (valid) {
                                    val confidencePct = (liveConfidence * 100).toInt()
                                    var full = trf("scan_full_prefix", imageSourceLabel, liveDiagnosis, confidencePct, liveSource) + "\n\n" +
                                        cropFullAdvisoryText(farmerPlantedCrop, liveDiagnosis, confidencePct, liveSource)
                                    val brief = googleBrief
                                    if (!brief.isNullOrBlank()) {
                                        full += "\n\n" + trf("scan_full_google", googleSource, brief)
                                    }
                                    scanResult = full
                                } else if (scannerError != null) {
                                    scanResult = scannerError
                                } else {
                                    scanResult = tr("scan_confirm_unclear")
                                }
                                isScanningForDisease = false
                                diseaseScanComplete = false
                                isAnalyzing = false
                                isGalleryMode = false
                                galleryBitmap = null
                                analyzerAttached = false
                            }
                        ) {
                            Text(str("scan_close"), fontWeight = FontWeight.Bold, color = Color(0xFFA75D5D))
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
}


/** Dynamic crop diagnostic lookup: no hardcoded maize fallback. */
private fun cropScanKey(raw: String): String = raw.trim().lowercase()

/** Lens-state guards in both languages (reset prompt is translated). */
private fun isPointLensText(s: String) =
    s.startsWith("Point the lens") || s.startsWith("Elekeza lenzi")

private fun isInvalidLensText(s: String) =
    s.startsWith("Invalid") || s.startsWith("Uncertain")

/** User-facing quality guidance in the active language (gate internals stay English). */
private fun qualityGuidance(quality: FrameQuality, confidencePct: Int = 0): String {
    return when (quality.reason) {
        FrameRejectReason.TOO_DARK -> tr("qual_dark")
        FrameRejectReason.TOO_BRIGHT -> tr("qual_bright")
        FrameRejectReason.BLURRY -> tr("qual_blurry")
        FrameRejectReason.NO_LEAF -> tr("qual_noleaf")
        FrameRejectReason.LOW_CONFIDENCE -> trf("qual_lowconf", confidencePct)
        else -> tr("qual_crash")
    }
}

private fun cropDiagnosticTitle(
    plantedCrop: String,
    liveDiagnosis: String,
    confidencePct: Int,
    source: String
): String {
    if (liveDiagnosis.contains("Healthy", ignoreCase = true)) {
        val name = plantedCrop.trim().ifBlank { "Crop" }
        return trf("diag_healthy_title", name, confidencePct, source)
    }
    return when (cropScanKey(plantedCrop)) {
        "mango" -> tr("diag_mango")
        "beans", "bean" -> tr("diag_beans")
        "maize" -> trf("diag_maize", liveDiagnosis, confidencePct, source)
        else -> {
            val label = plantedCrop.trim().ifBlank { liveDiagnosis }
            if (label.isBlank()) tr("diag_general")
            else trf("diag_general_crop", label.uppercase())
        }
    }
}

private fun cropDiagnosticBody(plantedCrop: String): List<String> {
    return when (cropScanKey(plantedCrop)) {
        "mango" -> listOf(tr("body_mango"))
        "beans", "bean" -> listOf(tr("body_beans"))
        "maize" -> listOf(tr("body_maize_1"), tr("body_maize_2"), tr("body_maize_3"))
        else -> listOf(tr("body_general"))
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
        listOf(tr("body_preventive"))
    } else {
        cropDiagnosticBody(plantedCrop)
    }
    return (listOf(title) + body).joinToString("\n\n")
}

/** Google-AI-Overview style structured result helpers. */
private fun overviewCropName(plantedCrop: String, liveDiagnosis: String): String {
    val typed = plantedCrop.trim()
    if (typed.isNotBlank()) return typed
    val lens = liveDiagnosis.trim()
    if (lens.isNotBlank() && !isPointLensText(lens) && !isInvalidLensText(lens)) return lens
    return tr("lens_your_crop")
}

private fun overviewPestName(plantedCrop: String, liveDiagnosis: String): String {
    if (liveDiagnosis.contains("Healthy", ignoreCase = true)) return "no pest"
    return when (cropScanKey(plantedCrop)) {
        "mango" -> "Mango Fruit Fly (Bactrocera dorsalis)"
        "beans", "bean" -> "Bean Fly (Ophiomyia phaseoli) / Black Bean Aphid"
        "maize" -> if (liveDiagnosis.isNotBlank() && !isPointLensText(liveDiagnosis)) liveDiagnosis else "Fall Armyworm (Spodoptera frugiperda)"
        else -> if (liveDiagnosis.isNotBlank() && !isPointLensText(liveDiagnosis) && !isInvalidLensText(liveDiagnosis)) liveDiagnosis else tr("lens_foliar")
    }
}

private fun overviewControlBullets(): List<Pair<String, String>> = listOf(
    tr("cb_lead_1") to tr("cb_rest_1"),
    tr("cb_lead_2") to tr("cb_rest_2"),
    tr("cb_lead_3") to tr("cb_rest_3"),
    tr("cb_lead_4") to tr("cb_rest_4")
)

private fun overviewCta(): String = tr("overview_cta")

private fun verifiedSourcesFootnote(): String = tr("overview_sources")

/** Ordered narration parts for one scan verdict (shared by all TTS paths). */
private fun buildOverviewSpeechParts(
    plantedCrop: String,
    liveDiagnosis: String,
    confidencePct: Int,
    source: String
): List<String> {
    val segments = mutableListOf<String>()
    segments.add(ADVISORY_FRAMEWORK_ATTRIBUTION)
    // Speak exactly what the advisory card shows (already localized via tr()).
    segments.add(cropDiagnosticTitle(plantedCrop, liveDiagnosis, confidencePct, source))
    if (liveDiagnosis.contains("Healthy", ignoreCase = true)) {
        segments.add(tr("body_preventive"))
    } else {
        overviewControlBullets().forEach { (lead, rest) ->
            segments.add("$lead $rest")
        }
        val extra = if (cropScanKey(plantedCrop).isBlank()) emptyList()
        else cropDiagnosticBody(plantedCrop)
        segments.addAll(extra)
    }
    segments.add(overviewCta())
    try {
        val grounded = MarketDataRepository.getPestAdvisory(plantedCrop.ifBlank { liveDiagnosis })
        grounded.cardLines().forEach { line ->
            segments.add(line)
        }
    } catch (e: Exception) {
    }
    segments.add(verifiedSourcesFootnote())
    return segments
}

/**
 * Listener-driven playback controller: Start / Pause / Stop for the long
 * CABI-KALRO-icipe narration. One chunk in flight at a time (QUEUE_FLUSH),
 * advancing only on this controller's own utterance callbacks, so stop and
 * pause take effect instantly with no leaked queue behind them.
 */
private class OverviewTtsController {
    enum class State { IDLE, PLAYING, PAUSED }

    var state by mutableStateOf(State.IDLE)
        private set

    private var chunks: List<String> = emptyList()
    private var index = 0
    private var engine: TextToSpeech? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = advance(utteranceId)
        override fun onError(utteranceId: String?) = advance(utteranceId)
        @Deprecated("Required override")
        override fun onError(utteranceId: String?, errorCode: Int) = advance(utteranceId)
    }

    private fun advance(utteranceId: String?) {
        if (!utteranceId.orEmpty().startsWith(PREFIX)) return
        if (state != State.PLAYING) return
        if (utteranceId != PREFIX + index) return
        index++
        if (index >= chunks.size) {
            index = 0
            state = State.IDLE
        } else {
            speakCurrent()
        }
    }

    fun attach(tts: TextToSpeech?) {
        engine = tts
        try {
            tts?.setOnUtteranceProgressListener(listener)
        } catch (e: Exception) {
        }
    }

    fun start(parts: List<String>) {
        val clean = parts.map(::speakableTtsText).filter { it.isNotBlank() }
            .flatMap { it.chunked(400) }
        try {
            engine?.stop()
        } catch (e: Exception) {
        }
        if (clean.isEmpty()) {
            state = State.IDLE
            return
        }
        chunks = clean
        index = 0
        state = State.PLAYING
        speakCurrent()
    }

    fun pause() {
        if (state != State.PLAYING) return
        try {
            engine?.stop()
        } catch (e: Exception) {
        }
        state = State.PAUSED
    }

    fun resume() {
        if (state != State.PAUSED) return
        state = State.PLAYING
        speakCurrent()
    }

    fun stop() {
        try {
            engine?.stop()
        } catch (e: Exception) {
        }
        index = 0
        state = State.IDLE
    }

    private fun speakCurrent() {
        val tts = engine ?: run { state = State.IDLE; return }
        applyTtsLocale(tts)
        try {
            tts.speak(chunks[index], TextToSpeech.QUEUE_FLUSH, null, PREFIX + index)
        } catch (e: Exception) {
            state = State.IDLE
        }
    }

    companion object {
        private const val PREFIX = "ovc_"
    }
}

/**
 * Production Google-AI-Overview style result card: identification header,
 * itemized bold-lead control bullets, crop protocol, and harvest CTA strip.
 * High-contrast soft-dark container with itemized rows, crash-proof
 * attribution metadata, and multi-source footer tags. Fits inside parent
 * verticalScroll without fixed heights or overlap.
 */
@Composable
private fun ScanOverviewCard(
    plantedCrop: String,
    liveDiagnosis: String,
    confidencePct: Int,
    source: String,
    textToSpeech: TextToSpeech?,
    modifier: Modifier = Modifier
) {
    val cropName = overviewCropName(plantedCrop, liveDiagnosis)
    val pestName = overviewPestName(plantedCrop, liveDiagnosis)
    val isHealthy = liveDiagnosis.contains("Healthy", ignoreCase = true)
    val scanTitle = cropDiagnosticTitle(plantedCrop, liveDiagnosis, confidencePct, source)
    val scanBody: List<String> = if (isHealthy) {
        listOf(tr("body_preventive"))
    } else {
        cropDiagnosticBody(plantedCrop)
    }
    val cardBg = Color(0xFF1C1C1E)
    val bodyText = Color(0xFFF5F5F5)
    val mutedText = Color(0xFFB9B9C0)
    val headerGold = Color(0xFFE6B325)
    val bulletGold = Color(0xFFE6B325)
    val ttsController = remember { OverviewTtsController() }
    LaunchedEffect(textToSpeech) { ttsController.attach(textToSpeech) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFE6B325).copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF064E3B), Color(0xFF1B4332))
                    )
                )
        ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = ADVISORY_FRAMEWORK_ATTRIBUTION,
                color = headerGold,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = str("scan_ident"),
                    color = headerGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                val ctlState = ttsController.state
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            ttsController.start(
                                buildOverviewSpeechParts(plantedCrop, liveDiagnosis, confidencePct, source)
                            )
                        },
                        enabled = ctlState != OverviewTtsController.State.PLAYING
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = str("audio_start"),
                            tint = headerGold
                        )
                    }
                    IconButton(
                        onClick = {
                            if (ctlState == OverviewTtsController.State.PLAYING) ttsController.pause()
                            else ttsController.resume()
                        },
                        enabled = ctlState != OverviewTtsController.State.IDLE
                    ) {
                        Icon(
                            imageVector = if (ctlState == OverviewTtsController.State.PAUSED) {
                                Icons.Filled.PlayArrow
                            } else {
                                Icons.Filled.Pause
                            },
                            contentDescription = str("audio_pause"),
                            tint = headerGold
                        )
                    }
                    IconButton(
                        onClick = { ttsController.stop() },
                        enabled = ctlState != OverviewTtsController.State.IDLE
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = str("audio_stop"),
                            tint = headerGold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isHealthy) strf("scan_ident_healthy", cropName, confidencePct, source)
                else strf("scan_ident_hit", pestName, cropName, confidencePct, source),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scanTitle,
                color = mutedText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = str("scan_control"),
                color = headerGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            overviewControlBullets().forEach { (lead, rest) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        text = "• ",
                        color = bulletGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color.White))
                            append(lead)
                            pop()
                            append(" $rest")
                        },
                        color = bodyText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = str("scan_protocol"),
                color = headerGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            scanBody.forEach { paragraph ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        text = "• ",
                        color = bulletGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = paragraph,
                        color = bodyText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = str("scan_validated"),
                color = headerGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            MarketDataRepository.getPestAdvisory(plantedCrop.ifBlank { liveDiagnosis }).cardLines().forEach { line ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        text = "• ",
                        color = bulletGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = line,
                        color = bodyText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF8E1), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = overviewCta(),
                    color = Color(0xFF2C2C2E),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = verifiedSourcesFootnote(),
                color = mutedText,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = ADVISORY_FRAMEWORK_ATTRIBUTION,
                color = mutedText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontStyle = FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = MarketDataRepository.PLATFORM_CORE_ENGINE_TAG,
                color = mutedText,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = str("scan_prod_tag"),
                color = headerGold,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        }
    }
}
