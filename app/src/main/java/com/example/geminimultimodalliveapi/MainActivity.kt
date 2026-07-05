package com.example.geminimultimodalliveapi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.example.geminimultimodalliveapi.camera.CameraCaptureHelper
import com.example.geminimultimodalliveapi.utils.PerformanceMonitor
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.utils.PermissionHelper
import com.example.geminimultimodalliveapi.session.SessionState
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import com.example.geminimultimodalliveapi.error.AppError
import com.google.android.material.card.MaterialCardView
import com.example.geminimultimodalliveapi.utils.GoogleSignInHelper
import com.example.geminimultimodalliveapi.utils.isNotificationServiceEnabled
import com.example.geminimultimodalliveapi.utils.showReAuthDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import com.example.geminimultimodalliveapi.utils.dpToPx
import java.util.Locale


class MainActivity : AppCompatActivity() {

    companion object {
        var isSessionConnected = false
        var isCameraActive = false
    }

    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient
    private var reAuthDialog: androidx.appcompat.app.AlertDialog? = null

    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    Toast.makeText(this, getString(R.string.toast_google_connect_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Sign-in failed", e)
                Toast.makeText(this, getString(R.string.toast_google_connect_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.toast_disconnect), Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var textureView: TextureView
    private lateinit var appPrefs: AppPreferences
    private lateinit var btnMenuMenu: ImageButton
    private lateinit var btnMeetingMode: ImageButton
    private lateinit var btnMicCenter: MaterialCardView
    private lateinit var imgMicCenter: ImageView
    private lateinit var btnShutterSingle: MaterialCardView
    private lateinit var imgShutterIcon: ImageView
    private lateinit var wave1: View
    private lateinit var wave2: View
    private lateinit var chatLog: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var consoleCard: MaterialCardView
    private lateinit var switchSoloFocus: SwitchCompat
    private lateinit var switchDateMode: SwitchCompat
    private lateinit var dateInsightCard: MaterialCardView
    private lateinit var spinnerProfile: Spinner
    private lateinit var datingSkillManager: com.example.geminimultimodalliveapi.data.DatingSkillManager
    private lateinit var progressEngagementTemp: android.widget.ProgressBar
    private lateinit var txtEngagementLabel: TextView
    private lateinit var containerLikesChips: android.widget.LinearLayout
    private lateinit var containerDislikesChips: android.widget.LinearLayout
    private lateinit var txtDatingTip: TextView
    private lateinit var txtAgentInfo: TextView
    private lateinit var dateDbHelper: com.example.geminimultimodalliveapi.data.DateProfileDbHelper

    // Live Diagnostics Views
    private lateinit var txtAttentionState: TextView
    private lateinit var txtActiveTopic: TextView
    private lateinit var txtLastEvent: TextView
    private lateinit var btnMockDriving: android.widget.Button
    private lateinit var btnMockHome: android.widget.Button

    private val CAMERA_REQUEST_CODE = 100
    private val STORAGE_REQUEST_CODE = 400
    private val EXTRA_PERMISSIONS_REQUEST_CODE = 101
    private var isExplicitCapture = false
    private var cameraCallId: String? = null

    private var isConnected = false
    private var wasCameraActiveBeforeStop = false
    private var flowCollectJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // Activity Coroutine Scope
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Modularized Components
    private var cameraCaptureHelper: CameraCaptureHelper? = null
    private var performanceMonitor: PerformanceMonitor? = null

    // Wave Animations
    private var wave1Animator: android.animation.AnimatorSet? = null
    private var wave2Animator: android.animation.AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (cameraCaptureHelper?.isCameraActive == true) {
                    cameraCaptureHelper?.stopPreview()
                    updateCaptureButtonState(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        if (savedInstanceState != null) {
            wasCameraActiveBeforeStop = savedInstanceState.getBoolean("wasCameraActiveBeforeStop", false)
            isExplicitCapture = savedInstanceState.getBoolean("isExplicitCapture", false)
            cameraCallId = savedInstanceState.getString("cameraCallId")
            val savedProfile = savedInstanceState.getString("activeProfileName", "")
            if (savedProfile.isNotEmpty()) {
                SessionStateHolder.activeProfileName = savedProfile
            }
            val savedLogs = savedInstanceState.getString("chatLogs", "")
            if (savedLogs.isNotEmpty()) {
                SessionStateHolder.updateChatLogs(savedLogs)
            }
            val savedAttention = savedInstanceState.getString("diag_attentionState", "IDLE")
            val savedTopic = savedInstanceState.getString("diag_activeTopic", "None")
            val savedLastEvent = savedInstanceState.getString("diag_lastEvent", "None")
            val savedMotion = savedInstanceState.getString("diag_motionState", "STILL")
            val savedLocation = savedInstanceState.getString("diag_locationState", "home")
            SessionStateHolder.updateDiagnostics {
                it.copy(
                    attentionState = savedAttention,
                    activeTopic = savedTopic,
                    lastEvent = savedLastEvent,
                    motionState = savedMotion,
                    locationState = savedLocation
                )
            }
        }

        appPrefs = AppPreferences.getInstance(this)

        // Preload SQLCipher libraries in background thread to speed up MeetingActivity launch
        Thread {
            try {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(applicationContext)
                Log.i("MainActivity", "SQLCipher native libraries loaded in background successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error preloading SQLCipher native libraries", e)
            }
        }.start()

        textureView = findViewById(R.id.textureView)
        textureView.alpha = 0f

        btnMenuMenu = findViewById(R.id.btnMenuMenu)
        btnMeetingMode = findViewById(R.id.btnMeetingMode)
        btnMicCenter = findViewById(R.id.btnMicCenter)
        imgMicCenter = findViewById(R.id.imgMicCenter)
        btnShutterSingle = findViewById(R.id.btnShutterSingle)
        imgShutterIcon = findViewById(R.id.imgShutterIcon)
        wave1 = findViewById(R.id.wave1)
        wave2 = findViewById(R.id.wave2)
        chatLog = findViewById(R.id.chatLog)
        logScrollView = findViewById(R.id.logScrollView)
        consoleCard = findViewById(R.id.consoleCard)
        switchSoloFocus = findViewById(R.id.switchSoloFocus)

        // Bind Diagnostics Views
        txtAttentionState = findViewById(R.id.txtAttentionState)
        txtActiveTopic = findViewById(R.id.txtActiveTopic)
        txtLastEvent = findViewById(R.id.txtLastEvent)
        btnMockDriving = findViewById(R.id.btnMockDriving)
        btnMockHome = findViewById(R.id.btnMockHome)

        // Status Badge buttons initialized but click listeners are disabled as they represent real sensors now.
 
        switchSoloFocus.isChecked = appPrefs.isSoloFocusEnabled
        switchSoloFocus.setOnCheckedChangeListener { _, isChecked ->
            appPrefs.isSoloFocusEnabled = isChecked
            if (isChecked && switchDateMode.isChecked) {
                switchDateMode.isChecked = false
                com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive = false
                Toast.makeText(this, getString(R.string.toast_dating_mode_disabled_by_focus), Toast.LENGTH_SHORT).show()
            }
            if (FloatingWidgetService.isSessionConnected) {
                Toast.makeText(this, getString(R.string.toast_reconnect_to_switch_mode), Toast.LENGTH_SHORT).show()
            }
        }

        // Menu Button opens settings
        btnMenuMenu.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Meeting Button opens meeting recorder
        btnMeetingMode.setOnClickListener {
            startActivity(Intent(this, MeetingActivity::class.java))
        }

        // Center Mic Button click connects session or toggles active/standby
        btnMicCenter.setOnClickListener {
            if (!isConnected) {
                val key = appPrefs.apiKey
                if (key.isEmpty()) {
                    Toast.makeText(this, getString(R.string.toast_input_api_key_first), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    if (PermissionHelper.checkAndRequestPermissions(this)) {
                        checkNotificationAccessPermission()
                        displayMessage("SYSTEM: Connecting...")
                        FloatingWidgetService.connectSession(this, key)
                    }
                }
            } else {
                val service = FloatingWidgetService.instance
                if (service != null && service.liveClient?.isConnected == true) {
                    val state = SessionStateHolder.state.value
                    if (state is SessionState.Standby) {
                        service.transitionToState(SessionState.Active(true))
                    } else if (state is SessionState.Active) {
                        service.transitionToState(SessionState.Standby(appPrefs.wakeWord))
                    }
                }
            }
        }

        // Long click on mic disconnects the session
        btnMicCenter.setOnLongClickListener {
            if (isConnected) {
                displayMessage("SYSTEM: Disconnecting...")
                FloatingWidgetService.disconnectSession(this@MainActivity)
                true
            } else {
                false
            }
        }

        // Single Camera / Shutter Button Click
        btnShutterSingle.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, getString(R.string.toast_connect_session_first_for_camera), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val isCameraActive = cameraCaptureHelper?.isCameraActive ?: false
            if (isCameraActive) {
                // If camera is already active, capture photo (shutter)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_REQUEST_CODE)
                    return@setOnClickListener
                }
                isExplicitCapture = true
                cameraCaptureHelper?.captureSingleFrame()
            } else {
                // Open camera
                updateCaptureButtonState(true)
                if (textureView.isAvailable) {
                    startCameraPreview()
                } else {
                    textureView.surfaceTextureListener = surfaceTextureListener
                }
            }
        }

        // Initialize camera helper and performance monitor
        performanceMonitor = PerformanceMonitor { newLevel ->
            cameraCaptureHelper?.sendIntervalMs = newLevel.sendIntervalMs
            displayMessage("SYSTEM: Adjusted parameters to ${newLevel.description}")
        }

        cameraCaptureHelper = CameraCaptureHelper(this, textureView, { bytes ->
            processAndSendImage(bytes)
        }, { jitter ->
            performanceMonitor?.recordJitter(jitter)
        }).apply {
            onPreviewStarted = {
                cameraCallId?.let { callId ->
                    Log.i("MainActivity", "Camera preview started, responding success to tool call $callId")
                    FloatingWidgetService.sendToolResponse(callId, true)
                    cameraCallId = null
                }
            }
        }

        handleTriggerConnectionIntent(intent)
        handleCameraIntent(intent)
        handlePermissionIntent(intent)

        googleSignInClient = GoogleSignInHelper.getClient(this)

        isConnected = FloatingWidgetService.isSessionConnected
        updateStatusIndicator(SessionStateHolder.state.value)

        // Initialize Dating Assistant Mode UI & Logic
        dateDbHelper = com.example.geminimultimodalliveapi.data.DateProfileDbHelper(this)
        switchDateMode = findViewById(R.id.switchDateMode)
        dateInsightCard = findViewById(R.id.dateInsightCard)
        spinnerProfile = findViewById(R.id.spinnerProfile)
        datingSkillManager = com.example.geminimultimodalliveapi.data.DatingSkillManager(this)
        progressEngagementTemp = findViewById(R.id.progressEngagementTemp)
        txtEngagementLabel = findViewById(R.id.txtEngagementLabel)
        containerLikesChips = findViewById(R.id.containerLikesChips)
        containerDislikesChips = findViewById(R.id.containerDislikesChips)
        txtDatingTip = findViewById(R.id.txtDatingTip)
        txtAgentInfo = findViewById(R.id.txtAgentInfo)

        switchDateMode.isChecked = com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive
        if (switchDateMode.isChecked) {
            consoleCard.visibility = View.GONE
            dateInsightCard.visibility = View.VISIBLE
            btnShutterSingle.visibility = View.GONE
        } else {
            consoleCard.visibility = View.VISIBLE
            dateInsightCard.visibility = View.GONE
            btnShutterSingle.visibility = View.VISIBLE
        }

        switchDateMode.setOnCheckedChangeListener { _, isChecked ->
            com.example.geminimultimodalliveapi.session.SessionStateHolder.isDateAssistantModeActive = isChecked
            if (isChecked) {
                if (switchSoloFocus.isChecked) {
                    switchSoloFocus.isChecked = false
                    appPrefs.isSoloFocusEnabled = false
                    Toast.makeText(this, getString(R.string.toast_disable_focus_mode_to_receive_both), Toast.LENGTH_SHORT).show()
                }
                consoleCard.visibility = View.GONE
                dateInsightCard.visibility = View.VISIBLE
                btnShutterSingle.visibility = View.GONE
            } else {
                consoleCard.visibility = View.VISIBLE
                dateInsightCard.visibility = View.GONE
                btnShutterSingle.visibility = View.VISIBLE
            }
        }

        setupProfileSpinner()

    }

    private fun showReAuthDialog() {
        reAuthDialog = showReAuthDialog(reAuthDialog) {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            updateCaptureButtonState(true)
            startCameraPreview()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            cameraCaptureHelper?.configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            cameraCaptureHelper?.stopPreview()
            updateCaptureButtonState(false)
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun startCameraPreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
        } else {
            cameraCaptureHelper?.startPreview(activityScope)
        }
    }

    private fun getSensorOrientation(): Int {
        return try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.getOrNull(0) ?: return 90
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        } catch (e: Exception) {
            Log.e("SaveImage", "Failed to get sensor orientation, defaulting to 90", e)
            90
        }
    }

    private fun rotateImageBytes(imageBytes: ByteArray, degrees: Float): ByteArray {
        if (degrees == 0f) return imageBytes
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                val matrix = android.graphics.Matrix().apply {
                    postRotate(degrees)
                }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                val outputStream = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                val rotatedBytes = outputStream.toByteArray()

                if (rotatedBitmap != bitmap) {
                    rotatedBitmap.recycle()
                }
                bitmap.recycle()
                outputStream.close()
                rotatedBytes
            } else {
                imageBytes
            }
        } catch (e: Exception) {
            Log.e("SaveImage", "Failed to rotate image bytes", e)
            imageBytes
        }
    }

    private fun processAndSendImage(imageBytes: ByteArray) {
        lifecycleScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            val currentTime = timeFormat.format(Date())
            Log.d("ImageCapture", "Image processed and sending at: $currentTime")

            val sensorOrientation = getSensorOrientation()
            val processedBytes = if (sensorOrientation != 0) {
                rotateImageBytes(imageBytes, sensorOrientation.toFloat())
            } else {
                imageBytes
            }

            if (isExplicitCapture) {
                isExplicitCapture = false
                saveImageToGallery(processedBytes)
            }

            val currentLevel = performanceMonitor?.currentLevel ?: PerformanceMonitor.PerformanceLevel.MEDIUM

            val b64Image = if (currentLevel.dimension == 480) {
                Base64.encodeToString(processedBytes, Base64.DEFAULT or Base64.NO_WRAP)
            } else {
                val bitmap = BitmapFactory.decodeByteArray(processedBytes, 0, processedBytes.size)
                if (bitmap != null) {
                    val scaledBitmap = scaleBitmap(bitmap, currentLevel.dimension)
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, currentLevel.quality, byteArrayOutputStream)
                    val bytes = byteArrayOutputStream.toByteArray()

                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                    bitmap.recycle()
                    byteArrayOutputStream.close()
                    Base64.encodeToString(bytes, Base64.DEFAULT or Base64.NO_WRAP)
                } else {
                    Base64.encodeToString(processedBytes, Base64.DEFAULT or Base64.NO_WRAP)
                }
            }

            if (SessionStateHolder.state.value is SessionState.Active) {
                FloatingWidgetService.sendImageFrame(b64Image)
            } else {
                Log.d("ImageCapture", "Skipped sending image frame because session is not Active")
            }

            val duration = System.currentTimeMillis() - startTime
            performanceMonitor?.recordProcessingTime(duration)
            Log.i("PerformanceMonitor", "Processed image in ${duration}ms (Level: ${currentLevel.name})")
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            val ratio = width.toFloat() / maxDimension
            newWidth = maxDimension
            newHeight = (height / ratio).toInt()
        } else {
            val ratio = height.toFloat() / maxDimension
            newHeight = maxDimension
            newWidth = (width / ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PermissionHelper.REQUIRED_PERMISSIONS_REQUEST_CODE -> {
                val hasAudio = PermissionHelper.hasRecordAudioPermission(this)
                val hasCamera = PermissionHelper.hasCameraPermission(this)
                if (hasAudio && hasCamera) {
                    if (!FloatingWidgetService.isSessionConnected) {
                        if (PermissionHelper.hasOverlayPermission(this)) {
                            val key = appPrefs.apiKey
                            if (key.isNotEmpty()) {
                                displayMessage("SYSTEM: Connecting...")
                                FloatingWidgetService.connectSession(this, key)
                            }
                        } else {
                            PermissionHelper.requestOverlayPermission(this)
                            Toast.makeText(this, "Please grant Overlay permission to enable full service features", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Required permissions (Microphone/Camera) denied. Cannot connect session.", Toast.LENGTH_SHORT).show()
                }
            }
            STORAGE_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.toast_storage_permission_granted_click_photo_again), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Storage permission denied. Cannot save photo.", Toast.LENGTH_SHORT).show()
                }
            }
            CAMERA_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraPreview()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            EXTRA_PERMISSIONS_REQUEST_CODE -> {
                val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                Log.i("MainActivity", "Extra permissions result: location=$hasLocation, phone=$hasPhone")
            }
        }
    }



    private fun startWaveAnimation() {
        runOnUiThread {
            stopWaveAnimation()

            // Wave 1 Animation
            val scaleX1 = android.animation.ObjectAnimator.ofFloat(wave1, "scaleX", 1.0f, 1.5f)
            val scaleY1 = android.animation.ObjectAnimator.ofFloat(wave1, "scaleY", 1.0f, 1.5f)
            val alpha1 = android.animation.ObjectAnimator.ofFloat(wave1, "alpha", 0.7f, 0f)
            scaleX1.repeatCount = android.animation.ValueAnimator.INFINITE
            scaleY1.repeatCount = android.animation.ValueAnimator.INFINITE
            alpha1.repeatCount = android.animation.ValueAnimator.INFINITE

            wave1Animator = android.animation.AnimatorSet().apply {
                playTogether(scaleX1, scaleY1, alpha1)
                duration = 1600
                start()
            }

            // Wave 2 Animation (750ms Delay)
            val scaleX2 = android.animation.ObjectAnimator.ofFloat(wave2, "scaleX", 1.0f, 1.5f)
            val scaleY2 = android.animation.ObjectAnimator.ofFloat(wave2, "scaleY", 1.0f, 1.5f)
            val alpha2 = android.animation.ObjectAnimator.ofFloat(wave2, "alpha", 0.7f, 0f)
            scaleX2.repeatCount = android.animation.ValueAnimator.INFINITE
            scaleY2.repeatCount = android.animation.ValueAnimator.INFINITE
            alpha2.repeatCount = android.animation.ValueAnimator.INFINITE
            scaleX2.startDelay = 800
            scaleY2.startDelay = 800
            alpha2.startDelay = 800

            wave2Animator = android.animation.AnimatorSet().apply {
                playTogether(scaleX2, scaleY2, alpha2)
                duration = 1600
                start()
            }
        }
    }

    private fun stopWaveAnimation() {
        runOnUiThread {
            wave1Animator?.cancel()
            wave1Animator = null
            wave2Animator?.cancel()
            wave2Animator = null
            wave1.alpha = 0f
            wave1.scaleX = 1.0f
            wave1.scaleY = 1.0f
            wave2.alpha = 0f
            wave2.scaleX = 1.0f
            wave2.scaleY = 1.0f
        }
    }

    private fun displayMessage(message: String) {
        Log.d("Chat", "Displaying message: $message")
        runOnUiThread {
            val currentText = chatLog.text.toString()
            val lines = if (currentText.isEmpty()) mutableListOf() else currentText.split("\n").toMutableList()
            lines.add(message)
            if (lines.size > 50) {
                lines.removeAt(0)
            }
            chatLog.text = lines.joinToString("\n")

            logScrollView.post {
                logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun updateStatusIndicator(state: SessionState) {
        runOnUiThread {
            when (state) {
                is SessionState.Disconnected, is SessionState.Error -> {
                    if (cameraCaptureHelper?.isCameraActive == true) {
                        cameraCaptureHelper?.stopPreview()
                        updateCaptureButtonState(false)
                    }
                    stopWaveAnimation()

                    // Standby mic button coloring
                    btnMicCenter.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                        "#4D707070".toColorInt() // 30% alpha standby gray
                    ))
                    imgMicCenter.setImageResource(R.drawable.baseline_mic_off_24)
                    imgMicCenter.imageTintList = android.content.res.ColorStateList.valueOf(
                        "#80FFFFFF".toColorInt()
                    )
                }
                is SessionState.Connecting, is SessionState.Reconnecting -> {
                    stopWaveAnimation()
                    btnMicCenter.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                        "#FFCA28".toColorInt() // Amber for connecting/reconnecting
                    ))
                    imgMicCenter.setImageResource(R.drawable.baseline_mic_off_24)
                    imgMicCenter.imageTintList = android.content.res.ColorStateList.valueOf(
                        "#FFFFFF".toColorInt()
                    )
                }
                is SessionState.Standby -> {
                    stopWaveAnimation()
                    btnMicCenter.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                        "#4A90E2".toColorInt() // Connected blue
                    ))
                    imgMicCenter.setImageResource(R.drawable.baseline_mic_24)
                    imgMicCenter.imageTintList = android.content.res.ColorStateList.valueOf(
                        "#FFFFFF".toColorInt()
                    )
                }
                is SessionState.Active -> {
                    val isRecordingState = state.isRecording
                    if (isRecordingState) {
                        btnMicCenter.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                            "#E53935".toColorInt() // Active red
                        ))
                        imgMicCenter.setImageResource(R.drawable.baseline_mic_24)
                        imgMicCenter.imageTintList = android.content.res.ColorStateList.valueOf(
                            "#FFFFFF".toColorInt()
                        )
                        startWaveAnimation()
                    } else {
                        btnMicCenter.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(
                            "#4A90E2".toColorInt() // Connected blue
                        ))
                        imgMicCenter.setImageResource(R.drawable.baseline_mic_24)
                        imgMicCenter.imageTintList = android.content.res.ColorStateList.valueOf(
                            "#FFFFFF".toColorInt()
                        )
                        stopWaveAnimation()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()

        // Auto-start FloatingWidgetService if enabled in preferences but not running
        if (appPrefs.isFloatingWidgetEnabled && FloatingWidgetService.instance == null) {
            if (PermissionHelper.hasOverlayPermission(this)) {
                val serviceIntent = Intent(this, FloatingWidgetService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to auto-start FloatingWidgetService", e)
                }
            }
        }

        // Sync active skill ID from preferences
        val activeId = appPrefs.lastDatingSkillId
        val skills = datingSkillManager.getAllSkills()
        val activeSkillObj = skills.find { it.id == activeId }
        if (activeSkillObj != null) {
            com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId = activeSkillObj.id
        } else if (skills.isNotEmpty()) {
            com.example.geminimultimodalliveapi.session.SessionStateHolder.activeSkillId = skills[0].id
            appPrefs.lastDatingSkillId = skills[0].id
        }

        flowCollectJob = activityScope.launch {
            launch {
                SessionStateHolder.isDateAssistantModeActiveFlow.collect { isActive ->
                    if (switchDateMode.isChecked != isActive) {
                        switchDateMode.isChecked = isActive
                    }
                }
            }
            launch {
                SessionStateHolder.state.collect { state ->
                    isConnected = state != SessionState.Disconnected
                    MainActivity.isSessionConnected = isConnected
                    updateStatusIndicator(state)
                }
            }
            launch {
                SessionStateHolder.chatLogs.collect { logs ->
                    chatLog.text = logs
                    logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
                }
            }
            launch {
                com.example.geminimultimodalliveapi.session.SessionStateHolder.diagnostics.collect { diag ->
                    txtAttentionState.text = getString(R.string.label_attention, diag.attentionState)
                    txtActiveTopic.text = getString(R.string.label_topic, diag.activeTopic)
                    txtLastEvent.text = getString(R.string.label_event, diag.lastEvent)

                    // Color code attention status dynamically on the UI
                    val colorHex = when (diag.attentionState) {
                        "ACTIVE_SESSION" -> "#4CAF50" // Green
                        "BACKGROUND" -> "#2196F3"    // Blue
                        "LISTENING" -> "#FFCA28"     // Amber
                        else -> "#FFB0B0B0"          // Grey
                    }
                    txtAttentionState.setTextColor(colorHex.toColorInt())

                    // Update real-time Sensor Status Badges (btnMockDriving & btnMockHome)
                    val isMoving = diag.motionState == "WALKING" || diag.motionState == "DRIVING"
                    val movingColor = if (isMoving) "#9C27B0" else "#80555555" // Purple highlight or Gray dark
                    btnMockDriving.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        movingColor.toColorInt()
                    )
                    btnMockDriving.text = "Moving: ${diag.motionState}"

                    val isHomeOrStill = diag.motionState == "STILL" || diag.locationState.lowercase() == "home"
                    val homeStillColor = if (isHomeOrStill) "#9C27B0" else "#80555555" // Purple highlight or Gray dark
                    btnMockHome.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        homeStillColor.toColorInt()
                    )
                    btnMockHome.text = "Still/Home: ${if (diag.motionState == "STILL") "STILL" else diag.locationState}"
                }
            }
            launch {
                SessionStateHolder.errorFlow.collect { error ->
                    val thaiMessage = when (error) {
                        is AppError.Network -> getString(R.string.error_network)
                        is AppError.Permission -> getString(R.string.error_permission, error.type)
                        is AppError.AuthExpired -> getString(R.string.error_auth_expired)
                        is AppError.Api -> getString(R.string.error_api, error.message)
                        is AppError.Tool -> getString(R.string.error_tool, error.name, error.message)
                    }
                    if (error is AppError.AuthExpired) {
                        showReAuthDialog()
                    } else {
                        val rootView = findViewById<View>(android.R.id.content)
                        if (rootView != null) {
                            com.google.android.material.snackbar.Snackbar.make(
                                rootView,
                                thaiMessage,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this@MainActivity, thaiMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            launch {
                com.example.geminimultimodalliveapi.session.SessionStateHolder.dateInsights.collect { insight ->
                    updateDateInsightsUI(insight)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        flowCollectJob?.cancel()
        flowCollectJob = null
    }

    private fun handleTriggerConnectionIntent(intent: Intent?) {
        isConnected = FloatingWidgetService.isSessionConnected
        val trigger = intent?.getBooleanExtra("TRIGGER_CONNECTION", false) ?: false
        if (trigger) {
            val savedKey = appPrefs.apiKey
            if (savedKey.isNotEmpty()) {
                if (!isConnected) {
                    if (PermissionHelper.checkAndRequestPermissions(this)) {
                        displayMessage("SYSTEM: Triggered from floating widget. Connecting...")
                        FloatingWidgetService.connectSession(this, savedKey)
                    }
                } else {
                    displayMessage("SYSTEM: Triggered from floating widget. Already connected.")
                }
            } else {
                Toast.makeText(this, "API Key is missing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTriggerConnectionIntent(intent)
        handleCameraIntent(intent)
        handlePermissionIntent(intent)
    }

    private fun handlePermissionIntent(intent: Intent?) {
        val requestCall = intent?.getBooleanExtra("REQUEST_CALL_PERMISSION", false) ?: false
        val requestAnswer = intent?.getBooleanExtra("REQUEST_ANSWER_CALL_PERMISSION", false) ?: false
        val requestLocation = intent?.getBooleanExtra("REQUEST_LOCATION_PERMISSION", false) ?: false
        val requestNotification = intent?.getBooleanExtra("REQUEST_NOTIFICATION_PERMISSION", false) ?: false
        
        val permissionsToRequest = mutableListOf<String>()
        if (requestCall && ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }
        if (requestAnswer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (requestLocation) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                EXTRA_PERMISSIONS_REQUEST_CODE
            )
        } else if (requestCall || requestAnswer || requestLocation) {
            Toast.makeText(this, getString(R.string.toast_required_permissions_granted), Toast.LENGTH_SHORT).show()
        }

        if (requestNotification) {
            if (!isNotificationServiceEnabled()) {
                Toast.makeText(this, getString(R.string.toast_enable_notification_access), Toast.LENGTH_LONG).show()
                try {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.toast_open_settings_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.toast_notification_permission_granted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleCameraIntent(intent: Intent?) {
        isConnected = FloatingWidgetService.isSessionConnected
        val startCamera = intent?.getBooleanExtra("START_CAMERA", false) ?: false
        val stopCamera = intent?.getBooleanExtra("STOP_CAMERA", false) ?: false
        val callId = intent?.getStringExtra("CAMERA_CALL_ID")
        
        if (callId != null) {
            cameraCallId = callId
        }

        if (startCamera) {
            if (!isConnected) {
                Toast.makeText(this, getString(R.string.toast_connect_session_first_for_camera_open), Toast.LENGTH_SHORT).show()
                updateCaptureButtonState(false)
                cameraCallId?.let { cid ->
                    FloatingWidgetService.sendToolResponse(cid, false)
                    cameraCallId = null
                }
            } else if (cameraCaptureHelper?.isCameraActive == true) {
                // Camera already active, send success response immediately
                cameraCallId?.let { cid ->
                    Log.i("MainActivity", "Camera already active, responding success immediately to $cid")
                    FloatingWidgetService.sendToolResponse(cid, true)
                    cameraCallId = null
                }
            } else {
                updateCaptureButtonState(true)
                if (textureView.isAvailable) {
                    startCameraPreview()
                } else {
                    textureView.surfaceTextureListener = surfaceTextureListener
                }
            }
        }
        if (stopCamera) {
            if (cameraCaptureHelper?.isCameraActive == true) {
                cameraCaptureHelper?.stopPreview()
                updateCaptureButtonState(false)
            }
            cameraCallId?.let { cid ->
                Log.i("MainActivity", "Camera stopped, responding success to $cid")
                FloatingWidgetService.sendToolResponse(cid, true)
                cameraCallId = null
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (wasCameraActiveBeforeStop && isConnected) {
            updateCaptureButtonState(true)
            if (textureView.isAvailable) {
                startCameraPreview()
            } else {
                textureView.surfaceTextureListener = surfaceTextureListener
            }
        }
        wasCameraActiveBeforeStop = false
    }

    override fun onStop() {
        super.onStop()
        stopWaveAnimation()
        if (cameraCaptureHelper?.isCameraActive == true) {
            wasCameraActiveBeforeStop = true
            cameraCaptureHelper?.stopPreview()
            updateCaptureButtonState(false)
        } else {
            wasCameraActiveBeforeStop = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MainActivity.isCameraActive = false
        cameraCaptureHelper?.stopPreview()
        cameraCaptureHelper?.onDestroy()
        activityScope.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("wasCameraActiveBeforeStop", wasCameraActiveBeforeStop)
        outState.putBoolean("isExplicitCapture", isExplicitCapture)
        outState.putString("cameraCallId", cameraCallId)
        outState.putString("activeProfileName", SessionStateHolder.activeProfileName)
        outState.putString("chatLogs", SessionStateHolder.chatLogs.value)
        val diag = SessionStateHolder.diagnostics.value
        outState.putString("diag_attentionState", diag.attentionState)
        outState.putString("diag_activeTopic", diag.activeTopic)
        outState.putString("diag_lastEvent", diag.lastEvent)
        outState.putString("diag_motionState", diag.motionState)
        outState.putString("diag_locationState", diag.locationState)
    }

    private fun enableImmersiveFullscreen() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
    }

    private fun updateCaptureButtonState(isActive: Boolean) {
        MainActivity.isCameraActive = isActive
        if (isActive) {
            textureView.alpha = 1f
            btnMicCenter.visibility = View.GONE
            wave1.visibility = View.GONE
            wave2.visibility = View.GONE
            consoleCard.visibility = View.GONE
            dateInsightCard.visibility = View.GONE
            btnMenuMenu.visibility = View.GONE
            btnShutterSingle.visibility = View.VISIBLE

            imgShutterIcon.setImageResource(R.drawable.ic_shutter_24)
            imgShutterIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                "#E53935".toColorInt() // Shutter red!
            )
        } else {
            textureView.alpha = 0f
            btnMicCenter.visibility = View.VISIBLE
            wave1.visibility = View.VISIBLE
            wave2.visibility = View.VISIBLE
            
            if (switchDateMode.isChecked) {
                consoleCard.visibility = View.GONE
                dateInsightCard.visibility = View.VISIBLE
                btnShutterSingle.visibility = View.GONE
            } else {
                consoleCard.visibility = View.VISIBLE
                dateInsightCard.visibility = View.GONE
                btnShutterSingle.visibility = View.VISIBLE
            }
            
            btnMenuMenu.visibility = View.VISIBLE

            imgShutterIcon.setImageResource(R.drawable.baseline_camera_24)
            imgShutterIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                "#000000".toColorInt() // Black for camera trigger
            )
        }
    }



    private fun saveImageToGallery(imageBytes: ByteArray) {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val imageFileName = "GeminiLive_$timeStamp.jpg"

        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeminiLiveDemo")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        var uri: android.net.Uri? = null
        try {
            uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(imageBytes)
                    outputStream.flush()
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Log.i("SaveImage", "Image saved to gallery: $uri")
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_photo_saved_success), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SaveImage", "Failed to save image to gallery", e)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.toast_photo_save_failed), Toast.LENGTH_SHORT).show()
            }
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null)
                } catch (delEx: Exception) {
                    Log.e("SaveImage", "Failed to delete failed image entry", delEx)
                }
            }
        }
    }

    private fun setupProfileSpinner() {
        lifecycleScope.launch {
        val names = withContext(Dispatchers.IO) {
            val loaded = dateDbHelper.getAllProfileNames().toMutableList()
            if (loaded.isEmpty()) {
                loaded.add("คู่เดตทั่วไป")
                dateDbHelper.saveProfile("คู่เดตทั่วไป", emptyList(), emptyList(), emptyList())
            }
            loaded
        }
        names.add("+ เพิ่มโปรไฟล์ใหม่...")

        val adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProfile.adapter = adapter

        // Set selection
        val activeName = com.example.geminimultimodalliveapi.session.SessionStateHolder.activeProfileName
        if (activeName.isNotEmpty()) {
            val idx = names.indexOf(activeName)
            if (idx >= 0) {
                spinnerProfile.setSelection(idx)
            }
        } else {
            com.example.geminimultimodalliveapi.session.SessionStateHolder.activeProfileName = names[0]
            spinnerProfile.setSelection(0)
        }

        spinnerProfile.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = names[position]
                if (selected == "+ เพิ่มโปรไฟล์ใหม่...") {
                    showAddProfileDialog()
                } else {
                    com.example.geminimultimodalliveapi.session.SessionStateHolder.activeProfileName = selected
                    // Load existing insights from database for this profile into StateHolder
                    val profileInsight = dateDbHelper.getProfile(selected)
                    if (profileInsight != null) {
                        com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDateInsights {
                            profileInsight.copy(tip = "กำลังฟังบทสนทนากับ $selected...")
                        }
                    } else {
                        com.example.geminimultimodalliveapi.session.SessionStateHolder.updateDateInsights {
                            com.example.geminimultimodalliveapi.data.DateInsight(tip = "กำลังฟังบทสนทนากับ $selected...")
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    }



    private fun showAddProfileDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("เพิ่มโปรไฟล์คู่เดตใหม่")
        val input = android.widget.EditText(this)
        input.hint = "ชื่อโปรไฟล์คู่เดต"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("ตกลง") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty() && name != "+ เพิ่มโปรไฟล์ใหม่...") {
                dateDbHelper.saveProfile(name, emptyList(), emptyList(), emptyList())
                com.example.geminimultimodalliveapi.session.SessionStateHolder.activeProfileName = name
                setupProfileSpinner()
            } else {
                spinnerProfile.setSelection(0)
            }
        }
        builder.setNegativeButton("ยกเลิก") { dialog, _ ->
            dialog.cancel()
            spinnerProfile.setSelection(0)
        }
        builder.show()
    }

    private fun updateDateInsightsUI(insight: com.example.geminimultimodalliveapi.data.DateInsight) {
        runOnUiThread {
            // Update Likes Chips
            containerLikesChips.removeAllViews()
            for (like in insight.likes) {
                if (like.trim().isNotEmpty()) {
                    val chip = createChipView(like, "#E040FB", "#FFFFFF")
                    containerLikesChips.addView(chip)
                }
            }

            // Update Dislikes Chips
            containerDislikesChips.removeAllViews()
            for (dislike in insight.dislikes) {
                if (dislike.trim().isNotEmpty()) {
                    val chip = createChipView(dislike, "#FF5252", "#FFFFFF")
                    containerDislikesChips.addView(chip)
                }
            }

            // Update Engagement Level / Temp
            txtEngagementLabel.text = "VIBE TEMP: ${insight.engagementLevel}"
            val progressVal = when (insight.engagementLevel) {
                "Cold" -> 20
                "Warm" -> 60
                "Hot" -> 100
                else -> 50
            }
            progressEngagementTemp.progress = progressVal

            // Set progress tint color based on level
            val progressColor = when (insight.engagementLevel) {
                "Cold" -> "#2196F3"
                "Warm" -> "#FFEB3B"
                "Hot" -> "#FF4081"
                else -> "#FFE040FB"
            }
            progressEngagementTemp.progressTintList = android.content.res.ColorStateList.valueOf(
                progressColor.toColorInt()
            )

            // Set Agent Info
            val agentName = if (insight.activeAgentName.isNotEmpty()) insight.activeAgentName else ""
            val reasoning = if (insight.routerReasoning.isNotEmpty()) " • ${insight.routerReasoning}" else ""
            val text = "🤖 $agentName$reasoning"
            val cacheText = if (insight.isCached) " ⚡ [Cached]" else ""
            val fullText = "$text$cacheText"
            val spannable = android.text.SpannableString(fullText)
            if (insight.isCached) {
                val start = text.length
                val end = fullText.length
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#4CAF50")), // Premium green
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    end,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            txtAgentInfo.text = spannable

            // Set suggestions
            txtDatingTip.text = if (insight.tip.isNotEmpty()) insight.tip else "รอประมวลผลคำแนะนำที่นี่..."
        }
    }

    private fun createChipView(text: String, bgColorHex: String, textColorHex: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 10f
        tv.setTextColor(textColorHex.toColorInt())
        tv.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
        
        val shape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(bgColorHex.toColorInt())
        }
        tv.background = shape

        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dpToPx(4), 0, dpToPx(4), 0)
        }
        tv.layoutParams = params
        return tv
    }



    private fun checkNotificationAccessPermission() {
        if (!isNotificationServiceEnabled()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_notification_access_title))
                .setMessage(getString(R.string.dialog_notification_access_message))
                .setPositiveButton(getString(R.string.dialog_notification_access_positive)) { _, _ ->
                    try {
                        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.toast_open_settings_failed_general), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.dialog_notification_access_negative), null)
                .show()
        }
    }

}