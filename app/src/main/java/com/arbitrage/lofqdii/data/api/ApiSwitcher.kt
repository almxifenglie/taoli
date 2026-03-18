package com.arbitrage.lofqdii.data.api

import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.util.PremiumCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

enum class ApiSource(val displayName: String) {
    EASTMONEY("东方财富"),
    TIANTIAN("天天基金"),
    SINA("新浪财经")
}

class ApiSwitcher private constructor() {

    private val eastMoneyApi = EastMoneyApi.getInstance()
    private val tianTianFundApi = TianTianFundApi.getInstance()
    private val sinaFinanceApi = SinaFinanceApi.getInstance()

    private var currentSource = ApiSource.EASTMONEY

    companion object {
        @Volatile
        private var instance: ApiSwitcher? = null

        fun getInstance(): ApiSwitcher {
            return instance ?: synchronized(this) {
                instance ?: ApiSwitcher().also { instance = it }
            }
        }
    }

    fun getCurrentSource(): ApiSource = currentSource

    fun setSource(source: ApiSource) {
        currentSource = source
    }

    suspend fun getLOFList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return when (currentSource) {
            ApiSource.EASTMONEY -> eastMoneyApi.getLOFList(page, pageSize)
            else -> eastMoneyApi.getLOFList(page, pageSize)
        }
    }

    suspend fun getQDIIList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return when (currentSource) {
            ApiSource.EASTMONEY -> eastMoneyApi.getQDIIList(page, pageSize)
            else -> eastMoneyApi.getQDIIList(page, pageSize)
        }
    }

    suspend fun getFundWithPremium(code: String, fundType: FundType): Result<Fund> = coroutineScope {
        try {
            val priceDeferred = async { eastMoneyApi.getFundDetail(code) }
            val navDeferred = async { tianTianFundApi.getFundNav(code) }
            val estimateNavDeferred = async { tianTianFundApi.getFundEstimateNav(code) }
            val subscribeDeferred = async { tianTianFundApi.getSubscribeStatus(code) }

            val priceResult = priceDeferred.await()
            val navResult = navDeferred.await()
            val estimateNavResult = estimateNavDeferred.await()
            val subscribeResult = subscribeDeferred.await()

            if (priceResult.isError) {
                return@coroutineScope Result.error(priceResult.getErrorMessage() ?: "获取价格失败")
            }

            val baseFund = priceResult.getOrNull()!!
            val nav = navResult.getOrNull()?.first
            val navDate = navResult.getOrNull()?.second
            val estimateNav = estimateNavResult.getOrNull()?.first
            val estimateTime = estimateNavResult.getOrNull()?.second
            val subscribeStatus = subscribeResult.getOrNull()?.first ?: SubscribeStatus.UNKNOWN
            val subscribeLimit = subscribeResult.getOrNull()?.second

            val t1PremiumRate = PremiumCalculator.calculateT1PremiumRate(
                baseFund.marketPrice,
                nav
            )

            val realtimePremiumRate = PremiumCalculator.calculateRealtimePremiumRate(
                baseFund.marketPrice,
                estimateNav
            )

            val fund = baseFund.copy(
                type = fundType,
                nav = nav,
                estimateNav = estimateNav,
                t1PremiumRate = t1PremiumRate,
                realtimePremiumRate = realtimePremiumRate,
                subscribeStatus = subscribeStatus,
                subscribeLimit = subscribeLimit,
                navDate = navDate,
                updateTime = estimateTime ?: System.currentTimeMillis().toString()
            )

            Result.success(fund)
        } catch (e: Exception) {
            Result.error("获取基金数据失败: ${e.message}", e)
        }
    }

    suspend fun enrichFunds(funds: List<Fund>): List<Fund> = coroutineScope {
        funds.map { fund ->
            async {
                val result = getFundWithPremium(fund.code, fund.type)
                result.getOrNull() ?: fund
            }
        }.awaitAll()
    }

    suspend fun getFundListWithPremium(
        fundType: FundType,
        page: Int = 1,
        pageSize: Int = 100
    ): Result<List<Fund>> {
        val listResult = if (fundType == FundType.LOF) {
            getLOFList(page, pageSize)
        } else {
            getQDIIList(page, pageSize)
        }

        if (listResult.isError) {
            return listResult
        }

        val funds = listResult.getOrNull() ?: return Result.error("获取列表失败")
        
        val enrichedFunds = funds.take(30).map { fund ->
            getFundWithPremium(fund.code, fundType).getOrNull() ?: fund
        }

        return Result.success(enrichedFunds)
    }

    suspend fun testApiConnection(source: ApiSource): Boolean {
        return try {
            when (source) {
                ApiSource.EASTMONEY -> {
                    val result = eastMoneyApi.getLOFList(1, 1)
                    result.isSuccess
                }
                ApiSource.TIANTIAN -> {
                    val result = tianTianFundApi.getFundEstimate("000001")
                    result.isSuccess
                }
                ApiSource.SINA -> {
                    val result = sinaFinanceApi.getFundPrice("161725")
                    result.isSuccess
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
