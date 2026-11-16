package com.translator.app

import com.translator.app.keyboard.KeyboardLayouts
import com.translator.app.keyboard.Shift
import com.translator.app.keyboard.ShiftState
import com.translator.app.keyboard.TypingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLogicTest {
    @Test
    fun buffer_typesAndDeletes() {
        val b = TypingBuffer()
        assertTrue(b.isEmpty)
        b.append("he"); b.append("llo")
        assertEquals("hello", b.text)
        assertTrue(b.backspace())
        assertEquals("hell", b.text)
        b.clear()
        assertFalse(b.backspace())
    }

    @Test
    fun buffer_backspaceRemovesWholeEmoji() {
        val b = TypingBuffer()
        b.append("a😀")
        b.backspace()
        assertEquals("a", b.text)
    }

    @Test
    fun buffer_blankIsDetected() {
        val b = TypingBuffer()
        b.append("  ")
        assertTrue(b.isBlank)
        assertFalse(b.isEmpty)
        b.replaceWith("hi")
        assertEquals("hi", b.text)
    }

    @Test
    fun shift_onceThenUsedUp() {
        val s = ShiftState()
        s.tap(0)
        assertEquals("A", s.apply("a"))
        s.afterLetter()
        assertEquals(Shift.Off, s.state)
        assertEquals("a", s.apply("a"))
    }

    @Test
    fun shift_doubleTapLocksAndThirdTapReleases() {
        val s = ShiftState()
        s.tap(0); s.tap(200)
        assertEquals(Shift.Lock, s.state)
        s.afterLetter()
        assertEquals("B", s.apply("b"))
        s.tap(5_000)
        assertEquals(Shift.Off, s.state)
    }

    @Test
    fun shift_slowSecondTapTurnsItOff() {
        val s = ShiftState()
        s.tap(0); s.tap(2_000)
        assertEquals(Shift.Off, s.state)
    }

    @Test
    fun layouts_urduForUrduAndLatinForTheRest() {
        assertEquals("urdu", KeyboardLayouts.forLanguage("ur").id)
        assertEquals("latin", KeyboardLayouts.forLanguage("es").id)
        assertTrue(KeyboardLayouts.urdu.rtl)
        assertEquals(3, KeyboardLayouts.latin.letters.size)
        assertEquals(10, KeyboardLayouts.latin.letters[0].size)
        assertTrue(KeyboardLayouts.urdu.letters.flatten().all { it.isNotEmpty() })
        assertEquals(KeyboardLayouts.urdu.letters.flatten().size, KeyboardLayouts.urdu.letters.flatten().toSet().size)
    }
}
