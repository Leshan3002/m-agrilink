package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.m_agrilink.data.local.SessionManager
import com.example.m_agrilink.ui.AuthOnboardingScreen
import com.example.m_agrilink.ui.DashboardScreen
import com.example.m_agrilink.ui.theme.MAgriLinkTheme
import kotlinx.coroutines.launch

/**
 * M-AgriLink Navigation Engine.
 *
 * Central layout navigation graph:
 *  Route A "auth_onboarding" -> AuthOnboardingScreen() boot landing page.
 *  Route B "dashboard"        -> DashboardScreen() home hub.
 *
 * Boot bypass: the DataStore login Flow is read asynchronously — when
 * `is_logged_in` is true the onboarding route is skipped and the dashboard
 * loads immediately with the persisted profile.
 */
object MagriLinkRoutes {
    const val AUTH_ONBOARDING = "auth_onboarding"
    const val DASHBOARD = "dashboard"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MAgriLinkTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val sessionManager = remember(context) { SessionManager(context) }
                val savedLogin by sessionManager.isLoggedIn
                    .collectAsState(initial = null)
                val savedName by sessionManager.userName
                    .collectAsState(initial = "")
                val savedEmail by sessionManager.userEmail
                    .collectAsState(initial = "")

                // Post-registration identity, forwarded thread-safely from the
                // onboarding coroutines. Defaults keep the HUD populated on boot.
                var registeredName by rememberSaveable { mutableStateOf("Leshan Levi") }
                var registeredEmail by rememberSaveable { mutableStateOf("levislekesio@gmail.com") }

                // Hydrate the HUD from the persisted session on boot.
                LaunchedEffect(savedName) {
                    if (savedName.isNotBlank()) registeredName = savedName
                }
                LaunchedEffect(savedEmail) {
                    if (savedEmail.isNotBlank()) registeredEmail = savedEmail
                }

                fun persistAndEnter(name: String, email: String) {
                    if (name.isNotBlank()) registeredName = name
                    if (email.isNotBlank()) registeredEmail = email
                    scope.launch {
                        sessionManager.saveSession(registeredName, registeredEmail)
                    }
                    navController.navigate(MagriLinkRoutes.DASHBOARD) {
                        popUpTo(MagriLinkRoutes.AUTH_ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                fun logOut() {
                    scope.launch {
                        sessionManager.clearSession()
                    }
                    registeredName = "Leshan Levi"
                    registeredEmail = "levislekesio@gmail.com"
                    navController.navigate(MagriLinkRoutes.AUTH_ONBOARDING) {
                        popUpTo(MagriLinkRoutes.DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                if (savedLogin == null) {
                    // Session still loading — hold a blank frame, never flash onboarding.
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = if (savedLogin == true) {
                            MagriLinkRoutes.DASHBOARD
                        } else {
                            MagriLinkRoutes.AUTH_ONBOARDING
                        }
                    ) {
                        composable(MagriLinkRoutes.AUTH_ONBOARDING) {
                            AuthOnboardingScreen(
                                onAuthenticated = ::persistAndEnter,
                                onGoogleSignIn = ::persistAndEnter
                            )
                        }
                        composable(MagriLinkRoutes.DASHBOARD) {
                            DashboardScreen(
                                displayName = registeredName,
                                displayEmail = registeredEmail,
                                onLogout = ::logOut
                            )
                        }
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
