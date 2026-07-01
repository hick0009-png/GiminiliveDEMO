# GeminiLiveDemo

แอปพลิเคชัน Android ตัวอย่างที่สาธิตการใช้งาน **Gemini Multimodal Live API** ร่วมกับการบันทึกเสียง กล้องถ่ายรูป ปฏิทิน (Google Calendar) การดึงข้อมูลเอกสาร (Google Drive/PDF) และระบบความจำ (Memory Context)

## ฟีเจอร์หลัก (Features)
1.  **Multimodal Live Client:** เชื่อมต่อ WebSocket สตรีมมิ่งเสียง/ภาพแบบ Real-time กับโมดูล Gemini Live API
2.  **Voice Interaction:** ควบคุมการเชื่อมต่อและสั่งการด้วยเสียง (Wake Word)
3.  **Tool Use (Function Calling):**
    *   **Camera:** เปิด/ปิดกล้องและส่งวิดีโอเฟรมไปยังโมเดล
    *   **Calendar:** ดูรายการกิจกรรมและสร้างตารางงานใหม่ลง Google Calendar
    *   **Document:** อ่านและดึงเนื้อหาจาก PDF/DOCX บน Google Drive
    *   **Memory:** เก็บรักษาสถานะข้อมูลเกี่ยวกับรถยนต์และข้อมูลส่วนบุคคลอื่นๆ (Semantic Memory)

---

## ขั้นตอนการติดตั้งและการตั้งค่า (Setup Instructions)

### 1. ความต้องการของระบบ (Prerequisites)
*   **Android Studio** version Koala (2024.1.1) หรือใหม่กว่า
*   **JDK 17 หรือ 21** (แนะนำให้ใช้ JetBrains Runtime (JBR) ที่มาพร้อมกับ Android Studio)
*   ตั้งค่า `JAVA_HOME` ชี้ไปยังโฟลเดอร์ JDK ของระบบ

### 2. การตั้งค่า API Key
แอปพลิเคชันต้องการ Gemini API Key ในการเชื่อมต่อ:
1.  รับ API Key จาก [Google AI Studio](https://aistudio.google.com/)
2.  เปิดแอปพลิเคชัน เข้าไปที่เมนู **Settings (ตั้งค่า)**
3.  ป้อน API Key และทำการบันทึก (ระบบจะเข้ารหัสเก็บไว้ใน `EncryptedSharedPreferences` เพื่อความปลอดภัย)

### 3. การตั้งค่า Google OAuth (สำหรับ Calendar & Drive Integration)
เพื่อใช้งานฟีเจอร์จัดการปฏิทินและเอกสาร คุณต้องตั้งค่า OAuth 2.0 ใน Google Cloud Console:
1.  สร้างโครงการบน [Google Cloud Console](https://console.cloud.google.com/)
2.  เปิดใช้งาน **Google Calendar API** และ **Google Drive API**
3.  ไปที่หน้าจอ OAuth consent screen แล้วตั้งค่าแอพพลิเคชันเป็นประเภท External/Internal พร้อมเพิ่ม Scopes:
    *   `.../auth/calendar.events`
    *   `.../auth/drive.readonly`
4.  สร้าง OAuth Client ID สำหรับ Android:
    *   **Package Name:** `com.example.geminimultimodalliveapi`
    *   **SHA-1 fingerprint:** ดึงข้อมูล SHA-1 จากการรันคำสั่ง:
        ```bash
        ./gradlew signingReport
        ```
    *   นำค่า SHA-1 ไปกรอกในขั้นตอนสร้าง Client ID ใน Cloud Console
5.  รายละเอียดขั้นตอนดาวน์โหลดคู่มือเพิ่มเติมสามารถดูได้ที่ `docs/GOOGLE_OAUTH_SETUP.md`
