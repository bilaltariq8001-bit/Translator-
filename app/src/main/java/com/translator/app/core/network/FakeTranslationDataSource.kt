package com.translator.app.core.network

import kotlinx.coroutines.delay

/**
 * Stand-in used until the manager's API client arrives. It knows the sample phrases from the design and otherwise
 * echoes the text with the target code, so nobody mistakes it for a real translation. It never leaves the device.
 */
class FakeTranslationDataSource(private val latencyMs: Long = 700) : TranslationDataSource {
    override suspend fun translate(request: TranslateRequest): TranslationOutcome {
        delay(latencyMs)
        val known = samples[request.targetLang]?.get(request.text.trim())
        return TranslationOutcome.Success(Translation(known ?: "[${request.targetLang}] ${request.text}", detectedLang = null))
    }

    private val samples = mapOf(
        "es" to mapOf(
            "Hi! How are you?" to "¡Hola! ¿Cómo estás?",
            "Where is the train station?" to "¿Dónde está la estación de tren?",
        ),
        "ja" to mapOf("How much does this cost?" to "これはいくらですか？"),
        "ur" to mapOf("Hi! How are you?" to "ہیلو! آپ کیسے ہیں؟"),
    )
}
