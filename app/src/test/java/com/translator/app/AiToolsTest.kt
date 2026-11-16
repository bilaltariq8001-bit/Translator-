package com.translator.app

import com.translator.app.core.ai.AiChatDataSource
import com.translator.app.core.ai.AiNotConfiguredException
import com.translator.app.core.ai.AiResult
import com.translator.app.core.ai.AiTextGenerator
import com.translator.app.core.ai.AiToolsDataSource
import com.translator.app.core.ai.AiTurn
import com.translator.app.core.ai.AiWriterKind
import com.translator.app.core.ai.parsePhrases
import com.translator.app.core.network.TranslationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolsTest {
    private class Gen(val answer: String = "", val error: Exception? = null) : AiTextGenerator {
        var system = ""
        var user = ""
        var streamedTurns: List<AiTurn> = emptyList()
        override suspend fun generate(system: String, user: String): String {
            this.system = system; this.user = user
            error?.let { throw it }
            return answer
        }
        override fun stream(system: String, turns: List<AiTurn>): Flow<String> {
            this.system = system; streamedTurns = turns
            return flowOf("Hel", "lo")
        }
    }

    @Test
    fun email_promptUsesToneAndKeepsInputAsData() = runBlocking {
        val g = Gen("**Subject:** Leave\n\nDear Ms. Khan")
        val r = AiToolsDataSource(g).write(AiWriterKind.Email, "Ask for Friday off", "Friendly")
        assertTrue(g.system.contains("friendly") && g.system.contains("Subject:"))
        assertEquals("Ask for Friday off", g.user)
        assertFalse(g.system.contains("Ask for Friday off"))
        assertEquals("Subject: Leave\n\nDear Ms. Khan", (r as AiResult.Success).value)
    }

    @Test
    fun slang_promptNamesTheTargetLanguage() = runBlocking {
        val g = Gen("Meaning: great")
        AiToolsDataSource(g).write(AiWriterKind.Slang, "no cap", "Urdu")
        // The whole explanation is asked for in the chosen language, not just one line of it.
        assertTrue(g.system.contains("WHOLE reply in Urdu"))
        assertTrue(g.system.contains("never in English"))
    }

    @Test
    fun grammar_blankAnswerIsAnError() = runBlocking {
        val r = AiToolsDataSource(Gen("  ")).write(AiWriterKind.Grammar, "text", "")
        assertTrue((r as AiResult.Failure).error is TranslationError.Unknown)
    }

    @Test
    fun write_notConfigured_mapsToAiUnavailable() = runBlocking {
        val r = AiToolsDataSource(Gen(error = AiNotConfiguredException())).write(AiWriterKind.Email, "x", "Formal")
        assertEquals(TranslationError.AiUnavailable, (r as AiResult.Failure).error)
    }

    @Test
    fun phrases_parseWithAndWithoutRomanization() {
        val p = parsePhrases(
            "PHRASE: A table for two, please. => 二人です。 => Futari desu.\n" +
                "- **PHRASE:** The bill, please. => La cuenta, por favor.\nnoise\nPHRASE: broken line",
        )
        assertEquals(2, p.size)
        assertEquals("Futari desu.", p[0].romanization)
        assertEquals(null, p[1].romanization)
        assertEquals("La cuenta, por favor.", p[1].native)
    }

    @Test
    fun phrases_promptNamesCategoryAndLanguage() = runBlocking {
        val g = Gen("PHRASE: Hi => Hola")
        val r = AiToolsDataSource(g).phrases("Hotel", "es")
        assertTrue(g.system.contains("Hotel") && g.system.contains("Spanish"))
        assertEquals("Hola", (r as AiResult.Success).value.single().native)
    }

    @Test
    fun chat_sendsOnlyTheLastTwentyTurns_andStreamsChunks() = runBlocking {
        val g = Gen()
        val turns = (1..30).map { AiTurn(it % 2 == 1, "m$it") }
        val chunks = AiChatDataSource(g).reply(turns).toList()
        assertEquals(listOf("Hel", "lo"), chunks)
        assertEquals(20, g.streamedTurns.size)
        assertEquals("m30", g.streamedTurns.last().text)
        assertTrue(g.system.contains("plain text"))
    }
}
