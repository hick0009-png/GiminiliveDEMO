package com.example.geminimultimodalliveapi.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class AppPreferences private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var isFallbackActive = false
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        isFallbackActive = true
        // Fallback to normal shared preferences in case of keystore corruption or emulator issues
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        migrateOldPrefs(context)
    }

    private fun migrateOldPrefs(context: Context) {
        val oldPrefs = context.applicationContext.getSharedPreferences("GeminiPrefs", Context.MODE_PRIVATE)
        if (oldPrefs.contains(KEY_API_KEY) && !sharedPrefs.contains(KEY_API_KEY)) {
            val oldApiKey = oldPrefs.getString(KEY_API_KEY, "") ?: ""
            val oldWidget = oldPrefs.getBoolean(KEY_FLOATING_WIDGET_ENABLED, false)
            val oldVoice = oldPrefs.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
            val oldWake = oldPrefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
            val oldChime = oldPrefs.getBoolean(KEY_CHIME_ENABLED, true)

            val apiKeyToSave = if (isFallbackActive && oldApiKey.isNotEmpty()) encryptFallback(oldApiKey) else oldApiKey
            sharedPrefs.edit().apply {
                putString(KEY_API_KEY, apiKeyToSave)
                putBoolean(KEY_FLOATING_WIDGET_ENABLED, oldWidget)
                putString(KEY_SELECTED_VOICE, oldVoice)
                putString(KEY_WAKE_WORD, oldWake)
                putBoolean(KEY_CHIME_ENABLED, oldChime)
                apply()
            }
            // Clear old API key from plain text for security
            oldPrefs.edit().remove(KEY_API_KEY).apply()
        }
    }

    var apiKey: String
        get() {
            val raw = sharedPrefs.getString(KEY_API_KEY, "") ?: ""
            return if (isFallbackActive && raw.isNotEmpty()) decryptFallback(raw) else raw
        }
        set(value) {
            val toSave = if (isFallbackActive && value.isNotEmpty()) encryptFallback(value) else value
            sharedPrefs.edit().putString(KEY_API_KEY, toSave).apply()
        }

    var isFloatingWidgetEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_FLOATING_WIDGET_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_FLOATING_WIDGET_ENABLED, value).apply()

    var selectedVoice: String
        get() = sharedPrefs.getString(KEY_SELECTED_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) = sharedPrefs.edit().putString(KEY_SELECTED_VOICE, value).apply()

    var wakeWord: String
        get() = sharedPrefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
        set(value) = sharedPrefs.edit().putString(KEY_WAKE_WORD, value).apply()

    var isChimeEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_CHIME_ENABLED, true)
        set(value) = sharedPrefs.edit().putBoolean(KEY_CHIME_ENABLED, value).apply()

    var deepgramApiKey: String
        get() {
            val raw = sharedPrefs.getString(KEY_DEEPGRAM_API_KEY, "") ?: ""
            return if (isFallbackActive && raw.isNotEmpty()) decryptFallback(raw) else raw
        }
        set(value) {
            val toSave = if (isFallbackActive && value.isNotEmpty()) encryptFallback(value) else value
            sharedPrefs.edit().putString(KEY_DEEPGRAM_API_KEY, toSave).apply()
        }

    var isSoloFocusEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_SOLO_FOCUS_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_SOLO_FOCUS_ENABLED, value).apply()

    var calendarReminderMinutes: Int
        get() = sharedPrefs.getInt(KEY_CALENDAR_REMINDER_MINUTES, 15)
        set(value) = sharedPrefs.edit().putInt(KEY_CALENDAR_REMINDER_MINUTES, value).apply()

    var isMicAgcEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_MIC_AGC_ENABLED, true)
        set(value) = sharedPrefs.edit().putBoolean(KEY_MIC_AGC_ENABLED, value).apply()

    var micGainValue: Float
        get() = sharedPrefs.getFloat(KEY_MIC_GAIN_VALUE, 2.0f)
        set(value) = sharedPrefs.edit().putFloat(KEY_MIC_GAIN_VALUE, value).apply()

    var activeSessionTimeoutMs: Long
        get() = sharedPrefs.getLong(KEY_ACTIVE_SESSION_TIMEOUT, 30000L)
        set(value) = sharedPrefs.edit().putLong(KEY_ACTIVE_SESSION_TIMEOUT, value).apply()

    var sessionDisconnectTimeoutMs: Long
        get() = sharedPrefs.getLong(KEY_SESSION_DISCONNECT_TIMEOUT, 300000L)
        set(value) = sharedPrefs.edit().putLong(KEY_SESSION_DISCONNECT_TIMEOUT, value).apply()

    var isSensorIntegrationEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_SENSOR_INTEGRATION_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_SENSOR_INTEGRATION_ENABLED, value).apply()

    var isProactiveEventsEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_PROACTIVE_EVENTS_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_PROACTIVE_EVENTS_ENABLED, value).apply()

    var isDynamicFpsEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_DYNAMIC_FPS_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_DYNAMIC_FPS_ENABLED, value).apply()

    var isNotificationPerceptEnabled: Boolean
        get() = sharedPrefs.getBoolean(KEY_NOTIFICATION_PERCEPT_ENABLED, false)
        set(value) = sharedPrefs.edit().putBoolean(KEY_NOTIFICATION_PERCEPT_ENABLED, value).apply()

    var lastDatingSkill: String
        get() = sharedPrefs.getString(KEY_LAST_DATING_SKILL, "") ?: ""
        set(value) = sharedPrefs.edit().putString(KEY_LAST_DATING_SKILL, value).apply()

    var lastDatingSkillId: String
        get() = sharedPrefs.getString(KEY_LAST_DATING_SKILL_ID, "") ?: ""
        set(value) = sharedPrefs.edit().putString(KEY_LAST_DATING_SKILL_ID, value).apply()

    fun getOrCreateDatabasePassword(): String {
        val rawPassword = sharedPrefs.getString("db_password", "") ?: ""
        val savedPassword = if (isFallbackActive && rawPassword.isNotEmpty()) {
            decryptFallback(rawPassword)
        } else {
            rawPassword
        }
        if (savedPassword.isNotEmpty()) return savedPassword
        
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val randomPassword = (1..32)
            .map { kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
            
        val passwordToSave = if (isFallbackActive) {
            encryptFallback(randomPassword)
        } else {
            randomPassword
        }
        sharedPrefs.edit().putString("db_password", passwordToSave).apply()
        return randomPassword
    }

    private fun encryptFallback(plainText: String): String {
        return try {
            val keyStr = getFallbackKey()
            val keySpec = javax.crypto.spec.SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8).copyOf(16), "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("AppPreferences", "Encryption failed", e)
            plainText
        }
    }

    private fun decryptFallback(encryptedText: String): String {
        return try {
            val keyStr = getFallbackKey()
            val keySpec = javax.crypto.spec.SecretKeySpec(keyStr.toByteArray(Charsets.UTF_8).copyOf(16), "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = android.util.Base64.decode(encryptedText, android.util.Base64.NO_WRAP)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("AppPreferences", "Decryption failed", e)
            encryptedText
        }
    }

    private fun getFallbackKey(): String {
        val androidId = android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "DefaultFallbackKey123"
        return androidId
    }

    companion object {
        private const val PREFS_NAME = "GeminiPrefsEncrypted"
        private const val KEY_API_KEY = "API_KEY"
        private const val KEY_DEEPGRAM_API_KEY = "DEEPGRAM_API_KEY"
        private const val KEY_SOLO_FOCUS_ENABLED = "SOLO_FOCUS_ENABLED"
        private const val KEY_FLOATING_WIDGET_ENABLED = "FLOATING_WIDGET_ENABLED"
        private const val KEY_SELECTED_VOICE = "SELECTED_VOICE"
        private const val KEY_WAKE_WORD = "WAKE_WORD"
        private const val KEY_CHIME_ENABLED = "CHIME_ENABLED"
        private const val KEY_CALENDAR_REMINDER_MINUTES = "CALENDAR_REMINDER_MINUTES"
        
        private const val KEY_MIC_AGC_ENABLED = "MIC_AGC_ENABLED"
        private const val KEY_MIC_GAIN_VALUE = "MIC_GAIN_VALUE"
        private const val KEY_ACTIVE_SESSION_TIMEOUT = "ACTIVE_SESSION_TIMEOUT"
        private const val KEY_SESSION_DISCONNECT_TIMEOUT = "SESSION_DISCONNECT_TIMEOUT"
        
        private const val KEY_SENSOR_INTEGRATION_ENABLED = "SENSOR_INTEGRATION_ENABLED"
        private const val KEY_PROACTIVE_EVENTS_ENABLED = "PROACTIVE_EVENTS_ENABLED"
        private const val KEY_DYNAMIC_FPS_ENABLED = "DYNAMIC_FPS_ENABLED"
        private const val KEY_NOTIFICATION_PERCEPT_ENABLED = "NOTIFICATION_PERCEPT_ENABLED"
        private const val KEY_LAST_DATING_SKILL = "LAST_DATING_SKILL"
        private const val KEY_LAST_DATING_SKILL_ID = "LAST_DATING_SKILL_ID"

        private const val DEFAULT_VOICE = "Aoede"
        private const val DEFAULT_WAKE_WORD = "กอหญ้า"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context).also { INSTANCE = it }
            }
        }
    }
}
