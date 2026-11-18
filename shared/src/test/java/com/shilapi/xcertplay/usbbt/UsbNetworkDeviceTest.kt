package com.shilapi.xcertplay.usbbt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbNetworkDeviceTest {
    @Test fun rndisDeviceLevelTripleMatches() {
        assertTrue(isUsbNetworkAdapter(sig(vendorId = 0x0B95, deviceClass = 0xEF, deviceSubclass = 0x04, deviceProtocol = 0x01)))
    }

    @Test fun cdcEcmNcmEemInterfaceClassesMatch() {
        assertTrue(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0x02, 0x06, 0x00)))))
        assertTrue(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0x02, 0x0D, 0x00)))))
        assertTrue(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0x02, 0x0C, 0x00)))))
    }

    @Test fun cdcNetworkingTripleAtDeviceLevelMatches() {
        assertTrue(isUsbNetworkAdapter(sig(deviceClass = 0x02, deviceSubclass = 0x06, deviceProtocol = 0x00)))
    }

    @Test fun appleDeviceNeverMatchesEvenWithNcmInterface() {
        assertFalse(isUsbNetworkAdapter(sig(vendorId = 0x05AC, ifaces = listOf(iface(0x02, 0x0D, 0x00)))))
    }

    @Test fun bluetoothClassCodeDoesNotMatchTheNetworkMatcher() {
        assertFalse(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0xE0, 0x01, 0x01)))))
    }

    @Test fun massStorageHubPrinterAndCdcDataOnlyDoNotMatch() {
        assertFalse(isUsbNetworkAdapter(sig(deviceClass = 0x08, deviceSubclass = 0x06, deviceProtocol = 0x50)))
        assertFalse(isUsbNetworkAdapter(sig(deviceClass = 0x09, deviceSubclass = 0x00, deviceProtocol = 0x00)))
        assertFalse(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0x07, 0x01, 0x02)))))
        assertFalse(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0x0A, 0x00, 0x00)))))
    }

    @Test fun cdcAcmModemDoesNotMatch() {
        assertFalse(isUsbNetworkAdapter(sig(ifaces = listOf(iface(0x02, 0x02, 0x01)))))
    }

    @Test fun emptyDescriptorDoesNotMatch() {
        assertFalse(isUsbNetworkAdapter(sig()))
    }

    private fun sig(
        vendorId: Int = 0x0B95,
        deviceClass: Int = 0x00,
        deviceSubclass: Int = 0x00,
        deviceProtocol: Int = 0x00,
        ifaces: List<UsbInterfaceSignature> = emptyList(),
    ) = UsbDeviceSignature(
        vendorId = vendorId,
        productId = 0x1790,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        interfaces = ifaces,
    )

    private fun iface(cls: Int, sub: Int, protocol: Int) = UsbInterfaceSignature(cls, sub, protocol)
}
