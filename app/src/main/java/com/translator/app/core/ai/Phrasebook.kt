package com.translator.app.core.ai

import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import kotlinx.coroutines.CancellationException

/** Ready-made English travel phrases by category. They are translated on request, so no AI model is needed. */
object Phrasebook {
    val categories: Map<String, List<String>> = linkedMapOf(
        "Restaurant" to listOf("A table for two, please", "Can I see the menu?", "What do you recommend?", "I am vegetarian", "Is this dish spicy?", "The bill, please"),
        "Directions" to listOf("Where is the train station?", "How do I get to the airport?", "Is it far from here?", "Can you show me on the map?", "Where is the nearest bathroom?", "Please turn left"),
        "Hotel" to listOf("I have a reservation", "Can I check in now?", "What time is breakfast?", "The room key does not work", "Can I have more towels, please?", "Please call me a taxi"),
        "Emergency" to listOf("I need help", "Please call an ambulance", "Please call the police", "I lost my passport", "Where is the nearest hospital?", "I am allergic to this"),
    )
}

/** Travel phrases from the built-in [Phrasebook], translated with the normal translator. The writing tools need AI. */
class PhrasebookToolSource(private val translator: TranslationDataSource) : AiToolSource {

    override suspend fun write(kind: AiWriterKind, input: String, option: String): AiResult<String> =
        AiResult.Failure(TranslationError.AiUnavailable)

    override suspend fun phrases(category: String, langCode: String): AiResult<List<AiPhrase>> {
        val english = Phrasebook.categories[category] ?: return AiResult.Failure(TranslationError.Validation("No phrases for that category."))
        return try {
            // One request with a line per phrase; if the lines do not come back one to one, translate them one by one.
            val joined = translator.translate(TranslateRequest(english.joinToString("\n"), "en", langCode, "Travel"))
            if (joined is TranslationOutcome.Failure) return AiResult.Failure(joined.error)
            val lines = (joined as TranslationOutcome.Success).value.text.split("\n").map { it.trim() }
            if (lines.size == english.size && lines.all { it.isNotEmpty() }) {
                AiResult.Success(english.zip(lines) { e, n -> AiPhrase(e, n, null) })
            } else {
                val out = english.map { e ->
                    when (val r = translator.translate(TranslateRequest(e, "en", langCode, "Travel"))) {
                        is TranslationOutcome.Success -> AiPhrase(e, r.value.text, null)
                        is TranslationOutcome.Failure -> return AiResult.Failure(r.error)
                    }
                }
                AiResult.Success(out)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failure(TranslationError.Unknown(e))
        }
    }
}

/** Uses [primary] (the AI model) and, for travel phrases only, falls back to [fallback] when AI is not set up or out of quota. */
class FallbackToolSource(private val primary: AiToolSource, private val fallback: AiToolSource) : AiToolSource {
    override suspend fun write(kind: AiWriterKind, input: String, option: String): AiResult<String> = primary.write(kind, input, option)

    override suspend fun phrases(category: String, langCode: String): AiResult<List<AiPhrase>> {
        val first = primary.phrases(category, langCode)
        if (first is AiResult.Failure && (first.error is TranslationError.AiUnavailable || first.error is TranslationError.RateLimited)) {
            return fallback.phrases(category, langCode)
        }
        return first
    }
}
