package com.example.m_agrilink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * TASK 1: THE HARDCODED PACKAGE ID ROUTER
 * Optimized router for verified Kenyan agricultural package namespaces.
 */
object AgriLinkRouter {

    const val PACKAGE_KAOP = "ke.or.kalro.kaop"
    const val PACKAGE_KALRO = "ke.or.kalro.varietyselector"
    const val PACKAGE_KAMIS = "ke.or.kamis"

    /**
     * Safely routes the user to the target app or its Play Store page.
     * Implements Android 11+ visibility requirements via manifest queries.
     */
    fun routeToAgriculturalService(context: Context, packageId: String) {
        val packageManager = context.packageManager

        // 1. Check if the target application is installed on the user's device
        val launchIntent = packageManager.getLaunchIntentForPackage(packageId)
        
        if (launchIntent != null) {
            // App is installed, open it
            context.startActivity(launchIntent)
        } else {
            // 2. Target is missing, catch and forward to Play Store using 'market://'
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                // 3. Secondary catch block for web link fallback
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                    context.startActivity(webIntent)
                } catch (secondaryException: Exception) {
                    Toast.makeText(context, "Error: No application can handle this request", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
