package com.buttonbox.ble

import org.json.JSONObject

internal enum class T3RamTransactionState {
    IDLE,
    READY,
    APPROVED,
    ARMED,
    TRANSMITTING,
    ACKNOWLEDGED,
    WRITE_READBACK_VERIFIED,
    CANDIDATE_SNAPSHOT_VERIFIED,
    RESTORE_ARMED,
    RESTORING,
    RESTORE_ACKNOWLEDGED,
    RESTORE_READBACK_VERIFIED,
    BASELINE_RESTORED,
    REJECTED,
    SAFE_ABORTED,
    RECOVERY_REQUIRED
}

internal enum class T3RecoveryPhase { WRITE, RESTORE }

internal enum class T3RamBlockReason {
    NONE,
    INVALID_STATE,
    RECOVERY_MARKER_PRESENT,
    RECOVERY_MARKER_CORRUPT,
    RECOVERY_MARKER_PERSIST_FAILED,
    RECOVERY_MARKER_CLEAR_FAILED,
    CONTEXT_MISMATCH,
    TUNE_CHANGED,
    ENGINE_NOT_STOPPED,
    INVALID_VOLTAGE,
    VOLTAGE_TOO_LOW,
    SUPPORT_POWER_NOT_CONFIRMED,
    FLASH_WRITE_PENDING,
    FLASH_WRITE_ERRORS_CHANGED,
    SAFETY_SCHEMA_CHANGED,
    PREVIEW_MISMATCH,
    DUPLICATE_ACTION,
    STALE_RESPONSE,
    ECU_REJECTED,
    PROTOCOL_FAILURE,
    READBACK_MISMATCH,
    SNAPSHOT_MISMATCH
}

/**
 * Optional evidence-backed numeric floor for a future hardware-specific policy.
 *
 * `minimumVoltage=null` explicitly means that no numeric VBatt cutoff is asserted. Native W3 still
 * requires a fresh finite positive ECU-reported VBatt sample and explicit support-power confirmation.
 */
internal data class T3BenchWritePolicy(
    val minimumVoltage: Double? = null,
    val requireSupportPowerConfirmation: Boolean = true
) {
    init {
        require(minimumVoltage == null || (minimumVoltage.isFinite() && minimumVoltage > 0.0)) {
            "T3 minimum voltage must be null or a positive finite value"
        }
    }
}

internal data class T3RamSafetyInputs(
    val context: TuningContext,
    val rpm: Double,
    val cranking: Boolean,
    val voltage: Double,
    val supportPowerConfirmed: Boolean,
    val flashWritePending: Boolean,
    val flashWriteErrors: Int,
    val safetyDefinitionFingerprint: String
)

internal data class T3RamRecoveryMarker(
    val phase: T3RecoveryPhase,
    val actionId: String,
    val previewId: String,
    val sessionId: Long,
    val generation: Long,
    val ecuSignature: String,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val candidateTuneFingerprint: String,
    val targetDefinitionFingerprint: String,
    val originalRawHex: String,
    val proposedRawHex: String,
    val createdAtEpochMs: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("phase", phase.name)
        .put("actionId", actionId)
        .put("previewId", previewId)
        .put("sessionId", sessionId)
        .put("generation", generation)
        .put("ecuSignature", ecuSignature)
        .put("profileFingerprint", profileFingerprint)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("targetDefinitionFingerprint", targetDefinitionFingerprint)
        .put("originalRawHex", originalRawHex)
        .put("proposedRawHex", proposedRawHex)
        .put("createdAtEpochMs", createdAtEpochMs)

    companion object {
        const val SCHEMA = "EpicDashT3RamRecovery/v1"

        fun fromJson(json: JSONObject): T3RamRecoveryMarker {
            require(json.getString("schema") == SCHEMA) { "Unsupported T3 recovery marker schema" }
            val marker = T3RamRecoveryMarker(
                phase = T3RecoveryPhase.valueOf(json.getString("phase")),
                actionId = json.getString("actionId"),
                previewId = json.getString("previewId"),
                sessionId = json.getLong("sessionId"),
                generation = json.getLong("generation"),
                ecuSignature = json.getString("ecuSignature"),
                profileFingerprint = json.getString("profileFingerprint"),
                baselineTuneFingerprint = json.getString("baselineTuneFingerprint"),
                candidateTuneFingerprint = json.getString("candidateTuneFingerprint"),
                targetDefinitionFingerprint = json.getString("targetDefinitionFingerprint"),
                originalRawHex = json.getString("originalRawHex"),
                proposedRawHex = json.getString("proposedRawHex"),
                createdAtEpochMs = json.getLong("createdAtEpochMs")
            )
            require(marker.actionId.isNotBlank()) { "T3 recovery action ID is blank" }
            require(marker.previewId.matches(FP)) { "Invalid T3 recovery preview ID" }
            require(marker.sessionId > 0L && marker.generation >= 0L) { "Invalid T3 recovery ownership" }
            require(marker.ecuSignature.isNotBlank()) { "T3 recovery ECU signature is blank" }
            require(marker.profileFingerprint.matches(FP)) { "Invalid T3 recovery profile fingerprint" }
            require(marker.baselineTuneFingerprint.matches(FP) && marker.candidateTuneFingerprint.matches(FP)) {
                "Invalid T3 recovery tune fingerprint"
            }
            require(marker.targetDefinitionFingerprint.matches(FP)) { "Invalid T3 recovery target definition fingerprint" }
            require(marker.originalRawHex.matches(HEX_SCALAR) && marker.proposedRawHex.matches(HEX_SCALAR) &&
                marker.originalRawHex.length == marker.proposedRawHex.length && marker.originalRawHex.length in setOf(2, 4, 8)
            ) { "Invalid T3 recovery scalar bytes" }
            return marker
        }

        private val FP = Regex("[0-9a-fA-F]{64}")
        private val HEX_SCALAR = Regex("[0-9a-fA-F]+")
    }
}

internal enum class T3RecoveryStoreState { ABSENT, PRESENT, CORRUPT }

internal data class T3RecoveryStoreRead(
    val state: T3RecoveryStoreState,
    val marker: T3RamRecoveryMarker? = null,
    val error: String = ""
)

internal interface T3RamRecoveryMarkerStore {
    fun load(): T3RecoveryStoreRead
    fun persist(marker: T3RamRecoveryMarker): Boolean
    fun clear(): Boolean
}

internal data class T3StartupDecision(
    val writeBlocked: Boolean,
    val replayAllowed: Boolean,
    val recoveryRequired: Boolean,
    val markerCorrupt: Boolean
)

internal enum class T3RecoveryClassification {
    NO_MARKER,
    CORRUPT_MARKER,
    IDENTITY_MISMATCH,
    BASELINE,
    CANDIDATE,
    UNKNOWN_TUNE
}

/**
 * Production T3 transaction owner. It owns safety/approval/recovery state, but not Android USB.
 * The later UsbEcuManager adapter may transmit only bodies returned by beginCandidateWrite() and
 * beginRestoreWrite(). There is deliberately no retry or persistence/burn API.
 */
internal class T3RamScalarTransaction(
    private val proposal: TuningScalarChangePlan,
    private val baselineSnapshot: TuneSnapshot,
    private val policy: T3BenchWritePolicy,
    private val baselineFlashWriteErrors: Int,
    private val markerStore: T3RamRecoveryMarkerStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    var state: T3RamTransactionState = T3RamTransactionState.IDLE
        private set
    var lastBlockReason: T3RamBlockReason = T3RamBlockReason.NONE
        private set
    var transmissionAttempts: Int = 0
        private set
    val readOnlyOperationAvailable: Boolean = true

    private val candidateSnapshot: TuneSnapshot
    val candidateTuneFingerprint: String
    private var activeActionId: String? = null
    private var activeMarker: T3RamRecoveryMarker? = null
    private var safetyDefinitionFingerprint: String? = null

    init {
        require(baselineFlashWriteErrors >= 0) { "T3 flash-error baseline must be non-negative" }
        RamScalarAuthority.requirePlan(proposal)
        candidateSnapshot = T3TuneSnapshotVerifier.candidateSnapshot(baselineSnapshot, proposal)
        candidateTuneFingerprint = candidateSnapshot.fingerprint
    }

    fun prepare(inputs: T3RamSafetyInputs): Boolean {
        if (state != T3RamTransactionState.IDLE && state != T3RamTransactionState.SAFE_ABORTED) {
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        when (markerStore.load().state) {
            T3RecoveryStoreState.PRESENT -> {
                state = T3RamTransactionState.RECOVERY_REQUIRED
                return block(T3RamBlockReason.RECOVERY_MARKER_PRESENT, keepState = true)
            }
            T3RecoveryStoreState.CORRUPT -> {
                state = T3RamTransactionState.RECOVERY_REQUIRED
                return block(T3RamBlockReason.RECOVERY_MARKER_CORRUPT, keepState = true)
            }
            T3RecoveryStoreState.ABSENT -> Unit
        }
        val reason = preconditionFailure(inputs, proposal.context.tuneFingerprint)
        if (reason != null) {
            state = T3RamTransactionState.SAFE_ABORTED
            return block(reason, keepState = true)
        }
        activeActionId = null
        activeMarker = null
        safetyDefinitionFingerprint = inputs.safetyDefinitionFingerprint.lowercase()
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.READY
        return true
    }

    /** Explicit one-shot approval bound to the T2 preview identity. */
    fun approve(actionId: String, previewId: String, inputs: T3RamSafetyInputs): Boolean {
        if (state != T3RamTransactionState.READY) {
            if (activeActionId != null) return block(T3RamBlockReason.DUPLICATE_ACTION, keepState = true)
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        if (actionId.isBlank()) {
            state = T3RamTransactionState.SAFE_ABORTED
            return block(T3RamBlockReason.PREVIEW_MISMATCH, keepState = true)
        }
        if (previewId != proposal.previewId) {
            state = T3RamTransactionState.SAFE_ABORTED
            return block(T3RamBlockReason.PREVIEW_MISMATCH, keepState = true)
        }
        val reason = preconditionFailure(inputs, proposal.context.tuneFingerprint)
        if (reason != null) {
            state = T3RamTransactionState.SAFE_ABORTED
            return block(reason, keepState = true)
        }
        activeActionId = actionId
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.APPROVED
        return true
    }

    /** Persist/verify the recovery marker before the transport may emit a C frame. */
    fun armCandidateWrite(): Boolean {
        if (state != T3RamTransactionState.APPROVED) return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        val actionId = activeActionId ?: return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        val marker = marker(T3RecoveryPhase.WRITE, actionId)
        if (!markerStore.persist(marker)) {
            state = T3RamTransactionState.SAFE_ABORTED
            return block(T3RamBlockReason.RECOVERY_MARKER_PERSIST_FAILED, keepState = true)
        }
        val reread = markerStore.load()
        if (reread.state != T3RecoveryStoreState.PRESENT || reread.marker != marker) {
            state = T3RamTransactionState.SAFE_ABORTED
            return block(T3RamBlockReason.RECOVERY_MARKER_PERSIST_FAILED, keepState = true)
        }
        activeMarker = marker
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.ARMED
        return true
    }

    /** Calling this crosses the conservative point-of-possible-transmission boundary. */
    fun beginCandidateWrite(): ByteArray? {
        if (state != T3RamTransactionState.ARMED) {
            block(T3RamBlockReason.INVALID_STATE, keepState = true)
            return null
        }
        val marker = activeMarker
        val persisted = markerStore.load()
        if (marker == null || persisted.state != T3RecoveryStoreState.PRESENT || persisted.marker != marker) {
            state = T3RamTransactionState.SAFE_ABORTED
            block(T3RamBlockReason.RECOVERY_MARKER_PERSIST_FAILED, keepState = true)
            return null
        }
        transmissionAttempts++
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.TRANSMITTING
        return T3PilotRamProtocol.candidateWriteBody(proposal)
    }

    fun acknowledgeCandidateWrite(responseBody: ByteArray, responseGeneration: Long) {
        if (state != T3RamTransactionState.TRANSMITTING) {
            block(T3RamBlockReason.INVALID_STATE, keepState = true)
            return
        }
        if (responseGeneration != proposal.context.generation) {
            requireRecovery(T3RamBlockReason.STALE_RESPONSE)
            return
        }
        val code = try {
            T3PilotRamProtocol.requireWriteAck(responseBody)
        } catch (_: Exception) {
            requireRecovery(T3RamBlockReason.PROTOCOL_FAILURE)
            return
        }
        when {
            code == 0x00 -> {
                lastBlockReason = T3RamBlockReason.NONE
                state = T3RamTransactionState.ACKNOWLEDGED
            }
            T3PilotRamProtocol.isKnownExplicitRejection(code) -> {
                if (markerStore.clear()) {
                    activeMarker = null
                    activeActionId = null
                    lastBlockReason = T3RamBlockReason.ECU_REJECTED
                    state = T3RamTransactionState.REJECTED
                } else {
                    requireRecovery(T3RamBlockReason.RECOVERY_MARKER_CLEAR_FAILED)
                }
            }
            else -> requireRecovery(T3RamBlockReason.PROTOCOL_FAILURE)
        }
    }

    fun verifyCandidateReadBack(responseBody: ByteArray, responseGeneration: Long): Boolean {
        if (state != T3RamTransactionState.ACKNOWLEDGED) return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        if (responseGeneration != proposal.context.generation) {
            requireRecovery(T3RamBlockReason.STALE_RESPONSE)
            return false
        }
        val raw = try {
            T3PilotRamProtocol.requireReadBack(responseBody, proposal)
        } catch (_: Exception) {
            requireRecovery(T3RamBlockReason.PROTOCOL_FAILURE)
            return false
        }
        if (!raw.contentEquals(proposal.encodedBytes())) {
            requireRecovery(T3RamBlockReason.READBACK_MISMATCH)
            return false
        }
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.WRITE_READBACK_VERIFIED
        return true
    }

    /** Full snapshot verification is mandatory before the paired restore is armed. */
    fun verifyCandidateSnapshot(observed: TuneSnapshot, currentContext: TuningContext): Boolean {
        if (state != T3RamTransactionState.WRITE_READBACK_VERIFIED) {
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        if (!activeContextMatches(currentContext, observed.fingerprint) || observed.generation != proposal.context.generation) {
            requireRecovery(T3RamBlockReason.CONTEXT_MISMATCH)
            return false
        }
        if (!T3TuneSnapshotVerifier.matchesCandidate(observed, baselineSnapshot, proposal)) {
            requireRecovery(T3RamBlockReason.SNAPSHOT_MISMATCH)
            return false
        }
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.CANDIDATE_SNAPSHOT_VERIFIED
        return true
    }

    /** Update the durable marker to RESTORE before a second C frame can be emitted. */
    fun armRestore(inputs: T3RamSafetyInputs): Boolean {
        if (state != T3RamTransactionState.CANDIDATE_SNAPSHOT_VERIFIED) {
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        val reason = preconditionFailure(inputs, candidateTuneFingerprint)
        if (reason != null) {
            requireRecovery(reason)
            return false
        }
        val actionId = activeActionId ?: run {
            requireRecovery(T3RamBlockReason.INVALID_STATE)
            return false
        }
        val marker = marker(T3RecoveryPhase.RESTORE, actionId)
        if (!markerStore.persist(marker)) {
            requireRecovery(T3RamBlockReason.RECOVERY_MARKER_PERSIST_FAILED)
            return false
        }
        val reread = markerStore.load()
        if (reread.state != T3RecoveryStoreState.PRESENT || reread.marker != marker) {
            requireRecovery(T3RamBlockReason.RECOVERY_MARKER_PERSIST_FAILED)
            return false
        }
        activeMarker = marker
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.RESTORE_ARMED
        return true
    }

    fun beginRestoreWrite(): ByteArray? {
        if (state != T3RamTransactionState.RESTORE_ARMED) {
            block(T3RamBlockReason.INVALID_STATE, keepState = true)
            return null
        }
        val marker = activeMarker
        val persisted = markerStore.load()
        if (marker == null || marker.phase != T3RecoveryPhase.RESTORE ||
            persisted.state != T3RecoveryStoreState.PRESENT || persisted.marker != marker
        ) {
            requireRecovery(T3RamBlockReason.RECOVERY_MARKER_PERSIST_FAILED)
            return null
        }
        transmissionAttempts++
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.RESTORING
        return T3PilotRamProtocol.restoreWriteBody(proposal)
    }

    fun acknowledgeRestore(responseBody: ByteArray, responseGeneration: Long) {
        if (state != T3RamTransactionState.RESTORING) {
            block(T3RamBlockReason.INVALID_STATE, keepState = true)
            return
        }
        if (responseGeneration != proposal.context.generation) {
            requireRecovery(T3RamBlockReason.STALE_RESPONSE)
            return
        }
        val code = try {
            T3PilotRamProtocol.requireWriteAck(responseBody)
        } catch (_: Exception) {
            requireRecovery(T3RamBlockReason.PROTOCOL_FAILURE)
            return
        }
        if (code == 0x00) {
            lastBlockReason = T3RamBlockReason.NONE
            state = T3RamTransactionState.RESTORE_ACKNOWLEDGED
        } else {
            // Even an explicit restore rejection leaves the accepted candidate value in RAM.
            requireRecovery(if (T3PilotRamProtocol.isKnownExplicitRejection(code)) T3RamBlockReason.ECU_REJECTED else T3RamBlockReason.PROTOCOL_FAILURE)
        }
    }

    fun verifyRestoreReadBack(responseBody: ByteArray, responseGeneration: Long): Boolean {
        if (state != T3RamTransactionState.RESTORE_ACKNOWLEDGED) {
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        if (responseGeneration != proposal.context.generation) {
            requireRecovery(T3RamBlockReason.STALE_RESPONSE)
            return false
        }
        val raw = try {
            T3PilotRamProtocol.requireReadBack(responseBody, proposal)
        } catch (_: Exception) {
            requireRecovery(T3RamBlockReason.PROTOCOL_FAILURE)
            return false
        }
        if (!raw.contentEquals(proposal.originalBytes())) {
            requireRecovery(T3RamBlockReason.READBACK_MISMATCH)
            return false
        }
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.RESTORE_READBACK_VERIFIED
        return true
    }

    fun verifyBaselineRestored(observed: TuneSnapshot, currentContext: TuningContext): Boolean {
        if (state != T3RamTransactionState.RESTORE_READBACK_VERIFIED) {
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        if (!activeContextMatches(currentContext, observed.fingerprint) || observed.generation != proposal.context.generation) {
            requireRecovery(T3RamBlockReason.CONTEXT_MISMATCH)
            return false
        }
        if (!T3TuneSnapshotVerifier.matchesBaseline(observed, baselineSnapshot)) {
            requireRecovery(T3RamBlockReason.SNAPSHOT_MISMATCH)
            return false
        }
        if (!markerStore.clear()) {
            requireRecovery(T3RamBlockReason.RECOVERY_MARKER_CLEAR_FAILED)
            return false
        }
        activeMarker = null
        activeActionId = null
        safetyDefinitionFingerprint = null
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.BASELINE_RESTORED
        return true
    }

    /** Any failure after beginCandidateWrite/beginRestoreWrite is uncertain and never retried. */
    fun markTransportOrVerificationUncertain() {
        if (state in setOf(
                T3RamTransactionState.TRANSMITTING,
                T3RamTransactionState.ACKNOWLEDGED,
                T3RamTransactionState.WRITE_READBACK_VERIFIED,
                T3RamTransactionState.CANDIDATE_SNAPSHOT_VERIFIED,
                T3RamTransactionState.RESTORE_ARMED,
                T3RamTransactionState.RESTORING,
                T3RamTransactionState.RESTORE_ACKNOWLEDGED,
                T3RamTransactionState.RESTORE_READBACK_VERIFIED
            )
        ) {
            requireRecovery(T3RamBlockReason.PROTOCOL_FAILURE)
        }
    }

    /** Safe cancellation exists only before the first possible mutation. */
    fun abortBeforePossibleTransmission(): Boolean {
        if (state !in setOf(T3RamTransactionState.READY, T3RamTransactionState.APPROVED, T3RamTransactionState.ARMED)) {
            return block(T3RamBlockReason.INVALID_STATE, keepState = true)
        }
        if (state == T3RamTransactionState.ARMED && !markerStore.clear()) {
            requireRecovery(T3RamBlockReason.RECOVERY_MARKER_CLEAR_FAILED)
            return false
        }
        activeMarker = null
        activeActionId = null
        safetyDefinitionFingerprint = null
        lastBlockReason = T3RamBlockReason.NONE
        state = T3RamTransactionState.SAFE_ABORTED
        return true
    }

    private fun marker(phase: T3RecoveryPhase, actionId: String): T3RamRecoveryMarker = T3RamRecoveryMarker(
        phase = phase,
        actionId = actionId,
        previewId = proposal.previewId,
        sessionId = proposal.context.sessionId,
        generation = proposal.context.generation,
        ecuSignature = proposal.context.ecuSignature,
        profileFingerprint = proposal.context.profileFingerprint.lowercase(),
        baselineTuneFingerprint = baselineSnapshot.fingerprint.lowercase(),
        candidateTuneFingerprint = candidateTuneFingerprint.lowercase(),
        targetDefinitionFingerprint = proposal.target.definitionFingerprint.lowercase(),
        originalRawHex = proposal.originalRawHex.lowercase(),
        proposedRawHex = proposal.proposedRawHex.lowercase(),
        createdAtEpochMs = clock()
    )

    private fun preconditionFailure(inputs: T3RamSafetyInputs, expectedTuneFingerprint: String): T3RamBlockReason? {
        if (!ownershipMatches(inputs.context)) return T3RamBlockReason.CONTEXT_MISMATCH
        if (!inputs.context.tuneFingerprint.equals(expectedTuneFingerprint, ignoreCase = true)) return T3RamBlockReason.TUNE_CHANGED
        if (!inputs.safetyDefinitionFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) return T3RamBlockReason.CONTEXT_MISMATCH
        val frozenSafetyDefinition = safetyDefinitionFingerprint
        if (frozenSafetyDefinition != null && !inputs.safetyDefinitionFingerprint.equals(frozenSafetyDefinition, ignoreCase = true)) {
            return T3RamBlockReason.SAFETY_SCHEMA_CHANGED
        }
        if (!inputs.rpm.isFinite() || inputs.rpm != 0.0 || inputs.cranking) return T3RamBlockReason.ENGINE_NOT_STOPPED
        if (!inputs.voltage.isFinite() || inputs.voltage <= 0.0) return T3RamBlockReason.INVALID_VOLTAGE
        val minimumVoltage = policy.minimumVoltage
        if (minimumVoltage != null && inputs.voltage < minimumVoltage) return T3RamBlockReason.VOLTAGE_TOO_LOW
        if (policy.requireSupportPowerConfirmation && !inputs.supportPowerConfirmed) return T3RamBlockReason.SUPPORT_POWER_NOT_CONFIRMED
        if (inputs.flashWritePending) return T3RamBlockReason.FLASH_WRITE_PENDING
        if (inputs.flashWriteErrors != baselineFlashWriteErrors) return T3RamBlockReason.FLASH_WRITE_ERRORS_CHANGED
        return null
    }

    private fun ownershipMatches(context: TuningContext): Boolean =
        context.sessionId == proposal.context.sessionId &&
            context.generation == proposal.context.generation &&
            context.source == TuningDataSource.LIVE &&
            context.ecuSignature == proposal.context.ecuSignature &&
            context.profileFingerprint.equals(proposal.context.profileFingerprint, ignoreCase = true)

    private fun activeContextMatches(context: TuningContext, expectedTuneFingerprint: String): Boolean =
        ownershipMatches(context) && context.tuneFingerprint.equals(expectedTuneFingerprint, ignoreCase = true)

    private fun block(reason: T3RamBlockReason, keepState: Boolean): Boolean {
        lastBlockReason = reason
        if (!keepState) state = T3RamTransactionState.SAFE_ABORTED
        return false
    }

    private fun requireRecovery(reason: T3RamBlockReason) {
        lastBlockReason = reason
        state = T3RamTransactionState.RECOVERY_REQUIRED
    }

    companion object {
        fun startupDecision(store: T3RamRecoveryMarkerStore): T3StartupDecision = when (store.load().state) {
            T3RecoveryStoreState.ABSENT -> T3StartupDecision(false, false, false, false)
            T3RecoveryStoreState.PRESENT -> T3StartupDecision(true, false, true, false)
            T3RecoveryStoreState.CORRUPT -> T3StartupDecision(true, false, true, true)
        }

        fun classifyRecovery(
            stored: T3RecoveryStoreRead,
            observed: TuneSnapshot,
            context: TuningContext
        ): T3RecoveryClassification {
            if (stored.state == T3RecoveryStoreState.ABSENT) return T3RecoveryClassification.NO_MARKER
            if (stored.state == T3RecoveryStoreState.CORRUPT) return T3RecoveryClassification.CORRUPT_MARKER
            val marker = stored.marker ?: return T3RecoveryClassification.CORRUPT_MARKER
            val identityMatches = context.sessionId > 0L && context.generation >= 0L &&
                context.source == TuningDataSource.LIVE &&
                context.ecuSignature == marker.ecuSignature &&
                context.profileFingerprint.equals(marker.profileFingerprint, ignoreCase = true) &&
                observed.ecuSignature == marker.ecuSignature &&
                observed.profileFingerprint.equals(marker.profileFingerprint, ignoreCase = true) &&
                observed.generation == context.generation &&
                context.tuneFingerprint.equals(observed.fingerprint, ignoreCase = true)
            if (!identityMatches) return T3RecoveryClassification.IDENTITY_MISMATCH
            return when {
                observed.fingerprint.equals(marker.baselineTuneFingerprint, ignoreCase = true) -> T3RecoveryClassification.BASELINE
                observed.fingerprint.equals(marker.candidateTuneFingerprint, ignoreCase = true) -> T3RecoveryClassification.CANDIDATE
                else -> T3RecoveryClassification.UNKNOWN_TUNE
            }
        }

        /** Only exact baseline recovery may clear the marker automatically; candidate never replays. */
        fun clearRecoveredBaseline(
            store: T3RamRecoveryMarkerStore,
            observed: TuneSnapshot,
            context: TuningContext
        ): Boolean {
            val read = store.load()
            if (classifyRecovery(read, observed, context) != T3RecoveryClassification.BASELINE) return false
            return store.clear()
        }
    }
}
