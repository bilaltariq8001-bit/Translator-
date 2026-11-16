package com.translator.app

import com.translator.app.core.i18n.LocaleFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleFormatterTest {

    @Test
    fun testNumberFormatting() {
        val formattedUs = LocaleFormatter.formatNumber(1234567.89, Locale.US)
        assertEquals("1,234,567.89", formattedUs)
    }

    @Test
    fun testTurkishCasing() {
        val text = "istanbul"
        val turkishUpper = LocaleFormatter.safeToUpperCase(text, Locale("tr", "TR"))
        assertEquals("İSTANBUL", turkishUpper)
    }

    @Test
    fun testBidiIsolation() {
        val isolated = LocaleFormatter.isolateBidi("hello", isRtl = true)
        assertEquals("\u200Fhello\u200F", isolated)
    }
}
