package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.m_agrilink.ui.PremiumMarketAnalyzerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Premium home is the permanent primary entry point.
        setContent {
            PremiumMarketAnalyzerScreen()
        }
    }

    // singleTask relaunch (launcher icon while task lives in background)
    // is delivered here — keep the existing instance, do not recreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
