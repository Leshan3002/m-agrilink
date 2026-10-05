package com.example.m_agrilink.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Cookie / consent store for the native app.
 *
 * Android has no browser cookies, so this is the equivalent:
 * a persisted consent record (SharedPreferences) + mirrored flags on
 * [FarmerProfile]. Personalization tracking only runs when the user
 * opts in, and the banner must ask on first launch.
 */
class CookieConsentManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class ConsentState(
        val choice: String = "pending", // pending | accepted | custom | declined
        val analytics: Boolean = false,
        val personalization: Boolean = false,
        val marketing: Boolean = false,
        val decidedAt: Long = 0L
    )

    fun current(): ConsentState = ConsentState(
        choice = prefs.getString(KEY_CHOICE, "pending") ?: "pending",
        analytics = prefs.getBoolean(KEY_ANALYTICS, false),
        personalization = prefs.getBoolean(KEY_PERSONALIZATION, false),
        marketing = prefs.getBoolean(KEY_MARKETING, false),
        decidedAt = prefs.getLong(KEY_DECIDED_AT, 0L)
    )

    fun needsBanner(): Boolean = current().choice == "pending"

    /** User tapped Accept: everything on, full personalization for the recommender. */
    fun acceptAll() = save("accepted", analytics = true, personalization = true, marketing = true)

    /** User rejected non-essential tracking: app still works, no personalization. */
    fun declineAll() = save("declined", analytics = false, personalization = false, marketing = false)

    /** Granular settings from the Cookie Settings sheet. */
    fun saveCustom(analytics: Boolean, personalization: Boolean, marketing: Boolean) =
        save("custom", analytics, personalization, marketing)

    private fun save(choice: String, analytics: Boolean, personalization: Boolean, marketing: Boolean) {
        prefs.edit()
            .putString(KEY_CHOICE, choice)
            .putBoolean(KEY_ANALYTICS, analytics)
            .putBoolean(KEY_PERSONALIZATION, personalization)
            .putBoolean(KEY_MARKETING, marketing)
            .putLong(KEY_DECIDED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Gate for any interest tracking used by the recommendation algorithm. */
    fun canPersonalize(): Boolean = current().personalization

    fun canAnalyse(): Boolean = current().analytics

    companion object {
        private const val PREFS = "agrilink_cookies"
        private const val KEY_CHOICE = "cookie_choice"
        private const val KEY_ANALYTICS = "consent_analytics"
        private const val KEY_PERSONALIZATION = "consent_personalization"
        private const val KEY_MARKETING = "consent_marketing"
        private const val KEY_DECIDED_AT = "decided_at"
    }
}
