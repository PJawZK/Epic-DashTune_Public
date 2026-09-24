package com.buttonbox.ble

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Small process crash breadcrumb used only for diagnostics.
 *
 * Android's historical exit reason tells us that the process crashed but does not reliably include
 * the Java/Kotlin exception text. Preserve the uncaught throwable before delegating to Android's
 * existing default handler so the next diagnostic export can identify the actual failure.
 */
internal object CrashTraceStore {
    private const val FILE_NAME = "last-uncaught-crash.json"
    private const val MAX_TRACE_CHARS = 32_000

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
            val appContext = context.applicationContext
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching { persist(appContext, thread, throwable) }
                previous.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    private fun persist(context: Context, thread: Thread, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val trace = writer.toString().take(MAX_TRACE_CHARS)
        val json = JSONObject()
            .put("capturedAtEpochMs", System.currentTimeMillis())
            .put("threadName", thread.name)
            .put("threadId", thread.id)
            .put("exceptionClass", throwable.javaClass.name)
            .put("message", throwable.message?.take(1000) ?: "")
            .put("trace", trace)
        val target = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(json.toString(), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            target.writeText(json.toString(), Charsets.UTF_8)
            temp.delete()
        }
    }

    fun diagnosticsJson(context: Context): JSONObject {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return JSONObject().put("available", false)
        return runCatching {
            val raw = file.readText(Charsets.UTF_8).take(MAX_TRACE_CHARS + 4096)
            JSONObject(raw).put("available", true)
        }.getOrElse { error ->
            JSONObject()
                .put("available", false)
                .put("readError", (error.message ?: error.javaClass.simpleName).take(240))
        }
    }
}
