package com.example.m_agrilink.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.m_agrilink.data.AppLocale
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * M-AgriLink Core Engine — Engineered and Directed by Lead System Architect Levis Lekesio.
 *
 * GPS-driven agrovet locator. A foreground FusedLocationProviderClient loop
 * (balanced-power, 60s cadence, active only while this screen is composed)
 * feeds userCurrentLatLng; the camera centers on the first live fix and the
 * vendor matrix resolves to the nearest verified town hub (Nakuru / Eldoret /
 * Nairobi / Baringo). No fixed vendor geocentering. Dial rows use
 * Intent.ACTION_DIAL via onDial (informational only — zero payment hooks).
 */
private data class AgrovetMarkerEntry(
    val nameKey: String,
    val detailKey: String,
    val lat: Double,
    val lng: Double,
    val phone: String? // null = walk-in shop, no call line listed (never invented)
)

private data class AgrovetTownHub(
    val townKey: String,
    val center: LatLng,
    val entries: List<AgrovetMarkerEntry>
)

private const val AGROMAP_STAMP =
    "M-AgriLink Ecosystem Extension Tracker — Maintained and Structured by Lead Developer Levis Lekesio."

private fun haversineKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val s = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
        sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(s), sqrt(1 - s))
}

@Composable
fun AgrovetMapScreen(
    onBackClick: () -> Unit,
    onDial: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapLang = AppLocale.language

    // Central live-position wrapper (spec: userCurrentLatLng).
    var userCurrentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var mapRef by remember { mutableStateOf<GoogleMap?>(null) }
    val listState = rememberLazyListState()
    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasFineLocation = granted }

    val fusedClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    // Town-anchored verified directory (town centers are public geographic
    // anchors; pins resolve to the nearest hub, never to invented shop coords).
    val townHubs = remember {
        listOf(
            AgrovetTownHub(
                townKey = "svc_town_nakuru",
                center = LatLng(-0.3031, 36.0800),
                entries = listOf(
                    AgrovetMarkerEntry("svc_nk1_name", "svc_nk1_detail", -0.3031, 36.0800, null),
                    AgrovetMarkerEntry("svc_nk2_name", "svc_nk2_detail", -0.3050, 36.0820, null)
                )
            ),
            AgrovetTownHub(
                townKey = "svc_town_eldoret",
                center = LatLng(0.5143, 35.2698),
                entries = listOf(
                    AgrovetMarkerEntry("svc_el1_name", "svc_el1_detail", 0.5143, 35.2698, null),
                    AgrovetMarkerEntry("svc_el2_name", "svc_el2_detail", 0.5160, 35.2715, null)
                )
            ),
            AgrovetTownHub(
                townKey = "svc_town_nairobi",
                center = LatLng(-1.2921, 36.8219),
                entries = listOf(
                    AgrovetMarkerEntry("svc_nb1_name", "svc_nb1_detail", -1.2921, 36.8219, null)
                )
            ),
            AgrovetTownHub(
                townKey = "svc_town_baringo",
                center = LatLng(0.4722, 35.9760),
                entries = listOf(
                    AgrovetMarkerEntry("svc_br1_name", "svc_br1_detail", 0.4722, 35.9760, null),
                    AgrovetMarkerEntry("svc_br2_name", "svc_br2_detail", 0.4700, 35.9740, null)
                )
            )
        )
    }
    // Nearest hub to the live fix; Baringo corridor until a fix resolves.
    val activeHub = remember(userCurrentLatLng, townHubs) {
        val fix = userCurrentLatLng
        if (fix == null) {
            townHubs.first { it.townKey == "svc_town_baringo" }
        } else {
            townHubs.minByOrNull { haversineKm(fix, it.center) } ?: townHubs.last()
        }
    }
    val activeEntries = activeHub.entries

    // 1) Active foreground location loop: 60s cadence, removed on exit.
    DisposableEffect(hasFineLocation) {
        if (!hasFineLocation) return@DisposableEffect onDispose {}
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .setMaxUpdateDelayMillis(120_000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    userCurrentLatLng = LatLng(it.latitude, it.longitude)
                }
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
        }
        onDispose {
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (e: Exception) {
            }
        }
    }
    LaunchedEffect(hasFineLocation) {
        if (!hasFineLocation) {
            try {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } catch (e: Exception) {
            }
        }
    }

    val mapView = remember(context) {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { googleMap ->
                googleMap.uiSettings.isZoomControlsEnabled = true
                // Neutral country viewport until the live GPS fix resolves.
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(0.0236, 37.9062), 6f)
                )
                if (hasFineLocation) {
                    try {
                        googleMap.isMyLocationEnabled = true
                    } catch (e: SecurityException) {
                    }
                }
                mapRef = googleMap
                googleMap.setOnMarkerClickListener { marker ->
                    val idx = (marker.tag as? Int) ?: return@setOnMarkerClickListener false
                    selectedIndex = idx
                    scope.launch { listState.animateScrollToItem(idx) }
                    false
                }
            }
        }
    }
    val lifecycleOwner = remember(context) { context as LifecycleOwner }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            } catch (e: Exception) {
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            try {
                lifecycleOwner.lifecycle.removeObserver(observer)
            } catch (e: Exception) {
            }
            try {
                mapView.onDestroy()
            } catch (e: Exception) {
            }
        }
    }
    // 2) Center on the live position the instant the first fix resolves
    // (once only, so later fixes never yank a panning user).
    LaunchedEffect(userCurrentLatLng) {
        val fix = userCurrentLatLng
        if (fix != null && !centeredOnce) {
            centeredOnce = true
            try {
                mapRef?.animateCamera(CameraUpdateFactory.newLatLngZoom(fix, 14f))
            } catch (e: Exception) {
            }
        }
    }
    // Reset selection whenever the resolved hub changes.
    LaunchedEffect(activeHub) { selectedIndex = 0 }
    // 3) Dynamic proximity plotting: re-plot on map-ready / language / hub change.
    LaunchedEffect(mapRef, mapLang, activeHub) {
        val map = mapRef ?: return@LaunchedEffect
        try {
            map.clear()
            activeEntries.forEachIndexed { i, m ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(m.lat, m.lng))
                        .title(tr(m.nameKey))
                        .snippet(tr(m.detailKey))
                )?.tag = i
            }
        } catch (e: Exception) {
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
        // Floating top back navigation.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBackClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Text("← ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(str("back_home"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (!hasFineLocation) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 68.dp, start = 12.dp, end = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = str("svc_loc_need"),
                        fontSize = 12.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            try {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } catch (e: Exception) {
                            }
                        }
                    ) {
                        Text(str("svc_loc_allow"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }
            }
        }
        // 4) Bottom sheet synced to the plotted hub entries.
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = strf("svc_sub", tr(activeHub.townKey)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(activeEntries) { i, m ->
                        Card(
                            onClick = {
                                selectedIndex = i
                                try {
                                    mapRef?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(m.lat, m.lng), 15f)
                                    )
                                } catch (e: Exception) {
                                }
                                scope.launch { listState.animateScrollToItem(i) }
                            },
                            modifier = Modifier.width(250.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedIndex == i) Color(0xFFFFF8E1) else Color(0xFFF4F6F8)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedIndex == i) Color(0xFFE6B325) else Color(0xFFE0E0E0)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = tr(m.nameKey),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tr(m.detailKey),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (m.phone != null) {
                                    TextButton(onClick = { onDial(m.phone) }) {
                                        Text(
                                            text = strf("svc_call", m.phone),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1B5E20)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = str("svc_walkin"),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = AGROMAP_STAMP,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
