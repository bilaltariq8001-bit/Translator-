package com.translator.app

import com.translator.app.core.ai.AiInsightsDataSource
import com.translator.app.core.ai.AiNotConfiguredException
import com.translator.app.core.ai.AiTextGenerator
import com.translator.app.core.ai.InsightsOutcome
import com.translator.app.core.ai.InsightsRequest
import com.translator.app.core.ai.parseInsights
import com.translator.app.core.network.TranslationError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiInsightsTest {
    private val request = InsightsRequest("Hi! How are you?", "¡Hola! ¿Cómo estás?", "en", "es")

    private class Gen(val answer: String? = null, val error: Exception? = null) : AiTextGenerator {
        var system = ""
        var user = ""
        override suspend fun generate(system: String, user: String): String {
            this.system = system; this.user = user
            error?.let { throw it }
            return answer.orEmpty()
        }
    }

    @Test
    fun parse_readsAllSections() {
        val v = parseInsights(
            """
            MEANING: A friendly, informal greeting.
            VARIANT: Hola, ¿qué tal?
            VARIANT: ¿Cómo andas?
            EXAMPLE: Hi! How are you today? => ¡Hola! ¿Cómo estás hoy?
            SYNONYM: ¿Qué tal?
            SYNONYM: ¿Cómo te va?
            """.trimIndent(),
        )!!
        assertEquals("A friendly, informal greeting.", v.meaning)
        assertEquals(listOf("Hola, ¿qué tal?", "¿Cómo andas?"), v.variants)
        assertEquals("¡Hola! ¿Cómo estás hoy?", v.examples.single().target)
        assertEquals(2, v.synonyms.size)
    }

    @Test
    fun parse_toleratesBulletsAndMarkdown() {
        val v = parseInsights("- **MEANING:** Greeting\n* `VARIANT:` Hola\n• EXAMPLE: A → B")!!
        assertEquals("Greeting", v.meaning)
        assertEquals(listOf("Hola"), v.variants)
        assertEquals("B", v.examples.single().target)
    }

    @Test
    fun parse_capsListsAndIgnoresNoise() {
        val many = (1..9).joinToString("\n") { "SYNONYM: s$it" } + "\nrandom chatter\nEXAMPLE: no arrow here"
        val v = parseInsights("MEANING: m\n$many")!!
        assertEquals(5, v.synonyms.size)
        assertTrue(v.examples.isEmpty())
    }

    @Test fun parse_nothingUseful_isNull() = assertNull(parseInsights("Sorry, I can't help with that."))

    @Test
    fun dataSource_success_andPromptKeepsTextAsData() = runBlocking {
        val g = Gen("MEANING: ok\nSYNONYM: x")
        val o = AiInsightsDataSource(g).insights(request)
        assertEquals("ok", (o as InsightsOutcome.Success).value.meaning)
        assertTrue(g.system.contains("English") && g.system.contains("Spanish"))
        assertFalse(g.system.contains("Hi! How are you?"))
        assertTrue(g.user.contains("Hi! How are you?") && g.user.contains("<<<"))
    }

    @Test
    fun dataSource_mapsFailures() = runBlocking {
        val notSet = AiInsightsDataSource(Gen(error = AiNotConfiguredException())).insights(request)
        assertEquals(TranslationError.AiUnavailable, (notSet as InsightsOutcome.Failure).error)
        val garbage = AiInsightsDataSource(Gen("nope")).insights(request)
        assertTrue((garbage as InsightsOutcome.Failure).error is TranslationError.Unknown)
    }
}
