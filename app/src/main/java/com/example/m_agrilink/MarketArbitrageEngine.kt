package com.example.m_agrilink

/**
 * Data model node representing computed comparative trade destination metrics.
 */
data class MarketComparisonNode(
    val hubName: String,
    val targetPrice: Double,
    val grossProfit: Double,
    val netProfit: Double,
    val transitCost: Double,
    val viabilityStatus: String
)

/**
 * 1. NATIVE CROSS-COUNTRY MARKET PRICE ANALYZER BUSINESS ENGINE
 */
class MarketArbitrageEngine {

    // Locally embedded source dataset parameters mapping destination trade hub market ceilings
    private val nationalMarketCeilingsMatrix = mapOf(
        "Maize" to mapOf(
            "Marigat" to 4200.0,
            "Nairobi" to 4800.0,
            "Eldoret" to 4450.0,
            "Mombasa" to 5100.0,
            "Kisumu" to 4900.0
        ),
        "Beans" to mapOf(
            "Marigat" to 11500.0,
            "Nairobi" to 13000.0,
            "Eldoret" to 12200.0,
            "Mombasa" to 13800.0,
            "Kisumu" to 13400.0
        )
    )

    /**
     * Ranks destination corridors by net profitability metrics after transit deduction overheads.
     */
    fun computeNationalArbitrage(
        crop: String,
        currentLocalPrice: Double,
        transportCostPerBag: Double
    ): List<MarketComparisonNode> {
        val destinationHubs = nationalMarketCeilingsMatrix[crop] ?: return emptyList()
        val comparisonNodes = mutableListOf<MarketComparisonNode>()

        for ((hub, targetPrice) in destinationHubs) {
            val grossProfit = targetPrice - currentLocalPrice
            val netProfit = grossProfit - transportCostPerBag
            
            val status = when {
                netProfit > 500.0 -> "🚀 HIGH VIABILITY CORRIDOR"
                netProfit > 0.0 -> "⚖️ MARGINAL PROFIT NODE"
                else -> "⚠️ UNVIABLE CORRIDOR LOSS"
            }

            comparisonNodes.add(
                MarketComparisonNode(
                    hubName = hub,
                    targetPrice = targetPrice,
                    grossProfit = grossProfit,
                    netProfit = netProfit,
                    transitCost = transportCostPerBag,
                    viabilityStatus = status
                )
            )
        }

        // Return descending sorted order by net profit parameter ceilings
        return comparisonNodes.sortedByDescending { it.netProfit }
    }
}
