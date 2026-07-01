# Bug Report — GeminiLiveDemo

> Generated: 2026-07-01  
> Scope: Full codebase review (70+ Kotlin files)  
> Total issues: ~89 (30 Critical, 35+ Major, 24 Minor)

---

## Critical (30)

### Build & Manifest

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 1 | `proguard-rules.pro` | 16 | `-repackageclasses` moves all classes → AndroidManifest references break → `ClassNotFoundException` on every component in release build | Remove `-repackageclasses` or add `-keep` rules for all manifest components |
| 2 | `build.gradle.kts` | project | Kotlin Serialization plugin NOT applied but `kotlinx-serialization-json` declared as dependency → compile error on `@Serializable` | Add `kotlin("plugin.serialization")` plugin |
| 3 | `AndroidManifest.xml` | after 31 | Missing `POST_NOTIFICATIONS` for API 33+ → foreground service notifications silently suppressed | Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` |

### WebSocket / Network

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 4 | `GeminiLiveClient.kt` | 110 | `onClosing()` is empty override → close handshake never completes → socket leak | Call `webSocket.close(code, reason)` inside `onClosing` |
| 5 | `GeminiLiveClient.kt` | 339, 357 | Text/audio callbacks invoked on OkHttp thread instead of main thread → race conditions | Wrap in `mainHandler.post { }` |
| 6 | `GeminiMeetingService.kt` | 24 | Non-synchronized `HashMap` (`cacheMap`) accessed from multiple threads → `ConcurrentModificationException` | Use `ConcurrentHashMap` |
| 7 | `GeminiLiveClient.kt` | 110 | ... |  |

### Service (FloatingWidgetService)

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 8 | `FloatingWidgetService.kt` | 389 | No `startForeground()` guard before `onCreate` returns → system kills service with "did not call startForeground()" | Call `startForeground()` with default notification at top of `onCreate` |
| 9 | `FloatingWidgetService.kt` | 163 | Static companion `instance` leaks entire service + transitive references | Replace with `WeakReference` |

### Tool System

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 10 | `ToolDefinitions.kt` | 17+ | **Uppercase JSON Schema types** (`"OBJECT"`, `"STRING"`, `"INTEGER"`) → Gemini API silently ignores all parameterized tool schemas → tools receive empty args | Replace every `"OBJECT"` → `"object"`, `"STRING"` → `"string"`, `"INTEGER"` → `"integer"` |
| 11 | `GeminiToolDispatcher.kt` | 74-518 | 5+ handlers lack try-catch → unhandled exception crashes app | Wrap each handler in `try { } catch (e: Exception) { sendErrorResponse() }` |
| 12 | `GeminiToolDispatcher.kt` | 337, 404 | `URL(url).readText()` uses default infinite timeout → network hang forever | Set `connectTimeout` and `readTimeout` (10s) on `HttpURLConnection` |
| 13 | `LocalVehicleDbHelper.kt` | 94 | Cursor leak if exception occurs between `cursor.getString()` and `cursor.close()` | Use `cursor.use { }` |
| 14 | `GeminiToolDispatcher.kt` | 52, 67 | Background activity start (`context.startActivity()`) fails on Android 10+ (API 29+) | Use `PendingIntent` via notification or restructure camera to work in-service |

### Activities

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 15 | `MeetingActivity.kt` | 375 | `Thread.sleep(500)` on main thread after disconnect → **ANR** | Use coroutine `delay(500)` instead |
| 16 | `MeetingActivity.kt` | 647 | `MediaPlayer.prepare()` synchronous → ANR for large audio files | Use `prepareAsync()` + `OnPreparedListener` |
| 17 | `MeetingActivity.kt` | 1099 | `AlertDialog.show()` without `isFinishing` check → WindowLeak on rotation | Guard with `if (isFinishing || isDestroyed) return` |
| 18 | `MainActivity.kt` | 423 | Bitmap decode/scale/rotate on main thread → ANR | Move to `Dispatchers.Default` or `Dispatchers.IO` |
| 19 | `MainActivity.kt` | 501 | `onRequestPermissionsResult` conflates CAMERA/AUDIO with CALL_PHONE/LOCATION under same request code → wrong permission logic triggers | Use separate request codes or switch on permission string |
| 20 | `SettingsActivity.kt` | 847 | `show()` on dialog after async operation without `isFinishing` → WindowLeak | Guard all dialog `.show()` with `isFinishing` check |

### Privacy & Security

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 21 | `SmartNotificationListenerService.kt` | 61 | Full notification message text (LINE, WhatsApp, SMS) sent verbatim to Gemini API | Strip message body; only send app name + sender |
| 22 | `AppPreferences.kt` | 163 | AES-ECB fallback (no IV, deterministic encryption) | Use AES-GCM with random IV |
| 23 | `AppPreferences.kt` | 191 | Fallback key derived from `ANDROID_ID` → changes on factory reset/OTA → permanent data loss | Use app signing certificate-derived key |

### MeetingRecordingService

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 24 | `MeetingRecordingService.kt` | 449 | `runBlocking` on main thread → ANR | Launch join on `Dispatchers.IO` |
| 25 | `MeetingRecordingService.kt` | 312 | `Channel(UNLIMITED)` → OOM under network lag | Use bounded channel with `BufferOverflow.DROP_OLDEST` |

### Architecture

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 26 | `AttentionManager.kt` | 30 | No timeout for LISTENING / ACTIVE_SESSION → system stays active indefinitely | Add idle timeout Job that resets to IDLE after configurable delay |
| 27 | `SituationLogManager.kt` | 96 | No log rotation → incident JSON files accumulate forever consuming disk | Implement age-based or size-based rotation |
| 28 | `PerceptionEngine.kt` | 54 | BroadcastReceiver registered in `init` but `destroy()` must be called explicitly → receiver leak if owner replaced | Use `Closeable` pattern or lifecycle-aware scope |

### Audio / Camera

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 29 | `AudioRecorder.kt` | 31 | `isRecording` lacks `@Volatile` → recording thread hot loop may never terminate | Add `@Volatile` annotation |
| 30 | `AudioRecorder.kt` | 175 | `audioRecord.release()` while `read()` in progress on recording thread → **SIGSEGV** native crash | Add lock or join recording thread before release |
| 31 | `AudioPlayer.kt` | 191 | AudioTrack + audio focus not released on coroutine cancellation → resource leak | Release in `finally` block |
| 32 | `CameraCaptureHelper.kt` | 53 | Race between `stopPreview()` and async `onOpened()` → camera device leaked if stop called before open completes | Add `@Volatile stopped` flag checked in `onOpened` |
| 33 | `CameraCaptureHelper.kt` | 36 | `isCameraActive` lacks `@Volatile` → periodic capture loop never stops | Add `@Volatile` |

### Memory / Database

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 34 | `MemoryManager.kt` | 64 | `updateMemoryPin` loads ALL memories to toggle 1 boolean column | Add direct SQL `UPDATE` method |
| 35 | `MemoryDbHelper.kt` | 52 | `addFact` insert + eviction not atomic → concurrent calls evict each other's data | Wrap in `beginTransaction()` / `endTransaction()` |
| 36 | `GoogleDriveServiceHelper.kt` | 23 | SQL injection — folder/file names with `'` break Drive API query | Escape single quotes by doubling |
| 37 | `MeetingDbHelper.kt` | 169 | `updateSpeakerName` read-modify-write without transaction → crash between read and write loses edit | Wrap in transaction |

### Agents

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 38 | `DatingRouterAgent.kt` | 91 | `deferred.await()` no timeout → hangs forever if callback never invoked | Wrap in `withTimeout(30_000)` |
| 39 | `DatingSkillAgentImpl.kt` | 83 | Same no-timeout `await()` | Same fix |
| 40 | `DatingAnalysisOrchestrator.kt` | 163 | Same no-timeout `await()` | Same fix |
| 41 | `DatingAnalysisOrchestrator.kt` | 106 | Multi-agent runs **sequentially** → N× time of single agent | Use `async`/`awaitAll` for parallel execution |
| 42 | `DocumentSelector.kt` | 29 | Only searches `*.json` but `DocumentManager` saves as `*.txt` → uploaded documents never found | Also search `*.txt` |

### UI / ViewModel

| # | File | Line | Issue | Fix |
|---|------|------|-------|-----|
| 43 | `MemoryViewModel.kt` | 31 | `init { loadData() }` calls SQLite + file I/O synchronously on main thread → UI jank/ANR | Move to `viewModelScope.launch(Dispatchers.IO)` |

---

## Major (35+ — selected highlights)

| Area | File | Issue |
|------|------|-------|
| WebSocket | `GeminiLiveClient.kt:57` | `isConnected` missing `@Volatile` — no happens-before guarantee across threads |
| WebSocket | `GeminiLiveClient.kt:76` | API key in WebSocket URL (visible in proxy logs, server logs) |
| WebSocket | `GeminiLiveClient.kt:117` | Stale `onClosed` callback nulls new WebSocket reference |
| WebSocket | `GeminiLiveClient.kt:211` | `activeToolCalls` leak when tool response never sent |
| WebSocket | `GeminiLiveClient.kt:239` | Sensitive tool response data (vehicle info, phone numbers) logged at INFO |
| WebSocket | `DeepgramLiveClient.kt` | No reconnect logic — disconnection tears down entire session |
| WebSocket | `DeepgramLiveClient.kt` | All callbacks on WebSocket thread (not main thread) |
| WebSocket | `GeminiTextService.kt` | 80% duplicated code between `generateText` and `generateTextWithSystemInstruction` |
| Service | `FloatingWidgetService.kt` | Multiple anonymous listener inner classes leak service (C4 × 8 instances) |
| Service | `FloatingWidgetService.kt` | `transitionToState` calls `delay(100)` on `Dispatchers.Main` |
| Service | `FloatingWidgetService.kt` | Audio focus loss callback is no-op — Gemini plays over other apps |
| Service | `FloatingWidgetService.kt` | No WakeLock — CPU can sleep during WebSocket/audio processing |
| Service | `FloatingWidgetService.kt` | Sensor/location callbacks not unregistered on `disconnect()` |
| Service | `MeetingRecordingService.kt` | `MediaRecorder` not released on `start()` exception |
| Service | `MeetingRecordingService.kt` | `committedSegments` data race from WebSocket thread |
| Service | `WakeWordDetector.kt` | `onError(0)` misused for success signal — misleading |
| Service | `WakeWordDetector.kt` | Auto-mute system notifications (side effect) |
| Service | `WakeWordDetector.kt` | Continuous SpeechRecognizer — high battery drain |
| Service | `OverlayWidgetController.kt` | Click/double-click race via both `performClick` + `setOnClickListener` |
| Activity | `MainActivity.kt` | No `onSaveInstanceState` — all state lost on rotation |
| Activity | `MainActivity.kt` | TextWatchers never removed — Activity leak |
| Activity | `MainActivity.kt` | Camera processing on main thread |
| Activity | `MeetingActivity.kt` | `playRunnable` inner class + Handler — memory leak |
| Activity | `MeetingActivity.kt` | No `onPause`/`onResume` — MediaPlayer not paused on Home |
| Activity | `GeminiTileService.kt` | Tile state set optimistically before async operation completes |
| Tool | `GeminiToolDispatcher.kt` | `CancellationTokenSource` leaked on failure path |
| Tool | `GeminiToolDispatcher.kt` | SQLite operations on main thread (saveVehicleInfo, queryVehicleInfo) |
| Tool | `GeminiToolDispatcher.kt` | `endCall` via reflection on private API — breaks on API 33+ |
| Tool | `GeminiToolDispatcher.kt` | Double response for same `callId` on phone call permission miss |
| Tool | `AppError.kt:8` | `AppError.Tool` defined but never constructed (12 tool handlers log errors but never post to error flow) |
| Tool | `AppError.kt:32` | Catch-all `Api(-1)` conflates non-API errors like NPE |
| Arch | `TopicManager.kt` | Unbounded keyword list growth in `addDynamicKeyword` |
| Arch | `DynamicRulesManager.kt` | Full SharedPrefs+JSON serialization on every get/save |
| Arch | `ContextManager.kt` | Public mutable fields without synchronization |
| Arch | `SituationLogManager.kt` | Synchronous file I/O on caller thread |
| Data | `DateProfileDbHelper.kt` | Synchronous SQLite on calling thread |
| Data | `LocalVehicleDbHelper.kt` | All SQLite synchronous on calling thread |
| Data | `DatingSkillManager.kt` | File I/O + no synchronization → race/corruption |
| Google | `GoogleCalendarServiceHelper.kt` | `CalendarEvent.id` defaults to `""` → update/delete silently fail |
| Google | `GoogleDriveServiceHelper.kt` | No OAuth token-refresh handling |
| Utils | `DocumentParser.kt` | TF-IDF + file I/O on calling thread |
| Utils | `PermissionHelper.kt` | Required + optional permissions conflated → dialog loop on denial |
| Utils | `PerformanceMonitor.kt` | Processing time measured but never evaluated |
| Utils | `SpeakerLockManager.kt` | `activeSpeakerId` not `@Volatile` + timer coroutine may leak |
| Agent | `DatingAnalysisOrchestrator.kt` | No partial failure handling — any agent crash fails entire pipeline |
| UI | `MemoryActivity.kt` | No loading/error state from ViewModel — user gets zero feedback on failure |
| Cross | All | Static `FloatingWidgetService.instance` — context leak |
| Cross | All | No dependency injection — untestable singletons everywhere |

---

## Minor (24 — selected highlights)

- Hardcoded Thai strings throughout (6+ files) — not localized
- `notifyDataSetChanged()` instead of `DiffUtil` in MeetingActivity adapters
- Duplicate `dpToPx` / `formatTime` utilities across activities
- `cacheDir` used for permanent meeting audio files (may be deleted by system)
- Redundant `ACCESS_COARSE_LOCATION` (implied by `ACCESS_FINE_LOCATION`)
- Theme name inconsistency (`Theme.GeminiLiveDemo` vs `Theme.GeminiMultimodalLiveAPI`)
- `app_logo` drawable used instead of conventional `mipmap/ic_launcher`
- Template "Sample" comments left in `backup_rules.xml` / `data_extraction_rules.xml`
- Emoji in log output (encoding issues in logcat parsers)
- Mixing version catalog + hardcoded dependency strings
- `isMinifyEnabled = false` in release build (unused code in APK)
- `onBackPressed` deprecated (API 34+) — should use `OnBackPressedDispatcher`
- `SearchMemories` LIKE query doesn't escape `%` / `_` wildcards
- Memory search results unordered (no `ORDER BY`)
- Audio thread at `MAX_PRIORITY` may starve UI thread
- Continuous pulse animation wastes GPU when overlay visible
- No `onTrimMemory` / `onLowMemory` override in FloatingWidgetService

---

## Top 10 Priority Fix Order

```
Rank  Issue                                           Impact                  Status
────────────────────────────────────────────────────────────────────────────────────────
 1    ToolDefinitions: uppercase JSON types            ALL tools broken        ✅ FIXED (commit 216fd41)
 2    ProGuard: -repackageclasses                      Release build crash     ✅ FIXED (commit 153a899)
 3    POST_NOTIFICATIONS missing                       Notifications silent    ✅ FIXED (commit 216fd41)
 4    Thread.sleep + MediaPlayer.prepare               ANR                     ✅ FIXED (commit 153a899)
 5    FloatingWidgetService: no startForeground()      Service crash           ✅ FIXED (commit 153a899)
 6    NotificationListener: message content leak       Privacy breach          ✅ FIXED (commit 153a899)
 7    deferred.await() no timeout (3 locations)        Coroutine hang          ✅ FIXED (commit 153a899)
 8    Channel(UNLIMITED)                               OOM                     ✅ FIXED (commit 153a899)
 9    AudioRecorder: release while reading             SIGSEGV                 ✅ FIXED (commit 153a899)
10    CameraCaptureHelper: stop/opened race            Camera device leak      ✅ FIXED (commit 153a899)
```

> All 10 critical bugs fixed as of 2026-07-01. See checkpoints `wp-20260701-221357-fix-10-easy-bugs-write-tests-001` and `wp-20260701-222601-fix-10-easy-bugs-write-tests-session-resume-002`.
