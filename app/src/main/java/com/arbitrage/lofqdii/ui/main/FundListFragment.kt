package com.arbitrage.lofqdii.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.arbitrage.lofqdii.R
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.FundType
import com.arbitrage.lofqdii.data.model.Result
import com.arbitrage.lofqdii.databinding.FragmentFundListBinding
import com.arbitrage.lofqdii.ui.detail.FundDetailActivity

class FundListFragment : Fragment() {

    private var _binding: FragmentFundListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FundListViewModel by viewModels()
    private lateinit var adapter: FundAdapter
    private var fundType: FundType = FundType.LOF

    fun refresh() {
        viewModel.refresh()
    }

    companion object {
        private const val ARG_FUND_TYPE = "fund_type"

        fun newInstance(fundType: FundType): FundListFragment {
            return FundListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FUND_TYPE, fundType.name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(ARG_FUND_TYPE)?.let {
            fundType = FundType.valueOf(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFundListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        setupErrorRetry()
        observeData()

        viewModel.setFundType(fundType)
    }

    private fun setupRecyclerView() {
        adapter = FundAdapter { fund ->
            onFundClick(fund)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupErrorRetry() {
        binding.retryButton.setOnClickListener {
            viewModel.loadFunds()
        }
    }

    private fun observeData() {
        viewModel.funds.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {
                    showLoading()
                }
                is Result.Success -> {
                    hideLoading()
                    if (result.data.isEmpty()) {
                        showError(getString(R.string.no_data))
                    } else {
                        showData(result.data)
                    }
                }
                is Result.Error -> {
                    hideLoading()
                    showError(result.message)
                }
            }
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            binding.swipeRefresh.isRefreshing = isRefreshing
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    private fun showData(funds: List<Fund>) {
        adapter.submitList(funds)
        binding.recyclerView.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorLayout.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun onFundClick(fund: Fund) {
        val intent = FundDetailActivity.newIntent(requireContext(), fund.code, fund.type)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
