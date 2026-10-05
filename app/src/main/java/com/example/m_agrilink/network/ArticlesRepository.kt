package com.example.m_agrilink.network

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Top & trending agritech/agriculture news.
 *
 * Live path: Google News RSS (no API key) for agritech + Kenya agriculture,
 * parsed on Dispatchers.IO. Offline path: curated FAO/icipe/CABI/KALRO links.
 * Returns (articles, source) where source is LIVE or OFFLINE.
 */
data class AgriNewsArticle(
    val title: String,
    val link: String,
    val sourceName: String,
    val publishedAt: String,
    val publishedEpoch: Long = 0L
)

object ArticlesRepository {

    private const val TRENDING_FEED =
        "https://news.google.com/rss/search?q=agritech&hl=en&gl=US&ceid=US%3Aen"
    private const val KENYA_FEED =
        "https://news.google.com/rss/search?q=agriculture%20Kenya&hl=en-KE&gl=KE&ceid=KE%3Aen"

    suspend fun fetchTopAgriNews(): Pair<List<AgriNewsArticle>, String> =
        withContext(Dispatchers.IO) {
            try {
                val live = (fetchFeed(TRENDING_FEED) + fetchFeed(KENYA_FEED))
                    .distinctBy { it.title.lowercase() }
                    .sortedByDescending { it.publishedEpoch }
                    .take(20)
                if (live.isEmpty()) throw IllegalStateException("empty news feeds")
                live to "LIVE"
            } catch (e: Exception) {
                offlinePicks() to "OFFLINE"
            }
        }

    private fun fetchFeed(url: String): List<AgriNewsArticle> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("User-Agent", "M-AgriLink/1.0")
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            return parseRss(xml).take(10)
        } catch (e: Exception) {
            return emptyList()
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
            }
        }
    }

    private fun parseRss(xml: String): List<AgriNewsArticle> {
        val out = mutableListOf<AgriNewsArticle>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var inItem = false
            var title = ""
            var link = ""
            var pubDate = ""
            var source = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "item" -> {
                            inItem = true
                            title = ""
                            link = ""
                            pubDate = ""
                            source = ""
                        }
                        "title" -> if (inItem && title.isEmpty()) title = parser.nextText().trim()
                        "link" -> if (inItem && link.isEmpty()) link = parser.nextText().trim()
                        "pubDate" -> if (inItem && pubDate.isEmpty()) pubDate = parser.nextText().trim()
                        "source" -> if (inItem && source.isEmpty()) source = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item") {
                        inItem = false
                        if (title.isNotBlank() && link.isNotBlank()) {
                            var cleanTitle = title
                            var cleanSource = source
                            if (cleanSource.isBlank() && cleanTitle.contains(" - ")) {
                                cleanSource = cleanTitle.substringAfterLast(" - ").trim()
                                cleanTitle = cleanTitle.substringBeforeLast(" - ").trim()
                            }
                            out.add(
                                AgriNewsArticle(
                                    title = cleanTitle,
                                    link = link,
                                    sourceName = cleanSource.ifBlank { "Google News" },
                                    publishedAt = pubDate.ifBlank { "Recent" },
                                    publishedEpoch = parseEpoch(pubDate)
                                )
                            )
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun parseEpoch(pubDate: String): Long {
        return try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                .parse(pubDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /** Curated stable links shown when offline (all real publisher pages). */
    private fun offlinePicks(): List<AgriNewsArticle> = listOf(
        AgriNewsArticle(
            title = "FAO Newsroom — food and agriculture updates",
            link = "https://www.fao.org/newsroom/en",
            sourceName = "FAO",
            publishedAt = "Offline pick"
        ),
        AgriNewsArticle(
            title = "icipe News — insect science for African farmers",
            link = "https://www.icipe.org/news",
            sourceName = "icipe",
            publishedAt = "Offline pick"
        ),
        AgriNewsArticle(
            title = "CABI Plantwise Knowledge Bank — crop pest advice",
            link = "https://plantwiseplusknowledgebank.org/",
            sourceName = "CABI",
            publishedAt = "Offline pick"
        ),
        AgriNewsArticle(
            title = "KALRO — Kenya agricultural research updates",
            link = "https://www.kalro.org/",
            sourceName = "KALRO",
            publishedAt = "Offline pick"
        ),
        AgriNewsArticle(
            title = "FAO e-Agriculture — digital farming stories",
            link = "https://www.fao.org/e-agriculture/",
            sourceName = "FAO",
            publishedAt = "Offline pick"
        )
    )
}
