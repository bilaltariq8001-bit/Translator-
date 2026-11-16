package com.translator.app.core.ads

import com.translator.app.core.config.CappingConfig

/**
 * Decides whether an interstitial may show, from one setting: time capping (a cooldown since the last ad) or click capping
 * (an ad on every Nth eligible back press). A placement whose counter switch is off is never capped. Plain Kotlin with an
 * injected clock, so it is tested without a device.
 */
class InterstitialCapping(private val now: () -> Long = System::currentTimeMillis) {
    private var eligibleClicks = 0L
    private var lastShownAt = 0L

    /** Call once for every back press that could have shown an ad. */
    fun recordEligible() { eligibleClicks++ }

    fun allows(config: CappingConfig, counterEnabled: Boolean): Boolean {
        if (!counterEnabled) return true
        return if (config.timeCapping) {
            lastShownAt == 0L || now() - lastShownAt >= config.cooldownSeconds * 1000L
        } else {
            eligibleClicks >= config.clickCap
        }
    }

    /** Call when an interstitial was actually shown: starts the cooldown and restarts the click count. */
    fun onShown() {
        lastShownAt = now()
        eligibleClicks = 0L
    }

    fun reset() { lastShownAt = 0L; eligibleClicks = 0L }
}
