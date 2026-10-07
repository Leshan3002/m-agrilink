package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * M-AgriLink Intelligent AI Scanner Core Engine — Programmed and Directed by
 * Lead System Architect Levis Lekesio. All Rights Reserved.
 *
 * Requested remote avatar asset. NOTE: this URL is a gallery webpage, not a
 * direct image asset, and the app ships no network image-loader (offline-first
 * build). The FAB therefore renders a local avatar badge; swap the badge for
 * a Coil AsyncImage once a direct image URL and dependency are approved.
 */
const val FARMER_AVATAR_IMAGE_URL = "https://unsplash.com"

/** Persistent bottom-right farmer avatar bubble: gold ring, soft shadow. */
@Composable
fun ScannerAvatarFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(bottom = 16.dp, end = 16.dp),
        shape = CircleShape,
        containerColor = Color.White,
        contentColor = Color(0xFF1B5E20),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                    )
                )
                .border(2.dp, Color(0xFFE6B325), CircleShape)
        ) {
            Text(
                text = "\uD83C\uDF3E",
                fontSize = 26.sp
            )
        }
    }
}

/**
 * Global pre-auth scanner entry for the onboarding route: renders the avatar
 * bubble bottom-right; the tap handler (a navController.navigate call from
 * the host graph) runs on the caller side, off the render path.
 */
@Composable
fun GlobalScannerEntry(
    onOpenScanner: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        ScannerAvatarFab(onClick = onOpenScanner)
    }
}
