package com.buttonbox.ble

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buttonbox.ble.data.DisplayType
import com.buttonbox.ble.data.EcuVariable
import com.buttonbox.ble.data.GaugeConfig
import com.buttonbox.ble.data.GaugePosition
import com.buttonbox.ble.data.SettingsManager
import com.buttonbox.ble.data.VariableRepository
import com.buttonbox.ble.databinding.ActivityAddGaugeBinding

class AddGaugeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddGaugeBinding
    private lateinit var variableRepository: VariableRepository
    private lateinit var settingsManager: SettingsManager
    private lateinit var adapter: VariableAdapter
    
    private var selectedPosition = GaugePosition.TOP
    private var allVariables: List<EcuVariable> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddGaugeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        variableRepository = VariableRepository(this)
        settingsManager = SettingsManager(this)
        
        allVariables = variableRepository.getOutputVariables()
        
        setupSearch()
        setupPositionToggle()
        setupVariableList()
        
        updateVariableList("")
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                updateVariableList(query)
            }
        })
        
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
        }
        
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupPositionToggle() {
        // Check if top row is full (2 gauges max)
        val topGaugeCount = settingsManager.gauges.count { it.position == GaugePosition.TOP }
        val topRowFull = topGaugeCount >= 2
        
        // Default to secondary if top is full
        if (topRowFull) {
            selectedPosition = GaugePosition.SECONDARY
            binding.togglePosition.check(R.id.btnPositionSecondary)
            binding.btnPositionTop.isEnabled = false
            binding.btnPositionTop.text = "Top Row (Full)"
        } else {
            selectedPosition = GaugePosition.TOP
            binding.togglePosition.check(R.id.btnPositionTop)
        }
        
        binding.togglePosition.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedPosition = when (checkedId) {
                    R.id.btnPositionTop -> GaugePosition.TOP
                    R.id.btnPositionSecondary -> GaugePosition.SECONDARY
                    else -> GaugePosition.SECONDARY
                }
            }
        }
    }

    private fun setupVariableList() {
        adapter = VariableAdapter { variable ->
            addGauge(variable)
        }
        
        binding.rvVariables.apply {
            layoutManager = LinearLayoutManager(this@AddGaugeActivity)
            adapter = this@AddGaugeActivity.adapter
        }
    }

    private fun updateVariableList(query: String) {
        val filtered = if (query.isBlank()) {
            allVariables
        } else {
            allVariables.filter { it.name.contains(query, ignoreCase = true) }
        }
        
        adapter.updateVariables(filtered)
        binding.tvVariableCount.text = "${filtered.size} variables"
    }

    private fun addGauge(variable: EcuVariable) {
        val gauge = GaugeConfig(
            variableHash = variable.hash,
            variableName = variable.name,
            label = variable.name.take(12),
            displayType = DisplayType.NUMBER,
            position = selectedPosition
        )
        
        if (settingsManager.gauges.any { it.variableHash == variable.hash }) {
            Toast.makeText(this, "${variable.name} already added", Toast.LENGTH_SHORT).show()
            return
        }
        
        settingsManager.addGauge(gauge)
        Toast.makeText(this, "Added ${variable.name}", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    // Adapter for variable list
    inner class VariableAdapter(
        private val onItemClick: (EcuVariable) -> Unit
    ) : RecyclerView.Adapter<VariableAdapter.ViewHolder>() {

        private var variables: List<EcuVariable> = emptyList()

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvVariableName)
            val tvHash: TextView = view.findViewById(R.id.tvVariableHash)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_variable, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val variable = variables[position]
            holder.tvName.text = variable.name
            holder.tvHash.text = "Hash: ${variable.hash}"
            holder.itemView.setOnClickListener { onItemClick(variable) }
        }

        override fun getItemCount() = variables.size

        fun updateVariables(newVariables: List<EcuVariable>) {
            variables = newVariables
            notifyDataSetChanged()
        }
    }
}
