package com.example.geminimultimodalliveapi.architecture

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class ConditionType { ON_MOTION, ON_LOCATION, ON_TOPIC, GENERAL }

data class DynamicSystemRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val conditionType: ConditionType,
    val conditionValue: String,        // e.g. "DRIVING", "SHOPPING_MALL", "used_car"
    val instructionToInject: String,   // Prompt directive to append
    val isActive: Boolean = true
)

class DynamicRulesManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("dynamic_situational_rules", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var cachedRules: List<DynamicSystemRule>? = null

    @Synchronized
    fun saveRule(rule: DynamicSystemRule) {
        val rules = getAllRules().toMutableList()
        // Deduplication & Override logic: remove previous rule with the same condition properties
        val removed = rules.removeAll { 
            it.conditionType == rule.conditionType && 
            it.conditionValue.trim().equals(rule.conditionValue.trim(), ignoreCase = true) 
        }
        if (removed) {
            Log.d("DynamicRulesManager", "Overwriting existing rule for ${rule.conditionType}=${rule.conditionValue}")
        }
        
        rules.add(rule)
        cachedRules = rules
        prefs.edit().putString("rules_list", gson.toJson(rules)).apply()
        
        Log.i("DynamicRulesManager", "Saved rule: ${rule.conditionType}=${rule.conditionValue} -> ${rule.instructionToInject}")
        
        // Log for user visibility
        val msg = "[Rules] Saved rule for ${rule.conditionValue}: '${rule.instructionToInject}'"
        com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog(msg)
    }

    @Synchronized
    fun getAllRules(): List<DynamicSystemRule> {
        val cached = cachedRules
        if (cached != null) return cached
        val json = prefs.getString("rules_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DynamicSystemRule>>() {}.type
            val rules: List<DynamicSystemRule> = gson.fromJson(json, type)
            cachedRules = rules
            rules
        } catch (e: Exception) {
            Log.e("DynamicRulesManager", "Failed to parse rules, returning empty", e)
            emptyList()
        }
    }

    @Synchronized
    fun clearAllRules() {
        cachedRules = null
        prefs.edit().remove("rules_list").apply()
        Log.i("DynamicRulesManager", "Cleared all dynamic rules")
        com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog("[Rules] Cleared all custom rules")
    }

    /**
     * Aggregates and returns prompt instructions for matched context sensors.
     */
    @Synchronized
    fun getActiveInstructionsForContext(currentMotion: String, currentLocation: String, currentTopic: String): String {
        val activeRules = getAllRules().filter { it.isActive }
        val sb = java.lang.StringBuilder()
        
        for (rule in activeRules) {
            val isMatched = when (rule.conditionType) {
                ConditionType.ON_MOTION -> rule.conditionValue.equals(currentMotion, ignoreCase = true)
                ConditionType.ON_LOCATION -> rule.conditionValue.equals(currentLocation, ignoreCase = true)
                ConditionType.ON_TOPIC -> rule.conditionValue.equals(currentTopic, ignoreCase = true)
                ConditionType.GENERAL -> true
            }
            if (isMatched) {
                sb.append("\n- User Dynamic Rule: ${rule.instructionToInject}")
            }
        }
        
        val compiledRules = sb.toString()
        if (compiledRules.isNotEmpty()) {
            Log.d("DynamicRulesManager", "Injected rules for context (Motion:$currentMotion, Location:$currentLocation, Topic:$currentTopic): $compiledRules")
        }
        return compiledRules
    }
}
