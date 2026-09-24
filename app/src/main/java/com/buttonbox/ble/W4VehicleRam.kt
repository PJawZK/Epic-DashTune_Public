package com.buttonbox.ble

import org.json.JSONObject

/**
 * W4 vehicle RAM-only capability policy.
 *
 * W4 accepts any uniquely resolved scalar whose primitive and storage range are supported by the
 * existing scalar codec and bounded C/R protocol. Page/offset/type/width/scaling/bounds always come
 * from the exact current imported INI; callers never supply storage metadata.
 */
internal object W4VehicleRamPolicy {
    const val AUTHORIZATION_MAX_AGE_MS = 120_000L
    const val ENTER_VEHICLE_RAM_ONLY_ACTION_ID = "w4-enter-vehicle-ram-only-v2"
}

internal class W4VehicleSessionAuthorization(
    private val maximumAgeMs: Long = W4VehicleRamPolicy.AUTHORIZATION_MAX_AGE_MS
) {
    init {
        require(maximumAgeMs > 0L) { "W4 vehicle authorization age must be positive" }
    }

    private var generation: Long = -1L
    private var confirmedAtElapsedMs: Long = -1L

    @Synchronized
    fun confirm(actionId: String, currentGeneration: Long, observedEcuVoltage: Double, nowElapsedMs: Long): Boolean {
        clear()
        if (actionId != W4VehicleRamPolicy.ENTER_VEHICLE_RAM_ONLY_ACTION_ID) return false
        if (currentGeneration <= 0L || nowElapsedMs < 0L) return false
        if (!observedEcuVoltage.isFinite() || observedEcuVoltage <= 0.0) return false
        generation = currentGeneration
        confirmedAtElapsedMs = nowElapsedMs
        return true
    }

    @Synchronized
    fun isConfirmed(currentGeneration: Long, nowElapsedMs: Long): Boolean {
        if (generation <= 0L || confirmedAtElapsedMs < 0L) return false
        if (currentGeneration != generation || nowElapsedMs < confirmedAtElapsedMs) return false
        return nowElapsedMs - confirmedAtElapsedMs <= maximumAgeMs
    }

    @Synchronized
    fun consume(currentGeneration: Long, nowElapsedMs: Long): Boolean {
        val accepted = isConfirmed(currentGeneration, nowElapsedMs)
        clear()
        return accepted
    }

    @Synchronized
    fun clear() {
        generation = -1L
        confirmedAtElapsedMs = -1L
    }
}

internal class W4VehicleManagerGate(
    private val authorization: W4VehicleSessionAuthorization = W4VehicleSessionAuthorization()
) {
    fun enter(actionId: String, generation: Long, observedEcuVoltage: Double, nowElapsedMs: Long): Boolean =
        authorization.confirm(actionId, generation, observedEcuVoltage, nowElapsedMs)

    fun isAuthorized(generation: Long, nowElapsedMs: Long): Boolean =
        authorization.isConfirmed(generation, nowElapsedMs)

    fun consumeForAttempt(generation: Long, nowElapsedMs: Long): Boolean =
        authorization.consume(generation, nowElapsedMs)

    fun clear() = authorization.clear()

    fun writePolicy(): T3BenchWritePolicy = T3BenchWritePolicy(
        minimumVoltage = null,
        requireSupportPowerConfirmation = true
    )
}

internal data class W4VehicleRamPreview(
    val previewId: String,
    val generation: Long,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val scalarName: String,
    val unit: String,
    val currentValue: Double,
    val requestedValue: Double,
    val effectiveValue: Double
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", "ready")
        .put("capability", "VEHICLE_RAM_ONLY")
        .put("previewId", previewId)
        .put("generation", generation)
        .put("profileFingerprint", profileFingerprint)
        .put("tuneFingerprint", baselineTuneFingerprint)
        .put("name", scalarName)
        .put("unit", unit)
        .put("currentValue", currentValue)
        .put("requestedValue", requestedValue)
        .put("effectiveValue", effectiveValue)
        .put("transmitted", false)
        .put("burnRequested", false)
}

internal data class W4VehicleRamEntryResult(
    val authorized: Boolean,
    val generation: Long,
    val observedVoltage: Double
)

internal enum class W4VehicleRamOutcome {
    APPLIED_RAM_ONLY,
    BASELINE_RESTORED,
    EXPLICITLY_REJECTED,
    SAFE_ABORTED,
    RECOVERY_REQUIRED
}

internal data class W4VehicleRamResult(
    val outcome: W4VehicleRamOutcome,
    val state: T3RamTransactionState,
    val blockReason: T3RamBlockReason,
    val transmissionAttempts: Int,
    val candidateTuneFingerprint: String,
    val finalTuneFingerprint: String? = null,
    val detail: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("outcome", outcome.name)
        .put("state", state.name)
        .put("blockReason", blockReason.name)
        .put("transmissionAttempts", transmissionAttempts)
        .put("candidateTuneFingerprint", candidateTuneFingerprint)
        .put("finalTuneFingerprint", finalTuneFingerprint ?: JSONObject.NULL)
        .put("detail", detail)
        .put("burnRequested", false)
}

internal data class W4VehicleRamExecution(
    val result: W4VehicleRamResult,
    val verifiedSnapshot: TuneSnapshot? = null
)

/**
 * Splits the accepted T3 paired proof into explicit W4 apply and explicit W4 restore phases.
 *
 * Apply stops only after exact scalar read-back and complete candidate TuneSnapshot verification.
 * The durable recovery marker remains present while the candidate is intentionally held in RAM.
 * Restore performs the same safety/read-back/full-snapshot verification before clearing that marker.
 */
internal class W4VehicleRamRunner(
    private val plan: TuningScalarChangePlan,
    private val transaction: T3RamScalarTransaction,
    private val transport: T3RamTransportPort
) {
    init {
        RamScalarAuthority.requirePlan(plan)
    }

    fun applyApproved(): W4VehicleRamExecution {
        if (transaction.state != T3RamTransactionState.APPROVED) {
            return result(W4VehicleRamOutcome.SAFE_ABORTED, "Transaction is not explicitly approved")
        }
        if (!transaction.armCandidateWrite()) {
            return result(
                if (transaction.state == T3RamTransactionState.RECOVERY_REQUIRED) W4VehicleRamOutcome.RECOVERY_REQUIRED
                else W4VehicleRamOutcome.SAFE_ABORTED,
                "Candidate write could not be durably armed"
            )
        }

        val candidateBody = transaction.beginCandidateWrite()
            ?: return result(
                if (transaction.state == T3RamTransactionState.RECOVERY_REQUIRED) W4VehicleRamOutcome.RECOVERY_REQUIRED
                else W4VehicleRamOutcome.SAFE_ABORTED,
                "Candidate write body was not released"
            )

        val candidateAck = exchangeAfterPossibleMutation(candidateBody, 1, T3BoundTransportPort.LABEL_CANDIDATE)
            ?: return recovery("Candidate write acknowledgement unavailable")
        transaction.acknowledgeCandidateWrite(candidateAck.body, candidateAck.generation)
        when (transaction.state) {
            T3RamTransactionState.REJECTED ->
                return result(W4VehicleRamOutcome.EXPLICITLY_REJECTED, "ECU explicitly rejected candidate write")
            T3RamTransactionState.ACKNOWLEDGED -> Unit
            else -> return recovery("Candidate write acknowledgement was not trustworthy")
        }

        val readBack = exchangeAfterPossibleMutation(
            T3PilotRamProtocol.readBackBody(plan),
            plan.target.byteSize + 1,
            T3BoundTransportPort.LABEL_CANDIDATE_READBACK
        ) ?: return recovery("Candidate read-back unavailable")
        if (!transaction.verifyCandidateReadBack(readBack.body, readBack.generation)) {
            return recovery("Candidate read-back did not exactly match proposed bytes")
        }

        val candidateSnapshot = try {
            transport.readCompleteTuneSnapshot(plan.context.generation)
        } catch (error: Exception) {
            transaction.markTransportOrVerificationUncertain()
            return recovery("Candidate TuneSnapshot failed: ${safeMessage(error)}")
        }
        val candidateContext = plan.context.copy(tuneFingerprint = candidateSnapshot.fingerprint)
        if (!transaction.verifyCandidateSnapshot(candidateSnapshot, candidateContext)) {
            return recovery("Complete candidate TuneSnapshot contained an unexpected difference")
        }

        return result(
            W4VehicleRamOutcome.APPLIED_RAM_ONLY,
            "Candidate verified and intentionally left applied in volatile ECU RAM",
            snapshot = candidateSnapshot
        )
    }

    fun restoreApplied(): W4VehicleRamExecution {
        if (transaction.state != T3RamTransactionState.CANDIDATE_SNAPSHOT_VERIFIED) {
            return result(W4VehicleRamOutcome.SAFE_ABORTED, "No verified W4 RAM candidate is awaiting restore")
        }

        val candidateSnapshot = try {
            transport.readCompleteTuneSnapshot(plan.context.generation)
        } catch (error: Exception) {
            transaction.markTransportOrVerificationUncertain()
            return recovery("Pre-restore TuneSnapshot failed: ${safeMessage(error)}")
        }
        if (!candidateSnapshot.fingerprint.equals(transaction.candidateTuneFingerprint, ignoreCase = true)) {
            transaction.markTransportOrVerificationUncertain()
            return recovery("Live tune no longer matches the verified W4 candidate")
        }

        val candidateContext = plan.context.copy(tuneFingerprint = candidateSnapshot.fingerprint)
        val safety = try {
            transport.readSafetyInputs(candidateContext)
        } catch (error: Exception) {
            transaction.markTransportOrVerificationUncertain()
            return recovery("Restore safety sample failed: ${safeMessage(error)}")
        }
        if (!transaction.armRestore(safety)) {
            return recovery("Restore safety/marker gate blocked while candidate remained in RAM")
        }

        val restoreBody = transaction.beginRestoreWrite()
            ?: return recovery("Restore write body was not released")
        val restoreAck = exchangeAfterPossibleMutation(restoreBody, 1, T3BoundTransportPort.LABEL_RESTORE)
            ?: return recovery("Restore acknowledgement unavailable")
        transaction.acknowledgeRestore(restoreAck.body, restoreAck.generation)
        if (transaction.state != T3RamTransactionState.RESTORE_ACKNOWLEDGED) {
            return recovery("Restore acknowledgement was rejected or untrustworthy")
        }

        val restoredReadBack = exchangeAfterPossibleMutation(
            T3PilotRamProtocol.readBackBody(plan),
            plan.target.byteSize + 1,
            T3BoundTransportPort.LABEL_RESTORE_READBACK
        ) ?: return recovery("Restore read-back unavailable")
        if (!transaction.verifyRestoreReadBack(restoredReadBack.body, restoredReadBack.generation)) {
            return recovery("Restore read-back did not exactly match original bytes")
        }

        val finalSnapshot = try {
            transport.readCompleteTuneSnapshot(plan.context.generation)
        } catch (error: Exception) {
            transaction.markTransportOrVerificationUncertain()
            return recovery("Final TuneSnapshot failed: ${safeMessage(error)}")
        }
        val finalContext = plan.context.copy(tuneFingerprint = finalSnapshot.fingerprint)
        if (!transaction.verifyBaselineRestored(finalSnapshot, finalContext)) {
            return recovery("Final complete TuneSnapshot did not restore the exact baseline fingerprint")
        }

        return result(
            W4VehicleRamOutcome.BASELINE_RESTORED,
            "Original baseline restored exactly and durable recovery marker cleared",
            snapshot = finalSnapshot
        )
    }

    private fun exchangeAfterPossibleMutation(body: ByteArray, maxResponseBody: Int, label: String): T3RamTransportResponse? =
        try {
            transport.exchange(body.copyOf(), maxResponseBody, label)
        } catch (_: Exception) {
            transaction.markTransportOrVerificationUncertain()
            null
        }

    private fun recovery(detail: String): W4VehicleRamExecution {
        if (transaction.state != T3RamTransactionState.RECOVERY_REQUIRED) {
            transaction.markTransportOrVerificationUncertain()
        }
        return result(W4VehicleRamOutcome.RECOVERY_REQUIRED, detail)
    }

    private fun result(
        outcome: W4VehicleRamOutcome,
        detail: String,
        snapshot: TuneSnapshot? = null
    ): W4VehicleRamExecution = W4VehicleRamExecution(
        W4VehicleRamResult(
            outcome = outcome,
            state = transaction.state,
            blockReason = transaction.lastBlockReason,
            transmissionAttempts = transaction.transmissionAttempts,
            candidateTuneFingerprint = transaction.candidateTuneFingerprint,
            finalTuneFingerprint = snapshot?.fingerprint,
            detail = detail.take(240)
        ),
        snapshot
    )

    private fun safeMessage(error: Exception): String = (error.message ?: error.javaClass.simpleName).take(160)
}
