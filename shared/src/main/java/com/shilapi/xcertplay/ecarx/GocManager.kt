package com.shilapi.xcertplay.ecarx

import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ExecutionException

/** Work stays inside the gate even if the caller times out or its Activity disappears. */
class GocManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("goc449", Context.MODE_PRIVATE)
    fun enabled() = prefs.getBoolean("enabled", false)
    fun phoneName() = prefs.getString("name", "") ?: ""
    private fun requireDevice() = check(GocFirmware.supported(Build.VERSION.SDK_INT, Build.DEVICE)) { "Unsupported ECARX firmware" }

    fun setEnabled(enabled: Boolean) = GocOperationGate.maintain {
        if (enabled) {
            requireDevice()
            check(GocFirmware.installed()) { "Install the matching GOC patch first" }
            GocFirmware.mac(prefs.getString("phone", "")!!)
            GocFirmware.mac(prefs.getString("local", "")!!)
        }
        check(prefs.edit().putBoolean("enabled", enabled).commit()) { "Could not save Bluetooth route" }
    }

    fun select(device: FactoryDevices.Device, local: String) = GocOperationGate.maintain {
        requireDevice()
        val phone = GocFirmware.mac(device.address)
        val address = GocFirmware.mac(local)
        check(prefs.edit().putString("phone", phone).putString("local", address)
            .putString("name", device.name).commit()) { "Could not save phone selection" }
    }

    fun devices(): FactoryDevices.Result {
        requireDevice()
        // FactoryDevices itself is single-flight even after its Binder timeout.
        return GocOperationGate.maintain {
            check(GocFirmware.hash(GocFirmware.read()) in setOf(GocFirmware.STOCK, GocFirmware.PATCH)) { "Unknown GOC firmware" }
            FactoryDevices.read(app)
        }
    }

    /** Called from a UI worker. Timeout releases the UI, not the transaction lease. */
    fun systemAction(action: String): String {
        require(action in setOf("check", "install", "rollback"))
        requireDevice()
        val task = FutureTask<String> {
            GocOperationGate.maintain {
                val dir = File(app.filesDir, "goc449-system")
                check(dir.isDirectory || dir.mkdirs()) { "Could not create GOC work directory" }
                val script = File(dir, "manage-system-goc.sh")
                app.assets.open("goc449/manage-system-goc.sh").use { input ->
                    script.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
                }
                if (action == "install") {
                    val bytes = GocFirmware.read()
                    val candidate = if (GocFirmware.hash(bytes) == GocFirmware.PATCH) bytes else GocFirmware.candidate(bytes)
                    File(dir, "gocsdk-449-iap2-test1").outputStream().use { it.write(candidate); it.fd.sync() }
                }
                try {
                    root("/system/bin/sh ${quote(script.absolutePath)} $action")
                } finally {
                    // Stock bytes can be restored even when restarting the stock service failed.
                    if (action == "rollback" && runCatching { GocFirmware.hash(GocFirmware.read()) == GocFirmware.STOCK }.getOrDefault(false)) {
                        check(prefs.edit().putBoolean("enabled", false).commit()) { "Stock restored; could not disable GOC preference" }
                    }
                }
            }
        }
        Thread(task, "goc449-system-action").apply { isDaemon = true; start() }
        return try { task.get(180, TimeUnit.SECONDS) }
        catch (e: TimeoutException) { throw IOException("Root operation is still pending. Wait, then check status; do not start another operation.", e) }
        catch (e: ExecutionException) { throw IOException(e.cause?.message ?: "GOC operation failed", e.cause) }
    }

    private fun root(command: String): String {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = ByteArrayOutputStream()
        process.inputStream.use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() < 65536) output.write(buffer, 0, minOf(count, 65536 - output.size()))
            }
        }
        val code = process.waitFor()
        val text = output.toString("UTF-8")
        if (code != 0) throw IOException("GOC operation failed ($code): $text")
        return text
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
