package com.translator.app

import com.translator.app.core.model.Languages
import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.Translation
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import com.translator.app.core.offline.OfflineFallbackDataSource
import com.translator.app.core.offline.OfflinePacks
import com.translator.app.core.offline.canTranslate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFallbackTest {
    private class FakePacks(have: Set<String>) : OfflinePacks {
        override val ready: StateFlow<Set<String>> = MutableStateFlow(have)
        override val busy: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun refresh() {}
        override suspend fun download(code: String) = true
        override suspend fun delete(code: String) {}
        override suspend fun translate(text: String, from: String, to: String) = "offline:$text"
    }

    private class Online(val outcome: TranslationOutcome) : TranslationDataSource {
        var calls = 0
        override suspend fun translate(request: TranslateRequest): TranslationOutcome { calls++; return outcome }
    }

    private val ok = TranslationOutcome.Success(Translation("online", null))
    private val down = TranslationOutcome.Failure(TranslationError.Network)
    private fun req(from: String = "en", to: String = "es") = TranslateRequest("hi", from, to, "General")

    @Test
    fun canTranslate_needsBothPacks_englishIsBuiltIn() {
        val p = FakePacks(setOf("es"))
        assertTrue(p.canTranslate("en", "es"))
        assertTrue(p.canTranslate("es", "en"))
        assertFalse(p.canTranslate("es", "fr"))
        assertFalse(p.canTranslate("auto", "es"))
        assertFalse(p.canTranslate("es", "es"))
    }

    @Test
    fun offlineAndPacksReady_usesPacksWithoutCallingOnline() = runBlocking {
        val online = Online(ok)
        val r = OfflineFallbackDataSource(online, FakePacks(setOf("es")), { false }).translate(req())
        assertEquals("offline:hi", (r as TranslationOutcome.Success).value.text)
        assertEquals(0, online.calls)
    }

    @Test
    fun onlineFailure_fallsBackToPacks() = runBlocking {
        val r = OfflineFallbackDataSource(Online(down), FakePacks(setOf("es")), { true }).translate(req())
        assertEquals("offline:hi", (r as TranslationOutcome.Success).value.text)
    }

    @Test
    fun withoutPacks_behavesLikeOnline() = runBlocking {
        val r = OfflineFallbackDataSource(Online(down), FakePacks(emptySet()), { true }).translate(req())
        assertTrue((r as TranslationOutcome.Failure).error is TranslationError.Network)
    }

    @Test
    fun onlineSuccess_isUsedWhenConnected() = runBlocking {
        val r = OfflineFallbackDataSource(Online(ok), FakePacks(setOf("es")), { true }).translate(req())
        assertEquals("online", (r as TranslationOutcome.Success).value.text)
    }

    @Test
    fun languageList_isLargeAndUnique_withDownloadablePacks() {
        val codes = Languages.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.size >= 100)
        assertTrue(Languages.downloadable.size >= 55)
        assertTrue(Languages.downloadable.none { it.code == "en" })
    }
}
