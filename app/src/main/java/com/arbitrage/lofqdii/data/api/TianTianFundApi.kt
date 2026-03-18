package com.arbitrage.lofqdii.data.api

import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

data class TianTianFundDetail(
    val code: String = "",
    val name: String = "",
    val type: String = "",
    val nav: Double? = null,
    val navDate: String? = null,
    val estimateNav: Double? = null,
    val estimateChangePercent: Double? = null,
    val estimateTime: String? = null,
    val subscribeStatus: String? = null,
    val subscribeLimit: String? = null,
    val scale: Double? = null
)

class TianTianFundApi private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        const val BASE_URL = "https://fundgz.1234567.com.cn"
        const val ESTIMATE_URL = "$BASE_URL/js"
        const val DETAIL_URL = "https://fundf10.eastmoney.com/jbgk"

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
            val url = "$ESTIMATE_URL/${code}.js"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fund.eastmoney.com/")
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

            val jsonStr = body
                .replace("jsonpgz(", "")
                .replace(");", "")

            val fundInfo = gson.fromJson(jsonStr, TianTianFundInfo::class.java)

            if (fundInfo.fundcode.isEmpty()) {
                return@withContext Result.error("Invalid fund code")
            }

            Result.success(fundInfo)
        } catch (e: Exception) {
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
                return@withContext Result.error("Invalid nav value")
            }

            Result.success(Pair(nav, info.jzrq))
        } catch (e: Exception) {
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
                return@withContext Result.error("Invalid estimate value")
            }

            Result.success(Pair(estimateNav, info.gztime))
        } catch (e: Exception) {
            Result.error("获取估算净值失败: ${e.message}", e)
        }
    }

    suspend fun getFundInfo(code: String): Result<TianTianFundDetail> = withContext(Dispatchers.IO) {
        try {
            val url = "https://fundf10.eastmoney.com/jbgk_$code.html"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fundf10.eastmoney.com/")
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

            val detail = parseFundDetail(body, code)
            Result.success(detail)
        } catch (e: Exception) {
            Result.error("获取基金详情失败: ${e.message}", e)
        }
    }

    suspend fun getSubscribeStatus(code: String): Result<Pair<SubscribeStatus, Double?>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://fund.eastmoney.com/Fund_sgzt_$code.html"

            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fund.eastmoney.com/")
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

            val status = parseSubscribeStatus(body)
            val limit = parseSubscribeLimit(body)

            Result.success(Pair(status, limit))
        } catch (e: Exception) {
            Result.error("获取申购状态失败: ${e.message}", e)
        }
    }

    private fun parseFundDetail(html: String, code: String): TianTianFundDetail {
        return TianTianFundDetail(
            code = code,
            name = extractValue(html, "基金简称") ?: "",
            type = extractValue(html, "基金类型") ?: "",
            nav = extractValue(html, "单位净值")?.toDoubleOrNull(),
            navDate = extractValue(html, "净值日期"),
            scale = extractValue(html, "基金规模")?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        )
    }

    private fun extractValue(html: String, key: String): String? {
        val pattern = Regex("$key[\\s\\S]*?<td[^>]*>(.*?)</td>", RegexOption.IGNORE_CASE)
        val match = pattern.find(html)
        return match?.groupValues?.get(1)?.trim()?.replace(Regex("<.*?>"), "")
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
            Regex("(\\d+(?:\\.\\d+)?)[\\s]*万元"),
            Regex("(\\d+(?:\\.\\d+)?)[\\s]*万"),
            Regex("限额[\\s]*(\\d+(?:\\.\\d+)?)")
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val value = match.groupValues[1].toDoubleOrNull()
                if (value != null) {
                    return if (html.contains("万")) value * 10000 else value
                }
            }
        }
        return null
    }
}
