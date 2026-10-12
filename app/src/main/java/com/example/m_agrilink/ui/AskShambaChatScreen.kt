package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * Dedicated Ask Shamba AI chat destination ("ask_shamba_chat").
 * Full-screen conversational workflow where they describe how their farm,
 * crops, or cattle are behaving and Shamba replies with friendly advice.
 * Neutral telemetry defaults so replies never depend on dashboard inputs.
 */
@Composable
fun AskShambaChatScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF064E3B))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = str("sscan_back"),
                        tint = Color.White
                    )
                }
                Text(
                    text = str("shamba_title"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
        containerColor = Color(0xFF064E3B)
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF064E3B))
                .padding(scaffoldPadding)
                .padding(16.dp)
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AiChatOverlay(
                telemetry = TelemetryContext(
                    location = "Kenya",
                    temp = "—",
                    rainProb = "N/A",
                    wind = "—",
                    humidity = "—",
                    cropVariety = "General"
                ),
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}
