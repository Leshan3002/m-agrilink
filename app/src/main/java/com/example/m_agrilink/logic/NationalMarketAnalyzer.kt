package com.example.m_agrilink.logic

import com.example.m_agrilink.data.MarketPriceDao

/**
 * 2. BUSINESS LOGIC LAYER: Arbitrage Calculation Result Node
 */
data class ArbitrageResult(
    val localPrice: Double,
    val targetPrice: Double,
    val transportCost: Double,
    val netMargin: Double,
    val recommendation: String
)

/**
 * 2. BUSINESS LOGIC LAYER: National Market Arbitrage Engine
 */
class NationalMarketAnalyzer(private val marketPriceDao: MarketPriceDao) {

    /**
     * Mathematical Arbitrage Computation
     * Formula: Net Margin = Target Hub Price - (Local Hub Price + Transport Cost)
     */
    suspend fun calculateArbitrage(
        localHub: String,
        targetHub: String,
        transportCostPerBag: Double,
        commodity: String = "Maize"
    ): ArbitrageResult {
        // Fetch cached prices from Room DAO
        val allPrices = marketPriceDao.getAllPrices()
        
        val localPrice = allPrices.find { it.hubName == localHub && it.commodityName == commodity }?.pricePer90kg ?: 0.0
        val targetPrice = allPrices.find { it.hubName == targetHub && it.commodityName == commodity }?.pricePer90kg ?: 0.0

        val netMargin = targetPrice - (localPrice + transportCostPerBag)
        
        val recommendation = if (netMargin > 400.0) {
            "🚀 HIGH VIABILITY: Transporting $commodity from $localHub to $targetHub yields a substantial net profit of KES $netMargin per bag. Highly recommended."
        } else if (netMargin > 0) {
            "⚖️ MARGINAL GAIN: A net profit of KES $netMargin per bag is possible. Ensure logistics efficiency to maintain this margin."
        } else {
            "⚠️ AVOID TRANSPORT: Selling locally in $localHub is more profitable. Cross-county transport to $targetHub results in a loss of KES ${Math.abs(netMargin)} per bag."
        }

        return ArbitrageResult(
            localPrice = localPrice,
            targetPrice = targetPrice,
            transportCost = transportCostPerBag,
            netMargin = netMargin,
            recommendation = recommendation
        )
    }
}
