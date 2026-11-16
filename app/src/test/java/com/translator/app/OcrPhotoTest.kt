package com.translator.app

import com.translator.app.core.ai.AiPhotoSource
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.FallbackPhotoSource
import com.translator.app.core.ai.OcrPhotoDataSource
import com.translator.app.core.ai.PhotoTranslation
import com.translator.app.core.ai.ScannedLine
import com.translator.app.core.ai.ScannedText
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.Translation
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPhotoTest {
    private class Tr(val out: TranslationOutcome) : TranslationDataSource {
        var last: TranslateRequest? = null
        override suspend fun translate(request: TranslateRequest): TranslationOutcome { last = request; return out }
    }

    private fun ok(text: String) = TranslationOutcome.Success(Translation(text, null))
    private fun line(t: String, top: Float) = ScannedLine(t, 0.1f, top, 0.9f, top + 0.1f, 0xFFFFFFFF.toInt())

    private fun failing(error: TranslationError) = object : AiPhotoSource {
        override suspend fun translate(jpeg: ByteArray, targetLang: String): AiResult<PhotoTranslation> = AiResult.Failure(error)
    }

    @Test
    fun readsThenTranslates_withAutoSource() = runBlocking {
        val tr = Tr(ok("Emergency exit"))
        val r = OcrPhotoDataSource({ ScannedText("Salida de emergencia") }, tr).translate(ByteArray(1), "en")
        assertEquals("Emergency exit", (r as AiResult.Success).value.translation)
        assertEquals("Salida de emergencia", r.value.original)
        assertEquals("auto", tr.last!!.sourceLang)
        assertEquals("en", tr.last!!.targetLang)
        assertTrue(r.value.overlay.isEmpty())
    }

    @Test
    fun lines_areTranslatedTogetherAndPlacedBack() = runBlocking {
        val tr = Tr(ok("Emergency exit\nNo entry"))
        val scanned = ScannedText("Salida\nNo pasar", listOf(line("Salida de emergencia", 0.2f), line("Prohibido el paso", 0.5f)))
        val r = OcrPhotoDataSource({ scanned }, tr).translate(ByteArray(1), "en")
        assertEquals("Salida de emergencia\nProhibido el paso", tr.last!!.text)
        val overlay = (r as AiResult.Success).value.overlay
        assertEquals(listOf("Emergency exit", "No entry"), overlay.map { it.text })
        assertEquals(0.5f, overlay[1].top, 0.0001f)
    }

    @Test
    fun linesAlreadyInTheTargetLanguage_areLeftAlone() = runBlocking {
        val scanned = ScannedText("a", listOf(line("Hello", 0.2f), line("Hola", 0.5f)))
        val r = OcrPhotoDataSource({ scanned }, Tr(ok("hello\nHello there"))).translate(ByteArray(1), "en")
        assertEquals(listOf("Hello there"), (r as AiResult.Success).value.overlay.map { it.text })
    }

    /** Merges all lines of a multi-line request into one, but translates a single line normally. */
    private class Merging : TranslationDataSource {
        override suspend fun translate(request: TranslateRequest): TranslationOutcome =
            TranslationOutcome.Success(Translation(if (request.text.contains('\n')) "merged into one line" else "T:" + request.text, null))
    }

    @Test
    fun lineCountMismatch_translatesLinesOneByOneSoTheOverlayIsKept() = runBlocking {
        val scanned = ScannedText("a", listOf(line("one", 0.2f), line("two", 0.5f)))
        val r = OcrPhotoDataSource({ scanned }, Merging()).translate(ByteArray(1), "en")
        val overlay = (r as AiResult.Success).value.overlay
        assertEquals(listOf("T:one", "T:two"), overlay.map { it.text })
        assertEquals("T:one" + '\n' + "T:two", r.value.translation)
    }

    @Test
    fun noText_isValidationError() = runBlocking {
        val r = OcrPhotoDataSource({ ScannedText("  ") }, Tr(ok("x"))).translate(ByteArray(1), "en")
        assertTrue((r as AiResult.Failure).error is TranslationError.Validation)
    }

    @Test
    fun translationFailure_isPassedOn() = runBlocking {
        val r = OcrPhotoDataSource({ ScannedText("Hola") }, Tr(TranslationOutcome.Failure(TranslationError.Network))).translate(ByteArray(1), "en")
        assertTrue((r as AiResult.Failure).error is TranslationError.Network)
    }

    @Test
    fun fallback_usesOcrWhenAiIsUnavailableOrLimited() = runBlocking {
        val ocr = OcrPhotoDataSource({ ScannedText("Hola") }, Tr(ok("Hello")))
        assertTrue(FallbackPhotoSource(failing(TranslationError.AiUnavailable), ocr).translate(ByteArray(1), "en") is AiResult.Success)
        assertTrue(FallbackPhotoSource(failing(TranslationError.RateLimited(60)), ocr).translate(ByteArray(1), "en") is AiResult.Success)
    }

    @Test
    fun fallback_keepsOtherAiErrors() = runBlocking {
        val ocr = OcrPhotoDataSource({ ScannedText("Hola") }, Tr(ok("Hello")))
        val r = FallbackPhotoSource(failing(TranslationError.Validation("x")), ocr).translate(ByteArray(1), "en")
        assertTrue((r as AiResult.Failure).error is TranslationError.Validation)
    }

    @Test
    fun ocrFirst_usesAiOnlyWhenThePhoneFindsNoText() = runBlocking {
        val noText = failing(TranslationError.Validation("none"))
        val aiOk = object : AiPhotoSource {
            override suspend fun translate(jpeg: ByteArray, targetLang: String): AiResult<PhotoTranslation> = AiResult.Success(PhotoTranslation("a", "b"))
        }
        val ocrOk = OcrPhotoDataSource({ ScannedText("Hola") }, Tr(ok("Hello")))
        assertEquals("Hello", ((com.translator.app.core.ai.OcrFirstPhotoSource(ocrOk, aiOk).translate(ByteArray(1), "en")) as AiResult.Success).value.translation)
        assertEquals("b", ((com.translator.app.core.ai.OcrFirstPhotoSource(noText, aiOk).translate(ByteArray(1), "en")) as AiResult.Success).value.translation)
        val r = com.translator.app.core.ai.OcrFirstPhotoSource(noText, failing(TranslationError.AiUnavailable)).translate(ByteArray(1), "en")
        assertTrue((r as AiResult.Failure).error is TranslationError.Validation)
        val net = com.translator.app.core.ai.OcrFirstPhotoSource(failing(TranslationError.Network), aiOk).translate(ByteArray(1), "en")
        assertTrue((net as AiResult.Failure).error is TranslationError.Network)
    }
}
