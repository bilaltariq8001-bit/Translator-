package com.translator.app.core.analytics

import com.translator.app.core.network.TranslationError

/** A fixed word for why something failed: never the error message, which could contain text. */
fun TranslationError.reason(): String = when (this) {
    TranslationError.Network -> "network"
    TranslationError.Timeout -> "timeout"
    is TranslationError.RateLimited -> "rate_limited"
    is TranslationError.Validation -> "validation"
    TranslationError.Auth -> "auth"
    is TranslationError.Server -> "server"
    is TranslationError.Unknown -> "unknown"
    TranslationError.AiUnavailable -> "ai_unavailable"
}
