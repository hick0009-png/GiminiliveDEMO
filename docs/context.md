# Project Context

Purpose

This document defines the shared understanding of the project.

Every AI agent should read this file before working.

---

## Project Vision

Android application providing a voice-activated AI assistant using Gemini Live API with multimodal capabilities (audio + camera) for hands-free assistance while riding a motorcycle. The app supports real-time voice conversation, tool execution (calling, calendar, navigation, weather, nearby places), long-term memory, meeting recording, and conversation analysis (dating mode).

---

## Mission

Build a production-grade Thai-language voice assistant that runs as a foreground service with floating widget, wake word detection, dual audio streaming (Gemini + Deepgram), situational awareness (motion/location sensors), dynamic behavior tuning, and encrypted local storage.

---

## Core Principles

- Real-time bidirectional audio streaming over WebSocket to Gemini Live API
- Use Deepgram for Thai speech-to-text with speaker diarization (focus/solo mode)
- Speaker Lock with wake word detection to filter only the owner's voice
- Situational state machine: IDLE -> LISTENING -> ACTIVE_SESSION -> BACKGROUND
- Long-term memory with utility scoring, temporal decay, and budget-based eviction
- Dynamic behavior rules (save_system_rule) that inject instructions based on motion/location/topic
- Conversation topic detection and auto-switching with 10-minute timeout
- All local databases encrypted with SQLCipher
- All user preferences encrypted with EncryptedSharedPreferences
- Multi-agent system for dating conversation analysis (Router + Skill Analyzer + Document Selector)

---

## Project Vocabulary

- **Wake Word:** Trigger phrase ("กอหญ้า") that activates the assistant from standby
- **Focus Mode:** Solo mode using Deepgram speaker diarization to only listen to the owner's voice
- **Dating Mode:** Conversation analysis mode that tracks two speakers (User + Partner) and provides real-time insights
- **Situational Context:** Real-time sensor snapshot (motion, location, attention state, current topic)
- **Dynamic Rules:** User-customizable behavior rules saved via voice command (save_system_rule)
- **Utility Score:** Memory ranking formula: `baseImportance + (accessCount * 2)`
- **Perception Event:** Sensor-derived events (motion change, location change, screen state, speech detection)

---

## Architecture

### Layer 1: Network (WebSocket + HTTP)
- `GeminiLiveClient` — WebSocket to `models/gemini-3.1-flash-live-preview` using OkHttp, handles bidirectional audio/text/tool messages
- `DeepgramLiveClient` — WebSocket to Deepgram `nova-2` model for Thai STT with diarization
- `GeminiTextService` — HTTP POST to `models/gemini-3.5-flash:generateContent` for text-only requests
- `ToolDefinitions` — 18 function declarations registered with Gemini

### Layer 2: Audio
- `AudioRecorder` — 16kHz PCM mono recording with AEC, Noise Suppressor, AGC, voice activity gate
- `AudioPlayer` — 24kHz PCM mono playback with jitter buffer (3 chunk minimum), audio focus management
- `AudioConfig` — Constants: INPUT_SAMPLE_RATE=16000, OUTPUT_SAMPLE_RATE=24000

### Layer 3: Service Layer
- `FloatingWidgetService` — Foreground service managing session lifecycle, audio routing, sensor integration, Deepgram/Gemini coordination
- `GeminiToolDispatcher` — Handles all 18 tool calls with coroutine-based async execution
- `WakeWordDetector` — Android SpeechRecognizer-based wake word detection
- `OverlayWidgetController` — Floating bubble overlay with single-click/double-click/long-press
- `SessionNotificationManager` — Foreground service notification (microphone type)
- `SmartNotificationListenerService` — Reads notifications for AI context
- `MeetingRecordingService` — Background meeting recorder
- `PhoneStateReceiver` — Detects phone call state changes

### Layer 4: Situational Architecture
- `PerceptionEngine` — Sensor fusion: screen state (BroadcastReceiver), motion (accelerometer with 10s debounce), location (FusedLocationProvider)
- `AttentionManager` — State machine: IDLE / LISTENING / ACTIVE_SESSION / BACKGROUND, transitions based on PerceptionEvents
- `TopicManager` — Keyword-based topic classification with 10-minute timeout, supports dynamic keyword addition
- `DynamicRulesManager` — Gson-serialized rules in SharedPreferences, matched by condition type (ON_MOTION, ON_LOCATION, ON_TOPIC, GENERAL)
- `ContextManager` — Builds combined system prompt: base instruction + real-time snapshot + matched dynamic rules
- `SituationLogManager` — Logs incidents (user correction, manual cancel, system error) as JSON to files/

### Layer 5: Memory System
- `MemoryManager` — CRUD + utility scoring + context prompt formatting (hierarchical: Core > Active Session > General)
- `MemoryDbHelper` — SQLCipher-encrypted SQLite (AssistantMemory.db) with decay and eviction
- `MemoryEntry` — id, content, isPinned, baseImportance (1-5), accessCount, lastAccessedTime, category
- Eviction: keeps top 50 unpinned memories by utility score
- Decay: reduces baseImportance by 1 every 24h for unpinned memories; deletes at 0

### Layer 6: Multi-Agent Dating System
- `DatingAnalysisOrchestrator` — Coordinates routing, skill analysis, document selection, result merging
- `DatingRouterAgent` — Uses Gemini to select the best skill from available skills based on transcript + sensor context
- `DatingSkillAgentImpl` — Primary analysis agent that produces JSON insights (likes, dislikes, personality, tip, engagement, red flags)
- `DocumentSelector` — TF-IDF scoring to select relevant documents for the active skill
- Supports single-agent (high confidence) or multi-agent (medium confidence) execution paths

### Activities & UI
- `MainActivity` — Camera preview (TextureView), wave animation, mic button, shutter, console log, diagnostics panel, dating mode UI
- `SettingsActivity` — API keys, voice selection, wake word, AGC, focus mode, sensor settings, calendar reminder
- `MeetingActivity` — Meeting recording with live transcript
- `MemoryActivity` — View/manage long-term memories
- `MeetingActivity` — Meeting recording with live transcript

### Data Layer
- `AppPreferences` — EncryptedSharedPreferences (AES256), stores API keys, wake word, voice, gains, timeouts, feature toggles
- `MeetingDbHelper` — Meeting records with transcript segments
- `DateProfileDbHelper` — Dating profiles with likes/dislikes/personality
- `DatingSkillManager` — CRUD for dating skills (JSON files in skills/ directory)
- `LocalVehicleDbHelper` — Vehicle info (license plate, insurance, tax, maintenance)
- `GoogleDriveServiceHelper` — Upload/download documents from Google Drive
- `GoogleCalendarServiceHelper` — CRUD events in Google Calendar

### Camera
- `CameraCaptureHelper` — Camera2 API, configurable resolution (PerformanceMonitor dynamic scaling), JPEG capture with rotation

### Utilities
- `SpeakerLockManager` — Speaker diarization lock with wake word matching (Thai tone-stripped normalization), 8-second lock window
- `PermissionHelper` — Audio, Camera, Overlay, Notification, Phone permissions
- `PerformanceMonitor` — 4 levels (LOW/MEDIUM/HIGH/ULTRA) adjusting image resolution and frame rate

---

## Business Rules

1. **Time & Weather:** NEVER guess current time or weather — always call get_current_time / get_current_weather tools
2. **Navigation:** When user asks for directions, call launch_navigation immediately
3. **Memory:** When user shares personal info, call remember_personal_fact immediately
4. **Deletion:** When user says to forget, call forget_personal_fact immediately
5. **Situational Context:** Call get_situational_context only when needed (not before every response)
6. **Dynamic Rules:** save_system_rule supports SAVE and CLEAR_ALL actions
7. **Active Timeout:** Auto-revert to Standby after N ms silence (default 30s)
8. **Disconnect Timeout:** Auto-disconnect after N ms total silence (default 5 min)
9. **Dating Session:** Auto-sleep after 5 min silence or 60 min max session
10. **Camera:** Camera can only be activated when session is connected
11. **Phone Calls:** Disconnect session + wait for audio to finish before starting a call
12. **Calendar:** Requires Google Sign-In with calendar scope

---

## External APIs

| API | Purpose | Endpoint |
|-----|---------|----------|
| Gemini Live API | Real-time voice + tool calls | `wss://generativelanguage.googleapis.com/ws/.../BidiGenerateContent` |
| Gemini Text API | Text-only generation | `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent` |
| Deepgram | Thai STT + Speaker Diarization | `wss://api.deepgram.com/v1/listen` |
| Open-Meteo | Current weather | `https://api.open-meteo.com/v1/forecast` |
| OpenStreetMap Overpass | Nearby places search | `https://overpass-api.de/api/interpreter` |
| Google Calendar API | CRUD events | Via GoogleApiClient |
| Google Drive API | Document upload/download | Via GoogleApiClient |

---

## Tool Functions (18 total)

1. `open_camera` — Open camera preview
2. `close_camera` — Close camera
3. `save_vehicle_info(category, key_name, info_value)` — Save vehicle data to local DB
4. `query_vehicle_info(category?)` — Query vehicle data
5. `delete_vehicle_info(category, key_name?)` — Delete vehicle data
6. `get_current_time` — Get device current time with Thai day
7. `query_policy_document(query)` — Search uploaded insurance/manual docs
8. `make_phone_call(phone_number)` — Initiate phone call
9. `end_phone_call` — End active call
10. `create_calendar_event(title, description, start_time, duration_minutes)` — Create Google Calendar event
11. `list_calendar_events` — List events for next 7 days
12. `get_current_weather` — Get weather at current location
13. `find_nearby_places(place_type)` — Find places within 5km using OSM
14. `launch_navigation(destination)` — Open Google Maps navigation
15. `remember_personal_fact(fact_content, importance, category)` — Save to long-term memory
16. `forget_personal_fact(query)` — Delete from long-term memory
17. `query_relevant_memories(search_query)` — Search long-term memory
18. `save_system_rule(condition_type, condition_value, instruction, action)` — Save/clear dynamic behavior rules
19. `get_situational_context` — Get current sensor context + matched dynamic rules

---

## Current Priorities

- Maintain stable WebSocket connection with auto-reconnect (3 attempts, exponential backoff)
- Optimize audio pipeline: reduce latency, prevent echo (AEC + software gain ducking)
- Improve speaker lock accuracy with Thai language normalization
- Keep memory system performing with eviction and decay
- Ensure dating mode multi-agent analysis produces accurate insights
- Maintain Hermes Continuity Protocol (HCP) checkpoint system for session continuity

---

---

## Version Control (Git)

| Property | Value |
|----------|-------|
| Remote | `https://github.com/hick0009-png/GiminiliveDEMO.git` |
| Branch | `main` |
| Commits | 4 (Initial, Level 3 HCP, onError, Docs update) |
| Status | Pushable, gitignored: `*.db`, `*.m4a`, `.checkpoints/` |

### Git Commands

```bash
# Push ก่อนเปลี่ยน session
git add -A
git commit -m "ข้อความ"
git push

# Pull เมื่อเปิด session ใหม่
git pull

# ดูประวัติ
git log --oneline

# ดูสถานะ
git status
```

### Gitignore ปัจจุบัน

```
*.iml, .gradle, local.properties, .idea/caches, .DS_Store,
/build, /captures, .externalNativeBuild, .cxx,
.checkpoints/, *.db, *.m4a, *.mp4, *.mkv
```

---

## Hermes Continuity Protocol (HCP)

### Levels Implemented

| Level | Features | Scripts |
|-------|----------|---------|
| 1 | Summary handoff | — |
| 2 | Checkpoint + Resume | `checkpoint.ps1`, `resume.ps1` |
| 2.5 | Auto-checkpoint + Audit | + `audit.ps1`, `hcp-config.json` |
| 3 | Replay + Recover + onError | + `replay.ps1`, `recover.ps1`, `hcp-guard.ps1` |

### Available Commands

ทุกคำสั่งเรียกผ่าน PowerShell (ต้องใช้ `-ExecutionPolicy Bypass`):
```
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\<script>.ps1" [พารามิเตอร์]
```

#### `/checkpoint` — บันทึกสถานะงาน

```powershell
# Manual
.\checkpoint.ps1 -Title "Implement login" -Completed '["ออกแบบ UI"]' -Pending '["เขียนเทส"]' -Progress 50

# Auto (เพิ่ม sequence number)
.\checkpoint.ps1 -Title "Implement login" -Auto

# Error checkpoint
.\checkpoint.ps1 -OnError -ErrorType "api_failure" -ErrorMessage "Connection timeout" -ToolUsed "GeminiLiveClient"
```

พารามิเตอร์: `-Title`, `-Objective`, `-Completed` (JSON), `-Pending` (JSON), `-Decisions` (JSON), `-Knowledge` (JSON), `-Risks` (JSON), `-NextSteps` (JSON), `-ContextSummary`, `-Status`, `-Progress`, `-Auto`, `-OnError`, `-ErrorType`, `-ErrorMessage`, `-ToolUsed`, `-ActionsBeforeError` (JSON), `-RecoveryInstruction`, `-ErrorException`

#### `/resume` — โหลด checkpoint กลับมา

```powershell
.\resume.ps1 -Path "checkpoints/wp-20260701-210000-level3-hcp.json"
```

พารามิเตอร์: `-Path` (optional — หากไม่ระบุก็ค้นหาอัตโนมัติจาก latest.json หรือใช้ไฟล์เช็คพอยท์ล่าสุด)

#### `/audit` — ดูสรุปการทำงาน

```powershell
# ทั้งหมด
.\audit.ps1

# กรอง
.\audit.ps1 -Project "git"

# JSON
.\audit.ps1 -Json
```

พารามิเตอร์: `-Project`, `-Title`, `-Json`

#### `/replay` — ดูประวัติละเอียด + state transition

```powershell
.\replay.ps1
.\replay.ps1 -Full           # รวม memory_snapshot
.\replay.ps1 -Id "wp-xxx"    # เช็คพอยท์เดียว
.\replay.ps1 -Json
```

พารามิเตอร์: `-Id`, `-Project`, `-Full`, `-Json`

#### `/recover` — กู้คืนจาก error

```powershell
# recover อัตโนมัติ
.\recover.ps1

# ระบุ checkpoint
.\recover.ps1 -Id "wp-xxx-error-001"

# Dry run
.\recover.ps1 -Id "wp-xxx" -DryRun
```

พารามิเตอร์: `-Id`, `-Status`, `-DryRun`, `-Json`

#### `hcp-guard` — Execute + auto error checkpoint

```powershell
.\hcp-guard.ps1 -Command "git push" -Context "Pushing to GitHub"

# Auto-checkpoint on success
.\hcp-guard.ps1 -Command "npm run build" -Context "Build" -CreateAutoCheckpointOnSuccess
```

พารามิเตอร์: `-Command` (required), `-Context`, `-Title`, `-Completed`, `-Pending`, `-Decisions`, `-Knowledge`, `-NextSteps`, `-RecoveryInstruction`, `-CreateAutoCheckpointOnSuccess`, `-PassThru`

### Schema

ระบบรองรับ 2 สกีมา:
1. **Lite Schema (`hermes-work-package-v1-lite`)**: บันทึกความก้าวหน้าจำเป็นขั้นต่ำสำหรับ Auto-checkpoint เพื่อลดขนาด Token (ประกอบด้วยฟิลด์: `schema, id, sequence, createdAt, updatedAt, title, status, progress, objective, completed, pending, next_steps, context_summary`)
2. **Full Schema (`hermes-work-package-v1`)**: บันทึกข้อมูลประวัติและวิเคราะห์แบบเต็มรูปแบบ (มีครบทุกฟิลด์ด้านบนบวกกับ: `scope, artifacts, agents, decisions, knowledge, assumptions, risks, dependencies, open_questions, memory_snapshot, tags, error_info`)

### Storage

- `checkpoints/` — Public checkpoints (version controlled)
- `.checkpoints/` — Private checkpoints (gitignored)
- `.agents/skills/work-package/` — All HCP scripts + config

### Current Checkpoints

| Seq | Title | Status |
|-----|-------|--------|
| 1 | Build Work Package System Level 2.5 | completed |
| 2 | Git Init + Push + Resolve Open Questions | completed |
| 3 | Pre-Level 3 HCP | in_progress |
| 4 | Level 3 HCP — replay + recover + error recovery | completed |
| 5 | Android App Analysis - Pre-Refactor | in_progress |
| 6 | Full HCP Level 3 — onError + docs + manual | completed |
| 7 | Full Code Review — Bug Report + Project Review | in_progress |
| 8 | Fix 10 easy bugs + write tests (batch 1) | completed |
| 9 | Fix 10 easy bugs + write tests (batch 2) | completed |
| 10 | Serena and Memory MCP Verification | completed |
| 11 | Hermes BOOS Planning & PDF to JSON Design | completed |

---

## Known Constraints

- Requires Gemini API Key and Deepgram API Key
- Requires Google Account for Calendar/Drive features
- Requires multiple runtime permissions: Camera, Microphone, Location, Phone, Notifications, Overlay
- GPS must be enabled for weather and nearby places
- Deepgram is only used in Focus Mode or Dating Mode
- Thai wake word detection uses Android SpeechRecognizer (may have accuracy limitations)
- SQLCipher native libraries are loaded at app start (pre-loaded in background thread)
- Minimum SDK 26 (Android 8.0)

---

## Current Decisions

- **HCP Schemas**: แยกใช้งานแบบ Lite (`hermes-work-package-v1-lite` สำหรับ auto-checkpoint) และแบบ Full (`hermes-work-package-v1` สำหรับ manual/milestones)
- **HCP Attribution Guard**: การตัดสินใจใดๆ ที่ AI บันทึกขึ้นมาเอง ให้ใช้ค่าเริ่มต้น `decidedBy: "AI"` เสมอ ยกเว้นกรณีได้รับการยืนยันอย่างเป็นทางการจากผู้ใช้
- **HCP Eviction Policy**: ลิมิตไฟล์ทำงานสูงสุดตามค่า `maxCheckpoints` (เริ่มต้น 50) หากเกินจะทำการย้ายไฟล์เก่าที่สุดไปเก็บไว้ที่ `checkpoints/archive/` อัตโนมัติ
- **HCP Notification**: เพิ่มการส่งผ่านสถานะด้วยการสร้าง/อัปเดตไฟล์ `checkpoints/latest.json` เสมอในทุกครั้งที่บันทึก
- **HCP Linux Compatibility**: ปรับปรุงคำสั่งทั้งหมดให้รองรับการรันแบบ Cross-platform ผ่าน PowerShell Core (`pwsh`) บนลินุกซ์
- **Recovery:** Checkpoint sequence numbers used to find last good state for recovery (พร้อมระบบ fallback หาจุดที่เป็น `in_progress` ล่าสุดก่อนหน้าเมื่อไม่มีจุด `completed`)
- **Git:** `checkpoints/` committed to git (public), `.checkpoints/` ignored (private)
- **Service Instance:** `WeakReference` for `FloatingWidgetService.instance` to prevent context leak
- **Audio Channel:** `Channel(BUFFERED, DROP_OLDEST)` for streaming audio to prevent OOM
- **AI Timeout:** All `CompletableDeferred.await()` calls wrapped in `withTimeout(30_000)`
- **Privacy:** Notification message body stripped before sending to Gemini
- Using `Gemini 3.1 Flash Live Preview` for real-time, `Gemini 3.5 Flash` for text-only
- Using Deepgram Nova-2 model with Thai language
- Using Open-Meteo (free, no API key) for weather
- Using OpenStreetMap Overpass API (free, no API key) for nearby places
- Using SQLCipher for database encryption
- Using EncryptedSharedPreferences with AES256
- Using Material 3 / Jetpack Compose for UI
- Using OkHttp WebSocket with 10s ping interval
- Dedicated high-priority threads for audio recording and playback
- Jitter buffer: minimum 3 chunks (~120ms) before starting playback
- **API Key Quota & Disconnect Safety**: Validates API keys and inference quota prior to WebSocket setup using a POST `generateContent` request. Detects `ProtocolException` handshake rejections (HTTP 4xx errors) to immediately disable connection retries. Updates session state to `Disconnected` instantly on explicit disconnect commands to support clean cancellation in the connecting phase.
- **Theme & Manifest Consolidation**: Merged custom styles under a single application theme `Theme.GeminiMultimodalLiveAPI` and cleaned activity overrides in the manifest. Restored custom `app_logo` launcher icons.
- **Live Translate Feature Design**: Planned integration of `gemini-3.5-live-translate-preview` via a dedicated `TranslateActivity` (split-screen layout). Uses a hybrid trigger workflow (manual UI toggle + hot-swap voice trigger). Integrates Deepgram for speaker filtering (Focus Mode) and dual-transcription diarization. Configures a quick-access shortcut as a 5th segment inside the floating widget's radial picker wheel.
- **Serena & Memory MCP Configuration**: Updated Cursor `mcp.json` to absolute path (`C:\Users\ASUS\.local\bin\serena.exe`) to bypass environment PATH limits and verified correct execution of both Serena and Memory MCP servers.
- **PDF to JSON via Gemini Files API**: เลือกใช้ระบบ Google Gemini Files API ร่วมกับ Multipart/Resumable upload เพื่อส่งไฟล์ PDF หรือภาพถ่ายขึ้นไปแปลงโครงสร้างข้อมูลเป็น JSON (พร้อมตารางและคำอธิบายภาพ) แทนการเรนเดอร์บิตแมปในเครื่อง โดยบันทึกรักษาเอกสารและรูปภาพต้นฉบับไว้ถาวรในโทรศัพท์ (`filesDir/media/`) เพื่อใช้อ้างอิง และใช้ข้อมูล JSON เป็นแคชขนาดเล็กในการทำคิวรี RAG ความหน่วงต่ำ
- **ReSpeaker XVF3800 & Hybrid Search**: ออกแบบระบบข้ามขั้นตอนการฟิลเตอร์ตัดเสียงสะท้อนและเสียงรบกวนระดับซอฟต์แวร์ของ Android เพื่อประหยัด CPU 15%-25% และเลือกใช้ระบบสืบค้นข้อมูลผสมผสาน Sparse (TF-IDF/FTS5) และ Dense (Semantic Vector) โดยจัดอันดับด้วยสูตร RRF (Reciprocal Rank Fusion)


---

## Working Style

This agent interviews before implementation.

This agent prefers clarification over assumptions.

This agent records confirmed knowledge.

This agent keeps documentation synchronized.

This agent challenges requirements when better solutions exist.

This agent optimizes for long-term maintainability.

---

## Key Source Files

| Package | Key Classes |
|---------|-------------|
| `network` | GeminiLiveClient, DeepgramLiveClient, GeminiTextService, ToolDefinitions, ApiKeyValidator |
| `audio` | AudioRecorder, AudioPlayer, AudioConfig |
| `session` | SessionState, SessionStateHolder, MeetingRecordingStateHolder |
| `service` | FloatingWidgetService, GeminiToolDispatcher, WakeWordDetector, OverlayWidgetController, SessionNotificationManager, SmartNotificationListenerService, MeetingRecordingService, PhoneStateReceiver |
| `architecture` | PerceptionEngine, AttentionManager, TopicManager, DynamicRulesManager, ContextManager, SituationLogManager |
| `memory` | MemoryManager, MemoryDbHelper, MemoryEntry |
| `agent` | DatingAnalysisOrchestrator, DatingRouterAgent, DatingSkillAgentImpl, DocumentSelector, SkillAgent |
| `data` | AppPreferences, MeetingDbHelper, MeetingModel, DateProfileDbHelper, DateInsight, DatingSkillManager, DatingSkill |
| `camera` | CameraCaptureHelper |
| `calendar` | CalendarManager |
| `utils` | SpeakerLockManager, PermissionHelper, PerformanceMonitor, LocalVehicleDbHelper, DocumentParser, GoogleDriveServiceHelper, GoogleCalendarServiceHelper |
| `ui` | MainActivity, SettingsActivity, MeetingActivity, MemoryActivity, FloatingWidgetService, GeminiTileService |
| `error` | AppError |
| `work-package (HCP)` | checkpoint.ps1, resume.ps1, audit.ps1, replay.ps1, recover.ps1, hcp-guard.ps1, hcp-config.json |

---

End of Context
