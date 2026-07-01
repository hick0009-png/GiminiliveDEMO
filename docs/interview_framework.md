# Interview Framework

Purpose

Transform vague requests into executable specifications.

---

Stage 1

Understand the Goal

Questions

Why is this needed?

Who requested it?

Who benefits?

How is success measured?

Example (project-specific)

User says: "Add a mute button."

Goal: Allow user to manually mute microphone input during an active Gemini session without disconnecting.

---

Stage 2

Define Vocabulary

Identify project-specific words.

Create or update glossary.

Example (project-specific)

"Focus Mode" = Solo mode using Deepgram speaker diarization

"Standby" = Connected but waiting for wake word

"Dynamic Rule" = Voice-tunable behavior rule saved via save_system_rule

"Perception Event" = Sensor-derived state change (motion, location, screen)

---

Stage 3

Discover Constraints

Budget

Deadline

Security

Performance

Compliance

Technology

Example (project-specific)

- Must support Thai language
- Must work as foreground service
- Database must be encrypted (SQLCipher)
- API keys must be stored encrypted
- Must handle network disconnection gracefully
- Minimum SDK 26

---

Stage 4

Discover Business Rules

Approval logic

Permissions

Validation

Exceptions

Automation

Example (project-specific)

- Never guess current time or weather — always call the tool
- Auto-revert to Standby after 30s silence
- Auto-disconnect after 5min total silence
- Dating mode auto-sleeps after 5min silence or 60min session

---

Stage 5

Identify Edge Cases

Failure

Duplicate

Rollback

Cancellation

Timeout

Offline

Example (project-specific)

- What if Deepgram connects but Gemini fails?
- What if user speaks during AI playback?
- What if GPS is off during weather request?
- What if speaker diarization misidentifies the speaker?
- What if WebSocket reconnects while a phone call is in progress?

---

Stage 6

Explore Alternatives

The agent should propose simpler solutions.

Compare

Option A

Option B

Option C

Explain trade-offs.

---

Stage 7

Risk Assessment

Technical

Business

Financial

Operational

Security

---

Stage 8

Executive Summary

Summarize

Objectives

Definitions

Constraints

Architecture

Risks

Decisions

Unknowns

---

Stage 9

Request Confirmation

Never implement before confirmation.

---

Stage 10

Implementation

Only after alignment.
