package com.example.geminimultimodalliveapi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.utils.PermissionHelper
import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import com.example.geminimultimodalliveapi.error.AppError
import com.example.geminimultimodalliveapi.network.ApiKeyValidator
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import com.example.geminimultimodalliveapi.utils.dpToPx
import com.example.geminimultimodalliveapi.utils.GoogleSignInHelper
import com.example.geminimultimodalliveapi.utils.isNotificationServiceEnabled
import com.example.geminimultimodalliveapi.utils.showReAuthDialog


class SettingsActivity : AppCompatActivity() {

    private var deepgramApiKeyWatcher: android.text.TextWatcher? = null
    private var wakeWordWatcher: android.text.TextWatcher? = null

    private lateinit var appPrefs: AppPreferences
    private lateinit var btnBack: ImageButton
    private lateinit var apiKeyInput: EditText
    private lateinit var connectButton: Button
    private lateinit var deepgramApiKeyInput: EditText
    private lateinit var btnTestDeepgram: Button
    private lateinit var voiceSpinner: Spinner
    private lateinit var wakeWordInput: EditText
    private lateinit var floatingWidgetSwitch: SwitchCompat
    private lateinit var chimeSwitch: SwitchCompat
    private lateinit var reminderSpinner: Spinner
    private lateinit var btnManageMemory: Button
    private lateinit var spinnerActiveTimeout: Spinner
    private lateinit var spinnerDisconnectTimeout: Spinner
    private lateinit var switchTranslateMode: SwitchCompat
    private lateinit var spinnerTargetLanguage: Spinner
    private lateinit var spinnerCoachingIntensity: Spinner
    private lateinit var spinnerContextProfile: Spinner
    private lateinit var switchMicAgc: SwitchCompat
    private lateinit var spinnerMicGain: Spinner
    private lateinit var layoutMicGain: android.widget.LinearLayout
    private lateinit var btnUploadPsychology: Button
    private lateinit var btnViewConvertedPsychology: Button
    private lateinit var menuItemPsychology: View
    private lateinit var cardPsychology: View
    private lateinit var spinnerSkillSettings: Spinner
    private lateinit var btnEditSkillSettings: android.widget.ImageButton
    private lateinit var datingSkillManager: com.example.geminimultimodalliveapi.data.DatingSkillManager

    private lateinit var switchSensorIntegration: SwitchCompat
    private lateinit var switchProactiveEvents: SwitchCompat
    private lateinit var switchDynamicFps: SwitchCompat
    private lateinit var switchNotificationPercept: SwitchCompat

    private lateinit var txtSettingsTitle: TextView
    private lateinit var layoutCategoryMenu: android.widget.LinearLayout
    private lateinit var layoutDetailsContainer: android.widget.LinearLayout

    // Menu items
    private lateinit var menuItemAi: View
    private lateinit var menuItemDeepgram: View
    private lateinit var menuItemMic: View
    private lateinit var menuItemWidget: View
    private lateinit var menuItemDocuments: View
    private lateinit var menuItemCalendar: View
    private lateinit var menuItemMemory: View

    // Detail cards
    private lateinit var cardGemini: View
    private lateinit var cardDeepgram: View
    private lateinit var cardMicSettings: View
    private lateinit var cardWidget: View
    private lateinit var documentCard: View
    private lateinit var calendarCard: View
    private lateinit var cardMemory: View

    private val VOICES = arrayOf(
        "Aoede (Female - Clear & Professional)",
        "Kore (Female - Warm & Smooth)",
        "Puck (Male - Friendly & Playful)",
        "Charon (Male - Deep & Calm)",
        "Fenrir (Male - Sharp & Authority)"
    )
    private val VOICE_IDS = arrayOf("Aoede", "Kore", "Puck", "Charon", "Fenrir")

    private val OVERLAY_PERMISSION_REQUEST_CODE = 300
    private val AUDIO_REQUEST_CODE = 200
    private var isConnected = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var flowCollectJob: Job? = null

    // Managers
    lateinit var calendarManager: com.example.geminimultimodalliveapi.calendar.CalendarManager
    lateinit var documentManager: com.example.geminimultimodalliveapi.document.DocumentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutCategoryMenu.visibility == View.GONE) {
                    handleBackPress()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        appPrefs = AppPreferences.getInstance(this)

        btnBack = findViewById(R.id.btnBack)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        connectButton = findViewById(R.id.connectButton)
        deepgramApiKeyInput = findViewById(R.id.deepgramApiKeyInput)
        btnTestDeepgram = findViewById(R.id.btnTestDeepgram)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        wakeWordInput = findViewById(R.id.wakeWordInput)
        switchTranslateMode = findViewById(R.id.switchTranslateMode)
        spinnerTargetLanguage = findViewById(R.id.spinnerTargetLanguage)
        spinnerCoachingIntensity = findViewById(R.id.spinnerCoachingIntensity)
        spinnerContextProfile = findViewById(R.id.spinnerContextProfile)
        floatingWidgetSwitch = findViewById(R.id.floatingWidgetSwitch)
        chimeSwitch = findViewById(R.id.chimeSwitch)
        reminderSpinner = findViewById(R.id.reminderSpinner)
        btnManageMemory = findViewById(R.id.btnManageMemory)
        spinnerActiveTimeout = findViewById(R.id.spinnerActiveTimeout)
        spinnerDisconnectTimeout = findViewById(R.id.spinnerDisconnectTimeout)
        switchMicAgc = findViewById(R.id.switchMicAgc)
        spinnerMicGain = findViewById(R.id.spinnerMicGain)
        layoutMicGain = findViewById(R.id.layoutMicGain)

        switchSensorIntegration = findViewById(R.id.switchSensorIntegration)
        switchProactiveEvents = findViewById(R.id.switchProactiveEvents)
        switchDynamicFps = findViewById(R.id.switchDynamicFps)
        switchNotificationPercept = findViewById(R.id.switchNotificationPercept)

        txtSettingsTitle = findViewById(R.id.txtSettingsTitle)
        layoutCategoryMenu = findViewById(R.id.layoutCategoryMenu)

        if (savedInstanceState != null) {
            val menuVis = savedInstanceState.getInt("menu_visibility", android.view.View.VISIBLE)
            layoutCategoryMenu.visibility = menuVis
            txtSettingsTitle.text = savedInstanceState.getCharSequence("title_text", "การตั้งค่า")
        }
        layoutDetailsContainer = findViewById(R.id.layoutDetailsContainer)

        menuItemAi = findViewById(R.id.menuItemAi)
        menuItemDeepgram = findViewById(R.id.menuItemDeepgram)
        menuItemMic = findViewById(R.id.menuItemMic)
        menuItemWidget = findViewById(R.id.menuItemWidget)
        menuItemDocuments = findViewById(R.id.menuItemDocuments)
        menuItemCalendar = findViewById(R.id.menuItemCalendar)
        menuItemMemory = findViewById(R.id.menuItemMemory)
        menuItemPsychology = findViewById(R.id.menuItemPsychology)

        cardGemini = findViewById(R.id.cardGemini)
        cardDeepgram = findViewById(R.id.cardDeepgram)
        cardMicSettings = findViewById(R.id.cardMicSettings)
        cardWidget = findViewById(R.id.cardWidget)
        documentCard = findViewById(R.id.documentCard)
        calendarCard = findViewById(R.id.calendarCard)
        cardMemory = findViewById(R.id.cardMemory)
        cardPsychology = findViewById(R.id.cardPsychology)

        menuItemAi.setOnClickListener {
            showCategoryDetail(cardGemini, "การตั้งค่า Gemini Live API")
        }
        menuItemDeepgram.setOnClickListener {
            showCategoryDetail(cardDeepgram, "การตั้งค่า Deepgram ASR")
        }
        menuItemMic.setOnClickListener {
            showCategoryDetail(cardMicSettings, "ระดับเสียงและไมโครโฟน")
        }
        menuItemWidget.setOnClickListener {
            showCategoryDetail(cardWidget, "อินเตอร์เฟซและวิดเจ็ตลอย")
        }
        menuItemDocuments.setOnClickListener {
            showCategoryDetail(documentCard, "เอกสารกรมธรรม์ & คู่มือรถ")
        }
        menuItemCalendar.setOnClickListener {
            showCategoryDetail(calendarCard, "ตารางนัดหมายและการประชุม")
        }
        menuItemMemory.setOnClickListener {
            showCategoryDetail(cardMemory, "ฐานความจำและข้อมูลรถยนต์")
        }
        menuItemPsychology.setOnClickListener {
            showCategoryDetail(cardPsychology, "คู่มือจิตวิทยาและการเจรจา")
        }

        btnBack.setOnClickListener {
            handleBackPress()
        }

        btnManageMemory.setOnClickListener {
            val intent = Intent(this, com.example.geminimultimodalliveapi.ui.memory.MemoryActivity::class.java)
            startActivity(intent)
        }

        btnUploadPsychology = findViewById(R.id.btnUploadPsychology)
        btnUploadPsychology.setOnClickListener {
            pickPsychologyPdfLauncher.launch(arrayOf("application/pdf"))
        }

        btnViewConvertedPsychology = findViewById(R.id.btnViewConvertedPsychology)
        btnViewConvertedPsychology.setOnClickListener {
            showViewConvertedPsychologyDialog()
        }

        // Initialize Skill Selector
        spinnerSkillSettings = findViewById(R.id.spinnerSkillSettings)
        btnEditSkillSettings = findViewById(R.id.btnEditSkillSettings)
        datingSkillManager = com.example.geminimultimodalliveapi.data.DatingSkillManager(this)

        setupSkillSpinner()

        btnEditSkillSettings.setOnClickListener {
            val activeId = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId
            val activeSkill = datingSkillManager.getSkill(activeId)
            if (activeSkill != null) {
                showEditSkillDialog(activeSkill)
            } else {
                Toast.makeText(this, "โปรดเลือกทักษะที่ต้องการแก้ไข", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize Managers
        val rootView = findViewById<View>(android.R.id.content)
        calendarManager = com.example.geminimultimodalliveapi.calendar.CalendarManager(this, rootView, object : com.example.geminimultimodalliveapi.calendar.CalendarManager.Callbacks {
            override fun launchGoogleSignIn(intent: Intent) {
                googleSignInLauncher.launch(intent)
            }
        })
        documentManager = com.example.geminimultimodalliveapi.document.DocumentManager(this, rootView, object : com.example.geminimultimodalliveapi.document.DocumentManager.Callbacks {
            override fun onGoogleSignedOut() {
                this@SettingsActivity.onGoogleSignedOut()
            }

            override fun launchGoogleSignIn(intent: Intent) {
                googleSignInLauncher.launch(intent)
            }

            override fun launchDocumentPicker(mimeTypes: Array<String>) {
                pickDocumentLauncher.launch(mimeTypes)
            }
        })

        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastAccount != null) {
            documentManager.onSignedIn(lastAccount)
            calendarManager.onSignedIn(lastAccount)
        } else {
            documentManager.onSignedOut()
            calendarManager.onSignedOut()
        }

        // API Key Preference
        val rawSavedKey = appPrefs.apiKey
        if (rawSavedKey.isEmpty() && BuildConfig.GEMINI_API_KEY.isNotEmpty()) {
            apiKeyInput.setText(BuildConfig.GEMINI_API_KEY)
            appPrefs.apiKey = BuildConfig.GEMINI_API_KEY
        } else {
            apiKeyInput.setText(rawSavedKey)
        }

        // Deepgram API Key Preference
        val rawSavedDeepgramKey = appPrefs.deepgramApiKey
        deepgramApiKeyInput.setText(rawSavedDeepgramKey)
        deepgramApiKeyWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                appPrefs.deepgramApiKey = s.toString().trim()
            }
        }
        deepgramApiKeyInput.addTextChangedListener(deepgramApiKeyWatcher)

        btnTestDeepgram.setOnClickListener {
            val key = deepgramApiKeyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "โปรดกรอก Deepgram API Key", Toast.LENGTH_SHORT).show()
            } else {
                testDeepgramConnection(key)
            }
        }

        // Voice selection preference
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, VOICES)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        voiceSpinner.adapter = adapter

        val savedVoice = appPrefs.selectedVoice
        val voiceIndex = VOICE_IDS.indexOf(savedVoice)
        if (voiceIndex >= 0) {
            voiceSpinner.setSelection(voiceIndex)
        }

        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedVoice = VOICE_IDS[position]
                appPrefs.selectedVoice = selectedVoice
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Wake Word Preference
        val savedWakeWord = appPrefs.wakeWord
        wakeWordInput.setText(savedWakeWord)
        wakeWordWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val newWord = s.toString().trim()
                if (newWord.isNotEmpty()) {
                    appPrefs.wakeWord = newWord
                    FloatingWidgetService.instance?.updateWakeWord(newWord)
                }
            }
        }
        wakeWordInput.addTextChangedListener(wakeWordWatcher)

        // Translate Mode Settings
        switchTranslateMode.isChecked = appPrefs.isTranslateModeEnabled
        switchTranslateMode.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.isTranslateModeEnabled = isChecked
            if (isChecked) {
                if (FloatingWidgetService.instance != null && com.example.geminimultimodalliveapi.session.SessionStateHolder.state.value !is com.example.geminimultimodalliveapi.session.SessionState.Disconnected) {
                    val intent = Intent(this, com.example.geminimultimodalliveapi.ui.translate.TranslateActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            } else {
                sendBroadcast(Intent("ACTION_CLOSE_TRANSLATE_UI"))
            }
        }

        val targetLangs = arrayOf("th" to "Thai", "en" to "English", "ja" to "Japanese", "zh" to "Chinese", "ko" to "Korean")
        val targetLangNames = targetLangs.map { it.second }.toTypedArray()
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, targetLangNames)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTargetLanguage.adapter = langAdapter
        
        val savedTargetLang = appPrefs.translateTargetLanguage
        val langIndex = targetLangs.indexOfFirst { it.first == savedTargetLang }
        if (langIndex >= 0) spinnerTargetLanguage.setSelection(langIndex)
        
        spinnerTargetLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.translateTargetLanguage = targetLangs[position].first
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val coachingIntensities = arrayOf("NONE" to "ปิดการแนะนำ", "ALERT_ONLY" to "แจ้งเตือนเฉพาะเมื่ออันตราย/โกหก", "ADVISORY" to "แนะนำเป็นระยะ", "FULL_COACHING" to "แนะนำแบบละเอียดต่อเนื่อง")
        val coachingNames = coachingIntensities.map { it.second }.toTypedArray()
        val coachingAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, coachingNames)
        coachingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCoachingIntensity.adapter = coachingAdapter

        val savedCoaching = appPrefs.coachingIntensity
        val coachingIndex = coachingIntensities.indexOfFirst { it.first == savedCoaching }
        if (coachingIndex >= 0) spinnerCoachingIntensity.setSelection(coachingIndex)

        spinnerCoachingIntensity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.coachingIntensity = coachingIntensities[position].first
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val profiles = arrayOf("PASSIVE_SUMMARY" to "แค่บันทึกสรุปหลังจบ", "DATING" to "เดทติ้ง / จีบสาว", "BUSINESS_NEGOTIATION" to "เจรจาธุรกิจ", "INTERVIEW" to "สัมภาษณ์งาน", "CASUAL_CHAT" to "เพื่อนคุยเล่น")
        val profileNames = profiles.map { it.second }.toTypedArray()
        val profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileNames)
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerContextProfile.adapter = profileAdapter

        val savedProfile = appPrefs.selectedContextProfile
        val profileIndex = profiles.indexOfFirst { it.first == savedProfile }
        if (profileIndex >= 0) spinnerContextProfile.setSelection(profileIndex)

        spinnerContextProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.selectedContextProfile = profiles[position].first
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Floating widget preference
        val isFloatingWidgetEnabled = appPrefs.isFloatingWidgetEnabled
        floatingWidgetSwitch.isChecked = isFloatingWidgetEnabled

        floatingWidgetSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (PermissionHelper.hasOverlayPermission(this)) {
                    appPrefs.isFloatingWidgetEnabled = true
                    startFloatingWidgetService()
                } else {
                    floatingWidgetSwitch.isChecked = false
                    PermissionHelper.requestOverlayPermission(this)
                    Toast.makeText(this, "Please grant Overlay permission to enable the Floating Widget", Toast.LENGTH_LONG).show()
                }
            } else {
                appPrefs.isFloatingWidgetEnabled = false
                stopFloatingWidgetService()
            }
        }

        // Chime sound preference
        val isChimeEnabled = appPrefs.isChimeEnabled
        chimeSwitch.isChecked = isChimeEnabled
        chimeSwitch.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.isChimeEnabled = isChecked
        }

        // Situational Awareness Preferences Setup
        switchSensorIntegration.isChecked = appPrefs.isSensorIntegrationEnabled
        switchSensorIntegration.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val permissions = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                if (permissions.isNotEmpty()) {
                    switchSensorIntegration.isChecked = false
                    ActivityCompat.requestPermissions(
                        this,
                        permissions.toTypedArray(),
                        401
                    )
                } else {
                    appPrefs.isSensorIntegrationEnabled = true
                }
            } else {
                appPrefs.isSensorIntegrationEnabled = false
            }
        }

        switchProactiveEvents.isChecked = appPrefs.isProactiveEventsEnabled
        switchProactiveEvents.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.isProactiveEventsEnabled = isChecked
        }

        switchDynamicFps.isChecked = appPrefs.isDynamicFpsEnabled
        switchDynamicFps.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.isDynamicFpsEnabled = isChecked
        }

        switchNotificationPercept.isChecked = appPrefs.isNotificationPerceptEnabled
        switchNotificationPercept.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isNotificationServiceEnabled()) {
                    switchNotificationPercept.isChecked = false
                    Toast.makeText(this, "โปรดอนุญาตสิทธิ์เข้าถึงแจ้งเตือนสำหรับแอปพลิเคชัน", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "ไม่สามารถเปิดหน้าตั้งค่าได้: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    appPrefs.isNotificationPerceptEnabled = true
                }
            } else {
                appPrefs.isNotificationPerceptEnabled = false
            }
        }

        // Active Session Inactivity Timeout Preference
        val TIMEOUT_OPTIONS = arrayOf("30 วินาที", "45 วินาที", "1 นาที", "ไม่กำหนดเวลา")
        val TIMEOUT_VALUES = arrayOf(30000L, 45000L, 60000L, -1L)
        val timeoutAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, TIMEOUT_OPTIONS)
        timeoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActiveTimeout.adapter = timeoutAdapter

        val savedTimeout = appPrefs.activeSessionTimeoutMs
        val timeoutIndex = TIMEOUT_VALUES.indexOf(savedTimeout)
        if (timeoutIndex >= 0) {
            spinnerActiveTimeout.setSelection(timeoutIndex)
        } else {
            spinnerActiveTimeout.setSelection(0) // Default to 30s
        }
        spinnerActiveTimeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.activeSessionTimeoutMs = TIMEOUT_VALUES[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Standby Session Disconnect Timeout Preference
        val DISCONNECT_OPTIONS = arrayOf("45 วินาที", "1 นาที", "5 นาที", "ไม่จำกัดเวลา")
        val DISCONNECT_VALUES = arrayOf(45000L, 60000L, 300000L, -1L)
        val disconnectAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, DISCONNECT_OPTIONS)
        disconnectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDisconnectTimeout.adapter = disconnectAdapter

        val savedDisconnect = appPrefs.sessionDisconnectTimeoutMs
        val disconnectIndex = DISCONNECT_VALUES.indexOf(savedDisconnect)
        if (disconnectIndex >= 0) {
            spinnerDisconnectTimeout.setSelection(disconnectIndex)
        } else {
            spinnerDisconnectTimeout.setSelection(2) // Default to 5 mins
        }
        spinnerDisconnectTimeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.sessionDisconnectTimeoutMs = DISCONNECT_VALUES[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Mic AGC & Gain Preferences
        val GAIN_OPTIONS = arrayOf("0.5x", "1.0x", "1.5x", "2.0x (แนะนำ)", "2.5x", "3.0x", "4.0x", "5.0x")
        val GAIN_VALUES = arrayOf(0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f)
        val gainAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, GAIN_OPTIONS)
        gainAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMicGain.adapter = gainAdapter

        val savedGain = appPrefs.micGainValue
        val gainIndex = GAIN_VALUES.indexOf(savedGain)
        if (gainIndex >= 0) {
            spinnerMicGain.setSelection(gainIndex)
        } else {
            spinnerMicGain.setSelection(3) // Default to 2.0x
        }

        val isAgcEnabled = appPrefs.isMicAgcEnabled
        switchMicAgc.isChecked = isAgcEnabled
        spinnerMicGain.isEnabled = !isAgcEnabled
        layoutMicGain.alpha = if (isAgcEnabled) 0.5f else 1.0f

        switchMicAgc.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.isMicAgcEnabled = isChecked
            spinnerMicGain.isEnabled = !isChecked
            layoutMicGain.alpha = if (isChecked) 0.5f else 1.0f
        }

        spinnerMicGain.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.micGainValue = GAIN_VALUES[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Calendar Reminder settings preference
        val reminderOptions = arrayOf(
            "10 นาทีล่วงหน้า",
            "15 นาทีล่วงหน้า",
            "30 นาทีล่วงหน้า",
            "1 ชั่วโมงล่วงหน้า",
            "1 วันล่วงหน้า"
        )
        val reminderValues = arrayOf(10, 15, 30, 60, 1440)
        val reminderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reminderOptions)
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        reminderSpinner.adapter = reminderAdapter

        val savedReminder = appPrefs.calendarReminderMinutes
        val reminderIndex = reminderValues.indexOf(savedReminder)
        if (reminderIndex >= 0) {
            reminderSpinner.setSelection(reminderIndex)
        } else {
            reminderSpinner.setSelection(1) // Default to 15 mins
        }

        reminderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appPrefs.calendarReminderMinutes = reminderValues[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Connect button click listener
        connectButton.setOnClickListener {
            if (isConnected) {
                FloatingWidgetService.disconnectSession(this@SettingsActivity)
            } else {
                val key = apiKeyInput.text.toString().trim()
                if (key.isEmpty()) {
                    Toast.makeText(this, "Please enter Gemini API Key", Toast.LENGTH_SHORT).show()
                } else {
                    appPrefs.apiKey = key
                    runOnUiThread {
                        connectButton.isEnabled = false
                        connectButton.text = "Checking key..."
                    }
                    ApiKeyValidator.verifyApiKey(key) { isValid, message ->
                        runOnUiThread {
                            if (isValid) {
                                checkPermissionsAndConnect(key)
                            } else {
                                connectButton.isEnabled = true
                                connectButton.text = "Connect"
                                apiKeyInput.isEnabled = true
                                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        isConnected = FloatingWidgetService.isSessionConnected
        updateStatusIndicator()

        // Migrate old psychology_database.json to documents/ folder if exists
        Thread {
            try {
                val oldFile = java.io.File(filesDir, "psychology_database.json")
                if (oldFile.exists()) {
                    val docsDir = java.io.File(filesDir, "documents")
                    if (!docsDir.exists()) docsDir.mkdirs()
                    val newFile = java.io.File(docsDir, "doc_psychology_default.json")
                    if (!newFile.exists()) {
                        val oldText = oldFile.readText()
                        val oldJson = org.json.JSONObject(oldText)
                        if (!oldJson.has("title")) {
                            oldJson.put("title", "คู่มือจิตวิทยาความสัมพันธ์")
                            oldJson.put("category", "dating")
                            oldJson.put("description", "สรุปคู่มือจิตวิทยาความสัมพันธ์การเดตและการสื่อสารสำหรับ AI แนะนำคู่สนทนา")
                        }
                        newFile.writeText(oldJson.toString())
                        Log.i("Migration", "Migrated old psychology_database.json to documents/doc_psychology_default.json")
                    }
                    oldFile.delete()
                }
            } catch (e: Exception) {
                Log.e("Migration", "Error migrating old database", e)
            }
        }.start()
    }

    private fun checkPermissionsAndConnect(key: String) {
        if (PermissionHelper.checkAndRequestPermissions(this)) {
            FloatingWidgetService.connectSession(this, key)
        }
    }

    private fun testDeepgramConnection(key: String) {
        btnTestDeepgram.isEnabled = false
        btnTestDeepgram.text = "Testing..."

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.deepgram.com/v1/projects")
            .addHeader("Authorization", "Token $key")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnTestDeepgram.isEnabled = true
                    btnTestDeepgram.text = "Test"
                    Toast.makeText(this@SettingsActivity, "เชื่อมต่อล้มเหลว: ไม่มีอินเทอร์เน็ต", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                runOnUiThread {
                    btnTestDeepgram.isEnabled = true
                    btnTestDeepgram.text = "Test"
                    if (response.isSuccessful) {
                        Toast.makeText(this@SettingsActivity, "เชื่อมต่อสำเร็จ! คีย์ใช้งานได้ปกติ", Toast.LENGTH_SHORT).show()
                    } else {
                        if (code == 401) {
                            Toast.makeText(this@SettingsActivity, "คีย์ไม่ถูกต้อง (401 Unauthorized)", Toast.LENGTH_LONG).show()
                        } else if (code == 402) {
                            Toast.makeText(this@SettingsActivity, "เครดิตการใช้งานหมดแล้ว (402 Payment Required)", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@SettingsActivity, "ข้อผิดพลาดรหัส $code", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun startFloatingWidgetService() {
        val intent = Intent(this, FloatingWidgetService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("FloatingWidget", "Error starting FloatingWidgetService", e)
        }
    }

    private fun stopFloatingWidgetService() {
        val intent = Intent(this, FloatingWidgetService::class.java)
        try {
            stopService(intent)
        } catch (e: Exception) {
            Log.e("FloatingWidget", "Error stopping FloatingWidgetService", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQUIRED_PERMISSIONS_REQUEST_CODE) {
            val hasAudio = PermissionHelper.hasRecordAudioPermission(this)
            val hasCamera = PermissionHelper.hasCameraPermission(this)
            if (hasAudio && hasCamera) {
                if (!FloatingWidgetService.isSessionConnected) {
                    val key = apiKeyInput.text.toString().trim()
                    if (key.isNotEmpty()) {
                        FloatingWidgetService.connectSession(this, key)
                    }
                }
            } else {
                connectButton.isEnabled = true
                connectButton.text = "Connect"
                Toast.makeText(this, "Required permissions (Microphone/Camera) denied. Cannot connect.", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 401) {
            val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (hasLocation && hasActivity) {
                appPrefs.isSensorIntegrationEnabled = true
                switchSensorIntegration.isChecked = true
                Toast.makeText(this, "เปิดใช้งานเซ็นเซอร์ตรวจจับสถานการณ์เรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
            } else {
                appPrefs.isSensorIntegrationEnabled = false
                switchSensorIntegration.isChecked = false
                Toast.makeText(this, "จำเป็นต้องได้รับสิทธิ์ตำแหน่งและกิจกรรมเคลื่อนไหวเพื่อเปิดใช้งาน", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatusIndicator() {
        runOnUiThread {
            if (!isConnected) {
                connectButton.text = "Connect"
                connectButton.isEnabled = true
                connectButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FFFFFF")
                )
                connectButton.setTextColor(android.graphics.Color.parseColor("#000000"))
                apiKeyInput.isEnabled = true
                voiceSpinner.isEnabled = true
            } else {
                connectButton.text = "Disconnect"
                connectButton.isEnabled = true
                connectButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#33FFFFFF")
                )
                connectButton.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                apiKeyInput.isEnabled = false
                voiceSpinner.isEnabled = false
            }
        }
    }

    private var reAuthDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showReAuthDialog() {
        val googleSignInClient = GoogleSignInHelper.getClient(this)
        reAuthDialog = showReAuthDialog(reAuthDialog) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    override fun onResume() {
        super.onResume()

        flowCollectJob = activityScope.launch {
            launch {
                SessionStateHolder.state.collect { state ->
                    isConnected = state != SessionState.Disconnected
                    updateStatusIndicator()
                }
            }
            launch {
                SessionStateHolder.errorFlow.collect { error ->
                    if (error is AppError.AuthExpired) {
                        showReAuthDialog()
                    }
                }
            }
        }

        if (::calendarManager.isInitialized) {
            calendarManager.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        flowCollectJob?.cancel()
        flowCollectJob = null
        if (::calendarManager.isInitialized) {
            calendarManager.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deepgramApiKeyWatcher?.let { deepgramApiKeyInput.removeTextChangedListener(it) }
        wakeWordWatcher?.let { wakeWordInput.removeTextChangedListener(it) }
        activityScope.cancel()
        if (::calendarManager.isInitialized) {
            calendarManager.onDestroy()
        }
        if (::documentManager.isInitialized) {
            documentManager.onDestroy()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("menu_visibility", layoutCategoryMenu.visibility)
        outState.putCharSequence("title_text", txtSettingsTitle.text)
    }

    val pickDocumentLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            documentManager.handleSelectedDocument(uri)
        }
    }

    val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    documentManager.onSignedIn(account)
                    calendarManager.onSignedIn(account)
                    Toast.makeText(this, "เชื่อมต่อ Google สำเร็จ!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GDrive", "Sign-in failed", e)
                Toast.makeText(this, "การเชื่อมต่อล้มเหลว: ${e.message}", Toast.LENGTH_LONG).show()
                documentManager.onSignedOut()
                calendarManager.onSignedOut()
            }
        } else {
            Log.e("GDrive", "Sign-in cancelled or failed with code: ${result.resultCode}")
            Toast.makeText(this, "ยกเลิกการเชื่อมต่อ", Toast.LENGTH_SHORT).show()
            documentManager.onSignedOut()
            calendarManager.onSignedOut()
        }
    }

    fun onGoogleSignedOut() {
        documentManager.onSignedOut()
        calendarManager.onSignedOut()
    }

    private fun showCategoryDetail(activeCard: View, title: String) {
        layoutCategoryMenu.visibility = View.GONE
        layoutDetailsContainer.visibility = View.VISIBLE

        cardGemini.visibility = View.GONE
        cardDeepgram.visibility = View.GONE
        cardMicSettings.visibility = View.GONE
        cardWidget.visibility = View.GONE
        documentCard.visibility = View.GONE
        calendarCard.visibility = View.GONE
        cardMemory.visibility = View.GONE
        cardPsychology.visibility = View.GONE

        activeCard.visibility = View.VISIBLE
        txtSettingsTitle.text = title
    }

    private fun handleBackPress() {
        if (layoutCategoryMenu.visibility == View.GONE) {
            layoutCategoryMenu.visibility = View.VISIBLE
            layoutDetailsContainer.visibility = View.GONE
            txtSettingsTitle.text = "การตั้งค่าการใช้งาน"
        } else {
            finish()
        }
    }



    private val pickPsychologyPdfLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            handleSelectedPsychologyPdf(uri)
        }
    }

    private var uploadProgressDialog: AlertDialog? = null

    private fun showProgressDialog(title: String, message: String) {
        runOnUiThread {
            this@SettingsActivity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (uploadProgressDialog != null) {
                uploadProgressDialog?.dismiss()
            }
            val builder = AlertDialog.Builder(this)
            val padding = dpToPx(24)
            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(padding, padding, padding, padding)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val progressBar = android.widget.ProgressBar(this).apply {
                isIndeterminate = true
                setPadding(0, 0, dpToPx(16), 0)
            }
            val textView = TextView(this).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = 14f
            }
            layout.addView(progressBar)
            layout.addView(textView)

            builder.setView(layout)
            builder.setTitle(title)
            builder.setCancelable(false)
            if (!isFinishing && !isDestroyed) {
                uploadProgressDialog = builder.create().apply {
                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#E61E1E1E")))
                    show()
                }
            }
        }
    }

    private fun updateProgressDialog(message: String) {
        runOnUiThread {
            uploadProgressDialog?.let { dialog ->
                fun findTextView(view: View): TextView? {
                    if (view is TextView) return view
                    if (view is android.view.ViewGroup) {
                        for (i in 0 until view.childCount) {
                            val child = view.getChildAt(i)
                            val res = findTextView(child)
                            if (res != null) return res
                        }
                    }
                    return null
                }
                val tv = dialog.window?.decorView?.let { findTextView(it) }
                if (tv != null) {
                    tv.text = message
                }
            }
        }
    }

    private fun dismissProgressDialog() {
        runOnUiThread {
            this@SettingsActivity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            uploadProgressDialog?.dismiss()
            uploadProgressDialog = null
        }
    }

    private fun setupSkillSpinner() {
        val skills = datingSkillManager.getAllSkills()
        val names = skills.map { it.name }.toMutableList()
        names.add("+ เพิ่มทักษะใหม่...")

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSkillSettings.adapter = adapter

        val activeId = appPrefs.lastDatingSkillId
        val activeSkill = skills.find { it.id == activeId }
        if (activeSkill != null) {
            val idx = skills.indexOf(activeSkill)
            if (idx >= 0) {
                spinnerSkillSettings.setSelection(idx)
                com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId = activeSkill.id
            }
        } else if (skills.isNotEmpty()) {
            spinnerSkillSettings.setSelection(0)
            com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId = skills[0].id
            appPrefs.lastDatingSkillId = skills[0].id
        }

        spinnerSkillSettings.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < skills.size) {
                    val selected = skills[position]
                    com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId = selected.id
                    appPrefs.lastDatingSkillId = selected.id
                } else {
                    showEditSkillDialog(null)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun showEditSkillDialog(skill: com.example.geminimultimodalliveapi.data.DatingSkill?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_skill, null)
        val editName = dialogView.findViewById<android.widget.EditText>(R.id.editSkillName)
        val editDesc = dialogView.findViewById<android.widget.EditText>(R.id.editSkillDescription)
        val editInst = dialogView.findViewById<android.widget.EditText>(R.id.editSkillInstructions)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)

        if (skill != null) {
            dialogTitle.text = "แก้ไขทักษะ (Skill.md)"
            editName.setText(skill.name)
            editName.isEnabled = false
            editDesc.setText(skill.description)
            editInst.setText(skill.instructions)
        } else {
            dialogTitle.text = "เพิ่มทักษะใหม่ (Skill.md)"
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
            if (skill == null) {
                val activeId = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId
                val skills = datingSkillManager.getAllSkills()
                val idx = skills.indexOfFirst { it.id == activeId }
                if (idx >= 0) {
                    spinnerSkillSettings.setSelection(idx)
                }
            }
        }

        dialogView.findViewById<View>(R.id.btnSave).setOnClickListener {
            val name = editName.text.toString().trim()
            val desc = editDesc.text.toString().trim()
            val inst = editInst.text.toString().trim()

            if (name.isEmpty() || desc.isEmpty() || inst.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกข้อมูลให้ครบถ้วน", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val id = skill?.id ?: name.lowercase().replace("\\s+".toRegex(), "_").replace("[^a-z0-9_]".toRegex(), "")
            if (id.isEmpty()) {
                Toast.makeText(this, "ชื่อทักษะไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newSkill = com.example.geminimultimodalliveapi.data.DatingSkill(
                id = id,
                name = name,
                description = desc,
                instructions = inst
            )

            if (datingSkillManager.saveSkill(newSkill)) {
                Toast.makeText(this, "บันทึกทักษะเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
                appPrefs.lastDatingSkillId = id
                setupSkillSpinner()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "เกิดข้อผิดพลาดในการบันทึกทักษะ", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun handleSelectedPsychologyPdf(uri: android.net.Uri) {
        val apiKey = appPrefs.apiKey
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกและเชื่อมต่อ Gemini API Key ก่อนอัปโหลดตำรา", Toast.LENGTH_LONG).show()
            return
        }

        showProgressDialog("การนำเข้าเอกสาร", "กำลังอ่านและสกัดข้อความจากไฟล์ PDF...")

        activityScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    dismissProgressDialog()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "ไม่สามารถเปิดไฟล์ PDF ได้", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Initialize PDFBox
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
                val rawText = inputStream.use { stream ->
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream).use { doc ->
                        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                        stripper.startPage = 1
                        stripper.endPage = 10
                        val fullText = stripper.getText(doc)
                        if (fullText.length > 50000) {
                            fullText.substring(0, 50000)
                        } else {
                            fullText
                        }
                    }
                }

                val cleanedText = rawText.trim()
                if (cleanedText.length < 100) {
                    dismissProgressDialog()
                    withContext(Dispatchers.Main) {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("ไม่สามารถดึงข้อมูลได้")
                            .setMessage("ไม่สามารถสกัดข้อความจากไฟล์ PDF นี้ได้ (ไฟล์อาจเป็นรูปภาพสแกน ไม่มีเลเยอร์ข้อมูลตัวหนังสือ หรือตัวอักษรน้อยกว่า 100 ตัว) กรุณาอัปโหลดไฟล์ PDF ที่มีข้อความพิมพ์")
                            .setPositiveButton("ตกลง", null)
                            .show()
                    }
                    return@launch
                }

                if (rawText.trim().length > 50000) {
                    updateProgressDialog("คำเตือน: เอกสารยาวเกิน 50,000 ตัวอักษร ระบบจะจำกัดเฉพาะ 50,000 ตัวแรกเพื่อประมวลผล...")
                    delay(1500L)
                }

                updateProgressDialog("กำลังวิเคราะห์และจัดโครงสร้างด้วย Gemini...")

                // Send to Gemini to summarize & format as JSON
                val prompt = """
                    คุณคือผู้เชี่ยวชาญด้านการสื่อสารและการต่อรองเจรจา (เช่น จิตวิทยาความสัมพันธ์ การเจรจาธุรกิจ หรือการต่อรองราคา)
                    โปรดวิเคราะห์ข้อความจากเอกสารอ้างอิงต่อไปนี้ และสรุปโครงสร้างความรู้/แนวคิด/เทคนิคที่สำคัญเพื่อใช้เป็นฐานข้อมูลแนะนำ AI ในการช่วยผู้ใช้วิเคราะห์การคุย
                    
                    ข้อความจากเอกสาร:
                    $cleanedText
                    
                    โปรดแปลงข้อมูลนี้ให้อยู่ในรูปแบบ JSON ต่อไปนี้เท่านั้น ห้ามมีคำอธิบายเพิ่มเติมใดๆ นอกเหนือจาก JSON:
                    {
                      "title": "ชื่อเอกสารหรือหัวข้อสรุปสั้นๆ (ภาษาไทย)",
                      "category": "หมวดหมู่เอกสารภาษาอังกฤษ (เช่น dating, business, bargaining, social_chat)",
                      "description": "คำอธิบายสั้นๆ เกี่ยวกับเนื้อหาของเอกสารนี้และหัวข้อที่ควรนำไปใช้ (ภาษาไทย)",
                      "principles": [
                        {
                          "name": "ชื่อหลักการ/แนวคิด/เทคนิค",
                          "description": "คำอธิบายสั้นๆ เกี่ยวกับหลักการนี้",
                          "keywords": ["คีย์เวิร์ดภาษาไทยที่เกี่ยวข้อง 3-4 คำ"],
                          "tips": [
                            "คำแนะนำในการเจรจา/สนทนา หรือคำถามชวนคุยที่สอดคล้องกับหลักการนี้ 1",
                            "คำแนะนำในการเจรจา/สนทนา หรือคำถามชวนคุยที่สอดคล้องกับหลักการนี้ 2"
                          ]
                        }
                      ]
                    }
                """.trimIndent()

                val systemPrompt = "คุณคือระบบแปลงเอกสารแนวทางเจรจาจิตวิทยาให้เป็นโครงสร้าง JSON เพื่อใช้ประมวลผลต่อ ให้ผลลัพธ์เป็น JSON เสมอ ห้ามพิมพ์คำอธิบายอื่นนอกเหนือจากโครงสร้าง JSON"

                com.example.geminimultimodalliveapi.network.GeminiTextService.generateTextWithSystemInstruction(
                    apiKey = apiKey,
                    prompt = prompt,
                    systemInstructionText = systemPrompt
                ) { resultText ->
                    activityScope.launch(Dispatchers.IO) {
                        dismissProgressDialog()
                        if (resultText == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SettingsActivity, "การวิเคราะห์โครงสร้างด้วย Gemini ล้มเหลว", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        // Clean up markdown wrapping if present
                        var cleanedJson = resultText.trim()
                        if (cleanedJson.startsWith("```json")) {
                            cleanedJson = cleanedJson.removePrefix("```json")
                        }
                        if (cleanedJson.startsWith("```")) {
                            cleanedJson = cleanedJson.removePrefix("```")
                        }
                        if (cleanedJson.endsWith("```")) {
                            cleanedJson = cleanedJson.removeSuffix("```")
                        }
                        cleanedJson = cleanedJson.trim()

                        // Validate JSON format
                        try {
                            org.json.JSONObject(cleanedJson)
                            
                            val docsDir = java.io.File(filesDir, "documents")
                            if (!docsDir.exists()) docsDir.mkdirs()
                            
                            val file = java.io.File(docsDir, "doc_${System.currentTimeMillis()}.json")
                            file.writeText(cleanedJson)

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SettingsActivity, "นำเข้าและจัดหมวดหมู่ตำราเอกสารสำเร็จเรียบร้อย!", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e("PsychologyPDF", "Invalid JSON from Gemini: $cleanedJson", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SettingsActivity, "การวิเคราะห์ล้มเหลว: โครงสร้างข้อมูลไม่ถูกต้อง", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                dismissProgressDialog()
                Log.e("PsychologyPDF", "Error handling selected PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "เกิดข้อผิดพลาดในการอ่านไฟล์ PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showViewConvertedPsychologyDialog() {
        val docsDir = java.io.File(filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()

        val jsonFiles = docsDir.listFiles { _, name ->
            name.startsWith("doc_") && name.endsWith(".json")
        } ?: emptyArray()

        if (jsonFiles.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("คลังเอกสารจิตวิทยา")
                .setMessage("ยังไม่มีเอกสารจิตวิทยาที่แปลงแล้ว\nกรุณาอัปโหลดเอกสาร PDF ก่อนเพื่อเพิ่มเข้าระบบ")
                .setPositiveButton("ตกลง", null)
                .show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("เอกสารที่แปลงแล้วทั้งหมด (${jsonFiles.size} ไฟล์)")

        val padding = dpToPx(16)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            )
        }

        val listLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        var dialogRef: AlertDialog? = null

        fun rebuildList() {
            listLayout.removeAllViews()
            val currentFiles = docsDir.listFiles { _, name ->
                name.startsWith("doc_") && name.endsWith(".json")
            } ?: emptyArray()

            if (currentFiles.isEmpty()) {
                val emptyTv = TextView(this).apply {
                    text = "ไม่มีเอกสารคงเหลือในระบบ"
                    setTextColor(Color.GRAY)
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dpToPx(24), 0, dpToPx(24))
                }
                listLayout.addView(emptyTv)
                return
            }

            for (file in currentFiles) {
                var title = file.name
                var category = "General"
                try {
                    val json = org.json.JSONObject(file.readText())
                    title = json.optString("title", file.name)
                    category = json.optString("category", "General")
                } catch (e: Exception) {}

                val rowLayout = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, dpToPx(8), 0, dpToPx(8))
                }

                val infoLayout = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    isClickable = true
                    setOnClickListener {
                        dialogRef?.dismiss()
                        showPsychologyDocumentDetailDialog(file)
                    }
                }

                val titleTv = TextView(this).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                }

                val categoryTv = TextView(this).apply {
                    text = "หมวดหมู่: $category"
                    setTextColor(Color.parseColor("#00BCD4"))
                    textSize = 11f
                }

                infoLayout.addView(titleTv)
                infoLayout.addView(categoryTv)

                val deleteButton = Button(this).apply {
                    text = "ลบ"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    background = getDrawable(android.R.drawable.btn_default)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        dpToPx(32)
                    )
                    setOnClickListener {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("ยืนยันการลบ")
                            .setMessage("คุณต้องการลบเอกสาร '$title' ใช่หรือไม่?")
                            .setPositiveButton("ลบ") { _, _ ->
                                file.delete()
                                rebuildList()
                                Toast.makeText(this@SettingsActivity, "ลบเอกสารสำเร็จ", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("ยกเลิก", null)
                            .show()
                    }
                }

                rowLayout.addView(infoLayout)
                rowLayout.addView(deleteButton)

                val divider = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                    ).apply {
                        setMargins(0, dpToPx(4), 0, dpToPx(4))
                    }
                    setBackgroundColor(Color.parseColor("#33FFFFFF"))
                }

                listLayout.addView(rowLayout)
                listLayout.addView(divider)
            }
        }

        rebuildList()
        scrollView.addView(listLayout)
        container.addView(scrollView)

        builder.setView(container)
        builder.setPositiveButton("ปิด", null)
        dialogRef = builder.create().apply {
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#E61E1E1E")))
            show()
        }
    }

    private fun showPsychologyDocumentDetailDialog(file: java.io.File) {
        var title = file.name
        var category = "ไม่ระบุหมวดหมู่"
        var description = "ไม่มีคำอธิบาย"
        val principlesList = mutableListOf<String>()

        try {
            val fileContent = file.readText()
            val json = org.json.JSONObject(fileContent)
            title = json.optString("title", file.name)
            category = json.optString("category", "General")
            description = json.optString("description", "ไม่มีคำอธิบาย")

            val principles = json.optJSONArray("principles")
            if (principles != null) {
                for (i in 0 until principles.length()) {
                    val p = principles.getJSONObject(i)
                    val pName = p.optString("name", "ไม่ระบุชื่อหลักการ")
                    val pDesc = p.optString("description", "")
                    
                    val keywordsArr = p.optJSONArray("keywords")
                    val keywords = if (keywordsArr != null) {
                        val kList = mutableListOf<String>()
                        for (j in 0 until keywordsArr.length()) {
                            kList.add(keywordsArr.getString(j))
                        }
                        kList.joinToString(", ")
                    } else {
                        ""
                    }

                    val tipsArr = p.optJSONArray("tips")
                    val tips = if (tipsArr != null) {
                        val tList = mutableListOf<String>()
                        for (j in 0 until tipsArr.length()) {
                            tList.add("- ${tipsArr.getString(j)}")
                        }
                        tList.joinToString("\n")
                    } else {
                        ""
                    }

                    val principleText = StringBuilder().apply {
                        append("📌 หลักการ: $pName\n")
                        if (pDesc.isNotEmpty()) append("• คำอธิบาย: $pDesc\n")
                        if (keywords.isNotEmpty()) append("• คำสำคัญ: $keywords\n")
                        if (tips.isNotEmpty()) append("💡 เคล็ดลับการสนทนา:\n$tips")
                    }.toString()

                    principlesList.add(principleText)
                }
            }
        } catch (e: Exception) {
            description = "เกิดข้อผิดพลาดในการดึงข้อมูลเนื้อหา: ${e.message}"
        }

        val thaiCategory = when (category.lowercase(java.util.Locale.US)) {
            "dating" -> "โหมดช่วยคุยเดต (Dating Assistant)"
            "bargaining" -> "โหมดต่อรองราคาสินค้า (Bargaining)"
            "business" -> "โหมดการเจรจาธุรกิจ (Business Negotiation)"
            "social_chat" -> "โหมดคุยทั่วไป/พัฒนาความสัมพันธ์ (Social Chat)"
            else -> category
        }

        val padding = dpToPx(16)
        val builder = AlertDialog.Builder(this)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(450)
            )
        }

        val contentLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, dpToPx(8))
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val categoryLabel = TextView(this).apply {
            text = "📁 หมวดหมู่การใช้งาน:"
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 12f
            setPadding(0, dpToPx(8), 0, 0)
        }
        val categoryTv = TextView(this).apply {
            text = thaiCategory
            setTextColor(Color.parseColor("#E040FB"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        }

        val descLabel = TextView(this).apply {
            text = "ℹ️ วัตถุประสงค์ / คำอธิบาย:"
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 12f
            setPadding(0, dpToPx(8), 0, 0)
        }
        val descTv = TextView(this).apply {
            text = description
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, dpToPx(16))
        }

        contentLayout.addView(titleTv)
        contentLayout.addView(categoryLabel)
        contentLayout.addView(categoryTv)
        contentLayout.addView(descLabel)
        contentLayout.addView(descTv)

        val divider = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                setMargins(0, 0, 0, dpToPx(12))
            }
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
        }
        contentLayout.addView(divider)

        val principlesTitle = TextView(this).apply {
            text = "🧠 หลักเกณฑ์ที่ระบบนำไปใช้งาน (${principlesList.size} ข้อ)"
            setTextColor(Color.parseColor("#00BCD4"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
        }
        contentLayout.addView(principlesTitle)

        for (pText in principlesList) {
            val cardView = com.google.android.material.card.MaterialCardView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(6), 0, dpToPx(6))
                }
                radius = dpToPx(8).toFloat()
                setCardBackgroundColor(Color.parseColor("#262626"))
            }

            val pTv = TextView(this).apply {
                text = pText
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            }
            cardView.addView(pTv)
            contentLayout.addView(cardView)
        }

        scrollView.addView(contentLayout)
        container.addView(scrollView)

        builder.setView(container)
        builder.setPositiveButton("ย้อนกลับ") { _, _ ->
            showViewConvertedPsychologyDialog()
        }
        builder.setNegativeButton("ปิดหน้าต่าง", null)
        builder.create().apply {
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#E61E1E1E")))
            show()
        }
    }



}
