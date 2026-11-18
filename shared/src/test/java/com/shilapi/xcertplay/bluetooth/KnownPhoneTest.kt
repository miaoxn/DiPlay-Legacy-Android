package com.shilapi.xcertplay.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KnownPhoneTest {

    @Test
    fun testSerializationAndDeserialization() {
        val phones = listOf(
            KnownPhone(
                address = "AA:BB:CC:DD:EE:FF",
                name = "Alice's iPhone",
                alias = "Alice Car",
                lastConnectedTime = 1700000000000L
            ),
            KnownPhone(
                address = "11:22:33:44:55:66",
                name = "Bob's iPhone",
                alias = null,
                lastConnectedTime = 1690000000000L
            )
        )

        val json = KnownPhone.serializeList(phones)
        val deserialized = KnownPhone.parseList(json)

        assertEquals(2, deserialized.size)
        assertEquals("AA:BB:CC:DD:EE:FF", deserialized[0].address)
        assertEquals("Alice's iPhone", deserialized[0].name)
        assertEquals("Alice Car", deserialized[0].alias)
        assertEquals("Alice Car", deserialized[0].displayName())
        assertEquals(1700000000000L, deserialized[0].lastConnectedTime)

        assertEquals("11:22:33:44:55:66", deserialized[1].address)
        assertEquals("Bob's iPhone", deserialized[1].name)
        assertEquals(null, deserialized[1].alias)
        assertEquals("Bob's iPhone", deserialized[1].displayName())
        assertEquals(1690000000000L, deserialized[1].lastConnectedTime)
    }

    @Test
    fun testMalformedJsonReturnsEmptyList() {
        assertTrue(KnownPhone.parseList("").isEmpty())
        assertTrue(KnownPhone.parseList("invalid json content").isEmpty())
        assertTrue(KnownPhone.parseList("{ \"not\": \"an array\" }").isEmpty())
    }
}
