package com.arbitrage.lofqdii.data.model

data class PremiumInfo(
    val code: String,
    val name: String,
    val t1PremiumRate: Double? = null,
    val realtimePremiumRate: Double? = null,
    val marketPrice: Double? = null,
    val nav: Double? = null,
    val estimateNav: Double? = null,
    val updateTime: String? = null
) {
    val hasValidT1Premium: Boolean
        get() = t1PremiumRate != null

    val hasValidRealtimePremium: Boolean
        get() = realtimePremiumRate != null

    val isPremiumOpportunity: Boolean
        get() = (t1PremiumRate ?: 0.0) > 2.0 || (realtimePremiumRate ?: 0.0) > 2.0

    val isDiscountOpportunity: Boolean
        get() = (t1PremiumRate ?: 0.0) < -2.0 || (realtimePremiumRate ?: 0.0) < -2.0
}
