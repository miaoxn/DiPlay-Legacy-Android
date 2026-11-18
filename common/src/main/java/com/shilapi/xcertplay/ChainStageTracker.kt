package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.ChainKind
import com.shilapi.xcertplay.airplay.ChainStageEvent

/**
 * Pure state machine behind the connection self-check lights (phase 1 of
 * the IKTCRS feedback-path design). No Android dependencies, so the rules
 * below are unit-testable without Robolectric.
 *
 * Arrival semantics: the last arrived step is the current one, earlier
 * steps count as passed. Steps within one attempt are *not* strictly
 * ordered in wall time — the wired listener-bind step (13) is emitted by
 * the VPN attach, which the controller awaits before it logs step 11 — so
 * a lower step arriving after a higher one must NOT reset the attempt.
 * Only two things start a new attempt: a kind change, or step 1 arriving
 * after a later step has already arrived (step 1 fires once per attempt).
 * A failure freezes the lights until the next attempt restarts at step 1.
 */
internal class ChainStageTracker {

    enum class Dot { PENDING, PASSED, CURRENT, FAILED }

    data class Snapshot(
        val kind: ChainKind?,
        val totalSteps: Int,
        val dots: List<Dot>,
        val currentStep: Int,
        val currentStepArrivedAt: Long,
        val failedStep: Int,
        val attemptStartedAt: Long,
    )

    private var kind: ChainKind? = null

    /** First-arrival moment per step, 1-based; 0 means not arrived yet. */
    private val arrivals = LongArray(MAX_STEPS + 1)
    private var maxStep = 0
    private var attemptStartedAt = 0L
    private var failedStep = 0

    val isActive: Boolean get() = kind != null && maxStep > 0
    val hasFailed: Boolean get() = failedStep > 0

    fun onEvent(event: ChainStageEvent) {
        val total = event.kind.totalSteps
        if (event.step < 1 || event.step > total) return
        if (failedStep > 0 && event.step != 1) return
        val kindChanged = kind != null && kind != event.kind
        val restarted = event.step == 1 && maxStep > 1
        if (kindChanged || restarted) reset()
        if (kind == null) {
            kind = event.kind
            attemptStartedAt = event.arrivedAtMillis
        }
        if (arrivals[event.step] == 0L) arrivals[event.step] = event.arrivedAtMillis
        if (event.step > maxStep) maxStep = event.step
    }

    /** Freeze the current step as failed; a no-op when no attempt is active. */
    fun markFailed() {
        if (maxStep > 0 && failedStep == 0) failedStep = maxStep
    }

    fun reset() {
        kind = null
        arrivals.fill(0L)
        maxStep = 0
        attemptStartedAt = 0L
        failedStep = 0
    }

    fun snapshot(): Snapshot {
        val currentKind = kind
        if (currentKind == null || maxStep == 0) {
            return Snapshot(null, 0, emptyList(), 0, 0L, 0, 0L)
        }
        val total = currentKind.totalSteps
        val failed = failedStep
        val dots = (1..total).map { step ->
            when {
                failed > 0 && step == failed -> Dot.FAILED
                failed > 0 && step < failed -> Dot.PASSED
                failed > 0 -> Dot.PENDING
                step < maxStep -> Dot.PASSED
                step == maxStep -> Dot.CURRENT
                else -> Dot.PENDING
            }
        }
        val current = if (failed > 0) failed else maxStep
        return Snapshot(
            kind = currentKind,
            totalSteps = total,
            dots = dots,
            currentStep = current,
            currentStepArrivedAt = arrivals[current],
            failedStep = failed,
            attemptStartedAt = attemptStartedAt,
        )
    }

    private companion object {
        const val MAX_STEPS = 16
    }
}
