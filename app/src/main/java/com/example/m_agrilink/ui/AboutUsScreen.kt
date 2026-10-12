package com.example.m_agrilink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.m_agrilink.R

/**
 * Corporate Profile & About Us destination ("about_us").
 * Minimalist credentials header on the dark agritech canvas. The portrait
 * loads `res/drawable/developer_profile.png` in a gold-framed square.
 * Background copy keeps inclusive they/their/them for farming communities.
 */
@Composable
fun AboutUsScreen(
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
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Corporate Profile & About Us",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.padding(end = 48.dp))
            }
        },
        containerColor = Color(0xFF064E3B)
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF064E3B))
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Portrait asset: hardwired developer_profile.png, 240.dp square,
            // gold frame, center crop.
            Image(
                painter = painterResource(id = R.drawable.developer_profile),
                contentDescription = "Lead Systems Architect portrait",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFFE6B325), RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Levis Lekesio",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFE6B325),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lead Systems Architect & ICT Professional",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB9B9C0),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "M-AgriLink was architected to serve smallholder farming communities " +
                    "with offline-first market intelligence, certified agronomy, and " +
                    "accessible AI diagnostics. Every module is engineered so they can " +
                    "plan their season, protect their crops, and trade their harvest " +
                    "with confidence — in English or Kiswahili, online or off the grid.",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B4332)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6B325).copy(alpha = 0.5f))
            ) {
                Text(
                    text = "\u201CTrue progress and lasting structural transformation never come " +
                        "from attempting to control external market forces; they begin entirely " +
                        "from within\u2014by mastering our own capabilities and sharpening the " +
                        "tools we build to serve our communities.\u201D",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6B325),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "M-AgriLink agricultural intelligence platform.",
                fontSize = 11.sp,
                color = Color(0xFFB9B9C0),
                textAlign = TextAlign.Center
            )
        }
    }
}
