package com.example.m_agrilink.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device interest tracker. Only records when [CookieConsentManager.canPersonalize]
 * is true. The stored signal (top crops, counties, weather views, AI topics) is read
 * by the Market/Weather/AI UI to rank suggestions — "the best for the algorithm".
 * No network, no third-party SDK: stays encrypted alongside Room via app storage.
 */
class UserInterestTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun trackCropView(crop: String, county: String) {
        if (crop.isBlank()) return
        bump("crop:" + crop.trim().lowercase())
        if (county.isNotBlank()) bump("county:" + county.trim().lowercase())
        pushRecent("recent_crops", crop.trim())
    }

    fun trackWeatherView(place: String) {
        if (place.isBlank()) return
        bump("weather:" + place.trim().lowercase())
    }

    fun trackAiTopic(query: String) {
        val topic = query.trim().lowercase().split(Regex("\\s+")).firstOrNull { it.length > 3 }
            ?: return
        bump("topic:$topic")
    }

    fun topCrops(limit: Int = 3): List<String> = topWithPrefix("crop:", limit)

    fun topCounties(limit: Int = 2): List<String> = topWithPrefix("county:", limit)

    fun recentCrops(limit: Int = 5): List<String> {
        return try {
            val arr = JSONArray(prefs.getString("recent_crops", "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }.distinct().take(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Human-readable line shown in the Cookie Settings sheet when personalization is on. */
    fun summaryLine(): String {
        val crops = topCrops(3)
        val counties = topCounties(2)
        if (crops.isEmpty() && counties.isEmpty()) return "No interests learned yet — use Market, Weather and AI and I will personalize here."
        return buildString {
            if (crops.isNotEmpty()) append("Top crops: " + crops.joinToString(", ") + ". ")
            if (counties.isNotEmpty()) append("Top areas: " + counties.joinToString(", ") + ".")
        }.trim()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun bump(key: String) {
        try {
            val counts = JSONObject(prefs.getString(KEY_COUNTS, "{}") ?: "{}")
            counts.put(key, counts.optInt(key, 0) + 1)
            prefs.edit().putString(KEY_COUNTS, counts.toString()).apply()
        } catch (e: Exception) {
        }
    }

    private fun topWithPrefix(prefix: String, limit: Int): List<String> {
        return try {
            val counts = JSONObject(prefs.getString(KEY_COUNTS, "{}") ?: "{}")
            counts.keys().asSequence()
                .filter { it.startsWith(prefix) }
                .sortedByDescending { counts.optInt(it, 0) }
                .map { it.removePrefix(prefix) }
                .take(limit).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun pushRecent(key: String, value: String) {
        try {
            val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
            val items = mutableListOf(value)
            (0 until arr.length()).map { arr.getString(it) }.forEach {
                if (!it.equals(value, ignoreCase = true)) items.add(it)
            }
            val trimmed = JSONArray(items.distinct().take(20))
            prefs.edit().putString(key, trimmed.toString()).apply()
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val PREFS = "agrilink_interests"
        private const val KEY_COUNTS = "interest_counts"
    }
}
