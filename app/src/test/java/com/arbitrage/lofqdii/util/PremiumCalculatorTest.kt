package com.arbitrage.lofqdii.util

import org.junit.Assert.*
import org.junit.Test

class PremiumCalculatorTest {

    @Test
    fun calculateT1PremiumRate_correctInput() {
        val result = PremiumCalculator.calculateT1PremiumRate(1.10, 1.00)
        assertEquals(10.0, result!!, 0.001)
    }

    @Test
    fun calculateT1PremiumRate_negativePremium() {
        val result = PremiumCalculator.calculateT1PremiumRate(0.95, 1.00)
        assertEquals(-5.0, result!!, 0.001)
    }

    @Test
    fun calculateT1PremiumRate_nullMarketPrice() {
        val result = PremiumCalculator.calculateT1PremiumRate(null, 1.00)
        assertNull(result)
    }

    @Test
    fun calculateT1PremiumRate_nullNav() {
        val result = PremiumCalculator.calculateT1PremiumRate(1.10, null)
        assertNull(result)
    }

    @Test
    fun calculateT1PremiumRate_zeroNav() {
        val result = PremiumCalculator.calculateT1PremiumRate(1.10, 0.0)
        assertNull(result)
    }

    @Test
    fun calculateRealtimePremiumRate_correctInput() {
        val result = PremiumCalculator.calculateRealtimePremiumRate(1.15, 1.10)
        assertEquals(4.545, result!!, 0.001)
    }

    @Test
    fun calculateRealtimePremiumRate_nullInputs() {
        assertNull(PremiumCalculator.calculateRealtimePremiumRate(null, 1.00))
        assertNull(PremiumCalculator.calculateRealtimePremiumRate(1.10, null))
    }

    @Test
    fun isPremiumOpportunity_aboveDefaultThreshold() {
        assertTrue(PremiumCalculator.isPremiumOpportunity(3.0))
        assertTrue(PremiumCalculator.isPremiumOpportunity(2.1))
        assertFalse(PremiumCalculator.isPremiumOpportunity(2.0))
        assertFalse(PremiumCalculator.isPremiumOpportunity(1.5))
    }

    @Test
    fun isPremiumOpportunity_customThreshold() {
        assertFalse(PremiumCalculator.isPremiumOpportunity(2.5, 3.0))
        assertTrue(PremiumCalculator.isPremiumOpportunity(3.5, 3.0))
    }

    @Test
    fun isDiscountOpportunity_belowThreshold() {
        assertTrue(PremiumCalculator.isDiscountOpportunity(-3.0))
        assertTrue(PremiumCalculator.isDiscountOpportunity(-2.1))
        assertFalse(PremiumCalculator.isDiscountOpportunity(-2.0))
    }

    @Test
    fun getPremiumLevel_classifiesCorrectly() {
        assertEquals(PremiumLevel.VERY_HIGH, PremiumCalculator.getPremiumLevel(6.0))
        assertEquals(PremiumLevel.VERY_HIGH, PremiumCalculator.getPremiumLevel(10.0))
        assertEquals(PremiumLevel.HIGH, PremiumCalculator.getPremiumLevel(3.0))
        assertEquals(PremiumLevel.HIGH, PremiumCalculator.getPremiumLevel(2.5))
        assertEquals(PremiumLevel.NORMAL, PremiumCalculator.getPremiumLevel(1.0))
        assertEquals(PremiumLevel.FLAT, PremiumCalculator.getPremiumLevel(0.0))
        assertEquals(PremiumLevel.LOW, PremiumCalculator.getPremiumLevel(-1.0))
        assertEquals(PremiumLevel.VERY_LOW, PremiumCalculator.getPremiumLevel(-3.0))
        assertEquals(PremiumLevel.EXTREME_LOW, PremiumCalculator.getPremiumLevel(-10.0))
    }

    @Test
    fun getPremiumLevel_nullInput() {
        assertEquals(PremiumLevel.UNKNOWN, PremiumCalculator.getPremiumLevel(null))
    }

    @Test
    fun formatPremiumRate_correctFormat() {
        assertEquals("+10.00%", PremiumCalculator.formatPremiumRate(10.0))
        assertEquals("-5.00%", PremiumCalculator.formatPremiumRate(-5.0))
        assertEquals("+0.00%", PremiumCalculator.formatPremiumRate(0.0))
        assertEquals("--", PremiumCalculator.formatPremiumRate(null))
    }

    @Test
    fun calculateArbitrageProfit_correctCalculation() {
        val profit = PremiumCalculator.calculateArbitrageProfit(5.0, 10000.0)
        assertNotNull(profit)
    }

    @Test
    fun calculateArbitrageProfit_nullPremium() {
        val profit = PremiumCalculator.calculateArbitrageProfit(null, 10000.0)
        assertNull(profit)
    }
}