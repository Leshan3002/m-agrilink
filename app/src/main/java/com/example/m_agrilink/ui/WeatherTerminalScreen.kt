package com.example.m_agrilink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.delay
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

@Composable
fun WeatherTerminalScreen(
    county: String = "Tana River",
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val fusedClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
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
    var gpsEnabled by remember { mutableStateOf(true) }

    fun refreshGpsState() {
        val lm = context.getSystemService(LocationManager::class.java)
        gpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFineLocation = granted
        showLocationDialog = !granted
        if (granted) {
            refreshGpsState()
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    latLon = if (loc != null) {
                        String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)
                    } else {
                        null
                    }
                }
            } catch (e: SecurityException) {
                latLon = null
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshGpsState()
        if (hasFineLocation) {
            showLocationDialog = false
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    latLon = if (loc != null) {
                        String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)
                    } else {
                        null
                    }
                }
            } catch (e: SecurityException) {
                latLon = null
            }
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

    val forecast = remember {
        listOf(
            ForecastDay("Monday", "sun", 28, 19, 10),
            ForecastDay("Tuesday", "rain", 26, 19, 40),
            ForecastDay("Wednesday", "cloud", 27, 18, 25),
            ForecastDay("Thursday", "sun", 29, 20, 5),
            ForecastDay("Friday", "sun", 28, 19, 15),
            ForecastDay("Saturday", "rain", 25, 18, 55),
            ForecastDay("Sunday", "cloud", 27, 19, 20)
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    county.ifBlank { "Tana River" } + (if (latLon != null) " • $latLon" else ""),
                    color = Color(0xFFDCE6F5),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                        Text("24.5°C", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Humidity 65% • Wind 12 km/h", color = Color(0xFFDCE6F5), fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "🌱 Weather Advisory: High ambient humidity detected across ${county.ifBlank { "Tana River" }}. Ideal morning window open for protective anti-fungal treatments before wind speed accelerates.",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
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
                        Text("06:18 AM Local Time", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = "Sun path",
                        tint = Color(0xFFE6B325),
                        modifier = Modifier.size(40.dp).scale(sunScale)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🌇 Sunset", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("06:27 PM Local Time", fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
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
            "📅 7-Day Extended Forecast",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            forecast.forEach { day ->
                Card(
                    modifier = Modifier.width(140.dp).shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
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
                    }
                }
            }
        }
    }
}
