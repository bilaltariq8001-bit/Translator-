package com.translator.app

import com.translator.app.core.ai.RawOcrLine
import com.translator.app.core.ai.ScannedLine
import com.translator.app.core.ai.cleanOcrLine
import com.translator.app.core.ai.cleanScannedLines
import com.translator.app.core.ai.isFileNameLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCleanupTest {
    private fun raw(text: String, top: Float = 0.1f, height: Float = 0.05f, conf: Float = 0.9f) =
        RawOcrLine(ScannedLine(text, 0.1f, top, 0.9f, top + height, 0), conf)

    @Test
    fun leadingSignsAreRemoved() {
        assertEquals("Hello world", cleanOcrLine("• Hello world"))
        assertEquals("Hello world", cleanOcrLine("» ← © Hello world"))
        assertEquals("Check this", cleanOcrLine("✓ Check this |"))
        assertEquals("Open the door", cleanOcrLine("  -  Open   the door  "))
    }

    @Test
    fun openingQuotesAndSpanishMarksAreKept() {
        assertEquals("¿Dónde está la estación?", cleanOcrLine("¿Dónde está la estación?"))
        assertEquals("\"Stop\" he said.", cleanOcrLine("\"Stop\" he said."))
        assertEquals("(see page 4)", cleanOcrLine("(see page 4)"))
    }

    @Test
    fun fileNameLinesAreRecognised() {
        assertTrue(isFileNameLine("google-services.json"))
        assertTrue(isFileNameLine("py.json"))
        assertTrue(isFileNameLine("photo1.py"))
        assertFalse(isFileNameLine("Put the file google-services.json here"))
        assertFalse(isFileNameLine("Where is the train station?"))
    }

    @Test
    fun clutterLinesAreDropped() {
        val lines = cleanScannedLines(listOf(raw("• Real sentence one"), raw("google-services.json"), raw("@#%&"), raw("x"), raw("Real sentence two")))
        assertEquals(listOf("Real sentence one", "Real sentence two"), lines.map { it.text })
    }

    @Test
    fun lowConfidenceLinesAreDropped() {
        val lines = cleanScannedLines(listOf(raw("Clear text here"), raw("Blurry thing here", conf = 0.2f)))
        assertEquals(listOf("Clear text here"), lines.map { it.text })
    }

    @Test
    fun noConfidenceFromTheReaderIsNotTreatedAsLow() {
        assertEquals(1, cleanScannedLines(listOf(raw("Plain text line", conf = 0f))).size)
    }

    @Test
    fun muchSmallerBackgroundTextIsDropped() {
        val body = (0 until 5).map { raw("Body line number $it", top = 0.1f * it, height = 0.06f) }
        val tiny = raw("Tiny background label", top = 0.8f, height = 0.01f)
        val lines = cleanScannedLines(body + tiny)
        assertEquals(5, lines.size)
        assertTrue(lines.none { it.text.contains("Tiny") })
    }

    @Test
    fun fewLinesAreNeverDroppedForSize() {
        val lines = cleanScannedLines(listOf(raw("Big title line", height = 0.08f), raw("small note here", top = 0.5f, height = 0.01f)))
        assertEquals(2, lines.size)
    }
}
