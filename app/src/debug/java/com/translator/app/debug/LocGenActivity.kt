package com.translator.app.debug

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.translator.app.core.model.Languages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

/**
 * Developer tool (debug builds only). Translates the app's interface strings (assets/loc_source.json) into every
 * language the app can translate, with Firebase AI, and writes one JSON file per language to the app's external files
 * folder (loc/<code>.json). Only the texts missing from a language's file are translated, so a run can be resumed or
 * extended. It runs in a foreground service, so the phone can be used (or locked) while it works.
 * Start: adb shell am start -n com.translator.app/.debug.LocGenActivity [--es langs "sw,th"]
 */
class LocGenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, LocGenService::class.java).putExtra("langs", intent.getStringExtra("langs")))
        finish()
    }
}

class LocGenService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val placeholder = Regex("%\\d\\\$s")
    private var lastCall = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("locgen", "Translating the app", NotificationManager.IMPORTANCE_LOW))
        fun note(text: String) = Notification.Builder(this, "locgen").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Translating the app").setContentText(text).setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(7, note("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(7, note("Starting…"))
        val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locgen:run").apply { acquire(4 * 60 * 60 * 1000L) }

        val source = JSONObject(assets.open("loc_source.json").bufferedReader().readText())
        val keys = source.keys().asSequence().toList()
        val dir = File(getExternalFilesDir(null), "loc").apply { mkdirs() }
        val done = setOf("en", "es", "fr", "de", "ur", "ar", "hi", "ja", "zh")
        val wanted = intent?.getStringExtra("langs")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: Languages.all.map { it.code }.filter { it !in done }

        scope.launch {
            val gate = Semaphore(4)
            var finished = 0
            wanted.map { code ->
                async {
                    gate.withPermit {
                        val file = File(dir, "$code.json")
                        val existing = try { if (file.exists()) JSONObject(file.readText()) else JSONObject() } catch (e: Exception) { JSONObject() }
                        val missing = keys.filter { !existing.has(it) }
                        val ok = missing.isEmpty() || translate(code, source, missing, file, existing)
                        finished++
                        Log.i("LocGen", "$finished/${wanted.size} $code ${if (ok) "ok" else "FAILED"}")
                        manager.notify(7, note("$finished of ${wanted.size} languages"))
                    }
                }
            }.awaitAll()
            Log.i("LocGen", "ALL DONE")
            wake.release()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.coroutineContext[Job]?.cancel(); super.onDestroy() }

    private suspend fun translate(code: String, source: JSONObject, keys: List<String>, file: File, existing: JSONObject): Boolean {
        val language = Languages.byCodeOrNull(code)?.name ?: code
        val result = JSONObject(existing.toString())
        for (chunk in keys.chunked(110)) {
            val input = JSONObject().apply { chunk.forEach { put(it, source.getString(it)) } }
            var got: JSONObject? = null
            for (attempt in 1..8) {
                val model = if (attempt % 3 == 0) "gemini-flash-latest" else "gemini-flash-lite-latest"
                try {
                    paced()
                    got = ask(model, language, code, input, chunk, source)
                    if (got != null) break
                } catch (e: Exception) {
                    Log.w("LocGen", "$code attempt $attempt failed: ${e.javaClass.simpleName}")
                    // A quota or rate-limit answer means: wait for the window to reopen, then try again.
                    if (e.javaClass.simpleName.contains("Quota") || e.message.orEmpty().contains("429")) delay(65_000)
                }
            }
            if (got == null) { file.writeText(result.toString()); return false }
            chunk.forEach { result.put(it, got.getString(it)) }
            file.writeText(result.toString())
        }
        return true
    }

    /** Keeps the requests under the free-tier rate limit: at most one every 7 seconds. */
    private suspend fun paced() {
        val wait = lastCall + 7_000 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastCall = System.currentTimeMillis()
    }

    private suspend fun ask(model: String, language: String, code: String, input: JSONObject, chunk: List<String>, source: JSONObject): JSONObject? {
        val system = "You are a professional localizer translating the user interface of an Android translation app from English into $language (language code $code). " +
            "You receive a JSON object of string keys and English texts. Reply with one JSON object with exactly the same keys and the texts translated into $language. " +
            "Rules: keep placeholders such as %1\$s or %2\$s exactly as they are, in a natural position. Keep the product name \"AI Translator keyboard\" and the words Google, Gemini, Firebase, Android and Google Play in Latin letters. " +
            "Keep emoji and symbols such as 🔍, ➜, →, · and … unchanged. Keep leading spaces. Use short, natural wording suited to buttons and menus, in the polite everyday register of $language. " +
            "Do not translate the keys. If a text is a name of a language, give its name in $language."
        val ai = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = model,
            generationConfig = generationConfig { responseMimeType = "application/json"; temperature = 0.2f },
            systemInstruction = content { text(system) },
        )
        val text = withTimeout(150_000) { ai.generateContent(input.toString()).text.orEmpty() }
        val out = JSONObject(text.trim().removePrefix("```json").removeSuffix("```").trim())
        for (k in chunk) {
            val t = out.optString(k, "")
            if (t.isBlank()) return null
            if (placeholder.findAll(t).map { it.value }.sorted().toList() != placeholder.findAll(source.getString(k)).map { it.value }.sorted().toList()) return null
        }
        return out
    }
}
