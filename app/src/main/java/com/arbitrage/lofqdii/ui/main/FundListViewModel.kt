package com.arbitrage.lofqdii.ui.main

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arbitrage.lofqdii.data.api.ApiSource
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.data.repository.FundRepository
import com.arbitrage.lofqdii.util.DebugLogger
import kotlinx.coroutines.launch

class FundListViewModel : ViewModel() {

    private val repository = FundRepository.getInstance()

    private val _funds = MutableLiveData<Result<List<Fund>>>()
    val funds: LiveData<Result<List<Fund>>> = _funds

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private var currentFundType: FundType = FundType.LOF
    private var currentSortMode: SortMode = SortMode.PREMIUM_DESC

    enum class SortMode {
        PREMIUM_DESC,
        VOLUME_DESC,
        SCALE_DESC
    }

    fun setFundType(type: FundType) {
        currentFundType = type
        loadFunds()
    }

    fun setSortMode(mode: SortMode) {
        currentSortMode = mode
        sortAndEmitFunds()
    }

    fun loadFunds() {
        viewModelScope.launch {
            _funds.value = Result.loading()
            DebugLogger.i("开始加载${if (currentFundType == FundType.LOF) "LOF" else "QDII"}基金列表")
            
            val result = if (currentFundType == FundType.LOF) {
                repository.getLOFFundList(1, 50, true)
            } else {
                repository.getQDIIFundList(1, 50, true)
            }
            
            if (result.isSuccess) {
                val fundCount = result.getOrNull()?.size ?: 0
                DebugLogger.i("成功加载 $fundCount 只基金")
            } else {
                DebugLogger.e("加载失败: ${result.getErrorMessage()}")
            }
            
            _funds.value = result
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            DebugLogger.i("刷新${if (currentFundType == FundType.LOF) "LOF" else "QDII"}基金列表")
            
            val result = repository.refreshFundList(currentFundType)
            
            if (result.isSuccess) {
                val fundCount = result.getOrNull()?.size ?: 0
                DebugLogger.i("刷新成功，共 $fundCount 只基金")
            } else {
                DebugLogger.e("刷新失败: ${result.getErrorMessage()}")
            }
            
            _funds.value = result
            _isRefreshing.value = false
        }
    }

    fun setApiSource(source: ApiSource) {
        repository.setApiSource(source)
        loadFunds()
    }

    fun getApiSource(): ApiSource = repository.getApiSource()

    private fun sortAndEmitFunds() {
        val currentResult = _funds.value
        if (currentResult is Result.Success) {
            val sortedFunds = when (currentSortMode) {
                SortMode.PREMIUM_DESC -> currentResult.data.sortedByDescending { it.t1PremiumRate }
                SortMode.VOLUME_DESC -> currentResult.data.sortedByDescending { it.volume }
                SortMode.SCALE_DESC -> currentResult.data.sortedByDescending { it.scale }
            }
            _funds.value = Result.success(sortedFunds)
        }
    }
}
