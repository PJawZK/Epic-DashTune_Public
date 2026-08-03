package com.buttonbox.ble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent bounded history for diagnostics and warning incidents.
 * The payload is intentionally compact so it remains safe on older devices.
 */
object DiagnosticStore {
    private const val PREFS_NAME = "epicdash_jz_diagnostics"
    private const val KEY_EVENTS = "events"
    private const val KEY_WARNINGS = "warnings"
    private const val KEY_REVISION = "revision"
    private const val KEY_SESSION_ID = "session_id"
    private const val MAX_EVENTS = 200
    private const val MAX_WARNINGS = 100
    private const val INCIDENT_GROUP_WINDOW_MS = 30_000L
    private val idCounter = AtomicLong(0)
    private val snapshotCache = DiagnosticStoreSnapshotCache()

    @Synchronized
    fun startSession(context: Context, label: String): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessionId = newId("s")
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply()
        addEvent(context, "SESSION", "Session started: ${label.take(160)}")
        return sessionId
    }

    @Synchronized
    fun addEvent(context: Context, category: String, message: String): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val events = DiagnosticHistoryCodec.decode(prefs.getString(KEY_EVENTS, null))
        val id = newId("e")
        events.put(
            JSONObject()
                .put("id", id)
                .put("timestamp", System.currentTimeMillis())
                .put("sessionId", prefs.getString(KEY_SESSION_ID, "") ?: "")
                .put("category", category.take(24))
                .put("message", message.take(500))
        )
        trimFront(events, MAX_EVENTS)
        prefs.edit()
            .putString(KEY_EVENTS, events.toString())
            .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0L) + 1L)
            .apply()
        return id
    }

    @Synchronized
    fun addWarning(
        context: Context,
        ruleId: String,
        title: String,
        message: String,
        valuesJson: String?
    ): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val warnings = DiagnosticHistoryCodec.decode(prefs.getString(KEY_WARNINGS, null))
        val now = System.currentTimeMillis()
        val values = try {
            if (valuesJson.isNullOrBlank()) JSONObject() else JSONObject(valuesJson)
        } catch (_: Exception) {
            JSONObject().put("raw", valuesJson?.take(500) ?: "")
        }
        val source = values.optString("_source", "unknown").take(24)
        val sessionId = prefs.getString(KEY_SESSION_ID, "") ?: ""

        val latest = if (warnings.length() > 0) warnings.optJSONObject(warnings.length() - 1) else null
        val canGroup = latest != null &&
            latest.optString("ruleId") == ruleId &&
            latest.optString("sessionId", "") == sessionId &&
            latest.optString("source", "unknown") == source &&
            now - latest.optLong("lastTimestamp", latest.optLong("timestamp", 0L)) <= INCIDENT_GROUP_WINDOW_MS

        val id: String
        if (canGroup) {
            val grouped = latest!!
            id = grouped.optString("id")
            grouped.put("lastTimestamp", now)
            grouped.put("repeatCount", grouped.optInt("repeatCount", 1) + 1)
            grouped.put("message", message.take(500))
            grouped.put("title", title.take(120))
            grouped.put("values", values)
            grouped.put("acknowledged", false)
            grouped.put("acknowledgedAt", JSONObject.NULL)
        } else {
            id = newId("w")
            warnings.put(
                JSONObject()
                    .put("id", id)
                    .put("ruleId", ruleId.take(48))
                    .put("sessionId", sessionId)
                    .put("source", source)
                    .put("timestamp", now)
                    .put("lastTimestamp", now)
                    .put("repeatCount", 1)
                    .put("title", title.take(120))
                    .put("message", message.take(500))
                    .put("acknowledged", false)
                    .put("acknowledgedAt", JSONObject.NULL)
                    .put("values", values)
            )
        }
        trimFront(warnings, MAX_WARNINGS)
        prefs.edit()
            .putString(KEY_WARNINGS, warnings.toString())
            .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0L) + 1L)
            .apply()
        return id
    }

    @Synchronized
    fun acknowledgeWarning(context: Context, warningId: String) {
        if (warningId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val warnings = DiagnosticHistoryCodec.decode(prefs.getString(KEY_WARNINGS, null))
        var changed = false
        for (index in 0 until warnings.length()) {
            val item = warnings.optJSONObject(index) ?: continue
            if (item.optString("id") == warningId && !item.optBoolean("acknowledged")) {
                item.put("acknowledged", true)
                item.put("acknowledgedAt", System.currentTimeMillis())
                changed = true
                break
            }
        }
        if (changed) {
            prefs.edit()
                .putString(KEY_WARNINGS, warnings.toString())
                .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0L) + 1L)
                .apply()
        }
    }

    @Synchronized
    fun clearEvents(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_EVENTS)
            .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0L) + 1L)
            .apply()
    }

    @Synchronized
    fun clearWarnings(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_WARNINGS)
            .putLong(KEY_REVISION, prefs.getLong(KEY_REVISION, 0L) + 1L)
            .apply()
    }

    @Synchronized
    fun snapshotJson(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return snapshotCache.snapshot(
            revision = prefs.getLong(KEY_REVISION, 0L),
            currentSessionId = prefs.getString(KEY_SESSION_ID, "") ?: "",
            eventsRaw = prefs.getString(KEY_EVENTS, null),
            warningsRaw = prefs.getString(KEY_WARNINGS, null)
        )
    }

    private fun newId(prefix: String): String =
        "$prefix-${System.currentTimeMillis()}-${idCounter.incrementAndGet()}"

    private fun trimFront(array: JSONArray, maximum: Int) {
        while (array.length() > maximum) array.remove(0)
    }
}
