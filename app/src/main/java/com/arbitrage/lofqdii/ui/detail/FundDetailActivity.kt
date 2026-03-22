package com.arbitrage.lofqdii.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arbitrage.lofqdii.R
import com.arbitrage.lofqdii.data.api.DataSourceInfo
import com.arbitrage.lofqdii.data.api.FundDataResult
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.data.repository.FundRepository
import com.arbitrage.lofqdii.databinding.ActivityFundDetailBinding
import com.arbitrage.lofqdii.util.DebugLogger
import kotlinx.coroutines.launch

class FundDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFundDetailBinding
    private val repository = FundRepository.getInstance()

    private var fundCode: String = ""
    private var fundType: FundType = FundType.LOF

    companion object {
        private const val EXTRA_FUND_CODE = "fund_code"
        private const val EXTRA_FUND_TYPE = "fund_type"

        fun newIntent(context: Context, code: String, type: FundType): Intent {
            return Intent(context, FundDetailActivity::class.java).apply {
                putExtra(EXTRA_FUND_CODE, code)
                putExtra(EXTRA_FUND_TYPE, type.name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFundDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fundCode = intent.getStringExtra(EXTRA_FUND_CODE) ?: ""
        fundType = intent.getStringExtra(EXTRA_FUND_TYPE)?.let {
            try { FundType.valueOf(it) } catch (e: Exception) { FundType.LOF }
        } ?: FundType.LOF

        setupToolbar()
        loadFundDetail()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        binding.toolbar.title = "基金详情 - $fundCode"
    }

    private fun loadFundDetail() {
        lifecycleScope.launch {
            showLoading()
            
            val result = repository.getFundDetailWithSource(fundCode, fundType)
            hideLoading()

            when (result) {
                is Result.Success -> {
                    showFundDetail(result.data)
                }
                is Result.Error -> {
                    showError(result.message)
                }
                else -> {}
            }
        }
    }

    private fun showFundDetail(dataResult: FundDataResult) {
        val fund = dataResult.fund
        
        binding.apply {
            toolbar.title = "${fund.name.ifEmpty { fundCode }} - $fundCode"
            tvFundName.text = fund.name.ifEmpty { "加载中..." }
            tvFundCode.text = fund.code

            fund.t1PremiumRate?.let { rate ->
                tvT1Premium.text = fund.displayT1PremiumRate
                val colorRes = if (rate >= 0) R.color.premium_positive else R.color.premium_negative
                tvT1Premium.setTextColor(ContextCompat.getColor(this@FundDetailActivity, colorRes))
            } ?: run {
                tvT1Premium.text = "--"
                tvT1Premium.setTextColor(ContextCompat.getColor(this@FundDetailActivity, R.color.text_secondary))
            }

            fund.realtimePremiumRate?.let { rate ->
                tvRealtimePremium.text = fund.displayRealtimePremiumRate
                val colorRes = if (rate >= 0) R.color.premium_positive else R.color.premium_negative
                tvRealtimePremium.setTextColor(ContextCompat.getColor(this@FundDetailActivity, colorRes))
            } ?: run {
                tvRealtimePremium.text = "--"
                tvRealtimePremium.setTextColor(ContextCompat.getColor(this@FundDetailActivity, R.color.text_secondary))
            }

            tvMarketPrice.text = fund.displayMarketPrice
            tvNav.text = fund.displayNav
            tvEstimateNav.text = fund.estimateNav?.let { String.format("%.4f", it) } ?: "--"
            tvVolume.text = fund.displayVolume
            tvAmount.text = fund.amount?.let { String.format("%.2f万", it / 10000) } ?: "--"
            tvScale.text = fund.displayScale

            fund.changePercent?.let { percent ->
                val sign = if (percent >= 0) "+" else ""
                tvChangePercent.text = "$sign${String.format("%.2f", percent)}%"
                val colorRes = if (percent >= 0) R.color.rise else R.color.fall
                tvChangePercent.setTextColor(ContextCompat.getColor(this@FundDetailActivity, colorRes))
            } ?: run {
                tvChangePercent.text = "--"
            }

            val statusText = when (fund.subscribeStatus) {
                SubscribeStatus.OPEN -> getString(R.string.subscribe_open)
                SubscribeStatus.CLOSED -> getString(R.string.subscribe_closed)
                SubscribeStatus.LIMITED -> getString(R.string.subscribe_limited)
                SubscribeStatus.UNKNOWN -> "--"
            }
            tvSubscribeStatus.text = statusText

            val statusColor = when (fund.subscribeStatus) {
                SubscribeStatus.OPEN -> R.color.success
                SubscribeStatus.CLOSED -> R.color.error
                SubscribeStatus.LIMITED -> R.color.warning
                SubscribeStatus.UNKNOWN -> R.color.text_secondary
            }
            tvSubscribeStatus.setTextColor(ContextCompat.getColor(this@FundDetailActivity, statusColor))

            tvSubscribeLimit.text = fund.displaySubscribeLimit
            tvNavDate.text = fund.navDate ?: "--"
            tvUpdateTime.text = fund.updateTime ?: "--"

            showDataSourceInfo(dataResult)

            btnRetry.setOnClickListener {
                loadFundDetail()
            }
            
            btnShowLogs.setOnClickListener {
                showDebugLogs()
            }
        }
    }

    private fun showDataSourceInfo(dataResult: FundDataResult) {
        binding.apply {
            val sourceInfo = StringBuilder()
            
            dataResult.priceSource?.let { info ->
                val status = if (info.success) "成功" else "失败"
                sourceInfo.append("价格来源: ${info.name} ($status)\n")
                if (!info.success && info.errorMessage != null) {
                    sourceInfo.append("  错误: ${info.errorMessage}\n")
                }
            }
            
            dataResult.navSource?.let { info ->
                val status = if (info.success) "成功" else "失败"
                sourceInfo.append("净值来源: ${info.name} ($status)\n")
                if (!info.success && info.errorMessage != null) {
                    sourceInfo.append("  错误: ${info.errorMessage}\n")
                }
            }
            
            dataResult.estimateNavSource?.let { info ->
                val status = if (info.success) "成功" else "失败"
                sourceInfo.append("估值来源: ${info.name} ($status)\n")
                if (!info.success && info.errorMessage != null) {
                    sourceInfo.append("  错误: ${info.errorMessage}\n")
                }
            }
            
            dataResult.subscribeSource?.let { info ->
                val status = if (info.success) "成功" else "失败"
                sourceInfo.append("申购状态: ${info.name} ($status)\n")
                if (!info.success && info.errorMessage != null) {
                    sourceInfo.append("  错误: ${info.errorMessage}\n")
                }
            }
            
            if (sourceInfo.isNotEmpty()) {
                tvDataSourceInfo.text = sourceInfo.toString().trim()
                tvDataSourceInfo.visibility = View.VISIBLE
            } else {
                tvDataSourceInfo.visibility = View.GONE
            }
        }
    }

    private fun showDebugLogs() {
        val logs = DebugLogger.getRecentLogs(30)
        val logText = logs.joinToString("\n")
        
        binding.apply {
            if (tvDebugLogs.visibility == View.VISIBLE) {
                tvDebugLogs.visibility = View.GONE
                btnShowLogs.text = "显示日志"
            } else {
                tvDebugLogs.text = logText
                tvDebugLogs.visibility = View.VISIBLE
                btnShowLogs.text = "隐藏日志"
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.errorLayout.visibility = View.VISIBLE
        binding.tvError.text = message
    }
}
