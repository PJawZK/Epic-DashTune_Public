package com.buttonbox.ble

import org.json.JSONArray

/** Pure JSON-array helpers for the bounded diagnostic event and warning histories. */
internal object DiagnosticHistoryCodec {
    fun decode(raw: String?): JSONArray = try {
        if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
    } catch (_: Exception) {
        JSONArray()
    }

    fun reversed(source: JSONArray): JSONArray {
        val result = JSONArray()
        for (index in source.length() - 1 downTo 0) result.put(source.opt(index))
        return result
    }
}
