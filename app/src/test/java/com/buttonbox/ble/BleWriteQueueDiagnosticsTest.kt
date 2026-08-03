package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleWriteQueueDiagnosticsTest {
    @Test fun `queue depth high water and synchronous rejection are deterministic`() {
        var now = 100L
        val trace = BleWriteQueueTrace { now }
        val queue = InstrumentedBleWriteQueue(BleWriteQueueKind.BUTTON, trace) { }

        queue.offer(byteArrayOf(1, 2))
        queue.offer(byteArrayOf(3, 4, 5))
        var counters = trace.counterValues()
        assertEquals(2L, counters.getValue("buttonQueueItems"))
        assertEquals(5L, counters.getValue("buttonQueueBytes"))
        assertEquals(2L, counters.getValue("buttonHighWaterItems"))
        assertEquals(5L, counters.getValue("buttonHighWaterBytes"))

        now = 110L
        assertEquals(2, queue.poll()!!.size)
        val rejected = trace.onSynchronousResult(BleWriteQueueKind.BUTTON, accepted = false)
        assertNotNull(rejected)
        assertEquals("sync-rejected", rejected!!.event)

        counters = trace.counterValues()
        assertEquals(1L, counters.getValue("buttonQueueItems"))
        assertEquals(3L, counters.getValue("buttonQueueBytes"))
        assertEquals(1L, counters.getValue("buttonDispatchAttempts"))
        assertEquals(1L, counters.getValue("buttonSyncRejected"))
        assertEquals(1L, counters.getValue("buttonLostItems"))
        assertEquals(2L, counters.getValue("buttonLostBytes"))
        assertEquals(0L, counters.getValue("buttonInFlight"))
    }

    @Test fun `accepted writes track overlap completion status duration and failed loss`() {
        var now = 1_000L
        val trace = BleWriteQueueTrace { now }
        val button = InstrumentedBleWriteQueue(BleWriteQueueKind.BUTTON, trace) { }
        val gps = InstrumentedBleWriteQueue(BleWriteQueueKind.GPS_DATA, trace) { }

        button.offer(byteArrayOf(1, 2))
        button.poll()
        assertNull(trace.onSynchronousResult(BleWriteQueueKind.BUTTON, accepted = true))

        now = 1_005L
        gps.offer(ByteArray(8))
        gps.poll()
        val overlap = trace.onSynchronousResult(BleWriteQueueKind.GPS_DATA, accepted = true)
        assertNotNull(overlap)
        assertEquals("cross-queue-overlap", overlap!!.event)

        now = 1_010L
        val failed = trace.onCompletion(BleWriteQueueKind.BUTTON, status = 133)
        assertNotNull(failed)
        assertEquals("completion-failed", failed!!.event)
        assertTrue(failed.details.contains("bytes=2"))
        assertTrue(failed.details.contains("durationMs=10"))
        assertNull(trace.onCompletion(BleWriteQueueKind.GPS_DATA, status = 0))

        val counters = trace.counterValues()
        assertEquals(2L, counters.getValue("maxInFlightQueues"))
        assertEquals(1L, counters.getValue("crossQueueOverlapEvents"))
        assertEquals(1L, counters.getValue("buttonCompletionFailure"))
        assertEquals(133L, counters.getValue("buttonLastCompletionStatus"))
        assertEquals(10L, counters.getValue("buttonLastInFlightDurationMs"))
        assertEquals(10L, counters.getValue("buttonMaxInFlightDurationMs"))
        assertEquals(1L, counters.getValue("buttonLostItems"))
        assertEquals(2L, counters.getValue("buttonLostBytes"))
        assertEquals(1L, counters.getValue("gpsCompletionSuccess"))
        assertEquals(5L, counters.getValue("gpsLastInFlightDurationMs"))
        assertEquals(5L, counters.getValue("gpsMaxInFlightDurationMs"))
        assertEquals(0L, counters.getValue("activeInFlightQueues"))
    }

    @Test fun `precondition loss and teardown account queued pending inflight and duration`() {
        var now = 10L
        val trace = BleWriteQueueTrace { now }
        val variable = InstrumentedBleWriteQueue(BleWriteQueueKind.VARIABLE_REQUEST, trace) { }

        variable.offer(ByteArray(4))
        variable.poll()
        val missingGatt = trace.onPreconditionDrop(BleWriteQueueKind.VARIABLE_REQUEST, "gatt")
        assertEquals("precondition-drop", missingGatt.event)

        variable.offer(ByteArray(8))
        variable.offer(ByteArray(12))
        variable.poll()
        trace.onSynchronousResult(BleWriteQueueKind.VARIABLE_REQUEST, accepted = true)

        now = 20L
        variable.clear()
        val counters = trace.counterValues()
        assertEquals(1L, counters.getValue("varReqPreconditionDrops"))
        assertEquals(1L, counters.getValue("varReqClearedQueuedItems"))
        assertEquals(12L, counters.getValue("varReqClearedQueuedBytes"))
        assertEquals(1L, counters.getValue("varReqClearedInFlightItems"))
        assertEquals(8L, counters.getValue("varReqClearedInFlightBytes"))
        assertEquals(10L, counters.getValue("varReqLastInFlightDurationMs"))
        assertEquals(10L, counters.getValue("varReqMaxInFlightDurationMs"))
        assertEquals(0L, counters.getValue("varReqQueueItems"))
        assertEquals(0L, counters.getValue("varReqInFlight"))
    }

    @Test fun `active write exposes current in flight age`() {
        var now = 50L
        val trace = BleWriteQueueTrace { now }
        val gps = InstrumentedBleWriteQueue(BleWriteQueueKind.GPS_DATA, trace) { }

        gps.offer(ByteArray(8))
        gps.poll()
        trace.onSynchronousResult(BleWriteQueueKind.GPS_DATA, accepted = true)

        now = 75L
        val counters = trace.counterValues()
        assertEquals(1L, counters.getValue("gpsInFlight"))
        assertEquals(25L, counters.getValue("gpsInFlightAgeMs"))
        assertEquals(0L, counters.getValue("gpsLastInFlightDurationMs"))
    }

    @Test fun `completion without an accepted write is reported as late`() {
        val trace = BleWriteQueueTrace { 42L }
        val signal = trace.onCompletion(BleWriteQueueKind.GPS_DATA, status = 0)
        assertNotNull(signal)
        assertEquals("completion-without-inflight", signal!!.event)
        val counters = trace.counterValues()
        assertEquals(1L, counters.getValue("gpsLateCompletions"))
        assertFalse(counters.getValue("gpsInFlight") == 1L)
    }

    @Test fun `ordinary counter publication is coalesced and delayed flush captures final state`() {
        var now = 0L
        val trace = BleWriteQueueTrace { now }
        val queue = InstrumentedBleWriteQueue(BleWriteQueueKind.BUTTON, trace) { }

        assertTrue(trace.counterValues().isNotEmpty())
        queue.offer(ByteArray(2))
        assertTrue(trace.counterValues().isEmpty())

        now = BleWriteQueueTrace.COUNTER_PUBLICATION_INTERVAL_MS
        assertTrue(trace.counterValues().isNotEmpty())

        queue.poll()
        trace.onSynchronousResult(BleWriteQueueKind.BUTTON, accepted = true)
        assertTrue(trace.counterValues().isEmpty())
        now += 125L
        trace.onCompletion(BleWriteQueueKind.BUTTON, status = 0)
        assertTrue(trace.counterValues().isEmpty())

        now += BleWriteQueueTrace.COUNTER_PUBLICATION_INTERVAL_MS
        val finalCounters = trace.counterValues()
        assertTrue(finalCounters.isNotEmpty())
        assertEquals(0L, finalCounters.getValue("buttonQueueItems"))
        assertEquals(0L, finalCounters.getValue("buttonInFlight"))
        assertEquals(125L, finalCounters.getValue("buttonLastInFlightDurationMs"))
    }
}
