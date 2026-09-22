package com.example.m_agrilink

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.example.m_agrilink.data.AppDatabase
import com.example.m_agrilink.ui.AiChatOverlay
import com.example.m_agrilink.ui.TelemetryContext
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 2. IN-APP WEATHER FORECAST PAGE & AI ASSISTANT HUB
 * Integrated context gathering from live network and local Room cache.
 */
class WeatherForecastActivity : AppCompatActivity() {

    private val TAG = "WeatherForecastLive"
    private var currentTelemetry: TelemetryContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_forecast)

        val txtHeader = findViewById<TextView>(R.id.txtWeatherCoordinatesHeader)
        val txtOverview = findViewById<TextView>(R.id.txtWeatherCurrentOverview)
        val tvWind = findViewById<TextView>(R.id.tvWindStatus)
        val tvHumidity = findViewById<TextView>(R.id.tvHumidityStatus)
        val fabAi = findViewById<FloatingActionButton>(R.id.fabAiAssistant)

        val latParam = intent.getDoubleExtra("EXTRA_LATITUDE", 0.4857)
        val lonParam = intent.getDoubleExtra("EXTRA_LONGITUDE", 35.7412)

        txtHeader.text = "Location Hub: Coordinates [$latParam, $lonParam]"

        // 1. Initial Data Load & Network Sync
        lifecycleScope.launch {
            try {
                val response = fetchLiveMeteorologicalData(latParam, lonParam)
                if (response != null) {
                    val weatherSummary = "Temp: ${response.hourly.temperature_2m.firstOrNull()}°C, Rain: ${response.hourly.precipitation_probability.firstOrNull()}%"
                    txtOverview.text = "🌍 Live Sync: $weatherSummary"
                    
                    // Extract and Update Wind/Humidity Metrics
                    val currentWindSpeed = response.current_weather?.windspeed ?: 10.3
                    val currentHumidity = response.hourly.relativehumidity_2m.firstOrNull() ?: 62

                    // Cache context for AI module
                    currentTelemetry = TelemetryContext(
                        location = "Baringo",
                        temp = "${response.hourly.temperature_2m.firstOrNull()}°C",
                        rainProb = "${response.hourly.precipitation_probability.firstOrNull()}%",
                        wind = "$currentWindSpeed km/h",
                        humidity = "$currentHumidity%",
                        cropVariety = "Maize variety Katumani",
                        maizePrice = "KES 4,200" // Default fallback
                    )

                    tvWind.text = "Wind: NE Vector at $currentWindSpeed km/h"
                    tvHumidity.text = "Humidity: $currentHumidity% stable"

                    populateHourlyWeatherGraph(response.hourly.temperature_2m)
                    populateRainProbabilityGraph(response.hourly.precipitation_probability)

                    // 2. Room Integration: Pull cached prices to refine AI context
                    updateAitTelemetryWithCache()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network Sync Error: ${e.message}")
            }
        }

        // 3. MODULE 3: AI ASSISTANT OVERLAY TRIGGER
        fabAi.setOnClickListener {
            showAiAssistantOverlay()
        }
    }

    private suspend fun updateAitTelemetryWithCache() = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val cachedMaize = db.marketPriceDao().getCachedPrice("Maize")
        if (cachedMaize != null) {
            currentTelemetry = currentTelemetry?.copy(maizePrice = "KES ${cachedMaize.localHubPrice}")
        }
    }

    private fun showAiAssistantOverlay() {
        val telemetry = currentTelemetry ?: TelemetryContext("Loading...", "N/A", "N/A", "N/A", "N/A", "Unknown", "N/A")
        
        val bottomSheetDialog = BottomSheetDialog(this)
        val composeView = ComposeView(this).apply {
            setContent {
                AiChatOverlay(
                    telemetry = telemetry,
                    onDismiss = { bottomSheetDialog.dismiss() }
                )
            }
        }
        bottomSheetDialog.setContentView(composeView)
        bottomSheetDialog.show()
    }

    private suspend fun fetchLiveMeteorologicalData(lat: Double, lon: Double): WeatherResponseLive? = withContext(Dispatchers.IO) {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service = retrofit.create(OpenMeteoApiService::class.java)
            service.getLiveForecast(lat, lon)
        } catch (e: Exception) { null }
    }

    private fun populateHourlyWeatherGraph(tempData: List<Double>) {
        val lineChart = findViewById<LineChart>(R.id.weatherLineChart) ?: return
        val entries = tempData.take(24).mapIndexed { i, d -> Entry(i.toFloat(), d.toFloat()) }
        val dataSet = LineDataSet(entries, "Temp").apply {
            color = Color.parseColor("#A75D5D")
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#A75D5D")
            valueTextColor = Color.WHITE
        }
        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
    }

    private fun populateRainProbabilityGraph(rainData: List<Int>) {
        val barChart = findViewById<BarChart>(R.id.rainBarChart) ?: return
        val entries = rainData.take(5).mapIndexed { i, r -> BarEntry(i.toFloat(), r.toFloat()) }
        val dataSet = BarDataSet(entries, "Rain %").apply { color = Color.parseColor("#E6B325") }

        // Fix: Explicitly map X-axis indices to Day Strings
        val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri")
        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(days)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            setDrawGridLines(false)
            textColor = Color.WHITE
        }

        barChart.data = BarData(dataSet)
        barChart.invalidate()
    }
}
