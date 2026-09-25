package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FEATURE 3: KALRO ABIRI HIVE JOURNAL & GAPs TIMELINE
 * Specialized 4-Phase linear beekeeping lifecycle.
 */

@Composable
fun JourneyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "KALRO ABIRI Hive Journal",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // PHASE 1: Colonization & Catcher Hives
        JourneyCard(
            phase = "Phase 1",
            title = "Colonization & Catcher Hives",
            description = "🪵 Deploy baited Langstroth or KTBH catcher hives. Ensure proper orientation (South-East) and proximity to water/forage.",
            color = Color(0xFF2E7D32)
        )

        // PHASE 2: Apiary Management (Scouting)
        JourneyCard(
            phase = "Phase 2",
            title = "Weekly Apiary Scouting",
            description = "🪓 Execute routine scouting for Hive Beetles, Wax Moths, and Safari Ants. Ensure hive stands are greased and apiary is weed-free.",
            color = Color(0xFFE6B325)
        )

        // PHASE 3: Sustainable Honey Harvesting
        JourneyCard(
            phase = "Phase 3",
            title = "Sustainable Harvesting",
            description = "🍯 Determining comb capping thresholds. Harvest ONLY when honeycombs are 75%+ capped to guarantee low moisture and high quality.",
            color = Color(0xFF2E7D32)
        )

        // PHASE 4: By-product Value Addition
        JourneyCard(
            phase = "Phase 4",
            title = "By-product Value Addition",
            description = "🐝 Process beeswax for matrix construction. Harvest Propolis tinctures and collect Royal Jelly using ABIRI certified protocols.",
            color = Color(0xFFE6B325)
        )
    }
}

@Composable
fun JourneyCard(phase: String, title: String, description: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Timeline indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(80.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = phase,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF37474F),
                    lineHeight = 20.sp
                )
            }
        }
    }
}
