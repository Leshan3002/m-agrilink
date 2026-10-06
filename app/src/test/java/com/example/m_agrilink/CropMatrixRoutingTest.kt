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
        assertEquals("maize", matrix.id)
        assertTrue(matrix.lines.isNotEmpty())
    }
}
