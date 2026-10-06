package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.m_agrilink.ui.DriverRegistrationScreen
import com.example.m_agrilink.ui.PremiumMarketAnalyzerScreen

/**
 * M-AgriLink Navigation Engine — Programmed and Configured by Lead System Architect Levis Lekesio.
 *
 * Central navigation framework: a lightweight conditional state router
 * (no external NavHost dependency, so the offline build stays intact).
 * Route "dashboard" hosts the core home component
 * ([PremiumMarketAnalyzerScreen], defined in DashboardScreen.kt); route
 * "transporter_registration" hosts the secure driver onboarding wizard.
 * Route state is saveable, so they keep their place across rotation and
 * process death, and back navigation is a plain state write on the main
 * thread — snapshot-safe, no freezing.
 */
const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_TRANSPORTER_REGISTRATION = "transporter_registration"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Premium home is the permanent primary entry point.
        setContent {
            var currentRoute by rememberSaveable { mutableStateOf(ROUTE_DASHBOARD) }
            when (currentRoute) {
                ROUTE_TRANSPORTER_REGISTRATION -> DriverRegistrationScreen(
                    onBackClick = { currentRoute = ROUTE_DASHBOARD }
                )
                else -> PremiumMarketAnalyzerScreen(
                    onOpenTransporterRegistration = {
                        currentRoute = ROUTE_TRANSPORTER_REGISTRATION
                    }
                )
            }
        }
    }

    // singleTask relaunch (launcher icon while task lives in background)
    // is delivered here — keep the existing instance, do not recreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
