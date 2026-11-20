package com.translator.app.core.ads

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.WindowCompat

/**
 * A full-screen black layer laid over the app while an App Open (start or resume) or interstitial ad is shown, so the app's
 * own screen never shows through behind the ad, around it, under the status and navigation bars, or for a moment as it opens
 * and closes. While it is up the window background is black too and the bar icons turn light, so the whole screen is black.
 * It also swallows touches. Main thread only.
 */
object AdBackdrop {
    private const val TAG_KEY = "translator_ad_backdrop"

    private class Saved(val background: Drawable?, val lightStatus: Boolean, val lightNav: Boolean)
    private val saved = java.util.WeakHashMap<Activity, Saved>()

    fun isShowing(activity: Activity): Boolean = try {
        (activity.window?.decorView as? ViewGroup)?.findViewWithTag<View>(TAG_KEY) != null
    } catch (_: Throwable) { false }

    fun show(activity: Activity) {
        try {
            val window = activity.window ?: return
            val root = window.decorView as? ViewGroup ?: return
            if (root.findViewWithTag<View>(TAG_KEY) != null) return
            val bars = WindowCompat.getInsetsController(window, root)
            saved[activity] = Saved(
                background = try { root.background?.constantState?.newDrawable() } catch (_: Throwable) { null },
                lightStatus = bars.isAppearanceLightStatusBars,
                lightNav = bars.isAppearanceLightNavigationBars,
            )
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            bars.isAppearanceLightStatusBars = false
            bars.isAppearanceLightNavigationBars = false
            val layer = FrameLayout(activity).apply {
                tag = TAG_KEY
                setBackgroundColor(Color.BLACK)
                isClickable = true
                isFocusable = true
                elevation = 10_000f
                translationZ = 10_000f
            }
            root.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            layer.bringToFront()
        } catch (_: Throwable) { }
    }

    fun hide(activity: Activity) {
        try {
            val window = activity.window ?: return
            val root = window.decorView as? ViewGroup ?: return
            root.findViewWithTag<View>(TAG_KEY)?.let { root.removeView(it) }
            saved.remove(activity)?.let { s ->
                window.setBackgroundDrawable(s.background ?: ColorDrawable(APP_BACKGROUND))
                val bars = WindowCompat.getInsetsController(window, root)
                bars.isAppearanceLightStatusBars = s.lightStatus
                bars.isAppearanceLightNavigationBars = s.lightNav
            }
        } catch (_: Throwable) { }
    }

    /** The app's normal window background (Theme.Translator windowBackground). */
    private const val APP_BACKGROUND = 0xFFF7F9FC.toInt()
}
