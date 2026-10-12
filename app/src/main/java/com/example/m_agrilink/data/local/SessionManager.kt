package com.example.m_agrilink.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "magrilink_session")

/**
 * M-AgriLink Identity & Security Core.
 *
 * DataStore session persistence: login state + profile snapshot. All reads
 * are cold [Flow] streams collected off the main thread; all writes are
 * `suspend` calls so they stay logged neutrally in background dispatch.
 */
class SessionManager(context: Context) {

    private val store = context.applicationContext.sessionDataStore

    companion object {
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        // First-run cold-sweep flag: true until the maiden cache purge completes.
        // Permanent once flipped; sign-out never resets it for them.
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run_v6137")
    }

    val isLoggedIn: Flow<Boolean> = store.data.map { it[IS_LOGGED_IN] == true }

    val userName: Flow<String> = store.data.map { it[USER_NAME] ?: "" }

    val userEmail: Flow<String> = store.data.map { it[USER_EMAIL] ?: "" }

    /** True on maiden boot (key absent counts as first run). */
    val isFirstRun: Flow<Boolean> = store.data.map { it[IS_FIRST_RUN] != false }

    /** Permanently marks the cold sweep done so later boots skip it. */
    suspend fun markFirstRunComplete() {
        store.edit { prefs ->
            prefs[IS_FIRST_RUN] = false
        }
    }

    suspend fun saveSession(name: String, email: String) {
        store.edit { prefs ->
            prefs[IS_LOGGED_IN] = true
            prefs[USER_NAME] = name
            prefs[USER_EMAIL] = email
        }
    }

    suspend fun clearSession() {
        store.edit { prefs ->
            prefs.remove(IS_LOGGED_IN)
            prefs.remove(USER_NAME)
            prefs.remove(USER_EMAIL)
        }
    }
}
