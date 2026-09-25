package com.example.m_agrilink

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
}
