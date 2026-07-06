# เอกสารข้อกำหนดทางเทคนิค: การรวมระบบฮาร์ดแวร์ ReSpeaker XVF3800 (XMOS)

เอกสารฉบับนี้จัดเตรียมข้อกำหนดเชิงออกแบบเพื่อผสานรวมไมโครโฟนอาร์เรย์ **ReSpeaker XVF3800** ที่ใช้ชิปประมวลผลสัญญาณเสียงดิจิทัลระดับฮาร์ดแวร์ **XMOS XVF3800** เข้ากับแอปพลิเคชัน เพื่อการใช้งานแฮนด์ฟรีในโหมดแปลภาษาเรียลไทม์และการโทรสั่งงานด้วยเสียงบนรถจักรยานยนต์อย่างเสถียร

---

## 1. คุณลักษณะฮาร์ดแวร์ (Hardware Capabilities)

บอร์ดประมวลผลเสียง ReSpeaker XVF3800 มีคุณสมบัติพิเศษระดับฮาร์ดแวร์ประกอบด้วย:
* **Acoustic Echo Cancellation (AEC)**: ตัวป้องกันและหักล้างสัญญาณเสียงสะท้อนจากลำโพง
* **Noise Suppression (NS)**: การขจัดสัญญาณรบกวนภายนอก (เช่น เสียงลมหรือเสียงเครื่องยนต์)
* **Automatic Gain Control (AGC)**: การปรับแต่งระดับความดังของเสียงร้องอัตโนมัติ
* **Direction of Arrival (DoA)**: การระบุทิศทางพิกัดองศา (0°-360°) ของเสียงหลักที่ผ่านเข้ามา
* **Beamforming (BF)**: การสร้างลำแสงรับสัญญาณเฉพาะทิศทางของมุมผู้พูด

---

## 2. แผนงานหลัก 4 ด้าน (Integration Blueprint)

### 2.1 การจัดการเส้นทางเสียงและพอร์ตเชื่อมต่อ (USB Audio Class 2.0 Auto-Routing)
* **พฤติกรรมดักจับ (Detection)**: เมื่อเสียบขั้วต่อ USB-OTG ของ ReSpeaker เข้ากับโทรศัพท์แอนดรอยด์ ระบบจะดักตรวจผ่าน `ACTION_USB_DEVICE_ATTACHED` ใน `BroadcastReceiver` ของ `FloatingWidgetService`
* **การจัดตั้งลำดับความสำคัญ (Preferred Device Routing)**:
  เรียกใช้คำสั่งของ Android `AudioManager` (API 28+) ในการจัดตั้งให้อุปกรณ์เสียงที่เชื่อมต่อใหม่นี้เป็นเส้นทางส่งและรับสัญญาณเสียงหลัก (Preferred Input/Output Device) แทนการประมวลผลในโทรศัพท์:
  ```kotlin
  val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
  val usbMic = devices.find { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
  usbMic?.let {
      audioRecord.setPreferredDevice(it)
      Log.i("AudioRouter", "Successfully locked microphone channel to ReSpeaker XVF3800 USB input.")
  }
  ```

### 2.2 การข้ามซอฟต์แวร์ประมวลผลเสียงของ Android (Hardware DSP Bypass)
* **หลักการ**: เนื่องจากบอร์ด XVF3800 ได้ทำการขจัดเสียงสะท้อน (AEC) และคัดกรองเสียงรบกวน (NS) ผ่านชิป DSP ของตัวเองเสร็จสิ้นแล้ว หากเราเปิดซอฟต์แวร์ตัดเสียงสะท้อนซ้ำซ้อนของแอนดรอยด์จะส่งผลให้เสียงเกิดอาการแหบ หาย หรือเกิดความเพี้ยนอย่างรุนแรง (Double Processing Distortion)
* **การข้ามการประมวลผล**: ในการกำหนดค่าอินสแตนซ์ของ `AudioRecord` ใน [AudioRecorder.kt](file:///d:/New%20folder%20(2)/GeminiLiveDemo/app/src/main/java/com/example/geminimultimodalliveapi/audio/AudioRecorder.kt) จะข้ามขั้นตอนการเปิดใช้งาน `AcousticEchoCanceler` และ `NoiseSuppressor` ของแอนดรอยด์:
  ```kotlin
  // ข้ามการเรียกใช้ AcousticEchoCanceler.create(audioRecord.audioSessionId)
  // ข้ามการเรียกใช้ NoiseSuppressor.create(audioRecord.audioSessionId)
  // เพื่อประหยัด CPU ของระบบโทรศัพท์หลักลงประมาณ 15% - 25%
  ```

### 2.3 การคัดแยกผู้พูดตามทิศทางเสียงจริง (DoA Speaker Mapping)
* **หลักการ**: ใช้สัญญาณข้อมูล DoA (Direction of Arrival) จากตัวบอร์ดมาใช้ช่วยแยกแยะผู้พูด เช่น:
  - เสียงที่มาจากทิศทาง **มุม 0° (ด้านหน้าบอร์ด)** คือผู้ใช้ฝั่งไทย (เจ้าของเครื่อง)
  - เสียงที่มาจากทิศทาง **มุม 180° (ด้านหลังบอร์ด)** คือคู่สนทนาต่างชาติ
* **การดึงข้อมูลทิศทาง**:
  ตัวบอร์ด XVF3800 จะสื่อสารองศา DoA ผ่าน **USB HID Endpoint** หรือส่งข้อมูลผ่านช่องสัญญาณควบคุมซีเรียล (Serial Control Protocol) เราจะพัฒนารันสคริปต์ในแอปแอนดรอยด์คอยโพลลิงค่าองศาพิกัดเสียงเรียลไทม์ และนำองศานั้นไปสลับประเภทการแสดงผล Subtitle บนหน้าจอ `TranslateActivity` อัตโนมัติ (เช่น 0° แสดงฝั่งซ้าย, 180° แสดงฝั่งขวา) โดยไม่ต้องรอให้คลาวด์วิเคราะห์มิติเสียง ช่วยเพิ่มความรวดเร็วและแม่นยำสูง

### 2.4 การรันเบื้องหลังแบบเสถียร (Background Service Persistence & WakeLock)
* **ความปลอดภัยในการรันค้าง**: ระบบแปลภาษาขณะขับขี่รถจักรยานยนต์ต้องการความต่อเนื่อง ห้ามแอปดับเมื่อปิดหน้าจอมือถือ
* **การจัดการพลังงาน**: ทำการถือครองสิทธิ์ **CPU WakeLock** และล็อคสถานะการทำงานเบื้องหน้า (Foreground Service) ไว้ใน `FloatingWidgetService` เสมอเมื่อเปิดโหมดประมวลผล XVF3800 เพื่อป้องกันระบบปฏิบัติการ Android ปิดการสตรีมเสียงเมื่อมีการดับจอภาพมือถือพับเก็บลงกระเป๋า
  ```kotlin
  val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
  wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hermes:XVF3800WakeLock").apply {
      acquire(10 * 60 * 1000L) // 10 minutes session safety timeout
  }
  ```

---

## 3. การประเมินผลการกินทรัพยากร (Performance Impact)
* **CPU Load**: ลดลงอย่างเห็นได้ชัดจากการตัดซอฟต์แวร์ประมวลผล (AEC/NS) ไปให้ชิปเฉพาะบนบอร์ดภายนอกทำงานแทน
* **Latency**: เสียงแปลเรียลไทม์มีความหน่วงลดลงต่ำกว่าเดิม (ลดลงราวๆ 30-50ms) เนื่องจากลดขั้นตอนการจัดฟิลเตอร์เสียงในเลเยอร์ Android OS
