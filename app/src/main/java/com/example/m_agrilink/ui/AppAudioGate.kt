package com.example.m_agrilink.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global audio gate: one tap on any speaker mutes every voice in the app,
 * the next tap unmutes. Observable so all speaker icons flip together.
 */
object AppAudioGate {
    var muted by mutableStateOf(false)

    /** Returns true when the app is now muted. */
    fun toggle(): Boolean {
        muted = !muted
        return muted
    }
}
