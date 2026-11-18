package com.shilapi.xcertplay.network

import java.io.IOException
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class UsbSharedNetworkTest {
    @Test fun selectsUsbEthernetAddressAndIgnoresWifiLoopbackAndVirtualInterfaces() {
        val usb = ip("192.168.0.101")
        val result = selectUsbSharedAddress(listOf(
            iface("lo", isLoopback = true, addresses = listOf(ip("127.0.0.1"))),
            iface("wlan0", addresses = listOf(ip("192.168.0.102"))),
            iface("p2p0", addresses = listOf(ip("192.168.49.1"))),
            iface("dummy0", addresses = listOf(ip("192.168.1.1"))),
            iface("eth0", addresses = listOf(ip("fe80::1"), usb)),
        ))
        assertEquals(usb, result)
    }

    @Test fun acceptsUsbRndisAndSecondEthernetInterfaceNames() {
        assertEquals(
            ip("192.168.42.10"),
            selectUsbSharedAddress(listOf(iface("usb0", addresses = listOf(ip("192.168.42.10"))))),
        )
        assertEquals(
            ip("192.168.43.10"),
            selectUsbSharedAddress(listOf(iface("rndis0", addresses = listOf(ip("192.168.43.10"))))),
        )
        assertEquals(
            ip("10.0.0.5"),
            selectUsbSharedAddress(listOf(iface("eth1", addresses = listOf(ip("10.0.0.5"))))),
        )
    }

    @Test fun ignoresDownInterfacesAndUnusableAddresses() {
        assertNull(selectUsbSharedAddress(listOf(
            iface("eth0", isUp = false, addresses = listOf(ip("192.168.0.101"))))))
        assertNull(selectUsbSharedAddress(listOf(iface("eth0", addresses = listOf(ip("169.254.1.2"))))))
        assertNull(selectUsbSharedAddress(listOf(iface("eth0", addresses = listOf(ip("127.0.0.1"))))))
        assertNull(selectUsbSharedAddress(listOf(iface("eth0", addresses = listOf(ip("224.0.0.251"))))))
        assertNull(selectUsbSharedAddress(listOf(iface("eth0", addresses = listOf(ip("fe80::1"))))))
        assertNull(selectUsbSharedAddress(listOf(iface("eth0", addresses = emptyList()))))
        assertNull(selectUsbSharedAddress(emptyList()))
    }

    @Test fun refusesAmbiguousUsbSharedNetworks() {
        try {
            selectUsbSharedAddress(listOf(
                iface("eth0", addresses = listOf(ip("192.168.0.101"))),
                iface("usb0", addresses = listOf(ip("192.168.42.10"))),
            ))
            fail("Expected IOException for two usable USB-shared networks")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("Multiple"))
        }
    }

    @Test fun secondInterfaceWithoutUsableAddressDoesNotCreateAmbiguity() {
        assertEquals(ip("192.168.0.101"), selectUsbSharedAddress(listOf(
            iface("eth0", addresses = listOf(ip("192.168.0.101"))),
            iface("usb0", addresses = listOf(ip("fe80::1"))),
        )))
    }

    @Test fun interfaceNameRule() {
        assertTrue(isUsbSharedInterfaceName("eth0"))
        assertTrue(isUsbSharedInterfaceName("eth1"))
        assertTrue(isUsbSharedInterfaceName("usb0"))
        assertTrue(isUsbSharedInterfaceName("rndis0"))
        assertFalse(isUsbSharedInterfaceName("wlan0"))
        assertFalse(isUsbSharedInterfaceName("p2p0"))
        assertFalse(isUsbSharedInterfaceName("lo"))
        assertFalse(isUsbSharedInterfaceName("ap0"))
        assertFalse(isUsbSharedInterfaceName("dummy0"))
        assertFalse(isUsbSharedInterfaceName("tun0"))
    }

    private fun iface(
        name: String,
        isUp: Boolean = true,
        isLoopback: Boolean = false,
        addresses: List<InetAddress>,
    ) = UsbSharedInterface(name, isUp, isLoopback, addresses)

    private fun ip(value: String) = InetAddress.getByName(value)
}
