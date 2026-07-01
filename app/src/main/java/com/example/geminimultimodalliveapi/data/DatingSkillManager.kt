package com.example.geminimultimodalliveapi.data

import android.content.Context
import android.util.Log
import java.io.File

class DatingSkillManager(private val context: Context) {

    private val skillsDir: File by lazy {
        File(context.filesDir, "skills").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    init {
        initializeDefaultSkills()
    }

    private fun initializeDefaultSkills() {
        try {
            val existingFiles = skillsDir.listFiles { _, name -> name.endsWith(".md") }
            if (existingFiles.isNullOrEmpty()) {
                saveSkill(
                    DatingSkill(
                        id = "sweet_flirting",
                        name = "จีบสาวหวาน (Sweet Flirting)",
                        description = "ใช้เมื่อคู่เดตเป็นคนเรียบร้อย อ่อนหวาน ชอบเรื่องราวโรแมนติก หรือบรรยากาศที่นุ่มนวล",
                        instructions = """
- ชวนคุยในหัวข้อเบาสมอง เรื่องอาหาร สัตว์เลี้ยง คาเฟ่ หรือสถานที่ท่องเที่ยว
- หลีกเลี่ยงหัวข้อหยาบคาย รุนแรง หรือมุกตลกสองแง่สองง่ามโดยเด็ดขาด
- ชื่นชมคู่เดตอย่างจริงใจ สุภาพ และเน้นให้เกียรติ
- คอยสังเกตอารมณ์และถามไถ่ความรู้สึกเป็นระยะเพื่อสร้างความผูกพัน
                        """.trimIndent()
                    )
                )

                saveSkill(
                    DatingSkill(
                        id = "icebreaker",
                        name = "ละลายพฤติกรรม (First Date Icebreaker)",
                        description = "ใช้เริ่มต้นบทสนทนาในเดตแรกเพื่อลดความประหม่า และสร้างความเป็นกันเองอย่างรวดเร็ว",
                        instructions = """
- ค้นหาจุดร่วมหรือความสนใจร่วมกัน (เช่น ภาพยนตร์ เพลง งานอดิเรก หรืออาหารที่ชอบ)
- ถามคำถามปลายเปิดที่เป็นเชิงบวก เพื่อช่วยให้คู่เดตได้เล่าเรื่องของตนเองได้ง่ายขึ้น
- สังเกตบรรยากาศร้านหรือสถานการณ์รอบข้างมาเป็นหัวข้อสนทนาช่วยลดความตึงเครียด
- รักษาระดับพลังงานบวกและเป็นมิตรตลอดเวลา
                        """.trimIndent()
                    )
                )

                saveSkill(
                    DatingSkill(
                        id = "deep_talk",
                        name = "คุยเชิงลึก (Deep Talk)",
                        description = "ใช้เมื่อต้องการเรียนรู้ทัศนคติ เป้าหมายชีวิต หรือสร้างความผูกพันในระดับที่ลึกซึ้งยิ่งขึ้น",
                        instructions = """
- ถามไถ่ถึงแรงบันดาลใจ เป้าหมายชีวิต เรื่องราวประทับใจในวัยเด็ก หรือค่านิยมส่วนตัว
- เป็นผู้ฟังที่ดี (Active Listener) และสะท้อนสิ่งที่คู่เดตพูดก่อนที่จะแบ่งปันมุมมองของตนเอง
- หลีกเลี่ยงหัวข้อการเมือง ศาสนา หรือประเด็นดราม่าที่ตึงเครียดเกินไปหากยังไม่สนิทกันพอ
- สร้างบรรยากาศที่ปลอดภัยและรับฟังโดยไม่มีการตัดสินใดๆ
                        """.trimIndent()
                    )
                )
                Log.i("DatingSkillManager", "Initialized default skills successfully.")
            }
        } catch (e: Exception) {
            Log.e("DatingSkillManager", "Error initializing default skills", e)
        }
    }

    fun getAllSkills(): List<DatingSkill> {
        val list = mutableListOf<DatingSkill>()
        try {
            val files = skillsDir.listFiles { _, name -> name.endsWith(".md") } ?: return emptyList()
            for (file in files) {
                parseSkillFile(file)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            Log.e("DatingSkillManager", "Error reading skills directory", e)
        }
        return list
    }

    fun getSkill(id: String): DatingSkill? {
        val file = File(skillsDir, "$id.md")
        return parseSkillFile(file)
    }

    fun saveSkill(skill: DatingSkill): Boolean {
        return try {
            val file = File(skillsDir, "${skill.id}.md")
            val content = """
                ---
                name: ${skill.name}
                description: ${skill.description}
                ---
                # ${skill.name}
                ${skill.instructions}
            """.trimIndent()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e("DatingSkillManager", "Error saving skill: ${skill.id}", e)
            false
        }
    }

    fun deleteSkill(id: String): Boolean {
        val file = File(skillsDir, "$id.md")
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    private fun parseSkillFile(file: File): DatingSkill? {
        if (!file.exists()) return null
        try {
            val content = file.readText()
            val lines = content.lines()
            if (lines.isEmpty() || lines[0].trim() != "---") {
                return null
            }
            var name = ""
            var description = ""
            var instructionsStartIdx = -1
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line == "---") {
                    instructionsStartIdx = i + 1
                    break
                }
                if (line.startsWith("name:")) {
                    name = line.substringAfter("name:").trim()
                } else if (line.startsWith("description:")) {
                    description = line.substringAfter("description:").trim()
                }
            }
            if (instructionsStartIdx == -1 || instructionsStartIdx >= lines.size) {
                return null
            }
            val instructions = lines.subList(instructionsStartIdx, lines.size).joinToString("\n").trim()
            val id = file.nameWithoutExtension
            return DatingSkill(id = id, name = name, description = description, instructions = instructions)
        } catch (e: Exception) {
            Log.e("DatingSkillManager", "Error parsing skill file: ${file.name}", e)
            return null
        }
    }
}
