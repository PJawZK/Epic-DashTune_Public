package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDeviceSelectionPolicyTest {
    @Test
    fun massStorageWithBulkEndpointsIsRejectedBeforePermissionOrProbe() {
        val storage = device(
            deviceId = 12,
            vendorId = 0x14CD,
            productId = 0x1212,
            interfaces = listOf(massStorageInterface())
        )

        assertFalse(UsbDeviceSelectionPolicy.isSupportedDevice(storage))
        assertNull(UsbDeviceSelectionPolicy.selectCandidate(listOf(storage)))
    }

    @Test
    fun supportedMegaCompositeRequiresExactIdentityAndCdcShape() {
        val mega = megaDevice(deviceId = 13)

        assertTrue(UsbDeviceSelectionPolicy.isSupportedDevice(mega))
        assertEquals(13, UsbDeviceSelectionPolicy.selectCandidate(listOf(mega))?.deviceId)
    }

    @Test
    fun matchingIdentityWithoutCdcControlOrDataIsRejected() {
        val missingControl = device(
            deviceId = 3,
            vendorId = 0x0483,
            productId = 0x5740,
            interfaces = listOf(cdcDataInterface())
        )
        val missingData = device(
            deviceId = 4,
            vendorId = 0x0483,
            productId = 0x5740,
            interfaces = listOf(communicationsInterface(), massStorageInterface())
        )

        assertFalse(UsbDeviceSelectionPolicy.isSupportedDevice(missingControl))
        assertFalse(UsbDeviceSelectionPolicy.isSupportedDevice(missingData))
    }

    @Test
    fun genericCdcSerialDeviceIsRejectedDespiteCompatibleInterfaceShape() {
        val genericSerial = device(
            deviceId = 8,
            vendorId = 0x1234,
            productId = 0x5678,
            interfaces = listOf(communicationsInterface(), cdcDataInterface())
        )

        assertFalse(UsbDeviceSelectionPolicy.isSupportedDevice(genericSerial))
    }

    @Test
    fun deterministicSelectionIgnoresUnrelatedDevicesAndUsesLowestSupportedDeviceId() {
        val unrelated = device(
            deviceId = 1,
            vendorId = 0x14CD,
            productId = 0x1212,
            interfaces = listOf(massStorageInterface())
        )
        val laterMega = megaDevice(deviceId = 27)
        val earlierMega = megaDevice(deviceId = 19)

        val selected = UsbDeviceSelectionPolicy.selectCandidate(
            listOf(laterMega, unrelated, earlierMega)
        )

        assertEquals(19, selected?.deviceId)
    }

    @Test
    fun unrelatedAttachNeverStartsDiscovery() {
        val storage = device(
            deviceId = 12,
            vendorId = 0x14CD,
            productId = 0x1212,
            interfaces = listOf(massStorageInterface())
        )

        assertFalse(
            UsbDeviceSelectionPolicy.shouldStartDiscoveryForAttach(
                attached = storage,
                currentDeviceId = null,
                streaming = false
            )
        )
    }

    @Test
    fun supportedAttachStartsDiscoveryOnlyWhenNoHealthySessionOwnsUsb() {
        val mega = megaDevice(deviceId = 13)

        assertTrue(
            UsbDeviceSelectionPolicy.shouldStartDiscoveryForAttach(
                attached = mega,
                currentDeviceId = null,
                streaming = false
            )
        )
        assertFalse(
            UsbDeviceSelectionPolicy.shouldStartDiscoveryForAttach(
                attached = mega,
                currentDeviceId = 9,
                streaming = true
            )
        )
    }

    @Test
    fun onlyCurrentEcuDetachInvalidatesTheSession() {
        assertFalse(UsbDeviceSelectionPolicy.shouldInvalidateForDetach(12, 13))
        assertFalse(UsbDeviceSelectionPolicy.shouldInvalidateForDetach(13, null))
        assertTrue(UsbDeviceSelectionPolicy.shouldInvalidateForDetach(13, 13))
    }

    @Test
    fun streamingRequiresExactNonBlankIniSignature() {
        val expected = "rusEFI master.2026.05.24.MEGA144H7.3684405155"

        assertTrue(UsbDeviceSelectionPolicy.exactSignatureMatches(expected, "  $expected\u0000 ".trimEnd('\u0000', ' ')))
        assertFalse(UsbDeviceSelectionPolicy.exactSignatureMatches(expected, "rusEFI master.other"))
        assertFalse(UsbDeviceSelectionPolicy.exactSignatureMatches("", expected))
        assertFalse(UsbDeviceSelectionPolicy.exactSignatureMatches("   ", expected))
    }

    private fun megaDevice(deviceId: Int): UsbDeviceDescriptor = device(
        deviceId = deviceId,
        vendorId = 0x0483,
        productId = 0x5740,
        deviceClass = 239,
        deviceSubclass = 2,
        deviceProtocol = 1,
        interfaces = listOf(
            massStorageInterface(id = 0),
            communicationsInterface(id = 1),
            cdcDataInterface(id = 2)
        )
    )

    private fun device(
        deviceId: Int,
        vendorId: Int,
        productId: Int,
        deviceClass: Int = 0,
        deviceSubclass: Int = 0,
        deviceProtocol: Int = 0,
        interfaces: List<UsbInterfaceDescriptor>
    ): UsbDeviceDescriptor = UsbDeviceDescriptor(
        deviceId = deviceId,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        interfaces = interfaces
    )

    private fun communicationsInterface(id: Int = 1): UsbInterfaceDescriptor = UsbInterfaceDescriptor(
        id = id,
        interfaceClass = UsbDeviceSelectionPolicy.CLASS_COMMUNICATIONS,
        interfaceSubclass = 2,
        interfaceProtocol = 1,
        endpoints = emptyList()
    )

    private fun cdcDataInterface(id: Int = 2): UsbInterfaceDescriptor = UsbInterfaceDescriptor(
        id = id,
        interfaceClass = UsbDeviceSelectionPolicy.CLASS_CDC_DATA,
        interfaceSubclass = 0,
        interfaceProtocol = 0,
        endpoints = bulkInOut()
    )

    private fun massStorageInterface(id: Int = 0): UsbInterfaceDescriptor = UsbInterfaceDescriptor(
        id = id,
        interfaceClass = 8,
        interfaceSubclass = 6,
        interfaceProtocol = 80,
        endpoints = bulkInOut()
    )

    private fun bulkInOut(): List<UsbEndpointDescriptor> = listOf(
        UsbEndpointDescriptor(
            direction = UsbDeviceSelectionPolicy.DIRECTION_IN,
            transferType = UsbDeviceSelectionPolicy.TRANSFER_BULK
        ),
        UsbEndpointDescriptor(
            direction = UsbDeviceSelectionPolicy.DIRECTION_OUT,
            transferType = UsbDeviceSelectionPolicy.TRANSFER_BULK
        )
    )
}
