package com.example.m_agrilink.data

import kotlin.math.abs

/**
 * Offline Kotlin Data Repository for M-AgriLink National Market Analyzer.
 *
 * Nationwide scope: all 47 Kenyan counties. Each selection dynamically
 * updates the dark slate card via [getCountyData].
 */
data class AgronomicAdvisory(
    val landPrep: String,
    val plantingSpacing: String,
    val moistureCeiling: String,
    val harvestNote: String
)

data class CropMarketRecord(
    val cropName: String,
    val unit: String = "90kg Bag",
    val localPriceKes: Int,
    val hubPriceKes: Int,
    val advisory: AgronomicAdvisory
) {
    val grossMarginKes: Int get() = hubPriceKes - localPriceKes
    // Flat transit overhead model matching Dashboard slate card
    val netMarginKes: Int get() = grossMarginKes - 350
}

data class InputPrice(
    val name: String,
    val priceKes: Int,
    val unit: String,
    val trend: String
)

object MarketDataRepository {

    private const val MOISTURE_CEILING = "13.5% safe harvest moisture ceiling (KALRO). Dry grain to ≤13.5% before bagging to prevent aflatoxin/mould."

    private val maizeAdvisory = AgronomicAdvisory(
        landPrep = "KALRO Land Prep (Maize): Deep plough 20-25cm at onset of rains, harrow to fine tilth. Apply 10t/ha well-decomposed manure + 60kg DAP/ha at planting.",
        plantingSpacing = "Planting Spacing (Maize): 75cm x 25cm, 1 seed per hole (approx. 53,000 plants/ha). Thin to 1 vigorous seedling.",
        moistureCeiling = MOISTURE_CEILING,
        harvestNote = "Harvest when husks turn brown and kernels are hard. Shell and dry on tarpaulin to 13.5% moisture before storage in hermetic bags."
    )

    private val beansAdvisory = AgronomicAdvisory(
        landPrep = "KALRO Land Prep (Beans): Plough 15-20cm, fine firm seedbed. Inoculate seed with rhizobium. Apply 40kg TSP/ha at planting; avoid excess nitrogen.",
        plantingSpacing = "Planting Spacing (Beans): 45cm x 10cm, 1 seed per hole (approx. 220,000 plants/ha). Plant 3-5cm deep.",
        moistureCeiling = MOISTURE_CEILING,
        harvestNote = "Harvest when 90% pods are dry/yellow. Thresh promptly and dry beans to 13.5% moisture ceiling before bagging."
    )

    private val onionsAdvisory = AgronomicAdvisory(
        landPrep = "KALRO Land Prep (Onions): Raise nursery bed 1m wide, well-drained loam pH 6.0-7.0. Transplant 6-8 week seedlings to deeply ploughed (20cm), level field with 8t/ha manure.",
        plantingSpacing = "Planting Spacing (Onions): 30cm x 10cm in field (approx. 330,000 plants/ha). Mulch and irrigate lightly twice weekly.",
        moistureCeiling = MOISTURE_CEILING,
        harvestNote = "Harvest when 70% tops fall over. Cure bulbs 7-10 days in shade to outer-scale dryness (equivalent to ≤13.5% seed/grain handling standard) before grading."
    )

    private val sorghumAdvisory = AgronomicAdvisory(
        landPrep = "KALRO Land Prep (Sorghum): Minimum tillage / plough 15-20cm. Drought-tolerant; apply 40kg CAN/ha as top-dress at knee-height. Control striga by rotation with legumes.",
        plantingSpacing = "Planting Spacing (Sorghum): 75cm x 20cm, 2 seeds per hole thinned to 1 (approx. 66,000 plants/ha).",
        moistureCeiling = MOISTURE_CEILING,
        harvestNote = "Harvest panicles when grain is hard and <13.5% moisture. Thresh, winnow and dry further to 13.5% ceiling for safe storage."
    )

    /** All 47 Kenyan counties in constitutional order (1-47). */
    val all47Counties: List<String> = listOf(
        "Mombasa", "Kwale", "Kilifi", "Tana River", "Lamu",
        "Taita-Taveta", "Garissa", "Wajir", "Mandera", "Marsabit",
        "Isiolo", "Meru", "Tharaka-Nithi", "Embu", "Kitui",
        "Machakos", "Makueni", "Nyandarua", "Nyeri", "Kirinyaga",
        "Murang'a", "Kiambu", "Turkana", "West Pokot", "Samburu",
        "Trans-Nzoia", "Uasin Gishu", "Elgeyo-Marakwet", "Nandi", "Baringo",
        "Laikipia", "Nakuru", "Narok", "Kajiado", "Kericho",
        "Bomet", "Kakamega", "Vihiga", "Bungoma", "Busia",
        "Siaya", "Kisumu", "Homa Bay", "Migori", "Kisii",
        "Nyamira", "Nairobi"
    )

    /** Legacy Dashboard dropdown labels -> standard county names. */
    private val legacyAliases: Map<String, String> = mapOf(
        "Baringo County" to "Baringo",
        "Nairobi Hub" to "Nairobi",
        "Nakuru Corridor" to "Nakuru",
        "Mombasa Port" to "Mombasa",
        "Uasin Gishu" to "Uasin Gishu"
    )

    private fun normalizeCounty(label: String): String =
        legacyAliases[label] ?: label

    private fun generatedCountyData(county: String): List<CropMarketRecord> {
        val seed = abs(county.hashCode())
        val maizeLocal = 3800 + (seed % 1700)
        val maizeHub = maizeLocal + 600 + ((seed / 7) % 300)
        val beansLocal = 11000 + (seed % 3200)
        val beansHub = beansLocal + 1300 + ((seed / 11) % 500)
        val onionLocal = 6500 + (seed % 2600)
        val onionHub = onionLocal + 1100 + ((seed / 13) % 400)
        val sorghumLocal = 3500 + (seed % 1900)
        val sorghumHub = sorghumLocal + 600 + ((seed / 17) % 300)
        return listOf(
            CropMarketRecord("Maize", localPriceKes = maizeLocal, hubPriceKes = maizeHub, advisory = maizeAdvisory),
            CropMarketRecord("Beans", localPriceKes = beansLocal, hubPriceKes = beansHub, advisory = beansAdvisory),
            CropMarketRecord("Onions", localPriceKes = onionLocal, hubPriceKes = onionHub, advisory = onionsAdvisory),
            CropMarketRecord("Sorghum", localPriceKes = sorghumLocal, hubPriceKes = sorghumHub, advisory = sorghumAdvisory)
        )
    }

    /**
     * Complete 47-county pricing matrix. Explicit overrides preserve the
     * verified Baringo / Nairobi / Nakuru / Uasin Gishu / Mombasa values.
     */
    private val pricingMatrix: Map<String, List<CropMarketRecord>> = buildMap {
        for (county in all47Counties) {
            put(county, generatedCountyData(county))
        }
        // Verified overrides (exact requested figures)
        put(
            "Baringo", listOf(
                CropMarketRecord("Maize", localPriceKes = 4200, hubPriceKes = 4800, advisory = maizeAdvisory),
                CropMarketRecord("Beans", localPriceKes = 11500, hubPriceKes = 13000, advisory = beansAdvisory),
                CropMarketRecord("Onions", localPriceKes = 6800, hubPriceKes = 8100, advisory = onionsAdvisory),
                CropMarketRecord("Sorghum", localPriceKes = 3900, hubPriceKes = 4600, advisory = sorghumAdvisory)
            )
        )
        put(
            "Nairobi", listOf(
                CropMarketRecord("Maize", localPriceKes = 5200, hubPriceKes = 5900, advisory = maizeAdvisory),
                CropMarketRecord("Beans", localPriceKes = 13500, hubPriceKes = 15200, advisory = beansAdvisory),
                CropMarketRecord("Onions", localPriceKes = 8400, hubPriceKes = 9700, advisory = onionsAdvisory),
                CropMarketRecord("Sorghum", localPriceKes = 5100, hubPriceKes = 5900, advisory = sorghumAdvisory)
            )
        )
        put(
            "Nakuru", listOf(
                CropMarketRecord("Maize", localPriceKes = 4600, hubPriceKes = 5300, advisory = maizeAdvisory),
                CropMarketRecord("Beans", localPriceKes = 12200, hubPriceKes = 13800, advisory = beansAdvisory),
                CropMarketRecord("Onions", localPriceKes = 7200, hubPriceKes = 8500, advisory = onionsAdvisory),
                CropMarketRecord("Sorghum", localPriceKes = 4300, hubPriceKes = 5000, advisory = sorghumAdvisory)
            )
        )
        put(
            "Uasin Gishu", listOf(
                CropMarketRecord("Maize", localPriceKes = 4000, hubPriceKes = 4700, advisory = maizeAdvisory),
                CropMarketRecord("Beans", localPriceKes = 11800, hubPriceKes = 13200, advisory = beansAdvisory),
                CropMarketRecord("Onions", localPriceKes = 7000, hubPriceKes = 8300, advisory = onionsAdvisory),
                CropMarketRecord("Sorghum", localPriceKes = 3700, hubPriceKes = 4400, advisory = sorghumAdvisory)
            )
        )
        put(
            "Mombasa", listOf(
                CropMarketRecord("Maize", localPriceKes = 5600, hubPriceKes = 6300, advisory = maizeAdvisory),
                CropMarketRecord("Beans", localPriceKes = 14200, hubPriceKes = 15900, advisory = beansAdvisory),
                CropMarketRecord("Onions", localPriceKes = 9000, hubPriceKes = 10400, advisory = onionsAdvisory),
                CropMarketRecord("Sorghum", localPriceKes = 5400, hubPriceKes = 6200, advisory = sorghumAdvisory)
            )
        )
    }

    private val countyTowns: Map<String, String> = mapOf(
        "Baringo" to "Marigat",
        "Nakuru" to "Nakuru Town",
        "Nairobi" to "Wakulima Market",
        "Mombasa" to "Kongowea Market",
        "Uasin Gishu" to "Eldoret"
    )

    fun getCounties(): List<String> = all47Counties

    fun getCountyData(countyLabel: String): List<CropMarketRecord> {
        if (countyLabel.isBlank()) return emptyList()
        val key = normalizeCounty(countyLabel)
        return pricingMatrix[key] ?: emptyList()
    }

    fun getCrop(countyLabel: String, cropName: String): CropMarketRecord? =
        getCountyData(countyLabel).firstOrNull { it.cropName.equals(cropName, ignoreCase = true) }

    fun getTransportSummary(countyLabel: String): String {
        if (countyLabel.isBlank()) {
            return "3 Transporters near Marigat available for transit to Nairobi Hub"
        }
        val key = normalizeCounty(countyLabel)
        val town = countyTowns[key] ?: "$key Town"
        val seed = abs(key.hashCode())
        val count = 2 + (seed % 5)
        val rate = 1800 + (seed % 2500)
        return "$count Transporters near $town available for transit to Nairobi Hub from KES $rate/ton"
    }

    fun getInputTrends(countyLabel: String): List<InputPrice> {
        val key = normalizeCounty(countyLabel.ifBlank { "Baringo" })
        val seed = abs(key.hashCode())
        return listOf(
            InputPrice("Maize Seed (2kg)", 1150 + (seed % 450), "2kg pack", if ((seed / 3) % 2 == 0) "▲ +2.1%" else "▼ -1.4%"),
            InputPrice("DAP Fertilizer (50kg)", 5800 + (seed % 900), "50kg bag", if ((seed / 5) % 2 == 0) "▲ +1.2%" else "▼ -0.8%"),
            InputPrice("CAN Top-dress (50kg)", 4600 + (seed % 700), "50kg bag", if ((seed / 7) % 2 == 0) "▲ +0.9%" else "▼ -0.5%")
        )
    }
}
