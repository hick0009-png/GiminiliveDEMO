package com.example.geminimultimodalliveapi.utils

import com.example.geminimultimodalliveapi.network.DeepgramLiveClient.WordDetail
import kotlinx.coroutines.*
import java.util.Locale

class SpeakerLockManager(
    private val coroutineScope: CoroutineScope,
    private val isAudioPlaying: () -> Boolean = { false }
) {

    @Volatile
    var activeSpeakerId: Int? = null
        private set

    private var lockTimerJob: Job? = null
    private val lockDurationMs = 8000L // 8 seconds lock window

    private fun normalizeText(text: String): String {
        // Strip Thai tone marks (U+0E47 to U+0E4C) and common punctuation/spaces
        return text.replace(TONE_MARKS_REGEX, "")
            .lowercase(Locale.getDefault())
            .replace(PUNCTUATION_REGEX, "")
            .trim()
    }

    private fun getWakeWordVariations(wakeWord: String): List<String> {
        val normalized = normalizeText(wakeWord)
        val list = mutableListOf(normalized)
        if (normalized == "กอหญา") {
            list.add("กหญา")
            list.add("กยา")
            list.add("กอยา")
            list.add("กนยา")
            list.add("กญญา")
        }
        return list
    }

    @Synchronized
    fun processIncomingWords(words: List<WordDetail>, wakeWord: String): String? {
        if (words.isEmpty()) return null

        val variations = getWakeWordVariations(wakeWord)

        // 1. Scan for the wake word variations in the incoming chunk using Thai-friendly normalization
        var foundWakeWord = false
        var wakeWordSpeakerId: Int? = null

        // First try exact match per word
        for (word in words) {
            val normWord = normalizeText(word.word)
            if (variations.contains(normWord)) {
                foundWakeWord = true
                wakeWordSpeakerId = word.speaker
                break
            }
        }

        // If not found, try joining words to see if it matches (for languages like Thai without word spaces)
        if (!foundWakeWord) {
            val joinedText = normalizeText(words.joinToString("") { it.word })
            for (variation in variations) {
                if (joinedText.contains(variation)) {
                    foundWakeWord = true
                    val speakerCounts = mutableMapOf<Int, Int>()
                    for (word in words) {
                        val normW = normalizeText(word.word)
                        if (normW.isNotEmpty() && (variation.contains(normW) || normW.contains(variation))) {
                            speakerCounts[word.speaker] = (speakerCounts[word.speaker] ?: 0) + 1
                        }
                    }
                    wakeWordSpeakerId = speakerCounts.maxByOrNull { it.value }?.key ?: words.first().speaker
                    break
                }
            }
        }

        // 2. If wake word is found, lock onto that speaker
        if (foundWakeWord && wakeWordSpeakerId != null) {
            activeSpeakerId = wakeWordSpeakerId
            resetLockTimer()
        }

        // 3. If locked, filter and return only the locked speaker's words
        var lockedId = activeSpeakerId
        if (lockedId == null && 
            com.example.geminimultimodalliveapi.session.SessionStateHolder.state.value is com.example.geminimultimodalliveapi.session.SessionState.Active &&
            !isAudioPlaying()) {
            
            lockedId = words.firstOrNull()?.speaker
            activeSpeakerId = lockedId
            if (lockedId != null) {
                android.util.Log.i("SpeakerLockManager", "Automatically locked onto speaker $lockedId because session is Active")
                resetLockTimer()
            }
        }

        return if (lockedId != null) {
            val filteredWords = words.filter { it.speaker == lockedId }
            if (filteredWords.isNotEmpty()) {
                // If we recently received words from the active speaker, reset the timer to keep the lock active
                // BUT only if the AI is not speaking! (to prevent echo from extending the lock)
                if (!isAudioPlaying()) {
                    resetLockTimer()
                }
                filteredWords.joinToString(" ") { it.word }
            } else {
                null
            }
        } else {
            null
        }
    }

    @Synchronized
    fun extendLock() {
        if (activeSpeakerId != null) {
            resetLockTimer()
        }
    }

    @Synchronized
    fun releaseLock() {
        activeSpeakerId = null
        lockTimerJob?.cancel()
        lockTimerJob = null
    }

    private fun resetLockTimer() {
        lockTimerJob?.cancel()
        lockTimerJob = coroutineScope.launch {
            delay(lockDurationMs)
            synchronized(this@SpeakerLockManager) {
                activeSpeakerId = null
                lockTimerJob = null
            }
        }
    }

    companion object {
        private val TONE_MARKS_REGEX = "[\u0E47-\u0E4C]".toRegex()
        private val PUNCTUATION_REGEX = "[.,!?\\s]".toRegex()
    }
}
