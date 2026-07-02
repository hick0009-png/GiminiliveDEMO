# Workspace Agent Rules

## Verification
- ต้องเขียน Test ก่อน แล้วค่อยแก้โค้ด (Test-Driven) เพื่อแก้ปัญหา “AI คิดว่าเสร็จ แต่รันแล้วพัง”

## Goal-Driven Execution (อัปเกรด)
- ต้องนิยาม “เสร็จ” ด้วยเกณฑ์ที่วัดผลได้จริง + วางแผนก่อนเริ่ม

## Debugging
- ต้องอ่าน Error Log + Stack Trace ให้ครบ, จำลองปัญหา, แก้ทีละจุด

## Dependencies
- ห้ามโหลด Library ใหม่ถ้าไม่จำเป็น ต้องเช็คของเดิมก่อน

## Communication
- ห้ามพูดแบบ “น่าจะเวิร์ค” ต้องบอกตรง ๆ เวลาไม่แน่ใจ

## Common Failure Modes (อาการอันตรายที่ต้องเบรกตัวเอง)
1. **Kitchen Sink** (ลามไปไกลเกิน)
2. **Wrong Abstraction** (สร้าง Abstraction ที่ซับซ้อนเกินไปหรือไม่จำเป็น)
3. **Optimistic Path** (คิดแต่เคสที่สำเร็จปกติ ไม่ครอบคลุม edge cases)
4. **Runaway Refactor** (การรีแฟคเตอร์ที่ลุกลามบานปลายออกนอกขอบเขตงาน)
