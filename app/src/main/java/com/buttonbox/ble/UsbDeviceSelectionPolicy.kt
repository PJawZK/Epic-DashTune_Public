package com.buttonbox.ble

/**
 * Pure USB-host selection policy for the common STM32 rusEFI/EpicEFI transport.
 *
 * Firmware evidence shows that STM32 targets using the shared serial-over-USB layer expose the
 * same 0483:5740 device identity and CDC ACM shape. USB enumeration therefore identifies a safe
 * firmware transport candidate, not a specific ECU model. The actual ECU/board identity comes
 * later from the firmware-generated TunerStudio signature and must still exactly match the
 * imported INI before output decoding or tuning authority is established.
 *
 * Android descriptor objects are adapted into these immutable values by [UsbEcuManager]. Keeping
 * policy free of Android classes allows deterministic JVM coverage for unrelated hub devices,
 * multi-device selection, hot-plug preservation, detach ownership, and signature gating.
 */
internal data class UsbEndpointDescriptor(
    val direction: Int,
    val transferType: Int
)

internal data class UsbInterfaceDescriptor(
    val id: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpoints: List<UsbEndpointDescriptor>
) {
    fun hasBulkInAndOut(): Boolean {
        val hasBulkIn = endpoints.any {
            it.transferType == UsbDeviceSelectionPolicy.TRANSFER_BULK &&
                it.direction == UsbDeviceSelectionPolicy.DIRECTION_IN
        }
        val hasBulkOut = endpoints.any {
            it.transferType == UsbDeviceSelectionPolicy.TRANSFER_BULK &&
                it.direction == UsbDeviceSelectionPolicy.DIRECTION_OUT
        }
        return hasBulkIn && hasBulkOut
    }
}

internal data class UsbDeviceDescriptor(
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaces: List<UsbInterfaceDescriptor>
)

internal object UsbDeviceSelectionPolicy {
    /** Shared STM32 firmware USB serial identity from serial_over_usb/usbcfg.cpp. */
    const val STM32_FIRMWARE_VENDOR_ID: Int = 0x0483
    const val STM32_FIRMWARE_PRODUCT_ID: Int = 0x5740

    const val CLASS_COMMUNICATIONS: Int = 2
    const val CLASS_CDC_DATA: Int = 10

    const val DIRECTION_OUT: Int = 0x00
    const val DIRECTION_IN: Int = 0x80
    const val TRANSFER_BULK: Int = 2

    /**
     * Accept only the firmware's known STM32 USB identity with its CDC control/data shape.
     * The composite device may also expose a mass-storage interface; that interface alone never
     * makes a device eligible. Passing this gate does not identify a board and grants no tuning
     * authority: the runtime firmware signature and matching INI do that later.
     */
    fun isSupportedDevice(device: UsbDeviceDescriptor): Boolean {
        if (device.vendorId != STM32_FIRMWARE_VENDOR_ID || device.productId != STM32_FIRMWARE_PRODUCT_ID) {
            return false
        }
        val hasCommunicationsInterface = device.interfaces.any {
            it.interfaceClass == CLASS_COMMUNICATIONS
        }
        val hasCdcDataPipe = device.interfaces.any {
            it.interfaceClass == CLASS_CDC_DATA && it.hasBulkInAndOut()
        }
        return hasCommunicationsInterface && hasCdcDataPipe
    }

    /** Deterministic selection when more than one firmware transport candidate is present. */
    fun selectCandidate(devices: Collection<UsbDeviceDescriptor>): UsbDeviceDescriptor? =
        devices.asSequence()
            .filter(::isSupportedDevice)
            .sortedWith(
                compareBy<UsbDeviceDescriptor> { it.deviceId }
                    .thenBy { it.vendorId }
                    .thenBy { it.productId }
            )
            .firstOrNull()

    /**
     * Unrelated attachment must never advance generation or disturb a valid streaming session.
     * A second firmware transport candidate is also left alone while the current ECU session is
     * healthy.
     */
    fun shouldStartDiscoveryForAttach(
        attached: UsbDeviceDescriptor,
        currentDeviceId: Int?,
        streaming: Boolean
    ): Boolean {
        if (!isSupportedDevice(attached)) return false
        if (streaming && currentDeviceId != null) return false
        return true
    }

    fun shouldInvalidateForDetach(detachedDeviceId: Int, currentDeviceId: Int?): Boolean =
        currentDeviceId != null && detachedDeviceId == currentDeviceId

    /** Parse the board/family identity actually reported by the firmware, if recognizable. */
    fun firmwareIdentity(signature: String): EcuFirmwareIdentity? = EcuFirmwareIdentity.parse(signature)

    /** A profile with no expected signature is not sufficient authority to begin streaming. */
    fun exactSignatureMatches(expected: String, actual: String): Boolean {
        val normalizedExpected = expected.trim().trimEnd('\u0000').trim()
        val normalizedActual = actual.trim().trimEnd('\u0000').trim()
        return normalizedExpected.isNotEmpty() && normalizedActual == normalizedExpected
    }
}
