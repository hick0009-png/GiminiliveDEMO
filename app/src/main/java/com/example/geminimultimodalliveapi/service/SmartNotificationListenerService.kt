package com.example.geminimultimodalliveapi.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.geminimultimodalliveapi.FloatingWidgetService

class SmartNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("NotificationListener", "SmartNotificationListenerService connected and active")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        // Exclude our own app's notifications
        if (packageName == this.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Filter messages (or popular messaging apps)
        val isMessageCategory = notification.category == Notification.CATEGORY_MESSAGE
        val isPopularMessagingApp = when (packageName) {
            "jp.naver.line.android",        // LINE
            "com.facebook.orca",            // Messenger
            "com.whatsapp",                 // WhatsApp
            "org.telegram.messenger",       // Telegram
            "com.google.android.apps.messaging", // Google Messages (SMS)
            "com.android.mms"               // System SMS
            -> true
            else -> false
        }

        if (isMessageCategory || isPopularMessagingApp) {
            val appPrefs = com.example.geminimultimodalliveapi.data.AppPreferences.getInstance(this)
            if (!appPrefs.isNotificationPerceptEnabled) {
                Log.d("NotificationListener", "Notification perception is disabled, skipping intercept processing.")
                return
            }

            val title = extras.getString(Notification.EXTRA_TITLE) ?: "ผู้ส่งนิรนาม"
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            if (text.isEmpty()) return

            val appName = getAppName(packageName)
            Log.i("NotificationListener", "Intercepted message from $appName: Sender=$title (Msg length: ${text.length})")
            writeNotificationLog(appName, title, text)

            // Check if Gemini Live Session is connected
            val widgetService = FloatingWidgetService.instance
            val liveClient = widgetService?.liveClient
            if (liveClient != null && liveClient.isConnected) {
                // Send system notification alert to Gemini
                val messageText = "SYSTEM: มีแจ้งเตือนข้อความเข้าใหม่จากแอป $appName โดยคุณ $title ส่งข้อความว่า \"$text\" ช่วยอ่านออกเสียงแจ้งเตือนนี้ให้ผู้ใช้ทราบโดยด่วนเป็นภาษาไทยอย่างกระชับ"
                
                // Use a non-blocking coroutine or send directly
                try {
                    liveClient.sendTextMessage(messageText)
                    Log.i("NotificationListener", "Sent message notification to Gemini session")
                } catch (e: Exception) {
                    Log.e("NotificationListener", "Failed to forward notification to Gemini", e)
                }
            } else {
                Log.d("NotificationListener", "Gemini session is not connected, skipping notification forwarding")
            }
        }
    }

    private fun getAppName(packageName: String): String {
        val pm = packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            when (packageName) {
                "jp.naver.line.android" -> "LINE"
                "com.facebook.orca" -> "Messenger"
                "com.whatsapp" -> "WhatsApp"
                "org.telegram.messenger" -> "Telegram"
                "com.google.android.apps.messaging" -> "ข้อความ"
                else -> packageName.substringAfterLast('.')
            }
        }
    }

    private fun writeNotificationLog(appName: String, title: String, text: String) {
        try {
            val logDir = java.io.File(filesDir, "situational_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val logFile = java.io.File(logDir, "telemetry_notification_$timestamp.json")
            val json = """
                {
                  "eventId": "${java.util.UUID.randomUUID()}",
                  "timestamp": $timestamp,
                  "eventType": "NOTIFICATION_RECEIVED",
                  "appName": "$appName",
                  "sender": "$title",
                  "textLength": ${text.length}
                }
            """.trimIndent()
            logFile.writeText(json)
            Log.d("NotificationListener", "[Telemetry] Logged incoming notification: $appName from $title (Saved: files/situational_logs/${logFile.name})")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Failed to write notification telemetry log", e)
        }
    }
}
