package com.example.m_agrilink.domain

import android.content.Context
import com.example.m_agrilink.data.local.AgriLinkDatabase
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
    suspend fun updateCounty(county: String)
    suspend fun logCropSearch(cropName: String, county: String, source: String)
    suspend fun recentCrops(limit: Int = 8): List<String>
    suspend fun signOut()
}

class RoomFarmerAccountRepository(context: Context) : FarmerAccountRepository {

    private val dao = AgriLinkDatabase.getDatabase(context.applicationContext).farmerDao()

    override suspend fun ensureGuestProfile(): FarmerProfile = withContext(Dispatchers.IO) {
        dao.getProfile() ?: run {
            val id = dao.upsertProfile(FarmerProfile())
            dao.getProfile() ?: FarmerProfile(id = id)
        }
    }

    override suspend fun updateCounty(county: String) = withContext(Dispatchers.IO) {
        val profile = ensureGuestProfile()
        dao.updateCounty(profile.id, county)
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
        }
    }

    override suspend fun recentCrops(limit: Int): List<String> = withContext(Dispatchers.IO) {
        dao.recentCropNames(limit)
    }

    /** Local no-op now; FirebaseAuth.signOut() plugs in here later. */
    override suspend fun signOut() = Unit
}
