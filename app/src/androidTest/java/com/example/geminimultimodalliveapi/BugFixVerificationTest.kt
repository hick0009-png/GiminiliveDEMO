package com.example.geminimultimodalliveapi

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.geminimultimodalliveapi.agent.DocumentSelector
import com.example.geminimultimodalliveapi.memory.MemoryDbHelper
import com.example.geminimultimodalliveapi.memory.MemoryEntry
import com.example.geminimultimodalliveapi.network.ToolDefinitions
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BugFixVerificationTest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ━━━ ToolDefinitions: lowercase JSON schema types ━━━

    @Test
    fun toolDefinitions_allTypeValuesAreLowercase() {
        val tools = ToolDefinitions.getTools()
        val jsonStr = tools.toString(2)

        val forbidden = listOf("\"OBJECT\"", "\"STRING\"", "\"INTEGER\"", "\"ARRAY\"", "\"NUMBER\"", "\"BOOLEAN\"")
        for (f in forbidden) {
            assertFalse("Found uppercase type $f", jsonStr.contains(f))
        }

        val required = listOf("\"object\"", "\"string\"", "\"integer\"")
        for (r in required) {
            assertTrue("Missing lowercase type $r", jsonStr.contains(r))
        }
    }

    @Test
    fun toolDefinitions_everyFunctionHasNameDescriptionAndParameters() {
        val tools = ToolDefinitions.getTools()
        val funcDecls = tools.getJSONObject(0).getJSONArray("functionDeclarations")

        assertTrue("Need >= 18 function declarations", funcDecls.length() >= 18)

        val names = mutableSetOf<String>()
        for (i in 0 until funcDecls.length()) {
            val decl = funcDecls.getJSONObject(i)
            val name = decl.getString("name")
            assertTrue("Duplicate: $name", names.add(name))
            assertTrue("Missing description for $name", decl.has("description"))
            assertTrue("Missing parameters for $name", decl.has("parameters"))

            val params = decl.getJSONObject("parameters")
            assertEquals("Parameter type must be lowercase for $name", "object", params.getString("type"))

            if (params.has("properties")) {
                val props = params.getJSONObject("properties")
                for (key in props.keySet()) {
                    val prop = props.getJSONObject(key)
                    val propType = prop.getString("type")
                    assertTrue(
                        "Property $key of $name has uppercase type: $propType",
                        propType.all { it.isLowerCase() }
                    )
                }
            }
        }
    }

    // ━━━ MemoryDbHelper: LIKE escape + ORDER BY ━━━

    @Test
    fun memoryDbHelper_searchWithPercentAndUnderscore() {
        val dbHelper = MemoryDbHelper(appContext)
        dbHelper.writableDatabase

        val entry = MemoryEntry(
            id = "test_pct_${System.currentTimeMillis()}",
            content = "ข้อมูล 100% พร้อมใช้งาน_ทดสอบ",
            baseImportance = 3
        )
        dbHelper.insertOrUpdateMemory(entry)

        val byPercent = dbHelper.searchMemories("100%")
        assertTrue("Search with % should find memory", byPercent.any { it.id == entry.id })

        val byUnderscore = dbHelper.searchMemories("พร้อมใช้งาน_ทดสอบ")
        assertTrue("Search with _ should find memory", byUnderscore.any { it.id == entry.id })

        dbHelper.deleteMemory(entry.id)
    }

    @Test
    fun memoryDbHelper_getMemoryListOrderedByLastAccessedDesc() {
        val dbHelper = MemoryDbHelper(appContext)
        dbHelper.writableDatabase

        val idOld = "order_old_${System.currentTimeMillis()}"
        val idNew = "order_new_${System.currentTimeMillis()}"

        dbHelper.insertOrUpdateMemory(MemoryEntry(id = idOld, content = "old", lastAccessedTime = 1000L))
        dbHelper.insertOrUpdateMemory(MemoryEntry(id = idNew, content = "new", lastAccessedTime = 9999999999999L))

        val list = dbHelper.getMemoryList()
        val idxOld = list.indexOfFirst { it.id == idOld }
        val idxNew = list.indexOfFirst { it.id == idNew }

        assertTrue("Old entry not found", idxOld >= 0)
        assertTrue("New entry not found", idxNew >= 0)
        assertTrue("Newer entry should come before older", idxNew < idxOld)

        dbHelper.deleteMemory(idOld)
        dbHelper.deleteMemory(idNew)
    }

    // ━━━ DocumentSelector: .json + .txt support ━━━

    @Test
    fun documentSelector_detectsTxtFiles() {
        val docsDir = File(appContext.filesDir, "documents")
        docsDir.mkdirs()

        File(docsDir, "sel_test_a.txt").writeText(
            "{\"category\":\"test\",\"title\":\"A\",\"description\":\"hello world\"}"
        )
        File(docsDir, "sel_test_b.json").writeText(
            "{\"category\":\"test\",\"title\":\"B\",\"description\":\"hello world\"}"
        )

        val results = DocumentSelector(appContext).selectRelevantDocuments("hello", "test")

        assertTrue("Should find .txt result", results.any { it.fileName == "sel_test_a.txt" })
        assertTrue("Should find .json result", results.any { it.fileName == "sel_test_b.json" })

        File(docsDir, "sel_test_a.txt").delete()
        File(docsDir, "sel_test_b.json").delete()
    }

    // ━━━ POST_NOTIFICATIONS permission ━━━

    @Test
    fun androidManifest_hasPostNotificationsPermission() {
        val granted = PackageManager.PERMISSION_GRANTED
        val result = appContext.packageManager.checkPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            appContext.packageName
        )
        assertEquals("POST_NOTIFICATIONS must be declared", granted, result)
    }

    // ━━━ Clean XML files (no template comments) ━━━

    @Test
    fun backupRulesXml_hasNoTemplateComments() {
        val content = readXml("backup_rules")
        assertNotNull("backup_rules.xml not found", content)
        assertFalse("backup_rules.xml must not contain 'Sample'", content!!.contains("Sample"))
        assertFalse("backup_rules.xml must not contain 'uncomment'", content.contains("uncomment"))
    }

    @Test
    fun dataExtractionRulesXml_hasNoTemplateComments() {
        val content = readXml("data_extraction_rules")
        assertNotNull("data_extraction_rules.xml not found", content)
        assertFalse("data_extraction_rules.xml must not contain 'Sample'", content!!.contains("Sample"))
        assertFalse("data_extraction_rules.xml must not contain 'uncomment'", content.contains("uncomment"))
    }

    // ━━━ Helper ━━━

    private fun readXml(name: String): String? = try {
        val id = appContext.resources.getIdentifier(name, "xml", appContext.packageName)
        if (id == 0) return null
        val parser = appContext.resources.getXml(id)
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
        appContext.resources.openRawResource(id).bufferedReader().readText()
    } catch (_: Exception) { null }
}
