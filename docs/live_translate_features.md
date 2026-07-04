# RFC: Gemini 3.5 Live Translate - Features & Github Analysis

This document details the configuration parameters, unique behaviors, and UX tricks discovered from Google's official Gemini Cookbook and community samples (like LiveKit) for integrating `gemini-3.5-live-translate-preview` into our mobile app.

---

## 1. Core Configuration & API Parameters

According to the official Google Gemini API docs and python cookbook, the translation capabilities are controlled via the `TranslationConfig` nested inside `LiveConnectConfig` (or JSON `setup` payload for raw WebSockets):

- **`targetLanguageCode`** *(String)*: Specifies the BCP-47 language tag for translation output (e.g. `"en"` for English, `"ja"` for Japanese, `"zh-Hans"` for Simplified Chinese, `"es"` for Spanish).
- **`echoTargetLanguage`** *(Boolean)*:
  - If `true`, when the input audio matches the target language, the model will "echo" (repeat) the input audio directly without re-translating or going silent.
  - If `false`, it only streams translation outputs when the input language differs from the target language.

---

## 2. Dual-Transcription Trick (UX Booster)

By configuring the transcription settings for both inputs and outputs, we can stream back **two separate real-time text streams** concurrently over the WebSocket:

1. **`inputAudioTranscriptionConfig`**: Transcribes the user's spoken words in the source language (e.g., Thai).
2. **`outputAudioTranscriptionConfig`**: Transcribes the translated result in the target language (e.g., English).

### JSON Setup Payload Example for WebSockets:
```json
{
  "setup": {
    "model": "models/gemini-3.5-live-translate-preview",
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "translationConfig": {
        "targetLanguageCode": "en",
        "echoTargetLanguage": true
      }
    },
    "inputAudioTranscriptionConfig": {
      "model": "models/speech-to-text"
    }
  }
}
```

---

## 3. Real-Time UI Solutions for our App

Using the dual-transcription capability, we can construct a **Split-Subtitle Interpreter Dashboard** on the mobile screen:

```
┌────────────────────────────────────────────────────────┐
│                     LIVE INTERPRETER                   │
├───────────────────────────┬────────────────────────────┤
│   🔊 Spoken (Thai)        │   🌐 Translated (English)  │
├───────────────────────────┼────────────────────────────┤
│ "สวัสดีครับ วันนี้มีประชุม" │ "Hello, there is a meeting │
│                           │  today."                   │
│                           │                            │
└───────────────────────────┴────────────────────────────┘
```

- **Left Panel (Source)**: Renders text from `inputAudioTranscriptionConfig` dynamically.
- **Right Panel (Target)**: Renders text from `outputAudioTranscriptionConfig` dynamically.
- **Audio Output**: Streams the 24kHz translation audio directly to the user's earphone/headset.
- **Low Latency**: Because the pipeline is hosted entirely inside a single WebSocket connection on `gemini-3.5-live-translate-preview`, latency is near-zero (matching Google Translate's Listening Mode).
