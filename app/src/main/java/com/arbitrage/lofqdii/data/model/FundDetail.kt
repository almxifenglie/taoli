package com.arbitrage.lofqdii.data.model

data class FundDetail(
    val code: String,
    val name: String,
    val type: FundType,
    val marketPrice: Double? = null,
    val prevClosePrice: Double? = null,
    val openPrice: Double? = null,
    val highPrice: Double? = null,
    val lowPrice: Double? = null,
    val nav: Double? = null,
    val estimateNav: Double? = null,
    val estimateChangePercent: Double? = null,
    val t1PremiumRate: Double? = null,
    val realtimePremiumRate: Double? = null,
    val volume: Long? = null,
    val amount: Double? = null,
    val scale: Double? = null,
    val subscribeStatus: SubscribeStatus = SubscribeStatus.UNKNOWN,
    val subscribeLimit: Double? = null,
    val redeemStatus: RedeemStatus = RedeemStatus.UNKNOWN,
    val redeemLimit: Double? = null,
    val managementFee: Double? = null,
    val custodyFee: Double? = null,
    val navDate: String? = null,
    val updateTime: String? = null,
    val fundManager: String? = null,
    val fundCompany: String? = null,
    val establishDate: String? = null
) {
    val hasValidPrice: Boolean
        get() = marketPrice != null

    val hasValidNav: Boolean
        get() = nav != null

    val changeAmount: Double?
        get() = if (marketPrice != null && prevClosePrice != null) {
            marketPrice - prevClosePrice
        } else null

    val changePercent: Double?
        get() = if (marketPrice != null && prevClosePrice != null && prevClosePrice != 0.0) {
            (marketPrice - prevClosePrice) / prevClosePrice * 100
        } else null

    val displayChange: String
        get() {
            val change = changeAmount ?: return "--"
            val percent = changePercent ?: return "--"
            val sign = if (change >= 0) "+" else ""
            return "$sign${String.format("%.3f", change)} ($sign${String.format("%.2f", percent)}%)"
        }

    val displayT1PremiumRate: String
        get() = t1PremiumRate?.let {
            val sign = if (it >= 0) "+" else ""
            "$sign${String.format("%.2f", it)}%"
        } ?: "--"

    val displayRealtimePremiumRate: String
        get() = realtimePremiumRate?.let {
            val sign = if (it >= 0) "+" else ""
            "$sign${String.format("%.2f", it)}%"
        } ?: "--"
}

enum class RedeemStatus(val displayName: String) {
    OPEN("开放赎回"),
    CLOSED("暂停赎回"),
    LIMITED("限额赎回"),
    UNKNOWN("未知")
}
