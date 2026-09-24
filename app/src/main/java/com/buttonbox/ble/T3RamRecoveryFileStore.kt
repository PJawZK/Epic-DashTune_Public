package com.buttonbox.ble

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * Small durable recovery marker for the T3 RAM-only pilot.
 *
 * AtomicFile plus FileDescriptor.sync() is intentionally used instead of SharedPreferences.apply():
 * the marker must be durable before the transport is permitted to cross the point where a C frame
 * may have started transmitting.
 */
internal class T3RamRecoveryFileStore(context: Context) : T3RamRecoveryMarkerStore {
    private val atomicFile = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    @Synchronized
    override fun load(): T3RecoveryStoreRead {
        return try {
            val text = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val marker = T3RamRecoveryMarker.fromJson(JSONObject(text))
            T3RecoveryStoreRead(T3RecoveryStoreState.PRESENT, marker)
        } catch (_: FileNotFoundException) {
            T3RecoveryStoreRead(T3RecoveryStoreState.ABSENT)
        } catch (error: Exception) {
            T3RecoveryStoreRead(
                state = T3RecoveryStoreState.CORRUPT,
                error = (error.message ?: error.javaClass.simpleName).take(240)
            )
        }
    }

    @Synchronized
    override fun persist(marker: T3RamRecoveryMarker): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            val bytes = marker.toJson().toString().toByteArray(Charsets.UTF_8)
            stream.write(bytes)
            stream.flush()
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            stream = null
            val reread = load()
            reread.state == T3RecoveryStoreState.PRESENT && reread.marker == marker
        } catch (_: Exception) {
            stream?.let { runCatching { atomicFile.failWrite(it) } }
            false
        }
    }

    @Synchronized
    override fun clear(): Boolean {
        return try {
            atomicFile.delete()
            load().state == T3RecoveryStoreState.ABSENT
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val FILE_NAME = "t3_ram_recovery_marker.json"
    }
}
