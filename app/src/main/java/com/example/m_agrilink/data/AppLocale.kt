package com.example.m_agrilink.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global app locale holder (lives in data so repositories can render
 * display strings in the farmer's language without a ui dependency).
 * Synced from the Language picker in DashboardScreen.
 */
object AppLocale {
    var language by mutableStateOf("English")
    val isSwahili: Boolean get() = language == "Kiswahili"
}
