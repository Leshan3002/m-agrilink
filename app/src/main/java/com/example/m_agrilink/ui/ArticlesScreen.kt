package com.example.m_agrilink.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.m_agrilink.network.AgriNewsArticle
import com.example.m_agrilink.network.ArticlesRepository

/**
 * Top & trending agritech/agriculture news page.
 * Live Google News RSS when online, curated FAO/icipe/CABI/KALRO picks offline.
 * Fetches on Dispatchers.IO inside the repository — never on the main thread.
 */
@Composable
fun ArticlesPage(
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val cardBg = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val cardText = if (isDarkTheme) Color.White else Color.Black
    val cardMuted = if (isDarkTheme) Color(0xFFB9B9C0) else Color.DarkGray
    var refreshKey by remember { mutableIntStateOf(0) }
    var articles by remember { mutableStateOf<List<AgriNewsArticle>?>(null) }
    var feedSource by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        loading = true
        val (list, src) = ArticlesRepository.fetchTopAgriNews()
        articles = list
        feedSource = src
        loading = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
        ) {
            Text(str("back_home"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        str("art_hero"),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (feedSource.isNotEmpty() && !loading) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE6B325), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (feedSource == "LIVE") str("art_live") else str("art_offline"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    str("art_sub"),
                    color = Color(0xFFDCE6F5),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { refreshKey++ },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(str("art_refresh"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (loading && articles == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(str("art_loading"), fontSize = 13.sp, color = cardMuted)
            }
        } else {
            (articles ?: emptyList()).forEachIndexed { index, article ->
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "#${index + 1} ${article.title}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp,
                            color = cardText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "🗞 ${article.sourceName} • ${article.publishedAt}",
                            fontSize = 11.sp,
                            color = cardMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenLink(article.link) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Text(str("art_read"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    onOpenLink(
                                        "https://www.youtube.com/results?search_query=" +
                                            Uri.encode(article.title.take(80))
                                    )
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(str("art_video"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                str("art_sources"),
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
