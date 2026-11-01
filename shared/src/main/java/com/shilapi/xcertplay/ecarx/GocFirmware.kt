package com.shilapi.xcertplay.ecarx

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** Exact 449 build only. Never apply offsets to an unrecognized executable. */
object GocFirmware {
    const val STOCK = "52596a2ba3e1be85dc09efd20290b2d6b36aef9e569acd1d86014bcb991fe15e"
    const val PATCH = "882afd622fe0d7a154e4cf0531d122c3fc54ba87c93cf47f90db258131502c0f"
    const val SIZE = 2804800
    const val UUID = "00000000-deca-fade-deca-deafdecacafe"
    val systemFile = File("/system/bin/gocsdk")

    fun supported(sdk: Int, device: String) = sdk == 22 && device == "xea406m"
    @JvmStatic fun mac(value: String): String {
        val compact = value.trim().replace(":", "").uppercase(Locale.US)
        require(compact.matches(Regex("[0-9A-F]{12}")) &&
            compact !in setOf("000000000000", "FFFFFFFFFFFF", "020000000000")) { "Invalid Bluetooth address" }
        return compact.chunked(2).joinToString(":")
    }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun read(file: File = systemFile): ByteArray {
        if (file.length() != SIZE.toLong()) throw IOException("Unknown GOC executable size")
        return file.inputStream().use { input ->
            val bytes = ByteArray(SIZE)
            var offset = 0
            while (offset < SIZE) {
                val count = input.read(bytes, offset, SIZE - offset)
                if (count < 0) throw IOException("Truncated GOC executable")
                offset += count
            }
            if (input.read() != -1) throw IOException("GOC executable changed while reading")
            bytes
        }
    }
    fun installed(): Boolean = runCatching { hash(read()) == PATCH }.getOrDefault(false)
    fun candidate(stock: ByteArray): ByteArray {
        require(stock.size == SIZE && hash(stock) == STOCK) { "Unknown stock GOC firmware" }
        val result = stock.copyOf()
        fun edit(offset: Int, before: String, after: String) {
            fun decode(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val old = decode(before)
            val new = decode(after)
            check(old.size == new.size && result.copyOfRange(offset, offset + old.size).contentEquals(old))
            new.copyInto(result, offset)
        }
        edit(0xbbffc, "00207047", "01207047")
        edit(0x49be8,
            "2de9f04182b080468de806001f46089e1c20cff7c5fd044600230380a0f80280031d9de8030083e803009df824302373a7734eb10123637304f110033068716803c332891a8005e004f1100024f052f800236373204602b0bde8f081",
            "70b504460d4616462c20cff7c9fd448004460f2323806560a6600223e36004f1100024f067f805a104f11a00102223f081ff01236385204670bd00bf00000000decafadedecadeafdecacafe00bf00bf00bf00bf00bf00bf00bf00bf")
        check(hash(result) == PATCH) { "Generated GOC patch hash mismatch" }
        return result
    }
}
