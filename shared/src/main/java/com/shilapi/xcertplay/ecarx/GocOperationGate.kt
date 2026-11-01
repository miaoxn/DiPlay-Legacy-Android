package com.shilapi.xcertplay.ecarx

import java.io.IOException

/** A session holds its lease through asynchronous teardown, not just while its socket is open. */
object GocOperationGate {
    private val sessions = mutableSetOf<Any>()
    private var maintenance = false
    @Synchronized fun beginSession(owner: Any) {
        if (maintenance) throw IOException("GOC maintenance in progress; retry after it completes")
        sessions.add(owner)
    }
    @Synchronized fun endSession(owner: Any) { sessions.remove(owner) }
    fun <T> maintain(action: () -> T): T {
        synchronized(this) {
            check(!maintenance && sessions.isEmpty()) { "Stop CarPlay before managing factory Bluetooth" }
            maintenance = true
        }
        try { return action() } finally { synchronized(this) { maintenance = false } }
    }
}
