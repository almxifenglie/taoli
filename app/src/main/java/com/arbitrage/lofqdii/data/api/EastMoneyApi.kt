package com.arbitrage.lofqdii.data.api

import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.util.PremiumCalculator
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class EastMoneyFundItem(
    @SerializedName("f12") val code: String,
    @SerializedName("f14") val name: String,
    @SerializedName("f2") val price: Double?,
    @SerializedName("f3") val changePercent: Double?,
    @SerializedName("f4") val changeAmount: Double?,
    @SerializedName("f5") val volume: Long?,
    @SerializedName("f6") val amount: Double?,
    @SerializedName("f15") val high: Double?,
    @SerializedName("f16") val low: Double?,
    @SerializedName("f17") val open: Double?,
    @SerializedName("f18") val prevClose: Double?
)

data class EastMoneyResponse(
    @SerializedName("data") val data: EastMoneyData?,
    @SerializedName("rc") val rc: Int?
)

data class EastMoneyData(
    @SerializedName("total") val total: Int?,
    @SerializedName("diff") val diff: List<EastMoneyFundItem>?
)

class EastMoneyApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        const val BASE_URL = "https://push2.eastmoney.com"
        const val LOF_LIST_URL = "$BASE_URL/api/qt/clist/get"
        const val QDII_LIST_URL = "$BASE_URL/api/qt/clist/get"
        
        private const val LOF_FIELDS = "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18"
        private const val QDII_FIELDS = "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18"

        private const val LOF_FS = "b:MK0404"
        private const val QDII_FS = "b:MK0405"

        @Volatile
        private var instance: EastMoneyApi? = null

        fun getInstance(): EastMoneyApi {
            return instance ?: synchronized(this) {
                instance ?: EastMoneyApi().also { instance = it }
            }
        }
    }

    suspend fun getLOFList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return fetchFundList(LOF_FS, LOF_FIELDS, FundType.LOF, page, pageSize)
    }

    suspend fun getQDIIList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return fetchFundList(QDII_FS, QDII_FIELDS, FundType.QDII, page, pageSize)
    }

    private suspend fun fetchFundList(
        fs: String,
        fields: String,
        fundType: FundType,
        page: Int,
        pageSize: Int
    ): Result<List<Fund>> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(LOF_LIST_URL)
                append("?fs=$fs")
                append("&fields=$fields")
                append("&pn=$page")
                append("&pz=$pageSize")
                append("&ut=fa5fd1943c7b386f172d6893dbfba10b")
            }

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://quote.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return@withContext Result.error("Empty response")
            }

            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            val data = jsonResponse.getAsJsonObject("data")
            val diff = data?.getAsJsonArray("diff")

            if (diff == null || diff.size() == 0) {
                return@withContext Result.error("No data available")
            }

            val funds = diff.map { item ->
                val obj = item.asJsonObject
                Fund(
                    code = obj.get("f12")?.asString ?: "",
                    name = obj.get("f14")?.asString ?: "",
                    type = fundType,
                    marketPrice = obj.get("f2")?.takeIf { !it.isJsonNull }?.asDouble,
                    changePercent = obj.get("f3")?.takeIf { !it.isJsonNull }?.asDouble,
                    volume = obj.get("f5")?.takeIf { !it.isJsonNull }?.asLong,
                    amount = obj.get("f6")?.takeIf { !it.isJsonNull }?.asDouble,
                    updateTime = System.currentTimeMillis().toString()
                )
            }

            Result.success(funds)
        } catch (e: Exception) {
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    suspend fun getFundDetail(code: String): Result<Fund> = withContext(Dispatchers.IO) {
        try {
            val secid = if (code.startsWith("5") || code.startsWith("6")) {
                "1.$code"
            } else {
                "0.$code"
            }

            val url = "https://push2.eastmoney.com/api/qt/stock/get?secid=$secid&fields=f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://quote.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return@withContext Result.error("Empty response")
            }

            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            val data = jsonResponse.getAsJsonObject("data")

            if (data == null || data.isJsonNull) {
                return@withContext Result.error("No data available")
            }

            val fund = Fund(
                code = data.get("f12")?.asString ?: code,
                name = data.get("f14")?.asString ?: "",
                type = if (code.startsWith("16") || code.startsWith("50")) FundType.LOF else FundType.QDII,
                marketPrice = data.get("f2")?.takeIf { !it.isJsonNull }?.asDouble,
                changePercent = data.get("f3")?.takeIf { !it.isJsonNull }?.asDouble,
                volume = data.get("f5")?.takeIf { !it.isJsonNull }?.asLong,
                amount = data.get("f6")?.takeIf { !it.isJsonNull }?.asDouble,
                updateTime = System.currentTimeMillis().toString()
            )

            Result.success(fund)
        } catch (e: Exception) {
            Result.error("获取数据失败: ${e.message}", e)
        }
    }
}
