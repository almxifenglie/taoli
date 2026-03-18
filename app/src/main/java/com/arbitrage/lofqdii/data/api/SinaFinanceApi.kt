package com.arbitrage.lofqdii.data.api

import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SinaFinanceApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        const val BASE_URL = "https://hq.sinajs.cn"

        @Volatile
        private var instance: SinaFinanceApi? = null

        fun getInstance(): SinaFinanceApi {
            return instance ?: synchronized(this) {
                instance ?: SinaFinanceApi().also { instance = it }
            }
        }
    }

    suspend fun getFundPrice(code: String): Result<Fund> = withContext(Dispatchers.IO) {
        try {
            val marketCode = if (code.startsWith("5") || code.startsWith("6")) "sh" else "sz"
            val fullCode = "${marketCode}$code"
            val url = "$BASE_URL/list=$fullCode"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://finance.sina.com.cn/")
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

            val fund = parseSinaFundData(code, body)
            if (fund == null) {
                return@withContext Result.error("Parse failed")
            }

            Result.success(fund)
        } catch (e: Exception) {
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    suspend fun getFundPrices(codes: List<String>): Result<List<Fund>> = withContext(Dispatchers.IO) {
        try {
            val fullCodes = codes.map { code ->
                val marketCode = if (code.startsWith("5") || code.startsWith("6")) "sh" else "sz"
                "${marketCode}$code"
            }

            val url = "$BASE_URL/list=${fullCodes.joinToString(",")}"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://finance.sina.com.cn/")
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

            val funds = mutableListOf<Fund>()
            val lines = body.split("\n")

            for ((index, line) in lines.withIndex()) {
                if (index >= codes.size) break
                val fund = parseSinaFundData(codes[index], line)
                if (fund != null) {
                    funds.add(fund)
                }
            }

            Result.success(funds)
        } catch (e: Exception) {
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    private fun parseSinaFundData(code: String, data: String): Fund? {
        try {
            val content = data.substringAfter("\"").substringBefore("\"")
            if (content.isEmpty()) return null

            val parts = content.split(",")
            if (parts.size < 6) return null

            val name = parts[0]
            val open = parts[1].toDoubleOrNull()
            val prevClose = parts[2].toDoubleOrNull()
            val price = parts[3].toDoubleOrNull()
            val high = parts[4].toDoubleOrNull()
            val low = parts[5].toDoubleOrNull()
            val volume = parts.getOrNull(8)?.toLongOrNull()
            val amount = parts.getOrNull(9)?.toDoubleOrNull()

            return Fund(
                code = code,
                name = name,
                type = if (code.startsWith("16") || code.startsWith("50")) FundType.LOF else FundType.QDII,
                marketPrice = price,
                changePercent = if (price != null && prevClose != null && prevClose != 0.0) {
                    (price - prevClose) / prevClose * 100
                } else null,
                volume = volume,
                amount = amount,
                updateTime = System.currentTimeMillis().toString()
            )
        } catch (e: Exception) {
            return null
        }
    }
}
