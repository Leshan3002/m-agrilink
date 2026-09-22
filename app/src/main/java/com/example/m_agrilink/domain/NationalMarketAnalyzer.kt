package com.example.m_agrilink.domain

/**
 * 3. THE MISSING EDGE: NATION-WIDE MARKET ANALYZER
 */
object NationalMarketAnalyzer {

    data class ArbitrageOpportunity(
        val grossProfitDiff: Double,
        val netProfitDiff: Double,
        val logisticsOutweighProfit: Boolean,
        val advisoryString: String
    )

    /**
     * Optimization routing math algorithm calculating cross-county logistics viability.
     */
    fun computeArbitrageOpportunity(
        localHubPrice: Double,
        targetHubPrice: Double,
        transportationCostPerBag: Double,
        localCounty: String,
        targetCounty: String
    ): ArbitrageOpportunity {
        val grossProfitDiff = targetHubPrice - localHubPrice
        val netProfitDiff = grossProfitDiff - transportationCostPerBag
        
        // Logistics outweigh selling values if net profit difference is less than or equal to zero
        val logisticsOutweighProfit = netProfitDiff <= 0.0

        val advisoryString = if (logisticsOutweighProfit) {
            "⚠️ WARNING: Cross-county logistics costs outweigh target marketplace pricing. Transporting commodities from $localCounty to $targetCounty results in an absolute net financial loss of KES ${Math.abs(netProfitDiff)} per bag. Selling within local trade centers is strongly recommended."
        } else {
            "✅ ARBITRAGE VIABILITY DETECTED: Transporting harvest from $localCounty to $targetCounty covers transportation parameters and offers an optimal net gain of KES $netProfitDiff per bag."
        }

        return ArbitrageOpportunity(
            grossProfitDiff = grossProfitDiff,
            netProfitDiff = netProfitDiff,
            logisticsOutweighProfit = logisticsOutweighProfit,
            advisoryString = advisoryString
        )
    }
}
