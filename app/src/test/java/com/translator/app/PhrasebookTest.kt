package com.translator.app

import com.translator.app.core.ai.AiPhrase
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.AiToolSource
import com.translator.app.core.ai.AiWriterKind
import com.translator.app.core.ai.FallbackToolSource
import com.translator.app.core.ai.Phrasebook
import com.translator.app.core.ai.PhrasebookToolSource
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.Translation
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasebookTest {
    /** Upper-cases every line, so the result keeps the one-to-one lines. */
    private class Shout : TranslationDataSource {
        var calls = 0
        override suspend fun translate(request: TranslateRequest): TranslationOutcome {
            calls++
            return TranslationOutcome.Success(Translation(request.text.uppercase(), null))
        }
    }

    private class Merging : TranslationDataSource {
        var calls = 0
        override suspend fun translate(request: TranslateRequest): TranslationOutcome {
            calls++
            return TranslationOutcome.Success(Translation(request.text.replace("\n", " ").uppercase(), null))
        }
    }

    private class Unavailable : AiToolSource {
        override suspend fun write(kind: AiWriterKind, input: String, option: String) = AiResult.Failure(TranslationError.AiUnavailable)
        override suspend fun phrases(category: String, langCode: String) = AiResult.Failure(TranslationError.AiUnavailable)
    }

    @Test
    fun everyCategoryHasSixPhrases() {
        assertTrue(Phrasebook.categories.keys.containsAll(listOf("Restaurant", "Directions", "Hotel", "Emergency")))
        assertTrue(Phrasebook.categories.values.all { it.size == 6 })
    }

    @Test
    fun phrases_areTranslatedInOneRequest() = runBlocking {
        val tr = Shout()
        val r = PhrasebookToolSource(tr).phrases("Hotel", "es") as AiResult.Success
        assertEquals(6, r.value.size)
        assertEquals(AiPhrase("I have a reservation", "I HAVE A RESERVATION", null), r.value[0])
        assertEquals(1, tr.calls)
    }

    @Test
    fun ifLinesComeBackMerged_phrasesAreTranslatedOneByOne() = runBlocking {
        val tr = Merging()
        val r = PhrasebookToolSource(tr).phrases("Emergency", "fr") as AiResult.Success
        assertEquals(6, r.value.size)
        assertEquals(7, tr.calls)
    }

    @Test
    fun fallback_usesPhrasebookWhenAiIsUnavailable_butNotForWriting() = runBlocking {
        val tool = FallbackToolSource(Unavailable(), PhrasebookToolSource(Shout()))
        assertTrue(tool.phrases("Restaurant", "de") is AiResult.Success)
        assertTrue((tool.write(AiWriterKind.Email, "hi", "Formal") as AiResult.Failure).error is TranslationError.AiUnavailable)
    }
}
