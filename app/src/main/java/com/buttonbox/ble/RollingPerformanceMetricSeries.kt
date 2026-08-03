package com.buttonbox.ble

import kotlin.math.ceil

/**
 * Thread-safe rolling metric accumulator used by [PerformanceMetrics].
 *
 * The rolling window owns percentile and window-average scope. Lifetime count, average and maximum
 * intentionally remain independent from the rolling window so diagnostic semantics do not change
 * when old samples rotate out.
 */
internal class RollingPerformanceMetricSeries(
    private val windowSize: Int
) {
    init {
        require(windowSize > 0) { "windowSize must be positive" }
    }

    private val values = LongArray(windowSize)
    private var cursor = 0
    private var size = 0
    private var totalCount = 0L
    private var totalValue = 0L
    private var maximum = 0L
    private var cachedSnapshot: RollingPerformanceMetricSnapshot? = null

    @Synchronized
    fun add(value: Long) {
        val safe = value.coerceAtLeast(0L)
        values[cursor] = safe
        cursor = (cursor + 1) % windowSize
        if (size < windowSize) size++
        totalCount++
        totalValue += safe
        if (safe > maximum) maximum = safe
        cachedSnapshot = null
    }

    @Synchronized
    fun snapshot(): RollingPerformanceMetricSnapshot {
        cachedSnapshot?.let { return it }

        val sortedWindow = LongArray(size)
        for (index in 0 until size) {
            val sourceIndex = (cursor - size + index + windowSize) % windowSize
            sortedWindow[index] = values[sourceIndex]
        }
        sortedWindow.sort()

        return RollingPerformanceMetricSnapshot(
            windowSamples = sortedWindow.size,
            totalSamples = totalCount,
            windowAverage = if (sortedWindow.isNotEmpty()) sortedWindow.average() else 0.0,
            lifetimeAverage = if (totalCount > 0L) totalValue.toDouble() / totalCount else 0.0,
            p50 = percentile(sortedWindow, 0.50),
            p95 = percentile(sortedWindow, 0.95),
            maximum = maximum
        ).also { cachedSnapshot = it }
    }

    @Synchronized
    fun reset() {
        cursor = 0
        size = 0
        totalCount = 0L
        totalValue = 0L
        maximum = 0L
        cachedSnapshot = null
        values.fill(0L)
    }

    private fun percentile(sortedValues: LongArray, percent: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = (ceil(percent * sortedValues.size).toInt() - 1)
            .coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }
}

internal data class RollingPerformanceMetricSnapshot(
    val windowSamples: Int,
    val totalSamples: Long,
    val windowAverage: Double,
    val lifetimeAverage: Double,
    val p50: Long,
    val p95: Long,
    val maximum: Long
)
