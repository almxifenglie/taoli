package com.arbitrage.lofqdii.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val TAG = "ArbitrageApp"
    private const val ENABLE_DEBUG = true
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    private val logs = mutableListOf<String>()
    private const val MAX_LOG_SIZE = 500
    
    fun d(message: String) {
        if (ENABLE_DEBUG) {
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] $message"
            Log.d(TAG, message)
            addLog(logEntry)
        }
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] ERROR: $message${throwable?.let { " - ${it.message}" } ?: ""}"
        Log.e(TAG, message, throwable)
        addLog(logEntry)
    }
    
    fun w(message: String) {
        if (ENABLE_DEBUG) {
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] WARN: $message"
            Log.w(TAG, message)
            addLog(logEntry)
        }
    }
    
    fun i(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] INFO: $message"
        Log.i(TAG, message)
        addLog(logEntry)
    }
    
    private fun addLog(entry: String) {
        synchronized(logs) {
            if (logs.size >= MAX_LOG_SIZE) {
                logs.removeAt(0)
            }
            logs.add(entry)
        }
    }
    
    fun getLogs(): List<String> {
        synchronized(logs) {
            return logs.toList()
        }
    }
    
    fun getRecentLogs(count: Int = 50): List<String> {
        synchronized(logs) {
            return logs.takeLast(count)
        }
    }
    
    fun clearLogs() {
        synchronized(logs) {
            logs.clear()
        }
    }
    
    fun logApiRequest(api: String, url: String, code: String? = null) {
        val codeInfo = code?.let { "[$it] " } ?: ""
        d("${codeInfo}API请求: $api - $url")
    }
    
    fun logApiSuccess(api: String, code: String, data: String) {
        d("[$code] API成功: $api - $data")
    }
    
    fun logApiError(api: String, code: String, error: String) {
        e("[$code] API失败: $api - $error")
    }
    
    fun logDataResult(code: String, price: String?, nav: String?, subscribe: String?) {
        d("[$code] 数据结果: 价格=$price, 净值=$nav, 申购=$subscribe")
    }
}
