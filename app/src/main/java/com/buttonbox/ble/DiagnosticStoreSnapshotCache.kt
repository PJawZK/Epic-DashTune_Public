package com.buttonbox.ble

import org.json.JSONObject

/**
 * Reuses the immutable-by-convention diagnostic history snapshot while its persisted inputs are
 * unchanged. Callers must treat the returned [JSONObject] as read-only.
 */
internal class DiagnosticStoreSnapshotCache {
    private data class Key(
        val revision: Long,
        val currentSessionId: String,
        val eventsRaw: String?,
        val warningsRaw: String?
    )

    private var cachedKey: Key? = null
    private var cachedSnapshot: JSONObject? = null

    @Synchronized
    fun snapshot(
        revision: Long,
        currentSessionId: String,
        eventsRaw: String?,
        warningsRaw: String?
    ): JSONObject {
        val key = Key(revision, currentSessionId, eventsRaw, warningsRaw)
        if (key == cachedKey) cachedSnapshot?.let { return it }

        return JSONObject()
            .put("revision", revision)
            .put("currentSessionId", currentSessionId)
            .put(
                "events",
                DiagnosticHistoryCodec.reversed(DiagnosticHistoryCodec.decode(eventsRaw))
            )
            .put(
                "warnings",
                DiagnosticHistoryCodec.reversed(DiagnosticHistoryCodec.decode(warningsRaw))
            )
            .also {
                cachedKey = key
                cachedSnapshot = it
            }
    }
}
