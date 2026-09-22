package com.example.m_agrilink

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 1. NATIVE MARKET COMPARISON RECYCLERVIEW ADAPTER
 */
class MarketArbitrageAdapter(private var dataset: List<MarketComparisonNode>) :
    RecyclerView.Adapter<MarketArbitrageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDestinationHubName: TextView = view.findViewById(R.id.txtDestinationHubName)
        val txtViabilityStatus: TextView = view.findViewById(R.id.txtViabilityStatus)
        val txtTargetPrice: TextView = view.findViewById(R.id.txtTargetPrice)
        val txtTransitCost: TextView = view.findViewById(R.id.txtTransitCost)
        val txtGrossProfit: TextView = view.findViewById(R.id.txtGrossProfit)
        val txtNetProfit: TextView = view.findViewById(R.id.txtNetProfit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_market_comparison, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = dataset[position]
        holder.txtDestinationHubName.text = node.hubName
        holder.txtViabilityStatus.text = node.viabilityStatus
        holder.txtTargetPrice.text = "Ceiling Price: KES ${node.targetPrice}"
        holder.txtTransitCost.text = "Transit Overhead: KES ${node.transitCost}"
        holder.txtGrossProfit.text = "Gross Margin: KES ${node.grossProfit}"
        holder.txtNetProfit.text = "Net Profit: KES ${node.netProfit}"
    }

    override fun getItemCount() = dataset.size

    fun updateData(newDataset: List<MarketComparisonNode>) {
        this.dataset = newDataset
        notifyDataSetChanged()
    }
}
