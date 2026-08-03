package com.buttonbox.ble

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Lightweight rolling timing metrics for the Performance Lab diagnostic report. */
object PerformanceMetrics {
    private const val WINDOW = 256

    private val series = ConcurrentHashMap<String, RollingPerformanceMetricSeries>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val mutationRevision = AtomicLong(0L)
    @Volatile private var activeProfile: PerformanceProfile = PerformanceProfile.FULL_OPTIMIZED
    @Volatile private var cachedSnapshotRevision: Long = -1L
    @Volatile private var cachedSnapshotJson: JSONObject = JSONObject()

    fun setProfile(profile: PerformanceProfile) {
        if (activeProfile == profile) return
        activeProfile = profile
        markDirty()
    }

    fun timingUs(name: String, elapsedNanos: Long) =
        sample(name, elapsedNanos.coerceAtLeast(0L) / 1_000L)

    fun sample(name: String, value: Long) {
        series.computeIfAbsent(name) { RollingPerformanceMetricSeries(WINDOW) }.add(value)
        markDirty()
    }

    fun increment(name: String, amount: Long = 1L) {
        counters.computeIfAbsent(name) { AtomicLong() }.addAndGet(amount)
        markDirty()
    }

    fun setCounter(name: String, value: Long) {
        val existing = counters.putIfAbsent(name, AtomicLong(value))
        if (existing == null) {
            markDirty()
        } else if (existing.getAndSet(value) != value) {
            markDirty()
        }
    }

    fun reset() {
        series.values.forEach { it.reset() }
        counters.values.forEach { it.set(0L) }
        markDirty()
    }

    internal fun unitFor(name: String): String = when (name) {
        "bridgePayloadBytes", "usbEnvelopeBytes" -> "bytes"
        "decodedChannels", "publishedChannels", "usbReadsPerEnvelope" -> "count"
        else -> "us"
    }

    private fun markDirty() {
        mutationRevision.incrementAndGet()
    }

    private fun metricSnapshotJson(
        unit: String,
        snapshot: RollingPerformanceMetricSnapshot
    ): JSONObject = JSONObject()
        .put("unit", unit)
        .put("windowSamples", snapshot.windowSamples)
        .put("totalSamples", snapshot.totalSamples)
        .put("windowAverage", snapshot.windowAverage)
        .put("lifetimeAverage", snapshot.lifetimeAverage)
        .put("p50", snapshot.p50)
        .put("p95", snapshot.p95)
        .put("maximum", snapshot.maximum)

    /**
     * Returns the immutable diagnostic snapshot for the current mutation revision.
     *
     * Callers treat the returned object as read-only. A new object is built only after a metric,
     * counter, profile, or reset mutation. If a concurrent mutation occurs during construction, the
     * mixed-time result is returned for that call but deliberately not cached as an authoritative
     * revision.
     */
    fun snapshotJson(): JSONObject {
        val requestedRevision = mutationRevision.get()
        if (cachedSnapshotRevision == requestedRevision) return cachedSnapshotJson

        synchronized(this) {
            val lockedRevision = mutationRevision.get()
            if (cachedSnapshotRevision == lockedRevision) return cachedSnapshotJson

            val timing = JSONObject()
            series.keys.sorted().forEach { key ->
                val snapshot = series[key]?.snapshot()
                timing.put(
                    key,
                    if (snapshot != null) metricSnapshotJson(unitFor(key), snapshot) else JSONObject()
                )
            }
            val countJson = JSONObject()
            counters.keys.sorted().forEach { key ->
                countJson.put(key, counters[key]?.get() ?: 0L)
            }
            val built = JSONObject()
                .put("profile", activeProfile.toJson())
                .put("timings", timing)
                .put("counters", countJson)

            if (mutationRevision.get() == lockedRevision) {
                cachedSnapshotJson = built
                cachedSnapshotRevision = lockedRevision
            }
            return built
        }
    }
}
