package com.example.m_agrilink

import com.example.m_agrilink.data.MarketDataRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the SACCO planner matrix follows the farmer's typed crop instead
 * of falling back to the hardcoded maize stream.
 */
class CropMatrixRoutingTest {

    @Test
    fun mango_routesToOrchardMatrix() {
        val matrix = MarketDataRepository.getCropInputMatrix("Mango")
        assertEquals("mango", matrix.id)
        assertEquals(3, matrix.lines.size)
        val seedling = matrix.lines[0]
        assertEquals(150, seedling.unitPriceKes)
        assertEquals(100.0, seedling.unitsPerAcre, 0.0)
        assertEquals(350, matrix.lines[2].unitPriceKes)
        assertEquals(10.0, matrix.lines[2].unitsPerAcre, 0.0)
    }

    @Test
    fun beans_routesToLegumeMatrix() {
        val matrix = MarketDataRepository.getCropInputMatrix("beans")
        assertEquals("beans", matrix.id)
        assertEquals(450, matrix.lines[0].unitPriceKes)
        assertEquals(10.0, matrix.lines[0].unitsPerAcre, 0.0)
        assertEquals(2000, matrix.lines[1].unitPriceKes)
    }

    @Test
    fun maize_usesSubsidizedSeedAndDap() {
        val matrix = MarketDataRepository.getCropInputMatrix("maize", maizeSeedPack = "2kg")
        assertEquals("maize", matrix.id)
        assertEquals(300, matrix.lines[0].unitPriceKes)
        assertEquals(5.0, matrix.lines[0].unitsPerAcre, 0.0)
        assertEquals(2000, matrix.lines[1].unitPriceKes)
    }

    @Test
    fun mango_twoAcreProjection_matchesSpecExample() {
        val matrix = MarketDataRepository.getCropInputMatrix("mango")
        val costs = matrix.lines.map { (it.unitsPerAcre * 2).toInt() * it.unitPriceKes }
        assertEquals(listOf(30000, 8000, 7000), costs)
        assertEquals(45000, costs.sum())
    }

    @Test
    fun unknownCrop_fallsBackToStandardMatrix() {
        val matrix = MarketDataRepository.getCropInputMatrix("coffee")
        assertEquals("cash", matrix.id)
    }

    @Test
    fun everyCrop_getsANamedPlan() {
        val cases = mapOf(
            "Onions" to "vegetable",
            "Potatoes" to "potato",
            "Sorghum" to "cereal",
            "Avocado" to "orchard",
            "Green grams" to "legume",
            "Soybean" to "legume",
            "Tea" to "cash",
            "Wheat" to "cereal"
        )
        for ((crop, expectedId) in cases) {
            val matrix = MarketDataRepository.getCropInputMatrix(crop)
            assertEquals("crop=$crop", expectedId, matrix.id)
        }
    }

    @Test
    fun estimates_areFlagged_unverifiedSpecsAreNot() {
        assertTrue(MarketDataRepository.getCropInputMatrix("Mango").verified)
        assertTrue(MarketDataRepository.getCropInputMatrix("Beans").verified)
        assertTrue(MarketDataRepository.getCropInputMatrix("Maize").verified)
        assertTrue(!MarketDataRepository.getCropInputMatrix("Onions").verified)
        assertTrue(!MarketDataRepository.getCropInputMatrix("Sorghum").verified)
    }

    @Test
    fun onion_oneAcreProjection_isLive() {
        val matrix = MarketDataRepository.getCropInputMatrix("Onions")
        val costs = matrix.lines.map { (it.unitsPerAcre * 1).toInt() * it.unitPriceKes }
        assertEquals(listOf(2600, 2000, 1800), costs)
        assertEquals(6400, costs.sum())
    }

    @Test
    fun potatoes_planByTuberCount() {
        val matrix = MarketDataRepository.getCropInputMatrix("Potatoes")
        assertEquals("potato", matrix.id)
        assertEquals(4000, matrix.lines[0].unitPriceKes)
        assertEquals(10.0, matrix.lines[0].unitsPerAcre, 0.0)
        val costs = matrix.lines.map { (it.unitsPerAcre * 1).toInt() * it.unitPriceKes }
        assertEquals(listOf(40000, 4000, 1800), costs)
        assertEquals(45800, costs.sum())
    }

    @Test
    fun pineapple_doesNotHijackOrchardRule() {
        assertEquals("standard", MarketDataRepository.getCropInputMatrix("Pineapple").id)
    }

    @Test
    fun greenTea_routesToCash() {
        assertEquals("cash", MarketDataRepository.getCropInputMatrix("Green Tea").id)
    }
}
