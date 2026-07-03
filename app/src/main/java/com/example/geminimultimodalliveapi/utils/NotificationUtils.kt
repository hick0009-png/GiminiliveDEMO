package com.example.geminimultimodalliveapi.utils

import android.content.ComponentName
import android.content.Context
import com.example.geminimultimodalliveapi.service.SmartNotificationListenerService

fun Context.isNotificationServiceEnabled(): Boolean {
    val cn = ComponentName(this, SmartNotificationListenerService::class.java)
    val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}
