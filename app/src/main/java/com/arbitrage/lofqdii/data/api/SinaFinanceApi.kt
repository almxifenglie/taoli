package com.arbitrage.lofqdii.data.api

import android.util.Log
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class SinaFinanceApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "SinaFinanceApi"
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

            Log.d(TAG, "Fetching price for $code: $url")

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://finance.sina.com.cn/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val responseBody = response.body
            if (responseBody == null) {
                Log.e(TAG, "Response body is null")
                return@withContext Result.error("Empty response body")
            }

            val bytes = responseBody.bytes()
            val body = String(bytes, Charset.forName("GBK"))
            
            Log.d(TAG, "Response for $code: ${body.take(200)}")

            val fund = parseSinaFundData(code, body)
            if (fund == null) {
                Log.e(TAG, "Failed to parse response for $code")
                return@withContext Result.error("Parse failed")
            }

            Log.d(TAG, "Parsed fund: code=${fund.code}, price=${fund.marketPrice}, volume=${fund.volume}")
            Result.success(fund)
        } catch (e: Exception) {
            Log.e(TAG, "getFundPrice error for $code: ${e.message}", e)
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
            Log.d(TAG, "Fetching prices for ${codes.size} funds: $url")

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://finance.sina.com.cn/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val responseBody = response.body
            if (responseBody == null) {
                Log.e(TAG, "Response body is null")
                return@withContext Result.error("Empty response body")
            }

            val bytes = responseBody.bytes()
            val body = String(bytes, Charset.forName("GBK"))

            val funds = mutableListOf<Fund>()
            val lines = body.split("\n")

            for ((index, line) in lines.withIndex()) {
                if (index >= codes.size) break
                val fund = parseSinaFundData(codes[index], line)
                if (fund != null) {
                    funds.add(fund)
                }
            }

            Log.d(TAG, "Parsed ${funds.size} funds from ${codes.size} codes")
            Result.success(funds)
        } catch (e: Exception) {
            Log.e(TAG, "getFundPrices error: ${e.message}", e)
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    suspend fun getFundPriceOnly(code: String): Result<Double?> = withContext(Dispatchers.IO) {
        try {
            val marketCode = if (code.startsWith("5") || code.startsWith("6")) "sh" else "sz"
            val fullCode = "${marketCode}$code"
            val url = "$BASE_URL/list=$fullCode"

            Log.d(TAG, "Fetching price only for $code: $url")

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://finance.sina.com.cn/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val responseBody = response.body
            if (responseBody == null) {
                return@withContext Result.error("Empty response body")
            }

            val bytes = responseBody.bytes()
            val body = String(bytes, Charset.forName("GBK"))

            val content = body.substringAfter("\"").substringBefore("\"")
            if (content.isEmpty()) {
                return@withContext Result.error("No content")
            }

            val parts = content.split(",")
            if (parts.size < 4) {
                return@withContext Result.error("Invalid format")
            }

            val price = parts[3].toDoubleOrNull()
            if (price == null || price == 0.0) {
                val prevClose = parts[2].toDoubleOrNull()
                Log.d(TAG, "Price is null or zero, prevClose=$prevClose")
                return@withContext Result.success(prevClose)
            }

            Log.d(TAG, "Price for $code: $price")
            Result.success(price)
        } catch (e: Exception) {
            Log.e(TAG, "getFundPriceOnly error for $code: ${e.message}", e)
            Result.error("获取价格失败: ${e.message}", e)
        }
    }

    private fun parseSinaFundData(code: String, data: String): Fund? {
        try {
            val content = data.substringAfter("\"").substringBefore("\"")
            if (content.isEmpty()) {
                Log.w(TAG, "Empty content for $code")
                return null
            }

            val parts = content.split(",")
            if (parts.size < 6) {
                Log.w(TAG, "Invalid parts size for $code: ${parts.size}")
                return null
            }

            val name = parts[0]
            val open = parts[1].toDoubleOrNull()
            val prevClose = parts[2].toDoubleOrNull()
            val price = parts[3].toDoubleOrNull()
            val high = parts[4].toDoubleOrNull()
            val low = parts[5].toDoubleOrNull()
            val volume = parts.getOrNull(8)?.toLongOrNull()
            val amount = parts.getOrNull(9)?.toDoubleOrNull()

            if (name.isEmpty()) {
                Log.w(TAG, "Empty name for $code")
                return null
            }

            return Fund(
                code = code,
                name = name,
                type = if (code.startsWith("16") || code.startsWith("50")) FundType.LOF else FundType.QDII,
                marketPrice = price ?: prevClose,
                changePercent = if (price != null && prevClose != null && prevClose != 0.0) {
                    (price - prevClose) / prevClose * 100
                } else null,
                volume = volume,
                amount = amount,
                updateTime = System.currentTimeMillis().toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseSinaFundData error for $code: ${e.message}", e)
            return null
        }
    }
}
