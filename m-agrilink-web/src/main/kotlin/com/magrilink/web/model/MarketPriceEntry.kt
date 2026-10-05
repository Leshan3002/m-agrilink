package com.magrilink.web.model

import kotlinx.serialization.Serializable

/**
 * Single county price point returned by /api/v1/market-analytics.
 */
@Serializable
data class MarketPriceEntry(
    val county: String,
    val crop: String,
    val priceKshPerKg: Double,
    val ownerAccount: String,
    val updatedAt: String
)
