package com.buttonbox.ble

/** Pure characterization seam for the existing Android Location -> local dashboard mapping. */
object GpsSampleMapper {
    const val METRES_PER_SECOND_TO_KILOMETRES_PER_HOUR = 3.6f
    const val LOCAL_ZERO_THRESHOLD_KMH = 0.05f

    data class Source(
        val speedMetresPerSecond: Float,
        /** Characterized for provenance; current mapping intentionally does not consult it. */
        val speedAvailable: Boolean,
        val accuracyMetres: Float?,
        val speedAccuracyMetresPerSecond: Float?,
        val bearingDegrees: Float?,
        val altitudeMetres: Double?,
        val fixTimeEpochMs: Long
    )

    data class LocalSample(
        val speedKilometresPerHour: Float,
        val accuracyMetres: Float,
        val speedAccuracyKilometresPerHour: Float,
        val bearingDegrees: Float,
        val altitudeMetres: Float,
        val fixTimeEpochMs: Long
    )

    fun map(source: Source): LocalSample {
        val rawSpeedKmh = source.speedMetresPerSecond * METRES_PER_SECOND_TO_KILOMETRES_PER_HOUR
        return LocalSample(
            speedKilometresPerHour = normalizeAcquisitionSpeedKmh(rawSpeedKmh),
            accuracyMetres = source.accuracyMetres ?: Float.NaN,
            speedAccuracyKilometresPerHour = source.speedAccuracyMetresPerSecond
                ?.times(METRES_PER_SECOND_TO_KILOMETRES_PER_HOUR) ?: Float.NaN,
            bearingDegrees = source.bearingDegrees ?: Float.NaN,
            altitudeMetres = source.altitudeMetres?.toFloat() ?: Float.NaN,
            fixTimeEpochMs = source.fixTimeEpochMs
        )
    }

    /** First legacy boundary: this also maps negative infinity to zero. */
    fun normalizeAcquisitionSpeedKmh(speedKmh: Float): Float =
        if (speedKmh < LOCAL_ZERO_THRESHOLD_KMH) 0f else speedKmh

    /** Second legacy boundary: its finite guard intentionally differs from acquisition mapping. */
    fun normalizeDashboardSpeedKmh(speedKmh: Float): Float =
        if (speedKmh.isFinite() && speedKmh < LOCAL_ZERO_THRESHOLD_KMH) 0f else speedKmh
}
