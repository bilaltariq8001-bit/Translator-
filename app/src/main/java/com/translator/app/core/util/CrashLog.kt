package com.translator.app.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a short, privacy-safe note about the last crash so a problem seen on someone else's phone can be understood: the
 * kind of error and where it happened (class names and code lines only, never the error message, which can contain text
 * the person typed), plus the phone model and Android version. The note stays on the phone; the person chooses whether
 * to copy it out (Settings > About > Diagnostics). The crash itself is not hidden: the normal handler still runs.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { File(app.filesDir, FILE).writeText(describe(thread, error)) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun describe(thread: Thread, error: Throwable): String = buildString {
        append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        append("Thread: ").append(thread.name).append('\n')
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 5) {
            append(if (depth == 0) "Error: " else "Caused by: ").append(t.javaClass.name).append('\n')
            t.stackTrace.take(25).forEach { append("  at ").append(it.className).append('.').append(it.methodName).append(':').append(it.lineNumber).append('\n') }
            t = t.cause; depth++
        }
    }

    /** Everything worth sending with a bug report, as plain text. Contains no user content. */
    fun report(context: Context): String = buildString {
        val info = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
        append("App: ").append(info?.versionName).append('\n')
        append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" / ").append(Build.DEVICE).append('\n')
        append("CPU: ").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
        val am = context.getSystemService(ActivityManager::class.java)
        append("Memory class: ").append(am?.memoryClass).append(" MB, low RAM: ").append(am?.isLowRamDevice).append('\n')
        append("Locale: ").append(Locale.getDefault().toLanguageTag()).append('\n')
        if (Build.VERSION.SDK_INT >= 30) {
            // Why Android ended the app recently (a crash, being killed in the background, low memory ...): the kills that
            // some phone makers' battery managers do show up here.
            val reasons = try { am?.getHistoricalProcessExitReasons(context.packageName, 0, 6).orEmpty() } catch (e: Exception) { emptyList() }
            reasons.forEach { append("Exit: reason ").append(it.reason).append(", importance ").append(it.importance).append(", ").append(SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(it.timestamp))).append('\n') }
        }
        append("\n").append(com.translator.app.core.ads.AdDiagnostics.report(context))
        append("\nLast crash:\n")
        val file = File(context.filesDir, FILE)
        append(if (file.exists()) file.readText() else "none recorded\n")
    }
}
