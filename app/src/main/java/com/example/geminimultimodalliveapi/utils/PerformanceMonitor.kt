package com.example.geminimultimodalliveapi.utils

import android.util.Log
import java.util.Collections

class PerformanceMonitor(
    private val onPerformanceLevelChanged: (PerformanceLevel) -> Unit
) {
    enum class PerformanceLevel(
        val sendIntervalMs: Long,
        val dimension: Int,
        val quality: Int,
        val description: String
    ) {
        LOW(5000, 320, 55, "Low (Battery Saver/Slow Device)"),
        MEDIUM(3000, 480, 70, "Medium (Balanced)"),
        HIGH(1500, 480, 70, "High (Performance)")
    }

    private val jitterHistory = Collections.synchronizedList(mutableListOf<Long>())
    private val processingTimeHistory = Collections.synchronizedList(mutableListOf<Long>())
    private val HISTORY_LIMIT = 3
    private var lastLevelChangeTime: Long = 0
    private val COOLDOWN_PERIOD_MS = 15000

    var currentLevel = PerformanceLevel.MEDIUM
        private set

    fun recordJitter(jitter: Long) {
        synchronized(jitterHistory) {
            jitterHistory.add(jitter)
            if (jitterHistory.size > HISTORY_LIMIT) {
                jitterHistory.removeAt(0)
            }
        }
        evaluatePerformance()
    }

    fun recordProcessingTime(duration: Long) {
        synchronized(processingTimeHistory) {
            processingTimeHistory.add(duration)
            if (processingTimeHistory.size > HISTORY_LIMIT) {
                processingTimeHistory.removeAt(0)
            }
        }
        evaluatePerformance()
    }

    private fun evaluatePerformance() {
        val avgJitter = synchronized(jitterHistory) {
            if (jitterHistory.isEmpty()) 0.0 else jitterHistory.average()
        }
        val avgProcessingTime = synchronized(processingTimeHistory) {
            if (processingTimeHistory.isEmpty()) 0.0 else processingTimeHistory.average()
        }

        val now = System.currentTimeMillis()

        // Critical conditions for downgrading (immediate)
        if (avgJitter > 150.0 || avgProcessingTime > 150.0) {
            if (currentLevel == PerformanceLevel.HIGH) {
                changePerformanceLevel(PerformanceLevel.MEDIUM)
            } else if (currentLevel == PerformanceLevel.MEDIUM) {
                changePerformanceLevel(PerformanceLevel.LOW)
            }
            return
        }

        // Conditions for upgrading (requires cooldown and consistent performance)
        if (now - lastLevelChangeTime > COOLDOWN_PERIOD_MS) {
            if (avgJitter < 30.0 && avgProcessingTime < 70.0) {
                if (currentLevel == PerformanceLevel.LOW) {
                    changePerformanceLevel(PerformanceLevel.MEDIUM)
                } else if (currentLevel == PerformanceLevel.MEDIUM) {
                    changePerformanceLevel(PerformanceLevel.HIGH)
                }
            }
        }
    }

    private fun changePerformanceLevel(newLevel: PerformanceLevel) {
        if (currentLevel == newLevel) return
        Log.i("PerformanceMonitor", "Changing performance level from $currentLevel to $newLevel")
        currentLevel = newLevel
        lastLevelChangeTime = System.currentTimeMillis()

        // Clear history so we evaluate the new level fresh
        jitterHistory.clear()
        processingTimeHistory.clear()

        onPerformanceLevelChanged(newLevel)
    }
}
