---
name: gemini-live-audio-orchestration
description: Guidelines and architectures for managing bidirectional real-time audio streaming, jitter buffering, speaker locking, and echo cancellation using Gemini Live API and Deepgram in Android.
---

# Gemini Live Audio Orchestration & Troubleshooting

This skill governs how to build, maintain, and troubleshoot real-time, low-latency, bidirectional audio streaming systems in Android using the Gemini Live WebSocket API and Deepgram.

## 1. Core Architectures & Problem-Solving Guidelines

### A. Acoustic Feedback Prevention (Echo Cancellation)
* **Challenge**: When the device speaker plays Gemini's synthesized voice, the microphone picks up this output and sends it back to the Gemini Live WebSocket. The Gemini server detects this as user speech (barge-in / interruption) and sends an `interruption` signal, halting playback.
* **Guideline**: Implement **Playback-Controlled Microphone Muting**. Check the state of the audio player before sending media chunks to the server:
  ```kotlin
  if (audioPlayer?.isPlaying != true) {
      // Encode and send audio chunk (microphone input)
      liveClient?.sendMediaChunk(base64Data, "audio/pcm;rate=16000")
  }
  ```
  This cuts off the feedback loop entirely, preventing self-interruption on the server.

### B. Real-Time Jitter Buffering & Underflow Recovery
* **Challenge**: Network jitter causes incoming audio chunks to arrive at varying intervals. If the queue runs dry (underflow), playing chunks immediately upon arrival with zero buffer cushion causes constant clicking and micro-stuttering.
* **Guideline**: Implement a **State-Driven Jitter Buffer** with underflow re-buffering:
  1. Maintain an `isBuffering` boolean flag in the playback thread.
  2. When `isBuffering` is true, wait for the queue size to reach `minBufferChunks` (e.g., 3 chunks / 120ms of audio) before setting `isBuffering = false` and resuming playback.
  3. Use a short polling timeout (e.g., `50ms`) to retrieve chunks. If a poll returns null, set `isBuffering = true` immediately to re-accumulate the cushion.
  ```kotlin
  var isBuffering = true
  while (isActive) {
      if (isBuffering) {
          if (audioQueue.size >= minBufferChunks) {
              isBuffering = false
          } else {
              delay(10)
              continue
          }
      }
      val chunk = audioQueue.poll(50, TimeUnit.MILLISECONDS)
      if (chunk != null) {
          playAudio(chunk)
      } else {
          isBuffering = true // Transition back to buffering underflow
      }
  }
  ```

### C. Dynamic Sample Rate Management
* **Challenge**: Gemini Live API responses might alter their output sample rates depending on conditions or updates. If the sample rate mismatches the `AudioTrack` configuration, playback speed shifts, causing audio corruption and pops. Re-creating `AudioTrack` constantly causes major latency.
* **Guideline**: Parse sample rate dynamically from the MIME type parameters (e.g., `"audio/pcm;rate=24000"`) and recreate the `AudioTrack` **only** when the sample rate actually changes:
  ```kotlin
  var rate = 24000
  if (mimeType.contains("rate=")) {
      rate = mimeType.substringAfter("rate=").substringBefore(";").trim().toInt()
  }
  if (currentSampleRate != rate) {
      currentSampleRate = rate
      releaseAudioTrack() // Will be recreated on next playAudio()
  }
  ```

### D. Focused Voice Mode & Speaker Locking
* **Challenge**: Standard wake-word detection lacks security, letting anyone speak to the device. Using a separate REST API fallback for Focused Mode causes latency and robotic text-to-speech outputs.
* **Guideline**: Use **Deepgram ASR + Speaker Lock** running concurrently with the **Gemini Live Client**:
  1. Connect to both Deepgram (for locking onto a specific speaker ID using wake-word phonetic matches) and Gemini Live (for streaming synthesis).
  2. Once the user is locked and finishes speaking (`speech_final: true`), route the text transcript to `liveClient?.sendTextMessage(query)` instead of a REST API.
  3. This yields high-quality, real-time Gemini voice output while retaining speaker lock protection.
  4. Strip tone marks and white spaces from ASR transcripts in tonal languages (e.g., Thai wake-word `"กอหญ้า"` matching `"ก็หญ้า"`, `"กัญญา"`) to ensure robust pattern matching.

### E. API Connection Coordination & Redundant Transition Guard
* **Challenge**: When connecting both Gemini Live and Deepgram WebSockets concurrently, both sockets independently call connection callbacks. Each callback triggers state transitions (e.g., `Standby`) and plays status chimes. This leads to duplicate overlapping chime plays (chime stuttering) and repeatedly cancels/restarts the system speech recognizer, creating a loops of client errors.
* **Guideline**: Implement **Connection Sockets Coordination**:
  1. Define connection state flags (e.g., `isGeminiConnected`, `isDeepgramConnected`).
  2. Implement a unified, main-thread serialized checker function (e.g., `checkAndCompleteConnection()`). Check if all required sockets are open before transitioning state.
  3. Implement a **Redundant State Guard** in the state machine (e.g., `transitionToState`). If a transition targets the same class of state with matching properties (e.g., `Standby` with the same wake word), ignore it. This prevents duplicate chime playback and recognizer restarts.

### F. Standby Socket Keep-Alive & Screen-Off Voice Wakeup
* **Challenge**: Stopping the microphone recorder in `Standby` state to save battery stops the data flow to the Deepgram WebSocket. Deepgram's cloud socket times out after 10-12 seconds of silence and closes with code `1011`, triggering an unexpected session disconnection. In addition, standard Android system `SpeechRecognizer` is suspended when the screen goes off, preventing hands-free wakeup.
* **Guideline**: Implement **Continuous Standby Recording (Gated Focus Mode)**:
  1. Keep the low-power `AudioRecorder` running and stream audio data to Deepgram even in `Standby` state when Focus Mode is active.
  2. Gate the output stream: only send audio bytes to Gemini Live if the attention manager state is actively set to `ACTIVE_SESSION` (saving bandwidth and Gemini API usage costs).
  3. This feeds Deepgram continuous VAD audio, keeping the WebSocket alive indefinitely (preventing 1011 timeouts).
  4. Since Deepgram is running in a Foreground Service, it remains active when the screen is off, enabling high-performance cloud-based wake-word detection.

---

## 2. Recommended Settings & Constants
* **Input Rate**: `16000Hz` (16-bit Mono PCM)
* **Output Rate**: `24000Hz` (dynamically parsed, defaults to 24000Hz)
* **Recorder Chunk Size**: `640` short samples (40ms chunks)
* **Jitter Buffer Cushion**: `minBufferChunks = 3` (120ms cushion)
* **Inactivity Idle Stop Timeout**: `1000ms` (stops the AudioTrack and releases resources after 1.0 second of silence)
* **Voice Activity Detection Threshold**: `amp > 500` (dynamic slider/presets recommended for riding noise)
* **Deepgram Timeout Prevention**: Continuous recording or `{"type": "KeepAlive"}` text frames sent every 5-8 seconds.
