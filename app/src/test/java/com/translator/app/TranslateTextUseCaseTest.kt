package com.translator.app

import com.translator.app.core.data.InMemoryHistoryRepository
import com.translator.app.core.data.TranslateTextUseCase
import com.translator.app.core.network.FakeTranslationDataSource
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateTextUseCaseTest {
    private val request = TranslateRequest("Hi! How are you?", "en", "es", "General")

    @Test
    fun success_isSavedToHistory() = runBlocking {
        val history = InMemoryHistoryRepository()
        val useCase = TranslateTextUseCase(FakeTranslationDataSource(latencyMs = 0), history, clock = { 42L })

        val (outcome, id) = useCase(request)

        assertTrue(outcome is TranslationOutcome.Success)
        assertEquals("¡Hola! ¿Cómo estás?", (outcome as TranslationOutcome.Success).value.text)
        val saved = history.items.first().single()
        assertEquals(id, saved.id)
        assertEquals(42L, saved.timeMillis)
        assertEquals("Hi! How are you?", saved.sourceText)
    }

    @Test
    fun failure_isNotSaved() = runBlocking {
        val history = InMemoryHistoryRepository()
        val failing = object : TranslationDataSource {
            override suspend fun translate(request: TranslateRequest) = TranslationOutcome.Failure(TranslationError.Network)
        }

        val (outcome, id) = TranslateTextUseCase(failing, history)(request)

        assertEquals(TranslationOutcome.Failure(TranslationError.Network), outcome)
        assertNull(id)
        assertTrue(history.items.first().isEmpty())
    }

    @Test
    fun favorite_toggles() = runBlocking {
        val history = InMemoryHistoryRepository()
        val (_, id) = TranslateTextUseCase(FakeTranslationDataSource(0), history)(request)

        history.setFavorite(id!!, true)

        assertTrue(history.items.first().single().favorite)
    }
}
