package com.example.geminimultimodalliveapi

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.example.geminimultimodalliveapi.audio.AudioPlayer
import com.example.geminimultimodalliveapi.audio.AudioRecorder
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.memory.MemoryManager
import com.example.geminimultimodalliveapi.network.GeminiLiveClient
import com.example.geminimultimodalliveapi.service.GeminiToolDispatcher
import com.example.geminimultimodalliveapi.service.OverlayWidgetController
import com.example.geminimultimodalliveapi.service.SessionNotificationManager
import com.example.geminimultimodalliveapi.service.WakeWordDetector
import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import com.example.geminimultimodalliveapi.error.AppError
import com.example.geminimultimodalliveapi.utils.LocalVehicleDbHelper
import com.example.geminimultimodalliveapi.utils.PermissionHelper
import com.example.geminimultimodalliveapi.network.DeepgramLiveClient
import com.example.geminimultimodalliveapi.network.GeminiTextService
import com.example.geminimultimodalliveapi.utils.SpeakerLockManager
import android.speech.tts.TextToSpeech
import android.content.IntentFilter
import android.telephony.TelephonyManager
import com.example.geminimultimodalliveapi.service.PhoneStateReceiver
import java.util.Locale
import kotlinx.coroutines.*
import android.view.MotionEvent
import android.view.WindowManager
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import java.io.File

class FloatingWidgetService : Service() {

    private lateinit var dbHelper: LocalVehicleDbHelper
    lateinit var memoryManager: MemoryManager

    // Service Coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Decoupled Helper Components
    private lateinit var notificationManager: SessionNotificationManager
    private lateinit var widgetController: OverlayWidgetController
    private var radialPickerView: com.example.geminimultimodalliveapi.ui.RadialSkillPickerView? = null
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var toolDispatcher: GeminiToolDispatcher
    private var phoneStateReceiver: PhoneStateReceiver? = null

    // Situational Architecture Components
    lateinit var perceptionEngine: com.example.geminimultimodalliveapi.architecture.PerceptionEngine
        private set
    lateinit var attentionManager: com.example.geminimultimodalliveapi.architecture.AttentionManager
        private set
    lateinit var topicManager: com.example.geminimultimodalliveapi.architecture.TopicManager
        private set
    lateinit var dynamicRulesManager: com.example.geminimultimodalliveapi.architecture.DynamicRulesManager
        private set
    lateinit var contextManager: com.example.geminimultimodalliveapi.architecture.ContextManager
        private set
    lateinit var situationLogManager: com.example.geminimultimodalliveapi.architecture.SituationLogManager
        private set

    // Multi-Agent Dating Orchestrator
    private var datingOrchestrator: com.example.geminimultimodalliveapi.agent.DatingAnalysisOrchestrator? = null

    // Core Live Session States
    var liveClient: GeminiLiveClient? = null
        private set
    private var deepgramClient: DeepgramLiveClient? = null
    private var speakerLockManager: SpeakerLockManager? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    @Volatile
    private var isGeminiConnected = false
    @Volatile
    private var isDeepgramConnected = false
    private val chatHistory = mutableListOf<Pair<String, String>>()
    private val activeQueryBuffer = StringBuilder()

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var sessionAudioFocusRequest: AudioFocusRequest? = null
    private val sessionFocusLock = Any()
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    // Situational sensors variables
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var accelerometerListener: SensorEventListener? = null
    private var homeLatitude: Double? = null
    private var homeLongitude: Double? = null
    private val GEOFENCE_RADIUS_METERS = 100.0
    private var lastDecelerationAlertTime = 0L
    private val ALERT_THROTTLE_MS = 30000L

    // Voice Gate parameters for Focus Mode
    private var ambientNoiseLevel = 500f
    private var dynamicThreshold = 800
    private val voiceHangoverMs = 1000L
    private val maxPreRollChunks = 6
    private val preRollBuffer = java.util.LinkedList<ByteArray>()
    @Volatile
    private var voiceGateOpen = false
    @Volatile
    private var lastVoiceTime = 0L

    // Inactivity and Media Streaming Jobs
    private var lastActivityTime: Long = 0
    private var idleMonitorJob: Job? = null
    private var staticFrameJob: Job? = null
    private var deepgramKeepAliveJob: Job? = null

    // Throttling for memory context updates to avoid WebSocket flooding
    private var lastPromptRefreshTime = 0L
    private val promptRefreshThrottleMs = 8000L
    private var pendingPromptRefreshJob: Job? = null

    // Diarization-Gated Forked Stream variables
    private val dgAudioBufferQueue = java.util.LinkedList<ByteArray>()
    private val maxDgBufferChunks = 100
    private val amplitudeHistory = java.util.LinkedList<Int>()
    private val amplitudeHistorySize = 50
    @Volatile
    private var isSpeakerVerifiedActive = false
    @Volatile
    private var lastOwnerSpeechTime = 0L

    private var currentWakeWord = "กอหญ้า"

    // Dating Mode private variables
    private var datingSessionStartTime = 0L
    private var lastDatingActivityTime = 0L
    private var datingUserSpeakerId: Int? = null
    private var datingPartnerSpeakerId: Int? = null
    private val datingSpeakerCounts = mutableMapOf<Int, Int>()
    private val datingTranscriptHistory = mutableListOf<Pair<String, String>>()
    private var datingSilenceTimerJob: Job? = null
    private var lastDatingAnalysisTime = 0L
    @Volatile
    private var isAnalysisInProgress = false
    private val DATING_ANALYSIS_MIN_INTERVAL = 10000L

    companion object {
        private var weakInstance: java.lang.ref.WeakReference<FloatingWidgetService>? = null
        val instance: FloatingWidgetService?
            get() = weakInstance?.get()

        val isSessionConnected: Boolean
            get() = SessionStateHolder.state.value != SessionState.Disconnected

        val isRecording: Boolean
            get() {
                val s = SessionStateHolder.state.value
                return s is SessionState.Active && s.isRecording
            }

        val chatLogText: String
            get() = SessionStateHolder.chatLogs.value

        var lastCapturedFrame: String? = null

        fun sendImageFrame(b64Image: String) {
            lastCapturedFrame = b64Image
            instance?.liveClient?.sendMediaChunk(b64Image, "image/jpeg")
        }

        fun sendToolResponse(callId: String, success: Boolean) {
            instance?.liveClient?.sendToolResponse(callId, success)
        }

        fun connectSession(context: Context, apiKey: String) {
            if (!com.example.geminimultimodalliveapi.utils.NetworkUtils.isNetworkAvailable(context)) {
                android.widget.Toast.makeText(context, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต กรุณาเชื่อมต่ออินเทอร์เน็ตก่อนใช้งาน", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(context, FloatingWidgetService::class.java).apply {
                putExtra("IS_CONNECTED", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun disconnectSession(context: Context) {
            val intent = Intent(context, FloatingWidgetService::class.java).apply {
                putExtra("IS_CONNECTED", false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val clientListener = object : GeminiLiveClient.Listener {
        override fun onConnected() {
            Log.i("FloatingWidgetService", "Connected to Gemini Live")
            isGeminiConnected = true
            logAndNotify("SYSTEM: Connected to Live API!")
            checkAndCompleteConnection()
        }

        override fun onDisconnected(reason: String) {
            Log.i("FloatingWidgetService", "Connection closed: Reason=$reason")
            logAndNotify("SYSTEM: Connection closed (Reason: $reason)")
            handleDisconnect(unexpected = true)
        }

        override fun onReconnecting(attempt: Int) {
            Log.i("FloatingWidgetService", "Reconnecting to Gemini Live (attempt $attempt/3)...")
            logAndNotify("SYSTEM: Reconnecting... (attempt $attempt/3)")
            SessionStateHolder.updateState(SessionState.Reconnecting)
        }

        override fun onError(errorMsg: String) {
            Log.e("FloatingWidgetService", "Error: $errorMsg")
            logAndNotify("SYSTEM: Connection error ($errorMsg)")
            SessionStateHolder.updateState(SessionState.Error(errorMsg))
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_LONG).show()
            }
            handleDisconnect(unexpected = true)
        }

        override fun onTextMessageReceived(text: String) {
            logAndNotify("GEMINI: $text")
            situationLogManager.logChatTurn("model", text)
        }

        override fun onAudioChunkReceived(base64Audio: String, sampleRate: Int) {
            val isDateMode = com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive
            if (isDateMode) {
                return
            }
            lastActivityTime = System.currentTimeMillis()
            audioPlayer?.playAudioChunk(base64Audio, sampleRate, serviceScope) {
                lastActivityTime = System.currentTimeMillis()
            }
        }

        override fun onInterrupted() {
            Log.i("FloatingWidgetService", "Gemini Live interrupted playback")
            audioPlayer?.stop()
        }

        // Forward all tool requests to GeminiToolDispatcher
        override fun onCameraOpenRequested(callId: String) {
            toolDispatcher.handleCameraOpen(callId, liveClient)
        }

        override fun onCameraCloseRequested(callId: String) {
            toolDispatcher.handleCameraClose(callId, liveClient)
        }

        override fun onSaveVehicleInfoRequested(callId: String, category: String, keyName: String, infoValue: String) {
            toolDispatcher.handleSaveVehicleInfo(callId, category, keyName, infoValue, liveClient)
        }

        override fun onQueryVehicleInfoRequested(callId: String, category: String?) {
            toolDispatcher.handleQueryVehicleInfo(callId, category, liveClient)
        }

        override fun onGetCurrentTimeRequested(callId: String) {
            toolDispatcher.handleGetCurrentTime(callId, liveClient)
        }

        override fun onDeleteVehicleInfoRequested(callId: String, category: String, keyName: String?) {
            toolDispatcher.handleDeleteVehicleInfo(callId, category, keyName, liveClient)
        }

        override fun onQueryPolicyDocumentRequested(callId: String, query: String) {
            toolDispatcher.handleQueryPolicyDocument(callId, query, liveClient)
        }

        override fun onMakePhoneCallRequested(callId: String, phoneNumber: String) {
            toolDispatcher.handleMakePhoneCall(callId, phoneNumber, liveClient)
        }

        override fun onEndPhoneCallRequested(callId: String) {
            toolDispatcher.handleEndPhoneCall(callId, liveClient)
        }

        override fun onCreateCalendarEventRequested(
            callId: String,
            title: String,
            description: String,
            startTimeIso: String,
            durationMinutes: Int
        ) {
            toolDispatcher.handleCreateCalendarEvent(callId, title, description, startTimeIso, durationMinutes, liveClient)
        }

        override fun onListCalendarEventsRequested(callId: String) {
            toolDispatcher.handleListCalendarEvents(callId, liveClient)
        }

        override fun onGetCurrentWeatherRequested(callId: String) {
            toolDispatcher.handleGetCurrentWeather(callId, liveClient)
        }

        override fun onFindNearbyPlacesRequested(callId: String, placeType: String) {
            toolDispatcher.handleFindNearbyPlaces(callId, placeType, liveClient)
        }

        override fun onLaunchNavigationRequested(callId: String, destination: String) {
            toolDispatcher.handleLaunchNavigation(callId, destination, liveClient)
        }

        override fun onRememberPersonalFactRequested(callId: String, factContent: String, importance: Int, category: String) {
            toolDispatcher.handleRememberPersonalFact(callId, factContent, importance, category, liveClient)
        }

        override fun onForgetPersonalFactRequested(callId: String, query: String) {
            toolDispatcher.handleForgetPersonalFact(callId, query, liveClient)
        }

        override fun onQueryRelevantMemoriesRequested(callId: String, searchQuery: String) {
            toolDispatcher.handleQueryRelevantMemories(callId, searchQuery, liveClient)
        }

        override fun onSaveSystemRuleRequested(
            callId: String,
            conditionType: String?,
            conditionValue: String?,
            instruction: String?,
            action: String
        ) {
            toolDispatcher.handleSaveSystemRule(callId, conditionType, conditionValue, instruction, action, liveClient)
        }

        override fun onGetSituationalContextRequested(callId: String) {
            toolDispatcher.handleGetSituationalContext(callId, liveClient)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.getBooleanExtra("UPDATE_VISIBILITY", false)) {
                updateWidgetVisibility()
            }
            if (it.hasExtra("IS_CONNECTED")) {
                val isConnectedExtra = it.getBooleanExtra("IS_CONNECTED", false)
                val isConnected = SessionStateHolder.state.value != SessionState.Disconnected
                if (isConnectedExtra != isConnected) {
                    if (isConnectedExtra) {
                        val appPrefs = AppPreferences.getInstance(this)
                        connect(appPrefs.apiKey)
                    } else {
                        disconnect()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "GeminiLiveDemo:ServiceWakeLock").apply {
                acquire(10 * 60 * 1000L) // 10 minutes safety timeout
            }
            Log.i("FloatingWidgetService", "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.i("FloatingWidgetService", "WakeLock released")
        }
        wakeLock = null
    }

    fun updateWidgetVisibility() {
        val appPrefs = AppPreferences.getInstance(this)
        val isWidgetEnabled = appPrefs.isFloatingWidgetEnabled
        val isGranted = PermissionHelper.hasOverlayPermission(this)

        if (isWidgetEnabled && isGranted) {
            widgetController.show()
            val isConnected = SessionStateHolder.state.value != SessionState.Disconnected
            widgetController.updateWidgetColor(isConnected)
        } else {
            widgetController.hide()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("FloatingWidgetService", "Service created")

        // Must call startForeground immediately to prevent service crash
        notificationManager = SessionNotificationManager(this)
        notificationManager.showOrUpdateNotification("กำลังเริ่มระบบ...")
        
        // Dynamically register PhoneStateReceiver to receive CALL_STATE events securely (not exported)
        phoneStateReceiver = PhoneStateReceiver()
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(phoneStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(phoneStateReceiver, filter)
        }
        weakInstance = java.lang.ref.WeakReference(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        dbHelper = LocalVehicleDbHelper(this)
        memoryManager = MemoryManager(this)
        memoryManager.decayAll()

        notificationManager.showOrUpdateNotification("Floating Widget Ready")

        // Initialize Overlay controller with callbacks
        widgetController = OverlayWidgetController(this, object : OverlayWidgetController.Callbacks {
            override fun onSingleClick() {
                if (MainActivity.isCameraActive) {
                    Log.i("FloatingWidget", "Camera is active. Minimizing camera.")
                    val stopCameraIntent = Intent(this@FloatingWidgetService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("STOP_CAMERA", true)
                    }
                    startActivity(stopCameraIntent)
                } else {
                    val isConnected = SessionStateHolder.state.value != SessionState.Disconnected
                    if (isConnected) {
                        val state = SessionStateHolder.state.value
                        if (state is SessionState.Standby) {
                            transitionToState(SessionState.Active(true))
                        } else if (state is SessionState.Active) {
                            transitionToState(SessionState.Standby(currentWakeWord))
                        }
                    } else {
                        toggleConnection()
                    }
                }
            }

            override fun onDoubleClick() {
                val intent = Intent(this@FloatingWidgetService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            }

            override fun onLongPress() {
                showRadialSkillPicker()
            }

            override fun onTouchGesture(event: MotionEvent) {
                radialPickerView?.processTouchEvent(event)
            }
        })

        // Initialize WakeWord detector with callbacks
        wakeWordDetector = WakeWordDetector(this, object : WakeWordDetector.Listener {
            override fun onWakeWordDetected(command: String) {
                transitionToState(SessionState.Active(true), command)
            }

            override fun onError(error: Int) {
                // Restart listening if we are in standby
                val state = SessionStateHolder.state.value
                if (state is SessionState.Standby && isSessionConnected) {
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_NO_MATCH -> 50L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 200L
                        else -> 300L
                    }
                    serviceScope.launch {
                        delay(delay)
                        if (SessionStateHolder.state.value is SessionState.Standby && isSessionConnected) {
                            wakeWordDetector.startListening()
                        }
                    }
                }
            }
        })

        // Initialize Situational Architecture Components
        dynamicRulesManager = com.example.geminimultimodalliveapi.architecture.DynamicRulesManager(this)
        topicManager = com.example.geminimultimodalliveapi.architecture.TopicManager()
        
        attentionManager = com.example.geminimultimodalliveapi.architecture.AttentionManager { active ->
            Log.i("FloatingWidgetService", "Attention state updated: active=$active")
        }
        
        contextManager = com.example.geminimultimodalliveapi.architecture.ContextManager(dynamicRulesManager, topicManager)
        situationLogManager = com.example.geminimultimodalliveapi.architecture.SituationLogManager(this, contextManager, dynamicRulesManager)
        perceptionEngine = com.example.geminimultimodalliveapi.architecture.PerceptionEngine(this, serviceScope)

        // Listen to perception events and bind them to managers
        serviceScope.launch {
            perceptionEngine.events.collect { event ->
                attentionManager.onNewEvent(event)
                
                // Update Context Manager
                when (event) {
                    is com.example.geminimultimodalliveapi.architecture.PerceptionEvent.MotionStateChanged -> {
                        contextManager.currentMotion = event.motion.name
                        
                        // Apply user dynamic rules dynamically (e.g. ignore stranger speech when driving)
                        val rules = dynamicRulesManager.getAllRules()
                        val hasIgnoreOther = rules.any { 
                            it.conditionType == com.example.geminimultimodalliveapi.architecture.ConditionType.ON_MOTION &&
                            it.conditionValue.equals("DRIVING", ignoreCase = true) &&
                            (it.instructionToInject.contains("ignore", ignoreCase = true) || it.instructionToInject.contains("ห้ามแทรก", ignoreCase = true))
                        }
                        attentionManager.setIgnoreOtherSpeakers(hasIgnoreOther)
                        
                        refreshDynamicPrompt()
                    }
                    is com.example.geminimultimodalliveapi.architecture.PerceptionEvent.LocationChanged -> {
                        contextManager.currentLocation = event.placeType
                        refreshDynamicPrompt()
                    }
                    is com.example.geminimultimodalliveapi.architecture.PerceptionEvent.ScreenStateChanged -> {
                        contextManager.currentAttention = if (event.isScreenOn) "ACTIVE" else "BACKGROUND"
                        refreshDynamicPrompt()
                    }
                    else -> {}
                }
            }
        }

        // Initialize Tool dispatcher with logger callback
        toolDispatcher = GeminiToolDispatcher(this, dbHelper, memoryManager, serviceScope, object : GeminiToolDispatcher.Logger {
            override fun log(message: String) {
                logAndNotify(message)
            }
        })

        // Initialize Multi-Agent Dating Orchestrator
        val prefsInit = AppPreferences.getInstance(this)
        val keyInit = prefsInit.apiKey
        if (keyInit.isNotEmpty()) {
            val skillMgr = com.example.geminimultimodalliveapi.data.DatingSkillManager(this)
            val routerAgent = com.example.geminimultimodalliveapi.agent.DatingRouterAgent(keyInit, skillMgr)
            val skillAgent = com.example.geminimultimodalliveapi.agent.DatingSkillAgentImpl(keyInit)
            val docSelector = com.example.geminimultimodalliveapi.agent.DocumentSelector(this)
            datingOrchestrator = com.example.geminimultimodalliveapi.agent.DatingAnalysisOrchestrator(
                apiKey = keyInit,
                skillManager = skillMgr,
                routerAgent = routerAgent,
                primaryAgent = skillAgent,
                documentSelector = docSelector
            )
        }

        serviceScope.launch {
            SessionStateHolder.errorFlow.collect { error ->
                val thaiMessage = when (error) {
                    is AppError.Network -> "การเชื่อมต่อเครือข่ายล้มเหลว กรุณาตรวจสอบอินเทอร์เน็ต"
                    is AppError.Permission -> "ขาดสิทธิ์การใช้งานที่จำเป็น: ${error.type}"
                    is AppError.AuthExpired -> "สิทธิ์การเข้าถึงบัญชี Google หมดอายุ กรุณาลงชื่อเข้าใช้งานอีกครั้ง"
                    is AppError.Api -> "ข้อผิดพลาดระบบ API: ${error.message}"
                    is AppError.Tool -> "เครื่องมือขัดข้อง (${error.name}): ${error.message}"
                }
                Toast.makeText(this@FloatingWidgetService, thaiMessage, Toast.LENGTH_SHORT).show()
                notificationManager.showOrUpdateNotification("ข้อผิดพลาด: $thaiMessage")
            }
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("th", "TH"))
                if (result != TextToSpeech.LANG_MISSING_DATA && 
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true
                }
            }
        }

        updateWidgetVisibility()
    }

    private fun toggleConnection() {
        val appPrefs = AppPreferences.getInstance(this)
        val apiKey = appPrefs.apiKey
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please configure Gemini API Key inside the app", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } else {
            if (!PermissionHelper.hasRecordAudioPermission(this) || !PermissionHelper.hasCameraPermission(this)) {
                Toast.makeText(this, "โปรดอนุญาตสิทธิ์กล้องและไมโครโฟนในแอปก่อนเชื่อมต่อ", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } else {
                val isConnected = SessionStateHolder.state.value != SessionState.Disconnected
                if (isConnected) {
                    situationLogManager.logManualCancel()
                    disconnect()
                } else connect(apiKey)
            }
        }
    }

    private val deepgramCallback = object : DeepgramLiveClient.Callback {
        override fun onTranscriptReceived(
            transcript: String,
            speakerId: Int,
            isFinal: Boolean,
            wordDetails: List<DeepgramLiveClient.WordDetail>
        ) {
            Log.d("FloatingWidgetService", "onTranscriptReceived: transcript='$transcript', speakerId=$speakerId, isFinal=$isFinal, wordsCount=${wordDetails.size}")
            if (wordDetails.isNotEmpty()) {
                Log.d("FloatingWidgetService", "onTranscriptReceived words details: " + wordDetails.joinToString { "${it.word}(speaker:${it.speaker})" })
            }

            val isDateMode = com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive
            if (isDateMode) {
                if (isFinal && transcript.trim().isNotEmpty()) {
                    val currentLockedId = speakerLockManager?.activeSpeakerId
                    if (currentLockedId != null && currentLockedId != datingUserSpeakerId) {
                        Log.i("DatingMode", "User ID aligned from $datingUserSpeakerId to $currentLockedId")
                        datingUserSpeakerId = currentLockedId
                        datingPartnerSpeakerId = null
                        synchronized(datingSpeakerCounts) {
                            datingSpeakerCounts.clear()
                        }
                    }
                    
                    if (datingUserSpeakerId == null) {
                        datingUserSpeakerId = speakerId
                    }

                    if (speakerId == datingUserSpeakerId) {
                        onSpeechReceivedDating("User", transcript)
                    } else {
                        var isPartner = false
                        synchronized(datingSpeakerCounts) {
                            datingSpeakerCounts[speakerId] = (datingSpeakerCounts[speakerId] ?: 0) + 1
                            val maxSpeaker = datingSpeakerCounts.filterKeys { it != datingUserSpeakerId }.maxByOrNull { it.value }?.key
                            if (maxSpeaker != null && maxSpeaker != datingPartnerSpeakerId) {
                                Log.i("DatingMode", "Aligned Partner ID to $maxSpeaker")
                                datingPartnerSpeakerId = maxSpeaker
                            }
                            isPartner = (speakerId == datingPartnerSpeakerId)
                        }

                        if (isPartner) {
                            onSpeechReceivedDating("Partner", transcript)
                        } else {
                            Log.i("DatingMode", "Ignored crosstalk/noise from Speaker $speakerId: $transcript")
                        }
                    }
                }
                return
            }

            val lockManager = speakerLockManager ?: return
            val appPrefs = AppPreferences.getInstance(this@FloatingWidgetService)
            val filteredText = lockManager.processIncomingWords(wordDetails, appPrefs.wakeWord)
            Log.d("FloatingWidgetService", "SpeakerLockManager output: filteredText='$filteredText', lockedSpeakerId=${lockManager.activeSpeakerId}")

            if (filteredText != null) {
                if (filteredText.trim().isNotEmpty()) {
                    lastOwnerSpeechTime = System.currentTimeMillis()
                    if (!isSpeakerVerifiedActive) {
                        isSpeakerVerifiedActive = true
                        flushSpeakerAudioBuffer()
                    }

                    // Instantly stop playing audio if Gemini is speaking when user starts talking (Interruption in Focus Mode)
                    if (isAudioPlaying()) {
                        Log.i("FloatingWidgetService", "Interrupted by user speech (Focus Mode)")
                        audioPlayer?.stop()
                    }
                }
                if (isFinal) {
                    if (activeQueryBuffer.isNotEmpty()) {
                        activeQueryBuffer.append(" ")
                    }
                    activeQueryBuffer.append(filteredText)
                    logAndNotify("USER: $filteredText")

                    // Update Topic Manager dynamically
                    topicManager.processQueryIntent(filteredText)
                    refreshDynamicPrompt()
                } else {
                    val currentText = if (activeQueryBuffer.isNotEmpty()) {
                        "${activeQueryBuffer.toString()} $filteredText"
                    } else {
                        filteredText
                    }
                    val logs = SessionStateHolder.chatLogs.value
                    val lastLineIndex = logs.lastIndexOf("\n")
                    val updatedLogs = if (lastLineIndex >= 0 && logs.substring(lastLineIndex).startsWith("\nUSER: ")) {
                        logs.substring(0, lastLineIndex) + "\nUSER: $currentText"
                    } else {
                        "$logs\nUSER: $currentText"
                    }
                    SessionStateHolder.updateChatLogs(updatedLogs)
                }

                val currentState = SessionStateHolder.state.value
                if (currentState is SessionState.Standby) {
                    transitionToState(SessionState.Active(true))
                }
            }
        }

        override fun onUtteranceEnd() {
            val query = activeQueryBuffer.toString().trim()
            if (query.isNotEmpty()) {
                activeQueryBuffer.setLength(0)
                
                // Log user turn for Telemetry
                situationLogManager.logChatTurn("user", query)
                Log.i("FloatingWidgetService", "Utterance ended: '$query'")
            }
        }

        override fun onOpen() {
            logAndNotify("SYSTEM: เชื่อมต่อ Deepgram สำเร็จ!")
            isDeepgramConnected = true
            checkAndCompleteConnection()
        }

        override fun onClose() {
            logAndNotify("SYSTEM: ตัดการเชื่อมต่อ Deepgram")
            handleDisconnect(unexpected = true)
        }

        override fun onError(t: Throwable, responseCode: Int?) {
            val errorMsg = when (responseCode) {
                401 -> "คีย์ API ของ Deepgram ไม่ถูกต้อง โปรดตรวจสอบรหัสในหน้าตั้งค่า"
                402 -> "เครดิตการใช้งานหมดแล้ว โปรดตรวจสอบยอดคงเหลือบัญชีของคุณ"
                else -> "การเชื่อมต่อขัดข้อง: ${t.message}"
            }
            logAndNotify("SYSTEM ERROR: $errorMsg")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_LONG).show()
            }
            handleDisconnect(unexpected = true)
        }
    }

    fun connect(apiKey: String) {
        if (!com.example.geminimultimodalliveapi.utils.NetworkUtils.isNetworkAvailable(this)) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "ไม่มีการเชื่อมต่ออินเทอร์เน็ต กรุณาเชื่อมต่ออินเทอร์เน็ตก่อนใช้งาน", Toast.LENGTH_LONG).show()
            }
            disconnect()
            return
        }
        acquireWakeLock()
        val isConnected = SessionStateHolder.state.value != SessionState.Disconnected
        if (isConnected) return
        if (!PermissionHelper.hasRecordAudioPermission(this) || !PermissionHelper.hasCameraPermission(this)) {
            Log.e("FloatingWidgetService", "Missing required permissions, aborting connection")
            return
        }

        isGeminiConnected = false
        isDeepgramConnected = false

        SessionStateHolder.updateState(SessionState.Connecting)
        logAndNotify("SYSTEM: Validating API Key...")

        com.example.geminimultimodalliveapi.network.ApiKeyValidator.verifyApiKey(apiKey) { isValid, errorMsg ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (SessionStateHolder.state.value == SessionState.Disconnected) {
                    Log.i("FloatingWidgetService", "Validation completed but session was already disconnected/cancelled.")
                    return@post
                }
                if (!isValid) {
                    Toast.makeText(applicationContext, "ข้อผิดพลาด API Key: $errorMsg", Toast.LENGTH_LONG).show()
                    logAndNotify("SYSTEM: Connection failed ($errorMsg)")
                    SessionStateHolder.updateState(SessionState.Error(errorMsg))
                    handleDisconnect(unexpected = true)
                } else {
                    proceedConnect(apiKey)
                }
            }
        }
    }

    private fun proceedConnect(apiKey: String) {
        val appPrefs = AppPreferences.getInstance(this)
        currentWakeWord = appPrefs.wakeWord
        wakeWordDetector.updateWakeWord(currentWakeWord)

        if (appPrefs.isSoloFocusEnabled) {
            logAndNotify("SYSTEM: Connecting to Live API (Focus Mode)...")
            notificationManager.showOrUpdateNotification("Active background Focused voice conversation...")
        } else {
            logAndNotify("SYSTEM: Connecting to Live API...")
            notificationManager.showOrUpdateNotification("Active background voice conversation...")
        }

        val selectedVoice = appPrefs.selectedVoice
        val initialPrompt = getInitialCombinedPrompt()
        liveClient = GeminiLiveClient(apiKey, selectedVoice, currentWakeWord, initialPrompt, clientListener)
        liveClient?.connect()

        val deepgramKey = appPrefs.deepgramApiKey
        val isDateMode = com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive
        if (isDateMode) {
            datingSessionStartTime = System.currentTimeMillis()
            lastDatingActivityTime = System.currentTimeMillis()
            synchronized(datingTranscriptHistory) {
                datingTranscriptHistory.clear()
            }
            synchronized(datingSpeakerCounts) {
                datingSpeakerCounts.clear()
            }
            datingUserSpeakerId = null
            datingPartnerSpeakerId = null
        }
        if ((appPrefs.isSoloFocusEnabled || isDateMode) && deepgramKey.isNotEmpty()) {
            speakerLockManager = SpeakerLockManager(serviceScope) { audioPlayer?.isPlaying == true }
            deepgramClient = DeepgramLiveClient(deepgramCallback)
            deepgramClient?.connect(deepgramKey, currentWakeWord)
            Log.i("FloatingWidgetService", "Connecting to Deepgram with speaker locking...")
        } else {
            Log.i("FloatingWidgetService", "Deepgram connection skipped (Dating/Focus Mode OFF or Key is empty).")
        }
    }

    private fun checkAndCompleteConnection() {
        serviceScope.launch(Dispatchers.Main) {
            val appPrefs = AppPreferences.getInstance(this@FloatingWidgetService)
            val deepgramKey = appPrefs.deepgramApiKey
            val isDateMode = com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive
            val needDeepgram = (appPrefs.isSoloFocusEnabled || isDateMode) && deepgramKey.isNotEmpty()

            val geminiReady = isGeminiConnected
            val deepgramReady = !needDeepgram || isDeepgramConnected

            if (geminiReady && deepgramReady) {
                val currentState = SessionStateHolder.state.value
                if (currentState == SessionState.Disconnected || currentState == SessionState.Connecting || currentState == SessionState.Reconnecting) {
                    Log.i("FloatingWidgetService", "All APIs connected successfully. Transitioning to Standby.")
                    updateTileState()
                    
                    if (audioPlayer == null) {
                        audioPlayer = AudioPlayer(context = this@FloatingWidgetService).apply {
                            onPlaybackActive = {
                                lastActivityTime = System.currentTimeMillis()
                                speakerLockManager?.extendLock()
                            }
                        }
                    }

                    transitionToState(SessionState.Standby(currentWakeWord))
                    startActiveStateMonitor()
                    startStaticFrameStreaming()
                    startDeepgramKeepAlive()
                    startSensorIntegration()
                }
            }
        }
    }

    fun disconnect() {
        releaseWakeLock()
        isGeminiConnected = false
        isDeepgramConnected = false
        releaseAudioResources()
        abandonSessionAudioFocus()
        liveClient?.disconnect()
        liveClient = null
        deepgramClient?.disconnect()
        deepgramClient = null
        speakerLockManager?.releaseLock()
        speakerLockManager = null
        activeQueryBuffer.setLength(0)
        chatHistory.clear()
        pendingPromptRefreshJob?.cancel()
        pendingPromptRefreshJob = null
        deepgramKeepAliveJob?.cancel()
        deepgramKeepAliveJob = null

        stopSensorIntegration()
        // Reset Diarization-Gating variables
        isSpeakerVerifiedActive = false
        lastOwnerSpeechTime = 0L
        synchronized(dgAudioBufferQueue) {
            dgAudioBufferQueue.clear()
        }
        synchronized(amplitudeHistory) {
            amplitudeHistory.clear()
        }

        // Explicitly update state to Disconnected
        if (SessionStateHolder.state.value != SessionState.Disconnected) {
            SessionStateHolder.updateState(SessionState.Disconnected)
        }
    }

    fun isAudioPlaying(): Boolean {
        return audioPlayer?.isPlaying == true
    }

    private fun releaseAudioResources() {
        audioRecorder?.release()
        audioRecorder = null
        
        audioPlayer?.release()
        audioPlayer = null

        staticFrameJob?.cancel()
        staticFrameJob = null
        lastCapturedFrame = null

        try {
            wakeWordDetector.stopListening()
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Error stopping wake word detector in releaseAudioResources", e)
        }
    }


    private fun handleDisconnect(unexpected: Boolean = false) {
        SessionStateHolder.updateState(SessionState.Disconnected)
        
        disconnect()

        notificationManager.showOrUpdateNotification("Floating Widget Ready")
        widgetController.updateWidgetColor(false)
        updateTileState()
        wakeWordDetector.stopListening()
        if (unexpected) {
            playErrorChime()
        }
    }

    private fun playErrorChime() {
        val appPrefs = AppPreferences.getInstance(this)
        val isChimeEnabled = appPrefs.isChimeEnabled
        if (!isChimeEnabled) return

        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 50)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 300)
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Error playing error chime", e)
        }
    }
    private fun requestSessionAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        synchronized(sessionFocusLock) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (sessionAudioFocusRequest == null) {
                        sessionAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAcceptsDelayedFocusGain(false)
                            .setOnAudioFocusChangeListener { focusChange ->
                                handleAudioFocusChange(focusChange)
                            }
                            .build()
                    }
                    val res = manager.requestAudioFocus(sessionAudioFocusRequest!!)
                    return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                } else {
                    @Suppress("DEPRECATION")
                    val res = manager.requestAudioFocus(
                        { focusChange -> handleAudioFocusChange(focusChange) },
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                    return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                }
            } catch (e: Exception) {
                Log.e("FloatingWidgetService", "Error requesting session audio focus", e)
                return false
            }
        }
    }

    private fun abandonSessionAudioFocus() {
        val manager = audioManager ?: return
        synchronized(sessionFocusLock) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    sessionAudioFocusRequest?.let {
                        manager.abandonAudioFocusRequest(it)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    manager.abandonAudioFocus { }
                }
            } catch (e: Exception) {
                Log.e("FloatingWidgetService", "Error abandoning session audio focus", e)
            }
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        Log.i("FloatingWidgetService", "Audio focus change: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w("FloatingWidgetService", "Audio focus lost, halting audio playback")
                audioPlayer?.stop()
                val state = SessionStateHolder.state.value
                if (state is SessionState.Active) {
                    val appPrefs = AppPreferences.getInstance(this)
                    transitionToState(SessionState.Standby(appPrefs.wakeWord))
                }
            }
        }
    }

    private fun logAndNotify(message: String) {
        Log.i("FloatingWidgetService", message)
        SessionStateHolder.appendChatLog(message)
    }

    private fun startActiveStateMonitor() {
        idleMonitorJob?.cancel()
        lastActivityTime = System.currentTimeMillis()
        idleMonitorJob = serviceScope.launch(Dispatchers.IO) {
            val appPrefs = AppPreferences.getInstance(this@FloatingWidgetService)
            while (isSessionConnected) {
                delay(1000)

                val isDateMode = com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive
                if (isDateMode) {
                    val now = System.currentTimeMillis()
                    if (now - lastDatingActivityTime > 5 * 60 * 1000) {
                        Log.i("FloatingWidgetService", "Auto-Sleep Dating Mode due to 5 min silence")
                        serviceScope.launch(Dispatchers.Main) {
                            triggerDatingAutoSleep("โหมดช่วยเดตหยุดทำงานอัตโนมัติเนื่องจากไม่มีการคุยเกิน 5 นาที")
                        }
                    }
                    if (now - datingSessionStartTime > 60 * 60 * 1000) {
                        Log.i("FloatingWidgetService", "Auto-Sleep Dating Mode due to 60 min session elapsed")
                        serviceScope.launch(Dispatchers.Main) {
                            triggerDatingAutoSleep("หมดเวลาเซสชันช่วยเดตสูงสุด 60 นาทีแล้ว")
                        }
                    }
                }

                val idleDuration = System.currentTimeMillis() - lastActivityTime
                val state = SessionStateHolder.state.value
                
                val activeTimeout = appPrefs.activeSessionTimeoutMs
                if (state is SessionState.Active && activeTimeout > 0L && idleDuration > activeTimeout) {
                    Log.i("FloatingWidgetService", "$activeTimeout ms of silence in ACTIVE state. Reverting to STANDBY.")
                    transitionToState(SessionState.Standby(currentWakeWord))
                }
                
                val maxStandbyIdle = appPrefs.sessionDisconnectTimeoutMs
                if (state is SessionState.Standby && maxStandbyIdle > 0L && idleDuration > maxStandbyIdle) {
                    Log.i("FloatingWidgetService", "$maxStandbyIdle ms of total silence in STANDBY. Auto disconnecting.")
                    disconnect()
                    break
                }
            }
        }
    }

    private fun startStaticFrameStreaming() {
        staticFrameJob?.cancel()
        staticFrameJob = serviceScope.launch(Dispatchers.IO) {
            while (isSessionConnected) {
                val appPrefs = AppPreferences.getInstance(this@FloatingWidgetService)
                val delayTime = if (appPrefs.isDynamicFpsEnabled) {
                    val motion = SessionStateHolder.diagnostics.value.motionState
                    val loc = SessionStateHolder.diagnostics.value.locationState
                    when {
                        motion == "DRIVING" -> 20000L
                        motion == "STILL" && loc != "home" -> 6000L
                        else -> 12000L
                    }
                } else {
                    12000L
                }
                delay(delayTime)
                val frame = lastCapturedFrame
                val state = SessionStateHolder.state.value
                if (frame != null && !MainActivity.isCameraActive && state is SessionState.Active) {
                    Log.d("FloatingWidgetService", "Streaming last captured frame to Gemini Live in background (delay = ${delayTime}ms)...")
                    liveClient?.sendMediaChunk(frame, "image/jpeg")
                }
            }
        }
    }

    private fun startDeepgramKeepAlive() {
        deepgramKeepAliveJob?.cancel()
        deepgramKeepAliveJob = serviceScope.launch(Dispatchers.IO) {
            while (isSessionConnected) {
                delay(5000)
                if (SessionStateHolder.state.value is SessionState.Standby) {
                    deepgramClient?.sendKeepAlive()
                }
            }
        }
    }

    private fun updateTileState() {
        try {
            android.service.quicksettings.TileService.requestListeningState(
                this,
                ComponentName(this, GeminiTileService::class.java)
            )
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Error updating tile state", e)
        }
    }

    fun updateWakeWord(newWord: String) {
        val validatedWord = if (newWord.isNullOrBlank()) "กอหญ้า" else newWord
        currentWakeWord = validatedWord
        wakeWordDetector.updateWakeWord(validatedWord)
        Log.i("FloatingWidgetService", "Wake word updated to: $validatedWord")
    }

    private fun playChime(isWakeUp: Boolean) {
        val appPrefs = AppPreferences.getInstance(this)
        val isChimeEnabled = appPrefs.isChimeEnabled
        if (!isChimeEnabled) return

        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 30)
            }
            if (isWakeUp) {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            } else {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            }
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Error playing chime", e)
        }
    }

    private fun flushSpeakerAudioBuffer() {
        val client = liveClient ?: return
        if (!isSessionConnected) return
        synchronized(dgAudioBufferQueue) {
            for (chunk in dgAudioBufferQueue) {
                if (attentionManager.isAttentionActive.value) {
                    val base64 = Base64.encodeToString(chunk, Base64.DEFAULT or Base64.NO_WRAP)
                    client.sendMediaChunk(base64, "audio/pcm;rate=16000")
                }
            }
            dgAudioBufferQueue.clear()
        }
        Log.d("FloatingWidgetService", "Flushed audio buffer synchronously.")
    }

    private fun getOrCreateAudioRecorder(): AudioRecorder {
        var recorder = audioRecorder
        if (recorder == null) {
            val appPrefs = AppPreferences.getInstance(this)
            recorder = AudioRecorder(this) { bytes, amp, zcr ->
                val currentTime = System.currentTimeMillis()

                // Stream audio to Deepgram for continuous speaker identification
                deepgramClient?.sendAudio(bytes, bytes.size)

                if (appPrefs.isSoloFocusEnabled) {
                    // Dynamic sliding-minimum noise floor estimation to automatically adapt to external music/ambient noise
                    synchronized(amplitudeHistory) {
                        amplitudeHistory.add(amp)
                        if (amplitudeHistory.size > amplitudeHistorySize) {
                            amplitudeHistory.removeFirst()
                        }
                        val noiseFloor = amplitudeHistory.minOrNull() ?: 500
                        // Threshold is 1.4x the sliding-minimum noise floor to distinguish speech from loud constant noise (e.g. music)
                        dynamicThreshold = (noiseFloor * 1.4f).toInt().coerceIn(600, 5000)
                    }

                    // ZCR speech filter: voice generally ranges between 0.015 and 0.55.
                    // If ZCR is extremely low (<0.015) or high (>0.55), it is wind rumble or hiss noise.
                    val isSpeechLike = zcr in 0.015f..0.55f
                    val isVoiceGateOpen = (amp > dynamicThreshold && isSpeechLike)

                    // (Removed premature activity update to prevent noise processing)
                    if (isSpeakerVerifiedActive && deepgramClient != null) {
                        // Check if owner speech lock timer has expired (increased to 5.0 seconds of silence to prevent clipping)
                        if (currentTime - lastOwnerSpeechTime > 5000L) {
                            isSpeakerVerifiedActive = false
                            Log.d("FloatingWidgetService", "Speaker lock window closed due to silence.")
                        }
                    }

                    val isVerified = if (deepgramClient != null) isSpeakerVerifiedActive else true

                    if (isVerified && isVoiceGateOpen) {
                        // If the voice gate was previously closed, flush the pre-roll buffer to prevent front-clipping
                        if (!voiceGateOpen) {
                            flushSpeakerAudioBuffer()
                        }
                        voiceGateOpen = true
                        lastVoiceTime = currentTime
                        lastActivityTime = currentTime
                        perceptionEngine.emitEvent(com.example.geminimultimodalliveapi.architecture.PerceptionEvent.SpeechDetected("owner", 0.95f))

                        if (attentionManager.isAttentionActive.value) {
                            val base64 = Base64.encodeToString(bytes, Base64.DEFAULT or Base64.NO_WRAP)
                            liveClient?.sendMediaChunk(base64, "audio/pcm;rate=16000")
                        }
                        synchronized(dgAudioBufferQueue) {
                            dgAudioBufferQueue.clear()
                        }
                    } else if (currentTime - lastVoiceTime < 1500L) { // [FIX] Adjusted Hangover to 1.5s for balance
                        voiceGateOpen = true
                        if (isVerified) {
                            if (attentionManager.isAttentionActive.value) {
                                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT or Base64.NO_WRAP)
                                liveClient?.sendMediaChunk(base64, "audio/pcm;rate=16000")
                            }
                            synchronized(dgAudioBufferQueue) {
                                dgAudioBufferQueue.clear()
                            }
                        } else {
                            synchronized(dgAudioBufferQueue) {
                                dgAudioBufferQueue.add(bytes.clone())
                                if (dgAudioBufferQueue.size > maxDgBufferChunks) {
                                    dgAudioBufferQueue.removeFirst()
                                }
                            }
                        }
                    } else {
                        voiceGateOpen = false
                        synchronized(dgAudioBufferQueue) {
                            dgAudioBufferQueue.add(bytes.clone())
                            if (dgAudioBufferQueue.size > maxDgBufferChunks) {
                                dgAudioBufferQueue.removeFirst()
                            }
                        }
                    }
                } else {
                    // Normal Mode (Focus Mode OFF)
                    if (amp > 500) {
                        lastActivityTime = currentTime
                        perceptionEngine.emitEvent(com.example.geminimultimodalliveapi.architecture.PerceptionEvent.SpeechDetected("owner", 0.95f))
                    }

                    if (isSpeakerVerifiedActive && deepgramClient != null) {
                        if (currentTime - lastOwnerSpeechTime > 3000L) {
                            isSpeakerVerifiedActive = false
                            Log.d("FloatingWidgetService", "Speaker lock window closed due to silence (Normal Mode).")
                        }
                    }

                    // If Deepgram is connected, gate by speaker lock; otherwise default to always stream
                    val shouldStream = if (deepgramClient != null) isSpeakerVerifiedActive else true

                    if (shouldStream) {
                        if (attentionManager.isAttentionActive.value) {
                            val base64 = Base64.encodeToString(bytes, Base64.DEFAULT or Base64.NO_WRAP)
                            liveClient?.sendMediaChunk(base64, "audio/pcm;rate=16000")
                        }
                        synchronized(dgAudioBufferQueue) {
                            dgAudioBufferQueue.clear()
                        }
                    } else {
                        synchronized(dgAudioBufferQueue) {
                            dgAudioBufferQueue.add(bytes.clone())
                            if (dgAudioBufferQueue.size > maxDgBufferChunks) {
                                dgAudioBufferQueue.removeFirst()
                            }
                        }
                    }
                }
            }
            audioRecorder = recorder
        }
        return recorder
    }

    // Fully public transition state method used safely without reflection
    fun transitionToState(state: SessionState, command: String = "") {
        if (!isSessionConnected) return
        
        val currentState = SessionStateHolder.state.value
        if (currentState::class == state::class) {
            var isRedundant = false
            if (currentState is SessionState.Standby && state is SessionState.Standby) {
                if (currentState.wakeWord == state.wakeWord) isRedundant = true
            } else if (currentState is SessionState.Active && state is SessionState.Active) {
                if (currentState.isRecording == state.isRecording) isRedundant = true
            } else if (currentState is SessionState.Error && state is SessionState.Error) {
                if (currentState.message == state.message) isRedundant = true
            } else if (currentState == state) {
                isRedundant = true
            }
            if (isRedundant) {
                Log.d("FloatingWidgetService", "Redundant state transition to $state ignored.")
                return
            }
        }

        SessionStateHolder.updateState(state)

        serviceScope.launch(Dispatchers.Main) {
            val appPrefs = AppPreferences.getInstance(this@FloatingWidgetService)
            if (state is SessionState.Active) {
                requestSessionAudioFocus()
                wakeWordDetector.stopListening()
                widgetController.updateWidgetColor(true)
                playChime(true)
                
                // Force Attention Manager to ACTIVE_SESSION to enable audio streaming immediately
                attentionManager.forceState(com.example.geminimultimodalliveapi.architecture.AttentionState.ACTIVE_SESSION)
                
                delay(100)
                
                // Reset Voice Gate and Diarization Gate variables for this active turn
                voiceGateOpen = false
                lastVoiceTime = 0L
                isSpeakerVerifiedActive = true // Open initially to allow first breath
                lastOwnerSpeechTime = System.currentTimeMillis()
                synchronized(dgAudioBufferQueue) {
                    dgAudioBufferQueue.clear()
                }

                getOrCreateAudioRecorder().start(serviceScope)
                
                if (command.isNotEmpty()) {
                    Log.i("FloatingWidgetService", "Sending single-breath command: '$command'")
                    logAndNotify("USER: $command")
                    liveClient?.sendTextMessage(command)
                }

                lastActivityTime = System.currentTimeMillis()
            } else if (state is SessionState.Standby) {
                abandonSessionAudioFocus()
                // Force Attention Manager to IDLE when returning to standby
                attentionManager.forceState(com.example.geminimultimodalliveapi.architecture.AttentionState.IDLE)
                
                audioPlayer?.stop()
                widgetController.updateWidgetColor(false)
                playChime(false)

                audioRecorder?.stop()
                wakeWordDetector.startListening()
                
                // Reset Voice Gate and Diarization Gate variables
                voiceGateOpen = false
                lastVoiceTime = 0L
                isSpeakerVerifiedActive = false
                lastOwnerSpeechTime = 0L
                synchronized(dgAudioBufferQueue) {
                    dgAudioBufferQueue.clear()
                }
                synchronized(amplitudeHistory) {
                    amplitudeHistory.clear()
                }
                
                lastActivityTime = System.currentTimeMillis()
            }
        }
    }

    private fun getInitialCombinedPrompt(): String {
        val baseSystemInstructionText = "คุณคือผู้ช่วย AI แสนเป็นมิตรสำหรับช่วยเหลือขณะขับขี่มอเตอร์ไซค์ ชื่อของคุณคือ \"$currentWakeWord\" " +
                "กรุณาพูดและตอบโต้กับผู้ใช้เป็นภาษาไทยเสมออย่างเป็นธรรมชาติสั้นกระชับ " +
                "กฎเหล็กเรื่องเวลาและสภาพอากาศ: ห้ามคุณคาดเดาหรือแต่งเวลาปัจจุบันหรือสภาพอากาศด้วยตนเองเด็ดขาด! " +
                "เมื่อผู้ใช้ถามเวลา ตอนนี้กี่โมง หรือถามเกี่ยวกับเวลาปัจจุบัน คุณต้องเรียกใช้งานเครื่องมือ get_current_time ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้ถามถึงสภาพอากาศ อุณหภูมิ ฝนตก หรือคำถามเกี่ยวกับลมฟ้าอากาศในปัจจุบัน คุณต้องเรียกใช้งานเครื่องมือ get_current_weather ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้ต้องการค้นหาสถานที่ใกล้เคียง เช่น ปั๊มน้ำมัน ร้านซ่อมรถ โรงพยาบาล ร้านอาหาร ร้านกาแฟ หรือตู้เอทีเอ็ม คุณต้องเรียกใช้งานเครื่องมือ find_nearby_places ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้ขอให้นำทาง บอกทาง หรือเปิดแผนที่นำทางไปยังสถานที่ใดๆ คุณต้องเรียกใช้งานเครื่องมือ launch_navigation ทันทีทุกครั้ง " +
                "เมื่อผู้ใช้บอกเรื่องราวส่วนตัว ข้อมูลสำคัญ หรือสั่งให้คุณจดจำเรื่องราวใดๆ (เช่น เรื่องครอบครัว สิ่งที่ชอบ สิ่งที่ต้องทำ หรือเรื่องที่พูดคุยกัน) คุณต้องเรียกใช้งานเครื่องมือ remember_personal_fact เพื่อบันทึกความจำระยะยาวทันที " +
                "เมื่อผู้ใช้สั่งให้ลืม ลบความจำ หรือบอกว่าเรื่องนั้นไม่ถูกต้องแล้ว คุณต้องเรียกใช้งานเครื่องมือ forget_personal_fact เพื่อลบความจำนั้นออกจากระบบทันที " +
                "เมื่อผู้ใช้สั่งจูนการตั้งค่าพฤติกรรม บันทึกกฎความประพฤติ หรือสั่งแก้ไขกฎพฤติกรรมในสถานการณ์ต่างๆ คุณต้องเรียกใช้งานเครื่องมือ save_system_rule ทันทีทุกครั้ง " +
                "คุณมีเครื่องมือช่วยเหลือในฐานข้อมูลความจำเครื่อง (SQLite) " +
                "เมื่อผู้ใช้สั่งให้จำ หรือผู้ใช้ถามถึงข้อมูล เช่น ทะเบียนรถ วันหมดอายุภาษี/ป้ายวงกลม ประวัติเช็คระยะ หรือที่จอดรถ " +
                "กรุณาเรียกใช้ฟังก์ชันบันทึกหรือค้นหาข้อมูลย้อนหลังให้สอดคล้องกันโดยอัตโนมัติ"

        val memoryContext = memoryManager.getFormattedContextPrompt()
        val contextWithMemory = if (memoryContext.isNotEmpty()) "$baseSystemInstructionText\n\n$memoryContext" else baseSystemInstructionText
        return contextManager.getCombinedSystemPrompt(contextWithMemory)
    }

    fun refreshDynamicPrompt() {
        val client = liveClient ?: return
        if (!isSessionConnected) return

        val currentTime = System.currentTimeMillis()
        val timeSinceLast = currentTime - lastPromptRefreshTime

        if (timeSinceLast >= promptRefreshThrottleMs) {
            pendingPromptRefreshJob?.cancel()
            lastPromptRefreshTime = currentTime
            val combinedPrompt = getInitialCombinedPrompt()
            client.updateMemoryContext(combinedPrompt)
            Log.d("FloatingWidgetService", "Dynamic prompt refreshed immediately.")
        } else {
            // Schedule a delayed update if one isn't already scheduled
            if (pendingPromptRefreshJob?.isActive != true) {
                pendingPromptRefreshJob = serviceScope.launch(Dispatchers.Main) {
                    val delayTime = promptRefreshThrottleMs - timeSinceLast
                    delay(delayTime)
                    lastPromptRefreshTime = System.currentTimeMillis()
                    val combinedPrompt = getInitialCombinedPrompt()
                    liveClient?.updateMemoryContext(combinedPrompt)
                    Log.d("FloatingWidgetService", "Dynamic prompt refreshed after throttle delay.")
                }
            }
        }
    }

    private fun startSensorIntegration() {
        val appPrefs = AppPreferences.getInstance(this)
        if (!appPrefs.isSensorIntegrationEnabled) {
            Log.i("SensorIntegration", "Sensor integration is disabled in settings, skipping initialization.")
            return
        }

        Log.i("SensorIntegration", "Initializing Sensor Integration...")
        
        // 1. Accelerometer setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            accelerometerListener = object : SensorEventListener {
                private var lastAccelerationMag = -1.0f
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val currentMag = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    if (lastAccelerationMag > 0) {
                        val deltaMag = Math.abs(currentMag - lastAccelerationMag)
                        if (deltaMag > 9.0f) {
                            triggerSuddenDeceleration(deltaMag)
                        }
                    }
                    lastAccelerationMag = currentMag
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager?.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i("SensorIntegration", "Accelerometer listener registered successfully")
        } else {
            Log.w("SensorIntegration", "Accelerometer sensor not available")
        }

        // 2. FusedLocationProviderClient setup
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
            locationCallback = object : LocationCallback() {
                private var lastLocation: android.location.Location? = null
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    
                    // Calculate speed
                    val speed = if (location.hasSpeed()) {
                        location.speed
                    } else {
                        val lastLoc = lastLocation
                        if (lastLoc != null) {
                            val dist = location.distanceTo(lastLoc)
                            val timeSec = (location.time - lastLoc.time) / 1000.0
                            if (timeSec > 0) (dist / timeSec).toFloat() else 0f
                        } else 0f
                    }
                    lastLocation = location
                    val speedKmH = speed * 3.6f
                    
                    // Determine motionType
                    val motionType = when {
                        speedKmH > 15.0f -> com.example.geminimultimodalliveapi.architecture.MotionType.DRIVING
                        speedKmH > 2.5f -> com.example.geminimultimodalliveapi.architecture.MotionType.WALKING
                        else -> com.example.geminimultimodalliveapi.architecture.MotionType.STILL
                    }
                    
                    // Update perception engine with debounce
                    perceptionEngine.updateMotionStateWithDebounce(motionType)
                    
                    // Geofence check
                    if (homeLatitude == null || homeLongitude == null) {
                        homeLatitude = location.latitude
                        homeLongitude = location.longitude
                        Log.i("SensorIntegration", "Home coordinates set to current location: lat=${location.latitude}, lon=${location.longitude}")
                        writeIncidentLog("HOME_COORDINATES_SET", "กำหนดพิกัดบ้านที่ lat=${location.latitude}, lon=${location.longitude}")
                    }
                    
                    val distToHome = FloatArray(1)
                    android.location.Location.distanceBetween(
                        location.latitude, location.longitude,
                        homeLatitude!!, homeLongitude!!,
                        distToHome
                    )
                    
                    val placeType = if (distToHome[0] <= GEOFENCE_RADIUS_METERS) "home" else "outside"
                    
                    val currentDiagnostics = SessionStateHolder.diagnostics.value
                    if (currentDiagnostics.locationState != placeType) {
                        Log.i("SensorIntegration", "Geofence status changed: $placeType (dist: ${distToHome[0]}m)")
                        perceptionEngine.emitEvent(com.example.geminimultimodalliveapi.architecture.PerceptionEvent.LocationChanged(placeType, placeType))
                        writeIncidentLog("GEOFENCE_TRANSITION", "สลับ Geofence: $placeType (ระยะห่างบ้าน ${distToHome[0]} เมตร)")
                    }
                }
            }
            try {
                fusedLocationClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    android.os.Looper.getMainLooper()
                )
                Log.i("SensorIntegration", "FusedLocationProviderClient updates requested successfully")
            } catch (e: SecurityException) {
                Log.e("SensorIntegration", "SecurityException requesting location updates", e)
            }
        } else {
            Log.w("SensorIntegration", "Location permission missing, skipping FusedLocationProviderClient updates")
        }
    }

    private fun stopSensorIntegration() {
        Log.i("SensorIntegration", "Stopping Sensor Integration...")
        try {
            sensorManager?.let { sm ->
                accelerometerListener?.let { listener ->
                    sm.unregisterListener(listener)
                }
            }
        } catch (e: Exception) {
            Log.e("SensorIntegration", "Error unregistering accelerometer listener", e)
        }
        sensorManager = null
        accelerometer = null
        accelerometerListener = null

        try {
            fusedLocationClient?.let { client ->
                locationCallback?.let { callback ->
                    client.removeLocationUpdates(callback)
                }
            }
        } catch (e: Exception) {
            Log.e("SensorIntegration", "Error removing location updates", e)
        }
        fusedLocationClient = null
        locationCallback = null
        homeLatitude = null
        homeLongitude = null
    }

    private fun triggerSuddenDeceleration(deltaMag: Float) {
        val now = System.currentTimeMillis()
        if (now - lastDecelerationAlertTime < ALERT_THROTTLE_MS) return
        lastDecelerationAlertTime = now
        
        val appPrefs = AppPreferences.getInstance(this)
        if (!appPrefs.isProactiveEventsEnabled) return

        val message = "SYSTEM: [แจ้งเตือนเซ็นเซอร์] ตรวจพบการเบรกกะทันหัน (ความเร็วลดลงฉับพลัน deltaMag: ${String.format("%.2f", deltaMag)} m/s²)"
        Log.w("SensorIntegration", "Sudden deceleration detected: $message")
        
        logAndNotify(message)
        liveClient?.sendTextMessage(message)
        writeIncidentLog("SUDDEN_DECELERATION", "ตรวจพบการเบรกกะทันหันอย่างรุนแรง deltaMag = $deltaMag")
    }

    private fun writeIncidentLog(eventType: String, details: String) {
        serviceScope.launch(Dispatchers.IO) {
        try {
            val logDir = File(filesDir, "situational_logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val timestamp = System.currentTimeMillis()
            val logFile = File(logDir, "telemetry_${eventType}_$timestamp.json")
            val json = """
                {
                  "eventId": "${java.util.UUID.randomUUID()}",
                  "timestamp": $timestamp,
                  "eventType": "$eventType",
                  "details": "$details",
                  "motionState": "${SessionStateHolder.diagnostics.value.motionState}",
                  "locationState": "${SessionStateHolder.diagnostics.value.locationState}"
                }
            """.trimIndent()
            logFile.writeText(json)
            val logMsg = "[Telemetry] Logged $eventType to files/situational_logs/${logFile.name}"
            Log.i("SensorIntegration", logMsg)
            SessionStateHolder.appendChatLog(logMsg)
        } catch (e: Exception) {
            Log.e("SensorIntegration", "Failed to write incident log", e)
        }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
        Log.i("FloatingWidget", "Service destroyed")
        stopSensorIntegration()
        // Explicitly unregisterListener for sensors as safety net
        accelerometerListener?.let { sensorManager?.unregisterListener(it) }
        try {
            phoneStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Error unregistering PhoneStateReceiver", e)
        }
        try {
            perceptionEngine.destroy()
        } catch (e: Exception) {}
        wakeWordDetector.destroy()
        widgetController.hide()
        
        toneGenerator?.release()
        toneGenerator = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Error shutting down TTS", e)
        }
        tts = null
        isTtsReady = false

        handleDisconnect()
        serviceScope.cancel()
        weakInstance = null
    }

    // --- Dating Assistant Mode Logic & Helpers ---

    private fun onSpeechReceivedDating(speaker: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        lastDatingActivityTime = System.currentTimeMillis()
        synchronized(datingTranscriptHistory) {
            datingTranscriptHistory.add(Pair(speaker, trimmed))
        }
        Log.i("DatingMode", "Added to history -> $speaker: $trimmed")

        // Auto-Sleep check for 60 min session timeout
        val elapsedSession = System.currentTimeMillis() - datingSessionStartTime
        if (elapsedSession > 60 * 60 * 1000) {
            Log.i("DatingMode", "60 minutes dating session timeout. Sleeping.")
            triggerDatingAutoSleep("เซสชันโหมดช่วยเดตหมดเวลา 60 นาทีแล้ว")
            return
        }

        // Trigger analysis
        val hasKeyword = checkDatingKeywords(trimmed)
        if (hasKeyword) {
            Log.i("DatingMode", "Dating keyword matched! Immediate analysis triggered.")
            triggerDatingAnalysis()
        } else {
            resetDatingSilenceTimer()
        }
    }

    private fun resetDatingSilenceTimer() {
        datingSilenceTimerJob?.cancel()
        datingSilenceTimerJob = serviceScope.launch {
            delay(3000L)
            Log.i("DatingMode", "3 seconds silence detected. Triggering analysis.")
            triggerDatingAnalysis()
        }
    }

    private fun triggerDatingAnalysis() {
        if (isAnalysisInProgress) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastDatingAnalysisTime
        if (elapsed < DATING_ANALYSIS_MIN_INTERVAL) {
            val delayTime = DATING_ANALYSIS_MIN_INTERVAL - elapsed
            datingSilenceTimerJob?.cancel()
            datingSilenceTimerJob = serviceScope.launch {
                delay(delayTime)
                triggerDatingAnalysis()
            }
            return
        }
        val isEmpty = synchronized(datingTranscriptHistory) {
            datingTranscriptHistory.isEmpty()
        }
        if (isEmpty) return
        performDatingAnalysis()
    }

    private fun performDatingAnalysis() {
        if (!isNetworkAvailable()) {
            Log.w("DatingMode", "Network unavailable. Caching transcript for later.")
            return
        }

        if (datingOrchestrator == null) {
            Log.w("DatingMode", "Orchestrator not initialized (API key missing at startup?)")
            val appPrefs = AppPreferences.getInstance(this)
            val apiKey = appPrefs.apiKey
            if (apiKey.isEmpty()) {
                isAnalysisInProgress = false
                return
            }
            val skillMgr = com.example.geminimultimodalliveapi.data.DatingSkillManager(this)
            val routerAgent = com.example.geminimultimodalliveapi.agent.DatingRouterAgent(apiKey, skillMgr)
            val skillAgent = com.example.geminimultimodalliveapi.agent.DatingSkillAgentImpl(apiKey)
            val docSelector = com.example.geminimultimodalliveapi.agent.DocumentSelector(this)
            datingOrchestrator = com.example.geminimultimodalliveapi.agent.DatingAnalysisOrchestrator(
                apiKey = apiKey,
                skillManager = skillMgr,
                routerAgent = routerAgent,
                primaryAgent = skillAgent,
                documentSelector = docSelector
            )
        }

        isAnalysisInProgress = true
        lastDatingAnalysisTime = System.currentTimeMillis()

        serviceScope.launch(Dispatchers.IO) {
            val transcriptHistory = synchronized(datingTranscriptHistory) {
                datingTranscriptHistory.toList()
            }
            val currentDiagnostics = SessionStateHolder.diagnostics.value
            val currentTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
            val noiseDb = 20 * Math.log10(ambientNoiseLevel.toDouble().coerceAtLeast(1.0))

            val sensorContext = com.example.geminimultimodalliveapi.agent.SensorContext(
                location = currentDiagnostics.locationState,
                motion = currentDiagnostics.motionState,
                noiseDb = noiseDb,
                currentTime = currentTime
            )

            val overrideSkillId = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId

            val result = datingOrchestrator!!.analyze(
                transcriptHistory = transcriptHistory,
                sensorContext = sensorContext,
                skillIdOverride = overrideSkillId.ifEmpty { null }
            )

            isAnalysisInProgress = false

            val insight = result.insight.copy(
                activeAgentId = result.selectedSkillId,
                activeAgentName = result.agentsUsed.joinToString(", "),
                routerReasoning = result.reasoning
            )

            SessionStateHolder.updateDateInsights { insight }

            val profileName = SessionStateHolder.activeProfileName
            if (profileName.isNotEmpty()) {
                val dbHelper = com.example.geminimultimodalliveapi.data.DateProfileDbHelper(this@FloatingWidgetService)
                dbHelper.saveProfile(profileName, insight.likes, insight.dislikes, insight.personality)
            }

            if (isBluetoothConnected() && isTtsReady && insight.tip.isNotEmpty()) {
                speakTip(insight.tip)
            }

            if (insight.hasRedFlag) {
                val redFlagPattern = longArrayOf(0, 300, 150, 300, 150, 300)
                triggerHapticAlert(redFlagPattern)
            }

            Log.i("DatingMode", "Orchestrator result: skill=${result.selectedSkillId}, confidence=${result.confidence}, agents=${result.agentsUsed}")
        }
    }

    private fun isBluetoothConnected(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.isBluetoothA2dpOn || am.isBluetoothScoOn
    }

    private fun speakTip(tip: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(tip, TextToSpeech.QUEUE_FLUSH, null, "DatingTip")
        }
    }

    private fun triggerHapticAlert(pattern: LongArray) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    private fun triggerDatingAutoSleep(reason: String) {
        com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive = false
        com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDateInsights {
            it.copy(tip = "โหมดผู้ช่วยเดตเข้าสู่การหลับ (Pause) - $reason")
        }

        val autoSleepPattern = longArrayOf(0, 100, 100, 100, 100, 500)
        triggerHapticAlert(autoSleepPattern)

        Log.i("FloatingWidgetService", "Dating Mode Auto-Sleep: $reason")
        transitionToState(SessionState.Standby(currentWakeWord))
        
        if (isBluetoothConnected() && isTtsReady) {
            speakTip(reason)
        }
    }

    private fun checkDatingKeywords(text: String): Boolean {
        val normalized = text.lowercase(Locale.getDefault())
        val keywords = arrayOf("ชอบ", "ไม่ชอบ", "เกลียด", "แพ้", "ประทับใจ", "กลัว", "สนใจ", "อร่อย", "หอม", "เหม็น", "รัก", "บ้า", "แย่", "ดี")
        for (kw in keywords) {
            if (normalized.contains(kw)) {
                return true
            }
        }
        return false
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return false
    }

    private fun showRadialSkillPicker() {
        if (radialPickerView != null) return
        
        // 1. Transition/Forced toggle to Dating Mode
        com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive = true
        // Turn off solo focus if enabled
        val appPrefs = AppPreferences.getInstance(this)
        appPrefs.isSoloFocusEnabled = false
        
        // If not connected, let's start connection automatically
        val isConnected = SessionStateHolder.state.value != SessionState.Disconnected
        if (!isConnected) {
            val key = appPrefs.apiKey
            if (key.isNotEmpty()) {
                connect(key)
            }
        }

        // Get list of skills
        val skillMgr = com.example.geminimultimodalliveapi.data.DatingSkillManager(this)
        val skills = skillMgr.getAllSkills()

        val picker = com.example.geminimultimodalliveapi.ui.RadialSkillPickerView(this).apply {
            setSkills(skills)
            listener = object : com.example.geminimultimodalliveapi.ui.RadialSkillPickerView.OnSkillSelectedListener {
                override fun onSkillSelected(skill: com.example.geminimultimodalliveapi.data.DatingSkill?) {
                    if (skill != null) {
                        // Apply the dating skill
                        com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId = skill.id
                        val prefs = AppPreferences.getInstance(this@FloatingWidgetService)
                        prefs.lastDatingSkillId = skill.id
                        
                        Toast.makeText(
                            this@FloatingWidgetService,
                            "เปิดใช้งานโหมดผู้ช่วยเดต: ${skill.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Switch active state if session is connected
                        if (SessionStateHolder.state.value is SessionState.Standby) {
                            transitionToState(SessionState.Active(true))
                        }
                    }
                    dismissRadialPicker()
                }

                override fun onDismiss() {
                    dismissRadialPicker()
                }
            }
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(picker, params)
            radialPickerView = picker
            Log.i("FloatingWidgetService", "Radial skill picker overlay added to window")
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Failed to add radial skill picker view", e)
        }
    }

    private fun dismissRadialPicker() {
        val picker = radialPickerView ?: return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(picker)
            radialPickerView = null
            Log.i("FloatingWidgetService", "Radial skill picker overlay removed from window")
        } catch (e: Exception) {
            Log.e("FloatingWidgetService", "Failed to remove radial skill picker view", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.i("FloatingWidgetService", "onTrimMemory level: $level")
        if (::dynamicRulesManager.isInitialized) {
            dynamicRulesManager.clearCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w("FloatingWidgetService", "onLowMemory called")
        if (::dynamicRulesManager.isInitialized) {
            dynamicRulesManager.clearCache()
        }
    }
}
