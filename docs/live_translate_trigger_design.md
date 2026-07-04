# Design Proposal: Live Translate Trigger & UX Flow

This document analyzes three approaches to triggering and controlling the **Live Translate** feature inside our application and recommends a hybrid solution optimized for both screen-based and hands-free (motorcycle riding) usage.

---

## 1. Comparison of Trigger Mechanisms

| Approach | How it works | Pros | Cons |
| :--- | :--- | :--- | :--- |
| **1. UI Settings (Manual)** | Toggle switch in Settings + Language Selector dropdown. | 100% predictable, easy to test, no accidental triggers. | Requires stopping and opening the settings page. |
| **2. Auto Activation** | Detects foreign incoming call, foreign notification, or foreign speech in background. | Zero effort, feels intelligent and seamless. | High risk of false triggers (e.g. ambient noise), increases API cost. |
| **3. Voice Command** | Say *"กอหญ้า, เปิดโหมดล่ามเป็นภาษาญี่ปุ่น"* to switch mode hands-free. | Fully hands-free, matches motorcycle riding use case. | Requires local keyword parsing or a hot-swap WebSocket reconnection. |

---

## 2. Recommended Design: The "Hybrid Control" Solution

To maximize flexibility and guarantee hands-free riding safety, we propose a **hybrid workflow**:

```
                       ┌─────────────────────────┐
                       │   How to Start Session  │
                       └───────────┬─────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼ (Screen-based)          ▼ (Hands-free Rider)      ▼ (Automatic Context)
  [ Manual Toggle ]           [ Voice Command ]        [ Notification Listener ]
  Set language in settings     User says: "กอหญ้า,       Incoming Japanese call
  and click "ต่อท่อ"            แปลภาษาเป็นญี่ปุ่น"       triggers automatic popup
         │                         │                         │
         └─────────────────────────┼─────────────────────────┘
                                   ▼
                       ┌─────────────────────────┐
                       │ Connect Live Translate  │
                       │ (Thai ↔ Target)         │
                       └───────────┬─────────────┘
                                   │ (Active translation)
                                   ▼
                       ┌─────────────────────────┐
                       │   Auto-Exit (5 Min)     │
                       │  Disconnects back to    │
                       │  standard AI assistant  │
                       └─────────────────────────┘
```

### A. The Setup (UI Settings)
The foundation remains the Settings UI. The user pre-configures their preferred default translation language (e.g., English) and toggles the feature.

### B. The Hot-Swap Voice Trigger (Hands-free Rider)
If the user is currently riding and using the standard AI Assistant:
1. The user says: *"กอหญ้า, เปลี่ยนเป็นโหมดล่ามภาษาอังกฤษ"* (Koyah, switch to English interpreter mode).
2. The running Gemini Live session registers this intent, intercepts it, and says: *"กำลังเข้าสู่โหมดล่ามภาษาอังกฤษ"* (Entering English interpreter mode).
3. The app automatically **disconnects** the standard session and **reconnects** using `gemini-3.5-live-translate-preview` with `targetLanguageCode = "en"`.

### C. The Auto-Exit Guard (Battery & Quota Protection)
If the translator is active but detects no speech from either side for **5 minutes**, the app will:
1. Play a chime.
2. Automatically disconnect the translate session.
3. Revert back to the standard, lower-cost AI standby mode to save battery and quota.
