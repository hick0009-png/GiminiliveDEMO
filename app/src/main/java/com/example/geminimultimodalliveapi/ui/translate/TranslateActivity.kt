package com.example.geminimultimodalliveapi.ui.translate

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.geminimultimodalliveapi.R
import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TranslateActivity : AppCompatActivity() {

    private lateinit var txtOtherPerson: TextView
    private lateinit var txtUser: TextView
    private lateinit var txtAdvice: TextView
    private lateinit var layoutAdvice: LinearLayout
    private lateinit var btnClose: ImageButton
    
    private val closeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_CLOSE_TRANSLATE_UI") {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        txtOtherPerson = findViewById(R.id.txtOtherPerson)
        txtUser = findViewById(R.id.txtUser)
        txtAdvice = findViewById(R.id.txtAdvice)
        layoutAdvice = findViewById(R.id.layoutAdvice)
        btnClose = findViewById(R.id.btnClose)

        btnClose.setOnClickListener {
            finish()
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, android.content.IntentFilter("ACTION_CLOSE_TRANSLATE_UI"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, android.content.IntentFilter("ACTION_CLOSE_TRANSLATE_UI"))
        }

        SessionStateHolder.clearTranslateSessionData()

        lifecycleScope.launch {
            SessionStateHolder.liveUserTranscript.collectLatest { text ->
                if (text.isNotEmpty()) {
                    txtUser.text = text
                } else {
                    txtUser.text = "รอเสียงพูด..."
                }
            }
        }

        lifecycleScope.launch {
            SessionStateHolder.liveTranslateTranscript.collectLatest { text ->
                if (text.isNotEmpty()) {
                    txtOtherPerson.text = text
                } else {
                    txtOtherPerson.text = "Waiting for speech..."
                }
            }
        }

        lifecycleScope.launch {
            SessionStateHolder.state.collectLatest { state ->
                when (state) {
                    is SessionState.Active -> {
                        // Check if there is advice in liveAdviceLog
                        val advice = SessionStateHolder.liveAdviceLog.lastOrNull()
                        if (advice != null) {
                            txtAdvice.text = advice
                            layoutAdvice.visibility = View.VISIBLE
                        } else {
                            layoutAdvice.visibility = View.GONE
                        }
                    }
                    is SessionState.Disconnected -> {
                        txtUser.text = "Session ended"
                        txtOtherPerson.text = "Session ended"
                    }
                    else -> {
                        // Handled by liveUserTranscript and liveTranslateTranscript flows
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(closeReceiver)
    }
}
