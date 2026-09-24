package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuFirmwareIdentityTest {
    @Test
    fun mega144H7IdentityComesFromFirmwareSignatureNotUsbIdentity() {
        val signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

        val identity = UsbDeviceSelectionPolicy.firmwareIdentity(signature)

        requireNotNull(identity)
        assertEquals("rusEFI", identity.firmwareFamily)
        assertEquals("MEGA144H7", identity.shortBoardName)
        assertEquals("2273317132", identity.signatureHash)
        assertEquals("MEGA144H7", identity.displayName)
        assertEquals(signature, identity.signature)
    }

    @Test
    fun otherFirmwareBoardsAreRecognizedWithoutAHardcodedBoardWhitelist() {
        val identities = listOf(
            "rusEFI master.2026.09.21.proteus_f7.1234567890" to "proteus_f7",
            "rusEFI master.2026.09.21.uaefi_pro_h7.987654321" to "uaefi_pro_h7",
            "EpicEFI master.2026.09.21.MEGA144F7.42" to "MEGA144F7"
        )

        identities.forEach { (signature, expectedBoard) ->
            val identity = UsbDeviceSelectionPolicy.firmwareIdentity(signature)
            requireNotNull(identity)
            assertEquals(expectedBoard, identity.shortBoardName)
            assertEquals(expectedBoard, identity.displayName)
        }
    }

    @Test
    fun familyCanStillBeRecognizedWhenGeneratedBoardSuffixIsUnavailable() {
        val identity = UsbDeviceSelectionPolicy.firmwareIdentity("rusEFI custom development build")

        requireNotNull(identity)
        assertEquals("rusEFI", identity.firmwareFamily)
        assertNull(identity.shortBoardName)
        assertNull(identity.signatureHash)
        assertEquals("rusEFI", identity.displayName)
    }

    @Test
    fun unrelatedOrIncidentalPrintableTextIsNotFirmwareIdentity() {
        assertNull(UsbDeviceSelectionPolicy.firmwareIdentity("Generic USB Serial Device"))
        assertNull(UsbDeviceSelectionPolicy.firmwareIdentity("Compatible with rusEFI protocol"))
    }

    @Test
    fun exactIniAuthorityRemainsFullSignatureEqualityNotBoardNameEquality() {
        val expected = "rusEFI master.2026.09.21.proteus_f7.1234567890"
        val differentLayout = "rusEFI master.2026.09.21.proteus_f7.1234567891"

        assertTrue(UsbDeviceSelectionPolicy.exactSignatureMatches(expected, "  $expected\u0000 "))
        assertFalse(UsbDeviceSelectionPolicy.exactSignatureMatches(expected, differentLayout))
    }
}
