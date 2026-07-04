# Deepgram's Role in Live Translate Architecture

This document describes how **Deepgram** supports the **Live Translate** feature inside our application, specifically focusing on speaker filtering (Focus Mode) and multi-speaker transcription separation (Diarization).

---

## 1. Deepgram's Key Capabilities

While `gemini-3.5-live-translate-preview` handles the end-to-end voice translation (audio-to-audio), it lacks the ability to:
1. **Diarize Speakers**: Distinguish who is speaking in a room.
2. **Filter Background Speech**: Block background voices (e.g. street noise, coffee shop chatter) from being translated.

This is where Deepgram's low-latency Thai speech-to-text and speaker diarization engine plays a critical role.

---

## 2. Architecture: Deepgram as a Gating & Labeling Layer

We can integrate Deepgram into the Live Translate pipeline in two ways:

### A. Focus Mode (Speaker Gating)
When the user is riding a motorcycle or in a noisy café, we only want to translate the *user's* voice, not the surrounding noise or other talkers.

```
                   ┌─────────────────────────────┐
                   │    Raw Mic Audio Stream     │
                   └──────────────┬──────────────┘
                                  │
                                  ▼
                        Deepgram Nova-2 +
                       SpeakerLockManager
                                  │
                                  ├─► [Background Voice] ──► Discard (No Translation)
                                  │
                                  ▼
                   [ User Voice Verified ]
                                  │ (Send audio packets)
                                  ▼
                     Gemini Live Translate API
                                  │
                                  ▼
                           English Output
```

- **How it works**: Deepgram processes the audio in parallel. If the active speaker does not match the owner's voice print (`SpeakerLockManager` lock), the app suppresses sending those audio chunks to the Gemini Live Translate WebSocket.
- **Benefit**: Saves API quota, reduces translation noise, and prevents translating random background talk.

---

### B. Meeting Mode (Diarization & Labeling)
In a face-to-face meeting between the User and a Partner speaking into the same device or microphone:
- **The Challenge**: If both voices are sent to the translator, the output transcript on screen becomes a single unlabelled block of text.
- **Deepgram's Solution**: Deepgram transcribes and tags each voice packet with a speaker ID (e.g., `Speaker 0` vs. `Speaker 1`).
- **Result**: The app maps Deepgram's speaker tags to the translation screen, showing distinct bubbles:
  - **You (Speaker 0)**: "สวัสดีครับ" $\rightarrow$ *[Translated to English]*
  - **Partner (Speaker 1)**: "Hello" $\rightarrow$ *[Translated to Thai]*
