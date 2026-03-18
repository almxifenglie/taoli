package com.arbitrage.lofqdii.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isMobileData(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}

object DateTimeUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String = dateTimeFormat.format(Date(timestamp))

    fun getCurrentDate(): String = dateFormat.format(Date())

    fun getCurrentTime(): String = timeFormat.format(Date())

    fun getCurrentDateTime(): String = dateTimeFormat.format(Date())

    fun isTradingTime(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

        if (dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY) {
            return false
        }

        val timeInMinutes = hour * 60 + minute
        val morningStart = 9 * 60 + 30
        val morningEnd = 11 * 60 + 30
        val afternoonStart = 13 * 60
        val afternoonEnd = 15 * 60

        return (timeInMinutes in morningStart..morningEnd) || 
               (timeInMinutes in afternoonStart..afternoonEnd)
    }
}

object FundCodeUtils {

    fun getMarketCode(code: String): String {
        return when {
            code.startsWith("5") || code.startsWith("6") -> "sh"
            code.startsWith("1") || code.startsWith("2") -> "sz"
            else -> "sz"
        }
    }

    fun isLOF(code: String): Boolean {
        return code.startsWith("16") || code.startsWith("50")
    }

    fun isQDII(code: String): Boolean {
        return code.startsWith("16") && code.length == 6
    }

    fun isValidFundCode(code: String): Boolean {
        return code.length == 6 && code.all { it.isDigit() }
    }
}
