package com.example.geminimultimodalliveapi.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.geminimultimodalliveapi.MainActivity
import com.example.geminimultimodalliveapi.R
import com.example.geminimultimodalliveapi.data.AppPreferences
import com.example.geminimultimodalliveapi.data.MeetingDbHelper
import com.example.geminimultimodalliveapi.data.TranscriptSegment
import com.example.geminimultimodalliveapi.network.DeepgramLiveClient
import com.example.geminimultimodalliveapi.session.MeetingRecordingStateHolder
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MeetingRecordingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_RECORDING"
        const val EXTRA_FILE_PATH = "EXTRA_FILE_PATH"
        const val EXTRA_LIVE_TRANSCRIPT = "EXTRA_LIVE_TRANSCRIPT"
        const val EXTRA_MEETING_ID = "EXTRA_MEETING_ID"
        const val NOTIFICATION_ID = 991
    }

    private var mediaRecorder: MediaRecorder? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var isRecording = false

    // Live Transcribing States
    private var isLiveTranscript = false
    private var deepgramClient: DeepgramLiveClient? = null
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var fileOutputStream: FileOutputStream? = null
    private var currentFilePath: String? = null
    private var originalFilePath: String? = null
    private var currentMeetingId: String? = null
    private var rawAudioBytesWritten = 0L

    private val committedSegments = mutableListOf<TranscriptSegment>()
    private var interimSegment: TranscriptSegment? = null

    // High-priority dedicated audio thread resources
    private val audioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "meeting-record-thread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()
    private var audioChannel: kotlinx.coroutines.channels.Channel<ByteArray>? = null
    private var audioPipelineJob: Job? = null

    private val deepgramCallback = object : DeepgramLiveClient.Callback {
        override fun onTranscriptReceived(
            transcript: String,
            speakerId: Int,
            isFinal: Boolean,
            wordDetails: List<DeepgramLiveClient.WordDetail>
        ) {
            val speakerName = "Speaker $speakerId"
            if (isFinal) {
                // Group speaker segments
                if (committedSegments.isNotEmpty() && committedSegments.last().speaker == speakerName) {
                    val lastSeg = committedSegments.last()
                    val space = if (lastSeg.text.endsWith(" ")) "" else " "
                    committedSegments[committedSegments.size - 1] = TranscriptSegment(
                        speaker = speakerName,
                        text = lastSeg.text + space + transcript
                    )
                } else {
                    committedSegments.add(TranscriptSegment(speakerName, transcript))
                }
                interimSegment = null

                // Persist committed segments directly to database
                currentMeetingId?.let { id ->
                    val jsonStr = serializeSegments(committedSegments)
                    serviceScope.launch(Dispatchers.IO) {
                        MeetingDbHelper.getInstance(applicationContext).updateMeetingTranscript(id, jsonStr)
                    }
                }
            } else {
                interimSegment = TranscriptSegment(speakerName, transcript)
            }

            // Update live UI flow
            val currentList = if (interimSegment != null) {
                committedSegments + interimSegment!!
            } else {
                committedSegments
            }
            MeetingRecordingStateHolder.updateLiveTranscript(currentList)
        }

        override fun onUtteranceEnd() {}

        override fun onOpen() {
            Log.i("MeetingRecordingService", "Deepgram live session connected successfully.")
        }

        override fun onClose() {
            Log.i("MeetingRecordingService", "Deepgram live session closed.")
        }

        override fun onError(t: Throwable, responseCode: Int?) {
            Log.e("MeetingRecordingService", "Deepgram live session error: code=$responseCode", t)
            val seconds = MeetingRecordingStateHolder.secondsElapsed.value
            if (seconds < 5) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        "การเชื่อมต่อ Deepgram ล้มเหลว เปลี่ยนไปบันทึกออฟไลน์ (.m4a)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                switchToOfflineFallback()
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        "การเชื่อมต่อขาดหาย จะดำเนินบันทึกออฟไลน์ต่อ (.wav)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                deepgramClient = null // Stop sending audio but keep writing raw PCM to WAV locally
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("MeetingRecordingService", "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val isLive = intent.getBooleanExtra(EXTRA_LIVE_TRANSCRIPT, false)
                val meetingId = intent.getStringExtra(EXTRA_MEETING_ID)
                
                if (filePath != null && meetingId != null) {
                    startRecording(filePath, isLive, meetingId)
                } else {
                    Log.e("MeetingRecordingService", "Required extras (filePath, meetingId) are missing!")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(filePath: String, isLive: Boolean, meetingId: String) {
        if (isRecording) return

        isLiveTranscript = isLive
        currentMeetingId = meetingId
        originalFilePath = filePath
        committedSegments.clear()
        interimSegment = null

        // 1. Start Foreground immediately
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("MeetingRecordingService", "Failed to start foreground service", e)
            stopSelf()
            return
        }

        MeetingRecordingStateHolder.updateLiveTranscriptState(isLiveTranscript)
        if (isLiveTranscript) {
            val appPrefs = AppPreferences.getInstance(this)
            val deepgramKey = appPrefs.deepgramApiKey
            if (deepgramKey.isEmpty()) {
                Log.w("MeetingRecordingService", "Deepgram key is empty. Falling back to offline .m4a")
                isLiveTranscript = false
                MeetingRecordingStateHolder.updateLiveTranscriptState(false)
                startOfflineRecording(filePath)
            } else {
                startLiveRecording(filePath, deepgramKey, appPrefs.wakeWord)
            }
        } else {
            startOfflineRecording(filePath)
        }
    }

    private fun startOfflineRecording(filePath: String) {
        try {
            currentFilePath = filePath
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(filePath)
                prepare()
                start()
            }

            isRecording = true
            MeetingRecordingStateHolder.updateRecordingState(true)
            startTimer()
            Log.i("MeetingRecordingService", "Started offline recording into file: $filePath")
        } catch (e: Exception) {
            Log.e("MeetingRecordingService", "Failed to start offline recording", e)
            showErrorToast(e.localizedMessage)
            isRecording = false
            MeetingRecordingStateHolder.updateRecordingState(false)
            stopForeground(true)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveRecording(filePath: String, deepgramKey: String, wakeWord: String) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                // Convert output filepath from .m4a to .wav on IO thread
                val wavPath = withContext(Dispatchers.IO) {
                    val originalFile = File(filePath)
                    val parentDir = originalFile.parentFile
                    val baseName = originalFile.nameWithoutExtension
                    val wavFile = File(parentDir, "$baseName.wav")
                    val path = wavFile.absolutePath

                    // Update file path in the database
                    val dbHelper = MeetingDbHelper.getInstance(applicationContext)
                    currentMeetingId?.let { id ->
                        val list = dbHelper.getMeetingList()
                        val savedMeeting = list.firstOrNull { it.id == id }
                        if (savedMeeting != null) {
                            val updated = savedMeeting.copy(filePath = path)
                            dbHelper.insertMeeting(updated)
                        }
                    }
                    path
                }
                currentFilePath = wavPath

                // Initialize AudioRecord
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioEncoding,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("MeetingRecordingService", "AudioRecord initialization failed")
                    audioRecord?.release()
                    audioRecord = null
                    switchToOfflineFallback()
                    return@launch
                }

                val wavFile = File(wavPath)
                fileOutputStream = withContext(Dispatchers.IO) { FileOutputStream(wavFile) }
                rawAudioBytesWritten = 0L
                // Write placeholder WAV header
                withContext(Dispatchers.IO) {
                    writeWavHeader(fileOutputStream!!, 0, 0, sampleRate.toLong(), 1, (sampleRate * 2).toLong())
                }

                // Start Deepgram Live Client
                deepgramClient = DeepgramLiveClient(deepgramCallback)
                deepgramClient?.connect(deepgramKey, wakeWord)

                // Initialize Asynchronous pipeline Channel
                val channel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.BUFFERED, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
                audioChannel = channel

                // Asynchronous Writer Coroutine (Consumes queue, performs blocking writes and network requests)
                audioPipelineJob = serviceScope.launch(Dispatchers.IO) {
                    try {
                        for (byteArray in channel) {
                            fileOutputStream?.write(byteArray)
                            rawAudioBytesWritten += byteArray.size
                            deepgramClient?.sendAudio(byteArray, byteArray.size)
                        }
                    } catch (e: Exception) {
                        Log.e("MeetingRecordingService", "Error in audio pipeline worker", e)
                    }
                }

                audioRecord?.startRecording()
                isRecording = true
                MeetingRecordingStateHolder.updateRecordingState(true)

                // High-priority Audio Reader Coroutine (Guarantees no dropped audio frames)
                recordJob = serviceScope.launch(audioDispatcher) {
                    val sendChunkSize = 640 // 40ms audio chunks
                    val buffer = ShortArray(sendChunkSize)

                    while (isRecording && coroutineContext.isActive) {
                        val readSize = audioRecord?.read(buffer, 0, sendChunkSize) ?: 0
                        if (readSize > 0) {
                            var maxAmp = 0
                            val byteBuffer = ByteBuffer.allocate(readSize * 2).order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until readSize) {
                                val valAbs = java.lang.Math.abs(buffer[i].toInt())
                                if (valAbs > maxAmp) {
                                    maxAmp = valAbs
                                }
                                byteBuffer.putShort(buffer[i])
                            }

                            val byteArray = ByteArray(readSize * 2)
                            System.arraycopy(byteBuffer.array(), 0, byteArray, 0, readSize * 2)

                            // Queue into channel buffer
                            channel.send(byteArray)

                            // Update state holder amplitude
                            MeetingRecordingStateHolder.updateAmplitude(maxAmp)
                        } else if (readSize < 0) {
                            Log.w("MeetingRecordingService", "Error reading AudioRecord data: $readSize")
                            delay(100)
                        } else {
                            yield()
                        }
                    }
                }

                startTimer()
                Log.i("MeetingRecordingService", "Started live transcribing and raw recording: $wavPath")
            } catch (e: Exception) {
                Log.e("MeetingRecordingService", "Failed to start live recording, switching to offline fallback", e)
                switchToOfflineFallback()
            }
        }
    }

    private fun switchToOfflineFallback() {
        val origPath = originalFilePath ?: return
        
        isRecording = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (ex: Exception) {
            Log.e("MeetingRecordingService", "Error releasing AudioRecord in fallback", ex)
        }
        audioRecord = null

        try {
            fileOutputStream?.close()
        } catch (ex: Exception) {
            // ignore
        }
        fileOutputStream = null

        // Delete partial WAV file
        currentFilePath?.let { path ->
            val wavFile = File(path)
            if (wavFile.exists()) {
                wavFile.delete()
            }
        }

        deepgramClient?.disconnect()
        deepgramClient = null

        isLiveTranscript = false
        MeetingRecordingStateHolder.updateLiveTranscriptState(false)
        currentFilePath = origPath

        // Reset database record to point to original m4a
        val dbHelper = MeetingDbHelper.getInstance(this)
        currentMeetingId?.let { id ->
            val list = dbHelper.getMeetingList()
            val savedMeeting = list.firstOrNull { it.id == id }
            if (savedMeeting != null) {
                val updated = savedMeeting.copy(filePath = origPath)
                dbHelper.insertMeeting(updated)
            }
        }

        // Restart with standard MediaRecorder
        startOfflineRecording(origPath)
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            timerJob?.cancel()
            timerJob = null

            if (isLiveTranscript) {
                isRecording = false
                recordJob?.cancel()
                recordJob = null

                // Close channel queue
                audioChannel?.close()
                audioChannel = null

                try {
                    runBlocking {
                        audioPipelineJob?.join()
                    }
                } catch (e: Exception) {
                    Log.e("MeetingRecordingService", "Error joining audio pipeline job", e)
                }
                audioPipelineJob = null

                try {
                    audioRecord?.let { record ->
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            record.stop()
                        }
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.e("MeetingRecordingService", "Error stopping AudioRecord", e)
                }
                audioRecord = null

                try {
                    fileOutputStream?.flush()
                    fileOutputStream?.close()
                } catch (e: Exception) {
                    Log.e("MeetingRecordingService", "Error closing FileOutputStream", e)
                }
                fileOutputStream = null

                // Update WAV header with correct audio sizes
                currentFilePath?.let { path ->
                    updateWavHeader(File(path))
                }

                // Graceful WebSocket close
                deepgramClient?.disconnect()
                deepgramClient = null
            } else {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.e("MeetingRecordingService", "Error stopping MediaRecorder", e)
                    }
                    release()
                }
                mediaRecorder = null
            }
            Log.i("MeetingRecordingService", "Stopped recording successfully.")
        } catch (e: Exception) {
            Log.e("MeetingRecordingService", "Error during stopRecording", e)
        } finally {
            isRecording = false
            MeetingRecordingStateHolder.updateRecordingState(false)
            stopForeground(true)
            stopSelf()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                MeetingRecordingStateHolder.updateSeconds(seconds)
                
                if (!isLiveTranscript) {
                    mediaRecorder?.let {
                        try {
                            val amp = it.maxAmplitude
                            MeetingRecordingStateHolder.updateAmplitude(amp)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "meeting_recording_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Meeting Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val titleText = if (isLiveTranscript) "กำลังถอดความการประชุมสดแยกผู้พูด..." else "กำลังบันทึกการประชุม..."

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(titleText)
            .setContentText("ระบบกำลังทำงานในพื้นหลัง (แตะเพื่อเปิดแอป)")
            .setSmallIcon(R.drawable.baseline_mic_24)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long,
        channels: Int,
        byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.toByte() // RIFF
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.toByte() // WAVE
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte() // 'fmt ' chunk
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // size of chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format PCM = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.toByte() // data chunk
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun updateWavHeader(wavFile: File) {
        if (!wavFile.exists()) return
        try {
            val totalAudioLen = wavFile.length() - 44
            val totalDataLen = totalAudioLen + 36
            val sampleRate = 16000L
            val channels = 1
            val byteRate = sampleRate * channels * 2

            RandomAccessFile(wavFile, "rw").use { raf ->
                val header = ByteArray(44)
                header[0] = 'R'.toByte()
                header[1] = 'I'.toByte()
                header[2] = 'F'.toByte()
                header[3] = 'F'.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                header[8] = 'W'.toByte()
                header[9] = 'A'.toByte()
                header[10] = 'V'.toByte()
                header[11] = 'E'.toByte()
                header[12] = 'f'.toByte()
                header[13] = 'm'.toByte()
                header[14] = 't'.toByte()
                header[15] = ' '.toByte()
                header[16] = 16
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1
                header[21] = 0
                header[22] = channels.toByte()
                header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = ((sampleRate shr 8) and 0xff).toByte()
                header[26] = ((sampleRate shr 16) and 0xff).toByte()
                header[27] = ((sampleRate shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = (channels * 2).toByte()
                header[33] = 0
                header[34] = 16
                header[35] = 0
                header[36] = 'd'.toByte()
                header[37] = 'a'.toByte()
                header[38] = 't'.toByte()
                header[39] = 'a'.toByte()
                header[40] = (totalAudioLen and 0xff).toByte()
                header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
                header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
                header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

                raf.seek(0)
                raf.write(header)
            }
            Log.i("MeetingRecordingService", "Updated WAV header successfully for $wavFile")
        } catch (e: Exception) {
            Log.e("MeetingRecordingService", "Error updating WAV header", e)
        }
    }

    private fun serializeSegments(segments: List<TranscriptSegment>): String {
        val array = JSONArray()
        for (seg in segments) {
            val obj = JSONObject()
            obj.put("speaker", seg.speaker)
            obj.put("text", seg.text)
            array.put(obj)
        }
        return array.toString()
    }

    private fun showErrorToast(msg: String?) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                "ไม่สามารถบันทึกเสียงได้: $msg",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
        audioExecutor.shutdown()
    }
}
