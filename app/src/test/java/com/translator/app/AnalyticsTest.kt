package com.translator.app

import com.translator.app.core.analytics.Analytics
import com.translator.app.core.analytics.AnalyticsSink
import com.translator.app.core.analytics.Ev
import com.translator.app.core.analytics.EventBuilder
import com.translator.app.core.consent.ConsentSignals
import com.translator.app.core.consent.consentSignals
import com.translator.app.core.consent.tcfPurpose
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalyticsTest {
    private class FakeSink : AnalyticsSink {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        var collection: Boolean? = null
        override fun log(name: String, params: Map<String, Any>) { events += name to params }
        override fun setUserProperty(name: String, value: String?) {}
        override fun setCollectionEnabled(enabled: Boolean) { collection = enabled }
        override fun setConsent(signals: ConsentSignals) {}
    }

    private val sink = FakeSink()
    private val ON = ConsentSignals(true, false, false, false)

    @Before fun setUp() = Analytics.install(sink)
    @After fun tearDown() = Analytics.install(null)

    @Test fun `free text is rejected`() {
        val b = EventBuilder()
        b.token("screen", "hello world, this is what I typed")
        b.token("screen", "line one\nline two")
        b.token("path", "/storage/emulated/0/DCIM/photo.jpg")
        b.token("url", "https://example.com/a")
        b.token("empty", "")
        assertEquals(5, b.rejected)
        assertTrue(b.build().isEmpty())
    }

    @Test fun `over long values are rejected`() {
        val b = EventBuilder()
        b.token("a", "x".repeat(EventBuilder.MAX_VALUE_LENGTH))
        b.token("b", "x".repeat(EventBuilder.MAX_VALUE_LENGTH + 1))
        assertEquals(1, b.rejected)
        assertEquals(setOf("a"), b.build().keys)
    }

    @Test fun `bad keys and too many params are rejected`() {
        val b = EventBuilder()
        b.token("Has Space", "ok")
        b.token("1starts_with_digit", "ok")
        b.token("k".repeat(41), "ok")
        assertEquals(3, b.rejected)
        repeat(EventBuilder.MAX_PARAMS + 3) { b.number("p$it", it.toLong()) }
        assertEquals(EventBuilder.MAX_PARAMS, b.build().size)
        assertEquals(6, b.rejected)
    }

    @Test fun `accepts tokens language codes numbers and buckets`() {
        val b = EventBuilder()
            .token("screen", "voice_list").lang("from", "en").lang("to", "zh-TW")
            .flag("ai", true).bucket("chars", 120).duration("t", 4_500)
        assertEquals(0, b.rejected)
        assertEquals(mapOf<String, Any>("screen" to "voice_list", "from" to "en", "to" to "zh-TW", "ai" to 1L, "chars" to "50_199", "t" to "3_9"), b.build())
    }

    @Test fun `language codes reject words`() {
        val b = EventBuilder()
        b.lang("from", "English text")
        b.lang("from", "EN")
        assertEquals(2, b.rejected)
    }

    @Test fun `library codes are cleaned to safe tokens`() {
        val b = EventBuilder().code("error", "No Fill: ad/unit 3!")
        assertEquals(0, b.rejected)
        assertEquals("no_fill__ad_unit_3_", b.build()["error"])
        assertEquals(EventBuilder.MAX_VALUE_LENGTH, EventBuilder().code("e", "a".repeat(100)).build()["e"].toString().length)
    }

    @Test fun `buckets never expose exact values`() {
        assertEquals("0", EventBuilder.bucketOf(0, EventBuilder.DEFAULT_EDGES))
        assertEquals("0", EventBuilder.bucketOf(-3, EventBuilder.DEFAULT_EDGES))
        assertEquals("1_9", EventBuilder.bucketOf(7, EventBuilder.DEFAULT_EDGES))
        assertEquals("1000_plus", EventBuilder.bucketOf(5_000, EventBuilder.DEFAULT_EDGES))
    }

    @Test fun `nothing is sent until analytics is allowed`() {
        Analytics.log(Ev.TTS_PLAYED) { lang("lang", "en") }
        assertTrue(sink.events.isEmpty())
        Analytics.setAllowed(ON)
        assertEquals(true, sink.collection)
        Analytics.log(Ev.TTS_PLAYED) { lang("lang", "en") }
        assertEquals(1, sink.events.size)
        Analytics.setAllowed(ConsentSignals.DENIED)
        Analytics.log(Ev.TTS_PLAYED) { lang("lang", "en") }
        assertEquals(1, sink.events.size)
        assertEquals(false, sink.collection)
    }

    @Test fun `unknown event names are dropped`() {
        Analytics.setAllowed(ON)
        Analytics.log("typed_text") { token("a", "b") }
        assertTrue(sink.events.isEmpty())
    }

    @Test fun `a rejected value never reaches the sink`() {
        Analytics.setAllowed(ON)
        Analytics.log(Ev.RESULT_COPIED) { token("source", "my secret sentence") }
        assertEquals(1, sink.events.size)
        assertTrue(sink.events[0].second.isEmpty())
    }

    @Test fun `screen is logged once per change`() {
        Analytics.setAllowed(ON)
        Analytics.screen("text"); Analytics.screen("text"); Analytics.screen("voice")
        assertEquals(2, sink.events.size)
    }

    @Test fun `no sink installed is a safe no-op`() {
        Analytics.install(null)
        Analytics.setAllowed(ON)
        Analytics.log(Ev.SCREEN_ENTER) { token("screen", "text") }
        Analytics.setUserProperty("user_cohort", "new")
    }

    private val all = "1111111111"

    @Test fun `tcf purposes are read by position and unreadable means denied`() {
        assertTrue(tcfPurpose("101", 1)); assertFalse(tcfPurpose("101", 2)); assertTrue(tcfPurpose("101", 3))
        assertFalse(tcfPurpose("101", 4)); assertFalse(tcfPurpose(null, 1)); assertFalse(tcfPurpose("", 1)); assertFalse(tcfPurpose("1", 0))
    }

    @Test fun `with consent rules the real purposes decide, not canRequestAds`() {
        // canRequestAds true but no purpose answers: everything stays denied.
        assertEquals(ConsentSignals.DENIED, consentSignals(true, true, -1, null))
        assertEquals(ConsentSignals.DENIED, consentSignals(true, true, -1, "0000000000"))
        // Only purpose 1 (basic ads): ad_storage yes, analytics no, ad_user_data no.
        val basic = consentSignals(true, true, -1, "1000000000")
        assertTrue(basic.adStorage); assertFalse(basic.analyticsStorage); assertFalse(basic.adUserData); assertFalse(basic.adPersonalization)
        // Everything granted.
        assertEquals(ConsentSignals(true, true, true, true), consentSignals(true, true, -1, all))
        // Purposes 1,7: ad_user_data without personalization.
        val measure = consentSignals(true, true, -1, "1000001000")
        assertTrue(measure.adUserData); assertFalse(measure.adPersonalization)
        // Analytics needs 1,8,9,10.
        assertTrue(consentSignals(true, true, -1, "1000000111").analyticsStorage)
        assertFalse(consentSignals(true, true, -1, "1000000110").analyticsStorage)
    }

    @Test fun `switching analytics off wins in regulated regions and the rest stays`() {
        val s = consentSignals(true, true, 0, all)
        assertFalse(s.analyticsStorage)
        assertTrue(s.adStorage)
    }

    @Test fun `outside consent rules analytics needs the first run choice`() {
        assertFalse(consentSignals(false, true, -1, null).analyticsStorage)
        assertTrue(consentSignals(false, false, 1, null).analyticsStorage)
        assertFalse(consentSignals(false, true, 0, null).analyticsStorage)
        assertTrue(consentSignals(false, true, -1, null).adStorage)
        assertFalse(consentSignals(false, false, -1, null).adStorage)
    }
}
