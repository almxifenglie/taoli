package com.arbitrage.lofqdii.data.api

import android.util.Log
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.util.PremiumCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

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
                result to "EastMoney_Kline"
            }

            val priceSinaDeferred = async {
                Log.d(TAG, "[$code] 开始请求新浪价格...")
                val result = sinaFinanceApi.getFundPriceOnly(code)
                result to "Sina"
            }

            val navTianTianDeferred = async {
                Log.d(TAG, "[$code] 开始请求天天基金净值...")
                val result = tianTianFundApi.getFundNav(code)
                result to "TianTian"
            }

            val navEastMoneyDeferred = async {
                Log.d(TAG, "[$code] 开始请求东方财富净值...")
                val result = eastMoneyApi.getFundNavFromDetail(code)
                result to "EastMoney"
            }

            val estimateNavTianTianDeferred = async {
                Log.d(TAG, "[$code] 开始请求天天基金估算净值...")
                val result = tianTianFundApi.getFundEstimateNav(code)
                result to "TianTian"
            }

            val estimateNavEastMoneyDeferred = async {
                Log.d(TAG, "[$code] 开始请求东方财富估算净值...")
                val result = eastMoneyApi.getFundEstimateNavFromApi(code)
                result to "EastMoney"
            }

            val subscribeF10Deferred = async {
                Log.d(TAG, "[$code] 开始请求fundf10申购状态...")
                val result = eastMoneyApi.getSubscribeStatusFromF10(code)
                result to "EastMoney_F10"
            }

            Log.d(TAG, "[$code] 等待价格数据返回 (K线 vs 新浪)...")
            
            select<Unit> {
                priceKlineDeferred.onAwait { (result, source) ->
                    if (result.isSuccess) {
                        val data = result.getOrNull()!!
                        price = data.first
                        volume = data.second
                        amount = data.third
                        priceSource = DataSourceInfo(source, true)
                        Log.d(TAG, "[$code] 东方财富K线价格成功: price=$price, volume=$volume")
                    } else {
                        Log.w(TAG, "[$code] 东方财富K线价格失败: ${result.getErrorMessage()}")
                        priceSource = DataSourceInfo(source, false, result.getErrorMessage())
                    }
                }
                priceSinaDeferred.onAwait { (result, source) ->
                    if (result.isSuccess && price == null) {
                        price = result.getOrNull()
                        priceSource = DataSourceInfo(source, true)
                        Log.d(TAG, "[$code] 新浪价格成功: price=$price")
                    } else if (result.isError && priceSource == null) {
                        Log.w(TAG, "[$code] 新浪价格失败: ${result.getErrorMessage()}")
                    }
                }
            }

            if (price == null) {
                Log.d(TAG, "[$code] 等待另一个价格数据源...")
                val (result, source) = if (priceKlineDeferred.isCompleted) {
                    priceSinaDeferred.await()
                } else {
                    priceKlineDeferred.await()
                }
                
                if (result.isSuccess) {
                    if (result.getOrNull() is Triple<*, *, *>) {
                        val data = result.getOrNull() as Triple<Double?, Long?, Double?>
                        price = data.first
                        volume = data.second
                        amount = data.third
                    } else {
                        price = result.getOrNull()
                    }
                    priceSource = DataSourceInfo(source, true)
                    Log.d(TAG, "[$code] 备用价格源成功: price=$price")
                } else {
                    priceSource = DataSourceInfo(source, false, result.getErrorMessage())
                    Log.e(TAG, "[$code] 备用价格源失败: ${result.getErrorMessage()}")
                }
            }

            Log.d(TAG, "[$code] 等待净值数据返回 (天天基金 vs 东方财富)...")
            
            select<Unit> {
                navTianTianDeferred.onAwait { (result, source) ->
                    if (result.isSuccess) {
                        val data = result.getOrNull()!!
                        nav = data.first
                        navDate = data.second
                        navSource = DataSourceInfo(source, true)
                        Log.d(TAG, "[$code] 天天基金净值成功: nav=$nav, date=$navDate")
                    } else {
                        Log.w(TAG, "[$code] 天天基金净值失败: ${result.getErrorMessage()}")
                        navSource = DataSourceInfo(source, false, result.getErrorMessage())
                    }
                }
                navEastMoneyDeferred.onAwait { (result, source) ->
                    if (result.isSuccess && nav == null) {
                        val data = result.getOrNull()!!
                        nav = data.first
                        navDate = data.second
                        navSource = DataSourceInfo(source, true)
                        Log.d(TAG, "[$code] 东方财富净值成功: nav=$nav, date=$navDate")
                    } else if (result.isError && navSource == null) {
                        Log.w(TAG, "[$code] 东方财富净值失败: ${result.getErrorMessage()}")
                    }
                }
            }

            if (nav == null) {
                Log.d(TAG, "[$code] 等待另一个净值数据源...")
                val (result, source) = if (navTianTianDeferred.isCompleted) {
                    navEastMoneyDeferred.await()
                } else {
                    navTianTianDeferred.await()
                }
                
                if (result.isSuccess) {
                    val data = result.getOrNull()!!
                    nav = data.first
                    navDate = data.second
                    navSource = DataSourceInfo(source, true)
                    Log.d(TAG, "[$code] 备用净值源成功: nav=$nav")
                } else {
                    if (navSource == null) {
                        navSource = DataSourceInfo(source, false, result.getErrorMessage())
                    }
                    Log.e(TAG, "[$code] 备用净值源失败: ${result.getErrorMessage()}")
                }
            }

            Log.d(TAG, "[$code] 等待估算净值数据返回...")
            
            select<Unit> {
                estimateNavTianTianDeferred.onAwait { (result, source) ->
                    if (result.isSuccess) {
                        val data = result.getOrNull()!!
                        estimateNav = data.first
                        estimateTime = data.second
                        estimateNavSource = DataSourceInfo(source, true)
                        Log.d(TAG, "[$code] 天天基金估算净值成功: estimateNav=$estimateNav")
                    } else {
                        Log.w(TAG, "[$code] 天天基金估算净值失败: ${result.getErrorMessage()}")
                        estimateNavSource = DataSourceInfo(source, false, result.getErrorMessage())
                    }
                }
                estimateNavEastMoneyDeferred.onAwait { (result, source) ->
                    if (result.isSuccess && estimateNav == null) {
                        val data = result.getOrNull()!!
                        estimateNav = data.first
                        estimateTime = data.second
                        estimateNavSource = DataSourceInfo(source, true)
                        Log.d(TAG, "[$code] 东方财富估算净值成功: estimateNav=$estimateNav")
                    } else if (result.isError && estimateNavSource == null) {
                        Log.w(TAG, "[$code] 东方财富估算净值失败: ${result.getErrorMessage()}")
                    }
                }
            }

            if (estimateNav == null) {
                Log.d(TAG, "[$code] 等待另一个估算净值数据源...")
                val (result, source) = if (estimateNavTianTianDeferred.isCompleted) {
                    estimateNavEastMoneyDeferred.await()
                } else {
                    estimateNavTianTianDeferred.await()
                }
                
                if (result.isSuccess) {
                    val data = result.getOrNull()!!
                    estimateNav = data.first
                    estimateTime = data.second
                    estimateNavSource = DataSourceInfo(source, true)
                    Log.d(TAG, "[$code] 备用估算净值源成功: estimateNav=$estimateNav")
                } else {
                    if (estimateNavSource == null) {
                        estimateNavSource = DataSourceInfo(source, false, result.getErrorMessage())
                    }
                    Log.e(TAG, "[$code] 备用估算净值源失败: ${result.getErrorMessage()}")
                }
            }

            Log.d(TAG, "[$code] 等待申购状态数据...")
            val (subscribeResult, subscribeSource) = subscribeF10Deferred.await()
            if (subscribeResult.isSuccess) {
                val data = subscribeResult.getOrNull()!!
                subscribeStatus = data.first
                subscribeLimit = data.second
                subscribeSourceInfo = DataSourceInfo(subscribeSource, true)
                Log.d(TAG, "[$code] 申购状态成功: status=$subscribeStatus, limit=$subscribeLimit")
            } else {
                subscribeSourceInfo = DataSourceInfo(subscribeSource, false, subscribeResult.getErrorMessage())
                Log.e(TAG, "[$code] 申购状态失败: ${subscribeResult.getErrorMessage()}")
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
