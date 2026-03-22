package com.arbitrage.lofqdii.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.arbitrage.lofqdii.R
import com.arbitrage.lofqdii.data.model.Fund
import com.arbitrage.lofqdii.data.model.SubscribeStatus
import com.arbitrage.lofqdii.databinding.ItemFundBinding

class FundAdapter(
    private val onItemClick: (Fund) -> Unit
) : ListAdapter<Fund, FundAdapter.FundViewHolder>(FundDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FundViewHolder {
        val binding = ItemFundBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FundViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FundViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FundViewHolder(
        private val binding: ItemFundBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(fund: Fund) {
            binding.apply {
                tvFundCode.text = fund.code
                tvFundName.text = fund.name.ifEmpty { "加载中..." }
                tvMarketPrice.text = fund.displayMarketPrice
                tvNav.text = fund.displayNav
                tvVolume.text = fund.displayVolume
                tvSubscribeLimit.text = fund.displaySubscribeLimit

                val premiumRate = fund.t1PremiumRate
                if (premiumRate != null) {
                    tvPremiumRate.text = fund.displayT1PremiumRate
                    val colorRes = if (premiumRate >= 0) R.color.premium_positive else R.color.premium_negative
                    tvPremiumRate.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
                } else {
                    tvPremiumRate.text = "--"
                    tvPremiumRate.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }

                val realtimePremium = fund.realtimePremiumRate
                if (realtimePremium != null) {
                    tvRealtimePremium.text = fund.displayRealtimePremiumRate
                    val colorRes = if (realtimePremium >= 0) R.color.premium_positive else R.color.premium_negative
                    tvRealtimePremium.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
                } else {
                    tvRealtimePremium.text = "--"
                    tvRealtimePremium.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                }

                val statusText = when (fund.subscribeStatus) {
                    SubscribeStatus.OPEN -> itemView.context.getString(R.string.subscribe_open)
                    SubscribeStatus.CLOSED -> itemView.context.getString(R.string.subscribe_closed)
                    SubscribeStatus.LIMITED -> itemView.context.getString(R.string.subscribe_limited)
                    SubscribeStatus.UNKNOWN -> "--"
                }
                tvSubscribeStatus.text = statusText

                val statusColor = when (fund.subscribeStatus) {
                    SubscribeStatus.OPEN -> R.color.success
                    SubscribeStatus.CLOSED -> R.color.error
                    SubscribeStatus.LIMITED -> R.color.warning
                    SubscribeStatus.UNKNOWN -> R.color.text_secondary
                }
                tvSubscribeStatus.setTextColor(ContextCompat.getColor(itemView.context, statusColor))

                if (fund.marketPrice == null) {
                    tvMarketPrice.setTextColor(ContextCompat.getColor(itemView.context, R.color.error))
                } else {
                    tvMarketPrice.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                }

                if (fund.nav == null) {
                    tvNav.setTextColor(ContextCompat.getColor(itemView.context, R.color.error))
                } else {
                    tvNav.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                }
            }
        }
    }

    class FundDiffCallback : DiffUtil.ItemCallback<Fund>() {
        override fun areItemsTheSame(oldItem: Fund, newItem: Fund): Boolean {
            return oldItem.code == newItem.code
        }

        override fun areContentsTheSame(oldItem: Fund, newItem: Fund): Boolean {
            return oldItem == newItem
        }
    }
}
