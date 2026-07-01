package com.example.geminimultimodalliveapi.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.geminimultimodalliveapi.MainActivity
import com.example.geminimultimodalliveapi.R

class SessionNotificationManager(private val service: Service) {

    companion object {
        private const val CHANNEL_ID = "GeminiLiveServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    fun showOrUpdateNotification(contentText: String) {
        createNotificationChannel()

        val notificationIntent = Intent(service, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            service,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Gemini Live Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.baseline_mic_24)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun stopNotification() {
        service.stopForeground(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Gemini Live Assistant Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
