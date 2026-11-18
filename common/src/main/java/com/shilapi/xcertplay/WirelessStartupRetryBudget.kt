package com.shilapi.xcertplay

import com.shilapi.xcertplay.network.WirelessStartupPolicy

internal class WirelessStartupRetryBudget {
    var retries = 0
        private set
    private var frameSession: Any? = null
    private var firstFrameMillis = 0L

    fun nextDelayMillis(): Long? {
        if (retries >= WirelessStartupPolicy.MAX_STARTUP_RETRIES) return null
        return (2_000L * (1L shl retries++)).coerceAtMost(30_000L)
    }

    fun firstFrame(session: Any, nowMillis: Long): Boolean {
        if (frameSession === session) return false
        frameSession = session
        firstFrameMillis = nowMillis
        return true
    }

    fun resetIfStable(session: Any, nowMillis: Long): Boolean {
        if (frameSession !== session || nowMillis - firstFrameMillis < WirelessStartupPolicy.STABLE_SESSION_MILLIS) return false
        retries = 0
        return true
    }

    /**
     * IKTCRS: settle the stability question when the session ends. The host's
     * one-shot +60s check (resetIfStable) only resets the budget if it happens
     * to run while the same session is still active; generation churn or a
     * swapped active session makes it miss silently, and the old
     * disconnected() then threw the evidence away — a session that had run
     * stably for minutes left the budget spent, a few failed reconnects
     * latched recovery off, and only a process restart (the budget is
     * in-memory) brought it back. A session whose first frame is at least
     * STABLE_SESSION_MILLIS old has earned the budget back; shorter sessions
     * reset nothing, so connect/drop churn cannot renew it forever. Returns
     * true when the budget was reset. The user stop latch
     * (startupRetryStopped) is untouched: it is neither read nor cleared here.
     */
    fun disconnected(nowMillis: Long): Boolean {
        val stable = frameSession != null &&
            nowMillis - firstFrameMillis >= WirelessStartupPolicy.STABLE_SESSION_MILLIS
        frameSession = null
        if (stable) retries = 0
        return stable
    }

    fun manualRetry() { retries = 0; frameSession = null }
}
