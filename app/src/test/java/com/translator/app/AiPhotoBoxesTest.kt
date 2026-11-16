package com.translator.app

import com.translator.app.core.ai.AiPhotoDataSource
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.AiTextGenerator
import com.translator.app.core.ai.parseBoxedLines
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPhotoBoxesTest {
    private val answer = """
        ```json
        [
          {"box_2d": [100, 50, 200, 900], "text": "خروج", "translation": "Exit"},
          {"box_2d": [300, 50, 400, 700], "text": "ممنوع \"داخلہ\"", "translation": "No \"entry\""}
        ]
        ```
    """.trimIndent()

    @Test
    fun parsesBoxesAndTextsAndFences() {
        val lines = parseBoxedLines(answer)
        assertEquals(2, lines.size)
        assertEquals("خروج", lines[0].text)
        assertEquals(0.05f, lines[0].left, 0.0001f)
        assertEquals(0.1f, lines[0].top, 0.0001f)
        assertEquals(0.9f, lines[0].right, 0.0001f)
        assertEquals(0.2f, lines[0].bottom, 0.0001f)
        assertEquals("No \"entry\"", lines[1].translation)
    }

    @Test
    fun skipsItemsWithoutAUsableBoxOrText() {
        val lines = parseBoxedLines("""[{"text":"a","translation":"b"},{"box_2d":[10,10,10,50],"text":"a","translation":"b"},{"box_2d":[10,10,50,50],"text":"","translation":"b"}]""")
        assertTrue(lines.isEmpty())
    }

    private class Gen(val answer: String) : AiTextGenerator {
        override suspend fun generate(system: String, user: String) = ""
        override suspend fun generateFromImage(system: String, prompt: String, jpeg: ByteArray) = answer
    }

    @Test
    fun dataSource_buildsAnOverlayWithSampledBackgrounds() = runBlocking {
        val r = AiPhotoDataSource(Gen(answer), sampler = { _, boxes -> boxes.map { 0xFF112233.toInt() } }).translate(ByteArray(1), "en")
        val v = (r as AiResult.Success).value
        assertEquals(listOf("Exit", "No \"entry\""), v.overlay.map { it.text })
        assertEquals(0xFF112233.toInt(), v.overlay[0].background)
        assertEquals("Exit\nNo \"entry\"", v.translation)
    }

    @Test
    fun linesAlreadyInTheTargetLanguageGetNoOverlay() = runBlocking {
        val same = """[{"box_2d":[10,10,50,500],"text":"Hello there","translation":"Hello there"}]"""
        val r = AiPhotoDataSource(Gen(same), sampler = { _, b -> b.map { 0 } }).translate(ByteArray(1), "en")
        assertTrue((r as AiResult.Success).value.overlay.isEmpty())
    }

    @Test
    fun oldFormatAnswerStillWorksWithoutOverlay() = runBlocking {
        val r = AiPhotoDataSource(Gen("ORIGINAL:\nHola\nTRANSLATION:\nHello")).translate(ByteArray(1), "en")
        assertEquals("Hello", (r as AiResult.Success).value.translation)
        assertTrue(r.value.overlay.isEmpty())
    }
}
