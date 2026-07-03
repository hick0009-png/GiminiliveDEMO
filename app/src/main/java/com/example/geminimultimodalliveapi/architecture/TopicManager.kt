package com.example.geminimultimodalliveapi.architecture

import android.util.Log

data class Topic(
    val id: String,
    val name: String,
    val keywords: MutableList<String> = mutableListOf(),
    val sessionStart: Long = System.currentTimeMillis(),
    var lastActivity: Long = System.currentTimeMillis()
)

class TopicManager(
    private val timeoutLimitMs: Long = 10 * 60 * 1000L // 10 minutes session close
) {
    var currentTopic: Topic? = null
        private set

    // Initial keyword lists for classification mapping
    private val topicKeywords = mutableMapOf(
        "used_car" to mutableListOf("รถมือสอง", "ค่างวด", "ผ่อนกี่ปี", "ดอกเบี้ย", "เงินดาวน์", "โตโยต้า", "ฮอนด้า", "ผ่อนรถ"),
        "navigation" to mutableListOf("ทางไป", "นำทาง", "แผนที่", "รถติดไหม", "ร้านอาหารแถวนี้", "ปั๊มน้ำมันใกล้สุด"),
        "weather" to mutableListOf("ฝนตกไหม", "อากาศ", "อุณหภูมิ", "พยากรณ์อากาศ")
    )

    fun addDynamicKeyword(topicName: String, keyword: String) {
        val normalizedKeyword = keyword.lowercase().trim()
        if (normalizedKeyword.isEmpty()) return

        val list = topicKeywords.getOrPut(topicName) { mutableListOf() }
        if (!list.contains(normalizedKeyword)) {
            if (list.size >= 50) {
                val indexToRemove = if (list.size > 5) 5 else 0
                if (indexToRemove < list.size) {
                    list.removeAt(indexToRemove)
                }
            }
            list.add(normalizedKeyword)
            Log.i("TopicManager", "Added dynamic keyword '$normalizedKeyword' to topic '$topicName'")
            com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog(
                "[Topic] Dynamically added keyword '$normalizedKeyword' to '$topicName'"
            )
        }
    }

    fun processQueryIntent(query: String): Topic {
        val now = System.currentTimeMillis()
        val detectedCategory = detectCategoryFromKeywords(query)
        val activeTopic = currentTopic

        if (activeTopic == null) {
            val name = detectedCategory ?: "general_chat"
            return createTopic(name)
        }

        val idleDuration = now - activeTopic.lastActivity

        val resultTopic = when {
            // Case 1: Topic timed out (10 minutes) -> Close and create new
            idleDuration > timeoutLimitMs -> {
                Log.d("TopicManager", "Topic '${activeTopic.name}' timed out. Creating new topic.")
                closeTopic(activeTopic)
                createTopic(detectedCategory ?: "general_chat")
            }
            
            // Case 2: Intent matches current topic, or no specific category detected but within conversational context (5 minutes)
            detectedCategory == activeTopic.name || (detectedCategory == null && idleDuration < 5 * 60 * 1000L) -> {
                updateTopic(activeTopic)
            }
            
            // Case 3: Explicitly switches to a different category
            detectedCategory != null && detectedCategory != activeTopic.name -> {
                switchTopic(activeTopic, detectedCategory)
            }
            
            // Default: Beyond 5 minutes but under 10 minutes with no matching keywords -> Start new general topic
            else -> {
                switchTopic(activeTopic, detectedCategory ?: "general_chat")
            }
        }
        return resultTopic
    }

    private fun createTopic(name: String): Topic {
        val newTopic = Topic(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            keywords = ArrayList(topicKeywords[name] ?: emptyList()).toMutableList()
        )
        currentTopic = newTopic
        Log.i("TopicManager", "Created Topic: $name (ID: ${newTopic.id})")
        
        com.example.geminimultimodalliveapi.session.SessionStateHolder.appendChatLog("[Topic] Switch to: $name")
        com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDiagnostics { current ->
            current.copy(activeTopic = name)
        }
        
        return newTopic
    }

    private fun updateTopic(topic: Topic): Topic {
        topic.lastActivity = System.currentTimeMillis()
        Log.d("TopicManager", "Updated Topic: ${topic.name}")
        return topic
    }

    private fun closeTopic(topic: Topic) {
        Log.i("TopicManager", "Closed Topic: ${topic.name}")
        if (currentTopic?.id == topic.id) {
            currentTopic = null
        }
    }

    private fun switchTopic(oldTopic: Topic, newTopicName: String): Topic {
        Log.i("TopicManager", "Switching Topic: '${oldTopic.name}' -> '$newTopicName'")
        closeTopic(oldTopic)
        return createTopic(newTopicName)
    }

    private fun detectCategoryFromKeywords(query: String): String? {
        val normalized = query.lowercase().replace("\\s".toRegex(), "")
        for ((category, keywords) in topicKeywords) {
            if (keywords.any { normalized.contains(it.lowercase().replace("\\s".toRegex(), "")) }) {
                return category
            }
        }
        return null
    }

    fun forceSetTopic(topicName: String) {
        val activeTopic = currentTopic
        if (activeTopic != null) {
            closeTopic(activeTopic)
        }
        createTopic(topicName)
    }
}
