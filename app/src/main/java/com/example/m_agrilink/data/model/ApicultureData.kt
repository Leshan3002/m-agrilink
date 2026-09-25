package com.example.m_agrilink.data.model

/**
 * FEATURE 1: ABIRI VALUE-CHAIN MATRIX (APICULTURE)
 * Specialized data structures for KALRO ABIRI honeybee management and market arbitrage.
 */

data class BeeProduct(
    val name: String,
    val localPrice: Double,
    val targetHubPrice: Double,
    val unit: String = "kg"
)

data class CountyApicultureProfile(
    val countyName: String,
    val primaryForage: List<String>,
    val products: List<BeeProduct>,
    val weatherAlertThresholds: WeatherThresholds = WeatherThresholds()
)

data class WeatherThresholds(
    val minTemp: Float = 15f,
    val maxTemp: Float = 38f,
    val maxWindSpeed: Float = 20f
)

object ApicultureRegistry {
    
    val countyProfiles = mapOf(
        "Baringo" to CountyApicultureProfile(
            countyName = "Baringo",
            primaryForage = listOf("Acacia", "Prosopis (Mathenge)", "Wildflowers"),
            products = listOf(
                BeeProduct("Morgia/Acacia Honey", 900.0, 1400.0),
                BeeProduct("Bee Venom Extraction", 4500.0, 7500.0, "gram"),
                BeeProduct("Stingless Bee Propolis", 1500.0, 2200.0, "100ml"),
                BeeProduct("Beeswax Matrix", 600.0, 850.0)
            )
        ),
        "Marigat" to CountyApicultureProfile(
            countyName = "Marigat",
            primaryForage = listOf("Prosopis Juliflora", "Acacia Tortilis"),
            products = listOf(
                BeeProduct("Morgia Honey", 850.0, 1350.0),
                BeeProduct("Propolis Tincture", 1200.0, 1900.0, "100ml"),
                BeeProduct("Pollen Matrix", 2000.0, 3200.0)
            )
        ),
        "Kitui" to CountyApicultureProfile(
            countyName = "Kitui",
            primaryForage = listOf("Acacia", "Mango Blossoms", "Sunflower"),
            products = listOf(
                BeeProduct("Multi-floral Honey", 800.0, 1200.0),
                BeeProduct("Beeswax Matrix", 550.0, 800.0)
            )
        ),
        "Nairobi Hub" to CountyApicultureProfile(
            countyName = "Nairobi Hub",
            primaryForage = listOf("Ornamental flowers", "Garden eucalyptus"),
            products = listOf(
                BeeProduct("Premium Honey", 1100.0, 1500.0),
                BeeProduct("Royal Jelly", 5000.0, 8000.0, "50g")
            )
        )
    )

    fun getCountyList(): List<String> = countyProfiles.keys.toList().sorted()
    
    fun getProfileForCounty(name: String): CountyApicultureProfile? = countyProfiles[name]
}
