package com.translator.app.core.data

import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationOutcome

class TranslateTextUseCase(
    private val remote: TranslationDataSource,
    private val history: HistoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Translates only. The caller shows the result at once and saves it with [save]. */
    suspend fun translate(request: TranslateRequest): TranslationOutcome = remote.translate(request)

    /** Saves a finished translation to history and returns its id. */
    suspend fun save(request: TranslateRequest, translatedText: String): Long =
        history.add(HistoryItem(0, request.sourceLang, request.targetLang, request.text, translatedText, clock()))

    /** Translates and, on success, saves to history. Returns the saved item id with the outcome. */
    suspend operator fun invoke(request: TranslateRequest): Pair<TranslationOutcome, Long?> {
        val t0 = com.translator.app.core.util.Perf.now()
        val outcome = remote.translate(request)
        com.translator.app.core.util.Perf.log("translate.source", t0, if (outcome is TranslationOutcome.Success) "ok" else "failed")
        if (outcome !is TranslationOutcome.Success) return outcome to null
        val t1 = com.translator.app.core.util.Perf.now()
        val id = history.add(
            HistoryItem(0, request.sourceLang, request.targetLang, request.text, outcome.value.text, clock()),
        )
        com.translator.app.core.util.Perf.log("translate.history", t1)
        return outcome to id
    }
}
