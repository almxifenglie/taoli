package com.arbitrage.lofqdii.util

import kotlin.math.abs

object PremiumCalculator {
    
    fun calculateT1PremiumRate(marketClosePrice: Double?, nav: Double?): Double? {
        if (marketClosePrice == null || nav == null || nav == 0.0) return null
        return (marketClosePrice - nav) / nav * 100
    }

    fun calculateRealtimePremiumRate(
        currentPrice: Double?,
        estimateNav: Double?
    ): Double? {
        if (currentPrice == null || estimateNav == null || estimateNav == 0.0) return null
        return (currentPrice - estimateNav) / estimateNav * 100
    }

    fun formatPremiumRate(rate: Double?): String {
        if (rate == null) return "--"
        val sign = if (rate >= 0) "+" else ""
        return "$sign${String.format("%.2f", rate)}%"
    }

    fun isPremiumOpportunity(rate: Double?, threshold: Double = 2.0): Boolean {
        return (rate ?: 0.0) > threshold
    }

    fun isDiscountOpportunity(rate: Double?, threshold: Double = -2.0): Boolean {
        return (rate ?: 0.0) < threshold
    }

    fun getPremiumLevel(rate: Double?): PremiumLevel {
        if (rate == null) return PremiumLevel.UNKNOWN
        return when {
            rate > 5.0 -> PremiumLevel.VERY_HIGH
            rate > 2.0 -> PremiumLevel.HIGH
            rate > 0.5 -> PremiumLevel.NORMAL
            rate > -0.5 -> PremiumLevel.FLAT
            rate > -2.0 -> PremiumLevel.LOW
            rate > -5.0 -> PremiumLevel.VERY_LOW
            else -> PremiumLevel.EXTREME_LOW
        }
    }

    fun calculateArbitrageProfit(
        premiumRate: Double?,
        amount: Double,
        tradingFee: Double = 0.0015,
        subscriptionFee: Double = 0.012,
        redemptionFee: Double = 0.005,
        slippage: Double = 0.001
    ): Double? {
        if (premiumRate == null) return null
        val totalFee = tradingFee + subscriptionFee + redemptionFee + slippage
        val netPremium = premiumRate / 100 - totalFee
        return amount * netPremium
    }
}

enum class PremiumLevel(val displayName: String, val color: String) {
    VERY_HIGH("极高溢价", "#F44336"),
    HIGH("高溢价", "#FF5722"),
    NORMAL("正常溢价", "#FF9800"),
    FLAT("平价", "#9E9E9E"),
    LOW("折价", "#4CAF50"),
    VERY_LOW("深度折价", "#2196F3"),
    EXTREME_LOW("极度折价", "#673AB7"),
    UNKNOWN("未知", "#9E9E9E")
}
