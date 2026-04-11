package com.arbitrage.lofqdii.data.api

import android.util.Log
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class TianTianFundInfo(
    val fundcode: String = "",
    val name: String = "",
    val jzrq: String = "",
    val dwjz: String = "",
    val gsz: String = "",
    val gszzl: String = "",
    val gztime: String = ""
)

class TianTianFundApi private constructor() {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "TianTianFundApi"
        private const val BASE_URL = "https://fundgz.1234567.com.cn"

        @Volatile
        private var instance: TianTianFundApi? = null

        fun getInstance(): TianTianFundApi {
            return instance ?: synchronized(this) {
                instance ?: TianTianFundApi().also { instance = it }
            }
        }
    }

    suspend fun getFundEstimate(code: String): Result<TianTianFundInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/js/$code.js"
            Log.d(TAG, "Requesting: $url")

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fund.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "Empty response")
                return@withContext Result.error("Empty response")
            }

            Log.d(TAG, "Response: $body")

            val jsonStr = body
                .replace("jsonpgz(", "")
                .replace(");", "")
                .trim()

            if (jsonStr.isEmpty() || jsonStr == "null") {
                Log.e(TAG, "Invalid JSONP response: $body")
                return@withContext Result.error("无效的JSONP响应")
            }

            val fundInfo = gson.fromJson(jsonStr, TianTianFundInfo::class.java)

            if (fundInfo.fundcode.isEmpty()) {
                Log.e(TAG, "Invalid fund code in response")
                return@withContext Result.error("无效的基金代码")
            }

            Log.d(TAG, "Parsed fund: ${fundInfo.fundcode}, nav: ${fundInfo.dwjz}, estimate: ${fundInfo.gsz}")
            Result.success(fundInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    suspend fun getFundNav(code: String): Result<Pair<Double, String?>> = withContext(Dispatchers.IO) {
        try {
            val result = getFundEstimate(code)
            if (result.isError) {
                return@withContext Result.error(result.getErrorMessage() ?: "Unknown error")
            }

            val info = result.getOrNull()!!
            val nav = info.dwjz.toDoubleOrNull()
            if (nav == null) {
                Log.e(TAG, "Invalid nav value: ${info.dwjz}")
                return@withContext Result.error("无效的净值: ${info.dwjz}")
            }

            Log.d(TAG, "Got nav: $nav, date: ${info.jzrq}")
            Result.success(Pair(nav, info.jzrq))
        } catch (e: Exception) {
            Log.e(TAG, "getFundNav error: ${e.message}", e)
            Result.error("获取净值失败: ${e.message}", e)
        }
    }

    suspend fun getFundEstimateNav(code: String): Result<Pair<Double, String?>> = withContext(Dispatchers.IO) {
        try {
            val result = getFundEstimate(code)
            if (result.isError) {
                return@withContext Result.error(result.getErrorMessage() ?: "Unknown error")
            }

            val info = result.getOrNull()!!
            val estimateNav = info.gsz.toDoubleOrNull()
            if (estimateNav == null) {
                Log.d(TAG, "No estimate nav available, using nav: ${info.dwjz}")
                val nav = info.dwjz.toDoubleOrNull()
                if (nav != null) {
                    return@withContext Result.success(Pair(nav, info.gztime))
                }
                return@withContext Result.error("无效的估算值")
            }

            Log.d(TAG, "Got estimate nav: $estimateNav, time: ${info.gztime}")
            Result.success(Pair(estimateNav, info.gztime))
        } catch (e: Exception) {
            Log.e(TAG, "getFundEstimateNav error: ${e.message}", e)
            Result.error("获取估算净值失败: ${e.message}", e)
        }
    }

    suspend fun getSubscribeStatus(code: String): Result<Pair<SubscribeStatus, Double?>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://fund.eastmoney.com/Fund_sgzt_$code.html"
            Log.d(TAG, "Requesting subscribe status: $url")

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fund.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Subscribe status HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return@withContext Result.error("Empty response")
            }

            if (body.contains("location.href") || body.contains("window.location")) {
                Log.w(TAG, "Page contains redirect script, not a valid fund page")
                return@withContext Result.error("页面已重定向，非有效基金页面")
            }

            val status = parseSubscribeStatus(body)
            val limit = parseSubscribeLimit(body)

            Log.d(TAG, "Subscribe status: $status, limit: $limit")
            Result.success(Pair(status, limit))
        } catch (e: Exception) {
            Log.e(TAG, "getSubscribeStatus error: ${e.message}", e)
            Result.error("获取申购状态失败: ${e.message}", e)
        }
    }

    private fun parseSubscribeStatus(html: String): SubscribeStatus {
        return when {
            html.contains("暂停申购") || html.contains("封闭期") -> SubscribeStatus.CLOSED
            html.contains("限制申购") || html.contains("限额") -> SubscribeStatus.LIMITED
            html.contains("正常申购") || html.contains("开放申购") -> SubscribeStatus.OPEN
            else -> SubscribeStatus.UNKNOWN
        }
    }

    private fun parseSubscribeLimit(html: String): Double? {
        val patterns = listOf(
            Regex("""([\d.]+)\s*万元"""),
            Regex("""限额\s*([\d.]+)\s*万"""),
            Regex("""日累计申购限额[^\d]*([\d.]+)"""),
            Regex("""申购限额[^\d]*([\d.]+)""")
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val valueStr = match.value
                val value = match.groupValues[1].toDoubleOrNull()
                if (value != null) {
                    return when {
                        valueStr.contains("亿") -> value * 100000000
                        valueStr.contains("万") -> value * 10000
                        valueStr.contains("千") -> value * 1000
                        else -> value
                    }
                }
            }
        }
        return null
    }
}
