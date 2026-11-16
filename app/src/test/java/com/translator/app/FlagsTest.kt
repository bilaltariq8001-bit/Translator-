package com.translator.app

import com.translator.app.core.model.Flags
import com.translator.app.core.model.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {
    private val globe = "🌐"

    @Test fun everyLanguageHasExactlyOneFlagOrTheGlobe() {
        Languages.all.forEach { lang ->
            val flag = Flags.of(lang.code)
            // One flag is two regional-indicator characters (4 UTF-16 units); the globe is one emoji.
            assertTrue("${lang.code}: $flag", flag == globe || (flag.codePointCount(0, flag.length) == 2 && flag.codePoints().allMatch { it in 0x1F1E6..0x1F1FF }))
        }
    }

    @Test fun knownLanguages() {
        assertEquals("🇪🇸", Flags.of("es"))   // Spain
        assertEquals("🇵🇰", Flags.of("ur"))   // Pakistan
        assertEquals("🇯🇵", Flags.of("ja"))   // Japan
    }

    @Test fun languageWithoutAHomeShowsTheGlobe() = assertEquals(globe, Flags.of("eo"))
}
