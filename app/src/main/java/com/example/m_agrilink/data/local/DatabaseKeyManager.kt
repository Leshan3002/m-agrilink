package com.example.m_agrilink.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom

/**
 * Holds the 256-bit SQLCipher passphrase inside the Android Keystore
 * (AES256-GCM via EncryptedSharedPreferences). Falls back to private
 * app prefs only if the Keystore is unusable, so the app never bricks —
 * the database file itself stays encrypted either way.
 */
object DatabaseKeyManager {

    private const val PREFS_NAME = "magrilink_keystore_prefs"
    private const val KEY_PASSPHRASE = "db_passphrase_b64"

    fun getPassphrase(context: Context): ByteArray {
        val stored = readStored(context)
        if (stored != null) return stored
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        writeStored(context, fresh)
        return fresh
    }

    private fun readStored(context: Context): ByteArray? {
        return try {
            val b64 = encryptedPrefs(context).getString(KEY_PASSPHRASE, null)
                ?: plainPrefs(context).getString(KEY_PASSPHRASE, null)
            b64?.let { Base64.decode(it, Base64.DEFAULT) }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeStored(context: Context, passphrase: ByteArray) {
        val b64 = Base64.encodeToString(passphrase, Base64.NO_WRAP)
        try {
            encryptedPrefs(context).edit().putString(KEY_PASSPHRASE, b64).apply()
        } catch (e: Exception) {
            plainPrefs(context).edit().putString(KEY_PASSPHRASE, b64).apply()
        }
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            alias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun plainPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
}
