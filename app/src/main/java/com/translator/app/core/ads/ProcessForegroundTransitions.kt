package com.translator.app.core.ads

/** Identifies process-level foreground entries, independently of Activity recreation and navigation. */
class ProcessForegroundTransitions {
    private var hasStarted = false
    private var wasBackgrounded = false

    /** Returns true only after an observed stop followed by a later process start. */
    fun onProcessStarted(): Boolean {
        if (!hasStarted) {
            hasStarted = true
            return false
        }
        val isReturn = wasBackgrounded
        wasBackgrounded = false
        return isReturn
    }

    fun onProcessStopped() {
        if (hasStarted) wasBackgrounded = true
    }
}
