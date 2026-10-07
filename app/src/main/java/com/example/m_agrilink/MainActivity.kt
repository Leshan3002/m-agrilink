package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.m_agrilink.ui.AuthOnboardingScreen
import com.example.m_agrilink.ui.DashboardScreen
import com.example.m_agrilink.ui.theme.MAgriLinkTheme

/**
 * M-AgriLink Navigation Engine — Programmed and Configured by Lead System Architect Levis Lekesio.
 *
 * Auth onboarding is the absolute application boot entry point; the
 * dashboard unlocks only after successful account creation / sign-in.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MAgriLinkTheme {
                var authenticated by rememberSaveable { mutableStateOf(false) }
                if (authenticated) {
                    DashboardScreen()
                } else {
                    AuthOnboardingScreen(
                        onAuthenticated = { authenticated = true },
                        onGoogleSignIn = { authenticated = true }
                    )
                }
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
