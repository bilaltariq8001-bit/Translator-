package com.translator.app

import com.translator.app.core.ads.InterstitialCapping
import com.translator.app.core.config.AdIdDefaults
import com.translator.app.core.config.AdPosition
import com.translator.app.core.config.CappingConfig
import com.translator.app.core.config.ConfigReader
import com.translator.app.core.config.InScreenAdType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialCappingTest {
    private var clock = 1_000_000L
    private val capping = InterstitialCapping { clock }
    private val time = CappingConfig(cacheSize = 2, timeCapping = true, cooldownSeconds = 60, clickCap = 3)
    private val clicks = CappingConfig(cacheSize = 2, timeCapping = false, cooldownSeconds = 60, clickCap = 3)

    @Test fun `time capping allows the first ad then waits for the cooldown`() {
        assertTrue(capping.allows(time, counterEnabled = true))
        capping.onShown()
        clock += 59_999
        assertFalse(capping.allows(time, true))
        clock += 1
        assertTrue(capping.allows(time, true))
    }

    @Test fun `click capping shows on every third eligible press`() {
        capping.recordEligible(); assertFalse(capping.allows(clicks, true))
        capping.recordEligible(); assertFalse(capping.allows(clicks, true))
        capping.recordEligible(); assertTrue(capping.allows(clicks, true))
        capping.onShown()
        capping.recordEligible(); assertFalse(capping.allows(clicks, true))
    }

    @Test fun `a placement with the counter off is never capped`() {
        capping.onShown()
        assertTrue(capping.allows(time, counterEnabled = false))
        assertTrue(capping.allows(clicks, counterEnabled = false))
    }

    @Test fun `time capping ignores click counts and the other way round`() {
        capping.onShown()
        repeat(10) { capping.recordEligible() }
        assertFalse(capping.allows(time, true))
        clock += 120_000
        assertTrue(capping.allows(time, true))
    }

    @Test fun `reset clears both`() {
        capping.onShown(); capping.reset()
        assertTrue(capping.allows(time, true))
    }

    // ---- config readers

    private val defaults = mapOf(
        "interstitial_cache_size" to "2", "is_interstitial_time_capping_enabled" to "true",
        "interstitial_cooldown_seconds" to "60", "interstitial_click_cap" to "3",
        "interstitial_ad_id" to "", "history_back_inter_enabled" to "true", "history_back_inter_should_load" to "true", "history_back_inter_counter_enabled" to "true",
        "banner_ad_id" to "", "text_banner_is_collapsing" to "false", "text_in_screen_ad_enabled" to "true", "text_ad_position" to "BOTTOM", "text_in_screen_ad_type" to "BANNER",
        "native_ad_id" to "",
    )
    private fun ids(debug: Boolean) = AdIdDefaults(
        banner = "build-banner",
        interstitial = "build-inter",
        appOpen = "build-app-open",
        native = "build-native",
        testBanner = "test-banner",
        testInterstitial = "test-inter",
        testAppOpen = "test-app-open",
        testNative = "test-native",
        debug = debug,
    )

    @Test fun `defaults are read when the source has nothing`() {
        val r = ConfigReader({ null }, defaults, ids(false))
        assertEquals(CappingConfig(2, true, 60, 3), r.capping())
        val m = r.interstitial("history_back")
        assertTrue(m.enabled && m.shouldLoad && m.counterEnabled)
        assertEquals("build-inter", m.adId)
        assertEquals(AdPosition.BOTTOM, r.banner("text").position)
    }

    @Test fun `remote values win and bad values fall back`() {
        val remote = mapOf("interstitial_cache_size" to "9", "interstitial_click_cap" to "oops", "interstitial_ad_id" to "remote-id", "text_ad_position" to "top", "banner_ad_id" to "remote-banner")
        val r = ConfigReader({ remote[it] }, defaults, ids(false))
        assertEquals(5, r.capping().cacheSize)          // clamped to 1..5
        assertEquals(3, r.capping().clickCap)           // unreadable -> default
        assertEquals("remote-id", r.interstitial("history_back").adId)
        assertEquals(AdPosition.TOP, r.banner("text").position)
        assertEquals("remote-banner", r.banner("text").adId)
    }

    @Test fun `debug builds always use the test ids`() {
        val remote = mapOf("interstitial_ad_id" to "remote-id", "banner_ad_id" to "remote-banner")
        val r = ConfigReader({ remote[it] }, defaults, ids(true))
        assertEquals("test-inter", r.interstitial("history_back").adId)
        assertEquals("test-banner", r.banner("text").adId)
    }

    @Test fun `a slot set to NATIVE is read as a native slot`() {
        val remote = mapOf("text_in_screen_ad_type" to "native", "native_ad_id" to "remote-native")
        val r = ConfigReader({ remote[it] }, defaults, ids(false))
        assertEquals(InScreenAdType.NATIVE, r.banner("text").type)
        assertEquals("remote-native", r.banner("text").nativeAdId)
    }

    @Test fun `one id per format is shared by every placement and slot`() {
        val remote = mapOf("interstitial_ad_id" to "one-inter", "banner_ad_id" to "one-banner", "native_ad_id" to "one-native")
        val r = ConfigReader({ remote[it] }, defaults, ids(false))
        assertEquals("one-inter", r.interstitial("history_back").adId)
        assertEquals("one-inter", r.interstitial("photo_open").adId)
        assertEquals("one-banner", r.banner("text").adId)
        assertEquals("one-banner", r.banner("voice").adId)
        assertEquals("one-native", r.nativeAd("history").adId)
        assertEquals("one-native", r.nativeAd("onboarding").adId)
    }

    @Test fun `native ids fall back to the build id`() {
        val r = ConfigReader({ null }, defaults, ids(false))
        assertEquals(InScreenAdType.BANNER, r.banner("text").type)
        assertEquals("build-native", r.banner("text").nativeAdId)
    }
}
