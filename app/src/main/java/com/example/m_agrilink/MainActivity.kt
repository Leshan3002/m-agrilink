package com.example.m_agrilink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import com.example.m_agrilink.ui.AiChatOverlay
import com.example.m_agrilink.ui.TelemetryContext
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * STABILITY-FOCUSED MAIN ACTIVITY
 * Uses standard XML layouts and safe routing logic to prevent crashes.
 */
class MainActivity : AppCompatActivity() {

    // 1. HARDCODED TARGETS
    private val packageKaop = "ke.or.kalro.kaop"
    private val packageKalro = "ke.or.kalro.varietyselector"
    private val packageKamis = "ke.or.kamis"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the XML content view
        setContentView(R.layout.activity_main)

        // 2. SAFELY ATTACH CLICK LISTENERS
        // Using null-safe calls (?.) to ensure stability even if XML IDs are missing
        findViewById<View>(R.id.cardKaop)?.setOnClickListener {
            connectToExternalApp(this, packageKaop)
        }

        findViewById<View>(R.id.cardKalro)?.setOnClickListener {
            connectToExternalApp(this, packageKalro)
        }

        findViewById<View>(R.id.cardKamis)?.setOnClickListener {
            connectToExternalApp(this, packageKamis)
        }

        // TASK 2: TRANSITION CONTROLLER TO NEW REGIONAL MARKET ACTIVITY
        findViewById<View>(R.id.btnOpenNationalAnalyzer)?.setOnClickListener {
            val intent = Intent(this, NationalMarketActivity::class.java)
            startActivity(intent)
        }

        // TASK 3: BOTTOM NAVIGATION ROUTING
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Transition to primary agrometeorological charts layout
                    startActivity(Intent(this, WeatherForecastActivity::class.java))
                    true
                }
                R.id.nav_advisories -> {
                    // Launch context-aware AI overlay dialog block
                    showAiAssistantOverlay()
                    true
                }
                R.id.nav_journey, R.id.nav_articles -> {
                    Toast.makeText(this, "Module under construction", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun showAiAssistantOverlay() {
        // Default telemetry for MainActivity context
        val telemetry = TelemetryContext(
            location = "National Hub",
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

    /**
     * 3. CRASH-PROOF ROUTING NAVIGATION METHOD
     * Safely handles external app launching with Play Store and Browser fallbacks.
     */
    private fun connectToExternalApp(context: Context, packageId: String) {
        val packageManager = context.packageManager

        // Check if the target app is already installed
        val launchIntent = packageManager.getLaunchIntentForPackage(packageId)

        if (launchIntent != null) {
            // App is installed, launch it directly
            context.startActivity(launchIntent)
        } else {
            // App is missing, attempt to open the native Google Play Store
            try {
                val marketUri = Uri.parse("market://details?id=$packageId")
                val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                // 3. Fallback: Open browser if Play Store is unavailable (common on emulators)
                try {
                    val webUri = Uri.parse("https://google.com")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                    context.startActivity(webIntent)
                } catch (e2: Exception) {
                    // Final safety fallback
                    Toast.makeText(context, "Unable to resolve connection", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
