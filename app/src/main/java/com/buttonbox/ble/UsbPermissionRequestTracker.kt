package com.buttonbox.ble

/** Bounded, generation-owned state for the single Android USB permission request in flight. */
internal class UsbPermissionRequestTracker {
    data class Request(val deviceId: Int, val generation: Long)

    private var pending: Request? = null

    @Synchronized
    fun replace(deviceId: Int, generation: Long): Request =
        Request(deviceId, generation).also { pending = it }

    @Synchronized
    fun consume(deviceId: Int, generation: Long): Request? {
        val request = pending
        if (request?.deviceId != deviceId || request.generation != generation) return null
        pending = null
        return request
    }

    @Synchronized
    fun clearOwnedBy(generation: Long): Boolean {
        if (pending?.generation != generation) return false
        pending = null
        return true
    }

    @Synchronized
    fun snapshot(): Request? = pending
}

/** Coordinates short permission publication with the connection-generation authority lock. */
internal class UsbPermissionRequestCoordinator(
    private val authority: UsbGenerationAuthority,
    private val tracker: UsbPermissionRequestTracker
) {
    fun publishIfCurrent(deviceId: Int, generation: Long, requestPermission: () -> Unit): Boolean =
        authority.runIfCurrent(generation) {
            tracker.replace(deviceId, generation)
            requestPermission()
        }

    fun advanceGeneration(): Long = authority.next { invalidatedGeneration ->
        tracker.clearOwnedBy(invalidatedGeneration)
    }
}
