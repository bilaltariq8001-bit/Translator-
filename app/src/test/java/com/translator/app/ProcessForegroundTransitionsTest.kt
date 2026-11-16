package com.translator.app

import com.translator.app.core.ads.ProcessForegroundTransitions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessForegroundTransitionsTest {
    @Test fun `cold process start is not mistaken for a background return`() {
        val transitions = ProcessForegroundTransitions()

        assertFalse(transitions.onProcessStarted())
    }

    @Test fun `one background stop produces exactly one foreground return`() {
        val transitions = ProcessForegroundTransitions()
        assertFalse(transitions.onProcessStarted())

        transitions.onProcessStopped()
        assertTrue(transitions.onProcessStarted())
        assertFalse(transitions.onProcessStarted())
    }

    @Test fun `activity recreation without a process stop is not a foreground return`() {
        val transitions = ProcessForegroundTransitions()
        assertFalse(transitions.onProcessStarted())

        // An Activity can stop and restart during recreation while ProcessLifecycleOwner keeps the app started.
        assertFalse(transitions.onProcessStarted())
    }
}
