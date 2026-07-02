# HCP Tool Manual — Hermes Continuity Protocol

## สารบัญ

1. [ภาพรวม](#1-ภาพรวม)
2. [/checkpoint — บันทึกสถานะงาน](#2-checkpoint--บันทึกสถานะงาน)
3. [/resume — โหลด checkpoint กลับมา](#3-resume--โหลด-checkpoint-กลับมา)
4. [/audit — ดูสรุปการทำงาน](#4-audit--ดูสรุปการทำงาน)
5. [/replay — ดูประวัติทั้งหมด](#5-replay--ดูประวัติทั้งหมด)
6. [/recover — กู้คืนจาก error](#6-recover--กู้คืนจาก-error)
7. [hcp-guard — Error guard wrapper](#7-hcp-guard--error-guard-wrapper)
8. [hcp-config.json — ตั้งค่าระบบ](#8-hcp-configjson--ตั้งค่าระบบ)
9. [Git Workflow](#9-git-workflow)
10. [Session Handoff](#10-session-handoff)
11. [Error Recovery Flow](#11-error-recovery-flow)
12. [JSON Schema Reference](#12-json-schema-reference)
13. [Cheatsheet](#13-cheatsheet)

---

## 1. ภาพรวม

เครื่องมือ HCP (Hermes Continuity Protocol) ทั้งหมดอยู่ใน:
```
.agents/skills/work-package/
├── SKILL.md          — คำอธิบาย skill + schema
├── checkpoint.ps1    — สร้าง checkpoint
├── resume.ps1        — โหลด checkpoint
├── audit.ps1         — ดู timeline + decisions
├── replay.ps1        — replay ประวัติทั้งหมด
├── recover.ps1       — กู้คืนจาก error
├── hcp-guard.ps1     — execute command + auto error checkpoint
└── hcp-config.json   — ตั้งค่าระบบ
```

Checkpoint JSON เก็บที่:
```
checkpoints/wp-{timestamp}-{title}-{seq}.json
```

---

## 2. /checkpoint — บันทึกสถานะงาน

### จุดประสงค์
บันทึกสถานะปัจจุบันของงานเป็น JSON ที่ AI หรือมนุษย์อ่านได้

### วิธีใช้

**บันทึกงานทั่วไป:**
```powershell
# Manual checkpoint
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\checkpoint.ps1" -Title "Implement login" -Objective "สร้างหน้า Login" -Completed '["ออกแบบ UI", "เขียน API"]' -Pending '["เขียนเทส"]' -Progress 50
```

**Auto checkpoint (sequence number อัตโนมัติ):**
```powershell
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\checkpoint.ps1" -Title "Implement login" -Auto
```

**Error checkpoint:**
```powershell
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\checkpoint.ps1" -OnError -ErrorType "api_failure" -ErrorMessage "Connection timeout" -ToolUsed "GeminiLiveClient" -RecoveryInstruction "Check network and retry"
```

### Parameters

| Parameter | Type | Default | คำอธิบาย |
|-----------|------|---------|----------|
| `-Title` | string | "" | ชื่อ checkpoint |
| `-Objective` | string | "" | เป้าหมายของงาน |
| `-Completed` | JSON string | "[]" | รายการที่ทำเสร็จแล้ว |
| `-Pending` | JSON string | "[]" | รายการที่ยังต้องทำ |
| `-Decisions` | JSON string | "[]" | การตัดสินใจ |
| `-Knowledge` | JSON string | "[]" | ความรู้ที่ค้นพบ |
| `-Risks` | JSON string | "[]" | ความเสี่ยง |
| `-NextSteps` | JSON string | "[]" | ขั้นตอนถัดไป |
| `-ContextSummary` | string | "" | สรุปบริบท |
| `-Status` | string | "in_progress" | สถานะ (in_progress/completed/error/failed) |
| `-Progress` | int | 0 | ความคืบหน้า 0-100 |
| `-Auto` | switch | false | สร้าง checkpoint อัตโนมัติ (เพิ่ม sequence) |
| `-OnError` | switch | false | สร้าง error checkpoint |
| `-ErrorType` | string | "" | ประเภท error |
| `-ErrorMessage` | string | "" | ข้อความ error |
| `-ToolUsed` | string | "" | tool ที่ทำให้ error |
| `-ActionsBeforeError` | JSON string | "[]" | actions ก่อน error |
| `-RecoveryInstruction` | string | "" | คำแนะนำการกู้คืน |
| `-ErrorException` | string | "" | exception stack trace |

### Output

```
Checkpoint saved:
checkpoints/wp-20260701-123456-implement-login.json
```

หรือ auto mode:
```
[auto-checkpoint] wp-20260701-123456-implement-login-001
```

หรือ error mode:
```
[error-checkpoint] wp-20260701-123456-error-recovery-001
```

---

## 3. /resume — โหลด checkpoint กลับมา

### จุดประสงค์
โหลด checkpoint เพื่อกลับมาทำงานต่อใน session ใหม่

### วิธีใช้

```powershell
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\resume.ps1" -Path "checkpoints/wp-20260701-210000-level3-hcp-004.json"
```

### Parameters

| Parameter | Type | Required | คำอธิบาย |
|-----------|------|----------|----------|
| `-Path` | string | ✅ | Path ไปยัง checkpoint JSON |

### Output
แสดง title, status, progress, objective, completed, pending, next_steps, decisions, knowledge, open_questions, context_summary

---

## 4. /audit — ดูสรุปการทำงาน

### จุดประสงค์
ดูภาพรวมของ checkpoint ทั้งหมด ทั้ง timeline, decisions, knowledge, open questions

### วิธีใช้

```powershell
# ดูทั้งหมด
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\audit.ps1"

# กรองตาม project
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\audit.ps1" -Project "git"

# กรองตาม title
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\audit.ps1" -Title "Level 3"

# JSON output
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\audit.ps1" -Json
```

### Parameters

| Parameter | Type | คำอธิบาย |
|-----------|------|----------|
| `-Project` | string | กรองตาม project name |
| `-Title` | string | กรองตาม title |
| `-Json` | switch | ส่งออกเป็น JSON |

### Output (Human-readable)

```
==========================================
AUDIT REPORT
Project:
Checkpoints: 4 | Errors: 0
Avg Progress: 75%
==========================================

--- Timeline ---
  [DONE] 2026-07-01T20:00:00+07:00 | Build Work Package System Level 2.5 (100%)
  [DONE] 2026-07-01T20:30:00+07:00 | Git Init + Push + Resolve Open Questions (100%)
          2026-07-01T20:45:00+07:00 | Pre-Level 3 HCP (0%)
  [DONE] 2026-07-01T21:00:00+07:00 | Level 3 HCP — /replay + /recover + error recovery (100%)

--- Decisions (8) ---
  [wp-xxx-001] ใช้ JSON schema (hermes-work-package-v1) แทน Markdown สำหรับ checkpoint
    reason: AI, โปรแกรม, Database, Dashboard อ่าน JSON ได้ทั้งหมด
    by: User + AI

--- Knowledge (13) ---
  * Level 3 HCP = replay + recover + audit trail

--- Status Breakdown ---
  completed: 3
  in_progress: 1
```

---

## 5. /replay — ดูประวัติทั้งหมด

### จุดประสงค์
แสดงประวัติการทำงานแบบละเอียด เรียงตาม sequence รวม state transition log

### วิธีใช้

```powershell
# ทุก checkpoint
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\replay.ps1"

# กรองตาม ID
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\replay.ps1" -Id "wp-20260701-210000-level3-hcp-004"

# กรองตาม project
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\replay.ps1" -Project "git"

# แบบเต็ม รวม memory_snapshot
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\replay.ps1" -Full

# JSON output
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\replay.ps1" -Json
```

### Parameters

| Parameter | Type | คำอธิบาย |
|-----------|------|----------|
| `-Id` | string | กรองตาม checkpoint ID |
| `-Project` | string | กรองตาม project name |
| `-Json` | switch | ส่งออกเป็น JSON |
| `-Full` | switch | แสดง memory_snapshot ด้วย |

### Output

```
==========================================
REPLAY
Entries: 4
==========================================

==========================================
Checkpoint #1: Build Work Package System Level 2.5
Time: 2026-07-01T20:00:00+07:00
Status: completed (100%)
Objective: สร้าง Work Package System...

  [Completed] 15 items:
    + Read และวิเคราะห์โค้ด...
    + อัปเดต docs/context.md...

  [Decisions]
    [DEC-001] ใช้ JSON schema...

  [Knowledge]
    * Work Package มี 3 Levels...

==========================================
STATE TRANSITION LOG
------------------------------------------
  #1 wp-xxx-001 completed | 100% (+100%) | +15 done, 0 pending, 4 decisions
  #2 wp-xxx-002 completed | 100% (+0%) | +6 done, 0 pending, 2 decisions
```

---

## 6. /recover — กู้คืนจาก error

### จุดประสงค์
เมื่อ checkpoint มี status = error/failed/cancelled ใช้ recover เพื่อหา last good state

### วิธีใช้

```powershell
# recover อัตโนมัติจาก error checkpoint ล่าสุด
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\recover.ps1"

# recover จาก checkpoint ที่ระบุ
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\recover.ps1" -Id "wp-20260701-123456-error-recovery-001"

# dry run (จำลอง ไม่ทำอะไรจริง)
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\recover.ps1" -Id "wp-20260701-123456-error-recovery-001" -DryRun

# JSON output
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\recover.ps1" -Json
```

### Parameters

| Parameter | Type | คำอธิบาย |
|-----------|------|----------|
| `-Id` | string | ID ของ checkpoint ที่ต้องการ recover |
| `-Status` | string | สถานะที่ถือว่า error (default: "error") |
| `-Json` | switch | ส่งออกเป็น JSON |
| `-DryRun` | switch | จำลอง recovery โดยไม่ทำอะไร |

### Output

```
==========================================
RECOVERY PLAN
==========================================

[ERROR CHECKPOINT]
  ID:       wp-xxx-err-001
  Title:    Error Recovery - Testing
  Status:   error
  Error Details:
    Type:    script_execution_error
    Message: Command exited with code 1
    Tool:    Testing bad command
    Recovery hint: Check the error above and retry

[RESTORE POINT]
  ID:       wp-xxx-good-004
  Title:    Level 3 HCP
  Status:   completed (100%)
  Completed so far (7): ...
  Decisions to preserve: ...
  Knowledge to reload: ...
  Memory Snapshot to restore: ...

RECOVERY INSTRUCTIONS
1. Load context from checkpoint: wp-xxx-good-004
2. Restore memory_snapshot values
3. Continue with pending items
```

---

## 7. hcp-guard — Error guard wrapper

### จุดประสงค์
Execute คำสั่งหรือ script โดยอัตโนมัติ:
- ถ้าสำเร็จ → (optional) auto-checkpoint
- ถ้า error → สร้าง error checkpoint ทันที

### วิธีใช้

```powershell
# Basic
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\hcp-guard.ps1" -Command "git push" -Context "Pushing to GitHub"

# Auto-checkpoint on success
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\hcp-guard.ps1" -Command "git push" -Context "Pushing to GitHub" -CreateAutoCheckpointOnSuccess

# พร้อม context สำหรับ error checkpoint
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\hcp-guard.ps1" -Command "npm run build" -Context "Build project" -RecoveryInstruction "Fix build errors and retry"

# ข้าม error (ถ้าไม่สน error code)
PowerShell -ExecutionPolicy Bypass -Command "& '.\.agents\skills\work-package\hcp-guard.ps1' -PassThru -Command 'dir nonexistent'"
```

### Parameters

| Parameter | Type | Required | คำอธิบาย |
|-----------|------|----------|----------|
| `-Command` | string | ✅ | คำสั่งที่ต้องการ execute |
| `-Context` | string | | คำอธิบาย context |
| `-Title` | string | | ชื่อ checkpoint (auto ถ้าไม่ใส่) |
| `-Objective` | string | | เป้าหมาย |
| `-Completed` | JSON string | | รายการที่ทำเสร็จ |
| `-Pending` | JSON string | | รายการที่ต้องทำ |
| `-Decisions` | JSON string | | การตัดสินใจ |
| `-Knowledge` | JSON string | | ความรู้ |
| `-NextSteps` | JSON string | | ขั้นตอนถัดไป |
| `-RecoveryInstruction` | string | | คำแนะนำการกู้คืน |
| `-CreateAutoCheckpointOnSuccess` | switch | | สร้าง auto-checkpoint เมื่อสำเร็จ |
| `-PassThru` | switch | | ส่งผลลัพธ์กลับ (ไม่ exit 1) |

### Output (success)

```
[hcp-guard] Running: Pushing to GitHub
[hcp-guard] Success.
[auto-checkpoint] wp-20260701-123456-pushing-to-github-001
(ผลลัพธ์ของคำสั่ง)
```

### Output (error)

```
[hcp-guard] Running: Pushing to GitHub
[hcp-guard] ERROR detected. Creating error checkpoint...
[error-checkpoint] wp-20260701-123456-error-recovery-pushing-to-github-001
```

---

## 8. hcp-config.json — ตั้งค่าระบบ

### ตำแหน่ง
```
.agents/skills/work-package/hcp-config.json
```

### โครงสร้าง

```json
{
  "version": "1.0",
  "autoCheckpoint": {
    "enabled": true,
    "frequency": 3,
    "onError": {
      "enabled": true,
      "captureStackTrace": true,
      "createCheckpointOnExitCode": true,
      "createCheckpointOnException": true
    },
    "onDecision": true,
    "onTopicSwitch": false,
    "onHighImpactTool": true
  },
  "storage": {
    "checkpointsDir": "checkpoints",
    "privateDir": ".checkpoints",
    "maxCheckpoints": 50,
    "retentionDays": 90
  },
  "audit": {
    "maxDecisionsPerCheckpoint": 20,
    "groupByProject": true
  },
  "replay": {
    "showMemorySnapshot": false,
    "maxEntries": 100
  },
  "recover": {
    "autoRestoreMemory": true,
    "requireConfirmation": true,
    "defaultRecoverFromStatus": ["error", "failed", "cancelled"]
  }
}
```

| Key | คำอธิบาย |
|-----|----------|
| `autoCheckpoint.enabled` | เปิด/ปิด auto-checkpoint |
| `autoCheckpoint.frequency` | สร้าง checkpoint ทุกกี่ action |
| `autoCheckpoint.onError` | สร้าง error checkpoint อัตโนมัติเมื่อเจอ error |
| `autoCheckpoint.onDecision` | สร้าง checkpoint เมื่อมีการตัดสินใจ |
| `autoCheckpoint.onHighImpactTool` | สร้าง checkpoint ก่อนเรียก tool สำคัญ |
| `storage.maxCheckpoints` | จำนวน checkpoint สูงสุดที่เก็บ |
| `storage.retentionDays` | เก็บ checkpoint กี่วัน |
| `audit.groupByProject` | จัดกลุ่ม audit ตาม project |
| `replay.maxEntries` | จำนวน checkpoint สูงสุดที่ replay |
| `recover.defaultRecoverFromStatus` | สถานะไหนที่ถือว่า error |

---

## 9. Git Workflow

### Push ก่อนเปลี่ยน session

```powershell
git add -A
git commit -m "สิ่งที่ทำใน session นี้"
git push
```

### Pull เมื่อเปิด session ใหม่

```powershell
git pull
```

### ดูประวัติ

```powershell
git log --oneline
```

### Remote

```powershell
git remote -v
# origin  https://github.com/hick0009-png/GiminiliveDEMO.git (fetch)
# origin  https://github.com/hick0009-png/GiminiliveDEMO.git (push)
```

---

## 10. Session Handoff

เมื่อเปลี่ยน session (เช่น จาก opencode ไป Cursor หรือ session ใหม่):

### ปิด session เก่า
```powershell
git add -A
git commit -m "Session end: <summary>"
git push
```

### เปิด session ใหม่
```powershell
git pull
PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\resume.ps1" -Path "checkpoints/wp-<ล่าสุด>.json"
```

### ข้อมูลสำคัญที่ต้องส่งต่อให้ Agent
1. checkpoint ล่าสุด (read resume output)
2. งานค้าง (จาก checkpoint pending + next_steps)
3. memory_snapshot (git remote, branch, hcp level)
4. Remote URL ถ้าต้องการ push/pull

---

## 11. Error Recovery Flow

```
1. เกิด Error
   ↓
2. สร้าง error checkpoint (manual หรือ hcp-guard)
   ↓
3. [error-checkpoint] wp-xxx-error-001
   ↓
4. /recover → หา last good checkpoint
   ↓
5. แสดง restore point + recovery instructions
   ↓
6. AI โหลด context จาก restore point
   ↓
7. ทำงานต่อจาก pending items
```

### ตัวอย่าง

```powershell
# Step 1: Error เกิดขึ้น
# Step 2: สร้าง error checkpoint
.\hcp-guard.ps1 -Command "git push" -Context "Pushing changes"

# หรือ manual
.\checkpoint.ps1 -OnError -ErrorType "api_failure" -ErrorMessage "timeout"

# Step 4: กู้คืน
.\recover.ps1

# หรือระบุ checkpoint
.\recover.ps1 -Id "wp-xxx-error-001"
```

---

## 12. JSON Schema Reference

```json
{
  "schema": "hermes-work-package-v1",
  "id": "wp-20260701-123456-my-work-001",
  "sequence": 1,
  "createdAt": "2026-07-01T12:34:56+07:00",
  "updatedAt": "2026-07-01T12:34:56+07:00",
  "title": "ชื่อแพ็กเกจงาน",
  "status": "in_progress | completed | error | failed | cancelled",
  "progress": 70,

  "objective": "เป้าหมายหลัก",
  "scope": "ขอบเขต",

  "completed": ["สิ่งที่ทำเสร็จแล้ว"],
  "pending": ["สิ่งที่ยังต้องทำ"],

  "artifacts": [
    {"path": "relative/path", "type": "file | code | doc | data | config", "description": "คำอธิบาย"}
  ],

  "agents": [
    {"role": "current | previous | next", "name": "ชื่อ Agent", "responsibility": "หน้าที่"}
  ],

  "decisions": [
    {
      "id": "DEC-001",
      "timestamp": "2026-07-01T12:00:00+07:00",
      "decision": "สิ่งที่ตัดสินใจ",
      "rationale": "เหตุผล",
      "alternatives": ["ทางเลือกอื่น"],
      "decidedBy": "User | AI | User + AI"
    }
  ],

  "knowledge": ["ข้อมูลที่ค้นพบ"],
  "assumptions": [{"assumption": "...", "confidence": "high | medium | low", "needsValidation": true}],
  "risks": [{"risk": "...", "impact": "high | medium | low", "mitigation": "..."}],
  "next_steps": ["ขั้นตอนถัดไป"],
  "dependencies": ["สิ่งที่ต้องรอ"],
  "open_questions": [{"question": "...", "assignedTo": ""}],

  "error_info": {
    "error_type": "tool_error | api_failure | timeout | exception | user_abort | script_execution_error | unknown",
    "error_message": "รายละเอียด error",
    "error_timestamp": "2026-07-01T12:34:56+07:00",
    "actions_before_error": ["action 1"],
    "tool_used": "ชื่อ tool",
    "stack_trace": "exception details",
    "recovery_instruction": "สิ่งที่ AI ควรทำต่อ"
  },

  "memory_snapshot": [
    {"key": "บริบทสำคัญ", "value": "รายละเอียด"}
  ],

  "context_summary": "สรุปบริบททั้งหมดสั้นๆ",
  "tags": ["tag1", "tag2"]
}
```

---

## 13. Cheatsheet

### การเรียกใช้ scripts

| คำสั่งใน Cursor/AI | PowerShell command |
|-------------------|-------------------|
| `/checkpoint` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\checkpoint.ps1" -Title "..." -Auto` |
| `/resume wp-xxx.json` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\resume.ps1" -Path "checkpoints/wp-xxx.json"` |
| `/audit` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\audit.ps1"` |
| `/replay` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\replay.ps1"` |
| `/recover` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\recover.ps1"` |
| `/recover --id xxx` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\recover.ps1" -Id "xxx"` |
| `hcp-guard <cmd>` | `PowerShell -ExecutionPolicy Bypass -File ".agents\skills\work-package\hcp-guard.ps1" -Command "<cmd>" -Context "<desc>"` |

### Quick reference

```
/create checkpoint         → checkpoint.ps1 -Title "..." -Auto
/save error state          → checkpoint.ps1 -OnError -ErrorType "..." -ErrorMessage "..."
/resume from checkpoint    → resume.ps1 -Path "checkpoints/wp-xxx.json"
/show audit                → audit.ps1
/show audit (JSON)         → audit.ps1 -Json
/replay history            → replay.ps1
/recover from error        → recover.ps1
/run with error guard      → hcp-guard.ps1 -Command "..." -Context "..."
/git push                  → git add -A; git commit -m "..."; git push
/git pull                  → git pull
```

---

## 14. กฎระเบียบและข้อกำหนดระบบเพิ่มเติม (HCP Improvements)

### 14.1 ประเภท Checkpoint (Lite vs. Full)
- **Lite Checkpoint (`hermes-work-package-v1-lite`)**: บันทึกความคืบหน้าของงานทั่วไปในระหว่างการพัฒนาแบบอัตโนมัติ (Auto) โดยไม่ต้องใช้ฟิลด์วิเคราะห์ข้อมูลเชิงลึก (เช่น decisions, knowledge) เพื่อลด token และความล่าช้า
- **Full Checkpoint (`hermes-work-package-v1`)**: ใช้เมื่อประมวลผลคำสั่ง manual `/checkpoint` บันทึกการเกิดข้อผิดพลาด หรือการปิดเฟสงานที่มีการวิเคราะห์ประเมินผล

### 14.2 หลักการกำหนดผู้ออกการตัดสินใจ (`decidedBy`)
- **ห้ามเดา**: ห้ามไม่ให้เอเจนต์สมมติหรืออ้างอิงชื่อบุคคลอื่นๆ ในการตัดสินใจโดยไม่จำเป็น
- **Default**: กำหนดเป็น `"AI"` เสมอสำหรับการตัดสินใจที่เอเจนต์บันทึกขึ้นมาด้วยตนเอง ยกเว้นกรณีที่ผู้ใช้ออกปากยืนยันอย่างเป็นทางการในแชท ให้ใช้ `"User"` หรือ `"User + AI"`

### 14.3 การจำกัดจำนวน Checkpoint (Eviction Policy)
- ดึงข้อมูล `maxCheckpoints` จากไฟล์คอนฟิก `hcp-config.json`
- หากไฟล์ Checkpoint มีจำนวนรวมเกินค่าดังกล่าว ระบบจะย้ายไฟล์ Checkpoint ที่เก่าที่สุดไปยังโฟลเดอร์ `checkpoints/archive/` ทันทีเพื่อเก็บข้อมูลสำรองไว้แต่ไม่รบกวนการทำงานปัจจุบัน

### 14.4 โปรโตคอลการแจ้งเตือนสถานะเอเจนต์ (`latest.json`)
- ทุกครั้งที่มีการบันทึกสถานะ Checkpoint ระบบจะสร้าง/เขียนทับไฟล์ [checkpoints/latest.json](file:///d:/New%20folder%20(2)/GeminiLiveDemo/checkpoints/latest.json)
- ไฟล์นี้ทำหน้าที่เป็นตัวบอกสถานะปัจจุบันให้กับเอเจนต์ถัดไป หรือระบบ Orchestrator เพื่อสั่ง Resume การทำงานได้ทันที

### 14.5 การใช้งานบน Linux VPS (PowerShell Core)
- การประมวลผลคำสั่งสามารถรันได้อย่างสมบูรณ์บน Linux VPS ผ่าน **PowerShell Core (`pwsh`)**
- หลีกเลี่ยง Windows API หรือการต่อ Path ที่เจาะจงเฉพาะ OS (แนะนำให้รันด้วย `pwsh` และติดตั้งตามคู่มือใน [SKILL.md](file:///d:/New%20folder%20(2)/GeminiLiveDemo/.agents/skills/work-package/SKILL.md))
