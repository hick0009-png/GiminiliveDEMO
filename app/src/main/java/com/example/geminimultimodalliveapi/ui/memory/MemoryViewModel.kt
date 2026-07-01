package com.example.geminimultimodalliveapi.ui.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geminimultimodalliveapi.memory.MemoryEntry
import com.example.geminimultimodalliveapi.memory.MemoryManager
import com.example.geminimultimodalliveapi.utils.LocalVehicleDbHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = LocalVehicleDbHelper(application)
    private val memoryManager = MemoryManager(application)

    private val _licensePlate = MutableStateFlow("")
    val licensePlate: StateFlow<String> = _licensePlate.asStateFlow()

    private val _taxCircle = MutableStateFlow("")
    val taxCircle: StateFlow<String> = _taxCircle.asStateFlow()

    private val _maintenance = MutableStateFlow("")
    val maintenance: StateFlow<String> = _maintenance.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val memories: StateFlow<List<MemoryEntry>> = _memories.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        // Load vehicle info
        val plates = dbHelper.queryInfo("license_plate")
        _licensePlate.value = plates.firstOrNull()?.get("info_value") ?: ""

        val tax = dbHelper.queryInfo("tax_circle")
        _taxCircle.value = tax.firstOrNull()?.get("info_value") ?: ""

        val maint = dbHelper.queryInfo("maintenance")
        _maintenance.value = maint.firstOrNull()?.get("info_value") ?: ""

        // Load memories
        _memories.value = memoryManager.getAllMemories()
    }

    fun saveVehicleInfo(category: String, keyName: String, value: String) {
        viewModelScope.launch {
            val success = dbHelper.saveInfo(category, keyName, value)
            if (success) {
                val memoryId = "vehicle_${category}_${keyName}"
                memoryManager.addFact(memoryId, "ข้อมูลรถหมวดหมู่ $category ($keyName): $value", isPinned = false, category = category)
                loadData()
            }
        }
    }

    fun addCustomFact(content: String, isPinned: Boolean = false) {
        viewModelScope.launch {
            if (content.isNotBlank()) {
                val memoryId = "fact_${System.currentTimeMillis()}"
                memoryManager.addFact(memoryId, content, isPinned = isPinned)
                loadData()
            }
        }
    }

    fun togglePin(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            memoryManager.updateMemoryPin(id, isPinned)
            loadData()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            if (id.startsWith("vehicle_")) {
                val parts = id.removePrefix("vehicle_").split("_")
                if (parts.size >= 2) {
                    val category = parts[0]
                    val keyName = parts.subList(1, parts.size).joinToString("_")
                    dbHelper.deleteInfo(category, keyName)
                }
            }
            memoryManager.deleteMemory(id)
            loadData()
        }
    }
}
