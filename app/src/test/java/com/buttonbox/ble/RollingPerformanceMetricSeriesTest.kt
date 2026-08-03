package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RollingPerformanceMetricSeriesTest {
    @Test
    fun emptySnapshotUsesExistingZeroSemantics() {
        val snapshot = RollingPerformanceMetricSeries(windowSize = 4).snapshot()

        assertEquals(0, snapshot.windowSamples)
        assertEquals(0L, snapshot.totalSamples)
        assertEquals(0.0, snapshot.windowAverage, 0.0)
        assertEquals(0.0, snapshot.lifetimeAverage, 0.0)
        assertEquals(0L, snapshot.p50)
        assertEquals(0L, snapshot.p95)
        assertEquals(0L, snapshot.maximum)
    }

    @Test
    fun rollingWindowAndLifetimeScopesRemainIndependent() {
        val series = RollingPerformanceMetricSeries(windowSize = 4)
        listOf(1L, 2L, 3L, 4L, 100L).forEach(series::add)

        val snapshot = series.snapshot()

        assertEquals(4, snapshot.windowSamples)
        assertEquals(5L, snapshot.totalSamples)
        assertEquals(27.25, snapshot.windowAverage, 0.0)
        assertEquals(22.0, snapshot.lifetimeAverage, 0.0)
        assertEquals(3L, snapshot.p50)
        assertEquals(100L, snapshot.p95)
        assertEquals(100L, snapshot.maximum)
    }

    @Test
    fun negativeSamplesRetainExistingZeroFloor() {
        val series = RollingPerformanceMetricSeries(windowSize = 3)
        series.add(-10L)
        series.add(5L)

        val snapshot = series.snapshot()

        assertEquals(2, snapshot.windowSamples)
        assertEquals(2L, snapshot.totalSamples)
        assertEquals(2.5, snapshot.windowAverage, 0.0)
        assertEquals(2.5, snapshot.lifetimeAverage, 0.0)
        assertEquals(0L, snapshot.p50)
        assertEquals(5L, snapshot.p95)
        assertEquals(5L, snapshot.maximum)
    }

    @Test
    fun unchangedSeriesReusesImmutableSnapshot() {
        val series = RollingPerformanceMetricSeries(windowSize = 4)
        series.add(10L)

        val first = series.snapshot()
        val second = series.snapshot()

        assertSame(first, second)
    }

    @Test
    fun newSampleInvalidatesCachedSnapshot() {
        val series = RollingPerformanceMetricSeries(windowSize = 4)
        series.add(10L)
        val first = series.snapshot()

        series.add(20L)
        val second = series.snapshot()

        assertNotSame(first, second)
        assertEquals(2L, second.totalSamples)
        assertEquals(15.0, second.windowAverage, 0.0)
    }

    @Test
    fun resetClearsRollingLifetimeAndCachedState() {
        val series = RollingPerformanceMetricSeries(windowSize = 2)
        series.add(100L)
        series.add(1L)
        series.add(2L)

        val beforeReset = series.snapshot()
        assertEquals(100L, beforeReset.maximum)

        series.reset()
        val afterReset = series.snapshot()

        assertNotSame(beforeReset, afterReset)
        assertEquals(0, afterReset.windowSamples)
        assertEquals(0L, afterReset.totalSamples)
        assertEquals(0L, afterReset.maximum)
    }
}
