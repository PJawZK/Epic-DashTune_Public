package com.buttonbox.ble

import java.util.Locale
import org.json.JSONObject

/**
 * W5/T6 protocol boundary for one profile-derived page burn.
 *
 * This is deliberately not a generic command formatter. The only accepted current profile shape is
 * exactly B%2i and the page identifier always comes from the currently imported INI.
 */
internal object T6BurnProtocol {
    const val SUPPORTED_BURN_COMMAND = "B%2i"
    const val BURN_OK_RESPONSE = 0x04

    fun buildBody(page: UsbTunePage): ByteArray {
        require(page.burnCommand == SUPPORTED_BURN_COMMAND) {
            "T6 unsupported burn command '\${page.burnCommand}' for page \${page.pageNumber}"
        }
        require(page.identifier in 0..0xffff) { "T6 burn page identifier is outside U16 range" }
        return byteArrayOf(
            'B'.code.toByte(),
            (page.identifier and 0xff).toByte(),
            ((page.identifier ushr 8) and 0xff).toByte()
        )
    }

    fun requireAcceptedAck(body: ByteArray) {
        require(body.size == 1 && (body[0].toInt() and 0xff) == BURN_OK_RESPONSE) {
            "T6 burn acknowledgement is not TS_RESPONSE_BURN_OK"
        }
    }
}

internal enum class T6FlashStatusFailure {
    INVALID_CONTEXT,
    GENERATION_MISMATCH,
    PROFILE_SIGNATURE_MISMATCH,
    PROFILE_FINGERPRINT_MISMATCH,
    SAMPLE_TIME_INVALID,
    SAMPLE_STALE,
    MISSING_CHANNEL,
    AMBIGUOUS_CHANNEL,
    CHANNEL_DEFINITION_MISMATCH,
    CHANNEL_DECODE_FAILED,
    CHANNEL_VALUE_INVALID
}

internal class T6FlashStatusException(
    val reason: T6FlashStatusFailure,
    message: String
) : IllegalStateException(message)

internal data class T6FlashStatus(
    val context: TuningContext,
    val needFlashBurn: Boolean,
    val flashWritePending: Boolean,
    val flashWrites: Int,
    val flashWriteErrors: Int,
    val burnRequestCnt: Int,
    val tuneWriteId: Long,
    val bootFlashWriteId: Long,
    val bootConfigPrimaryStatus: Int,
    val bootConfigBackupStatus: Int,
    val definitionFingerprint: String
)

internal object T6FlashChannelPolicy {
    const val NEED_FLASH_BURN = "needFlashBurn"
    const val FLASH_PENDING = "flashWritePending"
    const val FLASH_WRITES = "flashWrites"
    const val FLASH_ERRORS = "flashWriteErrors"
    const val BURN_REQUEST_COUNT = "burnRequestCnt"
    const val TUNE_WRITE_ID = "tuneWriteId"
    const val BOOT_FLASH_WRITE_ID = "bootFlashWriteId"
    const val BOOT_PRIMARY_STATUS = "bootConfigPrimaryStatus"
    const val BOOT_BACKUP_STATUS = "bootConfigBackupStatus"
}

/**
 * W5-specific status resolver. Storage metadata always comes from the current imported profile.
 * No historical output offsets or bit positions are duplicated here.
 */
internal class T6FlashStatusResolver(
    private val maximumSampleAgeMs: Long
) {
    init {
        require(maximumSampleAgeMs > 0L) { "T6 maximum status sample age must be positive" }
    }

    fun resolve(
        profile: UsbTunerStudioProfile,
        context: TuningContext,
        sample: T3NativeOutputSample,
        nowElapsedMs: Long
    ): T6FlashStatus {
        if (context.sessionId <= 0L || context.generation < 0L || context.source != TuningDataSource.LIVE) {
            fail(T6FlashStatusFailure.INVALID_CONTEXT, "T6 flash status requires a positive LIVE session")
        }
        if (sample.generation != context.generation) {
            fail(T6FlashStatusFailure.GENERATION_MISMATCH, "T6 native output sample belongs to another USB generation")
        }
        if (profile.signature != context.ecuSignature) {
            fail(T6FlashStatusFailure.PROFILE_SIGNATURE_MISMATCH, "T6 imported profile signature differs from live ECU")
        }
        val profileFingerprint = profile.tuneProfileFingerprint()
        if (!context.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            fail(T6FlashStatusFailure.PROFILE_FINGERPRINT_MISMATCH, "T6 imported profile fingerprint differs from live tuning context")
        }
        if (nowElapsedMs < sample.capturedElapsedMs) {
            fail(T6FlashStatusFailure.SAMPLE_TIME_INVALID, "T6 native status sample timestamp is in the future")
        }
        if (nowElapsedMs - sample.capturedElapsedMs > maximumSampleAgeMs) {
            fail(T6FlashStatusFailure.SAMPLE_STALE, "T6 native flash status sample is stale")
        }

        val channels = linkedMapOf(
            "needFlashBurn" to exactChannel(profile, T6FlashChannelPolicy.NEED_FLASH_BURN, allowBits = true),
            "flashPending" to exactChannel(profile, T6FlashChannelPolicy.FLASH_PENDING, allowBits = true),
            "flashWrites" to exactChannel(profile, T6FlashChannelPolicy.FLASH_WRITES),
            "flashErrors" to exactChannel(profile, T6FlashChannelPolicy.FLASH_ERRORS),
            "burnRequests" to exactChannel(profile, T6FlashChannelPolicy.BURN_REQUEST_COUNT),
            "tuneWriteId" to exactChannel(profile, T6FlashChannelPolicy.TUNE_WRITE_ID),
            "bootFlashWriteId" to exactChannel(profile, T6FlashChannelPolicy.BOOT_FLASH_WRITE_ID),
            "bootPrimaryStatus" to exactChannel(profile, T6FlashChannelPolicy.BOOT_PRIMARY_STATUS),
            "bootBackupStatus" to exactChannel(profile, T6FlashChannelPolicy.BOOT_BACKUP_STATUS)
        )

        val block = sample.bytes()
        if (block.size < profile.outputBlockSize) {
            fail(
                T6FlashStatusFailure.CHANNEL_DECODE_FAILED,
                "T6 native status sample is \${block.size} bytes but current INI requires \${profile.outputBlockSize}"
            )
        }

        return T6FlashStatus(
            context = context,
            needFlashBurn = decodeBinary(channels.getValue("needFlashBurn"), block),
            flashWritePending = decodeBinary(channels.getValue("flashPending"), block),
            flashWrites = decodeUnsignedInteger(channels.getValue("flashWrites"), block, 0xffL).toInt(),
            flashWriteErrors = decodeUnsignedInteger(channels.getValue("flashErrors"), block, 0xffL).toInt(),
            burnRequestCnt = decodeUnsignedInteger(channels.getValue("burnRequests"), block, 0xffL).toInt(),
            tuneWriteId = decodeUnsignedInteger(channels.getValue("tuneWriteId"), block, 0xffff_ffffL),
            bootFlashWriteId = decodeUnsignedInteger(channels.getValue("bootFlashWriteId"), block, 0xffff_ffffL),
            bootConfigPrimaryStatus = decodeUnsignedInteger(channels.getValue("bootPrimaryStatus"), block, 0xffL).toInt(),
            bootConfigBackupStatus = decodeUnsignedInteger(channels.getValue("bootBackupStatus"), block, 0xffL).toInt(),
            definitionFingerprint = definitionFingerprint(profile, channels)
        )
    }

    private fun exactChannel(
        profile: UsbTunerStudioProfile,
        name: String,
        allowBits: Boolean = false
    ): UsbOutputChannel {
        val matches = profile.channels.filter { it.name.equals(name, ignoreCase = true) }
        if (matches.isEmpty()) {
            fail(T6FlashStatusFailure.MISSING_CHANNEL, "T6 required flash-status channel '$name' is missing")
        }
        if (matches.size != 1) {
            fail(T6FlashStatusFailure.AMBIGUOUS_CHANNEL, "T6 required flash-status channel '$name' is ambiguous")
        }
        val channel = matches.single()
        val scalar = channel.kind.equals("scalar", ignoreCase = true)
        val bits = allowBits && channel.kind.equals("bits", ignoreCase = true)
        val bitCapacity = channel.byteSize * 8
        val validBits = bits && channel.bitStart >= 0 && channel.bitEnd >= channel.bitStart && channel.bitEnd < bitCapacity
        val validScalar = scalar && channel.scale.isFinite() && channel.scale != 0.0 && channel.translate.isFinite()
        if (channel.byteSize <= 0 || channel.offset < 0 || channel.offset + channel.byteSize > profile.outputBlockSize ||
            (!validScalar && !validBits)
        ) {
            fail(
                T6FlashStatusFailure.CHANNEL_DEFINITION_MISMATCH,
                "T6 flash-status channel '\${channel.name}' has an unusable current-profile definition"
            )
        }
        return channel
    }

    private fun decodeBinary(channel: UsbOutputChannel, block: ByteArray): Boolean {
        val value = decodeFinite(channel, block)
        if (value != 0.0 && value != 1.0) {
            fail(T6FlashStatusFailure.CHANNEL_VALUE_INVALID, "T6 channel '\${channel.name}' is not binary")
        }
        return value == 1.0
    }

    private fun decodeUnsignedInteger(channel: UsbOutputChannel, block: ByteArray, maximum: Long): Long {
        val value = decodeFinite(channel, block)
        if (value < 0.0 || value > maximum.toDouble() || value % 1.0 != 0.0) {
            fail(T6FlashStatusFailure.CHANNEL_VALUE_INVALID, "T6 channel '\${channel.name}' is not a valid unsigned integer")
        }
        return value.toLong()
    }

    private fun decodeFinite(channel: UsbOutputChannel, block: ByteArray): Double {
        val value = channel.decode(block)
            ?: fail(T6FlashStatusFailure.CHANNEL_DECODE_FAILED, "T6 channel '\${channel.name}' could not be decoded")
        if (!value.isFinite()) {
            fail(T6FlashStatusFailure.CHANNEL_VALUE_INVALID, "T6 channel '\${channel.name}' is not finite")
        }
        return value
    }

    private fun definitionFingerprint(
        profile: UsbTunerStudioProfile,
        channels: LinkedHashMap<String, UsbOutputChannel>
    ): String {
        val canonical = buildString {
            append("EpicDashT6FlashStatus/v1|")
            append(profile.signature).append('|').append(profile.outputBlockSize)
            channels.forEach { (role, channel) ->
                append('|').append(role)
                append('|').append(channel.name)
                append('|').append(channel.kind.lowercase(Locale.US))
                append('|').append(channel.dataType.uppercase(Locale.US))
                append('|').append(channel.offset)
                append('|').append(channel.byteSize)
                append('|').append(channel.bitStart)
                append('|').append(channel.bitEnd)
                append('|').append(java.lang.Double.doubleToLongBits(channel.scale))
                append('|').append(java.lang.Double.doubleToLongBits(channel.translate))
            }
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun fail(reason: T6FlashStatusFailure, message: String): Nothing =
        throw T6FlashStatusException(reason, message)
}

internal data class T6BurnProgress(
    val baseline: T6FlashStatus,
    val requestedTuneWriteId: Long,
    val expectedBurnRequestCnt: Int,
    val requestObservedPending: Boolean,
    val latest: T6FlashStatus
)

/**
 * Pure evidence policy used by tests and, later, the manager-owned USB transaction.
 *
 * A response-code-4 acknowledgement is intentionally absent from this policy: acknowledgement is
 * transport evidence only. Persistence scheduling/completion requires status progression.
 */
internal object T6BurnEvidencePolicy {
    fun requireReadyForBurn(status: T6FlashStatus) {
        require(!status.flashWritePending) { "T6 cannot start while a flash write is already pending" }
        require(status.flashWriteErrors >= 0) { "T6 flash error baseline is invalid" }
    }

    fun requestHasStarted(baseline: T6FlashStatus, current: T6FlashStatus): Boolean {
        requireSameSession(baseline, current)
        require(current.definitionFingerprint == baseline.definitionFingerprint) {
            "T6 flash-status schema changed during burn request"
        }
        require(current.flashWriteErrors == baseline.flashWriteErrors) {
            "T6 flash error counter changed during burn request"
        }

        val sameWriteId = current.tuneWriteId == baseline.tuneWriteId
        val sameBurnCount = current.burnRequestCnt == baseline.burnRequestCnt
        if (sameWriteId && sameBurnCount) return false

        require(current.tuneWriteId == nextU32(baseline.tuneWriteId)) {
            "T6 tuneWriteId did not advance exactly once"
        }
        require(current.burnRequestCnt == nextU8(baseline.burnRequestCnt)) {
            "T6 burnRequestCnt did not advance exactly once"
        }
        return true
    }

    fun observeRequest(
        baseline: T6FlashStatus,
        current: T6FlashStatus,
        pendingWasObserved: Boolean = false
    ): T6BurnProgress {
        require(requestHasStarted(baseline, current)) {
            "T6 burn request counters have not advanced yet"
        }

        val expectedWriteId = nextU32(baseline.tuneWriteId)
        val expectedBurnCount = nextU8(baseline.burnRequestCnt)

        return T6BurnProgress(
            baseline = baseline,
            requestedTuneWriteId = expectedWriteId,
            expectedBurnRequestCnt = expectedBurnCount,
            requestObservedPending = pendingWasObserved || current.flashWritePending,
            latest = current
        )
    }

    fun observeCompletion(progress: T6BurnProgress, current: T6FlashStatus): T6BurnProgress {
        requireSameSession(progress.baseline, current)
        require(current.definitionFingerprint == progress.baseline.definitionFingerprint) {
            "T6 flash-status schema changed before completion"
        }
        require(current.tuneWriteId == progress.requestedTuneWriteId) {
            "T6 tuneWriteId changed again before completion"
        }
        require(current.burnRequestCnt == progress.expectedBurnRequestCnt) {
            "T6 burnRequestCnt changed again before completion"
        }
        require(current.flashWriteErrors == progress.baseline.flashWriteErrors) {
            "T6 flash error counter changed before completion"
        }
        require(!current.flashWritePending) {
            "T6 flash write is still pending"
        }

        return progress.copy(
            requestObservedPending = progress.requestObservedPending || current.flashWritePending,
            latest = current
        )
    }

    fun requirePowerCyclePersistence(
        progress: T6BurnProgress,
        bootStatus: T6FlashStatus,
        persistedSnapshot: TuneSnapshot,
        expectedTuneFingerprint: String
    ) {
        require(bootStatus.context.generation != progress.baseline.context.generation) {
            "T6 persistence proof requires a new USB generation after power cycle"
        }
        require(bootStatus.context.ecuSignature == progress.baseline.context.ecuSignature) {
            "T6 ECU signature changed across power cycle"
        }
        require(bootStatus.context.profileFingerprint.equals(progress.baseline.context.profileFingerprint, ignoreCase = true)) {
            "T6 profile identity changed across power cycle"
        }
        require(!bootStatus.flashWritePending) {
            "T6 flash write is pending after power cycle"
        }
        requirePostBootWriteIdentity(
            bootStatus = bootStatus,
            expectedWriteId = progress.requestedTuneWriteId,
            label = "requested persistent write"
        )
        require(persistedSnapshot.generation == bootStatus.context.generation) {
            "T6 persisted TuneSnapshot belongs to another USB generation"
        }
        require(persistedSnapshot.ecuSignature == bootStatus.context.ecuSignature) {
            "T6 persisted TuneSnapshot ECU signature changed"
        }
        require(persistedSnapshot.profileFingerprint.equals(bootStatus.context.profileFingerprint, ignoreCase = true)) {
            "T6 persisted TuneSnapshot profile identity changed"
        }
        require(persistedSnapshot.fingerprint.equals(expectedTuneFingerprint, ignoreCase = true)) {
            "T6 persisted TuneSnapshot does not match the intended tune fingerprint"
        }
    }

    fun requirePowerCyclePersistenceFromRecovery(
        recovery: T6PersistentBurnRecoveryMarker,
        bootStatus: T6FlashStatus,
        persistedSnapshot: TuneSnapshot
    ) {
        require(recovery.phase == T6RecoveryPhase.AWAITING_POWER_CYCLE) {
            "T6 durable recovery marker is not awaiting power-cycle verification"
        }
        val requestedWriteId = recovery.requestedTuneWriteId
            ?: throw IllegalArgumentException("T6 durable recovery marker lacks requested tuneWriteId")
        require(recovery.requestedBurnRequestCnt != null) {
            "T6 durable recovery marker lacks requested burnRequestCnt"
        }
        require(bootStatus.context.generation != recovery.sourceGeneration) {
            "T6 persistence proof requires a new USB generation after power cycle"
        }
        require(bootStatus.context.ecuSignature == recovery.ecuSignature) {
            "T6 ECU signature changed across power cycle"
        }
        require(bootStatus.context.profileFingerprint.equals(recovery.profileFingerprint, ignoreCase = true)) {
            "T6 profile identity changed across power cycle"
        }
        require(bootStatus.definitionFingerprint == recovery.flashStatusDefinitionFingerprint) {
            "T6 flash-status schema changed across power cycle"
        }
        require(!bootStatus.flashWritePending) {
            "T6 flash write is pending after power cycle"
        }
        requirePostBootWriteIdentity(
            bootStatus = bootStatus,
            expectedWriteId = requestedWriteId,
            label = "durable requested persistence"
        )
        require(persistedSnapshot.generation == bootStatus.context.generation) {
            "T6 persisted TuneSnapshot belongs to another USB generation"
        }
        require(persistedSnapshot.ecuSignature == recovery.ecuSignature) {
            "T6 persisted TuneSnapshot ECU signature changed"
        }
        require(persistedSnapshot.profileFingerprint.equals(recovery.profileFingerprint, ignoreCase = true)) {
            "T6 persisted TuneSnapshot profile identity changed"
        }
        require(persistedSnapshot.fingerprint.equals(recovery.candidateTuneFingerprint, ignoreCase = true)) {
            "T6 persisted TuneSnapshot does not match the durable intended candidate fingerprint"
        }
    }

    fun requireBaselinePowerCycleRestored(
        recovery: T6PersistentBurnRecoveryMarker,
        bootStatus: T6FlashStatus,
        restoredSnapshot: TuneSnapshot
    ) {
        require(recovery.phase == T6RecoveryPhase.BASELINE_AWAITING_POWER_CYCLE) {
            "T6 recovery marker is not awaiting baseline power-cycle verification"
        }
        val restoreGeneration = recovery.restoreSourceGeneration
            ?: throw IllegalArgumentException("T6 recovery marker lacks restore source generation")
        val restoreWriteId = recovery.restoreRequestedTuneWriteId
            ?: throw IllegalArgumentException("T6 recovery marker lacks baseline restore tuneWriteId")
        require(recovery.restoreRequestedBurnRequestCnt != null) {
            "T6 recovery marker lacks baseline restore burnRequestCnt"
        }
        require(bootStatus.context.generation != restoreGeneration) {
            "T6 baseline persistence proof requires a new USB generation"
        }
        require(bootStatus.context.ecuSignature == recovery.ecuSignature) {
            "T6 ECU signature changed during baseline restoration"
        }
        require(bootStatus.context.profileFingerprint.equals(recovery.profileFingerprint, ignoreCase = true)) {
            "T6 profile identity changed during baseline restoration"
        }
        require(bootStatus.definitionFingerprint == recovery.flashStatusDefinitionFingerprint) {
            "T6 flash-status schema changed during baseline restoration"
        }
        require(!bootStatus.flashWritePending) {
            "T6 flash write is pending after baseline restoration power cycle"
        }
        requirePostBootWriteIdentity(
            bootStatus = bootStatus,
            expectedWriteId = restoreWriteId,
            label = "baseline restore burn"
        )
        require(restoredSnapshot.generation == bootStatus.context.generation) {
            "T6 restored TuneSnapshot belongs to another USB generation"
        }
        require(restoredSnapshot.ecuSignature == recovery.ecuSignature) {
            "T6 restored TuneSnapshot ECU signature changed"
        }
        require(restoredSnapshot.profileFingerprint.equals(recovery.profileFingerprint, ignoreCase = true)) {
            "T6 restored TuneSnapshot profile identity changed"
        }
        require(restoredSnapshot.fingerprint.equals(recovery.baselineTuneFingerprint, ignoreCase = true)) {
            "T6 final TuneSnapshot does not match the exact original baseline fingerprint"
        }
    }

    private fun requirePostBootWriteIdentity(
        bootStatus: T6FlashStatus,
        expectedWriteId: Long,
        label: String
    ) {
        require(bootStatus.tuneWriteId == expectedWriteId) {
            "T6 post-boot tuneWriteId does not match the $label identity"
        }
        require(bootStatus.bootFlashWriteId == 0L || bootStatus.bootFlashWriteId == expectedWriteId) {
            "T6 nonzero bootFlashWriteId does not match the $label identity"
        }
    }

    private fun requireSameSession(first: T6FlashStatus, second: T6FlashStatus) {
        require(second.context.sessionId == first.context.sessionId) { "T6 USB session changed during burn" }
        require(second.context.generation == first.context.generation) { "T6 USB generation changed during burn" }
        require(second.context.ecuSignature == first.context.ecuSignature) { "T6 ECU signature changed during burn" }
        require(second.context.profileFingerprint.equals(first.context.profileFingerprint, ignoreCase = true)) {
            "T6 profile identity changed during burn"
        }
    }

    private fun nextU8(value: Int): Int = (value + 1) and 0xff
    private fun nextU32(value: Long): Long = (value + 1L) and 0xffff_ffffL
}


internal enum class T6RecoveryPhase {
    ARMED_FOR_BURN,
    AWAITING_POWER_CYCLE,
    PERSISTED_CANDIDATE_VERIFIED,
    BASELINE_RESTORE_ACTIVE,
    BASELINE_AWAITING_POWER_CYCLE
}

internal data class T6PersistentBurnRecoveryMarker(
    val phase: T6RecoveryPhase,
    val actionId: String,
    val previewId: String,
    val sourceGeneration: Long,
    val ecuSignature: String,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val candidateTuneFingerprint: String,
    val scalarName: String,
    val unit: String,
    val targetDefinitionFingerprint: String,
    val originalRawHex: String,
    val proposedRawHex: String,
    val originalValue: Double,
    val candidateEffectiveValue: Double,
    val pageNumber: Int,
    val pageIdentifier: Int,
    val burnCommand: String,
    val baselineTuneWriteId: Long,
    val baselineBurnRequestCnt: Int,
    val baselineFlashWriteErrors: Int,
    val requestedTuneWriteId: Long? = null,
    val requestedBurnRequestCnt: Int? = null,
    val restoreSourceGeneration: Long? = null,
    val restoreRequestedTuneWriteId: Long? = null,
    val restoreRequestedBurnRequestCnt: Int? = null,
    val flashStatusDefinitionFingerprint: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("phase", phase.name)
        .put("actionId", actionId)
        .put("previewId", previewId)
        .put("sourceGeneration", sourceGeneration)
        .put("ecuSignature", ecuSignature)
        .put("profileFingerprint", profileFingerprint)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("scalarName", scalarName)
        .put("unit", unit)
        .put("targetDefinitionFingerprint", targetDefinitionFingerprint)
        .put("originalRawHex", originalRawHex)
        .put("proposedRawHex", proposedRawHex)
        .put("originalValue", originalValue)
        .put("candidateEffectiveValue", candidateEffectiveValue)
        .put("pageNumber", pageNumber)
        .put("pageIdentifier", pageIdentifier)
        .put("burnCommand", burnCommand)
        .put("baselineTuneWriteId", baselineTuneWriteId)
        .put("baselineBurnRequestCnt", baselineBurnRequestCnt)
        .put("baselineFlashWriteErrors", baselineFlashWriteErrors)
        .put("requestedTuneWriteId", requestedTuneWriteId ?: JSONObject.NULL)
        .put("requestedBurnRequestCnt", requestedBurnRequestCnt ?: JSONObject.NULL)
        .put("restoreSourceGeneration", restoreSourceGeneration ?: JSONObject.NULL)
        .put("restoreRequestedTuneWriteId", restoreRequestedTuneWriteId ?: JSONObject.NULL)
        .put("restoreRequestedBurnRequestCnt", restoreRequestedBurnRequestCnt ?: JSONObject.NULL)
        .put("flashStatusDefinitionFingerprint", flashStatusDefinitionFingerprint)
        .put("createdAtEpochMs", createdAtEpochMs)
        .put("updatedAtEpochMs", updatedAtEpochMs)

    fun awaitingPowerCycle(progress: T6BurnProgress, nowEpochMs: Long): T6PersistentBurnRecoveryMarker {
        require(progress.baseline.tuneWriteId == baselineTuneWriteId) {
            "T6 recovery baseline tuneWriteId changed"
        }
        require(progress.baseline.burnRequestCnt == baselineBurnRequestCnt) {
            "T6 recovery baseline burnRequestCnt changed"
        }
        require(progress.baseline.flashWriteErrors == baselineFlashWriteErrors) {
            "T6 recovery baseline flash error count changed"
        }
        require(progress.latest.definitionFingerprint == flashStatusDefinitionFingerprint) {
            "T6 recovery flash-status schema changed"
        }
        return copy(
            phase = T6RecoveryPhase.AWAITING_POWER_CYCLE,
            requestedTuneWriteId = progress.requestedTuneWriteId,
            requestedBurnRequestCnt = progress.expectedBurnRequestCnt,
            updatedAtEpochMs = nowEpochMs
        )
    }

    fun persistedCandidateVerified(nowEpochMs: Long): T6PersistentBurnRecoveryMarker {
        require(phase == T6RecoveryPhase.AWAITING_POWER_CYCLE) {
            "T6 recovery marker is not awaiting candidate power-cycle verification"
        }
        require(requestedTuneWriteId != null && requestedBurnRequestCnt != null) {
            "T6 recovery marker lacks scheduled persistence identity"
        }
        return copy(
            phase = T6RecoveryPhase.PERSISTED_CANDIDATE_VERIFIED,
            updatedAtEpochMs = nowEpochMs
        )
    }

    fun baselineRestoreActive(currentGeneration: Long, nowEpochMs: Long): T6PersistentBurnRecoveryMarker {
        require(phase == T6RecoveryPhase.PERSISTED_CANDIDATE_VERIFIED) {
            "T6 recovery marker is not ready for persisted baseline restoration"
        }
        require(currentGeneration > 0L && currentGeneration != sourceGeneration) {
            "T6 baseline restore requires the verified post-candidate generation"
        }
        return copy(
            phase = T6RecoveryPhase.BASELINE_RESTORE_ACTIVE,
            restoreSourceGeneration = currentGeneration,
            restoreRequestedTuneWriteId = null,
            restoreRequestedBurnRequestCnt = null,
            updatedAtEpochMs = nowEpochMs
        )
    }

    fun baselineAwaitingPowerCycle(
        progress: T6BurnProgress,
        nowEpochMs: Long
    ): T6PersistentBurnRecoveryMarker {
        require(phase == T6RecoveryPhase.BASELINE_RESTORE_ACTIVE) {
            "T6 recovery marker is not in baseline restore phase"
        }
        require(restoreSourceGeneration == progress.baseline.context.generation) {
            "T6 baseline restore generation changed before burn completion"
        }
        require(progress.latest.definitionFingerprint == flashStatusDefinitionFingerprint) {
            "T6 baseline restore flash-status schema changed"
        }
        return copy(
            phase = T6RecoveryPhase.BASELINE_AWAITING_POWER_CYCLE,
            restoreRequestedTuneWriteId = progress.requestedTuneWriteId,
            restoreRequestedBurnRequestCnt = progress.expectedBurnRequestCnt,
            updatedAtEpochMs = nowEpochMs
        )
    }

    companion object {
        const val SCHEMA = "EpicDashT6PersistentBurnRecovery/v1"
        private val FP = Regex("[0-9a-fA-F]{64}")
        private val HEX = Regex("[0-9a-fA-F]+")

        fun fromJson(json: JSONObject): T6PersistentBurnRecoveryMarker {
            require(json.getString("schema") == SCHEMA) { "Unsupported T6 recovery marker schema" }
            val marker = T6PersistentBurnRecoveryMarker(
                phase = T6RecoveryPhase.valueOf(json.getString("phase")),
                actionId = json.getString("actionId"),
                previewId = json.getString("previewId"),
                sourceGeneration = json.getLong("sourceGeneration"),
                ecuSignature = json.getString("ecuSignature"),
                profileFingerprint = json.getString("profileFingerprint"),
                baselineTuneFingerprint = json.getString("baselineTuneFingerprint"),
                candidateTuneFingerprint = json.getString("candidateTuneFingerprint"),
                scalarName = json.getString("scalarName"),
                unit = json.optString("unit"),
                targetDefinitionFingerprint = json.getString("targetDefinitionFingerprint"),
                originalRawHex = json.getString("originalRawHex"),
                proposedRawHex = json.getString("proposedRawHex"),
                originalValue = json.getDouble("originalValue"),
                candidateEffectiveValue = json.getDouble("candidateEffectiveValue"),
                pageNumber = json.getInt("pageNumber"),
                pageIdentifier = json.getInt("pageIdentifier"),
                burnCommand = json.getString("burnCommand"),
                baselineTuneWriteId = json.getLong("baselineTuneWriteId"),
                baselineBurnRequestCnt = json.getInt("baselineBurnRequestCnt"),
                baselineFlashWriteErrors = json.getInt("baselineFlashWriteErrors"),
                requestedTuneWriteId = if (json.isNull("requestedTuneWriteId")) null else json.getLong("requestedTuneWriteId"),
                requestedBurnRequestCnt = if (json.isNull("requestedBurnRequestCnt")) null else json.getInt("requestedBurnRequestCnt"),
                restoreSourceGeneration = if (json.isNull("restoreSourceGeneration")) null else json.getLong("restoreSourceGeneration"),
                restoreRequestedTuneWriteId = if (json.isNull("restoreRequestedTuneWriteId")) null else json.getLong("restoreRequestedTuneWriteId"),
                restoreRequestedBurnRequestCnt = if (json.isNull("restoreRequestedBurnRequestCnt")) null else json.getInt("restoreRequestedBurnRequestCnt"),
                flashStatusDefinitionFingerprint = json.getString("flashStatusDefinitionFingerprint"),
                createdAtEpochMs = json.getLong("createdAtEpochMs"),
                updatedAtEpochMs = json.getLong("updatedAtEpochMs")
            )
            require(marker.actionId == T6PersistentBurnPolicy.ENTER_PERSISTENT_SCALAR_BURN_ACTION_ID) {
                "Invalid T6 recovery action ID"
            }
            require(marker.previewId.matches(FP)) { "Invalid T6 recovery preview ID" }
            require(marker.sourceGeneration > 0L) { "Invalid T6 recovery source generation" }
            require(marker.ecuSignature.isNotBlank()) { "Invalid T6 recovery ECU signature" }
            require(marker.profileFingerprint.matches(FP)) { "Invalid T6 recovery profile fingerprint" }
            require(marker.baselineTuneFingerprint.matches(FP) && marker.candidateTuneFingerprint.matches(FP)) {
                "Invalid T6 recovery tune identity"
            }
            require(marker.targetDefinitionFingerprint.matches(FP)) {
                "Invalid T6 recovery target definition fingerprint"
            }
            require(marker.originalRawHex.matches(HEX) && marker.proposedRawHex.matches(HEX) &&
                marker.originalRawHex.length == marker.proposedRawHex.length &&
                marker.originalRawHex.length in setOf(2, 4, 8)
            ) { "Invalid T6 recovery scalar bytes" }
            require(marker.originalValue.isFinite() && marker.candidateEffectiveValue.isFinite()) {
                "Invalid T6 recovery engineering values"
            }
            require(marker.pageNumber > 0 && marker.pageIdentifier in 0..0xffff) {
                "Invalid T6 recovery page identity"
            }
            require(marker.burnCommand == T6BurnProtocol.SUPPORTED_BURN_COMMAND) {
                "Invalid T6 recovery burn command"
            }
            require(marker.baselineTuneWriteId in 0..0xffff_ffffL) { "Invalid T6 recovery baseline tuneWriteId" }
            require(marker.baselineBurnRequestCnt in 0..0xff) { "Invalid T6 recovery baseline burnRequestCnt" }
            require(marker.baselineFlashWriteErrors in 0..0xff) { "Invalid T6 recovery flash error baseline" }
            require(marker.requestedTuneWriteId == null || marker.requestedTuneWriteId in 0..0xffff_ffffL) {
                "Invalid T6 recovery requested tuneWriteId"
            }
            require(marker.requestedBurnRequestCnt == null || marker.requestedBurnRequestCnt in 0..0xff) {
                "Invalid T6 recovery requested burnRequestCnt"
            }
            require(marker.restoreSourceGeneration == null || marker.restoreSourceGeneration > 0L) {
                "Invalid T6 recovery restore source generation"
            }
            require(marker.restoreRequestedTuneWriteId == null || marker.restoreRequestedTuneWriteId in 0..0xffff_ffffL) {
                "Invalid T6 recovery restore requested tuneWriteId"
            }
            require(marker.restoreRequestedBurnRequestCnt == null || marker.restoreRequestedBurnRequestCnt in 0..0xff) {
                "Invalid T6 recovery restore requested burnRequestCnt"
            }
            require(marker.flashStatusDefinitionFingerprint.matches(FP)) {
                "Invalid T6 recovery flash-status fingerprint"
            }
            require(marker.createdAtEpochMs > 0L && marker.updatedAtEpochMs >= marker.createdAtEpochMs) {
                "Invalid T6 recovery timestamps"
            }
            return marker
        }
    }
}

internal enum class T6RecoveryStoreState { ABSENT, PRESENT, CORRUPT }

internal data class T6RecoveryStoreRead(
    val state: T6RecoveryStoreState,
    val marker: T6PersistentBurnRecoveryMarker? = null,
    val error: String = ""
)

internal interface T6PersistentBurnRecoveryStore {
    fun load(): T6RecoveryStoreRead
    fun persist(marker: T6PersistentBurnRecoveryMarker): Boolean
    fun clear(): Boolean
}

internal object T6PersistentBurnPolicy {
    const val AUTHORIZATION_MAX_AGE_MS = 60_000L
    const val ENTER_PERSISTENT_SCALAR_BURN_ACTION_ID = "w5-enter-persistent-scalar-burn-v1"
    const val RESTORE_PERSISTENT_BASELINE_ACTION_ID = "w5-restore-persistent-baseline-v1"
    const val STATUS_POLL_INTERVAL_MS = 100L
    const val STATUS_TIMEOUT_MS = 10_000L
}



internal data class T6BaselineRamRestoreResult(
    val snapshot: TuneSnapshot,
    val transmissionAttempts: Int,
    val detail: String
)

internal object T6BaselineRamRestoreRunner {
    fun execute(
        preview: T6BaselineRestorePreview,
        currentCandidateSnapshot: TuneSnapshot,
        transport: T3BoundTransportPort,
        baselineFlashWriteErrors: Int
    ): T6BaselineRamRestoreResult {
        val plan = preview.plan
        RamScalarAuthority.requirePlan(plan)
        require(currentCandidateSnapshot.generation == preview.generation) {
            "T6 baseline RAM restore candidate snapshot generation changed"
        }
        require(currentCandidateSnapshot.fingerprint.equals(preview.candidateTuneFingerprint, ignoreCase = true)) {
            "T6 baseline RAM restore no longer starts from the persisted candidate"
        }

        val safety = transport.readSafetyInputs(plan.context)
        require(safety.rpm == 0.0) { "T6 baseline RAM restore requires exactly 0 RPM" }
        require(!safety.cranking) { "T6 baseline RAM restore is blocked while cranking" }
        require(!safety.flashWritePending) { "T6 baseline RAM restore is blocked by pending flash write" }
        require(safety.flashWriteErrors == baselineFlashWriteErrors) {
            "T6 baseline RAM restore flash error count changed"
        }

        var transmissions = 0
        val writeBody = T3PilotRamProtocol.candidateWriteBody(plan)
        transmissions++
        val write = transport.exchange(
            writeBody,
            1,
            T3BoundTransportPort.LABEL_CANDIDATE
        )
        require(write.generation == preview.generation) {
            "T6 baseline RAM restore write response belongs to another generation"
        }
        val responseCode = T3PilotRamProtocol.requireWriteAck(write.body)
        require(responseCode == 0x00) {
            if (T3PilotRamProtocol.isKnownExplicitRejection(responseCode)) {
                "T6 baseline RAM restore was explicitly rejected by ECU"
            } else {
                "T6 baseline RAM restore ACK code is unexpected: 0x\${responseCode.toString(16)}"
            }
        }

        val read = transport.exchange(
            T3PilotRamProtocol.readBackBody(plan),
            plan.target.byteSize + 1,
            T3BoundTransportPort.LABEL_CANDIDATE_READBACK
        )
        require(read.generation == preview.generation) {
            "T6 baseline RAM restore read-back belongs to another generation"
        }
        val raw = T3PilotRamProtocol.requireReadBack(read.body, plan)
        require(raw.contentEquals(plan.encodedBytes())) {
            "T6 baseline RAM restore exact target read-back mismatch"
        }

        val restored = transport.readCompleteTuneSnapshot(preview.generation)
        require(T3TuneSnapshotVerifier.matchesCandidate(restored, currentCandidateSnapshot, plan)) {
            "T6 baseline RAM restore complete TuneSnapshot contains collateral differences"
        }
        require(restored.fingerprint.equals(preview.baselineTuneFingerprint, ignoreCase = true)) {
            "T6 baseline RAM restore complete TuneSnapshot does not equal the original baseline"
        }

        return T6BaselineRamRestoreResult(
            snapshot = restored,
            transmissionAttempts = transmissions,
            detail = "Original baseline restored exactly in RAM and verified by complete TuneSnapshot"
        )
    }
}

internal data class T6BaselineRestorePreview(
    val previewId: String,
    val generation: Long,
    val profileFingerprint: String,
    val candidateTuneFingerprint: String,
    val baselineTuneFingerprint: String,
    val scalarName: String,
    val unit: String,
    val currentCandidateValue: Double,
    val restoreBaselineValue: Double,
    internal val plan: TuningScalarChangePlan
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", "ready")
        .put("capability", "PERSISTENT_BASELINE_RESTORE")
        .put("previewId", previewId)
        .put("generation", generation)
        .put("profileFingerprint", profileFingerprint)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("name", scalarName)
        .put("unit", unit)
        .put("currentCandidateValue", currentCandidateValue)
        .put("restoreBaselineValue", restoreBaselineValue)
        .put("transmitted", false)
        .put("burnRequested", false)
}

internal object T6BaselineRestorePreviewFactory {
    fun prepare(
        profile: UsbTunerStudioProfile,
        currentSnapshot: TuneSnapshot,
        generation: Long,
        recovery: T6PersistentBurnRecoveryMarker
    ): T6BaselineRestorePreview {
        require(recovery.phase == T6RecoveryPhase.PERSISTED_CANDIDATE_VERIFIED) {
            "T6 baseline restore requires a power-cycle-verified persistent candidate"
        }
        require(generation > 0L && currentSnapshot.generation == generation) {
            "T6 baseline restore requires the current positive USB generation"
        }
        require(currentSnapshot.ecuSignature == recovery.ecuSignature) {
            "T6 baseline restore ECU signature changed"
        }
        require(currentSnapshot.profileFingerprint.equals(recovery.profileFingerprint, ignoreCase = true)) {
            "T6 baseline restore profile identity changed"
        }
        require(currentSnapshot.fingerprint.equals(recovery.candidateTuneFingerprint, ignoreCase = true)) {
            "T6 current tune is not the verified persistent candidate"
        }

        val prepared = W4ScalarProposalFactory.prepare(
            profile = profile,
            snapshot = currentSnapshot,
            generation = generation,
            name = recovery.scalarName,
            requestedValue = recovery.originalValue
        )
        val plan = prepared.plan
        require(plan.target.definitionFingerprint.equals(recovery.targetDefinitionFingerprint, ignoreCase = true)) {
            "T6 baseline restore scalar definition changed"
        }
        require(plan.originalRawHex.equals(recovery.proposedRawHex, ignoreCase = true)) {
            "T6 current scalar bytes are not the persisted candidate bytes"
        }
        require(plan.proposedRawHex.equals(recovery.originalRawHex, ignoreCase = true)) {
            "T6 baseline restore encoding does not reproduce the original scalar bytes"
        }

        val expectedBaseline = T3TuneSnapshotVerifier.candidateSnapshot(currentSnapshot, plan)
        require(expectedBaseline.fingerprint.equals(recovery.baselineTuneFingerprint, ignoreCase = true)) {
            "T6 one-way baseline restore does not reconstruct the original complete tune fingerprint"
        }

        val canonical = buildString {
            append("EpicDashT6BaselineRestorePreview/v1")
            append('|').append(generation)
            append('|').append(recovery.profileFingerprint.lowercase(Locale.US))
            append('|').append(recovery.candidateTuneFingerprint.lowercase(Locale.US))
            append('|').append(recovery.baselineTuneFingerprint.lowercase(Locale.US))
            append('|').append(recovery.scalarName)
            append('|').append(recovery.targetDefinitionFingerprint.lowercase(Locale.US))
            append('|').append(recovery.proposedRawHex.lowercase(Locale.US))
            append('|').append(recovery.originalRawHex.lowercase(Locale.US))
        }
        return T6BaselineRestorePreview(
            previewId = sha256Hex(canonical.toByteArray(Charsets.UTF_8)),
            generation = generation,
            profileFingerprint = recovery.profileFingerprint,
            candidateTuneFingerprint = recovery.candidateTuneFingerprint,
            baselineTuneFingerprint = recovery.baselineTuneFingerprint,
            scalarName = recovery.scalarName,
            unit = recovery.unit,
            currentCandidateValue = recovery.candidateEffectiveValue,
            restoreBaselineValue = recovery.originalValue,
            plan = plan
        )
    }
}

internal class T6BaselineRestoreAuthorization(
    private val maximumAgeMs: Long = T6PersistentBurnPolicy.AUTHORIZATION_MAX_AGE_MS
) {
    private var generation: Long = -1L
    private var previewId: String = ""
    private var confirmedAtElapsedMs: Long = -1L

    @Synchronized
    fun confirm(actionId: String, preview: T6BaselineRestorePreview, nowElapsedMs: Long): Boolean {
        clear()
        if (actionId != T6PersistentBurnPolicy.RESTORE_PERSISTENT_BASELINE_ACTION_ID) return false
        if (preview.generation <= 0L || nowElapsedMs < 0L || !preview.previewId.matches(FP)) return false
        generation = preview.generation
        previewId = preview.previewId.lowercase(Locale.US)
        confirmedAtElapsedMs = nowElapsedMs
        return true
    }

    @Synchronized
    fun consume(preview: T6BaselineRestorePreview, currentGeneration: Long, nowElapsedMs: Long): Boolean {
        val accepted = generation == currentGeneration &&
            generation == preview.generation &&
            confirmedAtElapsedMs >= 0L &&
            nowElapsedMs >= confirmedAtElapsedMs &&
            nowElapsedMs - confirmedAtElapsedMs <= maximumAgeMs &&
            previewId == preview.previewId.lowercase(Locale.US)
        clear()
        return accepted
    }

    @Synchronized
    fun clear() {
        generation = -1L
        previewId = ""
        confirmedAtElapsedMs = -1L
    }

    companion object {
        private val FP = Regex("[0-9a-fA-F]{64}")
    }
}

internal data class T6PersistentBurnPreview(
    val previewId: String,
    val generation: Long,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val candidateTuneFingerprint: String,
    val scalarName: String,
    val unit: String,
    val requestedValue: Double,
    val effectiveValue: Double,
    internal val pageNumber: Int,
    internal val pageIdentifier: Int,
    internal val burnCommand: String,
    internal val targetDefinitionFingerprint: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", "ready")
        .put("capability", "PERSISTENT_SCALAR_BURN")
        .put("previewId", previewId)
        .put("generation", generation)
        .put("profileFingerprint", profileFingerprint)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("name", scalarName)
        .put("unit", unit)
        .put("requestedValue", requestedValue)
        .put("effectiveValue", effectiveValue)
        .put("transmitted", false)
        .put("burnRequested", false)
}

internal data class T6PersistentBurnEntryResult(
    val authorized: Boolean,
    val generation: Long,
    val previewId: String
)

internal object T6PersistentBurnPreviewFactory {
    fun prepare(
        plan: TuningScalarChangePlan,
        verifiedCandidateSnapshot: TuneSnapshot,
        verifiedCandidateFingerprint: String,
        recoveryMarker: T3RamRecoveryMarker,
        profile: UsbTunerStudioProfile
    ): T6PersistentBurnPreview {
        RamScalarAuthority.requireAgainstProfile(plan, profile)
        require(plan.context.source == TuningDataSource.LIVE) { "T6 requires a LIVE W4 plan" }
        require(verifiedCandidateSnapshot.generation == plan.context.generation) {
            "T6 candidate TuneSnapshot belongs to another USB generation"
        }
        require(verifiedCandidateSnapshot.ecuSignature == plan.context.ecuSignature) {
            "T6 candidate TuneSnapshot ECU signature changed"
        }
        require(verifiedCandidateSnapshot.profileFingerprint.equals(plan.context.profileFingerprint, ignoreCase = true)) {
            "T6 candidate TuneSnapshot profile identity changed"
        }
        require(verifiedCandidateSnapshot.fingerprint.equals(verifiedCandidateFingerprint, ignoreCase = true)) {
            "T6 current TuneSnapshot is not the W4 verified candidate"
        }
        require(recoveryMarker.phase == T3RecoveryPhase.WRITE) {
            "T6 requires the durable W4 candidate recovery marker"
        }
        require(recoveryMarker.generation == plan.context.generation) {
            "T6 recovery marker belongs to another generation"
        }
        require(recoveryMarker.previewId.equals(plan.previewId, ignoreCase = true)) {
            "T6 recovery marker preview identity changed"
        }
        require(recoveryMarker.ecuSignature == plan.context.ecuSignature) {
            "T6 recovery marker ECU signature changed"
        }
        require(recoveryMarker.profileFingerprint.equals(plan.context.profileFingerprint, ignoreCase = true)) {
            "T6 recovery marker profile identity changed"
        }
        require(recoveryMarker.baselineTuneFingerprint.equals(plan.context.tuneFingerprint, ignoreCase = true)) {
            "T6 recovery marker baseline tune identity changed"
        }
        require(recoveryMarker.candidateTuneFingerprint.equals(verifiedCandidateFingerprint, ignoreCase = true)) {
            "T6 recovery marker candidate tune identity changed"
        }
        require(recoveryMarker.targetDefinitionFingerprint.equals(plan.target.definitionFingerprint, ignoreCase = true)) {
            "T6 recovery marker target definition changed"
        }

        val page = profile.tunePages.singleOrNull { it.pageNumber == plan.target.pageNumber }
            ?: throw IllegalArgumentException("T6 target tune page is missing or ambiguous")
        require(page.identifier == plan.target.pageIdentifier) { "T6 target page identifier changed" }
        require(page.burnCommand == T6BurnProtocol.SUPPORTED_BURN_COMMAND) {
            "T6 target page does not expose the supported current-profile burn command"
        }
        T6BurnProtocol.buildBody(page)

        val canonical = buildString {
            append("EpicDashT6PersistentBurnPreview/v1")
            append('|').append(plan.context.generation)
            append('|').append(plan.context.ecuSignature)
            append('|').append(plan.context.profileFingerprint.lowercase(Locale.US))
            append('|').append(plan.context.tuneFingerprint.lowercase(Locale.US))
            append('|').append(verifiedCandidateFingerprint.lowercase(Locale.US))
            append('|').append(plan.previewId.lowercase(Locale.US))
            append('|').append(plan.target.name)
            append('|').append(plan.target.definitionFingerprint.lowercase(Locale.US))
            append('|').append(page.pageNumber)
            append('|').append(page.identifier)
            append('|').append(page.burnCommand)
            append('|').append(java.lang.Double.doubleToLongBits(plan.requestedValue))
            append('|').append(java.lang.Double.doubleToLongBits(plan.effectiveEncodedValue))
        }
        return T6PersistentBurnPreview(
            previewId = sha256Hex(canonical.toByteArray(Charsets.UTF_8)),
            generation = plan.context.generation,
            profileFingerprint = plan.context.profileFingerprint,
            baselineTuneFingerprint = plan.context.tuneFingerprint,
            candidateTuneFingerprint = verifiedCandidateFingerprint,
            scalarName = plan.target.name,
            unit = plan.target.unit,
            requestedValue = plan.requestedValue,
            effectiveValue = plan.effectiveEncodedValue,
            pageNumber = page.pageNumber,
            pageIdentifier = page.identifier,
            burnCommand = page.burnCommand,
            targetDefinitionFingerprint = plan.target.definitionFingerprint
        )
    }
}

internal class T6PersistentBurnSessionAuthorization(
    private val maximumAgeMs: Long = T6PersistentBurnPolicy.AUTHORIZATION_MAX_AGE_MS
) {
    init {
        require(maximumAgeMs > 0L) { "T6 burn authorization age must be positive" }
    }

    private var generation: Long = -1L
    private var previewId: String = ""
    private var candidateTuneFingerprint: String = ""
    private var confirmedAtElapsedMs: Long = -1L

    @Synchronized
    fun confirm(
        actionId: String,
        preview: T6PersistentBurnPreview,
        currentGeneration: Long,
        nowElapsedMs: Long
    ): Boolean {
        clear()
        if (actionId != T6PersistentBurnPolicy.ENTER_PERSISTENT_SCALAR_BURN_ACTION_ID) return false
        if (currentGeneration <= 0L || currentGeneration != preview.generation || nowElapsedMs < 0L) return false
        if (!preview.previewId.matches(FP) || !preview.candidateTuneFingerprint.matches(FP)) return false
        generation = currentGeneration
        previewId = preview.previewId.lowercase(Locale.US)
        candidateTuneFingerprint = preview.candidateTuneFingerprint.lowercase(Locale.US)
        confirmedAtElapsedMs = nowElapsedMs
        return true
    }

    @Synchronized
    fun isConfirmed(preview: T6PersistentBurnPreview, currentGeneration: Long, nowElapsedMs: Long): Boolean {
        if (generation <= 0L || confirmedAtElapsedMs < 0L) return false
        if (currentGeneration != generation || currentGeneration != preview.generation) return false
        if (nowElapsedMs < confirmedAtElapsedMs || nowElapsedMs - confirmedAtElapsedMs > maximumAgeMs) return false
        if (previewId != preview.previewId.lowercase(Locale.US)) return false
        if (candidateTuneFingerprint != preview.candidateTuneFingerprint.lowercase(Locale.US)) return false
        return true
    }

    @Synchronized
    fun consume(preview: T6PersistentBurnPreview, currentGeneration: Long, nowElapsedMs: Long): Boolean {
        val accepted = isConfirmed(preview, currentGeneration, nowElapsedMs)
        clear()
        return accepted
    }

    @Synchronized
    fun clear() {
        generation = -1L
        previewId = ""
        candidateTuneFingerprint = ""
        confirmedAtElapsedMs = -1L
    }

    companion object {
        private val FP = Regex("[0-9a-fA-F]{64}")
    }
}

internal class T6PersistentBurnManagerGate(
    private val authorization: T6PersistentBurnSessionAuthorization = T6PersistentBurnSessionAuthorization()
) {
    fun enter(
        actionId: String,
        preview: T6PersistentBurnPreview,
        generation: Long,
        nowElapsedMs: Long
    ): Boolean = authorization.confirm(actionId, preview, generation, nowElapsedMs)

    fun isAuthorized(
        preview: T6PersistentBurnPreview,
        generation: Long,
        nowElapsedMs: Long
    ): Boolean = authorization.isConfirmed(preview, generation, nowElapsedMs)

    fun consumeForAttempt(
        preview: T6PersistentBurnPreview,
        generation: Long,
        nowElapsedMs: Long
    ): Boolean = authorization.consume(preview, generation, nowElapsedMs)

    fun clear() = authorization.clear()
}

internal data class T6BurnTransportResponse(
    val body: ByteArray,
    val generation: Long
)

internal class T6BoundBurnTransportPort(
    private val preview: T6PersistentBurnPreview,
    private val profileProvider: () -> UsbTunerStudioProfile,
    private val currentGeneration: () -> Long,
    private val isNativeOwnerThread: () -> Boolean,
    private val exchangeBody: (ByteArray, Int, String) -> ByteArray,
    private val nativeOutputSampleReader: () -> T3NativeOutputSample,
    private val flashStatusResolver: T6FlashStatusResolver,
    private val nowElapsedMs: () -> Long,
    private val snapshotReader: (Long) -> TuneSnapshot
) {
    private val burnBody: ByteArray

    init {
        val page = exactPage()
        burnBody = T6BurnProtocol.buildBody(page)
    }

    fun requestBurn(): T6BurnTransportResponse {
        requireOwnerAndGeneration()
        val response = exchangeBody(burnBody.copyOf(), 1, LABEL_BURN).copyOf()
        return T6BurnTransportResponse(response, currentGeneration())
    }

    fun readFlashStatus(context: TuningContext): T6FlashStatus {
        requireOwnerAndGeneration()
        require(context.generation == preview.generation) { "T6 flash-status context generation changed" }
        require(context.source == TuningDataSource.LIVE) { "T6 flash-status context is not LIVE" }
        require(context.ecuSignature.isNotBlank()) { "T6 flash-status ECU signature is blank" }
        require(context.profileFingerprint.equals(preview.profileFingerprint, ignoreCase = true)) {
            "T6 flash-status profile identity changed"
        }
        return flashStatusResolver.resolve(
            profile = profileProvider(),
            context = context,
            sample = nativeOutputSampleReader(),
            nowElapsedMs = nowElapsedMs()
        )
    }

    fun readCompleteTuneSnapshot(expectedGeneration: Long): TuneSnapshot {
        requireOwnerAndGeneration()
        require(expectedGeneration == preview.generation) {
            "T6 TuneSnapshot request generation differs from authorized burn generation"
        }
        val snapshot = snapshotReader(expectedGeneration)
        require(snapshot.generation == expectedGeneration) {
            "T6 TuneSnapshot reader returned another generation"
        }
        return snapshot
    }

    private fun exactPage(): UsbTunePage {
        val profile = profileProvider()
        require(profile.tuneProfileFingerprint().equals(preview.profileFingerprint, ignoreCase = true)) {
            "T6 imported profile changed after burn preview"
        }
        val page = profile.tunePages.singleOrNull { it.pageNumber == preview.pageNumber }
            ?: throw IllegalArgumentException("T6 burn page is missing or ambiguous")
        require(page.identifier == preview.pageIdentifier) { "T6 burn page identifier changed" }
        require(page.burnCommand == preview.burnCommand) { "T6 burn command changed after preview" }
        return page
    }

    private fun requireOwnerAndGeneration() {
        require(isNativeOwnerThread()) { "T6 burn transport may run only on the native USB owner thread" }
        require(currentGeneration() == preview.generation) { "T6 burn USB generation is no longer current" }
        exactPage()
    }

    companion object {
        const val LABEL_BURN = "T6 B persistent scalar burn"
    }
}

internal enum class T6PersistentBurnOutcome {
    BURN_COMPLETED_AWAITING_POWER_CYCLE,
    SAFE_ABORTED,
    RECOVERY_REQUIRED
}

internal data class T6PersistentBurnResult(
    val outcome: T6PersistentBurnOutcome,
    val transmissionAttempts: Int,
    val requestedTuneWriteId: Long?,
    val burnRequestCnt: Int?,
    val candidateTuneFingerprint: String,
    val rereadTuneFingerprint: String?,
    val detail: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("outcome", outcome.name)
        .put("transmissionAttempts", transmissionAttempts)
        .put("requestedTuneWriteId", requestedTuneWriteId ?: JSONObject.NULL)
        .put("burnRequestCnt", burnRequestCnt ?: JSONObject.NULL)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("rereadTuneFingerprint", rereadTuneFingerprint ?: JSONObject.NULL)
        .put("detail", detail)
        .put("transmitted", transmissionAttempts > 0)
        .put("burnRequested", transmissionAttempts > 0)
}


internal data class T6BaselineRestoreBurnResult(
    val outcome: T6PersistentBurnOutcome,
    val ramTransmissionAttempts: Int,
    val burnTransmissionAttempts: Int,
    val requestedTuneWriteId: Long?,
    val burnRequestCnt: Int?,
    val baselineTuneFingerprint: String,
    val rereadTuneFingerprint: String?,
    val detail: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("outcome", outcome.name)
        .put("ramTransmissionAttempts", ramTransmissionAttempts)
        .put("burnTransmissionAttempts", burnTransmissionAttempts)
        .put("requestedTuneWriteId", requestedTuneWriteId ?: JSONObject.NULL)
        .put("burnRequestCnt", burnRequestCnt ?: JSONObject.NULL)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("rereadTuneFingerprint", rereadTuneFingerprint ?: JSONObject.NULL)
        .put("detail", detail)
        .put("transmitted", ramTransmissionAttempts > 0 || burnTransmissionAttempts > 0)
        .put("burnRequested", burnTransmissionAttempts > 0)
}


internal data class T6FinalBaselineVerificationResult(
    val verified: Boolean,
    val generation: Long,
    val bootFlashWriteId: Long?,
    val baselineTuneFingerprint: String,
    val observedTuneFingerprint: String?,
    val bootConfigPrimaryStatus: Int?,
    val bootConfigBackupStatus: Int?,
    val detail: String,
    val observedTuneWriteId: Long? = null,
    val observedBurnRequestCnt: Int? = null,
    val observedFlashWritePending: Boolean? = null,
    val observedFlashWriteErrors: Int? = null,
    val observedFlashWrites: Int? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("verified", verified)
        .put("generation", generation)
        .put("bootFlashWriteId", bootFlashWriteId ?: JSONObject.NULL)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("observedTuneFingerprint", observedTuneFingerprint ?: JSONObject.NULL)
        .put("observedTuneWriteId", observedTuneWriteId ?: JSONObject.NULL)
        .put("observedBurnRequestCnt", observedBurnRequestCnt ?: JSONObject.NULL)
        .put("observedFlashWritePending", observedFlashWritePending ?: JSONObject.NULL)
        .put("observedFlashWriteErrors", observedFlashWriteErrors ?: JSONObject.NULL)
        .put("observedFlashWrites", observedFlashWrites ?: JSONObject.NULL)
        .put("bootConfigPrimaryStatus", bootConfigPrimaryStatus ?: JSONObject.NULL)
        .put("bootConfigBackupStatus", bootConfigBackupStatus ?: JSONObject.NULL)
        .put("detail", detail)
        .put("transmitted", false)
        .put("burnRequested", false)
}

internal data class T6PowerCycleVerificationResult(
    val verified: Boolean,
    val generation: Long,
    val bootFlashWriteId: Long?,
    val candidateTuneFingerprint: String,
    val observedTuneFingerprint: String?,
    val bootConfigPrimaryStatus: Int?,
    val bootConfigBackupStatus: Int?,
    val detail: String,
    val observedTuneWriteId: Long? = null,
    val observedBurnRequestCnt: Int? = null,
    val observedFlashWritePending: Boolean? = null,
    val observedFlashWriteErrors: Int? = null,
    val observedFlashWrites: Int? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("verified", verified)
        .put("generation", generation)
        .put("bootFlashWriteId", bootFlashWriteId ?: JSONObject.NULL)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("observedTuneFingerprint", observedTuneFingerprint ?: JSONObject.NULL)
        .put("observedTuneWriteId", observedTuneWriteId ?: JSONObject.NULL)
        .put("observedBurnRequestCnt", observedBurnRequestCnt ?: JSONObject.NULL)
        .put("observedFlashWritePending", observedFlashWritePending ?: JSONObject.NULL)
        .put("observedFlashWriteErrors", observedFlashWriteErrors ?: JSONObject.NULL)
        .put("observedFlashWrites", observedFlashWrites ?: JSONObject.NULL)
        .put("bootConfigPrimaryStatus", bootConfigPrimaryStatus ?: JSONObject.NULL)
        .put("bootConfigBackupStatus", bootConfigBackupStatus ?: JSONObject.NULL)
        .put("detail", detail)
        .put("transmitted", false)
        .put("burnRequested", false)
}

internal data class T6PendingPersistenceProof(
    val sourceGeneration: Long,
    val ecuSignature: String,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val candidateTuneFingerprint: String,
    val scalarName: String,
    val targetDefinitionFingerprint: String,
    val requestedTuneWriteId: Long,
    val burnRequestCnt: Int,
    val flashStatusDefinitionFingerprint: String,
    val originalRawHex: String,
    val proposedRawHex: String
)
