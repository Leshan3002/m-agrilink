package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.m_agrilink.ui.AuthOnboardingScreen
import com.example.m_agrilink.ui.DashboardScreen
import com.example.m_agrilink.ui.GlobalScannerEntry
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
                // Post-registration identity, forwarded thread-safely from the
                // onboarding coroutines. Defaults keep the HUD populated on boot.
                var registeredName by rememberSaveable { mutableStateOf("Leshan Levi") }
                var registeredEmail by rememberSaveable { mutableStateOf("levislekesio@gmail.com") }
                if (authenticated) {
                    DashboardScreen(
                        displayName = registeredName,
                        displayEmail = registeredEmail
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AuthOnboardingScreen(
                            onAuthenticated = { name, email ->
                                if (name.isNotBlank()) registeredName = name
                                if (email.isNotBlank()) registeredEmail = email
                                authenticated = true
                            },
                            onGoogleSignIn = { name, email ->
                                if (name.isNotBlank()) registeredName = name
                                if (email.isNotBlank()) registeredEmail = email
                                authenticated = true
                            }
                        )
                        // Persistent pre-auth scanner bubble (the dashboard
                        // hosts its own context-aware FAB once signed in).
                        GlobalScannerEntry()
                    }
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
