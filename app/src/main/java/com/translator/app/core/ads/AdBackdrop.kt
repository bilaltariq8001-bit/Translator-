package com.translator.app.core.ads

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * A full-screen black layer laid over the app while an App Open or interstitial ad is shown, so the app's own screen never
 * shows through behind the ad, around it, or for a moment as it opens and closes. It also swallows touches. Main thread only.
 */
object AdBackdrop {
    private const val TAG_KEY = "translator_ad_backdrop"

    fun show(activity: Activity) {
        try {
            val root = activity.window?.decorView as? ViewGroup ?: return
            if (root.findViewWithTag<View>(TAG_KEY) != null) return
            val layer = FrameLayout(activity).apply {
                tag = TAG_KEY
                setBackgroundColor(Color.BLACK)
                isClickable = true
                isFocusable = true
                elevation = 10_000f
            }
            root.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } catch (_: Throwable) { }
    }

    fun hide(activity: Activity) {
        try {
            val root = activity.window?.decorView as? ViewGroup ?: return
            root.findViewWithTag<View>(TAG_KEY)?.let { root.removeView(it) }
        } catch (_: Throwable) { }
    }
}
