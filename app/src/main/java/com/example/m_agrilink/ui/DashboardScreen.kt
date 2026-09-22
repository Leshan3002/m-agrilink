package com.example.m_agrilink.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.m_agrilink.AgriLinkRouter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.*

// 1. Data Model Setup
data class MarketItem(val commodity: String, val price: String, val location: String)

data class DashboardState(
    val weatherTemp: String = "24.0°C",
    val rainProb: String = "15%",
    val maizeVariety: String = "Maize variety Katumani",
    val currentLocation: String = "Detecting location...",
    val marketPrices: List<MarketItem> = listOf(
        MarketItem("Maize", "KES 4,500", "Nairobi"),
        MarketItem("Beans", "KES 12,000", "Mombasa"),
        MarketItem("Onions", "KES 100", "Nakuru"),
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    var dashboardState by remember { mutableStateOf(DashboardState()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAiChat by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }



    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            getCurrentLocation(context, fusedLocationClient, geocoder) { location, city ->
                dashboardState = dashboardState.copy(
                    currentLocation = city,
                    weatherTemp = if (city == "Nairobi") "22.0°C" else "26.0°C",
                    marketPrices = listOf(
                        MarketItem("Maize", "KES 4,200", city),
                        MarketItem("Beans", "KES 11,500", city),
                        MarketItem("Onions", "KES 90", city)
                    )
                )
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
            dashboardState = dashboardState.copy(currentLocation = "Permission Denied")
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation(context, fusedLocationClient, geocoder) { location, city ->
                dashboardState = dashboardState.copy(
                    currentLocation = city,
                    weatherTemp = if (city == "Nairobi") "22.0°C" else "26.0°C",
                    marketPrices = listOf(
                        MarketItem("Maize", "KES 4,200", city),
                        MarketItem("Beans", "KES 11,500", city),
                        MarketItem("Onions", "KES 90", city)
                    )
                )
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "M-AgriLink Dashboard",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("AI Assistant") },
                            onClick = {
                                menuExpanded = false
                                showAiChat = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Connect KAOP App") },
                            onClick = {
                                menuExpanded = false
                                AgriLinkRouter.routeToAgriculturalService(context, AgriLinkRouter.PACKAGE_KAOP)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Connect KALRO Selector") },
                            onClick = {
                                menuExpanded = false
                                AgriLinkRouter.routeToAgriculturalService(context, AgriLinkRouter.PACKAGE_KALRO)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Connect KAMIS App") },
                            onClick = {
                                menuExpanded = false
                                AgriLinkRouter.routeToAgriculturalService(context, AgriLinkRouter.PACKAGE_KAMIS)
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Location Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Current Location: ${dashboardState.currentLocation}",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Weather Data Card
            DashboardCard(title = "KAOP Weather Info") {
                InfoRow(label = "Temperature", value = dashboardState.weatherTemp)
                InfoRow(label = "Rain Probability", value = dashboardState.rainProb)
            }

            // Crop Recommendation Card
            DashboardCard(title = "KALRO Selector") {
                InfoRow(label = "Variety", value = dashboardState.maizeVariety)
            }

            // KAMIS Prices Table Card
            DashboardCard(title = "KAMIS Market Prices") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Commodity", color = Color(0xFF37474F), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Price", color = Color(0xFF37474F), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Location", color = Color(0xFF37474F), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
                    
                    dashboardState.marketPrices.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.commodity, color = Color.Black, modifier = Modifier.weight(1f))
                            Text(item.price, color = Color.Black, modifier = Modifier.weight(1f))
                            Text(item.location, color = Color.Black, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }

        if (showAiChat) {
            ModalBottomSheet(
                onDismissRequest = { showAiChat = false },
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                AiChatOverlay(
                    telemetry = TelemetryContext(
                        location = dashboardState.currentLocation,
                        temp = dashboardState.weatherTemp,
                        rainProb = dashboardState.rainProb,
                        wind = "10.3 km/h",
                        humidity = "62%",
                        cropVariety = dashboardState.maizeVariety,
                        maizePrice = dashboardState.marketPrices.firstOrNull()?.price ?: "N/A"
                    ),
                    onDismiss = { showAiChat = false }
                )
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF37474F))
        Text(text = value, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

// 5. GPS Logic
@SuppressLint("MissingPermission")
fun getCurrentLocation(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    geocoder: Geocoder,
    onLocationFound: (Location, String) -> Unit
) {
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location ->
            if (location != null) {
                try {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val city = addresses?.get(0)?.locality ?: "Unknown City"
                    onLocationFound(location, city)
                } catch (e: Exception) {
                    onLocationFound(location, "Lat: ${location.latitude}, Lon: ${location.longitude}")
                }
            } else {
                Toast.makeText(context, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
}
