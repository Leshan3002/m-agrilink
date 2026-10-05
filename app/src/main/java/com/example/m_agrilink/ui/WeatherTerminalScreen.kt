package com.example.m_agrilink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.m_agrilink.OpenMeteoApiService
import com.example.m_agrilink.OpenWeatherApiService
import com.example.m_agrilink.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ForecastDay(
    val weekday: String,
    val iconKind: String,
    val maxC: Int,
    val minC: Int,
    val rainPct: Int
)

private fun dayAdvisory(day: ForecastDay, county: String): String {
    val place = county.ifBlank { "Tana River" }
    return when {
        day.rainPct >= 50 -> "🌧️ ${day.weekday} in $place: Heavy rain risk (${day.rainPct}%). Max ${day.maxC}°C / Min ${day.minC}°C. " +
            "Avoid spraying; clear drainage furrows, delay fertilizer, keep harvested grain under 13.5% moisture. Scout for fungal pressure morning and evening."
        day.rainPct >= 30 -> "🌦️ ${day.weekday} in $place: Moderate showers likely (${day.rainPct}%). Max ${day.maxC}°C / Min ${day.minC}°C. " +
            "Spray only in dry morning window, wear protective gear, mulch to stop splash-borne disease. Ideal for transplanting after rain settles."
        day.iconKind == "sun" && day.maxC >= 28 -> "☀️ ${day.weekday} in $place: Hot and dry. Max ${day.maxC}°C / Min ${day.minC}°C, rain ${day.rainPct}%. " +
            "Irrigate early morning, mulch heavily, provide shade/water for cattle and poultry. Good day for drying grain and spraying before wind picks up."
        else -> "⛅ ${day.weekday} in $place: Mild conditions. Max ${day.maxC}°C / Min ${day.minC}°C, rain ${day.rainPct}%. " +
            "Good window for weeding, scouting pests, and applying protective anti-fungal treatments in the calm morning hours."
    }
}

@Composable
fun WeatherTerminalScreen(
    county: String = "Tana River",
    isDarkTheme: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }
    val openMeteo = remember {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApiService::class.java)
    }
    val openWeather = remember {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherApiService::class.java)
    }

    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showLocationDialog by remember { mutableStateOf(!hasFineLocation) }
    var latLon by remember { mutableStateOf<String?>(null) }
    var autoLat by remember { mutableStateOf<Double?>(null) }
    var autoLon by remember { mutableStateOf<Double?>(null) }
    var precisePlace by remember { mutableStateOf<String?>(null) }
    var detectedCounty by remember { mutableStateOf<String?>(null) }
    var resolvingPlace by remember { mutableStateOf(false) }
    var locatingNow by remember { mutableStateOf(false) }
    var gpsEnabled by remember { mutableStateOf(true) }
    // Live Open-Meteo snapshot for the AUTO-detected coordinates.
    var liveTemp by remember { mutableStateOf<Double?>(null) }
    var liveHumidity by remember { mutableStateOf<Int?>(null) }
    var liveWind by remember { mutableStateOf<Double?>(null) }
    var liveWeatherCode by remember { mutableStateOf<Int?>(null) }
    var liveLoading by remember { mutableStateOf(false) }
    var liveError by remember { mutableStateOf<String?>(null) }
    var liveForecast by remember { mutableStateOf<List<ForecastDay>?>(null) }
    var wxSource by remember { mutableStateOf("…") }
    var wxDataTime by remember { mutableStateOf<String?>(null) }
    var wxSunrise by remember { mutableStateOf<String?>(null) }
    var wxSunset by remember { mutableStateOf<String?>(null) }

    fun resolvePrecisePlace(lat: Double, lon: Double) {
        if (resolvingPlace) return
        resolvingPlace = true
        scope.launch(Dispatchers.IO) {
            var label: String? = null
            var countyGuess: String? = null
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context.applicationContext, Locale.getDefault())
                    .getFromLocation(lat, lon, 1)
                val a = results?.firstOrNull()
                if (a != null) {
                    // Prefer street-level detail: subLocality (e.g. Westlands)
                    // + locality/county (e.g. Nairobi).
                    val city = a.locality ?: a.subAdminArea ?: a.adminArea
                    val hood = a.subLocality ?: a.thoroughfare ?: a.featureName
                    label = when {
                        city != null && hood != null && !hood.equals(city, ignoreCase = true) -> "$city • $hood"
                        city != null -> city
                        hood != null -> hood
                        a.adminArea != null -> a.adminArea
                        else -> null
                    }
                    countyGuess = a.subAdminArea ?: a.locality ?: a.adminArea
                }
            } catch (e: Exception) {
                label = null
            }
            withContext(Dispatchers.Main) {
                if (label != null) precisePlace = label
                if (countyGuess != null) detectedCounty = countyGuess
                resolvingPlace = false
            }
        }
    }

    fun fetchLiveWx(lat: Double, lon: Double) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { liveLoading = true; liveError = null }
            // 1) PRIMARY: OpenWeatherMap (user choice). Needs OPENWEATHER_API_KEY.
            val owmKey = try { BuildConfig.OPENWEATHER_API_KEY.trim() } catch (e: Exception) { "" }
            if (owmKey.isNotBlank()) {
                try {
                    val cur = openWeather.getCurrent(lat, lon, owmKey)
                    val fc = try { openWeather.getForecast(lat, lon, owmKey) } catch (e: Exception) { null }
                    val t = cur.main?.temp
                    val h = cur.main?.humidity
                    val wMs = cur.wind?.speed // m/s
                    val wKmh = if (wMs != null) wMs * 3.6 else null
                    // OpenWeather condition id 200-623 = rain/drizzle/snow.
                    val owmId = cur.weather.firstOrNull()?.id
                    val raining = owmId != null && owmId in 200..623
                    // Group 3-hour slots into calendar days for honest max/min + max PoP.
                    val days = try {
                        val byDay = linkedMapOf<String, MutableList<com.example.m_agrilink.OwmItem>>()
                        (fc?.list ?: emptyList()).forEach { it ->
                            val key = (it.dt_txt?.take(10)) ?: "d${byDay.size}"
                            byDay.getOrPut(key) { mutableListOf() }.add(it)
                        }
                        val fmtDay = java.text.SimpleDateFormat("EEE", Locale.getDefault())
                        byDay.entries.take(7).mapIndexed { i, (_, items) ->
                            val maxT = items.mapNotNull { it.main?.temp_max ?: it.main?.temp }.maxOrNull()?.toInt() ?: 27
                            val minT = items.mapNotNull { it.main?.temp_min ?: it.main?.temp }.minOrNull()?.toInt() ?: 18
                            val rain = items.mapNotNull { it.pop }.maxOrNull()?.times(100)?.toInt() ?: 10
                            val label = if (i == 0) "Today" else fmtDay.format(Date((items.firstOrNull()?.dt ?: 0L) * 1000L))
                            val kind = when {
                                rain >= 40 -> "rain"
                                maxT >= 28 -> "sun"
                                else -> "cloud"
                            }
                            ForecastDay(label, kind, maxT, minT, rain)
                        }.takeIf { it.isNotEmpty() }
                    } catch (e: Exception) { null }
                    val sunriseStr = cur.sys?.sunrise?.let {
                        java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it * 1000L))
                    }
                    val sunsetStr = cur.sys?.sunset?.let {
                        java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it * 1000L))
                    }
                    withContext(Dispatchers.Main) {
                        if (t != null) liveTemp = t
                        liveHumidity = h
                        if (wKmh != null) liveWind = wKmh
                        // Map OWM rain -> WMO-ish rain bucket so rain-guard badge still works.
                        liveWeatherCode = if (raining) 61 else 0
                        if (days != null) liveForecast = days
                        wxSource = "OpenWeatherMap live"
                        wxDataTime = cur.dt.takeIf { it > 0 }?.let {
                            java.text.SimpleDateFormat("HH:mm z", Locale.getDefault()).format(Date(it * 1000L))
                        }
                        if (sunriseStr != null) wxSunrise = sunriseStr
                        if (sunsetStr != null) wxSunset = sunsetStr
                        liveLoading = false
                    }
                    return@launch
                } catch (e: Exception) {
                    // fall through to Open-Meteo fallback below
                }
            }
            // 2) FALLBACK: Open-Meteo (keyless). Time-aligned, never .firstOrNull().
            try {
                val res = openMeteo.getLiveForecast(lat, lon)
                val times = res.hourly.time
                val temps = res.hourly.temperature_2m
                val rains = res.hourly.precipitation_probability
                val hums = res.hourly.relativehumidity_2m
                // Find hourly index closest to NOW (Africa/Nairobi), not index 0.
                fun nowIndex(): Int {
                    if (times.isEmpty()) return 0
                    return try {
                        val tz = java.util.TimeZone.getTimeZone("Africa/Nairobi")
                        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                        fmt.timeZone = tz
                        val nowStr = fmt.format(Date()).substring(0, 13) // yyyy-MM-dd'T'HH
                        val idx = times.indexOfFirst { it.take(13) == nowStr }
                        if (idx >= 0) idx else 0
                    } catch (e: Exception) { 0 }
                }
                val nowIdx = nowIndex().coerceIn(0, maxOf(0, temps.size - 1))
                val nowT = res.current?.temperature_2m
                    ?: res.current_weather?.temperature
                    ?: temps.getOrNull(nowIdx)
                val nowH = res.current?.relative_humidity_2m ?: hums.getOrNull(nowIdx)
                val nowW = res.current?.wind_speed_10m ?: res.current_weather?.windspeed
                // WMO weathercode parsed off the main thread with the rest of
                // the payload (M-AgriLink rain-guard, developer: Levis Lekesio).
                val nowCode = res.current?.weather_code ?: res.current_weather?.weathercode
                withContext(Dispatchers.Main) {
                    if (nowT != null) liveTemp = nowT
                    liveHumidity = nowH
                    if (nowW != null) liveWind = nowW
                    liveWeatherCode = nowCode
                    // Prefer daily aggregates when present — honest max/min + max PoP.
                    try {
                        val daily = res.daily
                        liveForecast = if (daily != null && daily.time.size >= 7) {
                            val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val outFmt = java.text.SimpleDateFormat("EEE", Locale.getDefault())
                            daily.time.take(7).mapIndexed { i, d ->
                                val maxT = daily.temperature_2m_max.getOrNull(i)?.toInt() ?: 27
                                val minT = daily.temperature_2m_min.getOrNull(i)?.toInt() ?: 18
                                val rain = daily.precipitation_probability_max.getOrNull(i) ?: 20
                                val code = daily.weathercode.getOrNull(i) ?: -1
                                val label = if (i == 0) "Today" else try { outFmt.format(inFmt.parse(d)!!) } catch (e: Exception) { "+${i}d" }
                                val kind = when {
                                    code in setOf(51, 53, 55, 61, 63, 65, 80, 81, 82) || rain >= 40 -> "rain"
                                    maxT >= 28 -> "sun"
                                    else -> "cloud"
                                }
                                ForecastDay(label, kind, maxT, minT, rain)
                            }
                        } else {
                            // Hourly-derived fallback: slice from NOW, 24h per day, max PoP.
                            (0 until 7).map { d ->
                                val start = (nowIdx + d * 24).coerceAtMost(maxOf(0, temps.size - 1))
                                val tSlice = temps.drop(start).take(24)
                                val rSlice = rains.drop(start).take(24)
                                val maxT = tSlice.maxOrNull()?.toInt() ?: 27
                                val minT = tSlice.minOrNull()?.toInt() ?: 18
                                val rain = rSlice.maxOrNull() ?: 20
                                val label = if (d == 0) "Today" else "+${d}d"
                                val kind = when {
                                    rain >= 40 -> "rain"
                                    maxT >= 28 -> "sun"
                                    else -> "cloud"
                                }
                                ForecastDay(label, kind, maxT, minT, rain)
                            }
                        }
                    } catch (e: Exception) { liveForecast = null }
                    wxSource = "Open-Meteo fallback"
                    wxDataTime = res.current?.time ?: res.current_weather?.time ?: times.getOrNull(nowIdx)
                    if (res.daily?.sunrise?.isNotEmpty() == true) {
                        wxSunrise = res.daily.sunrise.firstOrNull()?.takeLast(5)
                        wxSunset = res.daily.sunset.firstOrNull()?.takeLast(5)
                    }
                    liveLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { liveLoading = false; liveError = "Live sync failed — showing offline pattern." }
            }
        }
    }

    fun onGpsFix(lat: Double, lon: Double) {
        autoLat = lat
        autoLon = lon
        latLon = String.format(Locale.US, "%.4f, %.4f", lat, lon)
        resolvePrecisePlace(lat, lon)
        fetchLiveWx(lat, lon)
    }

    fun refreshGpsState() {
        val lm = context.getSystemService(LocationManager::class.java)
        gpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    fun requestFreshFix() {
        if (locatingNow) return
        refreshGpsState()
        try {
            locatingNow = true
            // Fresh high-accuracy fix first (real location NOW, not Home input).
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { loc ->
                locatingNow = false
                if (loc != null) {
                    onGpsFix(loc.latitude, loc.longitude)
                } else {
                    // Fallback to last known fix.
                    try {
                        fusedClient.lastLocation.addOnSuccessListener { last ->
                            if (last != null) onGpsFix(last.latitude, last.longitude) else latLon = null
                        }
                    } catch (e: SecurityException) { latLon = null }
                }
            }.addOnFailureListener {
                locatingNow = false
                try {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) onGpsFix(last.latitude, last.longitude) else latLon = null
                    }
                } catch (e: SecurityException) { latLon = null }
            }
        } catch (e: SecurityException) {
            locatingNow = false
            latLon = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFineLocation = granted
        showLocationDialog = !granted
        if (granted) {
            showLocationDialog = false
            requestFreshFix()
        }
    }

    // AUTO-DETECT on entry: the moment the user opens Weather Terminal we
    // ignore the Home dashboard county and fix the real GPS location.
    LaunchedEffect(Unit) {
        refreshGpsState()
        if (hasFineLocation) {
            showLocationDialog = false
            requestFreshFix()
        } else {
            showLocationDialog = true
        }
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Location Access", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "🗺️ Allow M-AgriLink to Access Location? We require precise GPS synchronization to pull live Open-Meteo climate tracking maps, real-time localized rain curves, and specialized crop alerts for your specific county."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!hasFineLocation) {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                )
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                        )
                                    )
                                } catch (e2: Exception) {
                                    showLocationDialog = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                ) {
                    Text(
                        if (!hasFineLocation) "Allow Location" else "Open Device Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Later", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Live digital clock (regional timezone).
    var nowLabel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("hh:mm:ss a z", Locale.getDefault())
        while (true) {
            nowLabel = fmt.format(Date())
            delay(1000L)
        }
    }

    val pulse by rememberInfiniteTransition(label = "wxPulse").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wxPulseAlpha"
    )
    val sunScale by rememberInfiniteTransition(label = "wxSun").animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wxSunScale"
    )

    // No fake numbers: when live is null we show — instead of 24.5°C/65%.
    val forecast = liveForecast ?: emptyList()
    val hasLive = liveForecast != null && liveTemp != null
    var selectedDay by remember { mutableStateOf<ForecastDay?>(null) }
    // Effective place: AUTO-detected GPS first, Home county only as fallback.
    val effectivePlace = precisePlace ?: detectedCounty ?: county.ifBlank { "Tana River" }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Dedicated page back navigation (separate from Home).
        if (onClose != null) {
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Text("← Back to Home Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        // 2. Living weather container with premium graphics.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E3A8A), Color(0xFF64748B))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⛅ Weather Terminal",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (onClose != null) {
                        TextButton(onClick = onClose) {
                            Text("Close", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    (if (autoLat != null) "📍 Auto-detected: " else "📍 Home fallback: ") +
                        effectivePlace +
                        (if (resolvingPlace || locatingNow || liveLoading) " • locating…" else "") +
                        (if (latLon != null) " • $latLon" else "") +
                        " • $wxSource" +
                        (if (wxDataTime != null) " • $wxDataTime" else "") +
                        (if (liveError != null) " • OFFLINE" else ""),
                    color = Color(0xFFDCE6F5),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (!hasFineLocation) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            else requestFreshFix()
                        }
                    ) {
                        Text(
                            if (locatingNow || liveLoading) "Locating…" else "↻ Re-detect my location",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = "Sun",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(56.dp).scale(sunScale)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = "Cloud",
                        tint = Color.White.copy(alpha = pulse),
                        modifier = Modifier.size(44.dp).alpha(pulse)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (liveTemp != null) String.format(Locale.US, "%.1f°C", liveTemp) else "— offline —",
                            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Humidity ${liveHumidity?.let { "$it%" } ?: "—"} • Wind ${liveWind?.let { String.format(Locale.US, "%.0f km/h", it) } ?: "—"}" +
                                (if (liveError != null) " • offline pattern" else ""),
                            color = Color(0xFFDCE6F5), fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // WMO rain-guard: current_weather.weathercode decides, never the
                // hourly PoP string. Rain codes 51/53/55/61/63/65/80/81/82 force
                // the rainfall badge + KALRO washout advisory below.
                // Engineered under developer profile: Levis Lekesio.
                val isActiveRainfall = (liveWeatherCode ?: -1) in
                    setOf(51, 53, 55, 61, 63, 65, 80, 81, 82)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        if (isActiveRainfall) {
                            str("wx_rain_badge") + "\n\n" + str("wx_rain_advisory")
                        } else {
                            "🌱 Weather Advisory: High ambient humidity detected across $effectivePlace. Ideal morning window open for protective anti-fungal treatments before wind speed accelerates."
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (isActiveRainfall) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (!hasFineLocation || !gpsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (!hasFineLocation) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (e: Exception) {
                                    showLocationDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Enable GPS / Allow Location", color = Color(0xFF1E3A8A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Sunrise / sunset timeline dial.
        Card(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFE6B325))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("☀️ Daylight Timeline", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("🌅 Sunrise", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(wxSunrise ?: "— live only —", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = "Sun path",
                        tint = Color(0xFFE6B325),
                        modifier = Modifier.size(40.dp).scale(sunScale)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🌇 Sunset", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(wxSunset ?: "— live only —", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "🕒 Live local time: ${nowLabel.ifBlank { "syncing…" }}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. 7-day extended forecast bar index.
        Text(
            "📅 7-Day Extended Forecast • $wxSource",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDarkTheme) Color.White else Color.Black,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        if (!hasLive) {
            Text(
                if (liveLoading || locatingNow) "Syncing live forecast…" else "No live data yet — check GPS + internet, then tap Re-detect.",
                fontSize = 12.sp, color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            forecast.forEach { day ->
                Card(
                    onClick = { selectedDay = day },
                    modifier = Modifier.width(140.dp).shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedDay?.weekday == day.weekday) Color(0xFFFFF8E1) else Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(day.weekday, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(6.dp))
                        Icon(
                            imageVector = when (day.iconKind) {
                                "rain" -> Icons.Filled.WbCloudy
                                "cloud" -> Icons.Filled.Cloud
                                else -> Icons.Filled.WbSunny
                            },
                            contentDescription = day.weekday,
                            tint = when (day.iconKind) {
                                "rain" -> Color(0xFF1E3A8A)
                                "cloud" -> Color(0xFF64748B)
                                else -> Color(0xFFE6B325)
                            },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (day.iconKind == "rain") "🌧 Rain Cloud" else if (day.iconKind == "cloud") "☁️ Cloudy" else "☀️ Bright Sun",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Max: ${day.maxC}°C / Min: ${day.minC}°C",
                            fontSize = 11.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "☔ ${day.rainPct}% Rain",
                            fontSize = 11.sp,
                            color = Color(0xFF1E3A8A),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap for details →",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        selectedDay?.let { day ->
            AlertDialog(
                onDismissRequest = { selectedDay = null },
                title = { Text("📅 ${day.weekday} — Full Weather Details", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            "🌡️ Max: ${day.maxC}°C / Min: ${day.minC}°C",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("☔ Rain probability: ${day.rainPct}%", fontSize = 13.sp, color = Color(0xFF1E3A8A))
                        Text("💧 Humidity: ${liveHumidity?.let { "$it%" } ?: "—"} • 💨 Wind: ${liveWind?.let { String.format(Locale.US, "%.0f km/h", it) } ?: "—"}", fontSize = 13.sp, color = Color.DarkGray)
                        Text("🌅 Sunrise ${wxSunrise ?: "—"} • 🌇 Sunset ${wxSunset ?: "—"}", fontSize = 13.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(dayAdvisory(day, effectivePlace), fontSize = 13.sp, lineHeight = 19.sp, color = Color.Black)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { selectedDay = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                    ) {
                        Text("Got it", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
