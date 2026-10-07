package com.example.m_agrilink.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.m_agrilink.network.CropLiveLookup

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

/** Full-screen scanner workspace overlay with an explicit close control. */
@Composable
private fun ScannerOverlayDialog(
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF4F6F8)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close scanner",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

private fun openWebUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
    }
}

/**
 * Global pre-auth scanner entry for the onboarding route: the dashboard hosts
 * its own context-aware FAB, so this covers every remaining screen.
 */
@Composable
fun GlobalScannerEntry() {
    val context = LocalContext.current
    val lookup = remember(context) { CropLiveLookup(context.applicationContext) }
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        ScannerAvatarFab(onClick = { open = true })
    }
    if (open) {
        ScannerOverlayDialog(onClose = { open = false }) {
            AiScannerScreen(
                selectedCounty = "Kenya",
                farmerPlantedCrop = "",
                textToSpeech = null,
                liveLookup = lookup,
                onOpenWebLink = { openWebUrl(context, it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
