package com.arbitrage.lofqdii.data.api

import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class EastMoneyApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        const val BASE_URL = "https://push2.eastmoney.com"

        @Volatile
        private var instance: EastMoneyApi? = null

        fun getInstance(): EastMoneyApi {
            return instance ?: synchronized(this) {
                instance ?: EastMoneyApi().also { instance = it }
            }
        }
    }

    suspend fun getLOFList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return fetchFundList("m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23", FundType.LOF, page, pageSize)
    }

    suspend fun getQDIIList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return fetchFundList("m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23", FundType.QDII, page, pageSize)
    }

    private suspend fun fetchFundList(
        fs: String,
        fundType: FundType,
        page: Int,
        pageSize: Int
    ): Result<List<Fund>> = withContext(Dispatchers.IO) {
        try {
            val fields = "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18"
            val url = buildString {
                append("$BASE_URL/api/qt/clist/get")
                append("?fs=$fs")
                append("&fields=$fields")
                append("&pn=$page")
                append("&pz=$pageSize")
                append("&ut=fa5fd1943c7b386f172d6893dbfba10b")
                append("&secid=0.000001")
            }

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://quote.eastmoney.com/center/gridlist.html")
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

            val funds = parseFundListResponse(body, fundType)
            if (funds.isEmpty()) {
                return@withContext Result.error("No data available")
            }

            Result.success(funds)
        } catch (e: Exception) {
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    private fun parseFundListResponse(body: String, fundType: FundType): List<Fund> {
        val funds = mutableListOf<Fund>()
        
        try {
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            
            if (jsonResponse.has("data") && !jsonResponse.get("data").isJsonNull) {
                val dataElement = jsonResponse.get("data")
                
                val diffArray: JsonArray? = when {
                    dataElement.isJsonObject -> {
                        val dataObj = dataElement.asJsonObject
                        if (dataObj.has("diff") && !dataObj.get("diff").isJsonNull) {
                            dataObj.getAsJsonArray("diff")
                        } else null
                    }
                    dataElement.isJsonArray -> dataElement.asJsonArray
                    else -> null
                }

                diffArray?.forEach { item ->
                    try {
                        val obj = item.asJsonObject
                        val code = obj.get("f12")?.asString ?: ""
                        val name = obj.get("f14")?.asString ?: ""
                        
                        if (code.isNotEmpty() && name.isNotEmpty()) {
                            funds.add(Fund(
                                code = code,
                                name = name,
                                type = fundType,
                                marketPrice = obj.get("f2")?.takeIf { !it.isJsonNull }?.asDouble,
                                changePercent = obj.get("f3")?.takeIf { !it.isJsonNull }?.asDouble,
                                volume = obj.get("f5")?.takeIf { !it.isJsonNull }?.asLong,
                                amount = obj.get("f6")?.takeIf { !it.isJsonNull }?.asDouble,
                                updateTime = System.currentTimeMillis().toString()
                            ))
                        }
                    } catch (e: Exception) {
                        // Skip invalid items
                    }
                }
            }
        } catch (e: Exception) {
            // Parse error
        }
        
        return funds
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
            
            if (!jsonResponse.has("data") || jsonResponse.get("data").isJsonNull) {
                return@withContext Result.error("No data available")
            }

            val data = jsonResponse.getAsJsonObject("data")

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
