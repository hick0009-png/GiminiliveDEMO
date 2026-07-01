# Google OAuth Setup Guide (คู่มือการตั้งค่า Google OAuth)

คู่มือนี้แนะนำขั้นตอนการตั้งค่า Google Cloud Project และการกำหนดค่า OAuth 2.0 Credentials เพื่อเปิดใช้งานฟังก์ชันเชื่อมต่อ Google Drive และ Google Calendar ในแอปพลิการชัน Gemini Live Demo

---

## 1. การดึงค่า SHA-1 Fingerprint จากเครื่องผู้พัฒนา

เนื่องจาก Google Sign-in บน Android กำหนดให้ตรวจสอบ SHA-1 ของ APK Signature เสมอ ให้ดำเนินการดึงค่า Fingerprint ดังนี้:

### ใน Windows (PowerShell/CMD):
```powershell
keytool -list -v -keystore "$USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

### ใน macOS/Linux:
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

*เมื่อรันคำสั่งสำเร็จ ให้คัดลอกค่าแถว `SHA1:` เก็บไว้ (ตัวอย่างรูปแบบ: `AB:CD:EF:12:34:56...`)*

---

## 2. การสร้างและตั้งค่าโครงการใน Google Cloud Console

1. เปิดเบราว์เซอร์ไปที่ [Google Cloud Console](https://console.cloud.google.com/)
2. กดเลือกหรือสร้าง **Project ใหม่**
3. ไปที่เมนู **APIs & Services** > **Dashboard**
4. คลิก **Enable APIs and Services** แล้วค้นหาเพื่อเปิดใช้งาน APIs ต่อไปนี้:
   - **Google Calendar API** (สำหรับจัดการปฏิทินนัดหมาย)
   - **Google Drive API** (สำหรับซิงก์ไฟล์เอกสาร)

---

## 3. การกำหนดค่าหน้าจอขอความยินยอม (OAuth Consent Screen)

1. ไปที่เมนู **OAuth consent screen** ในแถบด้านซ้าย
2. เลือก User Type เป็น **External** (หรือ Internal หากใช้ในองค์กร Google Workspace) แล้วกด **Create**
3. กรอกข้อมูลแอปพลิเคชันพื้นฐาน (App name, User support email, Developer contact information)
4. ในขั้นตอน **Scopes** ให้เพิ่มขอบเขตสิทธิ์ที่จำเป็นดังนี้:
   - `.../auth/calendar` (Read, write, change, and delete any calendar you can access using Google Calendar)
   - `.../auth/calendar.events` (View and edit events on all your calendars)
   - `.../auth/drive.file` (View and manage Google Drive files and folders that you have opened or created with this app)
5. ในขั้นตอน **Test Users** ให้เพิ่มบัญชีอีเมล Google (@gmail.com) ที่จะใช้สำหรับทดสอบแอปพลิเคชันเข้าสู่ระบบ

---

## 4. การสร้าง OAuth 2.0 Client ID สำหรับ Android

1. ไปที่เมนู **Credentials** ในแถบด้านซ้าย
2. คลิก **+ CREATE CREDENTIALS** > **OAuth client ID**
3. เลือก Application type เป็น **Android**
4. กรอกข้อมูลสำหรับตรวจรับสิทธิ์:
   - **Name:** ระบุชื่อระบุตัวตน (เช่น `Gemini Live Demo - Debug`)
   - **Package name:** `com.example.geminimultimodalliveapi`
   - **SHA-1 certificate fingerprint:** วางค่า SHA-1 ที่คัดลอกมาจากขั้นตอนที่ 1
5. กด **Create** เพื่อบันทึกข้อมูล

---

## 5. การตรวจสอบความเรียบร้อยและการทดสอบเข้าสู่ระบบในแอป

1. ตรวจสอบให้มั่นใจว่าเครื่องทดสอบมีการเชื่อมต่ออินเทอร์เน็ต
2. เข้าหน้าจอ **Settings** (การตั้งค่า) ภายในแอปพลิเคชัน
3. กดปุ่ม **Connect** หรือ **Connect Google Drive**
4. ระบบจะแสดง Google Sign-in Sheet ให้เลือกบัญชี Google ที่ลงทะเบียนใน Test Users ไว้
5. ตรวจสอบขอบเขตการเข้าถึง (Consent Screen) จะต้องแสดงกล่องขออนุญาตใช้งานสิทธิ์ **Google Calendar** และ **Google Drive** ควบคู่กัน (การล็อกอินครั้งเดียวจะส่งขอบเขตสิทธิ์ครอบคลุมทั้งระบบ)
6. หากเกิดข้อผิดพลาดในการเชื่อมต่อ (เช่น API Exception 10 หรือ 12500) ให้ตรวจสอบว่าได้ใส่ค่า SHA-1 ใน Cloud Console ถูกต้องตรงกับตัวเครื่องที่กำลังรันดีบักหรือไม่
