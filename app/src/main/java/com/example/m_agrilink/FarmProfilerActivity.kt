package com.example.m_agrilink

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MODULE 2: RE-ENGINEERED OPENSTREETMAP POLYGON DRAWING CONTROLLER
 * Maps farm boundaries natively using osmdroid and manual spherical area math.
 */
class FarmProfilerActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var txtAcreage: TextView
    private lateinit var txtSoilClass: TextView
    private lateinit var txtMoisture: TextView

    private val farmPerimeterPoints = ArrayList<GeoPoint>()
    private var activePolygon: Polygon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. UserAgent policy check (Run BEFORE layout inflation)
        val ctx = applicationContext
        Configuration.getInstance().userAgentValue = "com.example.m_agrilink.farmprofiler"
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        setContentView(R.layout.activity_farm_profiler)

        txtAcreage = findViewById(R.id.txtComputedAcreage)
        txtSoilClass = findViewById(R.id.txtSoilClassificationDetails)
        txtMoisture = findViewById(R.id.txtVolumetricSoilMoisture)
        val btnClearPlot = findViewById<Button>(R.id.btnClearPolygonPins)

        initializeOsmMappingEngine()

        btnClearPlot.setOnClickListener {
            clearMapData()
        }
    }

    private fun initializeOsmMappingEngine() {
        map = findViewById(R.id.farmOsmMapView)
        map.setMultiTouchControls(true)

        val mapController = map.controller
        mapController.setZoom(16.5)
        val startPoint = GeoPoint(0.4857, 35.7412) // Baringo Hub
        mapController.setCenter(startPoint)

        // 2. Implement Map overlay click listener framework
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                dropMarkerAndDrawPolygon(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }

        val overlay = MapEventsOverlay(eventsReceiver)
        map.overlays.add(overlay)
    }

    private fun dropMarkerAndDrawPolygon(point: GeoPoint) {
        // 1. Drop visual marker pin
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Corner #${farmPerimeterPoints.size + 1}"
        map.overlays.add(marker)

        farmPerimeterPoints.add(point)

        // 2. Draw active bounding polygon
        if (activePolygon == null) {
            activePolygon = Polygon(map)
            activePolygon?.fillPaint?.color = Color.parseColor("#33A75D5D") // Semi-transparent terracotta
            activePolygon?.outlinePaint?.color = Color.parseColor("#A75D5D")
            activePolygon?.outlinePaint?.strokeWidth = 5f
            map.overlays.add(activePolygon)
        }

        activePolygon?.points = farmPerimeterPoints
        map.invalidate()

        // 3. Run Acreage Calculation
        if (farmPerimeterPoints.size >= 3) {
            lifecycleScope.launch {
                val areaSqM = calculateSphericalArea(farmPerimeterPoints)
                val acreage = areaSqM * 0.000247105
                withContext(Dispatchers.Main) {
                    txtAcreage.text = "📐 Farm Size: %.2f Acres".format(acreage)
                }
            }
        }
    }

    private fun clearMapData() {
        farmPerimeterPoints.clear()
        map.overlays.filterIsInstance<Marker>().forEach { map.overlays.remove(it) }
        activePolygon?.let { map.overlays.remove(it) }
        activePolygon = null
        txtAcreage.text = "📐 Farm Size: 0.00 Acres"
        map.invalidate()
    }

    /**
     * Manual Spherical Area Calculation (Approximation)
     */
    private suspend fun calculateSphericalArea(points: List<GeoPoint>): Double = withContext(Dispatchers.Default) {
        if (points.size < 3) return@withContext 0.0
        val radius = 6378137.0 // Earth radius in meters
        var area = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            area += Math.toRadians(p2.longitude - p1.longitude) *
                    (2 + sin(Math.toRadians(p1.latitude)) + sin(Math.toRadians(p2.latitude)))
        }
        abs(area * radius * radius / 2.0)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
