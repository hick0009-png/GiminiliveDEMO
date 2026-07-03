package com.example.geminimultimodalliveapi

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.utils.PermissionHelper

class GeminiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        Log.i("TileService", "onStartListening")
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        Log.i("TileService", "onClick")
        
        val isConnected = FloatingWidgetService.isSessionConnected
        val appPrefs = AppPreferences.getInstance(this)
        val apiKey = appPrefs.apiKey

        if (isConnected) {
            // Directly disconnect from the background
            FloatingWidgetService.disconnectSession(this)
        } else {
            // Check if we have API key and permissions to connect in the background
            val hasMicPermission = PermissionHelper.hasRecordAudioPermission(this)
            if (apiKey.isNotEmpty() && hasMicPermission) {
                FloatingWidgetService.connectSession(this, apiKey)
            } else {
                // If API Key or permission is missing, launch MainActivity to guide the user
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("TRIGGER_CONNECTION", true)
                }
                try {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Starting activity from tile in Android 14+ requires pending intent or special handling
                        // but startActivityAndCollapse works on most OS versions.
                        startActivityAndCollapse(intent)
                    } else {
                        startActivityAndCollapse(intent)
                    }
                } catch (e: Exception) {
                    Log.e("TileService", "Failed to start activity and collapse drawer", e)
                }
            }
        }
    }

    private fun updateTileState(forcedConnected: Boolean? = null) {
        val tile = qsTile ?: return
        val isConnected = forcedConnected ?: FloatingWidgetService.isSessionConnected
        tile.state = if (isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Gemini Live"
        tile.updateTile()
    }
}
