# เอกสารการออกแบบสถาปัตยกรรม: การแปลง PDF และรูปภาพเป็น JSON ผ่าน Gemini Files API

เอกสารฉบับนี้กำหนดรายละเอียดการออกแบบระบบแปลงเอกสาร PDF และรูปภาพถ่าย (เช่น ทะเบียนรถ, ป้ายวงกลมภาษี, ภาพถ่ายสัญญากระดาษ) ให้เป็นโครงสร้างข้อมูล JSON แบบกึ่งโครงสร้าง (Semi-structured JSON) เพื่อใช้เป็นแคชบริบทขนาดเล็กช่วยในการทำงานแบบเรียลไทม์ โดยรักษาไฟล์ต้นฉบับไว้บนอุปกรณ์โทรศัพท์มือถืออย่างถาวร

---

## 1. วัตถุประสงค์เชิงเปรียบเทียบขนาด (Objective & Lightweight Caching)

* **ความจำเป็นในการแปลงเป็น JSON**: ข้อมูลเอกสาร PDF เต็มรูปแบบ หรือรูปถ่ายสแกนความละเอียดสูง มีขนาดที่ใหญ่เกินกว่าจะส่งเข้าไปประมวลผลซ้ำๆ ในทรานสคริปต์สตรีมของบอทเรียลไทม์ ซึ่งจะทำให้เกิดความหน่วงสูง (Latency) และเสียโทเค็นจำนวนมากโดยใช่เหตุ
* **แนวทางแก้ไข**: แปลงไฟล์และรูปภาพต้นฉบับเหล่านั้นให้ออกมาเป็นข้อมูลสรุปโครงสร้างขนาดเล็กในรูปแบบ `.json` เพื่อป้อนให้ Gemini โหลดได้อย่างรวดเร็วในระดับเสี้ยววินาที (Low-latency runtime)
* **นโยบายการจัดเก็บไฟล์ต้นฉบับ**: 
  แอปพลิเคชันจะ **ไม่ทำลาย** หรือลบไฟล์เอกสาร PDF และรูปภาพต้นฉบับที่ผู้ใช้ถ่ายสแกน โดยจะเก็บไฟล์ภาพเหล่านี้ไว้ในโฟลเดอร์ของแอปพลิเคชันอย่างถาวร เพื่อให้ผู้ใช้กดเรียกดูเอกสาร/รูปภาพต้นฉบับได้ตลอดเวลาผ่านหน้าต่างประวัติเอกสาร

---

## 2. ลำดับขั้นตอนการทำงาน (Sequence Workflow)

```mermaid
sequenceDiagram
    autonumber
    actor User as ผู้ใช้ (Settings / Camera UI)
    participant Manager as DocumentManager
    participant Client as Gemini REST Client
    participant API as Gemini Files API
    participant Model as Gemini Model API
    database Storage as Local Storage (JSON & JPEG/PDF)

    User->>Manager: ถ่ายรูป (ป้ายทะเบียน/สัญญา) หรือ เลือก PDF
    Manager->>Storage: 1. เซฟไฟล์ต้นฉบับ (JPEG/PDF) ไว้ในเครื่องถาวร (filesDir/media/)
    Manager->>Client: เริ่มกระบวนการแปลงส่งไฟล์ (File size & Type)
    Client->>API: 2. HTTP POST /upload/v1beta/files (Initialize Upload)
    API-->>Client: ตอบกลับ upload URL
    Client->>API: 3. HTTP PUT (ส่งข้อมูลไฟล์ต้นฉบับขึ้นคลาวด์)
    API-->>Client: ตอบกลับ 'fileUri'
    Client->>Model: 4. HTTP POST /generateContent (Prompt + fileUri)
    Note over Model: Gemini วิเคราะห์รายละเอียดรูป/เอกสาร <br/>และพาร์สข้อมูลสกัดมาเป็น JSON
    Model-->>Client: 5. ส่งผลลัพธ์ JSON String กลับมา
    Client->>API: 6. HTTP DELETE [fileUri] (สั่งลบไฟล์บนคลาวด์ทันทีเพื่อความปลอดภัย)
    Client->>Storage: 7. บันทึกผลลัพธ์พาร์สลงไฟล์ {filename}.json ใน documents/
    Storage-->>User: อัปเดต UI รายการไฟล์คู่ขนาน (แสดงผลป้ายทะเบียน/รายละเอียด)
```

---

## 3. รายละเอียดการเข้ารหัสและจุดจัดเก็บข้อมูลในเครื่อง (Device Storage Map)

| ประเภทไฟล์ | จุดจัดเก็บในอุปกรณ์ (Path) | ระดับการเข้าถึงและความปลอดภัย |
| :--- | :--- | :--- |
| **ภาพถ่ายต้นฉบับ (JPEG)** | `context.filesDir/media/[image_id].jpg` | เก็บรักษาถาวรในโฟลเดอร์มีเดียส่วนตัวของแอป เพื่อให้ผู้ใช้สามารถกดคลิกส่องดูรูปหลักฐานดั้งเดิมได้ตลอดเวลา |
| **เอกสาร PDF ต้นฉบับ** | `context.filesDir/documents/[doc_id].pdf` | เก็บรักษาถาวรในโฟลเดอร์ส่วนตัวของแอป |
| **ข้อมูลสกัดสเปค (JSON)** | `context.filesDir/documents/[doc_id].json` | เก็บรักษาถาวร ใช้สำหรับการป้อนข้อความสรุป ตารางคัดย่อ และคำอธิบายภาพเข้าระบบสืบค้น (DocumentSelector) เพื่อยัดใส่ Prompt ของ AI |
| **ข้อมูลฟิลด์รถยนต์/ความจำ** | SQLCipher Database (Room) | เก็บข้อมูลในตาราง `vehicle_memory` หรือ `memories` แบบเข้ารหัส เพื่อใช้แสดงผลเชิงสถิติบนหน้าจอมือถือทันที |

---

## 4. แผนงานการเชื่อมต่อ API (API Interaction Plan)

การเชื่อมต่อ Gemini Files API จะส่งข้อมูลแบบ Resumable Upload สำหรับ PDF และส่งแบบ Inline Data (Base64) ในกรณีที่เป็นรูปถ่ายเดี่ยวที่มีขนาดเล็กเพื่อประหยัดจำนวนรอบการเชื่อมต่อ (Roundtrips)

### 4.1 กรณีรูปถ่ายเดี่ยวขนาดเล็ก (< 1MB)
* **วิธีประมวลผล**: ส่งรูปภาพแบบ Base64 ตรงๆ ไปที่โมเดล Gemini โดยไม่ต้องใช้ Files API เพื่อความรวดเร็ว
* **Body Request**:
  ```json
  {
    "contents": [
      {
        "parts": [
          {
            "inlineData": {
              "mimeType": "image/jpeg",
              "data": "[Base64 encoded string of image]"
            }
          },
          {
            "text": "Extract target details into JSON format according to schema."
          }
        ]
      }
    ],
    "generationConfig": {
      "responseMimeType": "application/json"
    }
  }
  ```

### 4.2 กรณีเอกสาร PDF หรือรูปภาพเอกสารความละเอียดสูงหลายหน้า
* **วิธีประมวลผล**: อัปโหลดผ่าน Files API (ตามรายละเอียดหัวข้อที่ 3 ของไฟล์ดีไซน์) เพื่อลดภาระแบนด์วิดท์ และส่งคำสั่งลบเมื่อได้รับ JSON คืนกลับมาเก็บรักษาลงดิสก์แอนดรอยด์เรียบร้อยแล้ว
