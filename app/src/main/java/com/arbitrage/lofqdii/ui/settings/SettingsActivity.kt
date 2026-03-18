package com.arbitrage.lofqdii.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arbitrage.lofqdii.R
import com.arbitrage.lofqdii.data.api.ApiSource
import com.arbitrage.lofqdii.data.api.ApiSwitcher
import com.arbitrage.lofqdii.databinding.ActivitySettingsBinding
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
        }

        binding.rgApiSource.setOnCheckedChangeListener { _, checkedId ->
            val source = when (checkedId) {
                R.id.rbEastMoney -> ApiSource.EASTMONEY
                R.id.rbTianTian -> ApiSource.TIANTIAN
                R.id.rbSina -> ApiSource.SINA
                else -> ApiSource.EASTMONEY
            }
            apiSwitcher.setSource(source)
        }
    }

    private fun setupApiTest() {
        binding.btnTestApi.setOnClickListener {
            testCurrentApi()
        }
    }

    private fun testCurrentApi() {
        val source = when (binding.rgApiSource.checkedRadioButtonId) {
            R.id.rbEastMoney -> ApiSource.EASTMONEY
            R.id.rbTianTian -> ApiSource.TIANTIAN
            R.id.rbSina -> ApiSource.SINA
            else -> ApiSource.EASTMONEY
        }

        binding.tvApiStatus.visibility = android.view.View.VISIBLE
        binding.tvApiStatus.text = "测试中..."
        binding.tvApiStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        lifecycleScope.launch {
            val isSuccess = apiSwitcher.testApiConnection(source)

            binding.tvApiStatus.text = if (isSuccess) {
                "连接成功"
            } else {
                "连接失败"
            }
            binding.tvApiStatus.setTextColor(
                ContextCompat.getColor(this@SettingsActivity, if (isSuccess) R.color.success else R.color.error)
            )
        }
    }
}
