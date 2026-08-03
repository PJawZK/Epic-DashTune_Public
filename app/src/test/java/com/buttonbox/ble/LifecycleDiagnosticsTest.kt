package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleDiagnosticsTest {
    @Test fun `owner ids generations counters and timestamps are deterministic`() {
        var wall = 1_000L
        var elapsed = 100L
        val trace = LifecycleTrace(
            processGeneration = "process-test",
            wallClock = { wall },
            elapsedClock = { elapsed },
            maxOwners = 8,
            maxEvents = 16
        )

        val activity = trace.register("activity", "Dashboard Lab")
        wall += 5
        elapsed += 5
        val manager = trace.register("manager", "USB LAB", activity)
        wall += 5
        elapsed += 5
        trace.mark(activity, "resumed", "foreground")
        trace.increment(manager, "frames", 3)
        trace.setCounter(manager, "generation", 7)
        wall += 5
        elapsed += 5
        trace.close(manager, "shutdown", "onDestroy")

        val snapshot = trace.snapshotJson()
        assertEquals(1, snapshot.getInt("schema"))
        assertEquals("process-test", snapshot.getString("processGeneration"))
        assertEquals(2, snapshot.getJSONArray("owners").length())

        val activityJson = snapshot.getJSONArray("owners").getJSONObject(0)
        assertEquals("activity-Dashboard-Lab-1", activityJson.getString("id"))
        assertEquals(1L, activityJson.getLong("generation"))
        assertTrue(activityJson.getBoolean("active"))
        assertEquals("resumed", activityJson.getString("state"))

        val managerJson = snapshot.getJSONArray("owners").getJSONObject(1)
        assertEquals("manager-USB-LAB-2", managerJson.getString("id"))
        assertEquals(activity, managerJson.getString("parentId"))
        assertFalse(managerJson.getBoolean("active"))
        assertEquals("shutdown", managerJson.getString("state"))
        assertEquals(3L, managerJson.getJSONObject("counters").getLong("frames"))
        assertEquals(7L, managerJson.getJSONObject("counters").getLong("generation"))
        assertEquals(115L, managerJson.getLong("destroyedElapsedMs"))

        val events = snapshot.getJSONArray("events")
        assertEquals(4, events.length())
        assertEquals("created", events.getJSONObject(0).getString("event"))
        assertEquals("created", events.getJSONObject(1).getString("event"))
        assertEquals("resumed", events.getJSONObject(2).getString("event"))
        assertEquals("shutdown", events.getJSONObject(3).getString("event"))
        assertEquals(4L, events.getJSONObject(3).getLong("sequence"))
    }

    @Test fun `event and owner retention remain bounded`() {
        var clock = 0L
        val trace = LifecycleTrace(
            processGeneration = "bounded",
            wallClock = { ++clock },
            elapsedClock = { clock },
            maxOwners = 2,
            maxEvents = 3
        )

        val first = trace.register("activity", "first")
        trace.close(first)
        val second = trace.register("activity", "second")
        val third = trace.register("manager", "third", second)
        trace.mark(second, "stopped")
        trace.mark(third, "streaming")

        val snapshot = trace.snapshotJson()
        val owners = snapshot.getJSONArray("owners")
        val events = snapshot.getJSONArray("events")

        assertEquals(2, owners.length())
        assertEquals("activity-second-2", owners.getJSONObject(0).getString("id"))
        assertEquals("manager-third-3", owners.getJSONObject(1).getString("id"))
        assertEquals(3, events.length())
        assertEquals("created", events.getJSONObject(0).getString("event"))
        assertEquals("stopped", events.getJSONObject(1).getString("event"))
        assertEquals("streaming", events.getJSONObject(2).getString("event"))
    }

    @Test fun `repeated transport states coalesce and late callbacks preserve shutdown`() {
        var clock = 10L
        val trace = LifecycleTrace(
            processGeneration = "coalesced",
            wallClock = { ++clock },
            elapsedClock = { clock },
            maxOwners = 4,
            maxEvents = 12
        )

        val owner = trace.register("manager", "USB LAB")
        trace.mark(owner, "state-waiting_device", "generation=1 waiting")
        trace.mark(owner, "state-waiting_device", "generation=2 waiting")
        trace.close(owner, "shutdown")
        trace.mark(owner, "phase-offline", "asynchronous disconnect callback")

        val snapshot = trace.snapshotJson()
        val ownerJson = snapshot.getJSONArray("owners").getJSONObject(0)
        assertFalse(ownerJson.getBoolean("active"))
        assertEquals("shutdown", ownerJson.getString("state"))
        assertEquals(1L, ownerJson.getJSONObject("counters").getLong("repeatedStateEvents"))
        assertEquals(1L, ownerJson.getJSONObject("counters").getLong("lateEvents"))

        val events = snapshot.getJSONArray("events")
        assertEquals(4, events.length())
        assertEquals("created", events.getJSONObject(0).getString("event"))
        assertEquals("state-waiting_device", events.getJSONObject(1).getString("event"))
        assertEquals("shutdown", events.getJSONObject(2).getString("event"))
        assertEquals("late-phase-offline", events.getJSONObject(3).getString("event"))
    }

    @Test fun `unknown owners do not create phantom diagnostics`() {
        val trace = LifecycleTrace(
            processGeneration = "unknown",
            wallClock = { 1L },
            elapsedClock = { 1L }
        )

        trace.mark("missing", "resumed")
        trace.increment("missing", "frames")
        trace.close("missing")

        val snapshot = trace.snapshotJson()
        assertEquals(0, snapshot.getJSONArray("owners").length())
        assertEquals(0, snapshot.getJSONArray("events").length())
    }
}
