package com.arbitrage.lofqdii.ui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arbitrage.lofqdii.R
import com.arbitrage.lofqdii.data.api.ApiSource
import com.arbitrage.lofqdii.data.api.ApiSwitcher
import com.arbitrage.lofqdii.databinding.ActivitySettingsBinding
import com.arbitrage.lofqdii.util.DebugLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val apiSwitcher = ApiSwitcher.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupApiSourceSelection()
        setupApiTest()
        setupDebugSection()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupApiSourceSelection() {
        val currentSource = apiSwitcher.getCurrentSource()

        when (currentSource) {
            ApiSource.EASTMONEY -> binding.rbEastMoney.isChecked = true
            ApiSource.TIANTIAN -> binding.rbTianTian.isChecked = true
            ApiSource.SINA -> binding.rbSina.isChecked = true
            ApiSource.COMBINED -> binding.rbCombined.isChecked = true
        }

        binding.rgApiSource.setOnCheckedChangeListener { _, checkedId ->
            val source = when (checkedId) {
                R.id.rbEastMoney -> ApiSource.EASTMONEY
                R.id.rbTianTian -> ApiSource.TIANTIAN
                R.id.rbSina -> ApiSource.SINA
                R.id.rbCombined -> ApiSource.COMBINED
                else -> ApiSource.COMBINED
            }
            apiSwitcher.setSource(source)
        }
    }

    private fun setupApiTest() {
        binding.btnTestApi.setOnClickListener {
            testAllApis()
        }
    }

    private fun testAllApis() {
        binding.tvApiStatus.visibility = View.VISIBLE
        binding.tvApiStatus.text = "测试所有API..."
        binding.tvApiStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        lifecycleScope.launch {
            val results = listOf(
                async { "东方财富" to apiSwitcher.testApiConnection(ApiSource.EASTMONEY) },
                async { "天天基金" to apiSwitcher.testApiConnection(ApiSource.TIANTIAN) },
                async { "新浪财经" to apiSwitcher.testApiConnection(ApiSource.SINA) }
            ).awaitAll()

            val statusBuilder = StringBuilder()
            results.forEach { (name, success) ->
                val status = if (success) "成功" else "失败"
                val color = if (success) "绿色" else "红色"
                statusBuilder.append("$name: $status\n")
                DebugLogger.i("API测试: $name - $status")
            }

            binding.tvApiStatus.text = statusBuilder.toString().trim()
            
            val allSuccess = results.all { it.second }
            binding.tvApiStatus.setTextColor(
                ContextCompat.getColor(this@SettingsActivity, if (allSuccess) R.color.success else R.color.warning)
            )
        }
    }

    private fun setupDebugSection() {
        binding.btnShowLogs.setOnClickListener {
            val logs = DebugLogger.getRecentLogs(50)
            if (logs.isEmpty()) {
                binding.tvDebugLogs.text = "暂无日志"
            } else {
                binding.tvDebugLogs.text = logs.joinToString("\n")
            }
            
            if (binding.tvDebugLogs.visibility == View.VISIBLE) {
                binding.tvDebugLogs.visibility = View.GONE
                binding.btnShowLogs.text = "显示日志"
            } else {
                binding.tvDebugLogs.visibility = View.VISIBLE
                binding.btnShowLogs.text = "隐藏日志"
            }
        }

        binding.btnClearLogs.setOnClickListener {
            DebugLogger.clearLogs()
            binding.tvDebugLogs.text = "日志已清空"
            binding.tvDebugLogs.visibility = View.VISIBLE
        }
    }
}
