package com.arbitrage.lofqdii.data.model

data class Fund(
    val code: String,
    val name: String,
    val type: FundType,
    val marketPrice: Double? = null,
    val nav: Double? = null,
    val estimateNav: Double? = null,
    val changePercent: Double? = null,
    val volume: Long? = null,
    val amount: Double? = null,
    val scale: Double? = null,
    val t1PremiumRate: Double? = null,
    val realtimePremiumRate: Double? = null,
    val subscribeStatus: SubscribeStatus = SubscribeStatus.UNKNOWN,
    val subscribeLimit: Double? = null,
    val navDate: String? = null,
    val updateTime: String? = null
) {
    val hasValidData: Boolean
        get() = marketPrice != null && nav != null

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

    val displayMarketPrice: String
        get() = marketPrice?.let { String.format("%.3f", it) } ?: "--"

    val displayNav: String
        get() = nav?.let { String.format("%.4f", it) } ?: "--"

    val displayVolume: String
        get() = volume?.let { formatVolume(it) } ?: "--"

    val displayScale: String
        get() = scale?.let { formatScale(it) } ?: "--"

    val displaySubscribeLimit: String
        get() = subscribeLimit?.let { 
            if (it <= 0) "无限额"
            else String.format("%.2f万", it / 10000)
        } ?: "--"

    private fun formatVolume(vol: Long): String {
        return when {
            vol >= 100000000 -> String.format("%.2f亿", vol / 100000000.0)
            vol >= 10000 -> String.format("%.2f万", vol / 10000.0)
            else -> vol.toString()
        }
    }

    private fun formatScale(s: Double): String {
        return when {
            s >= 100000000 -> String.format("%.2f亿", s / 100000000.0)
            s >= 10000 -> String.format("%.2f万", s / 10000.0)
            else -> String.format("%.2f", s)
        }
    }
}

enum class FundType {
    LOF,
    QDII
}

enum class SubscribeStatus(val displayName: String) {
    OPEN("开放申购"),
    CLOSED("暂停申购"),
    LIMITED("限额申购"),
    UNKNOWN("未知")
}
