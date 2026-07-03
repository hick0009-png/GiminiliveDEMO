package com.example.geminimultimodalliveapi

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.data.Meeting
import com.example.geminimultimodalliveapi.data.MeetingDbHelper
import com.example.geminimultimodalliveapi.data.TranscriptSegment
import com.example.geminimultimodalliveapi.network.GeminiMeetingService
import com.example.geminimultimodalliveapi.service.MeetingRecordingService
import com.example.geminimultimodalliveapi.session.MeetingRecordingStateHolder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
// removed unused serialization imports
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MeetingActivity : AppCompatActivity() {

    private val RECORD_AUDIO_REQUEST_CODE = 201

    private lateinit var appPrefs: AppPreferences
    private lateinit var meetingDbHelper: MeetingDbHelper
    private lateinit var audioManager: AudioManager

    // Main View UI
    private lateinit var editMeetingTitle: TextInputEditText
    private lateinit var switchAutoSilence: SwitchCompat
    private lateinit var switchLiveTranscript: SwitchCompat
    private lateinit var txtRecordTimer: TextView
    private lateinit var txtRecordStatus: TextView
    private lateinit var btnRecordMeeting: MaterialCardView
    private lateinit var imgRecordIcon: ImageView
    private lateinit var meetingWaveRing: View
    private lateinit var recyclerMeetings: RecyclerView
    private lateinit var mainMeetingLayout: LinearLayout
    private lateinit var txtHistoryHeader: TextView
    private lateinit var cardSearchMeetings: MaterialCardView
    private lateinit var layoutLiveTranscriptPanel: LinearLayout
    private lateinit var recyclerLiveTranscript: RecyclerView
    private lateinit var cardDeepgramStatus: MaterialCardView

    // Detail View UI
    private lateinit var detailMeetingLayout: LinearLayout
    private lateinit var txtDetailHeaderTitle: TextView
    private lateinit var btnShareMeeting: ImageButton
    private lateinit var txtDetailTitle: TextView
    private lateinit var txtDetailMeta: TextView
    private lateinit var btnPlayPauseAudio: MaterialCardView
    private lateinit var imgPlayPauseIcon: ImageView
    private lateinit var seekBarAudio: SeekBar
    private lateinit var txtCurrentPlayTime: TextView
    private lateinit var txtTotalPlayTime: TextView
    private lateinit var layoutProcessing: LinearLayout
    private lateinit var btnAnalyzeMeeting: Button
    private lateinit var layoutAnalysisResults: LinearLayout
    private lateinit var txtMeetingSummary: TextView
    private lateinit var btnRenameSpeaker: Button
    private lateinit var recyclerTranscript: RecyclerView

    // Adapters
    private lateinit var meetingsAdapter: MeetingsAdapter
    private val meetingsList = mutableListOf<Meeting>()
    private val allMeetingsList = mutableListOf<Meeting>()
    private var transcriptAdapter: TranscriptAdapter? = null
    private val transcriptList = mutableListOf<TranscriptSegment>()
    private lateinit var liveTranscriptAdapter: TranscriptAdapter
    private val liveTranscriptList = mutableListOf<TranscriptSegment>()

    // State
    private var isRecording = false
    private var currentMeetingId: String? = null
    private var currentFilePath: String? = null
    private var selectedMeeting: Meeting? = null
    private var originalRingerMode: Int? = null

    // Audio Playback
    private var mediaPlayer: MediaPlayer? = null
    private val playHandler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    private class SafePlayRunnable(activity: MeetingActivity) : Runnable {
        private val activityRef = java.lang.ref.WeakReference(activity)
        override fun run() {
            val act = activityRef.get() ?: return
            act.mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    act.seekBarAudio.progress = mp.currentPosition
                    act.txtCurrentPlayTime.text = act.formatTime(mp.currentPosition.toLong() / 1000)
                    act.playHandler.postDelayed(this, 200)
                }
            }
        }
    }

    private val playRunnable = SafePlayRunnable(this)
    private var searchWatcher: android.text.TextWatcher? = null

    private var waveAnimator: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meeting)

        if (savedInstanceState != null) {
            currentMeetingId = savedInstanceState.getString("currentMeetingId")
            currentFilePath = savedInstanceState.getString("currentFilePath")
            isPlaying = savedInstanceState.getBoolean("isPlaying", false)
        }

        appPrefs = AppPreferences.getInstance(this)
        meetingDbHelper = MeetingDbHelper.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        initViews()
        setupListeners()
        setupRecyclerViews()
        loadMeetings()
        observeRecordingState()
    }

    private fun initViews() {
        // Main view
        mainMeetingLayout = findViewById(R.id.mainMeetingLayout)
        editMeetingTitle = findViewById(R.id.editMeetingTitle)
        switchAutoSilence = findViewById(R.id.switchAutoSilence)
        switchLiveTranscript = findViewById(R.id.switchLiveTranscript)
        txtRecordTimer = findViewById(R.id.txtRecordTimer)
        txtRecordStatus = findViewById(R.id.txtRecordStatus)
        btnRecordMeeting = findViewById(R.id.btnRecordMeeting)
        imgRecordIcon = findViewById(R.id.imgRecordIcon)
        meetingWaveRing = findViewById(R.id.meetingWaveRing)
        recyclerMeetings = findViewById(R.id.recyclerMeetings)
        txtHistoryHeader = findViewById(R.id.txtHistoryHeader)
        cardSearchMeetings = findViewById(R.id.cardSearchMeetings)
        layoutLiveTranscriptPanel = findViewById(R.id.layoutLiveTranscriptPanel)
        recyclerLiveTranscript = findViewById(R.id.recyclerLiveTranscript)
        cardDeepgramStatus = findViewById(R.id.cardDeepgramStatus)

        // Detail view
        detailMeetingLayout = findViewById(R.id.detailMeetingLayout)
        txtDetailHeaderTitle = findViewById(R.id.txtDetailHeaderTitle)
        btnShareMeeting = findViewById(R.id.btnShareMeeting)
        txtDetailTitle = findViewById(R.id.txtDetailTitle)
        txtDetailMeta = findViewById(R.id.txtDetailMeta)
        btnPlayPauseAudio = findViewById(R.id.btnPlayPauseAudio)
        imgPlayPauseIcon = findViewById(R.id.imgPlayPauseIcon)
        seekBarAudio = findViewById(R.id.seekBarAudio)
        txtCurrentPlayTime = findViewById(R.id.txtCurrentPlayTime)
        txtTotalPlayTime = findViewById(R.id.txtTotalPlayTime)
        layoutProcessing = findViewById(R.id.layoutProcessing)
        btnAnalyzeMeeting = findViewById(R.id.btnAnalyzeMeeting)
        layoutAnalysisResults = findViewById(R.id.layoutAnalysisResults)
        txtMeetingSummary = findViewById(R.id.txtMeetingSummary)
        btnRenameSpeaker = findViewById(R.id.btnRenameSpeaker)
        recyclerTranscript = findViewById(R.id.recyclerTranscript)

        // Set default title
        val sdf = SimpleDateFormat("dd_MMM_yyyy_HHmm", Locale("th", "TH"))
        editMeetingTitle.setText("ประชุม_${sdf.format(Date())}")
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBackMeeting).setOnClickListener {
            finish()
        }

        btnRecordMeeting.setOnClickListener {
            toggleRecording()
        }

        findViewById<ImageButton>(R.id.btnCloseDetail).setOnClickListener {
            closeMeetingDetail()
        }

        btnPlayPauseAudio.setOnClickListener {
            toggleAudioPlayback()
        }

        btnAnalyzeMeeting.setOnClickListener {
            analyzeSelectedMeeting()
        }

        btnRenameSpeaker.setOnClickListener {
            showRenameSpeakerDialog()
        }

        btnShareMeeting.setOnClickListener {
            shareMeeting()
        }

        searchWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                filterMeetings(q)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        findViewById<EditText>(R.id.editSearchMeetings).addTextChangedListener(searchWatcher!!)

        seekBarAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    txtCurrentPlayTime.text = formatTime(progress.toLong() / 1000)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupRecyclerViews() {
        // Meetings List
        meetingsAdapter = MeetingsAdapter { meeting ->
            openMeetingDetail(meeting)
        }
        recyclerMeetings.layoutManager = LinearLayoutManager(this)
        recyclerMeetings.adapter = meetingsAdapter

        // Transcript list
        transcriptAdapter = TranscriptAdapter(transcriptList)
        recyclerTranscript.layoutManager = LinearLayoutManager(this)
        recyclerTranscript.adapter = transcriptAdapter

        // Live transcript list
        liveTranscriptAdapter = TranscriptAdapter(liveTranscriptList)
        recyclerLiveTranscript.layoutManager = LinearLayoutManager(this)
        recyclerLiveTranscript.adapter = liveTranscriptAdapter
    }

    private fun loadMeetings() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = meetingDbHelper.getMeetingList()
            runOnUiThread {
                allMeetingsList.clear()
                allMeetingsList.addAll(list)
                
                val query = findViewById<EditText>(R.id.editSearchMeetings)?.text?.toString() ?: ""
                filterMeetings(query)
            }
        }
    }

    private fun filterMeetings(query: String) {
        meetingsList.clear()
        if (query.trim().isEmpty()) {
            meetingsList.addAll(allMeetingsList)
        } else {
            val lowerQuery = query.lowercase(Locale.getDefault())
            for (meeting in allMeetingsList) {
                val titleMatch = meeting.title.lowercase(Locale.getDefault()).contains(lowerQuery)
                val summaryMatch = meeting.summary?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true
                if (titleMatch || summaryMatch) {
                    meetingsList.add(meeting)
                }
            }
        }
        meetingsAdapter.notifyDataSetChanged()
    }

    private fun observeRecordingState() {
        lifecycleScope.launch {
            MeetingRecordingStateHolder.isRecording.collect { recording ->
                isRecording = recording
                updateRecordingUI(recording)
            }
        }

        lifecycleScope.launch {
            MeetingRecordingStateHolder.secondsElapsed.collect { seconds ->
                if (isRecording) {
                    txtRecordTimer.text = formatTime(seconds)
                }
            }
        }

        lifecycleScope.launch {
            MeetingRecordingStateHolder.isLiveTranscript.collect { isLive ->
                switchLiveTranscript.isChecked = isLive
            }
        }

        lifecycleScope.launch {
            MeetingRecordingStateHolder.liveTranscript.collect { liveList ->
                if (isRecording && switchLiveTranscript.isChecked) {
                    // Update UI state to live transcribing
                    txtHistoryHeader.visibility = View.GONE
                    cardSearchMeetings.visibility = View.GONE
                    recyclerMeetings.visibility = View.GONE
                    layoutLiveTranscriptPanel.visibility = View.VISIBLE
                    switchLiveTranscript.isEnabled = false
                    editMeetingTitle.isEnabled = false

                    updateLiveTranscriptUI(liveList)
                }
            }
        }
    }

    private fun updateLiveTranscriptUI(newList: List<TranscriptSegment>) {
        if (newList.isEmpty()) {
            liveTranscriptList.clear()
            liveTranscriptAdapter.notifyDataSetChanged()
            return
        }

        val oldSize = liveTranscriptList.size
        val newSize = newList.size

        if (oldSize == newSize) {
            val lastIdx = oldSize - 1
            if (liveTranscriptList[lastIdx] != newList[lastIdx]) {
                liveTranscriptList[lastIdx] = newList[lastIdx]
                liveTranscriptAdapter.notifyItemChanged(lastIdx)
            }
        } else if (newSize > oldSize) {
            val lastIdx = oldSize - 1
            if (lastIdx >= 0 && liveTranscriptList[lastIdx] != newList[lastIdx]) {
                liveTranscriptList[lastIdx] = newList[lastIdx]
                liveTranscriptAdapter.notifyItemChanged(lastIdx)
            }
            for (i in oldSize until newSize) {
                liveTranscriptList.add(newList[i])
                liveTranscriptAdapter.notifyItemInserted(i)
            }
        } else {
            liveTranscriptList.clear()
            liveTranscriptList.addAll(newList)
            liveTranscriptAdapter.notifyDataSetChanged()
        }

        if (liveTranscriptList.isNotEmpty()) {
            recyclerLiveTranscript.scrollToPosition(liveTranscriptList.size - 1)
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            if (checkAudioPermission()) {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        val title = editMeetingTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "กรุณากรอกชื่อการประชุม", Toast.LENGTH_SHORT).show()
            return
        }

        // Disconnect Gemini Live if active to free up the microphone
        if (FloatingWidgetService.isSessionConnected) {
            Log.i("MeetingActivity", "Gemini Live session is connected. Disconnecting to release mic...")
            FloatingWidgetService.disconnectSession(this)
        }

        val meetingId = UUID.randomUUID().toString()
        currentMeetingId = meetingId
        val audioFile = File(cacheDir, "meeting_$meetingId.m4a")
        currentFilePath = audioFile.absolutePath

        // Save initial DB draft
        val newMeeting = Meeting(
            id = meetingId,
            title = title,
            timestamp = System.currentTimeMillis(),
            duration = 0,
            filePath = audioFile.absolutePath,
            summary = null,
            transcriptJson = null
        )
        meetingDbHelper.insertMeeting(newMeeting)

        // Silent mode if enabled
        if (switchAutoSilence.isChecked) {
            try {
                originalRingerMode = audioManager.ringerMode
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                Log.d("MeetingActivity", "Automatic Silence Mode enabled: set to SILENT")
            } catch (e: SecurityException) {
                Log.e("MeetingActivity", "Cannot toggle silent mode: ACCESS_NOTIFICATION_POLICY required", e)
            }
        }

        // Start service
        val intent = Intent(this, MeetingRecordingService::class.java).apply {
            action = MeetingRecordingService.ACTION_START
            putExtra(MeetingRecordingService.EXTRA_FILE_PATH, audioFile.absolutePath)
            putExtra(MeetingRecordingService.EXTRA_LIVE_TRANSCRIPT, switchLiveTranscript.isChecked)
            putExtra(MeetingRecordingService.EXTRA_MEETING_ID, meetingId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        if (switchLiveTranscript.isChecked) {
            txtHistoryHeader.visibility = View.GONE
            cardSearchMeetings.visibility = View.GONE
            recyclerMeetings.visibility = View.GONE
            layoutLiveTranscriptPanel.visibility = View.VISIBLE
            switchLiveTranscript.isEnabled = false
            editMeetingTitle.isEnabled = false
            liveTranscriptList.clear()
            liveTranscriptAdapter.notifyDataSetChanged()
            cardDeepgramStatus.visibility = View.VISIBLE
            startDeepgramPulse()
        }

        txtRecordTimer.visibility = View.VISIBLE
        txtRecordTimer.text = "00:00:00"
        txtRecordStatus.text = "กำลังบันทึกเสียงการประชุม..."
        imgRecordIcon.setImageResource(R.drawable.baseline_mic_off_24)
        startPulsingAnimation()
    }

    private fun stopRecording() {
        // Stop service
        val intent = Intent(this, MeetingRecordingService::class.java).apply {
            action = MeetingRecordingService.ACTION_STOP
        }
        startService(intent)

        // Restore Ringer Mode
        if (originalRingerMode != null) {
            try {
                audioManager.ringerMode = originalRingerMode!!
                originalRingerMode = null
                Log.d("MeetingActivity", "Restored original ringer mode")
            } catch (e: Exception) {
                Log.e("MeetingActivity", "Error restoring ringer mode", e)
            }
        }

        // Update Duration in DB
        val seconds = MeetingRecordingStateHolder.secondsElapsed.value
        currentMeetingId?.let { id ->
            val list = meetingDbHelper.getMeetingList()
            val savedMeeting = list.firstOrNull { it.id == id }
            if (savedMeeting != null) {
                val updatedMeeting = Meeting(
                    id = savedMeeting.id,
                    title = savedMeeting.title,
                    timestamp = savedMeeting.timestamp,
                    duration = seconds,
                    filePath = savedMeeting.filePath,
                    summary = savedMeeting.summary,
                    transcriptJson = savedMeeting.transcriptJson
                )
                meetingDbHelper.insertMeeting(updatedMeeting)
            }
        }

        txtRecordTimer.visibility = View.GONE
        txtRecordStatus.text = "หยุดการบันทึกแล้ว"
        imgRecordIcon.setImageResource(R.drawable.baseline_mic_24)
        stopPulsingAnimation()
        cardDeepgramStatus.visibility = View.GONE
        stopDeepgramPulse()

        // Refresh Title
        val sdf = SimpleDateFormat("dd_MMM_yyyy_HHmm", Locale("th", "TH"))
        editMeetingTitle.setText("ประชุม_${sdf.format(Date())}")

        loadMeetings()
    }

    private fun updateRecordingUI(recording: Boolean) {
        if (recording) {
            txtRecordTimer.visibility = View.VISIBLE
            imgRecordIcon.setImageResource(R.drawable.baseline_mic_off_24)
            startPulsingAnimation()
            if (switchLiveTranscript.isChecked) {
                txtHistoryHeader.visibility = View.GONE
                cardSearchMeetings.visibility = View.GONE
                recyclerMeetings.visibility = View.GONE
                layoutLiveTranscriptPanel.visibility = View.VISIBLE
                switchLiveTranscript.isEnabled = false
                editMeetingTitle.isEnabled = false
                cardDeepgramStatus.visibility = View.VISIBLE
                startDeepgramPulse()
            } else {
                cardDeepgramStatus.visibility = View.GONE
                stopDeepgramPulse()
            }
        } else {
            txtRecordTimer.visibility = View.GONE
            imgRecordIcon.setImageResource(R.drawable.baseline_mic_24)
            stopPulsingAnimation()
            txtHistoryHeader.visibility = View.VISIBLE
            cardSearchMeetings.visibility = View.VISIBLE
            recyclerMeetings.visibility = View.VISIBLE
            layoutLiveTranscriptPanel.visibility = View.GONE
            switchLiveTranscript.isEnabled = true
            editMeetingTitle.isEnabled = true
            cardDeepgramStatus.visibility = View.GONE
            stopDeepgramPulse()
        }
    }

    private fun startPulsingAnimation() {
        meetingWaveRing.visibility = View.VISIBLE
        val scaleX = ObjectAnimator.ofFloat(meetingWaveRing, "scaleX", 1.0f, 1.4f)
        val scaleY = ObjectAnimator.ofFloat(meetingWaveRing, "scaleY", 1.0f, 1.4f)
        val alpha = ObjectAnimator.ofFloat(meetingWaveRing, "alpha", 0.6f, 0f)

        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatCount = ValueAnimator.INFINITE
        alpha.repeatCount = ValueAnimator.INFINITE

        waveAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1200
            start()
        }
    }

    private fun stopPulsingAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        meetingWaveRing.alpha = 0f
        meetingWaveRing.scaleX = 1.0f
        meetingWaveRing.scaleY = 1.0f
        meetingWaveRing.visibility = View.GONE
    }

    private var deepgramPulseAnimator: ObjectAnimator? = null

    private fun startDeepgramPulse() {
        val dotDeepgram = findViewById<View>(R.id.dotDeepgram) ?: return
        deepgramPulseAnimator = ObjectAnimator.ofFloat(dotDeepgram, "alpha", 1.0f, 0.3f, 1.0f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopDeepgramPulse() {
        deepgramPulseAnimator?.cancel()
        deepgramPulseAnimator = null
        findViewById<View>(R.id.dotDeepgram)?.alpha = 1.0f
    }

    // Detail View Actions
    private fun openMeetingDetail(meeting: Meeting) {
        selectedMeeting = meeting
        mainMeetingLayout.visibility = View.GONE
        detailMeetingLayout.visibility = View.VISIBLE

        txtDetailHeaderTitle.text = meeting.title
        txtDetailTitle.text = meeting.title

        val sdf = SimpleDateFormat("d MMM yyyy, HH:mm น.", Locale("th", "TH"))
        val dateStr = sdf.format(Date(meeting.timestamp))
        txtDetailMeta.text = "$dateStr | ความยาว ${formatTime(meeting.duration)}"

        // Reset player UI
        isPlaying = false
        imgPlayPauseIcon.setImageResource(R.drawable.baseline_camera_24) // Wait, baseline_camera_24 is used for playing? Wait, what? Let's check drawables. Oh, let's see. In activity_meeting.xml we used baseline_camera_24 as play, but wait, play icon is usually play, and we have standard drawables.
        // Actually, we don't have play icon, but wait! We can use camera icon, or is there another one?
        // Wait, let's check baseline_camera_24 vs baseline_mic_24. We can use R.drawable.baseline_camera_24 for playing? Let's see if there is any play drawable.
        // Let's check what drawables we listed earlier:
        // app_logo, baseline_camera_24, baseline_error_24, baseline_mic_24, baseline_mic_off_24, floating_widget_bg, etc.
        // There is no play icon! So R.drawable.baseline_camera_24 was probably used, or we can use another drawable. Wait! We can use standard Android system drawables like `android.R.drawable.ic_media_play` and `android.R.drawable.ic_media_pause`!
        // Yes! Android system drawables are always available and look standard.
        // Let's check: android.R.drawable.ic_media_play and android.R.drawable.ic_media_pause are standard system resource drawables. Let's use them!
        imgPlayPauseIcon.setImageResource(android.R.drawable.ic_media_play)

        seekBarAudio.progress = 0
        txtCurrentPlayTime.text = "00:00"
        txtTotalPlayTime.text = formatTime(meeting.duration).substring(3) // hh:mm:ss to mm:ss

        // Release previous player
        releaseMediaPlayer()

        // Handle transcription/summary display
        if (meeting.summary.isNullOrEmpty()) {
            btnAnalyzeMeeting.text = "วิเคราะห์การประชุมด้วย Gemini"
            btnAnalyzeMeeting.visibility = View.VISIBLE
            layoutAnalysisResults.visibility = View.GONE
            layoutProcessing.visibility = View.GONE
        } else {
            btnAnalyzeMeeting.text = "วิเคราะห์การประชุมอีกครั้ง"
            btnAnalyzeMeeting.visibility = View.VISIBLE
            layoutAnalysisResults.visibility = View.VISIBLE
            layoutProcessing.visibility = View.GONE

            txtMeetingSummary.text = meeting.summary
            displayTranscript(meeting.transcriptJson)
        }
    }

    private fun closeMeetingDetail() {
        releaseMediaPlayer()
        detailMeetingLayout.visibility = View.GONE
        mainMeetingLayout.visibility = View.VISIBLE
        loadMeetings()
    }

    // Audio Playback Logic
    private fun toggleAudioPlayback() {
        val meeting = selectedMeeting ?: return
        val audioFile = File(meeting.filePath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "ไม่พบไฟล์เสียงในเครื่อง", Toast.LENGTH_SHORT).show()
            return
        }

        if (isPlaying) {
            pauseAudio()
        } else {
            playAudio(audioFile.absolutePath)
        }
    }

    private fun playAudio(filePath: String) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(filePath)
                    setOnPreparedListener { mp ->
                        seekBarAudio.max = mp.duration
                        txtTotalPlayTime.text = formatTime(mp.duration.toLong() / 1000).substring(3)
                        mp.start()
                        this@MeetingActivity.isPlaying = true
                        imgPlayPauseIcon.setImageResource(android.R.drawable.ic_media_pause)
                        playHandler.post(playRunnable)
                    }
                    setOnCompletionListener {
                        this@MeetingActivity.isPlaying = false
                        imgPlayPauseIcon.setImageResource(android.R.drawable.ic_media_play)
                        seekBarAudio.progress = 0
                        txtCurrentPlayTime.text = "00:00"
                        playHandler.removeCallbacks(playRunnable)
                    }
                    prepareAsync()
                } catch (e: Exception) {
                    Log.e("MeetingActivity", "Error preparing MediaPlayer", e)
                    Toast.makeText(this@MeetingActivity, "ไม่สามารถเล่นไฟล์เสียงนี้ได้", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            return
        }

        mediaPlayer?.start()
        isPlaying = true
        imgPlayPauseIcon.setImageResource(android.R.drawable.ic_media_pause)
        playHandler.post(playRunnable)
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        imgPlayPauseIcon.setImageResource(android.R.drawable.ic_media_play)
        playHandler.removeCallbacks(playRunnable)
    }

    private fun releaseMediaPlayer() {
        playHandler.removeCallbacks(playRunnable)
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    // Gemini API Analysis
    private fun analyzeSelectedMeeting() {
        val meeting = selectedMeeting ?: return
        val apiKey = appPrefs.apiKey
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "กรุณากรอก API Key ในเมนูตั้งค่าก่อน", Toast.LENGTH_SHORT).show()
            return
        }

        val audioFile = File(meeting.filePath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "ไม่พบไฟล์เสียงการประชุมสำหรับการวิเคราะห์", Toast.LENGTH_SHORT).show()
            return
        }

        btnAnalyzeMeeting.visibility = View.GONE
        layoutProcessing.visibility = View.VISIBLE
        layoutAnalysisResults.visibility = View.GONE

        GeminiMeetingService.analyzeMeeting(apiKey, audioFile, meeting.duration, object : GeminiMeetingService.Callback {
            override fun onSuccess(summary: String, transcriptJson: String) {
                runOnUiThread {
                    if (selectedMeeting?.id == meeting.id) {
                        // Save to Database
                        meetingDbHelper.updateMeetingTranscriptAndSummary(meeting.id, transcriptJson, summary)
                        
                        // Update UI
                        selectedMeeting = meetingDbHelper.getMeetingList().firstOrNull { it.id == meeting.id }
                        layoutProcessing.visibility = View.GONE
                        layoutAnalysisResults.visibility = View.VISIBLE
                        txtMeetingSummary.text = summary
                        displayTranscript(transcriptJson)
                        Toast.makeText(this@MeetingActivity, "วิเคราะห์การประชุมสำเร็จ!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    if (selectedMeeting?.id == meeting.id) {
                        layoutProcessing.visibility = View.GONE
                        btnAnalyzeMeeting.visibility = View.VISIBLE
                        if (!meeting.summary.isNullOrEmpty()) {
                            layoutAnalysisResults.visibility = View.VISIBLE
                        }
                        Toast.makeText(this@MeetingActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun displayTranscript(transcriptJson: String?) {
        transcriptList.clear()
        if (!transcriptJson.isNullOrEmpty()) {
            try {
                val array = org.json.JSONArray(transcriptJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val speaker = obj.optString("speaker", "")
                    val text = obj.optString("text", "")
                    transcriptList.add(TranscriptSegment(speaker, text))
                }
            } catch (e: Exception) {
                Log.e("MeetingActivity", "Error decoding transcript JSON manually", e)
            }
        }
        transcriptAdapter?.notifyDataSetChanged()
    }

    // Speaker Rename Dialog
    private fun showRenameSpeakerDialog() {
        val meeting = selectedMeeting ?: return
        if (meeting.transcriptJson.isNullOrEmpty()) {
            Toast.makeText(this, "ต้องทำการวิเคราะห์การประชุมก่อนจึงจะสามารถแก้ไขชื่อผู้พูดได้", Toast.LENGTH_SHORT).show()
            return
        }

        // Extract distinct speaker names currently in the list
        val distinctSpeakers = transcriptList.map { it.speaker }.distinct()
        if (distinctSpeakers.isEmpty()) return

        val builder = AlertDialog.Builder(this)
        builder.setTitle("แก้ไขชื่อผู้พูด")

        // Actually, let's build a simple view programmatically to avoid xml dependency conflicts! It is extremely clean and stable.
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(16)
            setPadding(pad, pad, pad, pad)
        }

        val labelSpinner = TextView(this).apply {
            text = "เลือกชื่อผู้พูดที่ต้องการเปลี่ยน:"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 14f
            setPadding(0, 0, 0, dpToPx(8))
        }
        layout.addView(labelSpinner)

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, distinctSpeakers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        layout.addView(spinner)

        val labelInput = TextView(this).apply {
            text = "พิมพ์ชื่อใหม่:"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 14f
            setPadding(0, dpToPx(16), 0, dpToPx(8))
        }
        layout.addView(labelInput)

        val input = EditText(this).apply {
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            hint = "เช่น สมพงษ์, ประธาน"
            setHintTextColor(0x80FFFFFF.toInt())
        }
        layout.addView(input)

        builder.setView(layout)

        builder.setPositiveButton("บันทึก") { _, _ ->
            val oldName = spinner.selectedItem as? String ?: return@setPositiveButton
            val newName = input.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "กรุณากรอกชื่อใหม่", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val success = meetingDbHelper.updateSpeakerName(meeting.id, oldName, newName)
            if (success) {
                // Reload meeting
                selectedMeeting = meetingDbHelper.getMeetingList().firstOrNull { it.id == meeting.id }
                selectedMeeting?.let {
                    displayTranscript(it.transcriptJson)
                }
                Toast.makeText(this, "แก้ไขชื่อผู้พูดสำเร็จ", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ไม่สามารถแก้ไขชื่อได้", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("ยกเลิก", null)
        
        val dialog = builder.create()
        if (!isFinishing && !isDestroyed) {
            dialog.show()
        }
    }

    // Share Meeting Transcript & Summary
    private fun shareMeeting() {
        val meeting = selectedMeeting ?: return
        
        val shareBody = StringBuilder().apply {
            append("หัวข้อการประชุม: ${meeting.title}\n")
            val sdf = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("th", "TH"))
            append("วันที่: ${sdf.format(Date(meeting.timestamp))}\n")
            append("ความยาว: ${formatTime(meeting.duration)}\n\n")

            if (!meeting.summary.isNullOrEmpty()) {
                append("=== สรุปผลการประชุม ===\n")
                append("${meeting.summary}\n\n")
            }

            if (transcriptList.isNotEmpty()) {
                append("=== บทสนทนาการประชุม ===\n")
                for (segment in transcriptList) {
                    append("${segment.speaker}: ${segment.text}\n")
                }
            }
        }.toString()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "สรุปการประชุม - ${meeting.title}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(intent, "แชร์ข้อมูลการประชุม"))
    }

    // Helper Utils
    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun checkAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                Toast.makeText(this, "ต้องการสิทธิ์เข้าถึงไมโครโฟนเพื่ออัดเสียง", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            pauseAudio()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playHandler.removeCallbacks(playRunnable)
        searchWatcher?.let {
            findViewById<EditText>(R.id.editSearchMeetings)?.removeTextChangedListener(it)
        }
        searchWatcher = null
        releaseMediaPlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentMeetingId", currentMeetingId)
        outState.putString("currentFilePath", currentFilePath)
        outState.putBoolean("isPlaying", isPlaying)
    }

    // Adapter for Meetings List
    inner class MeetingsAdapter(private val onItemClick: (Meeting) -> Unit) :
        RecyclerView.Adapter<MeetingsAdapter.MeetingViewHolder>() {

        inner class MeetingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.txtMeetingTitle)
            val date: TextView = view.findViewById(R.id.txtMeetingDate)
            val duration: TextView = view.findViewById(R.id.txtMeetingDuration)
            val status: TextView = view.findViewById(R.id.txtMeetingStatus)
            val deleteBtn: ImageButton = view.findViewById(R.id.btnDeleteMeeting)
            val card: MaterialCardView = view.findViewById(R.id.cardMeeting)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeetingViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meeting, parent, false)
            return MeetingViewHolder(view)
        }

        override fun onBindViewHolder(holder: MeetingViewHolder, position: Int) {
            val meeting = meetingsList[position]
            holder.title.text = meeting.title

            val sdf = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("th", "TH"))
            holder.date.text = sdf.format(Date(meeting.timestamp))

            // Duration format
            val sec = meeting.duration
            val m = sec / 60
            val s = sec % 60
            holder.duration.text = String.format("%02d:%02d นาที", m, s)

            // Status chip
            if (meeting.summary.isNullOrEmpty()) {
                holder.status.text = "ยังไม่ได้ประมวลผล"
                holder.status.setTextColor(0xFFD32F2F.toInt()) // Red
                holder.status.setBackgroundResource(R.drawable.floating_widget_bg) // simple bg
            } else {
                holder.status.text = "วิเคราะห์เสร็จสิ้น"
                holder.status.setTextColor(0xFF388E3C.toInt()) // Green
                holder.status.setBackgroundResource(R.drawable.floating_widget_bg)
            }

            holder.card.setOnClickListener {
                onItemClick(meeting)
            }

            holder.deleteBtn.setOnClickListener {
                if (isFinishing || isDestroyed) return@setOnClickListener
                AlertDialog.Builder(this@MeetingActivity)
                    .setTitle("ลบการประชุม")
                    .setMessage("คุณต้องการลบการประชุมนี้และไฟล์เสียงที่บันทึกไว้ใช่หรือไม่?")
                    .setPositiveButton("ลบ") { _, _ ->
                        meetingDbHelper.deleteMeeting(meeting.id)
                        loadMeetings()
                        Toast.makeText(this@MeetingActivity, "ลบสำเร็จ", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("ยกเลิก", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = meetingsList.size
    }

    // Adapter for Transcript Bubble segments
    inner class TranscriptAdapter(private val list: List<TranscriptSegment>) : RecyclerView.Adapter<TranscriptAdapter.TranscriptViewHolder>() {

        inner class TranscriptViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val speaker: TextView = view.findViewById(R.id.txtSpeaker)
            val speechText: TextView = view.findViewById(R.id.txtSpeechText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranscriptViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transcript_segment, parent, false)
            return TranscriptViewHolder(view)
        }

        override fun onBindViewHolder(holder: TranscriptViewHolder, position: Int) {
            val segment = list[position]
            holder.speaker.text = segment.speaker
            holder.speechText.text = segment.text

            // Dynamic color tag based on speaker name string hash code
            val colors = arrayOf(
                "#4A90E2", // Blue
                "#388E3C", // Green
                "#F57C00", // Orange
                "#7B1FA2", // Purple
                "#D32F2F", // Red
                "#0097A7"  // Teal
            )
            val index = Math.abs(segment.speaker.hashCode()) % colors.size
            holder.speaker.setTextColor(android.graphics.Color.parseColor(colors[index]))
        }

        override fun getItemCount(): Int = list.size
    }
}
