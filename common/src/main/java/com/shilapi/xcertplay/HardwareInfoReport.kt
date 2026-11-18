package com.shilapi.xcertplay

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.media.MediaCodecList
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import com.shilapi.xcertplay.compat.realSdkInt
import com.shilapi.xcertplay.media.MeasuredDecodeStats
import java.io.File
import java.util.Locale

/**
 * IKT54J suggestion G: a shareable hardware-configuration snapshot of the
 * head unit, so owners of "the same car" can compare the parts that actually
 * differ between production batches (SoC bin, RAM, decoder, display) instead
 * of guessing why one unit is smooth and another is not.
 *
 * Privacy: this is meant to be photographed and posted to group chats, so it
 * must never contain device-unique identifiers — no Wi-Fi/Bluetooth MAC, no
 * serial, no Android ID, no SSID. USB entries are VID:PID pairs only, which
 * identify a device model, not an individual unit.
 *
 * Every probe is best-effort: legacy ROMs stub out framework services, so a
 * failed probe degrades to "unknown"/"none" instead of failing the report.
 */
internal object HardwareInfoReport {

    /** The tier plus the rules that fired, so the report can say *why* the
     * unit graded this way (IKT54J §9.3.5) instead of only giving a verdict.
     * [basis] is `static-specs-only` before any video window has been
     * measured, `static+measured` once real decode numbers exist. */
    data class TierDecision(
        val tier: String,
        val reasons: List<String>,
        val basis: String,
    )

    /** Static-spec performance tier. Calibrated against the reporter's own
     * reference unit: an NTDA3 (1 GB, quad-core, 996 MHz) is his worked
     * "medium" example, so clock speed alone must not demote a quad-core
     * 1 GB unit to low — the proposal's clock rule is deliberately not
     * applied as a low trigger. Prefer [decideTier] when measured decode
     * numbers are available. */
    fun tierFor(ramTotalMb: Long, cpuCores: Int, cpuMaxMhz: Long?): String =
        decideTier(ramTotalMb, cpuCores, cpuMaxMhz, measured = null, targetFps = 0).tier

    /**
     * IKT54J §9.3.5 grading: any low rule fires the low tier (small memory,
     * few cores, fps attainment under 60%, or decode p90 over 60 ms); the
     * high tier needs the static spec *and* good measured numbers when any
     * have been recorded; anything else is medium. [targetFps] is the
     * configured frame rate the attainment is measured against; 0 or less
     * disables the attainment rules.
     */
    fun decideTier(
        ramTotalMb: Long,
        cpuCores: Int,
        cpuMaxMhz: Long?,
        measured: MeasuredDecodeStats.Snapshot?,
        targetFps: Int,
    ): TierDecision {
        val basis = if (measured == null) "static-specs-only" else "static+measured"
        val attainmentPct = if (measured != null && targetFps > 0) {
            measured.rxFpsMedian / targetFps * 100.0
        } else {
            null
        }
        val p90 = measured?.decodeP90MedianMs
        val lowReasons = ArrayList<String>()
        if (ramTotalMb <= 512L) lowReasons += "ram_total_mb=${ramTotalMb}<=512"
        if (cpuCores <= 2) lowReasons += "cpu_cores=${cpuCores}<=2"
        if (attainmentPct != null && attainmentPct < 60.0) {
            lowReasons += "fps_attainment=${attainmentPct.toInt()}%<60%"
        }
        if (p90 != null && p90 > 60) lowReasons += "decode_p90=${p90}ms>60ms"
        if (lowReasons.isNotEmpty()) return TierDecision("low", lowReasons, basis)
        val staticHigh = ramTotalMb >= 2048L && cpuCores >= 4 &&
            (cpuMaxMhz == null || cpuMaxMhz >= 1500L)
        val measuredGood = measured == null ||
            (attainmentPct != null && attainmentPct > 85.0 && p90 != null && p90 < 30)
        if (staticHigh && measuredGood) {
            val reason = if (measured == null) {
                "ram>=2048&cores>=4&clock-ok&no-measured-data"
            } else {
                "ram>=2048&cores>=4&clock-ok&fps_attainment>85%&decode_p90<30ms"
            }
            return TierDecision("high", listOf(reason), basis)
        }
        val reason = if (staticHigh && measured != null) {
            "high-blocked-by-measured:fps_attainment=${attainmentPct?.toInt()}%" +
                "/decode_p90=${p90}ms"
        } else {
            "no-low-rule;below-high"
        }
        return TierDecision("medium", listOf(reason), basis)
    }

    fun recommendFor(tier: String): String = when (tier) {
        "low" -> "legacy-rescue"
        "high" -> "quality"
        else -> "balanced"
    }

    /** Concrete preset values behind [recommendFor] (IKT54J §9.3.5 table).
     * Recommendation text only — nothing here applies settings. */
    fun presetFor(tier: String): String = when (tier) {
        "low" -> "30fps / 2.5Mbps / audio queue 20 / minimal logging"
        "high" -> "45-60fps / 4Mbps / audio queue 40"
        else -> "45fps / 3Mbps / audio queue 30"
    }

    private class Snapshot(
        val fields: List<Pair<String, String>>,
        val tier: String,
    )

    private fun collect(context: Context): Snapshot {
        val fields = ArrayList<Pair<String, String>>()
        fun add(key: String, value: String?) {
            fields.add(key to (value?.takeIf { it.isNotBlank() } ?: "unknown"))
        }
        add("manufacturer", Build.MANUFACTURER)
        add("model", Build.MODEL)
        add("board", Build.BOARD)
        add("device", Build.DEVICE)
        add("hardware", Build.HARDWARE)
        add("fingerprint", Build.FINGERPRINT)
        add("android", Build.VERSION.RELEASE)
        add("sdk", realSdkInt().toString())
        add("abi", abi())
        val cores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(0)
        add("cpu_cores", cores.toString())
        add("cpu_model", cpuModel())
        val cpuMaxMhz = cpuMaxMhz()
        add("cpu_max_mhz", cpuMaxMhz?.toString())
        var ramTotalMb = 0L
        var ramAvailMb = 0L
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                ramTotalMb = mi.totalMem / (1024L * 1024L)
                ramAvailMb = mi.availMem / (1024L * 1024L)
            }
        }
        add("ram_total_mb", if (ramTotalMb > 0) ramTotalMb.toString() else null)
        add("ram_avail_mb", if (ramTotalMb > 0) ramAvailMb.toString() else null)
        add("storage_avail_mb", storageAvailMb()?.toString())
        displayFields(context, fields)
        add("usb_host", if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) "yes" else "no")
        add("usb_devices", usbDevices(context))
        val decoder = h264Decoder()
        add("decoder", decoder)
        add("decoder_hw", decoder?.let { if (isHardwareDecoderName(it)) "yes" else "no" })
        add("root", if (suPresent()) "su-present" else "none-detected")
        val measured = runCatching { MeasuredDecodeStats.snapshot() }.getOrNull()
        val targetFps = runCatching { AirPlayPersistence.loadFps(context) }.getOrDefault(0)
        val decision = decideTier(ramTotalMb, cores, cpuMaxMhz, measured, targetFps)
        add("tier", decision.tier)
        add("tier_basis", decision.basis)
        add("tier_reasons", decision.reasons.joinToString(","))
        add("recommend", recommendFor(decision.tier))
        add("recommend_preset", presetFor(decision.tier))
        if (measured != null) {
            add("measured_windows", measured.windowCount.toString())
            add("measured_rx_fps", "%.1f".format(Locale.US, measured.rxFpsMedian))
            add("measured_decode_p90_ms", measured.decodeP90MedianMs?.toString())
            add("measured_decode_max_ms", measured.decodeMaxMs?.toString())
            add("measured_recoveries", measured.recoveriesTotal.toString())
            if (targetFps > 0) {
                val attainment = (measured.rxFpsMedian / targetFps * 100.0).toInt()
                add("measured_fps_attainment_pct", attainment.toString())
            }
        }
        return Snapshot(fields, decision.tier)
    }

    /** One compact line for the diagnostic report header. */
    fun summaryLine(context: Context): String = runCatching {
        val snapshot = collect(context)
        val map = snapshot.fields.toMap()
        "Hardware: model=${map["model"]} board=${map["board"]} " +
            "android=${map["android"]}/API ${map["sdk"]} ram=${map["ram_total_mb"]}MB " +
            "cores=${map["cpu_cores"]} decoder=${map["decoder"]} tier=${snapshot.tier}"
    }.getOrDefault("Hardware: unavailable")

    /** The full shareable card: fixed key order, one key=value per line. */
    fun fullText(context: Context): String = runCatching {
        val snapshot = collect(context)
        buildString {
            appendLine("DiPlay HWINFO v1")
            appendLine("note=hardware parameters only; no accounts, addresses or device-unique identifiers")
            for ((key, value) in snapshot.fields) {
                appendLine("$key=$value")
            }
        }.trimEnd()
    }.getOrDefault("DiPlay HWINFO v1\nnote=collection failed on this head unit")

    private fun abi(): String = runCatching {
        if (realSdkInt() >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            Build.CPU_ABI
        }
    }.getOrNull() ?: "unknown"

    private fun cpuModel(): String? = runCatching {
        val text = File("/proc/cpuinfo").readText()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Hardware") || trimmed.startsWith("model name")) {
                return@runCatching trimmed.substringAfter(":", "").trim()
                    .takeIf { it.isNotBlank() }
            }
        }
        null
    }.getOrNull()

    private fun cpuMaxMhz(): Long? = runCatching {
        val khz = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            .readText().trim().toLongOrNull() ?: return@runCatching null
        khz / 1000L
    }.getOrNull()

    private fun storageAvailMb(): Long? = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytes = if (realSdkInt() >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBytes
        } else {
            @Suppress("DEPRECATION")
            stat.availableBlocks.toLong() * stat.blockSize.toLong()
        }
        bytes / (1024L * 1024L)
    }.getOrNull()

    private fun displayFields(context: Context, fields: MutableList<Pair<String, String>>) {
        fun add(key: String, value: String?) {
            fields.add(key to (value?.takeIf { it.isNotBlank() } ?: "unknown"))
        }
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return@runCatching
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            add("display", "${metrics.widthPixels}x${metrics.heightPixels}")
            add("density_dpi", metrics.densityDpi.toString())
            @Suppress("DEPRECATION")
            val refresh = display.refreshRate
            add("refresh_hz", if (refresh % 1f == 0f) refresh.toInt().toString() else refresh.toString())
            val resources = context.resources
            val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
            add("status_bar_px", if (statusId > 0) resources.getDimensionPixelSize(statusId).toString() else null)
            val navId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            add("nav_bar_px", if (navId > 0) resources.getDimensionPixelSize(navId).toString() else null)
        }.onFailure {
            add("display", null)
        }
    }

    private fun usbDevices(context: Context): String = runCatching {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return@runCatching "unknown"
        val ids = usb.deviceList.values
            .map { "%04x:%04x".format(it.vendorId, it.productId) }
            .distinct().sorted()
        if (ids.isEmpty()) "none" else ids.joinToString(",")
    }.getOrDefault("unknown")

    private fun h264Decoder(): String? = runCatching {
        @Suppress("DEPRECATION")
        val count = MediaCodecList.getCodecCount()
        var software: String? = null
        for (i in 0 until count) {
            @Suppress("DEPRECATION")
            val info = MediaCodecList.getCodecInfoAt(i)
            if (info.isEncoder) continue
            val supportsAvc = runCatching {
                info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }
            }.getOrDefault(false)
            if (!supportsAvc) continue
            if (isHardwareDecoderName(info.name)) return@runCatching info.name
            if (software == null) software = info.name
        }
        software
    }.getOrNull()

    /** 4.x has no isHardwareAccelerated(); infer from the codec name, the same
     * heuristic the IKT54J field table prescribes (vendor OMX prefix = silicon,
     * OMX.google / c2.android / OMX.ffmpeg = software). */
    private fun isHardwareDecoderName(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.contains("google") || lower.contains("android.") ||
            lower.contains("ffmpeg") || lower.contains("sw")
        ) {
            return false
        }
        return lower.startsWith("omx.") || lower.startsWith("c2.")
    }

    private fun suPresent(): Boolean = runCatching {
        listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
        ).any { File(it).exists() }
    }.getOrDefault(false)
}
