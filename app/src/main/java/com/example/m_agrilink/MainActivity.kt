package com.example.m_agrilink

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.m_agrilink.data.local.SessionManager
import com.example.m_agrilink.ui.AboutUsScreen
import com.example.m_agrilink.ui.AiScannerScreen
import com.example.m_agrilink.ui.AskShambaChatScreen
import com.example.m_agrilink.ui.AuthOnboardingScreen
import com.example.m_agrilink.ui.DashboardScreen
import com.example.m_agrilink.ui.theme.MAgriLinkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M-AgriLink Navigation Engine.
 *
 * Central layout navigation graph:
 *  Route A "auth_onboarding" -> AuthOnboardingScreen() boot landing page.
 *  Route B "dashboard"        -> DashboardScreen() home hub.
 *  Route C "ai_scanner"       -> AiScannerScreen(navController) scanner shell.
 *  Route D "ask_shamba_chat"  -> AskShambaChatScreen(navController) chat module.
 *
 * Boot bypass: the DataStore login Flow is read asynchronously — when
 * `is_logged_in` is true the onboarding route is skipped and the dashboard
 * loads immediately with the persisted profile.
 */
object MagriLinkRoutes {
    const val AUTH_ONBOARDING = "auth_onboarding"
    const val DASHBOARD = "dashboard"
    const val AI_SCANNER = "ai_scanner"
    const val ASK_SHAMBA_CHAT = "ask_shamba_chat"
    const val ABOUT_US = "about_us"
}

/**
 * Central Kenyan-accented TTS binding shared by all screens.
 * English mode binds Kenyan English; Kiswahili mode binds sw-KE.
 * Slow 0.82f pace with 0.95f pitch models natural human speech so they can
 * follow dense field data comfortably.
 * No decorative icons or personal names; background logs use they/their/them.
 */
fun bindKenyanTtsLocale(tts: TextToSpeech, isSwahili: Boolean) {
    try {
        if (isSwahili) {
            tts.setLanguage(Locale("sw", "KE"))
        } else {
            val result = tts.setLanguage(Locale("en", "KE"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.US)
            }
        }
        tts.setSpeechRate(0.82f)
        tts.setPitch(0.95f)
    } catch (e: Exception) {
    }
}

/**
 * Shared horizontal media controller hub: text-only Play / Pause / Stop row.
 * Pause calls tts.stop() but keeps a resume offset so they do not lose their
 * place; Stop purges the speech thread and resets playback flags.
 */
@Composable
fun KenyanTtsMediaHub(
    textToSpeech: TextToSpeech?,
    textToRead: String,
    isSwahili: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var resumeOffset by remember(textToRead) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val readable = remember(textToRead) {
        textToRead.replace(Regex("[\\p{So}\\p{Sk}•●▲▼★☆→➔*]+"), " ").replace(Regex("\\s+"), " ").trim()
    }
    val playable = remember(readable, resumeOffset) {
        if (resumeOffset in 1 until readable.length) readable.drop(resumeOffset) else readable
    }
    DisposableEffect(textToSpeech) {
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) {
                isPlaying = false
                isPaused = false
                resumeOffset = 0
            }
            override fun onError(utteranceId: String?) {
                isPlaying = false
                isPaused = false
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                resumeOffset = start
            }
        }
        try {
            textToSpeech?.setOnUtteranceProgressListener(listener)
        } catch (e: Exception) {
        }
        onDispose { }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                val tts = textToSpeech ?: return@TextButton
                if (playable.isBlank() || isPlaying) return@TextButton
                isPaused = false
                isPlaying = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            bindKenyanTtsLocale(tts, isSwahili)
                            tts.stop()
                            playable.chunked(400).forEach { chunk ->
                                tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "kenyan_media_hub")
                            }
                        }
                    } catch (e: Exception) {
                        isPlaying = false
                    }
                }
            },
            enabled = textToSpeech != null && readable.isNotBlank() && !isPlaying
        ) {
            Text("▶️ Play Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = {
                try {
                    textToSpeech?.stop()
                } catch (e: Exception) {
                }
                isPlaying = false
                isPaused = true
            },
            enabled = isPlaying
        ) {
            Text("⏸️ Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = {
                try {
                    textToSpeech?.stop()
                } catch (e: Exception) {
                }
                isPlaying = false
                isPaused = false
                resumeOffset = 0
            },
            enabled = isPlaying || isPaused
        ) {
            Text("⏹️ Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Immersive full-bleed Agritech welcome splash: Deep Forest Green (#064E3B)
 * to Rich Slate (#1B4332) gradient, executive gold "M-AgriLink" title and
 * the corporate motto in crisp white secondary typography.
 */
@Composable
private fun BootSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF064E3B), Color(0xFF1B4332))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Welcome to",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "M-AgriLink",
                color = Color(0xFFE6B325),
                fontSize = 34.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE6B325).copy(alpha = 0.85f))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cultivating Connectivity, Maximizing Margins.",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First-run cold sweep (non-blocking): before any Compose content is
        // set, check the persistent flag off-main; on maiden boot purge stale
        // cache traces on Dispatchers.IO, then flip the flag permanently so
        // later boots skip the routine and start smoothly for them.
        lifecycleScope.launch {
            try {
                val session = SessionManager(applicationContext)
                if (session.isFirstRun.first()) {
                    withContext(Dispatchers.IO) {
                        try {
                            applicationContext.cacheDir.deleteRecursively()
                        } catch (e: Exception) {
                        }
                        try {
                            applicationContext.deleteDatabase("app_cache_database")
                        } catch (e: Exception) {
                        }
                    }
                    session.markFirstRunComplete()
                }
            } catch (e: Exception) {
            }
        }

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
                var registeredName by rememberSaveable { mutableStateOf("Levis Lekesio") }
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
                    registeredName = "Levis Lekesio"
                    registeredEmail = "levislekesio@gmail.com"
                    navController.navigate(MagriLinkRoutes.AUTH_ONBOARDING) {
                        popUpTo(MagriLinkRoutes.DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                // Immersive boot splash: non-blocking 2500ms cross-fade while
                // the DataStore session loads in the background. Persistent
                // profiles land on the dashboard; otherwise the gold-accented
                // registration form is revealed underneath.
                var showBootSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2500)
                    showBootSplash = false
                }

                // Fresh-install fallback: maiden boots always land on the
                // onboarding sheet with factory-fresh blank inputs while the
                // cold sweep runs in the background for them.
                var forceOnboarding by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    try {
                        if (sessionManager.isFirstRun.first()) forceOnboarding = true
                    } catch (e: Exception) {
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (savedLogin == null) {
                        // Session still loading — hold the splash, never flash onboarding.
                    } else {
                        NavHost(
                        navController = navController,
                        startDestination = if (savedLogin == true && !forceOnboarding) {
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
                                onLogout = ::logOut,
                                onOpenScanner = {
                                    navController.navigate(MagriLinkRoutes.AI_SCANNER)
                                },
                                onOpenChat = {
                                    navController.navigate(MagriLinkRoutes.ASK_SHAMBA_CHAT)
                                },
                                onOpenAboutUs = {
                                    navController.navigate(MagriLinkRoutes.ABOUT_US)
                                }
                            )
                        }
                        composable(MagriLinkRoutes.AI_SCANNER) {
                            AiScannerScreen(navController)
                        }
                        composable(MagriLinkRoutes.ASK_SHAMBA_CHAT) {
                            AskShambaChatScreen(navController)
                        }
                        composable(MagriLinkRoutes.ABOUT_US) {
                            AboutUsScreen(navController)
                        }
                    }
                    }
                    AnimatedVisibility(
                        visible = showBootSplash,
                        enter = fadeIn(animationSpec = tween(400)),
                        exit = fadeOut(animationSpec = tween(600))
                    ) {
                        BootSplash()
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
