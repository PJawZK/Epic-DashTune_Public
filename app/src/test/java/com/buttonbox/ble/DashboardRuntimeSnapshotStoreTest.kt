package com.buttonbox.ble

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRuntimeSnapshotStoreTest {
    @Test
    fun legacyCompleteSnapshotIsPreserved() {
        val store = DashboardRuntimeSnapshotStore()
        store.update(
            JSONObject()
                .put("source", "LIVE")
                .put("layout", JSONObject().put("name", "Daily"))
                .toString(),
            receivedAtMs = 1_000L
        )

        val result = store.snapshot(1_250L)

        assertEquals("LIVE", result.getString("source"))
        assertEquals("Daily", result.getJSONObject("layout").getString("name"))
        assertEquals(1_000L, result.getLong("nativeSnapshotUpdatedAt"))
        assertEquals(250L, result.getLong("nativeSnapshotAgeMs"))
    }

    @Test
    fun completeSectionEnvelopeReplacesPreviousSnapshot() {
        val store = DashboardRuntimeSnapshotStore()
        store.update(
            """{"source":"OLD","staleFutureKey":true}""",
            receivedAtMs = 100L
        )
        store.update(
            JSONObject()
                .put(DashboardRuntimeSnapshotStore.PATCH_VERSION_KEY, 1)
                .put(DashboardRuntimeSnapshotStore.COMPLETE_KEY, true)
                .put(
                    DashboardRuntimeSnapshotStore.SECTIONS_KEY,
                    JSONObject().put("source", "LIVE")
                )
                .toString(),
            receivedAtMs = 200L
        )

        val result = store.snapshot(200L)

        assertEquals("LIVE", result.getString("source"))
        assertFalse(result.has("staleFutureKey"))
        assertFalse(result.has(DashboardRuntimeSnapshotStore.COMPLETE_KEY))
    }

    @Test
    fun sectionPatchUpdatesOnlyProvidedTopLevelKeys() {
        val store = DashboardRuntimeSnapshotStore()
        store.update(
            JSONObject()
                .put("source", "LIVE")
                .put("layout", JSONObject().put("name", "Daily"))
                .put("channelValues", JSONObject().put("rpm", 800))
                .toString(),
            receivedAtMs = 1_000L
        )

        store.update(
            JSONObject()
                .put(DashboardRuntimeSnapshotStore.PATCH_VERSION_KEY, 1)
                .put(
                    DashboardRuntimeSnapshotStore.SECTIONS_KEY,
                    JSONObject()
                        .put("source", "MSL")
                        .put("channelValues", JSONObject().put("rpm", 2_500))
                )
                .toString(),
            receivedAtMs = 1_400L
        )

        val result = store.snapshot(1_500L)

        assertEquals("MSL", result.getString("source"))
        assertEquals(2_500, result.getJSONObject("channelValues").getInt("rpm"))
        assertEquals("Daily", result.getJSONObject("layout").getString("name"))
        assertEquals(100L, result.getLong("nativeSnapshotAgeMs"))
        assertFalse(result.has(DashboardRuntimeSnapshotStore.PATCH_VERSION_KEY))
        assertFalse(result.has(DashboardRuntimeSnapshotStore.SECTIONS_KEY))
    }

    @Test
    fun arraysAndExplicitNullReplacePreviousValues() {
        val store = DashboardRuntimeSnapshotStore()
        store.update(
            """{"validChannels":["rpm","map"],"fileName":"run.msl"}""",
            receivedAtMs = 100L
        )
        store.update(
            """{"runtimePatchVersion":1,"sections":{"validChannels":["rpm"],"fileName":null}}""",
            receivedAtMs = 200L
        )

        val result = store.snapshot(200L)

        assertEquals(1, result.getJSONArray("validChannels").length())
        assertTrue(result.isNull("fileName"))
    }

    @Test
    fun malformedInputProducesBoundedParseErrorSnapshot() {
        val store = DashboardRuntimeSnapshotStore(maximumPayloadChars = 32)
        store.update("not-json-abcdefghijklmnopqrstuvwxyz", receivedAtMs = 500L)

        val result = store.snapshot(600L)

        assertTrue(result.getBoolean("parseError"))
        assertTrue(result.getString("raw").length <= 32)
        assertEquals(100L, result.getLong("nativeSnapshotAgeMs"))
    }

    @Test
    fun noUpdateUsesNullNativeTimestamps() {
        val result = DashboardRuntimeSnapshotStore().snapshot(1_000L)

        assertTrue(result.isNull("nativeSnapshotUpdatedAt"))
        assertTrue(result.isNull("nativeSnapshotAgeMs"))
        assertNull(result.opt("source"))
    }
}
