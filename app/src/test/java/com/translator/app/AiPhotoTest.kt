package com.translator.app

import com.translator.app.core.ai.AiNotConfiguredException
import com.translator.app.core.ai.AiPhotoDataSource
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.AiTextGenerator
import com.translator.app.core.ai.parsePhoto
import com.translator.app.core.network.TranslationError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPhotoTest {
    private class Gen(val answer: String = "", val error: Exception? = null) : AiTextGenerator {
        var system = ""
        var size = -1
        override suspend fun generate(system: String, user: String): String = ""
        override suspend fun generateFromImage(system: String, prompt: String, jpeg: ByteArray): String {
            this.system = system; size = jpeg.size
            error?.let { throw it }
            return answer
        }
    }

    @Test
    fun parse_readsOriginalAndTranslation() {
        val p = parsePhoto("ORIGINAL:\nSalida de emergencia\nTRANSLATION:\nEmergency exit")!!
        assertEquals("Salida de emergencia", p.original)
        assertEquals("Emergency exit", p.translation)
    }

    @Test
    fun parse_keepsMultipleLinesAndIgnoresBold() {
        val p = parsePhoto("**ORIGINAL:**\nA\nB\n**TRANSLATION:**\nOne\nTwo")!!
        assertEquals("A\nB", p.original)
        assertEquals("One\nTwo", p.translation)
    }

    @Test
    fun parse_noTextOrEmptyIsNull() {
        assertNull(parsePhoto("NO_TEXT"))
        assertNull(parsePhoto(""))
        assertNull(parsePhoto("Sorry, I cannot read that"))
        assertNull(parsePhoto("ORIGINAL:\nx\nTRANSLATION:\n  "))
    }

    @Test
    fun translate_success_sendsPhotoAndTargetLanguage() = runBlocking {
        val gen = Gen("ORIGINAL:\nHola\nTRANSLATION:\nHello")
        val r = AiPhotoDataSource(gen).translate(ByteArray(10), "en")
        assertTrue(r is AiResult.Success)
        assertEquals("Hello", (r as AiResult.Success).value.translation)
        assertEquals(10, gen.size)
        assertTrue(gen.system.contains("English"))
    }

    @Test
    fun translate_noText_isValidationError() = runBlocking {
        val r = AiPhotoDataSource(Gen("NO_TEXT")).translate(ByteArray(1), "en")
        assertTrue((r as AiResult.Failure).error is TranslationError.Validation)
    }

    @Test
    fun translate_notConfigured_isAiUnavailable() = runBlocking {
        val r = AiPhotoDataSource(Gen(error = AiNotConfiguredException(IllegalStateException("x")))).translate(ByteArray(1), "en")
        assertEquals(TranslationError.AiUnavailable, (r as AiResult.Failure).error)
    }
}
