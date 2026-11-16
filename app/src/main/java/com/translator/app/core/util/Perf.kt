package com.translator.app.core.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Timing notes for development. They are written to the log only in a debuggable build (never in the release app) and
 * contain only a label and a number of milliseconds: never the text being translated, typed or answered.
 */
object Perf {
    @Volatile private var on = false

    fun init(context: Context) { on = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    fun now(): Long = System.nanoTime() / 1_000_000

    fun log(label: String, startedAt: Long, note: String = "") {
        if (on) Log.i("Perf", "$label ${now() - startedAt} ms $note".trim())
    }
}
