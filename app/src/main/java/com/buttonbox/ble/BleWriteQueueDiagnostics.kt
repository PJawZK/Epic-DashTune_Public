package com.buttonbox.ble

import java.util.ArrayDeque

/** Traffic classes used by the three existing legacy BLE write queues. */
internal enum class BleWriteQueueKind(val key: String, val counterPrefix: String) {
    BUTTON("button", "button"),
    VARIABLE_REQUEST("variable-request", "varReq"),
    GPS_DATA("gps-data", "gps")
}

/** A bounded event marker for notable queue outcomes; ordinary traffic remains counters-only. */
internal data class BleWriteQueueSignal(
    val kind: BleWriteQueueKind,
    val event: String,
    val details: String
)

/**
 * Pure observational state for the existing BLE queues.
 *
 * This class never decides whether to enqueue, dispatch, retry, drop, coalesce, clear,
 * reconnect, or write. BleManager retains all production decisions and calls these
 * methods only after the corresponding existing action occurs.
 *
 * Full counter publication is coalesced to avoid turning high-rate observation into a
 * queue-load source. Notable failures/discards and sampled overlap force publication.
 */
internal class BleWriteQueueTrace(private val elapsedClock: () -> Long) {
    companion object {
        const val COUNTER_PUBLICATION_INTERVAL_MS = 500L
        private const val EARLY_OVERLAP_EVENTS_TO_MARK = 5L
        private const val OVERLAP_EVENT_SAMPLE_INTERVAL = 100L
    }

    private data class QueueState(
        var queuedItems: Int = 0,
        var queuedBytes: Int = 0,
        var highWaterItems: Int = 0,
        var highWaterBytes: Int = 0,
        var enqueuedItems: Long = 0L,
        var enqueuedBytes: Long = 0L,
        var dispatchAttempts: Long = 0L,
        var preconditionDrops: Long = 0L,
        var synchronousAccepted: Long = 0L,
        var synchronousRejected: Long = 0L,
        var completionSuccess: Long = 0L,
        var completionFailure: Long = 0L,
        var lateCompletions: Long = 0L,
        var lastCompletionStatus: Int? = null,
        var clears: Long = 0L,
        var clearedQueuedItems: Long = 0L,
        var clearedQueuedBytes: Long = 0L,
        var clearedPendingItems: Long = 0L,
        var clearedPendingBytes: Long = 0L,
        var clearedInFlightItems: Long = 0L,
        var clearedInFlightBytes: Long = 0L,
        var lostItems: Long = 0L,
        var lostBytes: Long = 0L,
        var pendingDispatchBytes: Int = 0,
        var inFlight: Boolean = false,
        var inFlightBytes: Int = 0,
        var inFlightStartedElapsedMs: Long = 0L,
        var lastInFlightDurationMs: Long = 0L,
        var maxInFlightDurationMs: Long = 0L
    )

    private val queues = BleWriteQueueKind.values().associateWith { QueueState() }
    private var maximumSimultaneousInFlight = 0
    private var overlapEvents = 0L
    private var lastEventElapsedMs = 0L
    private var lastCounterPublicationElapsedMs = Long.MIN_VALUE
    private var forceNextCounterPublication = true

    @Synchronized
    fun onEnqueue(kind: BleWriteQueueKind, byteCount: Int) {
        val state = queues.getValue(kind)
        val bytes = byteCount.coerceAtLeast(0)
        state.queuedItems++
        state.queuedBytes += bytes
        state.enqueuedItems++
        state.enqueuedBytes += bytes.toLong()
        if (state.queuedItems > state.highWaterItems) state.highWaterItems = state.queuedItems
        if (state.queuedBytes > state.highWaterBytes) state.highWaterBytes = state.queuedBytes
        lastEventElapsedMs = elapsedClock()
    }

    @Synchronized
    fun onPoll(kind: BleWriteQueueKind, byteCount: Int): BleWriteQueueSignal? {
        val state = queues.getValue(kind)
        val bytes = byteCount.coerceAtLeast(0)
        var signal: BleWriteQueueSignal? = null

        // A second poll can only occur after an earlier polled item returned before a
        // synchronous write result. Account for that prior item instead of hiding it.
        if (state.pendingDispatchBytes > 0) {
            val abandoned = state.pendingDispatchBytes
            state.preconditionDrops++
            state.lostItems++
            state.lostBytes += abandoned.toLong()
            forceNextCounterPublication = true
            signal = BleWriteQueueSignal(
                kind,
                "abandoned-before-write",
                "bytes=$abandoned"
            )
        }

        state.queuedItems = (state.queuedItems - 1).coerceAtLeast(0)
        state.queuedBytes = (state.queuedBytes - bytes).coerceAtLeast(0)
        state.dispatchAttempts++
        state.pendingDispatchBytes = bytes
        lastEventElapsedMs = elapsedClock()
        return signal
    }

    @Synchronized
    fun onPreconditionDrop(kind: BleWriteQueueKind, reason: String): BleWriteQueueSignal {
        val state = queues.getValue(kind)
        val bytes = state.pendingDispatchBytes.coerceAtLeast(0)
        state.preconditionDrops++
        state.lostItems++
        state.lostBytes += bytes.toLong()
        state.pendingDispatchBytes = 0
        lastEventElapsedMs = elapsedClock()
        forceNextCounterPublication = true
        return BleWriteQueueSignal(kind, "precondition-drop", "reason=$reason bytes=$bytes")
    }

    @Synchronized
    fun onSynchronousResult(kind: BleWriteQueueKind, accepted: Boolean): BleWriteQueueSignal? {
        val state = queues.getValue(kind)
        val bytes = state.pendingDispatchBytes.coerceAtLeast(0)
        state.pendingDispatchBytes = 0
        lastEventElapsedMs = elapsedClock()

        if (!accepted) {
            state.synchronousRejected++
            state.lostItems++
            state.lostBytes += bytes.toLong()
            forceNextCounterPublication = true
            return BleWriteQueueSignal(kind, "sync-rejected", "bytes=$bytes")
        }

        state.synchronousAccepted++
        state.inFlight = true
        state.inFlightBytes = bytes
        state.inFlightStartedElapsedMs = lastEventElapsedMs
        val active = activeInFlightCount()
        if (active > maximumSimultaneousInFlight) maximumSimultaneousInFlight = active
        if (active <= 1) return null

        overlapEvents++
        val shouldMark = overlapEvents <= EARLY_OVERLAP_EVENTS_TO_MARK ||
            overlapEvents % OVERLAP_EVENT_SAMPLE_INTERVAL == 0L
        return if (shouldMark) {
            forceNextCounterPublication = true
            BleWriteQueueSignal(
                kind,
                "cross-queue-overlap",
                "active=$active bytes=$bytes count=$overlapEvents"
            )
        } else null
    }

    @Synchronized
    fun onCompletion(
        kind: BleWriteQueueKind,
        status: Int,
        successStatus: Int = 0
    ): BleWriteQueueSignal? {
        val state = queues.getValue(kind)
        val now = elapsedClock()
        lastEventElapsedMs = now
        state.lastCompletionStatus = status

        if (!state.inFlight) {
            state.lateCompletions++
            forceNextCounterPublication = true
            return BleWriteQueueSignal(kind, "completion-without-inflight", "status=$status")
        }

        val completedBytes = state.inFlightBytes.coerceAtLeast(0)
        val duration = inFlightDuration(state, now)
        state.lastInFlightDurationMs = duration
        if (duration > state.maxInFlightDurationMs) state.maxInFlightDurationMs = duration
        state.inFlight = false
        state.inFlightBytes = 0
        state.inFlightStartedElapsedMs = 0L
        return if (status == successStatus) {
            state.completionSuccess++
            null
        } else {
            state.completionFailure++
            state.lostItems++
            state.lostBytes += completedBytes.toLong()
            forceNextCounterPublication = true
            BleWriteQueueSignal(
                kind,
                "completion-failed",
                "status=$status bytes=$completedBytes durationMs=$duration"
            )
        }
    }

    @Synchronized
    fun onClear(kind: BleWriteQueueKind): BleWriteQueueSignal? {
        val state = queues.getValue(kind)
        val now = elapsedClock()
        val queuedItems = state.queuedItems
        val queuedBytes = state.queuedBytes
        val pendingItems = if (state.pendingDispatchBytes > 0) 1 else 0
        val pendingBytes = state.pendingDispatchBytes
        val inFlightItems = if (state.inFlight) 1 else 0
        val inFlightBytes = state.inFlightBytes
        val inFlightDuration = if (state.inFlight) inFlightDuration(state, now) else 0L
        val discardedItems = queuedItems + pendingItems + inFlightItems
        val discardedBytes = queuedBytes + pendingBytes + inFlightBytes

        state.clears++
        state.clearedQueuedItems += queuedItems.toLong()
        state.clearedQueuedBytes += queuedBytes.toLong()
        state.clearedPendingItems += pendingItems.toLong()
        state.clearedPendingBytes += pendingBytes.toLong()
        state.clearedInFlightItems += inFlightItems.toLong()
        state.clearedInFlightBytes += inFlightBytes.toLong()
        if (state.inFlight) {
            state.lastInFlightDurationMs = inFlightDuration
            if (inFlightDuration > state.maxInFlightDurationMs) {
                state.maxInFlightDurationMs = inFlightDuration
            }
        }
        state.queuedItems = 0
        state.queuedBytes = 0
        state.pendingDispatchBytes = 0
        state.inFlight = false
        state.inFlightBytes = 0
        state.inFlightStartedElapsedMs = 0L
        lastEventElapsedMs = now
        forceNextCounterPublication = true

        return if (discardedItems > 0) {
            BleWriteQueueSignal(
                kind,
                "cleared",
                "items=$discardedItems bytes=$discardedBytes queued=$queuedItems pending=$pendingItems inFlight=$inFlightItems inFlightDurationMs=$inFlightDuration"
            )
        } else null
    }

    @Synchronized
    fun counterValues(): Map<String, Long> {
        val now = elapsedClock()
        val intervalElapsed = lastCounterPublicationElapsedMs == Long.MIN_VALUE ||
            now - lastCounterPublicationElapsedMs >= COUNTER_PUBLICATION_INTERVAL_MS
        if (!forceNextCounterPublication && !intervalElapsed) return emptyMap()

        forceNextCounterPublication = false
        lastCounterPublicationElapsedMs = now
        val values = linkedMapOf<String, Long>()
        queues.forEach { (kind, state) ->
            val prefix = kind.counterPrefix
            values["${prefix}QueueItems"] = state.queuedItems.toLong()
            values["${prefix}QueueBytes"] = state.queuedBytes.toLong()
            values["${prefix}HighWaterItems"] = state.highWaterItems.toLong()
            values["${prefix}HighWaterBytes"] = state.highWaterBytes.toLong()
            values["${prefix}EnqueuedItems"] = state.enqueuedItems
            values["${prefix}EnqueuedBytes"] = state.enqueuedBytes
            values["${prefix}DispatchAttempts"] = state.dispatchAttempts
            values["${prefix}PreconditionDrops"] = state.preconditionDrops
            values["${prefix}SyncAccepted"] = state.synchronousAccepted
            values["${prefix}SyncRejected"] = state.synchronousRejected
            values["${prefix}CompletionSuccess"] = state.completionSuccess
            values["${prefix}CompletionFailure"] = state.completionFailure
            values["${prefix}LateCompletions"] = state.lateCompletions
            values["${prefix}LastCompletionStatus"] = state.lastCompletionStatus?.toLong() ?: -1L
            values["${prefix}Clears"] = state.clears
            values["${prefix}ClearedQueuedItems"] = state.clearedQueuedItems
            values["${prefix}ClearedQueuedBytes"] = state.clearedQueuedBytes
            values["${prefix}ClearedPendingItems"] = state.clearedPendingItems
            values["${prefix}ClearedPendingBytes"] = state.clearedPendingBytes
            values["${prefix}ClearedInFlightItems"] = state.clearedInFlightItems
            values["${prefix}ClearedInFlightBytes"] = state.clearedInFlightBytes
            values["${prefix}LostItems"] = state.lostItems
            values["${prefix}LostBytes"] = state.lostBytes
            values["${prefix}PendingBytes"] = state.pendingDispatchBytes.toLong()
            values["${prefix}InFlight"] = if (state.inFlight) 1L else 0L
            values["${prefix}InFlightBytes"] = state.inFlightBytes.toLong()
            values["${prefix}InFlightStartedMs"] = state.inFlightStartedElapsedMs
            values["${prefix}InFlightAgeMs"] = if (state.inFlight) {
                inFlightDuration(state, now)
            } else 0L
            values["${prefix}LastInFlightDurationMs"] = state.lastInFlightDurationMs
            values["${prefix}MaxInFlightDurationMs"] = state.maxInFlightDurationMs
        }
        values["activeInFlightQueues"] = activeInFlightCount().toLong()
        values["maxInFlightQueues"] = maximumSimultaneousInFlight.toLong()
        values["crossQueueOverlapEvents"] = overlapEvents
        values["lastQueueEventElapsedMs"] = lastEventElapsedMs
        return values
    }

    private fun activeInFlightCount(): Int = queues.values.count { it.inFlight }

    private fun inFlightDuration(state: QueueState, now: Long): Long =
        if (state.inFlightStartedElapsedMs > 0L) {
            (now - state.inFlightStartedElapsedMs).coerceAtLeast(0L)
        } else 0L
}

/**
 * ArrayDeque-compatible subset used by BleManager. The wrapper records state only;
 * ordering and all return values are delegated unchanged to java.util.ArrayDeque.
 */
internal class InstrumentedBleWriteQueue(
    private val kind: BleWriteQueueKind,
    private val trace: BleWriteQueueTrace,
    private val onChanged: (BleWriteQueueSignal?) -> Unit
) {
    private val delegate = ArrayDeque<ByteArray>()

    fun offer(data: ByteArray): Boolean {
        val accepted = delegate.offer(data)
        if (accepted) {
            trace.onEnqueue(kind, data.size)
            onChanged(null)
        }
        return accepted
    }

    fun poll(): ByteArray? {
        val data = delegate.poll()
        if (data != null) onChanged(trace.onPoll(kind, data.size))
        return data
    }

    fun isEmpty(): Boolean = delegate.isEmpty()

    fun clear() {
        val signal = trace.onClear(kind)
        delegate.clear()
        onChanged(signal)
    }
}
