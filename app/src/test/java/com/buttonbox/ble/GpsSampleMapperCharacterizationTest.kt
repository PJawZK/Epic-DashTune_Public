package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsSampleMapperCharacterizationTest {
    private fun source(
        speed: Float,
        speedAvailable: Boolean = true,
        accuracy: Float? = 4f,
        speedAccuracy: Float? = 0.5f,
        bearing: Float? = 123f,
        altitude: Double? = 45.25,
        time: Long = 1_700_000_000_123L
    ) = GpsSampleMapper.Source(
        speedMetresPerSecond = speed,
        speedAvailable = speedAvailable,
        accuracyMetres = accuracy,
        speedAccuracyMetresPerSecond = speedAccuracy,
        bearingDegrees = bearing,
        altitudeMetres = altitude,
        fixTimeEpochMs = time
    )

    @Test fun `unavailable speed flag is ignored and platform zero remains zero`() {
        assertEquals(0f, GpsSampleMapper.map(source(0f, speedAvailable = false)).speedKilometresPerHour)
    }

    @Test fun `positive metres per second converts to kilometres per hour`() {
        val mapped = GpsSampleMapper.map(source(10f))
        assertEquals(36f, mapped.speedKilometresPerHour)
        assertEquals(1.8f, mapped.speedAccuracyKilometresPerHour)
    }

    @Test fun `preserves duplicate low speed threshold boundaries`() {
        val below = 0.049f
        val exact = GpsSampleMapper.LOCAL_ZERO_THRESHOLD_KMH
        val above = 0.051f
        assertEquals(0f, GpsSampleMapper.normalizeDashboardSpeedKmh(below))
        assertEquals(exact, GpsSampleMapper.normalizeDashboardSpeedKmh(exact))
        assertEquals(above, GpsSampleMapper.normalizeDashboardSpeedKmh(above))
        assertEquals(0f, GpsSampleMapper.normalizeDashboardSpeedKmh(
            GpsSampleMapper.map(source(below / 3.6f)).speedKilometresPerHour
        ))
    }

    @Test fun `negative speed is normalized while non finite speed survives native boundaries`() {
        assertEquals(0f, GpsSampleMapper.map(source(-1f)).speedKilometresPerHour)
        assertTrue(GpsSampleMapper.map(source(Float.NaN)).speedKilometresPerHour.isNaN())
        assertEquals(Float.POSITIVE_INFINITY, GpsSampleMapper.map(source(Float.POSITIVE_INFINITY)).speedKilometresPerHour)
        assertEquals(0f, GpsSampleMapper.map(source(Float.NEGATIVE_INFINITY)).speedKilometresPerHour)
        assertEquals(Float.NEGATIVE_INFINITY, GpsSampleMapper.normalizeDashboardSpeedKmh(Float.NEGATIVE_INFINITY))
    }

    @Test fun `missing optional fields become unavailable NaN and source time is retained`() {
        val mapped = GpsSampleMapper.map(source(1f, accuracy = null, speedAccuracy = null, bearing = null, altitude = null))
        assertTrue(mapped.accuracyMetres.isNaN())
        assertTrue(mapped.speedAccuracyKilometresPerHour.isNaN())
        assertTrue(mapped.bearingDegrees.isNaN())
        assertTrue(mapped.altitudeMetres.isNaN())
        assertEquals(1_700_000_000_123L, mapped.fixTimeEpochMs)
    }

    @Test fun `direct optional fields and altitude float narrowing are preserved`() {
        val mapped = GpsSampleMapper.map(source(1f))
        assertEquals(4f, mapped.accuracyMetres)
        assertEquals(123f, mapped.bearingDegrees)
        assertEquals(45.25f, mapped.altitudeMetres)
    }
}
