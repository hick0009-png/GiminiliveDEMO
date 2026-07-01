# Work Package System (Level 2.5 — Hermes Continuity Protocol)

## Purpose

แทนที่ Agent จะส่งแค่ "สรุปบทสนทนา" (Level 1 handoff)
Work Package ส่ง **แพ็กเกจสถานะงานทั้งหมด** 
เพื่อให้ Agent ตัวต่อไปหรือ Session ใหม่กลับมาทำงานต่อได้ทันที
โดยไม่เสีย context และไม่ต้องรอให้อ่านประวัติแชทย้อนหลัง

---

## Commands

### `/checkpoint`

สร้าง checkpoint ของงานปัจจุบัน เก็บทุกอย่างที่ Agent จำเป็นต้องรู้
เพื่อกลับมาทำต่อหรือส่งต่อให้ Agent อื่น

```
/checkpoint
```

หรือระบุชื่อ:

```
/checkpoint "Opportunity Analysis - Phase 2"
```

**ผลลัพธ์:** สร้างไฟล์ `checkpoints/wp-{timestamp}-{slug}.json`
และปริ้น path กลับมา

### `/resume`

โหลด checkpoint กลับมาให้ Agent ทำงานต่อ

```
/resume checkpoints/wp-20260701-123456-opportunity-analysis.json
```

**ผลลัพธ์:** Agent อ่าน checkpoint และกลับสู่สถานะเดิม

### `/audit`

แสดง decision log และ timeline ของงานทั้งหมดที่ผ่านมา

```
/audit
```

ดูเฉพาะงาน:

```
/audit --project "วิเคราะห์ Supplier"
```

**ผลลัพธ์:** Timeline ของ checkpoint + decisions ทั้งหมด

---

## Level 2.5 Features

### Auto-Checkpoint

Agent จะสร้าง checkpoint **อัตโนมัติทุก N action** (N ตั้งค่าใน hcp-config.json)
โดยไม่ต้องรอให้ผู้ใช้พิมพ์ `/checkpoint` ทุกครั้ง

**Auto trigger เมื่อ:**
- ทำงานครบทุก 3 major actions (ตาม config)
- พบ decision สำคัญ
- พบ error/exception
- ผู้ใช้เปลี่ยน topic กะทันหัน
- ก่อนเรียกใช้ tool ที่มีผลกระทบสูง (ไฟล์ write, DB change)

**Behavior:**
- Auto checkpoint ใช้ `id` เดียวกับ checkpoint ล่าสุด (+ sequence number) เพื่อไม่ให้ทับกัน
- Agent ควรแจ้งสั้นๆ: `[auto-checkpoint] wp-xxx-003`

### Audit Trail

เมื่อ Agent ทำงานผ่าน checkpoint หลายตัว
`/audit` จะรวม decision log จากทุก checkpoint
เรียงตามเวลา และสรุปภาพรวม

Audit ประกอบด้วย:
- Timeline ของ checkpoint (เรียง chrono)
- Decision log พร้อม rationales
- รายการ open questions ที่ยังค้าง
- State transition map (progress -> pending -> completed)
- Summary statistics

### Config (hcp-config.json)

```json
{
  "version": "1.0",
  "autoCheckpoint": {
    "enabled": true,
    "frequency": 3,
    "onError": true,
    "onDecision": true,
    "onTopicSwitch": false,
    "onHighImpactTool": true
  },
  "storage": {
    "checkpointsDir": "checkpoints",
    "privateDir": ".checkpoints",
    "maxCheckpoints": 50
  },
  "audit": {
    "maxDecisionsPerCheckpoint": 20,
    "groupByProject": true
  }
}
```

---

## Work Package Schema

```json
{
  "schema": "hermes-work-package-v1",
  "id": "wp-20260701-123456-abc123",
  "sequence": 1,
  "createdAt": "2026-07-01T12:34:56+07:00",
  "updatedAt": "2026-07-01T12:34:56+07:00",
  "title": "ชื่อแพ็กเกจงาน",
  "status": "in_progress",
  "progress": 70,

  "objective": "เป้าหมายหลักของงานนี้",
  "scope": "ขอบเขตงาน",

  "completed": [
    "สิ่งที่ทำเสร็จแล้ว รายการ"
  ],
  "pending": [
    "สิ่งที่ยังต้องทำ รายการ"
  ],

  "artifacts": [
    {
      "path": "relative/path/to/file",
      "type": "file | code | doc | data",
      "description": "คำอธิบาย"
    }
  ],

  "agents": [
    {
      "role": "current | previous | next",
      "name": "ชื่อ Agent",
      "responsibility": "หน้าที่"
    }
  ],

  "decisions": [
    {
      "id": "DEC-001",
      "timestamp": "2026-07-01T12:00:00+07:00",
      "decision": "สิ่งที่ตัดสินใจ",
      "rationale": "เหตุผล",
      "alternatives": ["ทางเลือกอื่นที่ถูกปฏิเสธ"],
      "decidedBy": "ใครตัดสินใจ"
    }
  ],

  "knowledge": [
    "ข้อมูลสำคัญที่ค้นพบระหว่างทำงาน"
  ],

  "assumptions": [
    {
      "assumption": "สิ่งที่สมมติไว้",
      "confidence": "high | medium | low",
      "needsValidation": true
    }
  ],

  "risks": [
    {
      "risk": "ความเสี่ยง",
      "impact": "high | medium | low",
      "mitigation": "แนวทางลดความเสี่ยง"
    }
  ],

  "next_steps": [
    "ขั้นตอนถัดไปที่ต้องทำ เรียงตามลำดับ"
  ],

  "dependencies": [
    "สิ่งที่ต้องรอหรือต้องมีก่อน"
  ],

  "open_questions": [
    {
      "question": "คำถามที่ยังไม่มีคำตอบ",
      "assignedTo": "ผู้รับผิดชอบ (ถ้ามี)"
    }
  ],

  "memory_snapshot": [
    {
      "key": "บริบทสำคัญที่ Agent ควรจำ",
      "value": "รายละเอียด"
    }
  ],

  "context_summary": "สรุปบริบททั้งหมดสั้นๆ ที่ Agent ถัดไปต้องรู้",
  "tags": ["tag1", "tag2"]
}
```

---

## Working Directory

| Path | Purpose |
|------|---------|
| `checkpoints/` | Public checkpoints (version controlled, visible) |
| `.checkpoints/` | Private checkpoints (local only, gitignored) |
| `.agents/skills/work-package/hcp-config.json` | HCP configuration |

---

## Usage Flow

### Manual checkpoint + audit

```
/checkpoint "Implement login feature"
/checkpoint "Implement login feature - step 2"
/checkpoint "Implement login feature - step 3"
/audit
```

### Auto-checkpoint flow

```
Agent ทำงาน...
  → [auto-checkpoint] wp-login-001
Agent ทำงานต่อ...
  → [auto-checkpoint] wp-login-002
Agent พบ decision สำคัญ...
  → [auto-checkpoint] wp-login-003
Agent error...
  → [auto-checkpoint] wp-login-004 (error)
```

### Audit output

```
/audit --project "login"

=== AUDIT: login ===

Timeline:
  wp-login-001  10:00  design DB schema
  wp-login-002  10:15  implement API
  wp-login-003  10:30  DECISION: use JWT (alternatives: session, OAuth)
  wp-login-004  10:45  [ERROR] connection pool exhausted

Decisions:
  DEC-001  10:30  use JWT authentication
          reason: better mobile support
          decided by: Lead Dev

Open Questions:
  ? refresh token rotation strategy
  ? token expiry duration

Summary:
  4 checkpoints | 1 decision | 2 open questions
  70% complete | 3 todo items
```

---

## Automation (สำหรับ Agent)

### เมื่อ Agent เห็น `/checkpoint`:
1. รวบรวมสถานะงานปัจจุบันทั้งหมด
2. สร้าง JSON ตาม schema
3. เขียนไปยัง `checkpoints/wp-{timestamp}-{title-slug}.json`
4. คืน path ให้ผู้ใช้

### เมื่อ Agent เห็น `/resume <path>`:
1. อ่านไฟล์ JSON
2. แจ้งผู้ใช้ถึงสถานะที่โหลด
3. ดำเนินการต่อจากตรงนั้น

### เมื่อ Agent เห็น `/audit`:
1. อ่าน checkpoint ทั้งหมดใน `checkpoints/`
2. จัดกลุ่มตาม project/title ถ้ามี `--project`
3. แสดง timeline, decisions, open questions, summary stats

### Auto-checkpoint (Level 2.5):
1. อ่าน `hcp-config.json` เพื่อตรวจสอบ settings
2. นับ actions ที่ทำไปแล้วใน checkpoint ปัจจุบัน
3. ถ้า >= frequency → สร้าง checkpoint อัตโนมัติ
4. ใช้ sequence number ต่อจาก checkpoint ล่าสุด
5. แจ้งสั้นๆ: `[auto-checkpoint] wp-xxx-{seq}`
