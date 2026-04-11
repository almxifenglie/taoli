package com.arbitrage.lofqdii.data.api

import android.util.Log
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.model.Result
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class EastMoneyApi private constructor() {

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
        private const val TAG = "EastMoneyApi"
        const val BASE_URL = "https://push2.eastmoney.com"
        const val BASE_URL_HIS = "https://push2his.eastmoney.com"
        const val FUND_F10_URL = "https://fundf10.eastmoney.com"

        @Volatile
        private var instance: EastMoneyApi? = null

        fun getInstance(): EastMoneyApi {
            return instance ?: synchronized(this) {
                instance ?: EastMoneyApi().also { instance = it }
            }
        }
    }

    suspend fun getLOFList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return fetchFundList(
            "b:MK0404",
            FundType.LOF,
            page,
            pageSize
        )
    }

    suspend fun getQDIIList(page: Int = 1, pageSize: Int = 100): Result<List<Fund>> {
        return fetchFundList(
            "b:MK0405",
            FundType.QDII,
            page,
            pageSize
        )
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
                append("?cb=json")
                append("&pn=$page")
                append("&pz=$pageSize")
                append("&po=1")
                append("&np=1")
                append("&fltt=2")
                append("&invt=2")
                append("&fid=f3")
                append("&fs=$fs")
                append("&fields=$fields")
            }

            Log.d(TAG, "Fetching fund list: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://quote.eastmoney.com/center/gridlist.html")
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
                Log.e(TAG, "Empty response body")
                return@withContext Result.error("Empty response")
            }

            val funds = parseFundListResponse(body, fundType)
            if (funds.isEmpty()) {
                Log.e(TAG, "No funds parsed from response")
                return@withContext Result.error("No data available")
            }

            Log.d(TAG, "Successfully fetched ${funds.size} funds")
            Result.success(funds)
        } catch (e: Exception) {
            Log.e(TAG, "fetchFundList error: ${e.message}", e)
            Result.error("获取数据失败: ${e.message}", e)
        }
    }

    private fun parseFundListResponse(body: String, fundType: FundType): List<Fund> {
        val funds = mutableListOf<Fund>()
        
        try {
            var jsonStr = body
            if (jsonStr.contains("json(")) {
                jsonStr = jsonStr.substringAfter("json(").substringBeforeLast(")")
            }
            
            val jsonResponse = gson.fromJson(jsonStr, JsonObject::class.java)
            
            if (jsonResponse.has("data") && !jsonResponse.get("data").isJsonNull) {
                val dataObj = jsonResponse.getAsJsonObject("data")
                
                if (dataObj.has("diff") && !dataObj.get("diff").isJsonNull) {
                    val diffArray = dataObj.getAsJsonArray("diff")

                    diffArray.forEach { item ->
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
                            Log.w(TAG, "Error parsing fund item: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseFundListResponse error: ${e.message}", e)
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

    suspend fun getLOFPriceByKline(code: String): Result<Triple<Double?, Long?, Double?>> = withContext(Dispatchers.IO) {
        try {
            val market = if (code.startsWith("5") || code.startsWith("6")) "1" else "0"
            val secid = "$market.$code"
            
            val url = "$BASE_URL_HIS/api/qt/stock/kline/get?cb=jQuery&secid=$secid&fields1=f1,f2,f3,f4,f5&fields2=f51,f52,f53,f54,f55,f56,f57&klt=101&fqt=1&end=20500101&lmt=1"

            Log.d(TAG, "Fetching kline price for $code: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://quote.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Kline HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "Kline empty response")
                return@withContext Result.error("Empty response")
            }

            val jsonStr = body.substringAfter("jQuery(").substringBeforeLast(")")
            if (jsonStr.isEmpty()) {
                Log.e(TAG, "Kline invalid JSONP")
                return@withContext Result.error("Invalid JSONP response")
            }

            val jsonResponse = gson.fromJson(jsonStr, JsonObject::class.java)
            
            if (!jsonResponse.has("data") || jsonResponse.get("data").isJsonNull) {
                Log.e(TAG, "Kline no data")
                return@withContext Result.error("No data available")
            }

            val data = jsonResponse.getAsJsonObject("data")
            val klines = data.getAsJsonArray("klines")
            
            if (klines == null || klines.size() == 0) {
                Log.e(TAG, "Kline empty klines array")
                return@withContext Result.error("No kline data")
            }

            val klineStr = klines[0].asString
            val parts = klineStr.split(",")
            
            if (parts.size < 7) {
                Log.e(TAG, "Kline invalid format: $klineStr")
                return@withContext Result.error("Invalid kline format")
            }

            val open = parts[1].toDoubleOrNull()
            val close = parts[2].toDoubleOrNull()
            val high = parts[3].toDoubleOrNull()
            val low = parts[4].toDoubleOrNull()
            val volume = parts[5].toLongOrNull()
            val amount = parts[6].toDoubleOrNull()

            Log.d(TAG, "Kline parsed for $code: close=$close, volume=$volume, amount=$amount")
            Result.success(Triple(close, volume, amount))
        } catch (e: Exception) {
            Log.e(TAG, "getLOFPriceByKline error for $code: ${e.message}", e)
            Result.error("获取K线数据失败: ${e.message}", e)
        }
    }

    suspend fun getSubscribeStatusFromF10(code: String): Result<Pair<SubscribeStatus, Double?>> = withContext(Dispatchers.IO) {
        try {
            val url = "$FUND_F10_URL/fund_sgzt_$code.html"
            
            Log.d(TAG, "Fetching subscribe status from F10: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fund.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "F10 HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "F10 empty response")
                return@withContext Result.error("Empty response")
            }

            val status = parseSubscribeStatusFromHtml(body)
            val limit = parseSubscribeLimitFromHtml(body)
            
            Log.d(TAG, "F10 parsed for $code: status=$status, limit=$limit")
            Result.success(Pair(status, limit))
        } catch (e: Exception) {
            Log.e(TAG, "getSubscribeStatusFromF10 error for $code: ${e.message}", e)
            Result.error("获取申购状态失败: ${e.message}", e)
        }
    }

    private fun parseSubscribeStatusFromHtml(html: String): SubscribeStatus {
        val cleanHtml = html.replace("\\s".toRegex(), "").lowercase()
        return when {
            cleanHtml.contains("暂停申购") || cleanHtml.contains("封闭期") -> SubscribeStatus.CLOSED
            cleanHtml.contains("限大额") || cleanHtml.contains("限制申购") || cleanHtml.contains("限额申购") -> SubscribeStatus.LIMITED
            cleanHtml.contains("开放申购") || cleanHtml.contains("正常申购") -> SubscribeStatus.OPEN
            else -> SubscribeStatus.UNKNOWN
        }
    }

    private fun parseSubscribeLimitFromHtml(html: String): Double? {
        try {
            val patterns = listOf(
                Regex("""日累计申购限额[^<]*<td[^>]*>([^<]+)"""),
                Regex("""申购限额[^<]*<td[^>]*>([^<]+)"""),
                Regex("""日购买限额[^<]*<td[^>]*>([^<]+)"""),
                Regex("""购买限额[^<]*<td[^>]*>([^<]+)"""),
                Regex("""限額[^<]*<td[^>]*>([^<]+)""", RegexOption.IGNORE_CASE)
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val valueStr = match.groupValues[1].trim()
                    Log.d(TAG, "Found limit string with pattern ${pattern}: $valueStr")

                    if (valueStr.contains("无限额") || valueStr.contains("---") || valueStr.contains("无限制")) {
                        return null
                    }

                    val numPattern = Regex("""([\d.]+)""")
                    val numMatch = numPattern.find(valueStr)
                    if (numMatch != null) {
                        val value = numMatch.groupValues[1].toDoubleOrNull()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseSubscribeLimitFromHtml error: ${e.message}")
        }
        return null
    }

    suspend fun getFundNavFromDetail(code: String): Result<Pair<Double, String?>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://fundgz.1234567.com.cn/js/$code.js"
            
            Log.d(TAG, "Fetching nav from EastMoney: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://fund.eastmoney.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Nav HTTP error: ${response.code}")
                return@withContext Result.error("HTTP ${response.code}")
            }

            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                Log.e(TAG, "Nav empty response")
                return@withContext Result.error("Empty response")
            }

            val jsonStr = body
                .replace("jsonpgz(", "")
                .replace(");", "")
                .trim()

            if (jsonStr.isEmpty() || jsonStr == "null") {
                Log.e(TAG, "Nav invalid JSONP: $body")
                return@withContext Result.error("Invalid JSONP response")
            }

            val jsonObj = gson.fromJson(jsonStr, JsonObject::class.java)
            
            val navStr = jsonObj.get("dwjz")?.asString
            val navDate = jsonObj.get("jzrq")?.asString
            
            if (navStr.isNullOrEmpty()) {
                Log.e(TAG, "Nav value is empty")
                return@withContext Result.error("Nav value is empty")
            }

            val nav = navStr.toDoubleOrNull()
            if (nav == null) {
                Log.e(TAG, "Failed to parse nav: $navStr")
                return@withContext Result.error("Invalid nav value: $navStr")
            }

            Log.d(TAG, "Nav parsed for $code: nav=$nav, date=$navDate")
            Result.success(Pair(nav, navDate))
        } catch (e: Exception) {
            Log.e(TAG, "getFundNavFromDetail error for $code: ${e.message}", e)
            Result.error("获取净值失败: ${e.message}", e)
        }
    }

    suspend fun getFundEstimateNavFromApi(code: String): Result<Pair<Double, String?>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://fundgz.1234567.com.cn/js/$code.js"
            
            Log.d(TAG, "Fetching estimate nav: $url")
            
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
                .trim()

            if (jsonStr.isEmpty() || jsonStr == "null") {
                return@withContext Result.error("Invalid JSONP response")
            }

            val jsonObj = gson.fromJson(jsonStr, JsonObject::class.java)
            
            val gszStr = jsonObj.get("gsz")?.asString
            val gzTime = jsonObj.get("gztime")?.asString
            val navStr = jsonObj.get("dwjz")?.asString
            
            val estimateNav = gszStr?.toDoubleOrNull()
            val nav = navStr?.toDoubleOrNull()
            
            val resultNav = estimateNav ?: nav
            if (resultNav == null) {
                return@withContext Result.error("No nav value available")
            }

            Log.d(TAG, "Estimate nav parsed for $code: nav=$resultNav, time=$gzTime")
            Result.success(Pair(resultNav, gzTime))
        } catch (e: Exception) {
            Log.e(TAG, "getFundEstimateNavFromApi error for $code: ${e.message}", e)
            Result.error("获取估算净值失败: ${e.message}", e)
        }
    }
}
