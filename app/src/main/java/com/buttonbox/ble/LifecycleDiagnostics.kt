package com.buttonbox.ble

import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.LinkedHashMap

/**
 * Bounded process-local ownership and lifecycle instrumentation.
 *
 * This registry is observational only. It never starts, stops, reconnects, selects,
 * invalidates, or otherwise controls an Activity, manager, transport, WebView, GPS,
 * MSL source, or ECU session.
 */
internal class LifecycleTrace(
    private val processGeneration: String,
    private val wallClock: () -> Long,
    private val elapsedClock: () -> Long,
    private val maxOwners: Int = 32,
    private val maxEvents: Int = 160
) {
    private data class Owner(
        val id: String,
        val generation: Long,
        val kind: String,
        val label: String,
        val parentId: String,
        val createdWallTimeMs: Long,
        val createdElapsedMs: Long,
        var active: Boolean,
        var state: String,
        var lastEventWallTimeMs: Long,
        var lastEventElapsedMs: Long,
        var destroyedWallTimeMs: Long? = null,
        var destroyedElapsedMs: Long? = null,
        val counters: LinkedHashMap<String, Long> = linkedMapOf()
    )

    private data class Event(
        val sequence: Long,
        val ownerId: String,
        val ownerKind: String,
        val ownerLabel: String,
        val event: String,
        val details: String,
        val wallTimeMs: Long,
        val elapsedMs: Long
    )

    private val owners = LinkedHashMap<String, Owner>()
    private val events = ArrayDeque<Event>()
    private var ownerGeneration = 0L
    private var eventSequence = 0L

    @Synchronized
    fun register(kind: String, label: String, parentId: String = ""): String {
        val safeKind = sanitize(kind, 20).ifBlank { "owner" }
        val safeLabel = sanitize(label, 40).ifBlank { "unnamed" }
        val generation = ++ownerGeneration
        val id = "$safeKind-$safeLabel-$generation"
        val wall = wallClock()
        val elapsed = elapsedClock()
        val owner = Owner(
            id = id,
            generation = generation,
            kind = safeKind,
            label = safeLabel,
            parentId = parentId.take(96),
            createdWallTimeMs = wall,
            createdElapsedMs = elapsed,
            active = true,
            state = "created",
            lastEventWallTimeMs = wall,
            lastEventElapsedMs = elapsed
        )
        owners[id] = owner
        appendEvent(owner, "created", "")
        trimOwners()
        return id
    }

    @Synchronized
    fun mark(ownerId: String, event: String, details: String = "") {
        val owner = owners[ownerId] ?: return
        val wall = wallClock()
        val elapsed = elapsedClock()
        owner.lastEventWallTimeMs = wall
        owner.lastEventElapsedMs = elapsed

        if (!owner.active) {
            owner.counters["lateEvents"] = (owner.counters["lateEvents"] ?: 0L) + 1L
            appendEvent(owner, "late-${event.take(43)}", details)
            return
        }

        val boundedEvent = event.take(48)
        if (boundedEvent.startsWith("state-") && owner.state == boundedEvent) {
            owner.counters["repeatedStateEvents"] = (owner.counters["repeatedStateEvents"] ?: 0L) + 1L
            return
        }

        owner.state = boundedEvent
        appendEvent(owner, boundedEvent, details)
    }

    @Synchronized
    fun close(ownerId: String, event: String = "destroyed", details: String = "") {
        val owner = owners[ownerId] ?: return
        val wall = wallClock()
        val elapsed = elapsedClock()
        owner.active = false
        owner.state = event.take(48)
        owner.lastEventWallTimeMs = wall
        owner.lastEventElapsedMs = elapsed
        owner.destroyedWallTimeMs = wall
        owner.destroyedElapsedMs = elapsed
        appendEvent(owner, event, details)
        trimOwners()
    }

    @Synchronized
    fun increment(ownerId: String, counter: String, delta: Long = 1L) {
        val owner = owners[ownerId] ?: return
        val key = sanitize(counter, 40).ifBlank { return }
        owner.counters[key] = (owner.counters[key] ?: 0L) + delta
        owner.lastEventWallTimeMs = wallClock()
        owner.lastEventElapsedMs = elapsedClock()
    }

    @Synchronized
    fun setCounter(ownerId: String, counter: String, value: Long) {
        val owner = owners[ownerId] ?: return
        val key = sanitize(counter, 40).ifBlank { return }
        owner.counters[key] = value
        owner.lastEventWallTimeMs = wallClock()
        owner.lastEventElapsedMs = elapsedClock()
    }

    @Synchronized
    fun snapshotJson(): JSONObject {
        val ownerArray = JSONArray()
        owners.values.forEach { owner ->
            ownerArray.put(
                JSONObject()
                    .put("id", owner.id)
                    .put("generation", owner.generation)
                    .put("kind", owner.kind)
                    .put("label", owner.label)
                    .put("parentId", owner.parentId)
                    .put("active", owner.active)
                    .put("state", owner.state)
                    .put("createdWallTimeMs", owner.createdWallTimeMs)
                    .put("createdElapsedMs", owner.createdElapsedMs)
                    .put("lastEventWallTimeMs", owner.lastEventWallTimeMs)
                    .put("lastEventElapsedMs", owner.lastEventElapsedMs)
                    .put("destroyedWallTimeMs", owner.destroyedWallTimeMs ?: JSONObject.NULL)
                    .put("destroyedElapsedMs", owner.destroyedElapsedMs ?: JSONObject.NULL)
                    .put("counters", JSONObject(owner.counters as Map<*, *>))
            )
        }

        val eventArray = JSONArray()
        events.forEach { event ->
            eventArray.put(
                JSONObject()
                    .put("sequence", event.sequence)
                    .put("ownerId", event.ownerId)
                    .put("ownerKind", event.ownerKind)
                    .put("ownerLabel", event.ownerLabel)
                    .put("event", event.event)
                    .put("details", event.details)
                    .put("wallTimeMs", event.wallTimeMs)
                    .put("elapsedMs", event.elapsedMs)
            )
        }

        return JSONObject()
            .put("schema", 1)
            .put("processGeneration", processGeneration)
            .put("capturedWallTimeMs", wallClock())
            .put("capturedElapsedMs", elapsedClock())
            .put("ownerLimit", maxOwners)
            .put("eventLimit", maxEvents)
            .put("owners", ownerArray)
            .put("events", eventArray)
    }

    private fun appendEvent(owner: Owner, event: String, details: String) {
        events.addLast(
            Event(
                sequence = ++eventSequence,
                ownerId = owner.id,
                ownerKind = owner.kind,
                ownerLabel = owner.label,
                event = event.take(48),
                details = details.take(240),
                wallTimeMs = owner.lastEventWallTimeMs,
                elapsedMs = owner.lastEventElapsedMs
            )
        )
        while (events.size > maxEvents.coerceAtLeast(1)) events.removeFirst()
    }

    private fun trimOwners() {
        val limit = maxOwners.coerceAtLeast(1)
        while (owners.size > limit) {
            val removable = owners.entries.firstOrNull { !it.value.active }
                ?: owners.entries.firstOrNull()
                ?: return
            owners.remove(removable.key)
        }
    }

    private fun sanitize(value: String, maximum: Int): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9_.-]+"), "-")
        .trim('-')
        .take(maximum)
}

internal object LifecycleDiagnostics {
    private val processId = Process.myPid()
    private val processStartedWallTimeMs = System.currentTimeMillis()
    private val processStartedElapsedMs = SystemClock.elapsedRealtime()
    private val trace = LifecycleTrace(
        processGeneration = "p$processId-$processStartedWallTimeMs",
        wallClock = System::currentTimeMillis,
        elapsedClock = SystemClock::elapsedRealtime
    )

    fun registerActivity(label: String): String = trace.register("activity", label)

    fun registerManager(label: String, parentId: String = ""): String =
        trace.register("manager", label, parentId)

    fun mark(ownerId: String, event: String, details: String = "") =
        trace.mark(ownerId, event, details)

    fun close(ownerId: String, event: String = "destroyed", details: String = "") =
        trace.close(ownerId, event, details)

    fun increment(ownerId: String, counter: String, delta: Long = 1L) =
        trace.increment(ownerId, counter, delta)

    fun setCounter(ownerId: String, counter: String, value: Long) =
        trace.setCounter(ownerId, counter, value)

    fun snapshotJson(): JSONObject = trace.snapshotJson()
        .put("pid", processId)
        .put("processStartedWallTimeMs", processStartedWallTimeMs)
        .put("processStartedElapsedMs", processStartedElapsedMs)
}
