package com.buttonbox.ble

/** Pure seam that preserves independent exception isolation for GPS listener fan-out. */
object GpsListenerDispatcher {
    data class Result(val attempted: Int, val delivered: Int, val failed: Int)

    fun <T> dispatch(listeners: Iterable<T>, delivery: (T) -> Unit): Result {
        var attempted = 0
        var delivered = 0
        var failed = 0
        listeners.forEach { listener ->
            attempted++
            try {
                delivery(listener)
                delivered++
            } catch (_: Exception) {
                failed++
            }
        }
        return Result(attempted, delivered, failed)
    }
}
