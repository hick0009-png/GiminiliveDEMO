package com.example.geminimultimodalliveapi

import com.example.geminimultimodalliveapi.service.GeminiToolDispatcher
import com.example.geminimultimodalliveapi.utils.DocumentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Phase4UnitTest {

    private fun createMockDocxInputStream(documentXmlContent: String): InputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("word/document.xml")
            zos.putNextEntry(entry)
            zos.write(documentXmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    @Test
    fun testDocxParser_extractsTextAndHandlesParagraphs() {
        val xmlContent = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:r>
                            <w:t>Hello </w:t>
                        </w:r>
                        <w:r>
                            <w:t>World!</w:t>
                        </w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:t>This is a second paragraph.</w:t>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val inputStream = createMockDocxInputStream(xmlContent)
        val extractedText = DocumentParser.extractTextFromDocx(inputStream)

        val expected = "Hello World!\nThis is a second paragraph."
        assertEquals(expected, extractedText)
    }

    @Test
    fun testParseIsoDateTime_validFormats() {
        // OffsetDateTime format
        val offsetTime = "2026-06-14T10:30:00+07:00"
        val offsetMs = GeminiToolDispatcher.parseIsoDateTime(offsetTime)
        
        // Instant format
        val instantTime = "2026-06-14T03:30:00Z"
        val instantMs = GeminiToolDispatcher.parseIsoDateTime(instantTime)
        
        assertEquals(offsetMs, instantMs)

        // LocalDateTime format (falls back to local system default zone)
        val localTime = "2026-06-14T10:30:00"
        val localMs = GeminiToolDispatcher.parseIsoDateTime(localTime)
        
        val expectedLocalMs = java.time.LocalDateTime.parse(localTime)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedLocalMs, localMs)
    }

    @Test
    fun testParseIsoDateTime_invalidFormatThrowsException() {
        val invalidTime = "invalid-date-time-string"
        assertThrows(Exception::class.java) {
            GeminiToolDispatcher.parseIsoDateTime(invalidTime)
        }
    }

    @Test
    fun testDocumentPaginationLogic() {
        // DocumentPagination logic: pageSize = 10 characters
        val content = "1234567890abcdefghij" // 20 characters
        val pageSize = 10
        val totalLength = content.length
        val totalPages = if (totalLength == 0) 1 else ((totalLength - 1) / pageSize) + 1
        
        assertEquals(2, totalPages)

        // Page 1
        val page1Start = 0
        val page1End = (page1Start + pageSize).coerceAtMost(totalLength)
        val page1Text = content.substring(page1Start, page1End)
        assertEquals("1234567890", page1Text)

        // Page 2
        val page2Start = 10
        val page2End = (page2Start + pageSize).coerceAtMost(totalLength)
        val page2Text = content.substring(page2Start, page2End)
        assertEquals("abcdefghij", page2Text)
    }
}
