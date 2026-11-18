package com.shilapi.xcertplay.airplay

/** Which connection chain a stage event belongs to. */
enum class ChainKind(val totalSteps: Int) {
    WIRED(16),
    WIRELESS(12),
}

/**
 * One milestone of the wired (16-step) or wireless (12-step) connection
 * chain, emitted at the same point that writes the `WIRED-CHAIN [n/16]` /
 * `WIRELESS-CHAIN [n/12]` log line, so the step numbers on screen and in
 * the log can never drift apart.
 *
 * Semantics are arrival-based: every line is a "this step is done"
 * milestone, so the last arrived step is the current one and every earlier
 * step counts as passed. [arrivedAtMillis] is
 * [android.os.SystemClock.elapsedRealtime] at emission, matching the
 * monotonic clock the connection panel already uses for its wait timer.
 * [rawLine] is the exact log line, kept for later consumers (a failure
 * report snapshot) that need the original wording.
 */
data class ChainStageEvent(
    val kind: ChainKind,
    val step: Int,
    val arrivedAtMillis: Long,
    val rawLine: String,
) {
    val totalSteps: Int get() = kind.totalSteps
}
