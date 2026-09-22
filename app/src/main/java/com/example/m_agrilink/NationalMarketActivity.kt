package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * TASK 1-4: EXPANDED AND OPTIMIZED NATIONAL MARKET CORRIDOR ENGINE
 * Production-grade components patched to display expanded counties and base price metrics.
 */
class NationalMarketActivity : AppCompatActivity() {

    // ISSUE 1 PATCH: Expanded nested crop map database containing all 47 real-world Kenyan Counties
    private val countyCropMatrix = mapOf(
        "Mombasa" to listOf("Maize", "Beans", "Onions", "Cassava", "Coconuts", "Mangoes"),
        "Kwale" to listOf("Maize", "Beans", "Coconuts", "Sugarcane", "Mangoes"),
        "Kilifi" to listOf("Maize", "Onions", "Coconuts", "Cashew Nuts", "Sisal"),
        "Tana River" to listOf("Rice", "Mangoes", "Maize", "Bananas", "Cowpeas"),
        "Lamu" to listOf("Mangoes", "Coconuts", "Cashew Nuts", "Cotton", "Simsim"),
        "Taita Taveta" to listOf("Sisal", "Maize", "Beans", "Bananas", "Onions"),
        "Garissa" to listOf("Watermelon", "Bananas", "Mangoes", "Cowpeas", "Sorghum"),
        "Wajir" to listOf("Sorghum", "Millet", "Cowpeas", "Green Grams"),
        "Mandera" to listOf("Watermelon", "Sorghum", "Cowpeas", "Onions", "Millet"),
        "Marsabit" to listOf("Sorghum", "Millet", "Beans", "Maize"),
        "Isiolo" to listOf("Sorghum", "Maize", "Cowpeas", "Onions"),
        "Meru" to listOf("Tea", "Coffee", "Bananas", "Potatoes", "Maize", "Beans"),
        "Tharaka-Nithi" to listOf("Green Grams", "Sorghum", "Millet", "Maize"),
        "Embu" to listOf("Tea", "Coffee", "Macadamia", "Maize", "Beans", "Avocados"),
        "Kitui" to listOf("Green Grams", "Sorghum", "Millet", "Cowpeas", "Maize"),
        "Machakos" to listOf("Maize", "Beans", "Green Grams", "Sorghum", "Mangoes"),
        "Makueni" to listOf("Mangoes", "Green Grams", "Sorghum", "Millet", "Maize"),
        "Nyandarua" to listOf("Potatoes", "Cabbages", "Carrots", "Wheat", "Maize"),
        "Nyeri" to listOf("Tea", "Coffee", "Maize", "Beans", "Potatoes"),
        "Kirinyaga" to listOf("Rice", "Tea", "Coffee", "Tomatoes", "Maize"),
        "Murang'a" to listOf("Tea", "Coffee", "Avocados", "Maize", "Beans"),
        "Kiambu" to listOf("Tea", "Coffee", "Maize", "Beans", "Pineapples"),
        "Turkana" to listOf("Sorghum", "Millet", "Cowpeas", "Maize"),
        "West Pokot" to listOf("Maize", "Sorghum", "Millet", "Beans"),
        "Samburu" to listOf("Maize", "Sorghum", "Millet", "Beans"),
        "Trans Nzoia" to listOf("Maize", "Seed Maize", "Wheat", "Beans"),
        "Uasin Gishu" to listOf("Maize", "Wheat", "Beans", "Passion Fruit", "Barley"),
        "Elgeyo-Marakwet" to listOf("Potatoes", "Maize", "Wheat", "Pyrethrum"),
        "Nandi" to listOf("Tea", "Maize", "Sugarcane", "Coffee", "Beans"),
        "Baringo" to listOf("Maize", "Beans", "Onions", "Sorghum", "Millet", "Green Grams", "Cotton"),
        "Laikipia" to listOf("Maize", "Wheat", "Beans", "Potatoes"),
        "Nakuru" to listOf("Maize", "Potatoes", "Wheat", "Pyrethrum", "Barley"),
        "Narok" to listOf("Wheat", "Maize", "Barley", "Potatoes"),
        "Kajiado" to listOf("Maize", "Beans", "Tomatoes", "Onions"),
        "Kericho" to listOf("Tea", "Maize", "Sugarcane", "Coffee"),
        "Bomet" to listOf("Tea", "Maize", "Potatoes", "Coffee"),
        "Kakamega" to listOf("Sugarcane", "Maize", "Beans", "Cassava"),
        "Vihiga" to listOf("Tea", "Maize", "Beans", "Kales"),
        "Bungoma" to listOf("Sugarcane", "Maize", "Beans", "Coffee"),
        "Busia" to listOf("Sugarcane", "Cassava", "Maize", "Beans", "Finger Millet"),
        "Siaya" to listOf("Maize", "Beans", "Cassava", "Sorghum"),
        "Kisumu" to listOf("Sugarcane", "Rice", "Maize", "Beans", "Sorghum"),
        "Homa Bay" to listOf("Maize", "Sorghum", "Millet", "Cassava", "Cotton"),
        "Migori" to listOf("Tobacco", "Sugarcane", "Maize", "Beans", "Sorghum"),
        "Kisii" to listOf("Tea", "Coffee", "Bananas", "Maize", "Beans"),
        "Nyamira" to listOf("Tea", "Coffee", "Bananas", "Maize", "Potatoes"),
        "Nairobi" to listOf("Maize", "Beans", "Onions", "Kale (Sukuma Wiki)", "Tomatoes", "Mushrooms")
    )

    // Dynamic database lookup matrix mapping for base prices simulation (Issue 2)
    private val cropBasePricesMatrix = mapOf(
        "Maize" to Pair("KES 4,200", "KES 4,800"),
        "Beans" to Pair("KES 11,500", "KES 13,000"),
        "Onions" to Pair("KES 3,600", "KES 4,500"),
        "Sorghum" to Pair("KES 3,200", "KES 4,100"),
        "Millet" to Pair("KES 5,500", "KES 6,800"),
        "Green Grams" to Pair("KES 7,800", "KES 9,200"),
        "Cotton" to Pair("KES 60 / kg", "KES 85 / kg")
    )

    private val agronomicTrainingMatrix = mapOf(
        "Maize" to "🌱 Land Prep: Plough early up to 20cm deep.\n🚜 Planting: Space rows 75cm x 25cm.\n🪳 Management: Scout weekly for Armyworms.\n🌾 Harvesting: Collect under 13.5% moisture.",
        "Beans" to "🌱 Land Prep: Medium clod tilth.\n🚜 Planting: Space rows 45cm x 15cm.\n🪳 Management: Spray rust fungicide safely.\n🌾 Harvesting: Collect when pods turn brown.",
        "Onions" to "🌱 Land Prep: Highly refined nursery beds.\n🚜 Planting: Transplant at 30cm row width lines.\n🪳 Management: Consistent drip tracking.\n🌾 Harvesting: Harvest when tops collapse downwards."
    )

    private var selectedCounty: String = "Baringo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_national_market)

        val countySelectorDropdown = findViewById<AutoCompleteTextView>(R.id.countySelectorDropdown)
        val cropYieldListView = findViewById<ListView>(R.id.cropYieldListView)
        val txtMarketAnalyticsOutput = findViewById<TextView>(R.id.txtMarketAnalyticsOutput)
        val btnOpenWeatherForecast = findViewById<Button>(R.id.btnOpenWeatherForecast)
        val btnOpenGoogleMapsAdvisory = findViewById<Button>(R.id.btnOpenGoogleMapsAdvisory)

        val countyList = countyCropMatrix.keys.toList().sorted()
        val countyAdapter = ArrayAdapter(this, R.layout.item_county_dropdown, countyList)
        countySelectorDropdown.setAdapter(countyAdapter)

        countySelectorDropdown.setOnItemClickListener { parent, _, position, _ ->
            val county = parent.getItemAtPosition(position) as String
            selectedCounty = county
            val matchingCrops = countyCropMatrix[county] ?: emptyList()
            val cropAdapter = ArrayAdapter(this, R.layout.item_crop_row, matchingCrops)
            cropYieldListView.adapter = cropAdapter
            txtMarketAnalyticsOutput.text = "Selected County Corridor: $county.\nTap a crop above to compile growth advisory instructions."
        }

        // ISSUE 2 FIX: Update click listener to populate exact base and destination price parameters dynamically
        cropYieldListView.setOnItemClickListener { parent, _, position, _ ->
            val clickedCrop = parent.getItemAtPosition(position) as String
            val advisoryText = agronomicTrainingMatrix[clickedCrop]
            val basePrices = cropBasePricesMatrix[clickedCrop]

            txtMarketAnalyticsOutput.text = buildString {
                append("📊 $clickedCrop Local Profile ($selectedCounty County)\n")
                append("──────────────────────────────────────────\n")
                if (basePrices != null) {
                    append("• Base Local Price:          ${basePrices.first} / 90kg bag\n")
                    append("• Target Destination Price:  ${basePrices.second}\n")
                    append("──────────────────────────────────────────\n")
                }
                if (advisoryText != null) {
                    append(advisoryText)
                } else {
                    append("🌱 Land Prep: Sieve fine tilth layout before seasonal rainfall.\n")
                    append("🚜 Planting: Align depth parameters precisely with seed variety sizing.\n")
                    append("🪳 Management: Monitor for common local crop pathogens.\n")
                    append("🌾 Harvesting: Collect at optimal dry maturity indexes.")
                }
            }
        }

        // REDIRECT REPLACEMENTS TO FULLY INTEGRATED IN-APP PAGES
        btnOpenWeatherForecast.setOnClickListener {
            val intent = Intent(this, WeatherForecastActivity::class.java).apply {
                putExtra("EXTRA_LATITUDE", if (selectedCounty == "Baringo") 0.4857 else -1.2921)
                putExtra("EXTRA_LONGITUDE", if (selectedCounty == "Baringo") 35.7412 else 36.8219)
            }
            startActivity(intent)
        }

        btnOpenGoogleMapsAdvisory.setOnClickListener {
            val intent = Intent(this, FarmProfilerActivity::class.java)
            startActivity(intent)
        }
    }
}
