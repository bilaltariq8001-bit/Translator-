package com.translator.app.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * Opening another app's screen can fail on phones whose maker removed or renamed it (several Vivo, Oppo and Xiaomi builds
 * miss some Settings pages). Every such jump goes through here, so a missing screen never closes the app.
 */
object SafeIntents {
    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }

    /** This app's page in the system settings (to grant a permission), falling back to the main settings screen. */
    fun openAppSettings(context: Context) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        if (!start(context, details) && !start(context, Intent(Settings.ACTION_SETTINGS))) {
            try { Toast.makeText(context, "Open Settings > Apps to allow access", Toast.LENGTH_LONG).show() } catch (_: Throwable) { }
        }
    }
}
