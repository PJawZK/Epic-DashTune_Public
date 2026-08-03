package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbFrameRateTrackerTest {
    @Test fun `known completed frame sequence reports raw ecu rate`() {
        val tracker = UsbFrameRateTracker()
        repeat(41) { tracker.recordCompletedFrame(10_000L + it * 50L) }
        assertEquals(20.0, tracker.measuredHz(), 0.0001)
        assertEquals(41L, tracker.frameCount)
    }

    @Test fun `new session excludes reconnect and paused interval`() {
        val tracker = UsbFrameRateTracker()
        repeat(21) { tracker.recordCompletedFrame(it * 50L) }
        assertEquals(20.0, tracker.measuredHz(), 0.0001)

        tracker.reset()
        repeat(11) { tracker.recordCompletedFrame(60_000L + it * 50L) }
        assertEquals(20.0, tracker.measuredHz(), 0.0001)
        assertEquals(11L, tracker.frameCount)
    }

    @Test fun `rate changes only when completed ecu frames are recorded`() {
        val tracker = UsbFrameRateTracker()
        tracker.recordCompletedFrame(1_000L)
        tracker.recordCompletedFrame(1_050L)
        val beforeBridgeCoalescing = tracker.measuredHz()

        // No recordCompletedFrame call represents arbitrary bridge coalescing/render work.
        assertEquals(beforeBridgeCoalescing, tracker.measuredHz(), 0.0)
        assertEquals(20.0, tracker.measuredHz(), 0.0001)
    }
}
