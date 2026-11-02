package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Dedicated Ask Shamba AI chat destination ("ask_shamba_chat").
 *
 * The full-screen surface gives the conversation the same professional
 * treatment as the dashboard Bento cards: a clear identity header, a stable
 * message list, and a focused composer that stays above the keyboard.
 */
@Composable
fun AskShambaChatScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            Surface(color = Color(0xFF064E3B), tonalElevation = 4.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF0B5D46), CircleShape)
                    ) {
                        Text(
                            text = "S",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "M-AgriLink Assistant",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shamba • Farm advisory",
                            color = Color(0xFFB7E4C7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAF7)
    ) { scaffoldPadding ->
        AiChatOverlay(
            telemetry = TelemetryContext(
                location = "Kenya",
                temp = "—",
                rainProb = "N/A",
                wind = "—",
                humidity = "—",
                cropVariety = "General"
            ),
            onDismiss = { navController.popBackStack() },
            modifier = Modifier
                .padding(scaffoldPadding)
                .fillMaxSize(),
            fullScreen = true,
            showHeader = false
        )
    }
}
