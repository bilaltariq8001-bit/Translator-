package com.translator.app.core.offline

import com.translator.app.core.network.TranslateRequest
import com.translator.app.core.network.TranslationDataSource
import com.translator.app.core.network.TranslationError
import com.translator.app.core.network.TranslationOutcome
import com.translator.app.core.network.Translation
import kotlinx.coroutines.CancellationException

/**
 * Uses the installed language packs when the phone is offline, and as a fallback when the online service cannot be
 * reached. Without both packs (or with "auto" detect) it behaves exactly like [online].
 */
class OfflineFallbackDataSource(
    private val online: TranslationDataSource,
    private val packs: OfflinePacks,
    private val isOnline: () -> Boolean,
) : TranslationDataSource {

    override suspend fun translate(request: TranslateRequest): TranslationOutcome {
        val offlineOk = packs.canTranslate(request.sourceLang, request.targetLang)
        if (offlineOk && !isOnline()) return offline(request)
        val result = online.translate(request)
        if (offlineOk && result is TranslationOutcome.Failure && result.error.canFallBack()) {
            val local = offline(request)
            if (local is TranslationOutcome.Success) return local
        }
        return result
    }

    private fun TranslationError.canFallBack() =
        this is TranslationError.Network || this is TranslationError.Timeout || this is TranslationError.RateLimited || this is TranslationError.Server

    private suspend fun offline(r: TranslateRequest): TranslationOutcome = try {
        TranslationOutcome.Success(Translation(packs.translate(r.text, r.sourceLang, r.targetLang), null))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TranslationOutcome.Failure(TranslationError.Unknown(e))
    }
}
