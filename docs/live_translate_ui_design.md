# Design Proposal: Dedicated TranslateActivity & Floating Widget Shortcut

This document compares integrating the **Live Translate** feature into our existing screens vs. creating a dedicated screen (`TranslateActivity`), and details the quick-access integration inside the floating widget's radial picker wheel.

---

## 1. UI Integration Comparison

| Approach | UX Flow | Pros | Cons |
| :--- | :--- | :--- | :--- |
| **Option A: Integrate into MainActivity / MeetingActivity** | Text prints in the existing console chat window or meeting transcript list. | No new files, reuse existing adapters and UI views. | Clutters the interface. `MainActivity` (motorcycle console) and `MeetingActivity` (meeting record logs) are not visually suited for real-time face-to-face split-screen translation. |
| **Option B: Dedicated Screen (`TranslateActivity`)** | Opens a separate window with a split-screen layout designed for face-to-face conversations. | **Premium visual layout** (large font, split panels, opposite text orientations). Complete code isolation (prevents regressions in riding helper logic). | Adds a new activity file and manifest entry. |

---

## 2. Floating Widget Radial Picker Integration

To allow ultra-fast access to the **Interpreter Mode**, we will integrate a shortcut directly into the floating bubble's radial menu (วงล้อเลือกสกิล) when the user performs a long-press.

```
       [Long-Press Floating Bubble]
                   │
                   ▼
     ┌───────────────────────────┐
     │ Radial Wheel (5 segments) │
     │  1. Sweet Flirting        │
     │  2. Icebreaker            │
     │  3. Deep Talk             │
     │  4. Witty Teasing         │
     │  5. 🌐 INTERPRETER MODE   │  ◄── Added Entry!
     └─────────────┬─────────────┘
                   │
                   ▼ (User selects segment 5)
     ┌───────────────────────────┐
     │ Launch TranslateActivity  │
     └───────────────────────────┘
```

### Technical Implementation:
1. **DatingSkill List Expansion**:
   - In `FloatingWidgetService.kt`, append a custom virtual `DatingSkill` with ID `"live_translate"` and name `"โหมดล่ามแปลภาษา (Interpreter)"` to the list loaded from the manager.
2. **Radial Limit Increase**:
   - In `RadialSkillPickerView.kt`, update `.take(4)` to `.take(5)` to allow rendering 5 quadrants/segments cleanly.
3. **Trigger Action**:
   - In `FloatingWidgetService`'s selection callback, if the selected skill ID is `"live_translate"`, launch `TranslateActivity` with `Intent.FLAG_ACTIVITY_NEW_TASK`.

---

## 3. Dedicated `TranslateActivity` Layout ("Interpreter Mode")

```
┌────────────────────────────────────────────────────────┐
│  [Back]               INTERPRETER MODE          [Close]│
├────────────────────────────────────────────────────────┤
│                                                        │
│  🇯🇵 JAPANESE (Target)                                  │
│  "こんにちは、お会いできて嬉しいです。"                     │
│  [Play Sound Icon]                                     │
│                                                        │
├────────────────────────────────────────────────────────┤
│                                                        │
│  🇹🇭 THAI (Source)                                      │
│  "สวัสดีครับ ยินดีที่ได้พบกันครับ"                         │
│  [Mic Active Indicator 🔴]                             │
│                                                        │
├────────────────────────────────────────────────────────┤
│  [TH ⇄ JP Selector]   [Settings Toggle]    [Reset Button]│
└────────────────────────────────────────────────────────┘
```
