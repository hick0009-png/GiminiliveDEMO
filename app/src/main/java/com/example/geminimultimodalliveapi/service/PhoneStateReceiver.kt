package com.example.geminimultimodalliveapi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyManager
import android.util.Log
import java.util.Locale

class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            Log.i("PhoneStateReceiver", "Incoming call ringing: $incomingNumber")

            announceIncomingCall(context, incomingNumber)
        }
    }

    private fun announceIncomingCall(context: Context, phoneNumber: String?) {
        val pendingResult = goAsync()
        val contactName = if (phoneNumber != null) getContactName(context, phoneNumber) else null
        val textToSpeak = if (contactName != null) {
            "สายเรียกเข้าจากคุณ $contactName"
        } else if (!phoneNumber.isNullOrBlank()) {
            val formattedNumber = phoneNumber.map { it.toString() }.joinToString(" ")
            "สายเรียกเข้าจากหมายเลข $formattedNumber"
        } else {
            "มีสายเรียกเข้า"
        }

        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    val result = setLanguage(Locale("th"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("PhoneStateReceiver", "Thai language is not supported or missing data")
                        pendingResult.finish()
                        return@apply
                    }
                    
                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            tts?.shutdown()
                            pendingResult.finish()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            tts?.shutdown()
                            pendingResult.finish()
                        }
                    })

                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "CallerAnnouncer")
                    }
                    speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "CallerAnnouncer")
                }
            } else {
                Log.e("PhoneStateReceiver", "TextToSpeech initialization failed")
                pendingResult.finish()
            }
        }

        // Safety timeout to prevent keeping BroadcastReceiver alive indefinitely
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e("PhoneStateReceiver", "Error shutting down TTS during timeout", e)
            }
            try {
                pendingResult.finish()
            } catch (e: Exception) {
                // ignore
            }
        }, 15000L) // 15 seconds timeout
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (android.content.pm.PackageManager.PERMISSION_GRANTED != 
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)) {
            return null
        }
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Error looking up contact name", e)
        }
        return null
    }
}
