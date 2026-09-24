package com.buttonbox.ble

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.provider.MediaStore
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.Reader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * User-recoverable Tuner project envelope.
 *
 * The bundle contains exactly two authorities: the imported mainController.ini source and the last
 * complete native TuneSnapshot. It never owns ECU transport, semantic write, read-back or Burn.
 * Normal Tuner startup reparses the restored INI and fingerprint-gates the restored TuneSnapshot.
 */
internal object TunerRecoveryBundleStore {
    private const val BUNDLE_SCHEMA_VERSION = 2
    private const val SOURCE_FILE_NAME = "epicdash-mainController.ini"
    private const val PROJECT_DIR_NAME = "tuner-project"
    private const val SNAPSHOT_FILE_NAME = "last-tune.snapshot.json"
    private const val LEGACY_VALUES_FILE_NAME = "values.json"
    private const val LEGACY_PROJECT_FILE_NAME = "project.json"
    private const val BUNDLE_FILE_NAME = "EpicDash-JZ-Tuner-Recovery.json"
    private const val PREFS_NAME = "epicdash_tuner_recovery"
    private const val PREF_BUNDLE_URI = "bundleUri"
    private const val MAX_INI_CHARS = 8_000_000
    private const val MAX_SNAPSHOT_CHARS = 48_000_000
    private const val MAX_BUNDLE_CHARS = 64_000_000
    private const val MIRROR_DEBOUNCE_MS = 250L
    private val DOWNLOAD_RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/EpicDash-JZ/"
    private val mirrorExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "EpicDash-TunerRecoveryMirror").apply { isDaemon = true }
    }

    @Volatile private var rootObserver: FileObserver? = null
    @Volatile private var projectObserver: FileObserver? = null
    @Volatile private var pendingMirror: ScheduledFuture<*>? = null
    @Volatile private var lastMirrorStatus = "not_attempted"
    @Volatile private var lastImportStatus = "not_attempted"

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val restoredSnapshot: Boolean
    )

    private data class BundleTarget(val uri: Uri, val bytes: Long)

    private fun iniFile(context: Context) = File(context.filesDir, SOURCE_FILE_NAME)
    private fun projectDir(context: Context) = File(context.filesDir, PROJECT_DIR_NAME)
    private fun snapshotFile(context: Context) = File(projectDir(context), SNAPSHOT_FILE_NAME)
    private fun legacyValuesFile(context: Context) = File(projectDir(context), LEGACY_VALUES_FILE_NAME)
    private fun legacyProjectFile(context: Context) = File(projectDir(context), LEGACY_PROJECT_FILE_NAME)

    @Synchronized
    fun startMirroring(context: Context) {
        val appContext = context.applicationContext
        if (rootObserver == null) {
            val mask = FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
            @Suppress("DEPRECATION")
            rootObserver = object : FileObserver(appContext.filesDir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    when (path) {
                        SOURCE_FILE_NAME -> mirrorAsync(appContext)
                        PROJECT_DIR_NAME -> {
                            ensureProjectObserver(appContext)
                            mirrorAsync(appContext)
                        }
                    }
                }
            }.also { it.startWatching() }
        }
        ensureProjectObserver(appContext)
        mirrorAsync(appContext)
    }

    @Synchronized
    private fun ensureProjectObserver(context: Context) {
        if (projectObserver != null) return
        val dir = projectDir(context)
        if (!dir.isDirectory) return
        val mask = FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO
        @Suppress("DEPRECATION")
        projectObserver = object : FileObserver(dir.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == SNAPSHOT_FILE_NAME) mirrorAsync(context)
            }
        }.also { it.startWatching() }
    }

    @Synchronized
    fun mirrorAsync(context: Context) {
        val appContext = context.applicationContext
        pendingMirror?.cancel(false)
        pendingMirror = mirrorExecutor.schedule(
            { mirrorBundle(appContext) },
            MIRROR_DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun buildBundle(context: Context): String? {
        val ini = iniFile(context)
        if (!ini.isFile || ini.length() <= 0L) {
            lastMirrorStatus = "waiting_for_ini"
            return null
        }
        val iniSource = runCatching { ini.readText(Charsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_INI_CHARS }
            ?: run {
                lastMirrorStatus = "ini_invalid_or_too_large"
                return null
            }

        val snapshotRaw = snapshotFile(context).takeIf { it.isFile }?.let { file ->
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                ?.takeIf { it.isNotBlank() && it.length <= MAX_SNAPSHOT_CHARS }
        }
        val snapshotObject = snapshotRaw?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                TuneSnapshot.fromBackupJson(json)
                json
            }.getOrNull()
        }
        val profileName = context.getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE)
            .getString("profileName", "mainController.ini")
            ?.takeIf { it.isNotBlank() }
            ?: "mainController.ini"

        return JSONObject().apply {
            put("schemaVersion", BUNDLE_SCHEMA_VERSION)
            put("createdAtEpochMs", System.currentTimeMillis())
            put("profileName", profileName)
            put("iniSource", iniSource)
            put("tuneSnapshot", snapshotObject ?: JSONObject.NULL)
            put("snapshotPresent", snapshotObject != null)
        }.toString().takeIf { it.length <= MAX_BUNDLE_CHARS }
            ?: run {
                lastMirrorStatus = "bundle_too_large"
                null
            }
    }

    private fun rememberedUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_BUNDLE_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun rememberUri(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val requested = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, requested)
            true
        }.getOrElse {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_BUNDLE_URI, uri.toString())
            .putBoolean("persistableGrant", persisted)
            .apply()
    }

    private fun ownDownloadsBundle(context: Context): BundleTarget? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            val args = arrayOf(BUNDLE_FILE_NAME, DOWNLOAD_RELATIVE_PATH)
            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                BundleTarget(
                    ContentUris.withAppendedId(collection, cursor.getLong(0)),
                    cursor.getLong(1).coerceAtLeast(0L)
                )
            }
        }.getOrNull()
    }

    private fun writeUri(context: Context, uri: Uri, raw: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { writer -> writer.write(raw) }
            ?: return@runCatching false
        true
    }.getOrDefault(false)

    private fun mirrorBundle(context: Context): Boolean {
        val raw = buildBundle(context) ?: return false

        rememberedUri(context)?.let { uri ->
            if (writeUri(context, uri, raw)) {
                lastMirrorStatus = "updated_authorized_bundle"
                return true
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            lastMirrorStatus = "downloads_mirror_requires_android_10"
            return false
        }

        ownDownloadsBundle(context)?.let { existing ->
            if (writeUri(context, existing.uri, raw)) {
                rememberUri(context, existing.uri)
                lastMirrorStatus = "updated_downloads_bundle"
                return true
            }
        }

        var created: Uri? = null
        val createdOk = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, BUNDLE_FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOAD_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            created = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(created!!, "w")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { writer -> writer.write(raw) }
                ?: return@runCatching false
            context.contentResolver.update(
                created!!,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            true
        }.getOrElse {
            created?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            false
        }
        if (createdOk) {
            created?.let { rememberUri(context, it) }
            lastMirrorStatus = "created_downloads_bundle"
        } else {
            lastMirrorStatus = "downloads_bundle_write_failed"
        }
        return createdOk
    }

    private fun readCapped(reader: Reader, limit: Int): String? {
        val out = StringBuilder(minOf(limit, 64 * 1024))
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (out.length + count > limit) return null
            out.append(buffer, 0, count)
        }
        return out.toString()
    }

    private fun atomicWrite(target: File, raw: String): Boolean = runCatching {
        target.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(raw.toByteArray(Charsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
            true
        } catch (error: Throwable) {
            runCatching { atomic.failWrite(stream) }
            throw error
        }
    }.getOrDefault(false)

    private data class RecoveryFileState(val existed: Boolean, val raw: String?)

    private fun captureRecoveryFile(file: File, maxChars: Int): RecoveryFileState? {
        if (!file.exists()) return RecoveryFileState(false, null)
        if (!file.isFile || file.length() <= 0L) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        if (raw.length > maxChars) return null
        return RecoveryFileState(true, raw)
    }

    private fun restoreRecoveryFile(file: File, state: RecoveryFileState): Boolean =
        if (state.existed) state.raw?.let { atomicWrite(file, it) } == true
        else runCatching { !file.exists() || file.delete() }.getOrDefault(false)

    private fun commitRecoveryGeneration(
        context: Context,
        iniSource: String,
        snapshotRaw: String?,
        profileName: String
    ): Boolean {
        val ini = iniFile(context)
        val snapshot = snapshotFile(context)
        val oldIni = captureRecoveryFile(ini, MAX_INI_CHARS) ?: return false
        val oldSnapshot = captureRecoveryFile(snapshot, MAX_SNAPSHOT_CHARS) ?: return false
        val prefs = context.getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE)
        val oldProfileName = prefs.getString("profileName", null)

        fun rollback(): Boolean {
            val iniRestored = restoreRecoveryFile(ini, oldIni)
            val snapshotRestored = restoreRecoveryFile(snapshot, oldSnapshot)
            val editor = prefs.edit().remove("profile")
            if (oldProfileName == null) editor.remove("profileName") else editor.putString("profileName", oldProfileName)
            val prefsRestored = editor.commit()
            return iniRestored && snapshotRestored && prefsRestored
        }

        if (!atomicWrite(ini, iniSource)) return false
        val snapshotCommitted = if (snapshotRaw != null) {
            atomicWrite(snapshot, snapshotRaw)
        } else {
            runCatching { !snapshot.exists() || snapshot.delete() }.getOrDefault(false)
        }
        if (!snapshotCommitted) {
            rollback()
            return false
        }
        val exactIni = runCatching { ini.readText(Charsets.UTF_8) }.getOrNull() == iniSource
        val exactSnapshot = if (snapshotRaw == null) {
            !snapshot.exists()
        } else {
            val committed = runCatching { snapshot.readText(Charsets.UTF_8) }.getOrNull()
            committed == snapshotRaw && runCatching { TuneSnapshot.fromBackupJson(JSONObject(committed)) }.isSuccess
        }
        if (!exactIni || !exactSnapshot ||
            !prefs.edit().putString("profileName", profileName).remove("profile").commit()
        ) {
            rollback()
            return false
        }
        return true
    }

    fun importBundle(context: Context, uri: Uri): ImportResult {
        val appContext = context.applicationContext
        val raw = runCatching {
            appContext.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader -> readCapped(reader, MAX_BUNDLE_CHARS) }
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            lastImportStatus = "bundle_unreadable"
            return ImportResult(false, "The selected recovery file could not be read.", false)
        }

        val bundle = runCatching { JSONObject(raw) }.getOrNull()
        if (bundle == null || bundle.optInt("schemaVersion", 0) != BUNDLE_SCHEMA_VERSION) {
            lastImportStatus = "bundle_invalid_schema"
            return ImportResult(false, "The selected file is not a current EpicDash JZ Tuner recovery bundle.", false)
        }

        val iniSource = bundle.optString("iniSource", "")
        if (iniSource.isBlank() || iniSource.length > MAX_INI_CHARS) {
            lastImportStatus = "bundle_ini_invalid"
            return ImportResult(false, "The recovery bundle does not contain a valid INI source.", false)
        }
        val profileName = bundle.optString("profileName", "mainController.ini").ifBlank { "mainController.ini" }
        val profile = runCatching { UsbTunerStudioProfileParser.parse(iniSource, profileName) }.getOrNull()
            ?: run {
                lastImportStatus = "bundle_ini_parse_failed"
                return ImportResult(false, "The recovery INI could not be parsed.", false)
            }

        val snapshotObject = bundle.optJSONObject("tuneSnapshot")
        val snapshot = snapshotObject?.let { json -> runCatching { TuneSnapshot.fromBackupJson(json) }.getOrNull() }
        if (snapshotObject != null && snapshot == null) {
            lastImportStatus = "bundle_snapshot_invalid"
            return ImportResult(false, "The recovery bundle contains an invalid TuneSnapshot.", false)
        }
        if (snapshot != null && (
                snapshot.ecuSignature != profile.signature ||
                    !snapshot.profileFingerprint.equals(profile.tuneProfileFingerprint(), ignoreCase = true)
            )
        ) {
            lastImportStatus = "bundle_snapshot_profile_mismatch"
            return ImportResult(false, "The saved TuneSnapshot does not belong to the recovery INI.", false)
        }

        val snapshotRaw = snapshotObject?.toString()
        if (snapshotRaw != null && snapshotRaw.length > MAX_SNAPSHOT_CHARS) {
            lastImportStatus = "bundle_snapshot_too_large"
            return ImportResult(false, "The recovery TuneSnapshot is too large.", false)
        }
        if (!commitRecoveryGeneration(appContext, iniSource, snapshotRaw, profileName)) {
            lastImportStatus = "recovery_generation_commit_failed"
            return ImportResult(false, "Recovery project commit failed; the previous project was restored.", false)
        }
        val restoredSnapshot = snapshotRaw != null
        runCatching { legacyValuesFile(appContext).delete() }
        runCatching { legacyProjectFile(appContext).delete() }

        rememberUri(appContext, uri)
        ensureProjectObserver(appContext)
        lastImportStatus = if (restoredSnapshot) "restored_ini_and_snapshot" else "restored_ini_only"
        mirrorAsync(appContext)
        return ImportResult(
            true,
            if (restoredSnapshot) "Tuner project restored. Reopening with the saved INI and complete TuneSnapshot."
            else "Tuner INI restored. Reopening without a saved TuneSnapshot.",
            restoredSnapshot
        )
    }

    fun diagnostics(context: Context): String {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("bundleSchemaVersion", BUNDLE_SCHEMA_VERSION)
            put("bundleFileName", BUNDLE_FILE_NAME)
            put("iniAvailable", iniFile(appContext).isFile)
            put("snapshotAvailable", snapshotFile(appContext).isFile)
            put("localProjectState", TunerPermanentProjectStore.localProjectState(appContext).name)
            put("authorizedBundleUri", prefs.getString(PREF_BUNDLE_URI, null) ?: JSONObject.NULL)
            put("persistableGrant", prefs.getBoolean("persistableGrant", false))
            put("downloadsMirrorSupported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            put("reinstallRestoreRequiresUserSelection", true)
            put("lastMirrorStatus", lastMirrorStatus)
            put("lastImportStatus", lastImportStatus)
        }.toString()
    }
}
