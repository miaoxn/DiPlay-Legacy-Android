package com.shilapi.xcertplay.ecarx

import org.junit.Assert.*
import org.junit.Test

class GocFirmwareTest {
    @Test fun rejectsSimilarDevicesAndUnknownFirmware() {
        assertTrue(GocFirmware.supported(22, "xea406m"))
        assertFalse(GocFirmware.supported(23, "xea406m"))
        assertFalse(GocFirmware.supported(22, "E01"))
        assertThrows(IllegalArgumentException::class.java) { GocFirmware.candidate(ByteArray(GocFirmware.SIZE)) }
        assertThrows(IllegalArgumentException::class.java) { GocFirmware.candidate(byteArrayOf(1)) }
    }
    @Test fun rejectsPlaceholderAndMalformedAddresses() {
        assertEquals("AA:BB:CC:DD:EE:FF", GocFirmware.mac("aabbccddeeff"))
        for (bad in listOf("", "00:00:00:00:00:00", "02:00:00:00:00:00", "FFFFFFFFFFFF", "AA:BB:CC:DD:EE:GG", "AA;reboot")) {
            assertThrows(IllegalArgumentException::class.java) { GocFirmware.mac(bad) }
        }
    }
    @Test fun maintenanceCannotOverlapSessionOrOtherMaintenance() {
        val owner = Any()
        GocOperationGate.beginSession(owner)
        try { assertThrows(IllegalStateException::class.java) { GocOperationGate.maintain { fail("must not execute") } } }
        finally { GocOperationGate.endSession(owner) }
        GocOperationGate.maintain {
            assertThrows(java.io.IOException::class.java) { GocOperationGate.beginSession(owner) }
            assertThrows(IllegalStateException::class.java) { GocOperationGate.maintain {} }
        }
        assertThrows(IllegalArgumentException::class.java) { GocOperationGate.maintain { throw IllegalArgumentException("test") } }
        // A failed operation must release its lease.
        GocOperationGate.beginSession(owner)
        GocOperationGate.endSession(owner)
    }
}
