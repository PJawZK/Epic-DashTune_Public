package com.buttonbox.ble

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * Durable W5/T6 persistence recovery record.
 *
 * This file must be fsynced before the native manager is permitted to release a B frame. Unlike the
 * W4 RAM marker, this record survives the intentional USB generation change/power cycle required by
 * persistence verification.
 */
internal class T6PersistentBurnRecoveryFileStore(context: Context) : T6PersistentBurnRecoveryStore {
    private val atomicFile = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    @Synchronized
    override fun load(): T6RecoveryStoreRead {
        return try {
            val text = atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val marker = T6PersistentBurnRecoveryMarker.fromJson(JSONObject(text))
            T6RecoveryStoreRead(T6RecoveryStoreState.PRESENT, marker)
        } catch (_: FileNotFoundException) {
            T6RecoveryStoreRead(T6RecoveryStoreState.ABSENT)
        } catch (error: Exception) {
            T6RecoveryStoreRead(
                state = T6RecoveryStoreState.CORRUPT,
                error = (error.message ?: error.javaClass.simpleName).take(240)
            )
        }
    }

    @Synchronized
    override fun persist(marker: T6PersistentBurnRecoveryMarker): Boolean {
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
            reread.state == T6RecoveryStoreState.PRESENT && reread.marker == marker
        } catch (_: Exception) {
            stream?.let { runCatching { atomicFile.failWrite(it) } }
            false
        }
    }

    @Synchronized
    override fun clear(): Boolean {
        return try {
            atomicFile.delete()
            load().state == T6RecoveryStoreState.ABSENT
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val FILE_NAME = "t6_persistent_burn_recovery.json"
    }
}
