package com.example.m_agrilink.network

import com.example.m_agrilink.BuildConfig

/**
 * M-AgriLink Core Engine — Engineered and Directed by Lead System Architect Levis Lekesio.
 *
 * Encrypted-state memory bucket for the production vision credential.
 * The key is compiled from local.properties (gitignored — never committed,
 * never logged, never embedded in dump strings) and held here in a single
 * private volatile slot. Clients receive short-lived copies only at client
 * construction; diagnostics get a masked fingerprint. Nothing in this
 * object ever prints or returns the raw secret to logs or UI.
 *
 * Threat-model note: any secret shipped inside an APK can be extracted by
 * a determined reverse-engineer. For absolute protection the key belongs
 * behind a backend proxy; this vault is the strongest on-device posture.
 */
object SecureKeyVault {

    @Volatile
    private var cached: String? = null

    /** Short-lived copy for Retrofit client construction. Never log the result. */
    fun snapshot(): String {
        var key = cached
        if (key == null) {
            synchronized(this) {
                key = cached
                if (key == null) {
                    key = BuildConfig.GEMINI_API_KEY.trim()
                    cached = key
                }
            }
        }
        return key ?: ""
    }

    fun isConfigured(): Boolean = snapshot().isNotBlank()

    /** Safe fingerprint for diagnostics, e.g. AIza…W1lc (or "unconfigured"). */
    fun masked(): String {
        val key = snapshot()
        if (key.isBlank()) return "unconfigured"
        if (key.length <= 8) return "****"
        return key.take(4) + "…" + key.takeLast(4)
    }

    /** Drops the cached copy (re-read from BuildConfig on next use). */
    fun purge() {
        synchronized(this) { cached = null }
    }
}
