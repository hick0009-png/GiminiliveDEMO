# Project Review — GeminiMultimodalLiveAPI

> Generated: 2026-07-01  
> Files reviewed: 61 Kotlin files, build configs, AndroidManifest  
> Status: Post-code-review snapshot

---

## What is this app?

Thai-language voice assistant app using **Gemini Live API (WebSocket real-time)** for bidirectional voice conversations with camera vision, plus **Deepgram** for real-time Thai speech-to-text.

**Package:** `com.example.geminimultimodalliveapi`

---

## Architecture (6 Layers)

```
┌──────────────────────────────────────────────┐
│  UI Layer                                     │
│  MainActivity, MeetingActivity,               │
│  SettingsActivity, MemoryActivity             │
│  GeminiTileService (Quick Settings)            │
│  FloatingWidget (system overlay bubble)       │
├──────────────────────────────────────────────┤
│  Service Layer                                │
│  FloatingWidgetService (~2500 lines) — brain  │
│  MeetingRecordingService                      │
│  SmartNotificationListenerService             │
│  WakeWordDetector, OverlayWidgetController    │
│  PhoneStateReceiver, SessionNotificationMgr   │
├──────────────────────────────────────────────┤
│  Architecture Layer (Situational Awareness)   │
│  PerceptionEngine (sensors)                   │
│  AttentionManager                             │
│  TopicManager                                 │
│  ContextManager                               │
│  DynamicRulesManager                          │
│  SituationLogManager                          │
├──────────────────────────────────────────────┤
│  Agent Layer (Dating Assistant)               │
│  DatingRouterAgent                            │
│  DatingSkillAgentImpl                         │
│  DatingAnalysisOrchestrator                   │
│  DocumentSelector                             │
│  SkillAgent (interface)                        │
├──────────────────────────────────────────────┤
│  Network Layer                                │
│  GeminiLiveClient (WebSocket)                 │
│  DeepgramLiveClient (WebSocket)               │
│  GeminiTextService (REST)                     │
│  GeminiMeetingService (REST + Context Cache)  │
│  ToolDefinitions                              │
│  ApiKeyValidator                              │
├──────────────────────────────────────────────┤
│  Data Layer                                   │
│  MemoryManager + MemoryDbHelper (SQLite)      │
│  AppPreferences (EncryptedSharedPreferences)  │
│  MeetingDbHelper, DateProfileDbHelper         │
│  LocalVehicleDbHelper (SQLite)                │
│  GoogleDriveServiceHelper                     │
│  GoogleCalendarServiceHelper                  │
│  DocumentParser (TF-IDF search)               │
│  DatingSkillManager, MeetingModel             │
└──────────────────────────────────────────────┘
```

---

## Features (15 Total)

| # | Feature | Description | Status | Key Risk |
|---|---------|-------------|--------|----------|
| 1 | **Gemini Live Voice Chat** | WebSocket real-time bidirectional voice conversation with Gemini | ✅ Implemented | Tool schemas use uppercase JSON types — tools silently fail |
| 2 | **Camera Vision** | Periodic JPEG frame capture sent to Gemini for visual context | ✅ Implemented | Bitmap processing on main thread, race condition on camera lifecycle |
| 3 | **Deepgram ASR** | Real-time Thai speech-to-text with speaker diarization | ✅ Implemented | No reconnect logic, callbacks on WebSocket thread |
| 4 | **Wake Word** | Offline Thai wake word detection via SpeechRecognizer | ✅ Implemented | High battery drain, auto-mutes system notifications |
| 5 | **Floating Widget** | System overlay bubble with tap/double-tap/long-press gestures | ✅ Implemented | Click event race condition |
| 6 | **Vehicle Info** | Voice-based save/query for license plate, tax, maintenance | ✅ Implemented | Depends on tool schemas, SQLite on main thread |
| 7 | **Memory System** | Long-term memory with importance scoring, decay, pin, search | ✅ Implemented | `updateMemoryPin` loads all rows, insert+evict not atomic |
| 8 | **Google Calendar** | Create/list calendar events via voice | ✅ Implemented | No OAuth refresh handling, empty ID on null |
| 9 | **Google Drive** | Upload/list/delete documents for context | ✅ Implemented | SQL injection in queries, no OAuth refresh |
| 10 | **Meeting Recorder** | Audio recording + Deepgram captions + Gemini analysis + playback | ✅ Implemented | ANR risks, `Channel(UNLIMITED)` OOM risk |
| 11 | **Dating Assistant** | Multi-agent pipeline for dating conversation analysis | ✅ Implemented | `deferred.await()` no timeout — hangs forever |
| 12 | **Situational Awareness** | Motion/location/screen/Battery/bluetooth context collection | ✅ Implemented | Sensor leaks, no rotation, no log cleanup |
| 13 | **Dynamic Rules** | User-defined condition → action rules with SQLite | ✅ Implemented | Full JSON serialization on every read/write |
| 14 | **Notification Perception** | Reads all notifications via `NotificationListenerService` for context | ✅ Implemented | **Privacy breach** — full message text sent to Gemini |
| 15 | **Weather & Places** | Open-Meteo weather, OpenStreetMap nearby places via voice | ✅ Implemented | No network timeout — can hang forever |

---

## Tech Stack

| Component | Technology | Notes |
|-----------|-----------|-------|
| Language | Kotlin 2.0.0 | Outdated (latest 2.1.20) |
| AGP | 8.2.2 | Adequate |
| Min/Target SDK | 26 / 34 | Good |
| UI | Jetpack Compose + XML layouts | Hybrid approach |
| Architecture | Service-based + singleton holders | No MVVM/MVI, no DI |
| Gemini API | WebSocket (`wss://.../BidiGenerateContent`) | Real-time bidirectional |
| Deepgram | WebSocket real-time STT | Thai language support |
| Database | Manual SQLite via `SQLiteOpenHelper` | No Room, cursor leak risks |
| DI | **None** | All singletons via static access |
| Auth | Google OAuth (Calendar, Drive) | No refresh handling |
| Async | Coroutines + raw Threads + Handler | Mixed — hard to reason about |
| Audio | `AudioRecord` / `AudioTrack` | 16kHz input, 24kHz output |
| Camera | Camera2 API | Periodic capture at ~3s interval |

---

## File Inventory (61 Kotlin files)

### Root Package (5 files)
| File | Lines | Purpose |
|------|-------|---------|
| `FloatingWidgetService.kt` | ~2500 | Core service — Gemini/Deepgram clients, audio, sensors, overlay |
| `MainActivity.kt` | ~1050 | Camera preview, chat UI, Google sign-in |
| `SettingsActivity.kt` | ~1400 | Full settings (API keys, voice, wake word, skills, PDF upload) |
| `MeetingActivity.kt` | ~1007 | Meeting recording UI, Deepgram captions, playback |
| `GeminiTileService.kt` | 68 | Quick Settings tile |

### `audio/` (3 files)
`AudioConfig.kt`, `AudioRecorder.kt`, `AudioPlayer.kt`

### `session/` (3 files)
`SessionState.kt`, `SessionStateHolder.kt`, `MeetingRecordingStateHolder.kt`

### `network/` (6 files)
`GeminiLiveClient.kt`, `DeepgramLiveClient.kt`, `GeminiTextService.kt`, `GeminiMeetingService.kt`, `ToolDefinitions.kt`, `ApiKeyValidator.kt`

### `camera/` (1 file)
`CameraCaptureHelper.kt`

### `memory/` (3 files)
`MemoryEntry.kt`, `MemoryDbHelper.kt`, `MemoryManager.kt`

### `data/` (7 files)
`AppPreferences.kt`, `DatingSkill.kt`, `DatingSkillManager.kt`, `DateProfileDbHelper.kt`, `DateInsight.kt`, `MeetingModel.kt`, `MeetingDbHelper.kt`

### `service/` (7 files)
`GeminiToolDispatcher.kt`, `OverlayWidgetController.kt`, `WakeWordDetector.kt`, `SessionNotificationManager.kt`, `SmartNotificationListenerService.kt`, `MeetingRecordingService.kt`, `PhoneStateReceiver.kt`

### `architecture/` (6 files)
`PerceptionEngine.kt`, `AttentionManager.kt`, `TopicManager.kt`, `ContextManager.kt`, `DynamicRulesManager.kt`, `SituationLogManager.kt`

### `agent/` (5 files)
`DatingRouterAgent.kt`, `DatingSkillAgentImpl.kt`, `DatingAnalysisOrchestrator.kt`, `DocumentSelector.kt`, `SkillAgent.kt`

### `utils/` (7 files)
`LocalVehicleDbHelper.kt`, `GoogleDriveServiceHelper.kt`, `GoogleCalendarServiceHelper.kt`, `DocumentParser.kt`, `PermissionHelper.kt`, `PerformanceMonitor.kt`, `SpeakerLockManager.kt`

### `calendar/` (1 file)
`CalendarManager.kt`

### `document/` (1 file)
`DocumentManager.kt`

### `error/` (1 file)
`AppError.kt`

### `ui/` (5 files)
`MemoryActivity.kt`, `MemoryViewModel.kt`, `Color.kt`, `Theme.kt`, `Type.kt`

---

## Strengths

- **Ambitious scope** — 15 features in a single app, real-time multimodal AI integration
- **Gemini Live API WebSocket** — uses the latest bidirectional streaming API (not just REST)
- **Thai language first** — built for Thai users with Deepgram Thai STT
- **Situational awareness engine** — full sensor fusion + context management + dynamic rules
- **Speaker diarization + lock** — sophisticated multi-speaker audio handling
- **System-level integration** — floating widget, notification listener, quick settings tile
- **Meeting recorder** — full pipeline from audio capture → live captions → Gemini analysis
- **Multi-agent dating assistant** — skill-based routing with psychology document context

---

## Weaknesses (from Bug Report)

| Category | Count | Key Issues |
|----------|-------|------------|
| Critical | 30 | Tool schemas broken, ANR risks, privacy breach, memory leaks, OOM risk |
| Major | 35+ | Thread safety, no DI, no state preservation, no onSaveInstanceState |
| Minor | 24 | Hardcoded strings, duplicate utilities, emit in logs |

### Top 5 Critical Issues

1. **ToolDefinitions: uppercase JSON types** (`"OBJECT"` not `"object"`) → every parameterized tool silently fails
2. **ProGuard `-repackageclasses`** → release build crashes on every component
3. **Missing `POST_NOTIFICATIONS`** for Android 13+ → notifications silently suppressed
4. **Thread blocking on main thread** (`Thread.sleep`, `MediaPlayer.prepare`) → ANR
5. **Notification content privacy breach** → full message text sent to Gemini API

---

## Feature Readiness Matrix

| Feature | Works Now? | Risk Level | Blockers |
|---------|-----------|------------|----------|
| Voice Chat | ✅ Fixed | LOW | Tool schemas fixed |
| Camera Vision | ✅ Fixed | MED | Thread safety, camera race fixed |
| Deepgram ASR | ✅ Likely | MED | No reconnect |
| Wake Word | ✅ Likely | MED | Battery drain |
| Floating Widget | ✅ Fixed | LOW | startForeground + WeakRef fixed |
| Vehicle Info | ✅ Fixed | LOW | Tool schemas fixed |
| Memory System | ✅ Basic | LOW | Minor perf issues |
| Google Calendar | ⚠️ Partial | HIGH | No OAuth refresh |
| Google Drive | ✅ Fixed | LOW | SQL injection fixed |
| Meeting Recorder | ✅ Fixed | MED | ANR, OOM fixed |
| Dating Assistant | ✅ Fixed | LOW | Timeout added |
| Situational Awareness | ✅ Basic | MED | Sensor leaks |
| Dynamic Rules | ✅ Basic | MED | Perf with many rules |
| Notification Perception | ✅ Fixed | LOW | Privacy fixed |
| Weather & Places | ✅ Basic | MED | No timeout |

---

## Next Steps (Recommended Order)

> ✅ Items 1-8 completed as of 2026-07-01 (commits 216fd41, 153a899)

9. Fix **state preservation** (onSaveInstanceState) in all 3 activities
10. Begin **DI + Room migration** for long-term maintainability
