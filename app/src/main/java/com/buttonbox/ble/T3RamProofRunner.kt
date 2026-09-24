package com.buttonbox.ble

/**
 * Narrow transport seam for the W3 pilot. The production implementation belongs inside the
 * existing UsbEcuManager single executor; this interface deliberately has no arbitrary address,
 * raw USB endpoint, retry or burn operation.
 */
internal interface T3RamTransportPort {
    fun exchange(body: ByteArray, maxResponseBody: Int, label: String): T3RamTransportResponse
    fun readCompleteTuneSnapshot(expectedGeneration: Long): TuneSnapshot
    fun readSafetyInputs(context: TuningContext): T3RamSafetyInputs
}

internal data class T3RamTransportResponse(
    val body: ByteArray,
    val generation: Long
)

internal enum class T3RamProofOutcome {
    BASELINE_RESTORED,
    EXPLICITLY_REJECTED,
    SAFE_ABORTED,
    RECOVERY_REQUIRED
}

internal data class T3RamProofResult(
    val outcome: T3RamProofOutcome,
    val state: T3RamTransactionState,
    val blockReason: T3RamBlockReason,
    val transmissionAttempts: Int,
    val candidateTuneFingerprint: String,
    val finalTuneFingerprint: String? = null,
    val detail: String = ""
)

/**
 * Executes the already-approved paired RAM proof with no retry and no burn.
 *
 * The recovery marker is armed by T3RamScalarTransaction before this runner obtains each C body.
 * From that point onward any transport/parser/read-back/snapshot/safety exception is conservatively
 * classified as RECOVERY_REQUIRED. A successful candidate is restored immediately; there is no
 * API to leave the ordinary W3 proof intentionally parked at the candidate value.
 */
internal class T3RamProofRunner(
    private val plan: TuningScalarChangePlan,
    private val transaction: T3RamScalarTransaction,
    private val transport: T3RamTransportPort
) {
    init {
        T3PilotAuthority.requirePlan(plan)
    }

    fun executeApproved(): T3RamProofResult {
        if (transaction.state != T3RamTransactionState.APPROVED) {
            return result(T3RamProofOutcome.SAFE_ABORTED, detail = "Transaction is not explicitly approved")
        }

        if (!transaction.armCandidateWrite()) {
            return result(
                if (transaction.state == T3RamTransactionState.RECOVERY_REQUIRED) T3RamProofOutcome.RECOVERY_REQUIRED
                else T3RamProofOutcome.SAFE_ABORTED,
                detail = "Candidate write could not be durably armed"
            )
        }

        val candidateBody = transaction.beginCandidateWrite()
            ?: return result(
                if (transaction.state == T3RamTransactionState.RECOVERY_REQUIRED) T3RamProofOutcome.RECOVERY_REQUIRED
                else T3RamProofOutcome.SAFE_ABORTED,
                detail = "Candidate write body was not released"
            )

        val candidateAck = exchangeAfterPossibleMutation(candidateBody, 1, "T3 C candidate")
            ?: return recovery("Candidate write acknowledgement unavailable")
        transaction.acknowledgeCandidateWrite(candidateAck.body, candidateAck.generation)
        when (transaction.state) {
            T3RamTransactionState.REJECTED -> return result(T3RamProofOutcome.EXPLICITLY_REJECTED, detail = "ECU explicitly rejected candidate write")
            T3RamTransactionState.ACKNOWLEDGED -> Unit
            else -> return recovery("Candidate write acknowledgement was not trustworthy")
        }

        val readBackResponse = exchangeAfterPossibleMutation(
            T3PilotRamProtocol.readBackBody(plan),
            plan.target.byteSize + 1,
            "T3 R candidate read-back"
        ) ?: return recovery("Candidate read-back unavailable")
        if (!transaction.verifyCandidateReadBack(readBackResponse.body, readBackResponse.generation)) {
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

        val restoreSafety = try {
            transport.readSafetyInputs(candidateContext)
        } catch (error: Exception) {
            transaction.markTransportOrVerificationUncertain()
            return recovery("Restore safety sample failed: ${safeMessage(error)}")
        }
        if (!transaction.armRestore(restoreSafety)) {
            return recovery("Restore safety/marker gate blocked while candidate remained in RAM")
        }

        val restoreBody = transaction.beginRestoreWrite()
            ?: return recovery("Restore write body was not released")
        val restoreAck = exchangeAfterPossibleMutation(restoreBody, 1, "T3 C restore")
            ?: return recovery("Restore acknowledgement unavailable")
        transaction.acknowledgeRestore(restoreAck.body, restoreAck.generation)
        if (transaction.state != T3RamTransactionState.RESTORE_ACKNOWLEDGED) {
            return recovery("Restore acknowledgement was rejected or untrustworthy")
        }

        val restoredReadBack = exchangeAfterPossibleMutation(
            T3PilotRamProtocol.readBackBody(plan),
            plan.target.byteSize + 1,
            "T3 R restore read-back"
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
            return recovery("Final complete TuneSnapshot did not restore exact baseline fingerprint")
        }

        return result(
            T3RamProofOutcome.BASELINE_RESTORED,
            finalTuneFingerprint = finalSnapshot.fingerprint,
            detail = "Candidate verified and original baseline restored exactly"
        )
    }

    private fun exchangeAfterPossibleMutation(
        body: ByteArray,
        maxResponseBody: Int,
        label: String
    ): T3RamTransportResponse? = try {
        transport.exchange(body.copyOf(), maxResponseBody, label)
    } catch (_: Exception) {
        transaction.markTransportOrVerificationUncertain()
        null
    }

    private fun recovery(detail: String): T3RamProofResult {
        if (transaction.state != T3RamTransactionState.RECOVERY_REQUIRED) {
            transaction.markTransportOrVerificationUncertain()
        }
        return result(T3RamProofOutcome.RECOVERY_REQUIRED, detail = detail)
    }

    private fun result(
        outcome: T3RamProofOutcome,
        finalTuneFingerprint: String? = null,
        detail: String
    ): T3RamProofResult = T3RamProofResult(
        outcome = outcome,
        state = transaction.state,
        blockReason = transaction.lastBlockReason,
        transmissionAttempts = transaction.transmissionAttempts,
        candidateTuneFingerprint = transaction.candidateTuneFingerprint,
        finalTuneFingerprint = finalTuneFingerprint,
        detail = detail.take(240)
    )

    private fun safeMessage(error: Exception): String = (error.message ?: error.javaClass.simpleName).take(160)
}
