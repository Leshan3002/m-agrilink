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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import com.example.m_agrilink.data.MarketDataRepository
import com.example.m_agrilink.data.SaccoTelemetry
import com.example.m_agrilink.data.AgronomicAdvisory
import com.example.m_agrilink.data.AppLocale
import com.example.m_agrilink.data.CropNameNormalizer
import com.example.m_agrilink.data.FrameQuality
import com.example.m_agrilink.data.FrameRejectReason
import com.example.m_agrilink.ui.components.HiveJournalComponent
import com.example.m_agrilink.data.local.CookieConsentManager
import com.example.m_agrilink.data.local.UserInterestTracker
import com.example.m_agrilink.domain.RoomFarmerAccountRepository
import com.example.m_agrilink.network.CropLiveLookup
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ARCHITECT_ATTRIBUTION =
    "M-AgriLink agricultural intelligence platform."

internal const val ADVISORY_FRAMEWORK_ATTRIBUTION =
    "M-AgriLink advisory framework."

private const val VERIFIED_SOURCES_FOOTNOTE =
    "*Verified via KALRO ASAL Research Databases, CABI Plantwise Knowledge Bank, and icipe Kenya.*"

class ImageProcessingException(message: String) : Exception(message)

/** Keyed advisory display: Kiswahili when selected, original English otherwise. */
private fun advisoryField(a: AgronomicAdvisory, field: String): String {
    if (field == "moist") return if (AppLocale.isSwahili) tr("adv_moisture") else a.moistureCeiling
    if (!AppLocale.isSwahili) return when (field) {
        "space" -> a.plantingSpacing
        "harvest" -> a.harvestNote
        else -> a.landPrep
    }
    if (a.key in setOf("maize", "beans", "onions", "sorghum")) return tr("adv_${a.key}_$field")
    if (a.key.isNotBlank()) return trf("adv_custom_$field", a.key)
    return when (field) {
        "space" -> a.plantingSpacing
        "harvest" -> a.harvestNote
        else -> a.landPrep
    }
}


/** Shared TTS locale policy: Swahili voice in Kiswahili mode, else US English. */
internal fun applyTtsLocale(tts: TextToSpeech) {
    // M-AgriLink Core Engine.
    // Voice follows the Language picker: Kiswahili mode speaks Swahili (sw-KE when
    // the engine has it, else generic Swahili), English mode speaks US English.
    try {
        val voiceLocale = if (AppLocale.isSwahili) {
            val swKe = Locale("sw", "KE")
            if (tts.isLanguageAvailable(swKe) >= TextToSpeech.LANG_AVAILABLE) swKe
            else {
                val sw = Locale("sw")
                if (tts.isLanguageAvailable(sw) >= TextToSpeech.LANG_AVAILABLE) sw else Locale.US
            }
        } else {
            Locale.US
        }
        tts.language = voiceLocale
    } catch (e: Exception) {
    }
}

/** Strip pictographs/bullets so the engine reads words, not emoji names. */
internal fun speakableTtsText(s: String): String =
    s.replace(Regex("[\\p{So}\\p{Sk}•●▲▼★☆→➔*]+"), " ").replace(Regex("\\s+"), " ").trim()





/**
 * Elite Google-AI-Overview style live KAMIS honey card.
 * Reads straight from [MarketDataRepository.getHoneyProfile] — no hardcoded
 * UI strings. Fits inside parent verticalScroll with no fixed heights.
 */
@Composable
private fun HoneyOverviewCard(
    modifier: Modifier = Modifier
) {
    val honeyLang = AppLocale.language
    val honey = remember(honeyLang) { MarketDataRepository.getHoneyProfile() }
    val cardBg = Color(0xFF1C1C1E)
    val bodyText = Color(0xFFF5F5F5)
    val mutedText = Color(0xFFB9B9C0)
    val headerGold = Color(0xFFE6B325)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = str("honey_live"),
                color = headerGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = honey.commodityName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strf("honey_base", honey.baseIndexAverage),
                color = bodyText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strf("honey_traj", honey.trajectoryVector),
                color = bodyText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = str("honey_deviations"),
                color = headerGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            honey.deviationRows().forEach { (lead, rest) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        text = "• ",
                        color = headerGold,
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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = honey.sourceFootnote,
                color = mutedText,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

private fun formatKes(amount: Int): String =
    "KES " + "%,d".format(java.util.Locale.US, amount)

private fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toInt().toString()
    else "%.1f".format(java.util.Locale.US, qty)

/**
 * PestOutbreakAlertBanner: dual-axis county × crop biosecurity matrix.
 * Mango × Tana River (CABI weevil) and Maize × Baringo (icipe FAW) flash
 * crimson; Mango × Baringo (KALRO mildew), Maize × Tana River (MoA MLND)
 * and Beans × anywhere (KEPHIS bean fly) flash amber; every other pairing
 * holds the green all-clear with the active crop and county named. A soft
 * infinite alpha pulse keeps the capsule eye-catching; vertical-only flow,
 * no fixed heights, zero overlap risk.
 */
@Composable
private fun PestOutbreakAlertBanner(
    selectedCounty: String,
    plantedCrop: String = ""
) {
    val county = selectedCounty.trim().lowercase()
    val crop = plantedCrop.trim().lowercase()
    val isTana = "tana river" in county
    val isBaringo = "baringo" in county
    val isMango = "mango" in crop
    val isMaize = "maiz" in crop || "corn" in crop
    val isBeans = "bean" in crop
    val crimson = Color(0xFF7F1D1D)
    val amber = Color(0xFF78350F)
    val fieldGreen = Color(0xFF064E3B)
    val container: Color
    val message: String
    when {
        isMango && isTana -> {
            container = crimson
            message = str("pest_mango_tana")
        }
        isMango && isBaringo -> {
            container = amber
            message = str("pest_mango_baringo")
        }
        isMaize && isBaringo -> {
            container = crimson
            message = str("pest_maize_baringo")
        }
        isMaize && isTana -> {
            container = amber
            message = str("pest_maize_tana")
        }
        isBeans -> {
            container = amber
            message = str("pest_beans_any")
        }
        else -> {
            container = fieldGreen
            val cropLabel = plantedCrop.trim().ifBlank { str("lens_your_crop") }.uppercase()
            val countyLabel = selectedCounty.trim().ifBlank { str("out_your_county") }.uppercase()
            message = strf("pest_clear_fmt", cropLabel, countyLabel)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pestAlertPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pestAlertAlpha"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(pulseAlpha)
                .background(container, CircleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = str("pest_bio_tag"),
            color = Color.Gray,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * M-AgriLink Core Engine.
 *
 * Dynamic SACCO & Input Pricing Planner: certified-input price grid plus an
 * interactive acreage calculator with instant investment projections.
 * Purely informational — structural acreage investment projections only.
 */
@Composable
private fun SaccoInputPlannerWidget(
    modifier: Modifier = Modifier,
    plantedCrop: String = ""
) {
    val saccoLang = AppLocale.language
    val cropKey = plantedCrop.trim().lowercase()
    var maizePack by rememberSaveable { mutableStateOf("10kg") }
    val appContext = LocalContext.current.applicationContext
    // Live telemetry first; on signal drop the Room cache answers; when the
    // cache is also empty the in-code 2026 baselines apply. All off-main.
    var feed by remember { mutableStateOf(MarketDataRepository.SubsidyFeed(emptyMap(), false, "")) }
    LaunchedEffect(saccoLang) {
        feed = withContext(Dispatchers.IO) { MarketDataRepository.loadSubsidyFeed(appContext) }
    }
    val matrix = remember(cropKey, saccoLang, feed, maizePack) {
        MarketDataRepository.getCropInputMatrix(
            cropKey,
            overrides = feed.prices,
            maizeSeedPack = maizePack
        )
    }
    var acreageInput by rememberSaveable { mutableStateOf("1") }
    val acres = acreageInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val projection = remember(matrix, acres) {
        matrix.lines.map { line ->
            val qty = line.unitsPerAcre * acres
            Triple(line, qty, (qty * line.unitPriceKes).toInt())
        }
    }
    val totalKes = remember(projection) { projection.sumOf { it.third } }
    Card(
        modifier = modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = str("sac_title"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C2C2E)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = str("sac_sub"),
                color = Color.DarkGray,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strf("sac_matrix_for", matrix.cropLabel),
                color = Color(0xFF1B5E20),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (!matrix.verified) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = str("sac_est_note"),
                    color = Color(0xFF8D6E00),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontStyle = FontStyle.Italic
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (matrix.id == "maize") {
                Text(
                    text = str("sac_maize_pack_label"),
                    color = Color.DarkGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = maizePack == "2kg",
                        onClick = { maizePack = "2kg" },
                        label = { Text(str("sac_pack_2kg"), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = maizePack == "10kg",
                        onClick = { maizePack = "10kg" },
                        label = { Text(str("sac_pack_10kg"), fontSize = 12.sp) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            matrix.lines.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFF4F6F8), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "• ${item.name}",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 16.sp
                            )
                            Text(item.spec, color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatKes(item.unitPriceKes),
                                color = Color(0xFF1B5E20),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            if (item.badge.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(item.badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = acreageInput,
                onValueChange = { acreageInput = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(str("sac_acre"), color = Color.Gray) },
                placeholder = { Text(str("sac_acre_hint"), color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Live responsive revenue summary: sectioned high-contrast card.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = strf("sac_proj_for", if (acreageInput.isBlank()) "0" else acreageInput),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6B325)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    projection.forEach { (line, qty, costKes) ->
                        Text(
                            text = strf("sac_line_fmt", line.name, formatQty(qty), line.unitLabel, formatKes(costKes)),
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 19.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strf("sac_total", formatKes(totalKes)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE6B325)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (feed.live) strf("sac_src_live", feed.stamp.ifBlank { "—" })
                        else str("sac_src_cached"),
                        fontSize = 11.sp,
                        color = if (feed.live) Color(0xFF81C784) else Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = str("sac_planner_tag"),
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

/** FAQ narration: reads one troubleshooting answer aloud in the active language. */
private fun speakFaqAnswer(
    textToSpeech: TextToSpeech?,
    question: String,
    answer: String
) {
    val tts = textToSpeech ?: return
    try {
        tts.stop()
    } catch (e: Exception) {
    }
    applyTtsLocale(tts)
    listOf(question, answer).map(::speakableTtsText).filter { it.isNotBlank() }.forEach { part ->
        part.chunked(400).forEach { chunk ->
            try {
                tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "faq_answer")
            } catch (e: Exception) {
            }
        }
    }
}

/**
 * Expandable Frequently Asked Questions: tap a question card to smoothly
 * reveal its answer. No fixed heights — safe inside the parent verticalScroll.
 */
@Composable
private fun FaqItem(
    question: String,
    answer: String,
    textToSpeech: TextToSpeech? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(),
        label = "faqArrow"
    )
    Card(
        onClick = { isExpanded = !isExpanded },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6B325))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = question,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C2C2E),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) str("faq_collapse") else str("faq_expand"),
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = answer,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.weight(1f)
                        )
                        if (textToSpeech != null) {
                            IconButton(
                                onClick = { speakFaqAnswer(textToSpeech, question, answer) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VolumeUp,
                                    contentDescription = str("faq_listen"),
                                    tint = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqSectionComponent(
    textToSpeech: TextToSpeech? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = str("faq_title"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C2C2E)
            )
            Spacer(modifier = Modifier.height(10.dp))
            FaqItem(question = str("faq_q1"), answer = str("faq_a1"), textToSpeech = textToSpeech)
            Spacer(modifier = Modifier.height(8.dp))
            FaqItem(question = str("faq_q2"), answer = str("faq_a2"), textToSpeech = textToSpeech)
            Spacer(modifier = Modifier.height(8.dp))
            FaqItem(question = str("faq_q3"), answer = str("faq_a3"), textToSpeech = textToSpeech)
            Spacer(modifier = Modifier.height(8.dp))
            FaqItem(question = str("faq_q4"), answer = str("faq_a4"), textToSpeech = textToSpeech)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "© 2026 M-AgriLink. All rights reserved.",
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Dedicated Market Overview page (app core: market trends & analysis).
 * Ranks every crop in the corridor with margins, verdicts, and outlook.
 */
@Composable
private fun MarketOverviewPage(
    selectedCounty: String,
    countyData: List<com.example.m_agrilink.data.CropMarketRecord>,
    contentPrimary: Color,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSelectCounty: (String) -> Unit,
    cropFilter: String,
    onCropFilterChange: (String) -> Unit,
    myCrops: List<String>,
    plantedCrop: String,
    onAddCrop: (String) -> Unit,
    onSelectCrop: (String) -> Unit
) {
    // Full dark-mode palette: every surface + text resolves per theme.
    val cardBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val cardText = if (isDarkTheme) Color.White else Color.Black
    val cardMuted = if (isDarkTheme) Color(0xFFB9B9C0) else Color.DarkGray
    val canvasMuted = if (isDarkTheme) Color(0xFFD9D9E0) else Color.DarkGray
    var priceType by rememberSaveable { mutableStateOf("Wholesale") }
    var watchlist by rememberSaveable { mutableStateOf(setOf<String>()) }
    var newMarketCropInput by rememberSaveable { mutableStateOf("") }

    // Custom farmer crop joins the board via the KAMIS offline mirror.
    val customRecord = remember(selectedCounty, plantedCrop) {
        if (plantedCrop.isBlank()) null
        else MarketDataRepository.getOrGenerateCropRecord(selectedCounty, plantedCrop)
    }
    val hasCustom = customRecord != null &&
        countyData.none { it.cropName.equals(customRecord.cropName, ignoreCase = true) }
    val displayData = remember(countyData, customRecord, hasCustom) {
        countyData + (if (hasCustom) listOf(customRecord!!) else emptyList())
    }

    val ranked = remember(displayData) { displayData.sortedByDescending { it.netMarginKes } }
    val best = remember(displayData) { MarketDataRepository.bestArbitrage(displayData) }
    val focusCrop = if (cropFilter == "All") (best?.cropName ?: plantedCrop.ifBlank { "Maize" }) else cropFilter
    val isHoneyFocus = remember(focusCrop) { MarketDataRepository.isHoneyCrop(focusCrop) }
    val focusRecord = remember(displayData, focusCrop, selectedCounty, isHoneyFocus) {
        if (isHoneyFocus) null
        else displayData.firstOrNull { it.cropName.equals(focusCrop, ignoreCase = true) }
            ?: MarketDataRepository.getOrGenerateCropRecord(selectedCounty, focusCrop)
    }
    val history = remember(selectedCounty, focusCrop, isHoneyFocus) {
        if (isHoneyFocus) emptyList() else MarketDataRepository.getPriceHistory(selectedCounty, focusCrop)
    }
    val quotes = remember(selectedCounty, focusCrop, isHoneyFocus) {
        if (isHoneyFocus) emptyList() else MarketDataRepository.getMarketQuotes(selectedCounty, focusCrop)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
        ) {
            Text(str("back_home"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        // KAMIS-style hero header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    str("mkt_hero"),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    (if (selectedCounty.isBlank()) str("mkt_hero_all") else strf("mkt_hero_county", selectedCounty)) +
                        str("mkt_hero_tail"),
                    color = Color(0xFFDCE6F5),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    MarketDataRepository.marketOutlook(selectedCounty, displayData),
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = str("mkt_county_hub"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = contentPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Baringo", "Nairobi", "Nakuru", "Uasin Gishu", "Mombasa").forEach { hub ->
                val selected = selectedCounty == hub
                Button(
                    onClick = { onSelectCounty(hub) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFFE6B325) else cardBg
                    )
                ) {
                    Text(
                        hub,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.Black else cardText
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = str("mkt_mycrops"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = contentPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newMarketCropInput,
                onValueChange = { newMarketCropInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(str("mkt_crop_hint"), color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg,
                    focusedTextColor = cardText,
                    unfocusedTextColor = cardText
                )
            )
            Button(
                onClick = {
                    val crop = newMarketCropInput.trim()
                    if (crop.isNotBlank()) {
                        newMarketCropInput = ""
                        onAddCrop(crop)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(str("mkt_add"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (myCrops.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                myCrops.forEach { saved ->
                    AssistChip(
                        onClick = { onSelectCrop(saved) },
                        label = { Text(saved, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                str("mkt_tap_saved"),
                fontSize = 11.sp,
                color = canvasMuted,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = str("mkt_filter"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = contentPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (listOf("All") + (displayData.map { it.cropName } + listOf("Honey")).distinct()).forEach { name ->
                val selected = cropFilter == name
                Button(
                    onClick = { onCropFilterChange(name) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFF1B5E20) else cardBg
                    )
                ) {
                    Text(
                        if (name == "All") str("mkt_all") else name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else cardText
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Retail / wholesale segmented toggle (KAMIS core).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Wholesale", "Retail").forEach { mode ->
                val selected = priceType == mode
                Button(
                    onClick = { priceType = mode },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color(0xFFE6B325) else cardBg
                    )
                ) {
                    Text(
                        if (mode == "Wholesale") str("price_wholesale") else str("price_retail"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.Black else cardText
                    )
                }
            }
        }
        if (watchlist.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                strf("mkt_watching", watchlist.sorted().joinToString(" • ")),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (isHoneyFocus) {
            HoneyOverviewCard(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (displayData.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Text(
                    text = str("mkt_empty"),
                    color = cardText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            if (best != null) {
                val marginPct = if (best.localPriceKes > 0) (best.netMarginKes * 100) / best.localPriceKes else 0
                if (marginPct >= 15) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = strf("alert_margin", best.cropName, marginPct),
                            color = Color(0xFF1B5E20),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF8E1), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE6B325), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = strf("best_deal", best.cropName, best.localPriceKes, best.hubPriceKes, best.netMarginKes, MarketDataRepository.marketVerdict(best)),
                        color = Color(0xFF2C2C2E),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            // Ranked corridor cards with watchlist stars.
            ranked.filter { cropFilter == "All" || it.cropName.equals(cropFilter, ignoreCase = true) }
                .forEachIndexed { index, crop ->
                    val key = "${selectedCounty.ifBlank { "Kenya" }}:${crop.cropName}"
                    val watched = watchlist.contains(key)
                    val isKamisCustom = hasCustom && crop.cropName.equals(customRecord?.cropName, ignoreCase = true)
                    val isHoneyRow = MarketDataRepository.isHoneyCrop(crop.cropName)
                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1} ${crop.cropName} (${crop.unit})" + if (isKamisCustom) "  📡 KAMIS" else "",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardText,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    watchlist = if (watched) watchlist - key else watchlist + key
                                }) {
                                    Text(if (watched) "★" else "☆", fontSize = 18.sp, color = Color(0xFFE6B325))
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isHoneyRow) Color(0xFFE8F5E9)
                                            else if (crop.netMarginKes >= 0) Color(0xFFE8F5E9) else Color(0xFFFDECEA),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isHoneyRow) str("badge_rising")
                                        else strf("net_fmt", if (crop.netMarginKes >= 0) "+" else "", crop.netMarginKes),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isHoneyRow) Color(0xFF1B5E20)
                                        else if (crop.netMarginKes >= 0) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (isHoneyRow) strf("honey_row", crop.localPriceKes, crop.hubPriceKes)
                                else strf("row_local", crop.localPriceKes, crop.hubPriceKes, crop.grossMarginKes),
                                fontSize = 12.sp,
                                color = cardMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                MarketDataRepository.marketVerdict(crop),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFF81C784) else Color(0xFF1B5E20)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (isHoneyRow) MarketDataRepository.getHoneyProfile().sourceFootnote
                                else str("row_source"),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            // KAMIS deep dive: 7-day trend bars for the focus crop.
            if (focusRecord != null && history.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            strf("trend_title", focusRecord.cropName, focusRecord.unit) + if (hasCustom && focusCrop.equals(customRecord?.cropName, ignoreCase = true)) " 📡 KAMIS" else "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            str("trend_hint"),
                            fontSize = 11.sp,
                            color = cardMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val minPrice = history.minOf { it.priceKes }
                        val maxPrice = history.maxOf { it.priceKes }
                        val span = (maxPrice - minPrice).coerceAtLeast(1).toFloat()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            history.forEach { point ->
                                val frac = (point.priceKes - minPrice) / span
                                val barH = (26 + frac * 64).dp
                                val peak = point.priceKes == maxPrice
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${point.priceKes / 1000}k",
                                        fontSize = 9.sp,
                                        color = cardMuted
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .height(barH)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (peak) Color(0xFF2E7D32) else Color(0xFFE6B325))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(point.dayLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cardText)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            strf("trend_range", history.minOf { it.priceKes }, history.maxOf { it.priceKes }),
                            fontSize = 11.sp,
                            color = cardMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            // KAMIS per-market board for the focus crop.
            if (quotes.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            strf("board_title", focusCrop, if (priceType == "Wholesale") str("price_wholesale") else str("price_retail"), focusRecord?.unit ?: "90kg Bag") + if (hasCustom && focusCrop.equals(customRecord?.cropName, ignoreCase = true)) " 📡 KAMIS" else "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            str("board_hint"),
                            fontSize = 11.sp,
                            color = cardMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        quotes.forEach { q ->
                            val price = if (priceType == "Retail") q.retailKes else q.wholesaleKes
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(q.market, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cardText)
                                    Text("${q.county} • ${q.updatedAgo}", fontSize = 11.sp, color = cardMuted)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "KES $price",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cardText
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (q.trend) {
                                                    "UP" -> Color(0xFFE8F5E9)
                                                    "DOWN" -> Color(0xFFFDECEA)
                                                    else -> if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF4F6F8)
                                                },
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            (if (q.trend == "UP") "▲ +" else if (q.trend == "DOWN") "▼ " else "● ") +
                                                "${q.changePct}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (q.trend) {
                                                "UP" -> Color(0xFF1B5E20)
                                                "DOWN" -> Color(0xFFB71C1C)
                                                else -> cardMuted
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            str("board_source"),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = MarketDataRepository.PLATFORM_CORE_ENGINE_TAG,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** Leading initials for the HUD badge ("Leshan Levi" -> "LL"). */
private fun accountInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return "${parts[0].first()}${parts[1].first()}".uppercase()
}

/**
 * Top-right Active Account Profile HUD: circular initial badge + name /
 * status stack. Tapping expands a non-transactional identity summary drawer.
 */
@Composable
private fun ActiveAccountHud(
    displayName: String,
    displayEmail: String,
    onLogout: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val safeName = displayName.ifBlank { "Leshan Levi" }
    val safeEmail = displayEmail.ifBlank { "levislekesio@gmail.com" }
    Column(horizontalAlignment = Alignment.End) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 3.dp,
            modifier = Modifier.clickable { expanded = !expanded }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                            )
                        )
                ) {
                    Text(
                        text = accountInitials(safeName),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = safeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "\uD83D\uDFE2 Active Account",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 3.dp,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 260.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Account identity",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE6B325)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = safeName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = safeEmail,
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "They stay signed in on their device and their harvest data never leaves this phone.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Non-transactional summary — no payments are handled here.",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "\uD83D\uDEAA Log Out",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tapping Log Out clears their session so they sign in again next time.",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    displayName: String = "Leshan Levi",
    displayEmail: String = "levislekesio@gmail.com",
    onLogout: () -> Unit = {},
    onOpenScanner: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var expanded by remember { mutableStateOf(false) }
    // Session states survive minimize / rotation / process death.
    var selectedCounty by rememberSaveable { mutableStateOf("") }
    // M-AgriLink Core Engine.
    // Instant county-hub search filter (survives rotation; drives the 47-county list below).
    var countySearchQuery by rememberSaveable { mutableStateOf("") }
    val countyData = remember(selectedCounty) {
        MarketDataRepository.getCountyData(selectedCounty)
    }
    val targetHubName = remember(selectedCounty) {
        MarketDataRepository.targetHubFor(selectedCounty)
    }
    var farmerPlantedCrop by rememberSaveable { mutableStateOf("") }
    var showShambaChat by rememberSaveable { mutableStateOf(false) }

    // --- TOP ACTION BAR STATE MACHINE (thread-safe Compose state) ---
    // Account creation lives only in AuthOnboardingScreen (app boot entry);
    // the dashboard keeps no duplicate account page.
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    var selectedLanguage by rememberSaveable { mutableStateOf("English") }
    // Language picker drives every Dashboard string via AppLocale.
    LaunchedEffect(selectedLanguage) { AppLocale.language = selectedLanguage }
    var activeViewport by rememberSaveable { mutableStateOf("home") }
    var navExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var cacheNotice by remember { mutableStateOf<String?>(null) }
    // Market page crop focus (deep-linked from recents / My Crops).
    var marketCropFilter by rememberSaveable { mutableStateOf("All") }
    // Cookie consent (asked on first launch, Settings anytime).
    var consentState by remember { mutableStateOf(CookieConsentManager.ConsentState()) }
    var showCookieBanner by remember { mutableStateOf(false) }
    var showCookieSettings by remember { mutableStateOf(false) }
    var tmpAnalytics by remember { mutableStateOf(false) }
    var tmpPersonalization by remember { mutableStateOf(false) }
    var tmpMarketing by remember { mutableStateOf(false) }
    val systemMetadata = remember {
        mapOf(
            "architect" to ARCHITECT_ATTRIBUTION,
            "advisoryFramework" to ADVISORY_FRAMEWORK_ATTRIBUTION,
            "platform" to MarketDataRepository.PLATFORM_CORE_ENGINE_TAG,
            "viewport" to "home",
            "engine" to "M-AgriLink offline cache"
        )
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

    // Background/resume safety: leaving the app must NOT reset the dashboard.
    // On pause: stop speech (the isolated scanner screen releases its own
    // camera). Dashboard position (viewport/county/crop/chat) is kept via
    // rememberSaveable + ViewModel.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                try {
                    textToSpeech?.stop()
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
        cropKey == "maize" && activeHumidity >= 70 -> tr("adv_maize_humid")
        cropKey == "beans" && activeWindSpeed <= 10.0 -> tr("adv_beans_calm")
        else -> trf("adv_default", activeTemperature, activeHumidity, activeWindSpeed, selectedCounty.ifBlank { "Baringo" })
    }

    val counties = MarketDataRepository.getCounties()
    // Instant filter: matches anywhere in the county name, case-insensitive.
    val filteredCounties = remember(counties, countySearchQuery) {
        val q = countySearchQuery.trim()
        if (q.isEmpty()) counties else counties.filter { it.contains(q, ignoreCase = true) }
    }

    // --- LIVE LOOKUP RUNTIME (Room-backed history, Gemini-first briefs) ---
    val scope = rememberCoroutineScope()
    val accountRepo = remember(context) { RoomFarmerAccountRepository(context.applicationContext) }
    val cookieManager = remember(context) { CookieConsentManager(context.applicationContext) }
    val interestTracker = remember(context) { UserInterestTracker(context.applicationContext) }
    val liveLookup = remember(context) { CropLiveLookup(context.applicationContext) }
    var liveBrief by remember { mutableStateOf<String?>(null) }
    var briefSource by remember { mutableStateOf("") }
    var liveLoading by remember { mutableStateOf(false) }
    var cropHistory by remember { mutableStateOf(listOf<String>()) }
    var interestSummary by remember { mutableStateOf("") }

    fun refreshConsent() {
        val c = cookieManager.current()
        consentState = c
        tmpAnalytics = c.analytics
        tmpPersonalization = c.personalization
        tmpMarketing = c.marketing
        interestSummary = if (c.personalization) interestTracker.summaryLine() else ""
    }

    fun trackCropInterest(crop: String, county: String) {
        if (!cookieManager.canPersonalize()) return
        try {
            interestTracker.trackCropView(crop, county)
            interestSummary = interestTracker.summaryLine()
        } catch (e: Exception) { }
    }

    fun saveConsent(choice: String, analytics: Boolean, personalization: Boolean, marketing: Boolean) {
        scope.launch {
            try { accountRepo.saveCookieConsent(choice, analytics, personalization, marketing) } catch (e: Exception) { }
            refreshConsent()
            showCookieBanner = false
            showCookieSettings = false
            if (!personalization) {
                try { interestTracker.clear() } catch (e: Exception) { }
                interestSummary = ""
            }
        }
    }


    fun openWebLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
        }
    }

    // System back button: step back through chat -> market/weather/articles pages
    // instead of exiting the app from a sub-page.
    BackHandler(enabled = showShambaChat || activeViewport == "weather" || activeViewport == "market" || activeViewport == "articles") {
        when {
            showShambaChat -> showShambaChat = false
            activeViewport == "weather" || activeViewport == "market" || activeViewport == "articles" -> activeViewport = "home"
        }
    }

    LaunchedEffect(Unit) {
        cropHistory = try { accountRepo.recentCrops() } catch (e: Exception) { listOf() }
        // Restore saved county preference + ask cookies on first launch.
        try {
            val p = accountRepo.ensureGuestProfile()
            selectedCounty = p.county.takeIf { it.isNotBlank() }?.let {
                if (selectedCounty.isBlank()) it else selectedCounty
            } ?: selectedCounty
        } catch (e: Exception) { }
        refreshConsent()
        showCookieBanner = cookieManager.needsBanner()
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
            "Live lookup failed. Check the connection and try again." to "OFFLINE"
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

    Scaffold(
        bottomBar = {
            // Mobile-style 3-button bottom navigation: Navigate • Language • Settings.
            NavigationBar(
                containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
            ) {
                NavigationBarItem(
                    selected = activeViewport == "market" || activeViewport == "weather",
                    onClick = { navExpanded = true },
                    icon = { Text("🌐", fontSize = 20.sp) },
                    label = { Text(str("tab_navigate"), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { langExpanded = true },
                    icon = { Text("🔤", fontSize = 20.sp) },
                    label = { Text(str("tab_language"), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { settingsExpanded = true },
                    icon = { Text("⚙️", fontSize = 20.sp) },
                    label = { Text(str("tab_settings"), fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = activeViewport == "articles",
                    onClick = { activeViewport = "articles" },
                    icon = { Text("📰", fontSize = 20.sp) },
                    label = { Text(str("tab_articles"), fontSize = 11.sp) }
                )
            }
        }
    ) { scaffoldPadding ->
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
                    ActiveAccountHud(
                        displayName = displayName,
                        displayEmail = displayEmail,
                        onLogout = onLogout
                    )
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
                    text = str("banner_title"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = str("banner_sub"),
                    fontSize = 12.sp,
                    color = Color(0xFFF2E6E6)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // --- 1-i. NATIONAL BIOSECURITY ALERT CAPSULE (county × crop pest monitor) ---
        PestOutbreakAlertBanner(selectedCounty = selectedCounty, plantedCrop = farmerPlantedCrop)

        // Bottom-bar dialogs (mobile sheets replacing the old header buttons).
        if (navExpanded) {
            AlertDialog(
                onDismissRequest = { navExpanded = false },
                title = { Text(str("nav_title"), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        @Composable fun navOption(labelKey: String, target: String) {
                            TextButton(
                                onClick = {
                                    activeViewport = target
                                    navExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    (if (activeViewport == target) "✓ " else "") + str(labelKey),
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        navOption("nav_home", "home")
                        navOption("nav_market", "market")
                        navOption("nav_weather", "weather")
                        navOption("nav_articles", "articles")
                    }
                },
                confirmButton = { TextButton(onClick = { navExpanded = false }) { Text(str("dlg_close"), color = Color.Gray) } },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (langExpanded) {
            AlertDialog(
                onDismissRequest = { langExpanded = false },
                title = { Text(str("lang_title"), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        listOf("English", "Kiswahili", "Kikuyu", "Kalenjin", "Luo").forEach { lang ->
                            TextButton(
                                onClick = {
                                    selectedLanguage = lang
                                    langExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    (if (selectedLanguage == lang) "✓ " else "") + lang,
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { langExpanded = false }) { Text(str("dlg_close"), color = Color.Gray) } },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (settingsExpanded) {
            AlertDialog(
                onDismissRequest = { settingsExpanded = false },
                title = { Text(str("set_title"), fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        @Composable fun settingOption(label: String, onTap: () -> Unit) {
                            TextButton(onClick = onTap, modifier = Modifier.fillMaxWidth()) {
                                Text(label, fontSize = 14.sp, color = Color.Black, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        settingOption(if (isDarkTheme) str("set_dark_on") else str("set_dark_off")) {
                            isDarkTheme = !isDarkTheme
                        }
                        settingOption(str("set_clear")) {
                            liveBrief = null
                            briefSource = ""
                            cropHistory = listOf()
                            cacheNotice = tr("note_cache_cleared")
                        }
                        settingOption(str("set_a11y")) {
                            cacheNotice = tr("note_a11y")
                        }
                        settingOption(str("set_cookies")) {
                            tmpAnalytics = consentState.analytics
                            tmpPersonalization = consentState.personalization
                            tmpMarketing = consentState.marketing
                            showCookieSettings = true
                            settingsExpanded = false
                        }
                        if (cacheNotice != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(cacheNotice ?: "", fontSize = 12.sp, color = Color(0xFF1B5E20))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { settingsExpanded = false }) { Text(str("set_done"), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold) } },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            if (showCookieBanner) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text(str("cookie_title"), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                str("cookie_text"),
                                fontSize = 13.sp, lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(str("cookie_sub"), fontSize = 12.sp, color = Color.DarkGray)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { saveConsent("accepted", true, true, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text(str("cookie_accept"), color = Color.White, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                tmpAnalytics = false; tmpPersonalization = false; tmpMarketing = false
                                showCookieBanner = false
                                showCookieSettings = true
                            }) { Text(str("cookie_settings_btn"), color = Color(0xFF1E3A8A)) }
                            TextButton(onClick = { saveConsent("declined", false, false, false) }) { Text(str("cookie_decline"), color = Color.Gray) }
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (showCookieSettings) {
                AlertDialog(
                    onDismissRequest = { showCookieSettings = false; showCookieBanner = cookieManager.needsBanner() },
                    title = { Text(str("cookie_sheet_title"), fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            @Composable fun consentRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(desc, fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                    Switch(checked = checked, onCheckedChange = onChange)
                                }
                            }
                            consentRow(str("cr_analytics"), str("cr_analytics_d"), tmpAnalytics, { tmpAnalytics = it })
                            consentRow(str("cr_personal"), str("cr_personal_d"), tmpPersonalization, { tmpPersonalization = it })
                            consentRow(str("cr_offers"), str("cr_offers_d"), tmpMarketing, { tmpMarketing = it })
                            if (tmpPersonalization) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val learned = try { interestTracker.summaryLine() } catch (e: Exception) { "" }
                                Text(
                                    if (learned.isNotBlank()) strf("cr_learn_on", learned) else str("cr_learn_on_empty"),
                                    fontSize = 12.sp, color = Color(0xFF1B5E20)
                                )
                            } else {
                                Text(str("cr_learn_off"), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val choice = if (tmpAnalytics && tmpPersonalization && tmpMarketing) "accepted" else "custom"
                                saveConsent(choice, tmpAnalytics, tmpPersonalization, tmpMarketing)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text(str("cr_save"), color = Color.White, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCookieSettings = false; showCookieBanner = cookieManager.needsBanner() }) { Text(str("dlg_close"), color = Color.Gray) }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            run {
                val farmTips = listOf(
                    tr("tip_1"),
                    tr("tip_2"),
                    tr("tip_3"),
                    tr("tip_4"),
                    tr("tip_5"),
                    tr("tip_6"),
                    tr("tip_7")
                )
                val tipIndex = try {
                    (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % farmTips.size)
                } catch (e: Exception) {
                    0
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                ) {
                    Text(
                        text = strf("tip_prefix", farmTips[tipIndex]),
                        color = Color(0xFF2C2C2E),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Active viewport indicator: Home resets to primary dashboard.
            Text(
                text = when (activeViewport) {
                    "market" -> strf("vp_market", selectedLanguage)
                    "weather" -> strf("vp_weather", selectedLanguage)
                    "articles" -> strf("vp_articles", selectedLanguage)
                    else -> strf("vp_home", selectedLanguage)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else Color(0xFF2C2C2E),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Dedicated Market Overview page (app core: market trends & analysis).
            if (activeViewport == "market") {
                MarketOverviewPage(
                    selectedCounty = selectedCounty,
                    countyData = countyData,
                    contentPrimary = contentPrimary,
                    isDarkTheme = isDarkTheme,
                    onBack = { activeViewport = "home" },
                    onSelectCounty = { selectedCounty = it },
                    cropFilter = marketCropFilter,
                    onCropFilterChange = { marketCropFilter = it },
                    myCrops = cropHistory,
                    plantedCrop = farmerPlantedCrop,
                    onAddCrop = { crop ->
                        farmerPlantedCrop = crop
                        marketCropFilter = crop
                        trackCropInterest(crop, selectedCounty.ifBlank { "Kenya" })
                        scope.launch {
                            try {
                                accountRepo.logCropSearch(
                                    crop,
                                    selectedCounty.ifBlank { "Kenya" },
                                    "MANUAL"
                                )
                                cropHistory = accountRepo.recentCrops()
                            } catch (e: Exception) {
                            }
                        }
                    },
                    onSelectCrop = { crop ->
                        farmerPlantedCrop = crop
                        marketCropFilter = crop
                        trackCropInterest(crop, selectedCounty.ifBlank { "Kenya" })
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Dedicated Weather Terminal page (separate from Home page).
            if (activeViewport == "weather") {
                WeatherTerminalScreen(
                    county = selectedCounty.ifBlank { "Tana River" },
                    isDarkTheme = isDarkTheme,
                    onClose = { activeViewport = "home" }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Top & trending agritech news page (separate from Home page).
            if (activeViewport == "articles") {
                ArticlesPage(
                    isDarkTheme = isDarkTheme,
                    onBack = { activeViewport = "home" },
                    onOpenLink = { openWebLink(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (activeViewport != "weather" && activeViewport != "market" && activeViewport != "articles") {

            // --- 2. HIGH-CONTRAST GOLD DROPDOWN HUB ---
            Text(
                text = str("home_corridor"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = contentPrimary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            // M-AgriLink Core Engine.
            // Interactive county-hub search: typing filters the 47-county list instantly.
            OutlinedTextField(
                value = countySearchQuery,
                onValueChange = {
                    countySearchQuery = it
                    expanded = true
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(str("home_county_search"), color = Color.Gray) },
                leadingIcon = { Text("🔍", fontSize = 16.sp) },
                trailingIcon = {
                    if (countySearchQuery.isNotEmpty()) {
                        TextButton(onClick = { countySearchQuery = "" }) {
                            Text("✕", fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                },
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
                        text = if (selectedCounty.isEmpty()) str("home_tap") else selectedCounty,
                        color = if (selectedCounty.isEmpty()) Color.DarkGray else Color.Black, // ⚠️ FIX: Highly visible text out in the sun
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = str("home_dropdown"),
                        tint = Color(0xFFE6B325),
                        modifier = Modifier.size(28.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 400.dp).background(Color.White)
                ) {
                    if (filteredCounties.isEmpty()) {
                        Text(
                            text = strf("home_county_empty", countySearchQuery.trim()),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        filteredCounties.forEach { county ->
                            DropdownMenuItem(
                                text = { Text(county, color = Color.Black, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    selectedCounty = county
                                    expanded = false
                                    countySearchQuery = ""
                                    scope.launch { accountRepo.updateCounty(county) }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 2B. FARMER PLANTED CROP INPUT MODULE ---
            Text(
                text = str("home_crop_q"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = contentPrimary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            OutlinedTextField(
                value = farmerPlantedCrop,
                onValueChange = { farmerPlantedCrop = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(str("home_crop_hint"), color = Color.Gray) },
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
                            color = if (cropKey == crop.lowercase()) contentAccent else contentPrimary
                        )
                    }
                }
            }

            if (cropMatch?.corrected == true && farmerPlantedCrop.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strf("home_corrected", cropMatch.canonical, cropMatch.original),
                    fontSize = 12.sp,
                    color = contentAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (cropHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = str("home_recent"),
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
                            onClick = {
                                farmerPlantedCrop = past
                                marketCropFilter = past
                                activeViewport = "market"
                            },
                            label = { Text(past, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    str("home_recent_hint"),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
                                    text = str("bp_title"),
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
                                        contentDescription = if (AppAudioGate.muted) str("bp_unmute") else str("bp_mute"),
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
                                text = str("bp_action"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(advisoryField(blueprintCrop.advisory, "space"), color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(advisoryField(blueprintCrop.advisory, "land"), color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = str("bp_harvest"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(advisoryField(blueprintCrop.advisory, "moist"), color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(advisoryField(blueprintCrop.advisory, "harvest"), color = Color.DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            // --- 2D. LIVE METEOROLOGICAL ALERT STRIP ---
                            Text(
                                text = str("live_mgmt"),
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
                                    text = strf("live_brief_title", farmerPlantedCrop),
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
                                        str("live_searching"),
                                        color = Color.DarkGray,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    liveBrief ?: strf("live_none", farmerPlantedCrop),
                                    color = Color.DarkGray,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                str("live_saved"),
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
                            text = str("arb_title"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6B325)
                        )
                    }

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                    if (selectedCounty.isEmpty()) {
                        Text(
                            text = str("arb_empty"),
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        Text(
                            text = strf("arb_route", selectedCounty, targetHubName),
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
                                        strf("arb_local", crop.localPriceKes),
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        advisoryField(crop.advisory, "space"),
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(str("arb_hub"), color = Color.LightGray, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "KES ${crop.hubPriceKes}",
                                        color = Color(0xFFE6B325),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        strf("arb_net", crop.netMarginKes),
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
                                text = countyData.firstOrNull()?.let { advisoryField(it.advisory, "moist") }
                                    ?: str("arb_moist_fallback"),
                                color = Color(0xFF81C784), // Positive Green highlight
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3-ii. SACCO INPUT PLANNER WIDGET (price grid + acreage calculator) ---
            SaccoInputPlannerWidget(
                modifier = Modifier.fillMaxWidth(),
                plantedCrop = farmerPlantedCrop
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3-iii. APICULTURE HIVE JOURNAL (KALRO ABIRI module) ---
            HiveJournalComponent(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            // --- 3-iv. FREQUENTLY ASKED QUESTIONS (expandable docs) ---
            FaqSectionComponent(textToSpeech = textToSpeech, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(28.dp))

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
                            text = str("shamba_title"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = str("shamba_desc"),
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
                        Text(str("shamba_start"), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

            // --- 3A. SHAMBA AI DIAGNOSTIC COMPANION (stationary cockpit card) ---
            // Field-green glassmorphism entry: the floating avatar bubble and
            // its overlay were purged; this card routes to the scanner graph.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1B4332), Color(0xFF2D6A4F))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(2.dp, Color(0xFFE6B325), CircleShape)
                    ) {
                        Text(
                            text = "\uD83C\uDF3E",
                            fontSize = 28.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\uD83E\uDD16 Shamba AI Diagnostic Companion",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "They point their lens at any leaf and their companion reads it for them.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color(0xFFD8F3DC)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenScanner,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE6B325),
                                contentColor = Color(0xFF1B4332)
                            )
                        ) {
                            Text(
                                "Launch Intelligent AI Scan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
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
                            text = str("sac3_title"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C2C2E)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedCounty.isEmpty()) str("sac3_sub_empty")
                        else strf("sac3_sub_county", selectedCounty),
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
                            str("sac3_tip"),
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
                text = str("cta_launch"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
// CRITICAL SPACER BUFFER: Prevents layout elements from crashing into the bottom tab bar icons
            Spacer(modifier = Modifier.height(100.dp))
            Text(
                text = "© 2026 M-AgriLink. All rights reserved.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            } // end home-page widgets (weather lives on its own page)
        }
    }
    }
}

