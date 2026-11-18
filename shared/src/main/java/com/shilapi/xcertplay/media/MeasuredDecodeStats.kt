package com.shilapi.xcertplay.media

import java.util.ArrayDeque

/** One 5-second video window's numbers, for the process-lifetime measured
 * aggregate behind the hardware report (IKT54J §9.3.4). Decode percentiles
 * are null when no frame was decoded in the window. Public because
 * [MeasuredDecodeStats] exposes it across modules. */
data class VideoWindowStats(
    val rxFps: Double,
    val shownFps: Double,
    val decodeP50Ms: Int?,
    val decodeP90Ms: Int?,
    val decodeMaxMs: Int?,
    val recoveries: Int,
)

/**
 * Process-lifetime aggregate of main-screen video windows: the measured
 * half of the IKT54J §9.3.4 decode-capability table (received fps median,
 * decode p90 median, decode max, recovery count). Only the main screen
 * feeds it — secondary streams would pollute the decode picture.
 *
 * The rings keep the most recent 120 windows (10 minutes of 5-second
 * windows), so the numbers track recent behavior rather than the whole
 * process history. Nothing is persisted: before the first video window
 * the snapshot is null and the hardware report falls back to static
 * specs alone. Public because the hardware report in the common module
 * reads it (Kotlin `internal` is module-scoped).
 */
object MeasuredDecodeStats {

    data class Snapshot(
        val windowCount: Int,
        val rxFpsMedian: Double,
        val decodeP90MedianMs: Int?,
        val decodeMaxMs: Int?,
        val recoveriesTotal: Int,
    )

    private const val MAX_WINDOWS = 120

    private val rxSamples = ArrayDeque<Double>()
    private val p90Samples = ArrayDeque<Int>()
    private var maxDecodeMs: Int? = null
    private var recoveries = 0
    private var windows = 0

    @Synchronized
    fun record(window: VideoWindowStats) {
        windows++
        recoveries += window.recoveries
        rxSamples.addLast(window.rxFps)
        while (rxSamples.size > MAX_WINDOWS) rxSamples.removeFirst()
        window.decodeP90Ms?.let { p90 ->
            p90Samples.addLast(p90)
            while (p90Samples.size > MAX_WINDOWS) p90Samples.removeFirst()
        }
        window.decodeMaxMs?.let { max ->
            val previous = maxDecodeMs
            maxDecodeMs = if (previous == null) max else maxOf(previous, max)
        }
    }

    @Synchronized
    fun snapshot(): Snapshot? {
        if (windows == 0) return null
        return Snapshot(
            windowCount = windows,
            rxFpsMedian = median(rxSamples),
            decodeP90MedianMs = medianInt(p90Samples),
            decodeMaxMs = maxDecodeMs,
            recoveriesTotal = recoveries,
        )
    }

    @Synchronized
    fun reset() {
        rxSamples.clear()
        p90Samples.clear()
        maxDecodeMs = null
        recoveries = 0
        windows = 0
    }

    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun medianInt(values: Collection<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
