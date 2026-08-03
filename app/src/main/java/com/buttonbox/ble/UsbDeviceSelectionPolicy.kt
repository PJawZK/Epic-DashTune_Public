package com.buttonbox.ble

/**
 * Pure USB-host selection policy for the supported Mega144H7 transport.
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
    const val MEGA144H7_VENDOR_ID: Int = 0x0483
    const val MEGA144H7_PRODUCT_ID: Int = 0x5740

    const val CLASS_COMMUNICATIONS: Int = 2
    const val CLASS_CDC_DATA: Int = 10

    const val DIRECTION_OUT: Int = 0x00
    const val DIRECTION_IN: Int = 0x80
    const val TRANSFER_BULK: Int = 2

    /**
     * Accept only the physically validated Mega144H7 USB identity with its CDC control/data shape.
     * The composite device may also expose a mass-storage interface; that interface alone never
     * makes a device eligible.
     */
    fun isSupportedDevice(device: UsbDeviceDescriptor): Boolean {
        if (device.vendorId != MEGA144H7_VENDOR_ID || device.productId != MEGA144H7_PRODUCT_ID) {
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

    /** Deterministic selection when more than one supported ECU is present. */
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
     * A second supported device is also left alone while the current ECU session is healthy.
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

    /** A profile with no expected signature is not sufficient authority to begin streaming. */
    fun exactSignatureMatches(expected: String, actual: String): Boolean {
        val normalizedExpected = expected.trim()
        val normalizedActual = actual.trim()
        return normalizedExpected.isNotEmpty() && normalizedActual == normalizedExpected
    }
}
