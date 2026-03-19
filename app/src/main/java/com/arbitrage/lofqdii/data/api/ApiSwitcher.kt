package com.arbitrage.lofqdii.data.api

import android.util.Log
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.util.PremiumCalculator
import kotlinx.coroutines.async
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
        private const val TAG = "ApiSwitcher"

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
        return eastMoneyApi.getLOFList(page, pageSize)
    }

    suspend fun getQDIIList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return eastMoneyApi.getQDIIList(page, pageSize)
    }

    suspend fun getFundWithPremium(code: String, fundType: FundType): Result<Fund> = coroutineScope {
        try {
            Log.d(TAG, "Getting fund with premium: $code, type: $fundType")

            val priceDeferred = async { eastMoneyApi.getFundDetail(code) }
            val navDeferred = async { tianTianFundApi.getFundNav(code) }
            val estimateNavDeferred = async { tianTianFundApi.getFundEstimateNav(code) }
            val subscribeDeferred = async { tianTianFundApi.getSubscribeStatus(code) }

            val priceResult = priceDeferred.await()
            val navResult = navDeferred.await()
            val estimateNavResult = estimateNavDeferred.await()
            val subscribeResult = subscribeDeferred.await()

            if (priceResult.isError) {
                val errorMsg = priceResult.getErrorMessage() ?: "获取价格失败"
                Log.e(TAG, "Price error for $code: $errorMsg")
                return@coroutineScope Result.error(errorMsg)
            }

            val baseFund = priceResult.getOrNull()!!
            Log.d(TAG, "Got price for $code: ${baseFund.marketPrice}")

            var nav: Double? = null
            var navDate: String? = null
            var estimateNav: Double? = null
            var estimateTime: String? = null
            var subscribeStatus = SubscribeStatus.UNKNOWN
            var subscribeLimit: Double? = null

            navResult.getOrNull()?.let {
                nav = it.first
                navDate = it.second
                Log.d(TAG, "Got nav for $code: $nav")
            }
            if (navResult.isError) {
                Log.w(TAG, "Nav error for $code: ${navResult.getErrorMessage()}")
            }

            estimateNavResult.getOrNull()?.let {
                estimateNav = it.first
                estimateTime = it.second
                Log.d(TAG, "Got estimateNav for $code: $estimateNav")
            }
            if (estimateNavResult.isError) {
                Log.w(TAG, "EstimateNav error for $code: ${estimateNavResult.getErrorMessage()}")
            }

            subscribeResult.getOrNull()?.let {
                subscribeStatus = it.first
                subscribeLimit = it.second
                Log.d(TAG, "Got subscribeStatus for $code: $subscribeStatus, limit: $subscribeLimit")
            }
            if (subscribeResult.isError) {
                Log.w(TAG, "SubscribeStatus error for $code: ${subscribeResult.getErrorMessage()}")
            }

            val t1PremiumRate = PremiumCalculator.calculateT1PremiumRate(
                baseFund.marketPrice,
                nav
            )
            Log.d(TAG, "T-1 premium for $code: $t1PremiumRate")

            val realtimePremiumRate = PremiumCalculator.calculateRealtimePremiumRate(
                baseFund.marketPrice,
                estimateNav ?: nav
            )
            Log.d(TAG, "Realtime premium for $code: $realtimePremiumRate")

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
            Log.e(TAG, "getFundWithPremium error for $code: ${e.message}", e)
            Result.error("获取基金数据失败: ${e.message}", e)
        }
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
            Log.e(TAG, "API test error: ${e.message}")
            false
        }
    }
}
