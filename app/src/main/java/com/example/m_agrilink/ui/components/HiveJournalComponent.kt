package com.example.m_agrilink.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * M-AgriLink Apiculture Production Module
 * Engineered and Directed by Lead System Architect Levis Lekesio.
 * Verified via KALRO ABIRI Marigat & World Bank KCSAP Guidelines.
 */

// Swap these for direct image URLs (e.g. https://images.unsplash.com/...) —
// gallery pages such as https://unsplash.com are not image files and will
// show the fallback icon until replaced.
private const val GALLERY_URL_PURE_HONEY = "https://unsplash.com"
private const val GALLERY_URL_COMB_FRAME = "https://unsplash.com"

private const val GALLERY_MODULE_TAG =
    "M-AgriLink Production Module — Engineered and Directed by Lead System Architect Levis Lekesio."

private val galleryMemoryCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
        size > 12
}

private fun downloadBoundedBitmap(model: String, maxSide: Int = 480): Bitmap? {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(model).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        if (connection.responseCode !in 200..299) return null
        val raw = BitmapFactory.decodeStream(connection.inputStream) ?: return null
        if (raw.width <= 0 || raw.height <= 0) {
            try {
                raw.recycle()
            } catch (e: Exception) {
            }
            return null
        }
        val longest = maxOf(raw.width, raw.height)
        if (longest <= maxSide) return raw
        val scale = maxSide.toFloat() / longest
        val small = Bitmap.createScaledBitmap(
            raw,
            (raw.width * scale).toInt().coerceAtLeast(1),
            (raw.height * scale).toInt().coerceAtLeast(1),
            true
        )
        try {
            raw.recycle()
        } catch (e: Exception) {
        }
        return small
    } catch (e: IOException) {
        return null
    } catch (e: Exception) {
        return null
    } finally {
        try {
            connection?.disconnect()
        } catch (e: Exception) {
        }
    }
}

/** Dependency-free remote thumbnail: IO-thread download, bounded, cached, icon fallback. */
@Composable
private fun NetworkGalleryImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(model) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(model) { mutableStateOf(false) }
    LaunchedEffect(model) {
        synchronized(galleryMemoryCache) { galleryMemoryCache[model] }?.let {
            bitmap = it
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) { downloadBoundedBitmap(model) }
        if (loaded != null) {
            synchronized(galleryMemoryCache) { galleryMemoryCache[model] = loaded }
            bitmap = loaded
        } else {
            failed = true
        }
    }
    Box(
        modifier = modifier.background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = if (failed) "Apiary photo unavailable offline" else "Loading apiary photo",
                tint = Color.Gray,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun HiveJournalComponent(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🐝 KALRO ABIRI Hive Journal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "● Live",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Yield Analytics Cards Row
            Text(
                text = "📊 Projected Yield Metrics (Modern Langstroth / KTB Hives)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "• Target Yield: 24kg to 30kg of raw honey per hive, harvested twice annually.\n" +
                    "• Subsidized Hive Investment: KES 2,500 standard unit entry cost.\n" +
                    "• Profit Profile: 10 active hives yield approx. KES 144,000 per season.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Honey Forage Cycles & PASTURE MANAGEMENT
            Text(
                text = "🌱 Regional Honey Forage & Floral Calendar",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "• Forage Cover: Acacia and multi-flower shrub preservation in ASAL corridors.\n" +
                    "• Climate Shield: Maintain internal hive temperature under extreme drought to prevent absconding or colony migration.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Dynamic Public Imagery Row
            Text(
                text = "📸 Apiary Reference Gallery",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    NetworkGalleryImage(
                        model = GALLERY_URL_PURE_HONEY,
                        contentDescription = "Pure liquid honey harvesting",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                item {
                    NetworkGalleryImage(
                        model = GALLERY_URL_COMB_FRAME,
                        contentDescription = "Honeybees on a comb frame texture",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer Footnote
            Text(
                text = "*Sourced via KALRO Apiculture Value Chain, Climate-Smart TIMPs Manual 2020.*",
                fontSize = 11.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = GALLERY_MODULE_TAG,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
