package com.translator.app.core.offline

import kotlinx.coroutines.flow.StateFlow

/**
 * Language packs that let the phone translate without internet. [ready] holds the codes of installed packs; English is
 * built in and never listed. Translation between two languages needs both packs (English needs none).
 */
interface OfflinePacks {
    val ready: StateFlow<Set<String>>

    /** Codes being downloaded or deleted right now. */
    val busy: StateFlow<Set<String>>

    /** Re-reads which packs are installed. */
    suspend fun refresh()

    /** Downloads the pack for [code]. Returns false if it failed (no internet, no space). */
    suspend fun download(code: String): Boolean

    suspend fun delete(code: String)

    suspend fun translate(text: String, from: String, to: String): String
}

fun OfflinePacks.canTranslate(from: String, to: String): Boolean {
    if (from == "auto" || from == to) return false
    val have = ready.value
    return (from == "en" || from in have) && (to == "en" || to in have)
}
