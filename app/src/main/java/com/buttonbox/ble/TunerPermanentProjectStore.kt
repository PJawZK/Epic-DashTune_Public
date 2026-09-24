package com.buttonbox.ble

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Permanent Tuner project owner.
 *
 * The exact imported mainController.ini remains the sole structural authority. The last complete,
 * validated native TuneSnapshot is persisted atomically as the tune-value authority. A compiled
 * INI profile is allowed only as a derived startup accelerator and is accepted solely when its
 * source SHA-256 exactly matches the authoritative INI. No WebView cache, semantic value sidecar,
 * alternate transport, write path, read-back path or Burn authority exists here.
 */
internal object TunerPermanentProjectStore {
    private const val PROJECT_DIR_NAME = "tuner-project"
    private const val SNAPSHOT_FILE_NAME = "last-tune.snapshot.json"
    private const val COMPILED_PROFILE_FILE_NAME = "mainController.compiled-profile.json"
    private const val COMPILED_PROFILE_SCHEMA = "EpicDashTunerCompiledIni/v1"
    private const val LEGACY_VALUES_FILE_NAME = "values.json"
    private const val LEGACY_PROJECT_FILE_NAME = "project.json"
    private const val SOURCE_FILE_NAME = "epicdash-mainController.ini"
    private const val MAX_SNAPSHOT_CHARS = 48_000_000
    private const val MAX_INI_CHARS = 8_000_000
    private const val MAX_COMPILED_PROFILE_CHARS = 48_000_000

    private data class DeferredCompiledProfile(
        val candidateId: Long,
        val sourceSha256: String,
        val importedName: String,
        val profile: UsbTunerStudioProfile
    )

    data class BootstrapWorkspacePayload(
        val json: String,
        val compiledProfileCandidateId: Long?
    )

    // 1196: short-lived parsed-profile/deferred-candidate state is deliberately independent from
    // the giant permanent-workspace single-flight. A process profile hit must never wait behind
    // multi-second workspace projection/serialization.
    private val profileStateLock = Any()
    private val bootstrapCacheLock = Any()
    private val heavyWorkLock = Any()
    private val bootstrapCacheHits = AtomicLong(0L)
    private val compiledProfileHits = AtomicLong(0L)
    private val compiledProfileCandidateSequence = AtomicLong(0L)

    @Volatile private var appContext: Context? = null
    @Volatile private var lastPersistStatus = "not_attempted"
    @Volatile private var lastLoadStatus = "not_attempted"
    @Volatile private var lastProjectionStatus = "not_attempted"
    @Volatile private var lastProjectionElapsedMs = 0L
    @Volatile private var lastProfileLoadStatus = "not_attempted"
    @Volatile private var cachedSourceSha256 = ""
    @Volatile private var cachedImportedName = ""
    @Volatile private var cachedProfile: UsbTunerStudioProfile? = null
    @Volatile private var cachedBootstrapKey = ""
    @Volatile private var cachedBootstrapJson = ""
    @Volatile private var cachedBootstrapCandidateId: Long? = null
    private var deferredCompiledProfile: DeferredCompiledProfile? = null
    private var deferredCompiledProfileEligible: DeferredCompiledProfile? = null
    private var compiledProfilePersistInFlight = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        cleanupLegacySemanticState(context.applicationContext)
    }

    internal fun projectDir(context: Context): File = File(context.filesDir, PROJECT_DIR_NAME)
    internal fun snapshotFile(context: Context): File = File(projectDir(context), SNAPSHOT_FILE_NAME)
    internal fun sourceIniFile(context: Context): File = File(context.filesDir, SOURCE_FILE_NAME)
    internal fun compiledProfileFile(context: Context): File = File(projectDir(context), COMPILED_PROFILE_FILE_NAME)

    enum class LocalProjectState {
        NO_PROJECT,
        VALID_PROJECT,
        INI_ONLY,
        CORRUPT_PROJECT,
        PROFILE_MISMATCH
    }

    fun hasLocalProject(context: Context): Boolean {
        val ini = sourceIniFile(context)
        val snapshot = snapshotFile(context)
        if (!ini.isFile || ini.length() <= 0L || ini.length() > MAX_INI_CHARS) return false
        if (!snapshot.isFile || snapshot.length() <= 0L || snapshot.length() > MAX_SNAPSHOT_CHARS) return false
        val raw = runCatching { snapshot.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        if (raw.isBlank() || raw.length > MAX_SNAPSHOT_CHARS) return false
        return runCatching { TuneSnapshot.fromBackupJson(JSONObject(raw)) }.isSuccess
    }

    fun localProjectState(context: Context): LocalProjectState {
        val ini = sourceIniFile(context)
        if (!ini.isFile) return LocalProjectState.NO_PROJECT
        if (ini.length() <= 0L || ini.length() > MAX_INI_CHARS) return LocalProjectState.CORRUPT_PROJECT
        val profile = loadProfileForCurrentIni(context) ?: return LocalProjectState.CORRUPT_PROJECT
        val snapshot = snapshotFile(context)
        if (!snapshot.isFile) return LocalProjectState.INI_ONLY
        if (snapshot.length() <= 0L || snapshot.length() > MAX_SNAPSHOT_CHARS) return LocalProjectState.CORRUPT_PROJECT
        val raw = runCatching { snapshot.readText(Charsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_SNAPSHOT_CHARS }
            ?: return LocalProjectState.CORRUPT_PROJECT
        val decoded = runCatching { TuneSnapshot.fromBackupJson(JSONObject(raw)) }.getOrNull()
            ?: return LocalProjectState.CORRUPT_PROJECT
        return if (decoded.ecuSignature == profile.signature &&
            decoded.profileFingerprint.equals(profile.tuneProfileFingerprint(), ignoreCase = true)
        ) LocalProjectState.VALID_PROJECT else LocalProjectState.PROFILE_MISMATCH
    }

    fun persistAuthoritativeIni(context: Context, source: String, importedName: String): Boolean {
        if (source.isBlank() || source.length > MAX_INI_CHARS || importedName.isBlank()) return false
        val appContext = context.applicationContext
        val target = sourceIniFile(appContext)
        val previousExists = target.isFile
        val previous = if (previousExists) runCatching { target.readText(Charsets.UTF_8) }.getOrNull() else null
        val prefs = appContext.getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE)
        val previousName = prefs.getString("profileName", null)

        fun rollback() {
            if (previousExists && previous != null) atomicWrite(target, previous)
            else runCatching { target.delete() }
            val editor = prefs.edit().remove("profile")
            if (previousName == null) editor.remove("profileName") else editor.putString("profileName", previousName)
            editor.commit()
        }

        if (!atomicWrite(target, source)) return false
        val exact = runCatching { target.readText(Charsets.UTF_8) }.getOrNull()
        if (exact != source) {
            rollback()
            return false
        }
        if (!prefs.edit().putString("profileName", importedName).remove("profile").commit()) {
            rollback()
            return false
        }
        synchronized(profileStateLock) {
            cachedSourceSha256 = ""
            cachedImportedName = ""
            cachedProfile = null
            deferredCompiledProfile = null
            deferredCompiledProfileEligible = null
        }
        synchronized(bootstrapCacheLock) {
            cachedBootstrapKey = ""
            cachedBootstrapJson = ""
            cachedBootstrapCandidateId = null
        }
        lastProfileLoadStatus = "authoritative_ini_committed"
        return true
    }

    /**
     * Returns the current authoritative INI profile through the same SHA-bound process/compiled
     * profile path used by the permanent Tuner project. Activity recreation must not independently
     * reparse the same 32k-line INI.
     */
    fun loadProfileForCurrentIni(context: Context): UsbTunerStudioProfile? {
        val totalStartedNs = System.nanoTime()
        val result = runCatching {
            val ini = sourceIniFile(context)
            if (!ini.isFile || ini.length() <= 0L || ini.length() > MAX_INI_CHARS) return@runCatching null
            val readStartedNs = System.nanoTime()
            val source = ini.readText(Charsets.UTF_8)
            PerformanceMetrics.timingUs("tunerProfileIniRead", System.nanoTime() - readStartedNs)
            if (source.isBlank() || source.length > MAX_INI_CHARS) return@runCatching null
            val importedName = importedName(context)
            val hashStartedNs = System.nanoTime()
            val sourceSha = sha256(source)
            PerformanceMetrics.timingUs("tunerProfileIniHash", System.nanoTime() - hashStartedNs)
            val resolveStartedNs = System.nanoTime()
            profileForSource(context, source, importedName, sourceSha).also {
                PerformanceMetrics.timingUs("tunerProfileResolve", System.nanoTime() - resolveStartedNs)
            }
        }.getOrNull()
        PerformanceMetrics.timingUs("tunerProfileRestoreTotal", System.nanoTime() - totalStartedNs)
        return result
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

    private fun cleanupLegacySemanticState(context: Context) {
        val dir = projectDir(context)
        runCatching { File(dir, LEGACY_VALUES_FILE_NAME).delete() }
        runCatching { File(dir, LEGACY_PROJECT_FILE_NAME).delete() }
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun importedName(context: Context): String =
        context.getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE)
            .getString("profileName", "mainController.ini")
            ?.takeIf { it.isNotBlank() }
            ?: "mainController.ini"

    private fun loadCompiledProfile(
        context: Context,
        sourceSha256: String,
        importedName: String
    ): UsbTunerStudioProfile? {
        val file = compiledProfileFile(context)
        val readStartedNs = System.nanoTime()
        val raw = runCatching { file.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty() }.getOrDefault("")
        PerformanceMetrics.timingUs("tunerCompiledProfileRead", System.nanoTime() - readStartedNs)
        if (raw.isBlank() || raw.length > MAX_COMPILED_PROFILE_CHARS) return null
        val decodeStartedNs = System.nanoTime()
        val decoded = runCatching {
            val root = JSONObject(raw)
            require(root.optString("schema") == COMPILED_PROFILE_SCHEMA)
            require(root.optString("sourceSha256").equals(sourceSha256, ignoreCase = true))
            require(root.optString("importedName") == importedName)
            UsbTunerStudioProfile.fromJson(root.getJSONObject("profile"))
        }.getOrNull()
        PerformanceMetrics.timingUs("tunerCompiledProfileDecode", System.nanoTime() - decodeStartedNs)
        return decoded?.also {
            compiledProfileHits.incrementAndGet()
            PerformanceMetrics.increment("tunerProfileCompiledHits")
            lastProfileLoadStatus = "compiled_profile_hit"
        }
    }

    private fun persistCompiledProfile(
        context: Context,
        sourceSha256: String,
        importedName: String,
        profile: UsbTunerStudioProfile
    ): Boolean {
        val totalStartedNs = System.nanoTime()
        val persisted = runCatching {
            val encodeStartedNs = System.nanoTime()
            val profileJson = profile.toJson()
            PerformanceMetrics.timingUs("tunerCompiledProfileEncode", System.nanoTime() - encodeStartedNs)

            val serializeStartedNs = System.nanoTime()
            val raw = JSONObject()
                .put("schema", COMPILED_PROFILE_SCHEMA)
                .put("sourceSha256", sourceSha256)
                .put("importedName", importedName)
                .put("profileFingerprint", profile.tuneProfileFingerprint())
                .put("profile", profileJson)
                .toString()
            PerformanceMetrics.timingUs("tunerCompiledProfileSerialize", System.nanoTime() - serializeStartedNs)
            if (raw.length > MAX_COMPILED_PROFILE_CHARS) return@runCatching false

            val writeStartedNs = System.nanoTime()
            val result = atomicWrite(compiledProfileFile(context), raw)
            PerformanceMetrics.timingUs("tunerCompiledProfileWrite", System.nanoTime() - writeStartedNs)
            result
        }.getOrDefault(false)
        PerformanceMetrics.timingUs("tunerCompiledProfilePersistTotal", System.nanoTime() - totalStartedNs)
        return persisted
    }

    private fun currentSourceMatches(
        context: Context,
        sourceSha256: String,
        expectedImportedName: String
    ): Boolean {
        val startedNs = System.nanoTime()
        val matches = runCatching {
            if (importedName(context) != expectedImportedName) return@runCatching false
            val ini = sourceIniFile(context)
            if (!ini.isFile || ini.length() <= 0L || ini.length() > MAX_INI_CHARS) return@runCatching false
            val source = ini.readText(Charsets.UTF_8)
            source.isNotBlank() && sha256(source).equals(sourceSha256, ignoreCase = true)
        }.getOrDefault(false)
        PerformanceMetrics.timingUs("tunerCompiledProfileAuthorityCheck", System.nanoTime() - startedNs)
        return matches
    }

    /**
     * Persists the derived parsed-profile accelerator only after the full permanent workspace has
     * crossed the WebView injection boundary. This deliberately prevents its large JSON object graph
     * from overlapping cold workspace construction/serialization on memory-constrained tablets.
     */
    fun persistDeferredCompiledProfileAsync(context: Context, candidateId: Long?) {
        val waitStartedNs = System.nanoTime()
        val pending = synchronized(profileStateLock) {
            PerformanceMetrics.timingUs("tunerProfileStateLockWait", System.nanoTime() - waitStartedNs)
            if (candidateId == null) {
                PerformanceMetrics.increment("tunerCompiledProfileInjectionGateRejected")
                return
            }
            val next = deferredCompiledProfile
            if (next == null || next.candidateId != candidateId) {
                PerformanceMetrics.increment("tunerCompiledProfileInjectionGateRejected")
                return
            }
            // Eligibility belongs to the exact candidate paired with the serialized workspace that
            // completed injection. An older callback can never unlock a newer global candidate.
            deferredCompiledProfileEligible = next
            if (compiledProfilePersistInFlight) {
                PerformanceMetrics.increment("tunerCompiledProfileDeferredWhileBusy")
                return
            }
            compiledProfilePersistInFlight = true
            next
        }
        launchDeferredCompiledProfilePersistence(context.applicationContext, pending)
    }

    private fun launchDeferredCompiledProfilePersistence(
        context: Context,
        pending: DeferredCompiledProfile
    ) {
        PerformanceMetrics.increment("tunerCompiledProfileAsyncQueued")
        Thread({
            val persistenceResult = synchronized(heavyWorkLock) {
                val stillAuthoritative = currentSourceMatches(
                    context.applicationContext,
                    pending.sourceSha256,
                    pending.importedName
                )
                val persisted = if (stillAuthoritative) {
                    persistCompiledProfile(
                        context.applicationContext,
                        pending.sourceSha256,
                        pending.importedName,
                        pending.profile
                    )
                } else {
                    PerformanceMetrics.increment("tunerCompiledProfileAsyncStale")
                    false
                }
                stillAuthoritative to persisted
            }
            val stillAuthoritative = persistenceResult.first
            val persisted = persistenceResult.second
            val waitStartedNs = System.nanoTime()
            val retry = synchronized(profileStateLock) {
                PerformanceMetrics.timingUs("tunerProfileStateLockWait", System.nanoTime() - waitStartedNs)
                compiledProfilePersistInFlight = false
                val current = deferredCompiledProfile
                if (current != null &&
                    current.candidateId == pending.candidateId &&
                    current.sourceSha256.equals(pending.sourceSha256, ignoreCase = true) &&
                    current.importedName == pending.importedName &&
                    current.profile === pending.profile
                ) {
                    deferredCompiledProfile = null
                    if (deferredCompiledProfileEligible === pending) {
                        deferredCompiledProfileEligible = null
                    }
                }
                val next = deferredCompiledProfile
                if (next != null && deferredCompiledProfileEligible === next) {
                    // A busy post-injection callback already gated this exact newer candidate. Reserve
                    // the worker slot before leaving the lock so no third caller can queue it twice.
                    compiledProfilePersistInFlight = true
                    next
                } else {
                    null
                }
            }
            if (stillAuthoritative) {
                PerformanceMetrics.increment(
                    if (persisted) "tunerCompiledProfileAsyncSuccess" else "tunerCompiledProfileAsyncFailure"
                )
            }
            if (retry != null) {
                launchDeferredCompiledProfilePersistence(context.applicationContext, retry)
            }
        }, "EpicDash-TunerProfilePersist").apply { isDaemon = true }.start()
    }

    private fun profileForSource(
        context: Context,
        source: String,
        importedName: String,
        sourceSha256: String
    ): UsbTunerStudioProfile {
        val waitStartedNs = System.nanoTime()
        return synchronized(profileStateLock) {
            PerformanceMetrics.timingUs("tunerProfileStateLockWait", System.nanoTime() - waitStartedNs)
            cachedProfile?.takeIf {
                cachedSourceSha256.equals(sourceSha256, ignoreCase = true) && cachedImportedName == importedName
            }?.let {
                lastProfileLoadStatus = "process_profile_hit"
                PerformanceMetrics.increment("tunerProfileProcessHits")
                return@synchronized it
            }

            val compiled = loadCompiledProfile(context, sourceSha256, importedName)
            val profile = compiled ?: run {
                val parseStartedNs = System.nanoTime()
                UsbTunerStudioProfileParser.parse(source, importedName).also {
                    PerformanceMetrics.timingUs("tunerProfileIniParse", System.nanoTime() - parseStartedNs)
                    PerformanceMetrics.increment("tunerProfileIniParses")
                    lastProfileLoadStatus = "ini_parsed"
                    deferredCompiledProfile = DeferredCompiledProfile(
                        candidateId = compiledProfileCandidateSequence.incrementAndGet(),
                        sourceSha256 = sourceSha256,
                        importedName = importedName,
                        profile = it
                    )
                    deferredCompiledProfileEligible = null
                    PerformanceMetrics.increment("tunerCompiledProfileDeferred")
                }
            }
            cachedSourceSha256 = sourceSha256
            cachedImportedName = importedName
            cachedProfile = profile
            profile
        }
    }

    private fun readSnapshotForProfile(context: Context, profile: UsbTunerStudioProfile): TuneSnapshot? {
        val totalStartedNs = System.nanoTime()
        try {
            val file = snapshotFile(context)
            val readStartedNs = System.nanoTime()
            val raw = runCatching { file.takeIf { it.isFile }?.readText(Charsets.UTF_8).orEmpty() }
                .getOrDefault("")
            PerformanceMetrics.timingUs("tunerSnapshotRead", System.nanoTime() - readStartedNs)
            if (raw.isBlank() || raw.length > MAX_SNAPSHOT_CHARS) {
                lastLoadStatus = if (raw.isBlank()) "no_snapshot" else "snapshot_too_large"
                return null
            }
            val decodeStartedNs = System.nanoTime()
            val snapshot = runCatching { TuneSnapshot.fromBackupJson(JSONObject(raw)) }.getOrNull()
            PerformanceMetrics.timingUs("tunerSnapshotDecode", System.nanoTime() - decodeStartedNs)
            if (snapshot == null) {
                lastLoadStatus = "snapshot_invalid"
                return null
            }
            val verifyStartedNs = System.nanoTime()
            val profileFingerprint = profile.tuneProfileFingerprint()
            val matches = snapshot.ecuSignature == profile.signature &&
                snapshot.profileFingerprint.equals(profileFingerprint, ignoreCase = true)
            PerformanceMetrics.timingUs("tunerSnapshotVerify", System.nanoTime() - verifyStartedNs)
            if (!matches) {
                lastLoadStatus = "snapshot_profile_mismatch"
                return null
            }
            lastLoadStatus = "snapshot_loaded"
            return snapshot
        } finally {
            PerformanceMetrics.timingUs("tunerSnapshotLoadTotal", System.nanoTime() - totalStartedNs)
        }
    }

    private fun savedWorkspace(profile: UsbTunerStudioProfile, snapshot: TuneSnapshot): JSONObject {
        // Saved generation is historical metadata only. TuningWorkspaceBuilder requires a positive
        // current generation for its semantic decoder, so clone immutable bytes into a synthetic
        // decode-only generation and strip all write authority below.
        val decodeSnapshot = TuneSnapshot.create(
            ecuSignature = snapshot.ecuSignature,
            profileFingerprint = snapshot.profileFingerprint,
            pages = snapshot.pages,
            generation = 1L,
            capturedAtEpochMs = snapshot.capturedAtEpochMs
        )
        return TuningWorkspaceBuilder.build(profile, decodeSnapshot, 1L).toJson(includeArrayValues = true).also { workspace ->
            forceReadOnly(workspace)
            workspace.put("generation", 0L)
            workspace.put("capability", "SAVED_PROJECT")
            workspace.put("source", "SAVED_TUNE_SNAPSHOT")
            workspace.put("savedProject", true)
            workspace.put("definitionOnly", false)
            workspace.put("capturedAtEpochMs", snapshot.capturedAtEpochMs)
            workspace.put("tuneFingerprint", snapshot.fingerprint)
            workspace.put("permanentSnapshotBytes", snapshot.totalBytes)
        }
    }

    private fun bootstrapKey(sourceSha256: String, snapshot: TuneSnapshot?): String =
        sourceSha256 + "|" + (snapshot?.profileFingerprint ?: "definition") + "|" + (snapshot?.fingerprint ?: "none")

    /** Caller must hold profileStateLock. Returns only the lightweight ID associated with this profile. */
    private fun deferredCandidateIdForProfileLocked(profile: UsbTunerStudioProfile): Long? =
        deferredCompiledProfile?.takeIf { it.profile === profile }?.candidateId

    /** Caller must hold bootstrapCacheLock. Serializes once and installs the exact immutable cache payload. */
    private fun cacheBootstrapLocked(
        key: String,
        json: JSONObject,
        compiledProfileCandidateId: Long?
    ): BootstrapWorkspacePayload {
        val serializeStartedNs = System.nanoTime()
        val serialized = json.toString()
        PerformanceMetrics.timingUs("tunerBootstrapCacheSerialize", System.nanoTime() - serializeStartedNs)
        cachedBootstrapKey = key
        cachedBootstrapJson = serialized
        cachedBootstrapCandidateId = compiledProfileCandidateId
        return BootstrapWorkspacePayload(serialized, compiledProfileCandidateId)
    }

    private fun cachedBootstrapPayloadLocked(currentCandidateId: Long?): BootstrapWorkspacePayload {
        if (cachedBootstrapCandidateId != null && cachedBootstrapCandidateId != currentCandidateId) {
            cachedBootstrapCandidateId = null
        }
        return BootstrapWorkspacePayload(cachedBootstrapJson, cachedBootstrapCandidateId)
    }


    /**
     * Commits a fully assembled native TuneSnapshot. This is intentionally synchronous with the
     * authoritative read that produced the snapshot: once this returns true, process death,
     * Activity recreation, minimizing, or reboot cannot remove the tune state.
     *
     * Unit tests can construct readers without an Android Application; in that environment the
     * persistence hook is deliberately inert rather than changing tuning semantics.
     */
    fun persistVerifiedSnapshot(snapshot: TuneSnapshot): Boolean {
        val context = appContext ?: return true
        val raw = runCatching { snapshot.toBackupJson().toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= MAX_SNAPSHOT_CHARS }
            ?: run {
                lastPersistStatus = "snapshot_serialize_failed"
                return false
            }
        val verified = runCatching { TuneSnapshot.fromBackupJson(JSONObject(raw)) }.getOrNull()
        if (verified == null ||
            !verified.fingerprint.equals(snapshot.fingerprint, ignoreCase = true) ||
            !verified.profileFingerprint.equals(snapshot.profileFingerprint, ignoreCase = true) ||
            verified.totalBytes != snapshot.totalBytes
        ) {
            lastPersistStatus = "snapshot_self_verification_failed"
            return false
        }
        if (!atomicWrite(snapshotFile(context), raw)) {
            lastPersistStatus = "snapshot_atomic_write_failed"
            return false
        }
        cleanupLegacySemanticState(context)
        lastPersistStatus = "snapshot_committed"
        synchronized(bootstrapCacheLock) {
            cachedBootstrapKey = ""
            cachedBootstrapJson = ""
            cachedBootstrapCandidateId = null
        }
        PerformanceMetrics.increment("tunerOfflineProjectionDeferred")
        TunerRecoveryBundleStore.mirrorAsync(context)
        return true
    }

    fun loadSnapshotForProfile(context: Context, profile: UsbTunerStudioProfile): TuneSnapshot? =
        readSnapshotForProfile(context, profile)

    /**
     * Builds the disconnected Tuner directly from the authoritative INI plus the permanent native
     * TuneSnapshot and returns the final immutable payload that is also retained in the process
     * cache. The existing production TuningWorkspaceBuilder remains the sole semantic decoder.
     * A saved projection is always forced read-only before it reaches the WebView.
     *
     * The exact INI remains authoritative. A process-memory projection and SHA-validated compiled
     * profile only avoid repeating the ~6-10 second INI parse after Activity recreation/minimize.
     */
    fun bootstrapWorkspacePayload(context: Context): BootstrapWorkspacePayload {
        val startedMs = SystemClock.elapsedRealtime()
        val totalStartedNs = System.nanoTime()
        val result = runCatching {
            val ini = sourceIniFile(context)
            if (!ini.isFile || ini.length() <= 0L || ini.length() > MAX_INI_CHARS) {
                lastProjectionStatus = "no_authoritative_ini"
                return@runCatching BootstrapWorkspacePayload(
                    JSONObject()
                        .put("status", "not_ready")
                        .put("capability", "READ_ONLY")
                        .put("reason", "Import mainController.ini to create a permanent Tuner project")
                        .toString(),
                    null
                )
            }
            val readStartedNs = System.nanoTime()
            val source = ini.readText(Charsets.UTF_8)
            PerformanceMetrics.timingUs("tunerBootstrapIniRead", System.nanoTime() - readStartedNs)
            if (source.isBlank() || source.length > MAX_INI_CHARS) {
                lastProjectionStatus = "authoritative_ini_invalid"
                return@runCatching BootstrapWorkspacePayload(
                    JSONObject()
                        .put("status", "not_ready")
                        .put("capability", "READ_ONLY")
                        .put("reason", "Stored mainController.ini is invalid")
                        .toString(),
                    null
                )
            }
            val importedName = importedName(context)
            val hashStartedNs = System.nanoTime()
            val sourceSha = sha256(source)
            PerformanceMetrics.timingUs("tunerBootstrapIniHash", System.nanoTime() - hashStartedNs)
            val profileStartedNs = System.nanoTime()
            val profile = profileForSource(context, source, importedName, sourceSha)
            PerformanceMetrics.timingUs("tunerBootstrapProfileResolve", System.nanoTime() - profileStartedNs)
            val snapshotStartedNs = System.nanoTime()
            val snapshot = readSnapshotForProfile(context, profile)
            PerformanceMetrics.timingUs("tunerBootstrapSnapshotResolve", System.nanoTime() - snapshotStartedNs)
            val key = bootstrapKey(sourceSha, snapshot)

            val profileWaitStartedNs = System.nanoTime()
            val candidateId = synchronized(profileStateLock) {
                PerformanceMetrics.timingUs("tunerProfileStateLockWait", System.nanoTime() - profileWaitStartedNs)
                deferredCandidateIdForProfileLocked(profile)
            }
            val cacheWasReadyBeforeWait = cachedBootstrapKey == key && cachedBootstrapJson.isNotBlank()
            val waitStartedNs = System.nanoTime()
            synchronized(bootstrapCacheLock) {
                PerformanceMetrics.timingUs("tunerBootstrapSingleFlightWait", System.nanoTime() - waitStartedNs)
                if (cachedBootstrapKey == key && cachedBootstrapJson.isNotBlank()) {
                    if (!cacheWasReadyBeforeWait) PerformanceMetrics.increment("tunerBootstrapSingleFlightJoins")
                    bootstrapCacheHits.incrementAndGet()
                    PerformanceMetrics.increment("tunerBootstrapProcessHits")
                    lastProjectionStatus = "process_bootstrap_hit"
                    return@synchronized cachedBootstrapPayloadLocked(candidateId)
                }
                // 1207: this expensive saved-project projection exists only on an explicit offline
                // request. It remains single-flight here, but is never primed by a live TuneSnapshot.
                synchronized(heavyWorkLock) {
                    if (cachedBootstrapKey == key && cachedBootstrapJson.isNotBlank()) {
                        bootstrapCacheHits.incrementAndGet()
                        PerformanceMetrics.increment("tunerBootstrapProcessHits")
                        lastProjectionStatus = "process_bootstrap_hit"
                        return@synchronized cachedBootstrapPayloadLocked(candidateId)
                    }
                    PerformanceMetrics.increment("tunerBootstrapBuilds")
                    val buildStartedNs = System.nanoTime()
                    val json = if (snapshot == null) {
                        TuningIniDefinitionBuilder.build(profile)
                    } else {
                        savedWorkspace(profile, snapshot)
                    }
                    PerformanceMetrics.timingUs("tunerWorkspaceProjectionBuild", System.nanoTime() - buildStartedNs)
                    lastProjectionStatus = if (snapshot == null) "ini_structure_only" else "saved_snapshot_projected"
                    cacheBootstrapLocked(key, json, candidateId)
                }
            }
        }.getOrElse { error ->
            lastProjectionStatus = "projection_failed:${error.message ?: error.javaClass.simpleName}"
            BootstrapWorkspacePayload(
                JSONObject()
                    .put("status", "error")
                    .put("capability", "READ_ONLY")
                    .put("reason", (error.message ?: "Unable to open permanent Tuner project").take(240))
                    .toString(),
                null
            )
        }
        lastProjectionElapsedMs = (SystemClock.elapsedRealtime() - startedMs).coerceAtLeast(0L)
        PerformanceMetrics.timingUs("tunerPermanentBootstrapTotal", System.nanoTime() - totalStartedNs)
        return result
    }

    /** Compatibility wrapper for native callers that still need a JSONObject. */
    fun bootstrapWorkspaceJson(context: Context): JSONObject {
        val decodeStartedNs = System.nanoTime()
        val result = runCatching { JSONObject(bootstrapWorkspacePayload(context).json) }.getOrElse { error ->
            JSONObject()
                .put("status", "error")
                .put("capability", "READ_ONLY")
                .put("reason", (error.message ?: "Unable to decode permanent Tuner project").take(240))
        }
        PerformanceMetrics.timingUs("tunerBootstrapCompatibilityDecode", System.nanoTime() - decodeStartedNs)
        return result
    }

    private fun forceReadOnly(workspace: JSONObject) {
        listOf("scalars", "arrays", "bitFields", "tables", "curves").forEach { key ->
            val items = workspace.optJSONArray(key) ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                item.put("writeAllowed", false)
                item.put("writeBlockReason", "ECU is offline; values are from the last complete native TuneSnapshot.")
                if (key == "scalars" || key == "arrays" || key == "bitFields") {
                    item.put("valueAvailable", true)
                }
            }
        }
        val compatibility = workspace.optJSONObject("iniCompatibility")
        compatibility?.put("reason", "Current INI decoded against the last complete native TuneSnapshot; ECU is offline")
    }

    fun diagnostics(context: Context): JSONObject {
        val file = snapshotFile(context)
        val valid = runCatching {
            file.isFile && TuneSnapshot.fromBackupJson(JSONObject(file.readText(Charsets.UTF_8))) != null
        }.getOrDefault(false)
        return JSONObject()
            .put("nativeSnapshotPersistence", true)
            .put("snapshotFile", SNAPSHOT_FILE_NAME)
            .put("snapshotAvailable", file.isFile)
            .put("snapshotValid", valid)
            .put("snapshotBytes", file.takeIf { it.isFile }?.length() ?: 0L)
            .put("compiledProfileFile", COMPILED_PROFILE_FILE_NAME)
            .put("compiledProfileAvailable", compiledProfileFile(context).isFile)
            .put("lastPersistStatus", lastPersistStatus)
            .put("lastLoadStatus", lastLoadStatus)
            .put("lastProfileLoadStatus", lastProfileLoadStatus)
            .put("lastProjectionStatus", lastProjectionStatus)
            .put("lastProjectionElapsedMs", lastProjectionElapsedMs)
            .put("bootstrapCacheHits", bootstrapCacheHits.get())
            .put("compiledProfileHits", compiledProfileHits.get())
    }
}
