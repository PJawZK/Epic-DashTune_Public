package com.buttonbox.ble

/** Active-stream rate of completed ECU output frames; independent of bridge delivery. */
internal class UsbFrameRateTracker {
    var frameCount: Long = 0L
        private set
    private var firstFrameElapsedMs: Long = 0L
    private var latestFrameElapsedMs: Long = 0L

    fun reset() {
        frameCount = 0L
        firstFrameElapsedMs = 0L
        latestFrameElapsedMs = 0L
    }

    fun recordCompletedFrame(elapsedMs: Long): Double {
        if (frameCount == 0L) firstFrameElapsedMs = elapsedMs
        frameCount++
        latestFrameElapsedMs = elapsedMs
        return measuredHz()
    }

    fun measuredHz(): Double {
        if (frameCount < 2L) return 0.0
        val elapsedMs = latestFrameElapsedMs - firstFrameElapsedMs
        return if (elapsedMs > 0L) (frameCount - 1L) * 1000.0 / elapsedMs.toDouble() else 0.0
    }
}
