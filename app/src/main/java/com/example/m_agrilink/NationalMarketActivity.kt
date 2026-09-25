package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.m_agrilink.ui.AiChatOverlay
import com.example.m_agrilink.ui.TelemetryContext
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.util.*

/**
 * PRODUCTION-GRADE NATIONAL MARKET CORRIDOR ANALYZER
 * Features: 47-County matrix, Dynamic Arbitrage Engine, and Agronomic Advisories.
 */
class NationalMarketActivity : AppCompatActivity() {

    private val transportOverhead = 350.0 // KES per 90kg bag

    // Complete 47 Kenyan Counties Mapping
    private val countyCropMatrix = mapOf(
        "Mombasa" to listOf("Maize", "Beans", "Onions", "Cassava"),
        "Kwale" to listOf("Maize", "Beans", "Coconuts"),
        "Kilifi" to listOf("Maize", "Onions", "Coconuts"),
        "Tana River" to listOf("Rice", "Mangoes", "Maize"),
        "Lamu" to listOf("Mangoes", "Coconuts"),
        "Taita Taveta" to listOf("Sisal", "Maize", "Beans"),
        "Garissa" to listOf("Watermelon", "Bananas", "Maize"),
        "Wajir" to listOf("Sorghum", "Millet", "Cowpeas"),
        "Mandera" to listOf("Watermelon", "Sorghum"),
        "Marsabit" to listOf("Sorghum", "Millet"),
        "Isiolo" to listOf("Sorghum", "Maize"),
        "Meru" to listOf("Tea", "Coffee", "Bananas", "Maize"),
        "Tharaka-Nithi" to listOf("Green Grams", "Sorghum"),
        "Embu" to listOf("Tea", "Coffee", "Macadamia", "Maize"),
        "Kitui" to listOf("Green Grams", "Sorghum", "Millet"),
        "Machakos" to listOf("Maize", "Beans", "Green Grams"),
        "Makueni" to listOf("Mangoes", "Green Grams"),
        "Nyandarua" to listOf("Potatoes", "Cabbages", "Wheat"),
        "Nyeri" to listOf("Tea", "Coffee", "Maize"),
        "Kirinyaga" to listOf("Rice", "Tea", "Coffee"),
        "Murang'a" to listOf("Tea", "Coffee", "Avocados"),
        "Kiambu" to listOf("Tea", "Coffee", "Maize"),
        "Turkana" to listOf("Sorghum", "Millet"),
        "West Pokot" to listOf("Maize", "Sorghum"),
        "Samburu" to listOf("Maize", "Sorghum"),
        "Trans Nzoia" to listOf("Maize", "Wheat", "Beans"),
        "Uasin Gishu" to listOf("Maize", "Wheat", "Beans"),
        "Elgeyo-Marakwet" to listOf("Potatoes", "Maize", "Wheat"),
        "Nandi" to listOf("Tea", "Maize", "Sugarcane"),
        "Baringo" to listOf("Maize", "Beans", "Onions", "Sorghum", "Millet", "Green Grams", "Cotton"),
        "Laikipia" to listOf("Maize", "Wheat", "Beans"),
        "Nakuru" to listOf("Maize", "Potatoes", "Wheat"),
        "Narok" to listOf("Wheat", "Maize", "Barley"),
        "Kajiado" to listOf("Maize", "Beans", "Tomatoes"),
        "Kericho" to listOf("Tea", "Maize", "Sugarcane"),
        "Bomet" to listOf("Tea", "Maize", "Potatoes"),
        "Kakamega" to listOf("Sugarcane", "Maize", "Beans"),
        "Vihiga" to listOf("Tea", "Maize", "Beans"),
        "Bungoma" to listOf("Sugarcane", "Maize", "Beans"),
        "Busia" to listOf("Sugarcane", "Cassava", "Maize"),
        "Siaya" to listOf("Maize", "Beans", "Cassava"),
        "Kisumu" to listOf("Sugarcane", "Rice", "Maize"),
        "Homa Bay" to listOf("Maize", "Sorghum", "Millet"),
        "Migori" to listOf("Tobacco", "Sugarcane", "Maize"),
        "Kisii" to listOf("Tea", "Coffee", "Bananas"),
        "Nyamira" to listOf("Tea", "Coffee", "Bananas"),
        "Nairobi" to listOf("Maize", "Beans", "Onions", "Kale")
    )

    private val cropPriceMatrix = mapOf(
        "Maize" to Pair(4200.0, 4800.0),
        "Beans" to Pair(11500.0, 13000.0),
        "Onions" to Pair(3600.0, 4500.0),
        "Sorghum" to Pair(3200.0, 4100.0),
        "Millet" to Pair(5500.0, 6800.0),
        "Green Grams" to Pair(7800.0, 9200.0),
        "Cotton" to Pair(60.0, 85.0)
    )

    private val agronomicAdvisory = mapOf(
        "Maize" to "🌱 Land Prep: Plough early up to 20cm deep.\n🚜 Planting: Space rows 75cm x 25cm.\n🪳 Management: Scout weekly for Armyworms.\n🌾 Harvesting: Collect under 13.5% moisture.",
        "Beans" to "🌱 Land Prep: Medium clod tilth.\n🚜 Planting: Space rows 45cm x 15cm.\n🪳 Management: Spray rust fungicide safely.\n🌾 Harvesting: Collect when pods turn brown.",
        "Green Grams" to "🌱 Land Prep: Fine seedbed.\n🚜 Planting: 45cm x 15cm.\n🪳 Management: Control aphids early.\n🌾 Harvesting: When pods turn black and dry."
    )

    private var selectedCounty: String = "Baringo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_national_market)

        val countySelector = findViewById<AutoCompleteTextView>(R.id.selectCountyHub)
        val cropListView = findViewById<ListView>(R.id.cropYieldListView)
        val outputText = findViewById<TextView>(R.id.txtMarketAnalyticsOutput)
        val btnLaunchWeather = findViewById<MaterialButton>(R.id.btnLaunchRegionalAnalyzer)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // 1. Initialize County Dropdown
        val countyList = countyCropMatrix.keys.toList().sorted()
        val countyAdapter = ArrayAdapter(this, R.layout.item_county_dropdown, countyList)
        countySelector.setAdapter(countyAdapter)

        countySelector.setOnItemClickListener { parent, _, position, _ ->
            selectedCounty = parent.getItemAtPosition(position) as String
            val crops = countyCropMatrix[selectedCounty] ?: emptyList()
            cropListView.adapter = ArrayAdapter(this, R.layout.item_crop_row, crops)
            outputText.text = "📊 Selected: $selectedCounty County.\nTap a crop above to analyze arbitrage margins."
        }

        // 2. Arbitrage Calculation Engine
        cropListView.setOnItemClickListener { parent, _, position, _ ->
            val crop = parent.getItemAtPosition(position) as String
            calculateArbitrage(crop, outputText)
        }

        // 3. Routing Logic
        btnLaunchWeather.setOnClickListener {
            val intent = Intent(this, WeatherForecastActivity::class.java).apply {
                putExtra("EXTRA_LATITUDE", if (selectedCounty == "Baringo") 0.4857 else -1.2921)
                putExtra("EXTRA_LONGITUDE", if (selectedCounty == "Baringo") 35.7412 else 36.8219)
            }
            startActivity(intent)
        }

        // 4. Bottom Navigation Wiring
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, WeatherForecastActivity::class.java))
                    true
                }
                R.id.nav_advisories -> {
                    showAiAssistantOverlay()
                    true
                }
                else -> {
                    Toast.makeText(this, "Module Under Construction", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }

    private fun calculateArbitrage(crop: String, output: TextView) {
        val prices = cropPriceMatrix[crop]
        val advisory = agronomicAdvisory[crop] ?: "🌱 Land Prep: Fine tilth.\n🚜 Planting: Precise depth.\n🪳 Management: Daily monitoring.\n🌾 Harvesting: Optimal maturity."

        if (prices != null) {
            val (base, target) = prices
            val grossProfit = target - base
            val netProfit = grossProfit - transportOverhead
            
            val recommendation = if (netProfit > 500) "✅ Highly Viable Corridor" else "⚠️ Low Margin Warning"

            output.text = buildString {
                append("💰 Arbitrage Index: $crop\n")
                append("───────────────────────\n")
                append("• Local Price: KES $base\n")
                append("• Target Hub: KES $target\n")
                append("• Transit Fee: KES $transportOverhead\n")
                append("• Net Profit:  KES $netProfit / bag\n")
                append("💡 Insight: $recommendation\n\n")
                append("📖 Step-by-Step Advisory:\n")
                append(advisory)
            }
        } else {
            output.text = "Advisory for $crop:\n\n$advisory"
        }
    }

    private fun showAiAssistantOverlay() {
        val telemetry = TelemetryContext(
            location = selectedCounty,
            temp = "24°C",
            rainProb = "10%",
            wind = "12 km/h",
            humidity = "60%",
            cropVariety = "Mixed Commodities",
            maizePrice = "KES 4,500"
        )
        
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
}
