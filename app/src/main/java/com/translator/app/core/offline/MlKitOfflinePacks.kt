package com.translator.app.core.offline

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device translation with ML Kit: the packs are downloaded once and then work with no connection. */
class MlKitOfflinePacks : OfflinePacks {
    private val manager by lazy { RemoteModelManager.getInstance() }
    private val _ready = MutableStateFlow<Set<String>>(emptySet())
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    override val ready: StateFlow<Set<String>> = _ready.asStateFlow()
    override val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private fun model(code: String) = TranslateRemoteModel.Builder(tag(code)).build()
    private fun tag(code: String) = TranslateLanguage.fromLanguageTag(code) ?: throw IllegalArgumentException("No offline pack for $code")

    override suspend fun refresh() {
        try {
            val models = withContext(Dispatchers.Default) { manager.getDownloadedModels(TranslateRemoteModel::class.java) }.await()
            _ready.value = models.map { it.language }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep what we knew; the list will refresh next time.
        }
    }

    override suspend fun download(code: String): Boolean {
        _busy.update { it + code }
        return try {
            manager.download(model(code), DownloadConditions.Builder().build()).await()
            _ready.update { it + code }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            _busy.update { it - code }
        }
    }

    override suspend fun delete(code: String) {
        _busy.update { it + code }
        try {
            closeTranslatorsFor(code)
            manager.deleteDownloadedModel(model(code)).await()
            _ready.update { it - code }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            refresh()
        } finally {
            _busy.update { it - code }
        }
    }

    // Loading a language model takes most of the time of an offline translation, so the two most recently used pairs stay
    // loaded. They are closed when evicted, and whenever a pack they use is deleted.
    private val translators = LinkedHashMap<String, com.google.mlkit.nl.translate.Translator>()
    private val translatorLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun translate(text: String, from: String, to: String): String = translatorLock.withLock {
        val key = "$from>$to"
        val translator = translators.remove(key)
            ?: Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(tag(from)).setTargetLanguage(tag(to)).build())
        translators[key] = translator   // most recently used goes last
        while (translators.size > 2) {
            val eldest = translators.keys.first()
            translators.remove(eldest)?.close()
        }
        translator.translate(text).await()
    }

    private suspend fun closeTranslatorsFor(code: String) = translatorLock.withLock {
        translators.keys.filter { code == it.substringBefore('>') || code == it.substringAfter('>') }.forEach { translators.remove(it)?.close() }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }
}
