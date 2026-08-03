package com.buttonbox.ble

import org.json.JSONObject

/**
 * Stores the latest dashboard runtime diagnostic snapshot.
 *
 * The dashboard may publish either the legacy complete snapshot or a versioned top-level section
 * patch. Consumers always receive the same complete report shape. Parsed state is retained between
 * updates so unchanged sections do not need to cross the JavaScript bridge repeatedly.
 */
internal class DashboardRuntimeSnapshotStore(
    private val maximumPayloadChars: Int = 80_000
) {
    init {
        require(maximumPayloadChars > 0) { "maximumPayloadChars must be positive" }
    }

    private var stored = JSONObject()
    private var updatedAtMs = 0L

    @Synchronized
    fun update(payloadJson: String, receivedAtMs: Long) {
        val bounded = payloadJson.take(maximumPayloadChars)
        val payload = try {
            if (bounded.isBlank()) JSONObject() else JSONObject(bounded)
        } catch (_: Exception) {
            JSONObject().put("parseError", true).put("raw", bounded.take(500))
        }

        if (payload.optInt(PATCH_VERSION_KEY, 0) == PATCH_VERSION) {
            val sections = payload.optJSONObject(SECTIONS_KEY)
            if (sections != null) {
                if (payload.optBoolean(COMPLETE_KEY, false)) replace(sections) else mergeSections(sections)
            } else {
                replace(payload)
            }
        } else {
            replace(payload)
        }
        updatedAtMs = receivedAtMs.coerceAtLeast(0L)
    }

    @Synchronized
    fun snapshot(nowMs: Long): JSONObject {
        val result = deepCopy(stored)
        result.put(
            "nativeSnapshotUpdatedAt",
            if (updatedAtMs > 0L) updatedAtMs else JSONObject.NULL
        )
        result.put(
            "nativeSnapshotAgeMs",
            if (updatedAtMs > 0L) (nowMs - updatedAtMs).coerceAtLeast(0L) else JSONObject.NULL
        )
        return result
    }

    private fun replace(payload: JSONObject) {
        stored = deepCopy(payload)
        stored.remove(PATCH_VERSION_KEY)
        stored.remove(COMPLETE_KEY)
        stored.remove(SECTIONS_KEY)
    }

    private fun mergeSections(sections: JSONObject) {
        val keys = sections.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            stored.put(key, deepCopyValue(sections.opt(key)))
        }
    }

    private fun deepCopy(source: JSONObject): JSONObject = JSONObject(source.toString())

    private fun deepCopyValue(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject(value.toString())
        is org.json.JSONArray -> org.json.JSONArray(value.toString())
        else -> value
    }

    companion object {
        internal const val PATCH_VERSION_KEY = "runtimePatchVersion"
        internal const val COMPLETE_KEY = "complete"
        internal const val SECTIONS_KEY = "sections"
        internal const val PATCH_VERSION = 1
    }
}
