package com.arbitrage.lofqdii.data.api

import android.util.Log
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
    SINA("新浪财经"),
    COMBINED("多源组合")
}

data class DataSourceInfo(
    val name: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class FundDataResult(
    val fund: Fund,
    val priceSource: DataSourceInfo? = null,
    val navSource: DataSourceInfo? = null,
    val estimateNavSource: DataSourceInfo? = null,
    val subscribeSource: DataSourceInfo? = null
)

class ApiSwitcher private constructor() {

    private val eastMoneyApi = EastMoneyApi.getInstance()
    private val tianTianFundApi = TianTianFundApi.getInstance()
    private val sinaFinanceApi = SinaFinanceApi.getInstance()

    private var currentSource = ApiSource.COMBINED

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

    suspend fun getFundWithPremium(code: String, fundType: FundType): Result<FundDataResult> = coroutineScope {
        try {
            Log.d(TAG, "========== 开始获取基金数据: $code, 类型: $fundType ==========")

            var price: Double? = null
            var volume: Long? = null
            var amount: Double? = null
            var priceSource: DataSourceInfo? = null

            var nav: Double? = null
            var navDate: String? = null
            var navSource: DataSourceInfo? = null

            var estimateNav: Double? = null
            var estimateTime: String? = null
            var estimateNavSource: DataSourceInfo? = null

            var subscribeStatus = SubscribeStatus.UNKNOWN
            var subscribeLimit: Double? = null
            var subscribeSourceInfo: DataSourceInfo? = null

            Log.d(TAG, "并行请求: 价格(东方财富K线 + 新浪) + 净值(天天基金 + 东方财富) + 申购状态(fundf10)")

            val priceKlineDeferred = async {
                Log.d(TAG, "[$code] 开始请求东方财富K线价格...")
                val result = eastMoneyApi.getLOFPriceByKline(code)
                Pair(result, "EastMoney_Kline")
            }

            val priceSinaDeferred = async {
                Log.d(TAG, "[$code] 开始请求新浪价格和成交量...")
                val result = sinaFinanceApi.getFundPriceAndVolume(code)
                Pair(result, "Sina")
            }

            val navTianTianDeferred = async {
                Log.d(TAG, "[$code] 开始请求天天基金净值...")
                val result = tianTianFundApi.getFundNav(code)
                Pair(result, "TianTian")
            }

            val navEastMoneyDeferred = async {
                Log.d(TAG, "[$code] 开始请求东方财富净值...")
                val result = eastMoneyApi.getFundNavFromDetail(code)
                Pair(result, "EastMoney")
            }

            val estimateNavTianTianDeferred = async {
                Log.d(TAG, "[$code] 开始请求天天基金估算净值...")
                val result = tianTianFundApi.getFundEstimateNav(code)
                Pair(result, "TianTian")
            }

            val estimateNavEastMoneyDeferred = async {
                Log.d(TAG, "[$code] 开始请求东方财富估算净值...")
                val result = eastMoneyApi.getFundEstimateNavFromApi(code)
                Pair(result, "EastMoney")
            }

            val subscribeF10Deferred = async {
                Log.d(TAG, "[$code] 开始请求东方财富F10申购状态...")
                val result = eastMoneyApi.getSubscribeStatusFromF10(code)
                Pair(result, "EastMoney_F10")
            }

            val subscribeTianTianDeferred = async {
                Log.d(TAG, "[$code] 开始请求天天基金申购状态...")
                val result = tianTianFundApi.getSubscribeStatus(code)
                Pair(result, "TianTian")
            }

            Log.d(TAG, "[$code] 等待所有数据返回...")
            
            val priceKlineResult = priceKlineDeferred.await()
            val priceSinaResult = priceSinaDeferred.await()
            
            if (priceKlineResult.first.isSuccess) {
                val data = priceKlineResult.first.getOrNull()!!
                price = data.first
                volume = data.second
                amount = data.third
                priceSource = DataSourceInfo(priceKlineResult.second, true)
                Log.d(TAG, "[$code] 东方财富K线价格成功: price=$price, volume=$volume")
            } else {
                Log.w(TAG, "[$code] 东方财富K线价格失败: ${priceKlineResult.first.getErrorMessage()}")
            }

            if (price == null && priceSinaResult.first.isSuccess) {
                val sinaData = priceSinaResult.first.getOrNull()
                price = sinaData?.first
                if (volume == null) {
                    volume = sinaData?.second
                }
                priceSource = DataSourceInfo(priceSinaResult.second, true)
                Log.d(TAG, "[$code] 新浪价格成功: price=$price, volume=$volume")
            } else if (price == null) {
                priceSource = DataSourceInfo(priceSinaResult.second, false, priceSinaResult.first.getErrorMessage())
                Log.w(TAG, "[$code] 新浪价格失败: ${priceSinaResult.first.getErrorMessage()}")
            }

            val navTianTianResult = navTianTianDeferred.await()
            val navEastMoneyResult = navEastMoneyDeferred.await()

            if (navTianTianResult.first.isSuccess) {
                val data = navTianTianResult.first.getOrNull()!!
                nav = data.first
                navDate = data.second
                navSource = DataSourceInfo(navTianTianResult.second, true)
                Log.d(TAG, "[$code] 天天基金净值成功: nav=$nav, date=$navDate")
            } else {
                Log.w(TAG, "[$code] 天天基金净值失败: ${navTianTianResult.first.getErrorMessage()}")
            }

            if (nav == null && navEastMoneyResult.first.isSuccess) {
                val data = navEastMoneyResult.first.getOrNull()!!
                nav = data.first
                navDate = data.second
                navSource = DataSourceInfo(navEastMoneyResult.second, true)
                Log.d(TAG, "[$code] 东方财富净值成功: nav=$nav, date=$navDate")
            } else if (nav == null) {
                if (navSource == null) {
                    navSource = DataSourceInfo(navEastMoneyResult.second, false, navEastMoneyResult.first.getErrorMessage())
                }
                Log.e(TAG, "[$code] 所有净值源失败")
            }

            val estimateNavTianTianResult = estimateNavTianTianDeferred.await()
            val estimateNavEastMoneyResult = estimateNavEastMoneyDeferred.await()

            if (estimateNavTianTianResult.first.isSuccess) {
                val data = estimateNavTianTianResult.first.getOrNull()!!
                estimateNav = data.first
                estimateTime = data.second
                estimateNavSource = DataSourceInfo(estimateNavTianTianResult.second, true)
                Log.d(TAG, "[$code] 天天基金估算净值成功: estimateNav=$estimateNav")
            } else {
                Log.w(TAG, "[$code] 天天基金估算净值失败: ${estimateNavTianTianResult.first.getErrorMessage()}")
            }

            if (estimateNav == null && estimateNavEastMoneyResult.first.isSuccess) {
                val data = estimateNavEastMoneyResult.first.getOrNull()!!
                estimateNav = data.first
                estimateTime = data.second
                estimateNavSource = DataSourceInfo(estimateNavEastMoneyResult.second, true)
                Log.d(TAG, "[$code] 东方财富估算净值成功: estimateNav=$estimateNav")
            } else if (estimateNav == null) {
                if (estimateNavSource == null) {
                    estimateNavSource = DataSourceInfo(estimateNavEastMoneyResult.second, false, estimateNavEastMoneyResult.first.getErrorMessage())
                }
                Log.e(TAG, "[$code] 所有估算净值源失败")
            }

            val subscribeF10Result = subscribeF10Deferred.await()
            val subscribeTianTianResult = subscribeTianTianDeferred.await()

            val f10Data = subscribeF10Result.first.getOrNull()
            val ttData = subscribeTianTianResult.first.getOrNull()

            when {
                subscribeF10Result.first.isSuccess && f10Data != null && f10Data.first != SubscribeStatus.UNKNOWN -> {
                    subscribeStatus = f10Data.first
                    subscribeLimit = f10Data.second
                    subscribeSourceInfo = DataSourceInfo(subscribeF10Result.second, true)
                    Log.d(TAG, "[$code] 东方财富F10申购状态成功: status=$subscribeStatus, limit=$subscribeLimit")
                }
                subscribeTianTianResult.first.isSuccess && ttData != null -> {
                    subscribeStatus = ttData.first
                    subscribeLimit = ttData.second
                    subscribeSourceInfo = DataSourceInfo(subscribeTianTianResult.second, true)
                    Log.d(TAG, "[$code] 天天基金申购状态成功(备用): status=$subscribeStatus, limit=$subscribeLimit")
                }
                f10Data != null && f10Data.first != SubscribeStatus.UNKNOWN -> {
                    subscribeStatus = f10Data.first
                    subscribeLimit = f10Data.second
                    subscribeSourceInfo = DataSourceInfo(subscribeF10Result.second, true)
                    Log.d(TAG, "[$code] 东方财富F10申购状态(网络失败但有数据): status=$subscribeStatus, limit=$subscribeLimit")
                }
                else -> {
                    val f10Error = subscribeF10Result.first.getErrorMessage()
                    val ttError = subscribeTianTianResult.first.getErrorMessage()
                    subscribeSourceInfo = DataSourceInfo("Combined", false, "东方财富F10: $f10Error; 天天基金: $ttError")
                    Log.e(TAG, "[$code] 申购状态全部失败: 东方财富F10=$f10Error, 天天基金=$ttError")
                }
            }

            val t1PremiumRate = PremiumCalculator.calculateT1PremiumRate(price, nav)
            val realtimePremiumRate = PremiumCalculator.calculateRealtimePremiumRate(price, estimateNav ?: nav)

            Log.d(TAG, "[$code] 溢价率计算: T-1=$t1PremiumRate%, 实时=$realtimePremiumRate%")

            val fund = Fund(
                code = code,
                name = "",
                type = fundType,
                marketPrice = price,
                nav = nav,
                estimateNav = estimateNav,
                changePercent = null,
                volume = volume,
                amount = amount,
                t1PremiumRate = t1PremiumRate,
                realtimePremiumRate = realtimePremiumRate,
                subscribeStatus = subscribeStatus,
                subscribeLimit = subscribeLimit,
                navDate = navDate,
                updateTime = estimateTime ?: System.currentTimeMillis().toString()
            )

            val result = FundDataResult(
                fund = fund,
                priceSource = priceSource,
                navSource = navSource,
                estimateNavSource = estimateNavSource,
                subscribeSource = subscribeSourceInfo
            )

            Log.d(TAG, "========== 基金数据获取完成: $code ==========")
            Log.d(TAG, "结果摘要: price=$price(${priceSource?.name}:${priceSource?.success}), " +
                    "nav=$nav(${navSource?.name}:${navSource?.success}), " +
                    "subscribe=$subscribeStatus(${subscribeSourceInfo?.name}:${subscribeSourceInfo?.success})")

            Result.success(result)
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
                    val result = tianTianFundApi.getFundEstimate("161725")
                    result.isSuccess
                }
                ApiSource.SINA -> {
                    val result = sinaFinanceApi.getFundPrice("161725")
                    result.isSuccess
                }
                ApiSource.COMBINED -> {
                    val result = eastMoneyApi.getLOFList(1, 1)
                    result.isSuccess
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API test error: ${e.message}")
            false
        }
    }
}
