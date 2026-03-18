package com.arbitrage.lofqdii.data.repository

import com.arbitrage.lofqdii.data.api.ApiSource
import com.arbitrage.lofqdii.data.api.ApiSwitcher
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundDetail
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.data.api.EastMoneyApi
import com.arbitrage.lofqdii.data.api.TianTianFundApi
import com.arbitrage.lofqdii.util.PremiumCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FundRepository private constructor() {

    private val apiSwitcher = ApiSwitcher.getInstance()
    private val eastMoneyApi = EastMoneyApi.getInstance()
    private val tianTianFundApi = TianTianFundApi.getInstance()

    companion object {
        @Volatile
        private var instance: FundRepository? = null

        fun getInstance(): FundRepository {
            return instance ?: synchronized(this) {
                instance ?: FundRepository().also { instance = it }
            }
        }
    }

    fun getApiSource(): ApiSource = apiSwitcher.getCurrentSource()

    fun setApiSource(source: ApiSource) {
        apiSwitcher.setSource(source)
    }

    suspend fun getLOFFundList(
        page: Int = 1,
        pageSize: Int = 50,
        enrichPremium: Boolean = true
    ): Result<List<Fund>> = withContext(Dispatchers.IO) {
        try {
            val listResult = apiSwitcher.getLOFList(page, pageSize)
            if (listResult.isError) {
                return@withContext listResult
            }

            val funds = listResult.getOrNull() ?: return@withContext Result.error("获取列表失败")

            if (enrichPremium) {
                val enrichedFunds = enrichFundList(funds)
                Result.success(enrichedFunds.sortedByDescending { it.t1PremiumRate })
            } else {
                Result.success(funds)
            }
        } catch (e: Exception) {
            Result.error("获取LOF列表失败: ${e.message}", e)
        }
    }

    suspend fun getQDIIFundList(
        page: Int = 1,
        pageSize: Int = 50,
        enrichPremium: Boolean = true
    ): Result<List<Fund>> = withContext(Dispatchers.IO) {
        try {
            val listResult = apiSwitcher.getQDIIList(page, pageSize)
            if (listResult.isError) {
                return@withContext listResult
            }

            val funds = listResult.getOrNull() ?: return@withContext Result.error("获取列表失败")

            if (enrichPremium) {
                val enrichedFunds = enrichFundList(funds)
                Result.success(enrichedFunds.sortedByDescending { it.t1PremiumRate })
            } else {
                Result.success(funds)
            }
        } catch (e: Exception) {
            Result.error("获取QDII列表失败: ${e.message}", e)
        }
    }

    suspend fun getFundDetail(code: String, fundType: FundType): Result<Fund> {
        return apiSwitcher.getFundWithPremium(code, fundType)
    }

    fun getLOFFundListFlow(
        page: Int = 1,
        pageSize: Int = 50,
        enrichPremium: Boolean = true
    ): Flow<Result<List<Fund>>> = flow {
        emit(Result.loading())
        val result = getLOFFundList(page, pageSize, enrichPremium)
        emit(result)
    }.flowOn(Dispatchers.IO)

    fun getQDIIFundListFlow(
        page: Int = 1,
        pageSize: Int = 50,
        enrichPremium: Boolean = true
    ): Flow<Result<List<Fund>>> = flow {
        emit(Result.loading())
        val result = getQDIIFundList(page, pageSize, enrichPremium)
        emit(result)
    }.flowOn(Dispatchers.IO)

    private suspend fun enrichFundList(funds: List<Fund>): List<Fund> = coroutineScope {
        funds.map { fund ->
            async {
                try {
                    val enrichedResult = apiSwitcher.getFundWithPremium(fund.code, fund.type)
                    enrichedResult.getOrNull() ?: fund
                } catch (e: Exception) {
                    fund
                }
            }
        }.awaitAll()
    }

    suspend fun searchFund(keyword: String, fundType: FundType?): Result<List<Fund>> = withContext(Dispatchers.IO) {
        try {
            val lofResult = if (fundType == null || fundType == FundType.LOF) {
                getLOFFundList(1, 100, false)
            } else null

            val qdiiResult = if (fundType == null || fundType == FundType.QDII) {
                getQDIIFundList(1, 100, false)
            } else null

            val allFunds = mutableListOf<Fund>()
            lofResult?.getOrNull()?.let { allFunds.addAll(it) }
            qdiiResult?.getOrNull()?.let { allFunds.addAll(it) }

            val filtered = allFunds.filter { fund ->
                fund.code.contains(keyword, ignoreCase = true) ||
                fund.name.contains(keyword, ignoreCase = true)
            }

            if (filtered.isEmpty()) {
                Result.error("未找到匹配的基金")
            } else {
                val enriched = enrichFundList(filtered.take(20))
                Result.success(enriched.sortedByDescending { it.t1PremiumRate })
            }
        } catch (e: Exception) {
            Result.error("搜索失败: ${e.message}", e)
        }
    }

    suspend fun getTopPremiumFunds(
        fundType: FundType,
        topN: Int = 10,
        minPremium: Double = 0.0
    ): Result<List<Fund>> {
        val result = if (fundType == FundType.LOF) {
            getLOFFundList(1, 100, true)
        } else {
            getQDIIFundList(1, 100, true)
        }

        if (result.isError) {
            return result
        }

        val funds = result.getOrNull() ?: return Result.error("获取列表失败")
        val filtered = funds
            .filter { (it.t1PremiumRate ?: Double.MIN_VALUE) >= minPremium }
            .sortedByDescending { it.t1PremiumRate }
            .take(topN)

        return Result.success(filtered)
    }

    suspend fun refreshFundList(fundType: FundType): Result<List<Fund>> {
        return if (fundType == FundType.LOF) {
            getLOFFundList(1, 50, true)
        } else {
            getQDIIFundList(1, 50, true)
        }
    }
}
