package com.example.m_agrilink

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText

/**
 * 1. NATIVE INTEGRATED IN-APP MARKET ARBITRAGE CEILING RECYCLERVIEW PAGE
 */
class MarketAnalyzerSubpageActivity : AppCompatActivity() {

    private val engine = MarketArbitrageEngine()
    private lateinit var adapter: MarketArbitrageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market_analyzer_subpage)

        val cropSpinner = findViewById<AutoCompleteTextView>(R.id.analyzerCropSpinner)
        val editTransitCostInput = findViewById<TextInputEditText>(R.id.editTransitCostInput)
        val editLocalPriceInput = findViewById<TextInputEditText>(R.id.editLocalPriceInput)
        val btnCalculate = findViewById<Button>(R.id.btnCalculateArbitrageMatrix)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewArbitrageRankings)

        // Setup drop down spinner items
        val cropsList = listOf("Maize", "Beans")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cropsList)
        cropSpinner.setAdapter(spinnerAdapter)
        cropSpinner.setText("Maize", false)

        // Initialize RecyclerView layout structures
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MarketArbitrageAdapter(emptyList())
        recyclerView.adapter = adapter

        // Run calculation sequence automatically
        executeCalculations(cropSpinner, editLocalPriceInput, editTransitCostInput)

        btnCalculate.setOnClickListener {
            executeCalculations(cropSpinner, editLocalPriceInput, editTransitCostInput)
        }
    }

    private fun executeCalculations(
        cropSpinner: AutoCompleteTextView,
        localInput: TextInputEditText,
        transitInput: TextInputEditText
    ) {
        val crop = cropSpinner.text.toString()
        val localPrice = localInput.text.toString().toDoubleOrNull() ?: 4200.0
        val transitCost = transitInput.text.toString().toDoubleOrNull() ?: 300.0

        val nodes = engine.computeNationalArbitrage(crop, localPrice, transitCost)
        adapter.updateData(nodes)
    }
}
