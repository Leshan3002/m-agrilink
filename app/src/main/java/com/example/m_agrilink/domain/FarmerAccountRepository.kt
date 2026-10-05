package com.example.m_agrilink.domain

import android.content.Context
import com.example.m_agrilink.data.local.AgriLinkDatabase
import com.example.m_agrilink.data.local.CookieConsentManager
import com.example.m_agrilink.data.local.CropSearchHistory
import com.example.m_agrilink.data.local.FarmerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Account seam: the UI depends ONLY on this interface.
 * Today it is backed by Room (ACID, on-device). Tomorrow a
 * FirebaseFarmerAccountRepository can implement the same contract
 * (matching on externalUid) without touching a single screen.
 */
interface FarmerAccountRepository {
    suspend fun ensureGuestProfile(): FarmerProfile
    suspend fun getProfile(): FarmerProfile?
    suspend fun updateCounty(county: String)
    suspend fun saveDisplayName(name: String)
    suspend fun updateProfileDetails(email: String, phone: String, county: String, acreage: Double)
    suspend fun updateFullProfile(name: String, email: String, phone: String, county: String, acreage: Double)
    suspend fun linkGoogleAccount(uid: String, email: String, displayName: String, photoUrl: String?)
    suspend fun saveCookieConsent(choice: String, analytics: Boolean, personalization: Boolean, marketing: Boolean)
    suspend fun getProfileName(): String?
    suspend fun logCropSearch(cropName: String, county: String, source: String)
    suspend fun recentCrops(limit: Int = 8): List<String>
    suspend fun clearLocalData()
    suspend fun signOut()
}

class RoomFarmerAccountRepository(context: Context) : FarmerAccountRepository {

    private val appContext = context.applicationContext
    private val dao = AgriLinkDatabase.getDatabase(appContext).farmerDao()
    private val cookies = CookieConsentManager(appContext)

    override suspend fun ensureGuestProfile(): FarmerProfile = withContext(Dispatchers.IO) {
        dao.getProfile() ?: run {
            val id = dao.upsertProfile(FarmerProfile())
            dao.getProfile() ?: FarmerProfile(id = id)
        }
    }

    override suspend fun getProfile(): FarmerProfile? = withContext(Dispatchers.IO) {
        dao.getProfile()
    }

    override suspend fun updateCounty(county: String) = withContext(Dispatchers.IO) {
        val profile = ensureGuestProfile()
        dao.updateCounty(profile.id, county)
    }

    override suspend fun saveDisplayName(name: String) = withContext(Dispatchers.IO) {
        val clean = name.trim().take(40).ifBlank { "Guest Farmer" }
        val profile = ensureGuestProfile()
        dao.updateDisplayName(profile.id, clean)
    }

    override suspend fun updateProfileDetails(email: String, phone: String, county: String, acreage: Double) =
        withContext(Dispatchers.IO) {
            val profile = ensureGuestProfile()
            dao.updateProfileDetails(
                profile.id,
                email.trim().take(80),
                phone.trim().take(20),
                county.trim().take(40),
                acreage.coerceIn(0.1, 10000.0)
            )
        }

    override suspend fun updateFullProfile(name: String, email: String, phone: String, county: String, acreage: Double) =
        withContext(Dispatchers.IO) {
            val profile = ensureGuestProfile()
            dao.updateFullProfile(
                profile.id,
                name.trim().take(40).ifBlank { "Guest Farmer" },
                email.trim().take(80),
                phone.trim().take(20),
                county.trim().take(40),
                acreage.coerceIn(0.1, 10000.0)
            )
        }

    override suspend fun linkGoogleAccount(uid: String, email: String, displayName: String, photoUrl: String?) =
        withContext(Dispatchers.IO) {
            val profile = ensureGuestProfile()
            val cleanEmail = email.trim().take(80)
            val cleanName = displayName.trim().take(40).ifBlank { profile.displayName }
            dao.linkGoogleAccount(profile.id, "google", uid.ifBlank { cleanEmail }, cleanEmail, cleanName, photoUrl)
        }

    override suspend fun saveCookieConsent(choice: String, analytics: Boolean, personalization: Boolean, marketing: Boolean) =
        withContext(Dispatchers.IO) {
            val profile = ensureGuestProfile()
            dao.updateCookieConsent(profile.id, choice, analytics, personalization, marketing)
            when (choice) {
                "accepted" -> cookies.acceptAll()
                "declined" -> cookies.declineAll()
                else -> cookies.saveCustom(analytics, personalization, marketing)
            }
        }

    override suspend fun getProfileName(): String? = withContext(Dispatchers.IO) {
        dao.getProfile()?.displayName
    }

    override suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        dao.clearHistory()
        dao.clearProfiles()
    }

    override suspend fun logCropSearch(cropName: String, county: String, source: String) {
        withContext(Dispatchers.IO) {
            val profile = ensureGuestProfile()
            dao.addHistory(
                CropSearchHistory(
                    profileId = profile.id,
                    cropName = cropName,
                    county = county,
                    source = source
                )
            )
            // Keep the store lean and fast: cap history at 200 rows.
            try {
                dao.pruneHistory(200)
            } catch (e: Exception) {
            }
        }
    }

    override suspend fun recentCrops(limit: Int): List<String> = withContext(Dispatchers.IO) {
        dao.recentCropNames(limit)
    }

    /** Local no-op now; FirebaseAuth.signOut() plugs in here later. */
    override suspend fun signOut() = Unit
}
