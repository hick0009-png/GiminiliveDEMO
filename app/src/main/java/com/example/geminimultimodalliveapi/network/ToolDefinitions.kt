package com.example.geminimultimodalliveapi.network

import org.json.JSONArray
import org.json.JSONObject

object ToolDefinitions {
    fun getTools(): JSONArray {
        val tools = JSONArray()
        val tool = JSONObject()
        val functionDeclarations = JSONArray()

        // 1. Open Camera Tool
        val functionDecl1 = JSONObject()
        functionDecl1.put("name", "open_camera")
        functionDecl1.put("description", "เปิดกล้องถ่ายภาพวิดีโอเพื่อวิเคราะห์สิ่งที่ผู้ใช้นำเสนอเมื่อผู้ใช้สั่งให้เปิดกล้อง หรือผู้ใช้ต้องการให้ช่วยดู/ช่วยอธิบายสิ่งของเบื้องหน้ากล้อง")
        val parameters1 = JSONObject()
        parameters1.put("type", "OBJECT")
        parameters1.put("properties", JSONObject())
        functionDecl1.put("parameters", parameters1)
        functionDeclarations.put(functionDecl1)

        // 2. Close Camera Tool
        val functionDecl2 = JSONObject()
        functionDecl2.put("name", "close_camera")
        functionDecl2.put("description", "ปิดกล้องหรือหยุดส่งภาพวิดีโอเมื่อผู้ใช้สั่งให้ปิดกล้อง หรือบอกว่าเลิกดู/ใช้งานเสร็จแล้ว")
        val parameters2 = JSONObject()
        parameters2.put("type", "OBJECT")
        parameters2.put("properties", JSONObject())
        functionDecl2.put("parameters", parameters2)
        functionDeclarations.put(functionDecl2)
        
        // 3. Save Vehicle Info Tool
        val functionDecl3 = JSONObject()
        functionDecl3.put("name", "save_vehicle_info")
        functionDecl3.put("description", "บันทึกข้อมูลสำคัญของรถยนต์หรือข้อมูลการขับขี่ลงฐานข้อมูลความจำในเครื่อง เช่น หมายเลขทะเบียนรถ วันหมดอายุภาษี/ป้ายวงกลม เลขประกันภัย หรือบันทึกประวัติการเปลี่ยนถ่ายน้ำมันเครื่อง/เช็คระยะรถ")
        val parameters3 = JSONObject()
        parameters3.put("type", "OBJECT")
        val properties3 = JSONObject()
        
        val categoryProp = JSONObject()
        categoryProp.put("type", "STRING")
        categoryProp.put("description", "หมวดหมู่ข้อมูล เช่น license_plate, tax_circle, insurance, maintenance, parking, general")
        properties3.put("category", categoryProp)
        
        val keyNameProp = JSONObject()
        keyNameProp.put("type", "STRING")
        keyNameProp.put("description", "ชื่อข้อมูลย่อย เช่น plate_number, expiry_date, policy_number, oil_change, spot")
        properties3.put("key_name", keyNameProp)
        
        val infoValueProp = JSONObject()
        infoValueProp.put("type", "STRING")
        infoValueProp.put("description", "เนื้อหาหรือข้อความสำคัญที่ต้องการบันทึก เช่น 'กข 1234 กรุงเทพฯ', '2026-12-25', '12,000 กม.'")
        properties3.put("info_value", infoValueProp)
        
        parameters3.put("properties", properties3)
        val required3 = JSONArray()
        required3.put("category")
        required3.put("key_name")
        required3.put("info_value")
        parameters3.put("required", required3)
        functionDecl3.put("parameters", parameters3)
        functionDeclarations.put(functionDecl3)

        // 4. Query Vehicle Info Tool
        val functionDecl4 = JSONObject()
        functionDecl4.put("name", "query_vehicle_info")
        functionDecl4.put("description", "ค้นหาหรือดึงข้อมูลความจำสำคัญของรถที่เคยบันทึกไว้ในฐานข้อมูลเครื่อง")
        val parameters4 = JSONObject()
        parameters4.put("type", "OBJECT")
        val properties4 = JSONObject()
        
        val categoryQueryProp = JSONObject()
        categoryQueryProp.put("type", "STRING")
        categoryQueryProp.put("description", "หมวดหมู่ข้อมูลที่ต้องการค้นหา (ทางเลือก) เช่น license_plate, tax_circle, insurance, maintenance, parking หรือปล่อยว่างเพื่อค้นหาทั้งหมด")
        properties4.put("category", categoryQueryProp)
        
        parameters4.put("properties", properties4)
        functionDecl4.put("parameters", parameters4)
        functionDeclarations.put(functionDecl4)

        // 5. Get Current Time Tool
        val functionDecl5 = JSONObject()
        functionDecl5.put("name", "get_current_time")
        functionDecl5.put("description", "ดึงวันและเวลาปัจจุบันของระบบแบบ Real-time ล่าสุดจากเครื่องโทรศัพท์")
        val parameters5 = JSONObject()
        parameters5.put("type", "OBJECT")
        parameters5.put("properties", JSONObject())
        functionDecl5.put("parameters", parameters5)
        functionDeclarations.put(functionDecl5)

        // 6. Delete Vehicle Info Tool
        val functionDecl6 = JSONObject()
        functionDecl6.put("name", "delete_vehicle_info")
        functionDecl6.put("description", "ลบข้อมูลความจำของรถยนต์หรือลบรายการบางหมวดหมู่ที่เคยบันทึกไว้ในฐานข้อมูลเครื่องออกตามที่ผู้ใช้สั่งลบ")
        val parameters6 = JSONObject()
        parameters6.put("type", "OBJECT")
        val properties6 = JSONObject()
        
        val categoryDeleteProp = JSONObject()
        categoryDeleteProp.put("type", "STRING")
        categoryDeleteProp.put("description", "หมวดหมู่ข้อมูลที่ต้องการลบ เช่น license_plate, tax_circle, insurance, maintenance, parking, general")
        properties6.put("category", categoryDeleteProp)
        
        val keyNameDeleteProp = JSONObject()
        keyNameDeleteProp.put("type", "STRING")
        keyNameDeleteProp.put("description", "ชื่อข้อมูลย่อยที่ต้องการลบ (ทางเลือก) เช่น plate_number, expiry_date หรือปล่อยว่างเพื่อลบทั้งหมวดหมู่")
        properties6.put("key_name", keyNameDeleteProp)
        
        parameters6.put("properties", properties6)
        val required6 = JSONArray()
        required6.put("category")
        parameters6.put("required", required6)
        functionDecl6.put("parameters", parameters6)
        functionDeclarations.put(functionDecl6)
        
        // 7. Query Policy Document Tool
        val functionDecl7 = JSONObject()
        functionDecl7.put("name", "query_policy_document")
        functionDecl7.put("description", "ค้นหาหรือสืบค้นข้อมูลในไฟล์เอกสารกรมธรรม์ประกันภัยหรือคู่มือรถยนต์ที่ผู้ใช้อัปโหลดเก็บไว้ในเครื่อง เมื่อมีคำถามเกี่ยวกับความคุ้มครอง ข้อมูลกรมธรรม์ หรือวิธีดำเนินการเมื่อเกิดอุบัติเหตุ/ฉุกเฉิน")
        val parameters7 = JSONObject()
        parameters7.put("type", "OBJECT")
        val properties7 = JSONObject()
        
        val queryProp = JSONObject()
        queryProp.put("type", "STRING")
        queryProp.put("description", "คีย์เวิร์ดหรือประโยคคำถามภาษาไทยสำหรับการค้นหาในเอกสาร เช่น 'ขั้นตอนเคลมอุบัติเหตุ', 'ความคุ้มครองอุบัติเหตุ', 'สายด่วนแจ้งเคลม'")
        properties7.put("query", queryProp)
        
        parameters7.put("properties", properties7)
        val required7 = JSONArray()
        required7.put("query")
        parameters7.put("required", required7)
        functionDecl7.put("parameters", parameters7)
        functionDeclarations.put(functionDecl7)

        // 8. Make Phone Call Tool
        val functionDecl8 = JSONObject()
        functionDecl8.put("name", "make_phone_call")
        functionDecl8.put("description", "เปิดหน้าต่างโทรศัพท์และกดเบอร์โทรออก (เช่น เบอร์ฮอตไลน์ประกันภัย หรือสายด่วนฉุกเฉิน) ตามที่ผู้ใช้สั่งการด้วยเสียง")
        val parameters8 = JSONObject()
        parameters8.put("type", "OBJECT")
        val properties8 = JSONObject()
        
        val phoneNumberProp = JSONObject()
        phoneNumberProp.put("type", "STRING")
        phoneNumberProp.put("description", "หมายเลขโทรศัพท์ที่ต้องการโทรออก เช่น '1557', '1186'")
        properties8.put("phone_number", phoneNumberProp)
        
        parameters8.put("properties", properties8)
        val required8 = JSONArray()
        required8.put("phone_number")
        parameters8.put("required", required8)
        functionDecl8.put("parameters", parameters8)
        functionDeclarations.put(functionDecl8)

        // 8.1. End Phone Call Tool
        val functionDeclEndCall = JSONObject()
        functionDeclEndCall.put("name", "end_phone_call")
        functionDeclEndCall.put("description", "วางสายโทรศัพท์หรือวางสายโทรออกฉุกเฉินที่กำลังโทรอยู่ ณ ปัจจุบัน")
        val parametersEndCall = JSONObject()
        parametersEndCall.put("type", "OBJECT")
        parametersEndCall.put("properties", JSONObject())
        functionDeclEndCall.put("parameters", parametersEndCall)
        functionDeclarations.put(functionDeclEndCall)
        
        // 9. Create Calendar Event Tool
        val functionDecl9 = JSONObject()
        functionDecl9.put("name", "create_calendar_event")
        functionDecl9.put("description", "บันทึกและตั้งเตือนความจำในปฏิทิน Google Calendar พร้อมแจ้งเตือนแบบป็อปอัป สามารถระบุวัน เวลา และรายละเอียดได้")
        val parameters9 = JSONObject()
        parameters9.put("type", "OBJECT")
        val properties9 = JSONObject()
        
        val titleProp = JSONObject()
        titleProp.put("type", "STRING")
        titleProp.put("description", "ชื่องาน/หัวข้อเตือนความจำ เช่น 'นัดเช็คระยะรถมอเตอร์ไซค์', 'จ่ายค่าประกันรถ'")
        properties9.put("title", titleProp)
        
        val descProp = JSONObject()
        descProp.put("type", "STRING")
        descProp.put("description", "รายละเอียดของนัดหมาย เช่น 'ที่ศูนย์บริการพญาไท', 'เบี้ยประกัน 12,000 บาท'")
        properties9.put("description", descProp)
        
        val startTimeProp = JSONObject()
        startTimeProp.put("type", "STRING")
        startTimeProp.put("description", "วันเวลาเริ่มต้นของนัดหมายในรูปแบบ ISO 8601 (เช่น '2026-06-14T09:00:00+07:00')")
        properties9.put("start_time", startTimeProp)
        
        val durationProp = JSONObject()
        durationProp.put("type", "INTEGER")
        durationProp.put("description", "ระยะเวลาของนัดหมายเป็นนาที (ค่าเริ่มต้น 60 นาที)")
        properties9.put("duration_minutes", durationProp)
        
        parameters9.put("properties", properties9)
        val required9 = JSONArray()
        required9.put("title")
        required9.put("start_time")
        parameters9.put("required", required9)
        functionDecl9.put("parameters", parameters9)
        functionDeclarations.put(functionDecl9)

        // 10. List Calendar Events Tool
        val functionDecl10 = JSONObject()
        functionDecl10.put("name", "list_calendar_events")
        functionDecl10.put("description", "ดึงข้อมูลและค้นหารายการนัดหมาย/วันสำคัญในปฏิทิน Google Calendar ล่วงหน้า 7 วันถัดไปนับจากปัจจุบัน")
        val parameters10 = JSONObject()
        parameters10.put("type", "OBJECT")
        parameters10.put("properties", JSONObject())
        functionDecl10.put("parameters", parameters10)
        functionDeclarations.put(functionDecl10)

        // 11. Get Current Weather Tool
        val functionDecl11 = JSONObject()
        functionDecl11.put("name", "get_current_weather")
        functionDecl11.put("description", "ดึงข้อมูลสภาพอากาศและอุณหภูมิปัจจุบัน ณ ตำแหน่งพิกัดปัจจุบันของโทรศัพท์มือถือแบบเรียลไทม์")
        val parameters11 = JSONObject()
        parameters11.put("type", "OBJECT")
        parameters11.put("properties", JSONObject())
        functionDecl11.put("parameters", parameters11)
        functionDeclarations.put(functionDecl11)

        // 12. Find Nearby Places Tool
        val functionDecl12 = JSONObject()
        functionDecl12.put("name", "find_nearby_places")
        functionDecl12.put("description", "ค้นหาสถานที่หรือจุดบริการใกล้เคียงผู้ใช้ในระยะ 5 กิโลเมตร เช่น ปั๊มน้ำมัน (fuel), ร้านซ่อมมอเตอร์ไซค์/รถยนต์ (repair), โรงพยาบาล (hospital), ร้านอาหาร (restaurant), ร้านกาแฟ (cafe), หรือตู้เอทีเอ็ม (atm)")
        val parameters12 = JSONObject()
        parameters12.put("type", "OBJECT")
        val properties12 = JSONObject()
        
        val placeTypeProp = JSONObject()
        placeTypeProp.put("type", "STRING")
        placeTypeProp.put("description", "ประเภทสถานที่ที่ต้องการหา ได้แก่ fuel, repair, hospital, restaurant, cafe, atm")
        properties12.put("place_type", placeTypeProp)
        
        parameters12.put("properties", properties12)
        val required12 = JSONArray()
        required12.put("place_type")
        parameters12.put("required", required12)
        functionDecl12.put("parameters", parameters12)
        functionDeclarations.put(functionDecl12)

        // 13. Launch Navigation Tool
        val functionDecl13 = JSONObject()
        functionDecl13.put("name", "launch_navigation")
        functionDecl13.put("description", "เปิดแอปพลิเคชันนำทาง (Google Maps) เพื่อนำทางผู้ใช้ไปยังจุดหมายปลายทางที่ต้องการ")
        val parameters13 = JSONObject()
        parameters13.put("type", "OBJECT")
        val properties13 = JSONObject()
        
        val destProp = JSONObject()
        destProp.put("type", "STRING")
        destProp.put("description", "ชื่อสถานที่หรือที่อยู่ปลายทางที่ต้องการนำทางไป เช่น 'เซ็นทรัลลาดพร้าว', 'สนามบินดอนเมือง'")
        properties13.put("destination", destProp)
        
        parameters13.put("properties", properties13)
        val required13 = JSONArray()
        required13.put("destination")
        parameters13.put("required", required13)
        functionDecl13.put("parameters", parameters13)
        functionDeclarations.put(functionDecl13)

        // 14. Remember Personal Fact Tool
        val functionDecl14 = JSONObject()
        functionDecl14.put("name", "remember_personal_fact")
        functionDecl14.put("description", "บันทึกเรื่องราวส่วนตัว ข้อมูลสำคัญ หรือเรื่องราวที่ผู้ใช้บอกเล่าหรือสั่งให้จดจำ เพื่อเก็บเป็นความจำระยะยาว (Long-term Memory)")
        val parameters14 = JSONObject()
        parameters14.put("type", "OBJECT")
        val properties14 = JSONObject()
        
        val factProp = JSONObject()
        factProp.put("type", "STRING")
        factProp.put("description", "เนื้อหาเรื่องราวหรือข้อมูลสำคัญย่อยที่ต้องการให้ระบบจดจำ เช่น 'ผู้ใช้ชอบดื่มลาเต้เย็นหวานน้อย', 'ครอบครัวผู้ใช้เลี้ยงแมวชื่อส้มส้ม'")
        properties14.put("fact_content", factProp)

        val importanceProp = JSONObject()
        importanceProp.put("type", "INTEGER")
        importanceProp.put("description", "ระดับความสำคัญของข้อมูลความจำ (1-5) โดย 5 คือเรื่องที่สำคัญและถาวรมากที่สุดที่จะไม่มีวันเลือนหาย (เช่น เบอร์ฉุกเฉิน, ประวัติแพ้ยา, ทะเบียนรถ) ส่วน 1-2 คือเรื่องทั่วไปชั่วคราวหรือประวัติตามบริบทรายวันซึ่งจะค่อยๆ เสื่อมสลายเมื่อไม่ได้พูดถึง")
        properties14.put("importance", importanceProp)

        val categoryPropFact = JSONObject()
        categoryPropFact.put("type", "STRING")
        categoryPropFact.put("description", "หมวดหมู่ของข้อมูลความจำที่จดจำ เช่น 'personal' (เรื่องส่วนตัว), 'vehicle' (ข้อมูลรถยนต์/พาหนะ), 'health' (สุขภาพ/การแพ้ยา), 'daily' (เรื่องทั่วไปรายวัน), หรือ 'general' (หมวดหมู่ทั่วไป)")
        properties14.put("category", categoryPropFact)
        
        parameters14.put("properties", properties14)
        val required14 = JSONArray()
        required14.put("fact_content")
        parameters14.put("required", required14)
        functionDecl14.put("parameters", parameters14)
        functionDeclarations.put(functionDecl14)

        // 15. Forget Personal Fact Tool
        val functionDecl15 = JSONObject()
        functionDecl15.put("name", "forget_personal_fact")
        functionDecl15.put("description", "ลบหรือลืมเรื่องราวส่วนตัว ข้อมูลสำคัญ หรือเรื่องราวที่ผู้ใช้เคยสั่งให้จำ ที่ไม่ต้องการให้จำอีกต่อไป")
        val parameters15 = JSONObject()
        parameters15.put("type", "OBJECT")
        val properties15 = JSONObject()
        
        val queryPropDelete = JSONObject()
        queryPropDelete.put("type", "STRING")
        queryPropDelete.put("description", "คำสำคัญหรือคีย์เวิร์ดสำหรับระบุข้อความความจำที่ต้องการลบ เช่น 'ส้มส้ม', 'ลาเต้'")
        properties15.put("query", queryPropDelete)
        
        parameters15.put("properties", properties15)
        val required15 = JSONArray()
        required15.put("query")
        parameters15.put("required", required15)
        functionDecl15.put("parameters", parameters15)
        functionDeclarations.put(functionDecl15)

        // 16. Query Relevant Memories Tool
        val functionDecl16 = JSONObject()
        functionDecl16.put("name", "query_relevant_memories")
        functionDecl16.put("description", "ค้นหาหรือดึงข้อมูลความจำเก่าในอดีตจากฐานข้อมูลความจำ (SQLite) ที่เกี่ยวข้องกับคำค้นหา เพื่อนำมาตอบคำถามของผู้ใช้")
        val parameters16 = JSONObject()
        parameters16.put("type", "OBJECT")
        val properties16 = JSONObject()
        
        val queryPropSearch = JSONObject()
        queryPropSearch.put("type", "STRING")
        queryPropSearch.put("description", "คำค้นหาหรือคีย์เวิร์ดสำหรับระบุข้อความความจำที่ต้องการดึง เช่น 'แมว', 'รถ', 'ของชอบ'")
        properties16.put("search_query", queryPropSearch)
        
        parameters16.put("properties", properties16)
        val required16 = JSONArray()
        required16.put("search_query")
        parameters16.put("required", required16)
        functionDecl16.put("parameters", parameters16)
        functionDeclarations.put(functionDecl16)

        // 17. Save System Rule Tool (Dynamic Voice Tuning)
        val functionDecl17 = JSONObject()
        functionDecl17.put("name", "save_system_rule")
        functionDecl17.put("description", "บันทึกกฎปรับตัวพฤติกรรมของระบบแบบไดนามิกตามสถานการณ์และเซ็นเซอร์ เช่น เมื่อขับรถอยู่ให้ตอบสั้น หรือเปลี่ยนคีย์เวิร์ดของหัวข้อสนทนา หรือลบกฎปรับแต่งทั้งหมด")
        val parameters17 = JSONObject()
        parameters17.put("type", "OBJECT")
        val properties17 = JSONObject()
        
        val conditionTypeProp = JSONObject()
        conditionTypeProp.put("type", "STRING")
        conditionTypeProp.put("description", "ประเภทเงื่อนไขของเซ็นเซอร์ เช่น 'ON_MOTION', 'ON_LOCATION', 'ON_TOPIC', 'GENERAL'")
        properties17.put("condition_type", conditionTypeProp)
        
        val conditionValueProp = JSONObject()
        conditionValueProp.put("type", "STRING")
        conditionValueProp.put("description", "ค่าของเซ็นเซอร์ที่จะเปิดใช้งานกฎนี้ เช่น 'DRIVING', 'WALKING', 'shopping_mall', 'used_car' หรือชื่อหัวข้อ/พิกัดสถานที่")
        properties17.put("condition_value", conditionValueProp)
        
        val instructionProp = JSONObject()
        instructionProp.put("type", "STRING")
        instructionProp.put("description", "คำสั่งพฤติกรรมที่จะฉีด (Inject) เข้ากับ LLM เมื่อเซ็นเซอร์ตรงตามเงื่อนไข เช่น 'Speak concisely.', 'Speak Thai only.' หรือใส่คำแนะนำอื่น")
        properties17.put("instruction", instructionProp)

        val actionProp = JSONObject()
        actionProp.put("type", "STRING")
        actionProp.put("description", "การดำเนินการ: 'SAVE' เพื่อบันทึกกฎใหม่/ทับกฎเดิม หรือ 'CLEAR_ALL' เพื่อยกเลิก/ลบกฎจูนสดทั้งหมดออกจากเครื่อง")
        properties17.put("action", actionProp)
        
        parameters17.put("properties", properties17)
        val required17 = JSONArray()
        required17.put("action")
        parameters17.put("required", required17)
        functionDecl17.put("parameters", parameters17)
        functionDeclarations.put(functionDecl17)
        
        // 18. Get Situational Context Tool (Dynamic Situational Context)
        val functionDecl18 = JSONObject()
        functionDecl18.put("name", "get_situational_context")
        functionDecl18.put("description", "ดึงข้อมูลบริบทจากเซ็นเซอร์ล่าสุด (ตำแหน่งที่ตั้ง, สถานะการขับขี่/เคลื่อนไหว, หัวข้อสนทนาที่กำลังใช้งาน และกฎระบบพฤติกรรมจูนสดที่ตรงเงื่อนไข) เพื่อใช้ปรับพฤติกรรมและการตอบสนองต่อผู้ใช้งานตามสถานการณ์ล่าสุด")
        val parameters18 = JSONObject()
        parameters18.put("type", "OBJECT")
        parameters18.put("properties", JSONObject())
        functionDecl18.put("parameters", parameters18)
        functionDeclarations.put(functionDecl18)

        tool.put("functionDeclarations", functionDeclarations)
        tools.put(tool)

        // Add Google Search grounding tool
        val googleSearch = JSONObject()
        googleSearch.put("google_search", JSONObject())
        tools.put(googleSearch)

        return tools
    }
}
