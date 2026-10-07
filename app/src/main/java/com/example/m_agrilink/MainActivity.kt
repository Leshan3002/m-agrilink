package com.example.m_agrilink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.m_agrilink.network.CropLiveLookup
import com.example.m_agrilink.ui.AiScannerScreen
import com.example.m_agrilink.ui.AuthOnboardingScreen
import com.example.m_agrilink.ui.DashboardScreen
import com.example.m_agrilink.ui.GlobalScannerEntry
import com.example.m_agrilink.ui.theme.MAgriLinkTheme

/**
 * M-AgriLink Navigation Engine — Programmed and Configured by Lead System Architect Levis Lekesio.
 *
 * Central layout navigation graph:
 *  Route A "auth_onboarding" -> AuthOnboardingScreen() boot landing page.
 *  Route B "dashboard"        -> DashboardScreen() home hub.
 *  Route C "ai_scanner"       -> AiScannerScreen() diagnostics engine.
 */
object MagriLinkRoutes {
    const val AUTH_ONBOARDING = "auth_onboarding"
    const val DASHBOARD = "dashboard"
    const val AI_SCANNER = "ai_scanner"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MAgriLinkTheme {
                val navController = rememberNavController()
                // Post-registration identity, forwarded thread-safely from the
                // onboarding coroutines. Defaults keep the HUD populated on boot.
                var registeredName by rememberSaveable { mutableStateOf("Leshan Levi") }
                var registeredEmail by rememberSaveable { mutableStateOf("levislekesio@gmail.com") }
                val context = LocalContext.current
                val scannerLookup = remember(context) {
                    CropLiveLookup(context.applicationContext)
                }

                NavHost(
                    navController = navController,
                    startDestination = MagriLinkRoutes.AUTH_ONBOARDING
                ) {
                    composable(MagriLinkRoutes.AUTH_ONBOARDING) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AuthOnboardingScreen(
                                onAuthenticated = { name, email ->
                                    if (name.isNotBlank()) registeredName = name
                                    if (email.isNotBlank()) registeredEmail = email
                                    navController.navigate(MagriLinkRoutes.DASHBOARD) {
                                        popUpTo(MagriLinkRoutes.AUTH_ONBOARDING) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onGoogleSignIn = { name, email ->
                                    if (name.isNotBlank()) registeredName = name
                                    if (email.isNotBlank()) registeredEmail = email
                                    navController.navigate(MagriLinkRoutes.DASHBOARD) {
                                        popUpTo(MagriLinkRoutes.AUTH_ONBOARDING) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                            // Persistent pre-auth scanner bubble (the dashboard
                            // hosts its own context-aware FAB once signed in).
                            GlobalScannerEntry(
                                onOpenScanner = {
                                    navController.navigate(MagriLinkRoutes.AI_SCANNER)
                                }
                            )
                        }
                    }
                    composable(MagriLinkRoutes.DASHBOARD) {
                        DashboardScreen(
                            displayName = registeredName,
                            displayEmail = registeredEmail
                        )
                    }
                    composable(MagriLinkRoutes.AI_SCANNER) {
                        AiScannerScreen(
                            selectedCounty = "Kenya",
                            farmerPlantedCrop = "",
                            textToSpeech = null,
                            liveLookup = scannerLookup,
                            onOpenWebLink = { url ->
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                } catch (e: Exception) {
                                }
                            },
                            onClose = { navController.popBackStack() },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
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
