package com.translator.app.core.offline

import androidx.compose.runtime.compositionLocalOf
import com.translator.app.core.model.Language

/** Codes of the installed offline packs, so any language list can show which ones work without internet. */
val LocalOfflineReady = compositionLocalOf<Set<String>> { emptySet() }

/** Codes being downloaded or removed right now. */
val LocalOfflineBusy = compositionLocalOf<Set<String>> { emptySet() }

/** Starts downloading a language pack to the phone. Nothing is downloaded until the user taps Download. */
val LocalOfflineDownload = compositionLocalOf<(Language) -> Unit> { {} }
