package com.example.m_agrilink.data

/**
 * Typo-tolerant crop name matching (Levenshtein) so "Maze", "Bens" or
 * "Soghum" still resolve. Exact matches pass through untouched; close
 * matches auto-correct with a flag so the UI can show what changed;
 * anything else returns tidied as-is for live lookup.
 */
object CropNameNormalizer {

    val KNOWN_CROPS = listOf(
        "Maize", "Beans", "Onions", "Sorghum",
        "Potatoes", "Sweet Potatoes", "Tomatoes", "Cabbage",
        "Kale", "Sukuma Wiki", "Carrots", "Bananas",
        "Avocado", "Coffee", "Tea", "Wheat", "Rice",
        "Sugarcane", "Sunflower", "Groundnuts", "Cowpeas",
        "Green Grams", "Millet", "Cassava", "Mango",
        "Oranges", "Pawpaw", "Cotton", "Barley"
    )

    data class Match(val canonical: String, val corrected: Boolean, val original: String)

    fun normalize(raw: String): Match? {
        val query = raw.trim()
        if (query.isBlank()) return null
        KNOWN_CROPS.firstOrNull { it.equals(query, ignoreCase = true) }?.let {
            return Match(canonical = it, corrected = false, original = query)
        }
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (crop in KNOWN_CROPS) {
            val d = levenshtein(query.lowercase(), crop.lowercase())
            if (d < bestDist) {
                bestDist = d
                best = crop
            }
        }
        val threshold = maxOf(1, minOf(2, query.length / 4))
        return if (best != null && bestDist <= threshold) {
            Match(canonical = best, corrected = true, original = query)
        } else {
            Match(canonical = query.replaceFirstChar { it.uppercase() }, corrected = false, original = query)
        }
    }

    fun canonicalOrOriginal(raw: String): String = normalize(raw)?.canonical ?: raw.trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
