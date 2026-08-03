package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class PerformanceMetricsTest {
    @Test
    fun byteSeriesUseByteUnits() {
        assertEquals("bytes", PerformanceMetrics.unitFor("bridgePayloadBytes"))
        assertEquals("bytes", PerformanceMetrics.unitFor("usbEnvelopeBytes"))
    }

    @Test
    fun countSeriesUseCountUnits() {
        assertEquals("count", PerformanceMetrics.unitFor("decodedChannels"))
        assertEquals("count", PerformanceMetrics.unitFor("publishedChannels"))
        assertEquals("count", PerformanceMetrics.unitFor("usbReadsPerEnvelope"))
    }

    @Test
    fun timingSeriesRemainMicroseconds() {
        listOf(
            "usbRead",
            "usbCallback",
            "dataHubPublish",
            "bridgeJsonBuild",
            "bridgeSubmit",
            "bridgeRoundTrip"
        ).forEach { name ->
            assertEquals("Unexpected unit for $name", "us", PerformanceMetrics.unitFor(name))
        }
    }

    @Test
    fun unknownSeriesDefaultToMicroseconds() {
        assertEquals("us", PerformanceMetrics.unitFor("futureTimingMetric"))
    }

    @Test
    fun snapshotPreservesRollingAndLifetimeCalculations() {
        PerformanceMetrics.reset()
        (1L..257L).forEach { value ->
            PerformanceMetrics.sample("usbEnvelopeBytes", value)
        }

        val snapshot = PerformanceMetrics.snapshotJson()
            .getJSONObject("timings")
            .getJSONObject("usbEnvelopeBytes")

        assertEquals("bytes", snapshot.getString("unit"))
        assertEquals(256, snapshot.getInt("windowSamples"))
        assertEquals(257L, snapshot.getLong("totalSamples"))
        assertEquals(129.5, snapshot.getDouble("windowAverage"), 0.0)
        assertEquals(129.0, snapshot.getDouble("lifetimeAverage"), 0.0)
        assertEquals(129L, snapshot.getLong("p50"))
        assertEquals(245L, snapshot.getLong("p95"))
        assertEquals(257L, snapshot.getLong("maximum"))
    }

    @Test
    fun completeSnapshotIsReusedUntilMetricsMutate() {
        PerformanceMetrics.reset()

        val empty = PerformanceMetrics.snapshotJson()
        assertSame(empty, PerformanceMetrics.snapshotJson())

        PerformanceMetrics.sample("snapshotCacheProbe", 7L)
        val populated = PerformanceMetrics.snapshotJson()

        assertNotSame(empty, populated)
        assertEquals(
            1,
            populated
                .getJSONObject("timings")
                .getJSONObject("snapshotCacheProbe")
                .getInt("windowSamples")
        )
        assertSame(populated, PerformanceMetrics.snapshotJson())

        PerformanceMetrics.reset()
        val reset = PerformanceMetrics.snapshotJson()
        assertNotSame(populated, reset)
        assertEquals(
            0,
            reset
                .getJSONObject("timings")
                .getJSONObject("snapshotCacheProbe")
                .getInt("windowSamples")
        )
    }

    @Test
    fun unchangedCounterDoesNotInvalidateSnapshot() {
        PerformanceMetrics.reset()
        PerformanceMetrics.setCounter("unchangedCounterProbe", 12L)
        val first = PerformanceMetrics.snapshotJson()

        PerformanceMetrics.setCounter("unchangedCounterProbe", 12L)
        assertSame(first, PerformanceMetrics.snapshotJson())

        PerformanceMetrics.setCounter("unchangedCounterProbe", 13L)
        val changed = PerformanceMetrics.snapshotJson()
        assertNotSame(first, changed)
        assertEquals(13L, changed.getJSONObject("counters").getLong("unchangedCounterProbe"))
    }

    @Test
    fun newZeroCounterAppearsInSnapshot() {
        PerformanceMetrics.reset()
        val before = PerformanceMetrics.snapshotJson()

        PerformanceMetrics.setCounter("newZeroCounterProbe", 0L)
        val after = PerformanceMetrics.snapshotJson()

        assertNotSame(before, after)
        assertEquals(0L, after.getJSONObject("counters").getLong("newZeroCounterProbe"))
    }
}
