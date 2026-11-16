package com.translator.app.core.network

/**
 * Remembers recent successful translations so asking for the same thing again answers at once instead of going back to
 * the network. A result is reused only when everything that shapes it is identical: the text, both languages, the style
 * and [variant] (which says which engine is in use, so switching AI translation on or off never serves the other
 * engine's answer). Failures are never stored, so a retry always really retries, and nothing is kept on disk or after
 * the app closes.
 */
class CachingTranslationDataSource(
    private val delegate: TranslationDataSource,
    private val variant: suspend () -> String = { "" },
    private val capacity: Int = 64,
) : TranslationDataSource {
    private val cache = object : LinkedHashMap<String, Translation>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translation>?) = size > capacity
    }

    override suspend fun translate(request: TranslateRequest): TranslationOutcome {
        val key = listOf(variant(), request.sourceLang, request.targetLang, request.tone, request.text).joinToString("\u0000")
        synchronized(cache) { cache[key] }?.let { return TranslationOutcome.Success(it) }
        val outcome = delegate.translate(request)
        if (outcome is TranslationOutcome.Success) synchronized(cache) { cache[key] = outcome.value }
        return outcome
    }
}
