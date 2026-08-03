package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class DiagnosticStoreSnapshotCacheTest {
    @Test
    fun unchangedPersistedInputsReuseSnapshot() {
        val cache = DiagnosticStoreSnapshotCache()
        val events = """[{"id":"event-1"}]"""
        val warnings = """[{"id":"warning-1"}]"""

        val first = cache.snapshot(7L, "session-1", events, warnings)
        val second = cache.snapshot(7L, "session-1", events, warnings)

        assertSame(first, second)
    }

    @Test
    fun revisionOrStoredInputChangeInvalidatesSnapshot() {
        val cache = DiagnosticStoreSnapshotCache()
        val first = cache.snapshot(7L, "session-1", "[]", "[]")
        val revisionChanged = cache.snapshot(8L, "session-1", "[]", "[]")
        val sessionChanged = cache.snapshot(8L, "session-2", "[]", "[]")
        val eventsChanged = cache.snapshot(8L, "session-2", "[1]", "[]")
        val warningsChanged = cache.snapshot(8L, "session-2", "[1]", "[2]")

        assertNotSame(first, revisionChanged)
        assertNotSame(revisionChanged, sessionChanged)
        assertNotSame(sessionChanged, eventsChanged)
        assertNotSame(eventsChanged, warningsChanged)
    }

    @Test
    fun snapshotPreservesSchemaAndNewestFirstOrdering() {
        val snapshot = DiagnosticStoreSnapshotCache().snapshot(
            revision = 12L,
            currentSessionId = "session-12",
            eventsRaw = """[{"id":"old-event"},{"id":"new-event"}]""",
            warningsRaw = """[{"id":"old-warning"},{"id":"new-warning"}]"""
        )

        assertEquals(12L, snapshot.getLong("revision"))
        assertEquals("session-12", snapshot.getString("currentSessionId"))
        assertEquals("new-event", snapshot.getJSONArray("events").getJSONObject(0).getString("id"))
        assertEquals("old-event", snapshot.getJSONArray("events").getJSONObject(1).getString("id"))
        assertEquals("new-warning", snapshot.getJSONArray("warnings").getJSONObject(0).getString("id"))
        assertEquals("old-warning", snapshot.getJSONArray("warnings").getJSONObject(1).getString("id"))
    }

    @Test
    fun malformedStoredHistoryRemainsEmpty() {
        val snapshot = DiagnosticStoreSnapshotCache().snapshot(
            revision = 1L,
            currentSessionId = "session",
            eventsRaw = "bad-events",
            warningsRaw = "bad-warnings"
        )

        assertEquals(0, snapshot.getJSONArray("events").length())
        assertEquals(0, snapshot.getJSONArray("warnings").length())
    }
}
