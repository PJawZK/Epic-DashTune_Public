package com.buttonbox.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Test-only W2 transaction fixture. It models the future one-scalar T3 state machine without
 * owning USB, emitting a real transfer, or changing packaged application capability.
 */
internal enum class W2TransactionState {
    IDLE,
    READY,
    TRANSMITTING,
    ACKNOWLEDGED,
    READBACK_VERIFIED,
    REJECTED,
    SAFE_ABORTED,
    RECOVERY_REQUIRED
}

internal data class W2SafetyInputs(
    val sessionId: Long,
    val generation: Long,
    val source: TuningDataSource,
    val ecuSignature: String,
    val profileFingerprint: String,
    val tuneFingerprint: String,
    val engineStopped: Boolean,
    val cranking: Boolean,
    val voltage: Double,
    val flashWritePending: Boolean,
    val flashWriteErrors: Int
)

/** Marker that would have to survive a real process death once runtime write support exists. */
internal data class W2RecoveryMarker(
    val previewId: String,
    val sessionId: Long,
    val generation: Long,
    val ecuSignature: String,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val targetDefinitionFingerprint: String,
    val originalRawHex: String,
    val proposedRawHex: String
)

internal data class W2RestartDecision(
    val writeBlocked: Boolean,
    val replayAllowed: Boolean,
    val recoveryRequired: Boolean
)

internal data class W2RecoveryEvidence(
    val sessionId: Long,
    val generation: Long,
    val source: TuningDataSource,
    val ecuSignature: String,
    val profileFingerprint: String,
    val tuneFingerprint: String,
    val completeTuneRead: Boolean,
    val onlyExpectedTargetBytesDiffer: Boolean,
    val observedRaw: ByteArray
)

internal class W2RamTransactionHarness(
    private val proposal: TuningScalarChangePlan,
    /** Simulator policy input only. This is not an accepted physical Mega144H7 threshold. */
    private val minimumVoltage: Double
) {
    var state: W2TransactionState = W2TransactionState.IDLE
        private set
    var transmissionAttempts: Int = 0
        private set
    var burnRequestsRejected: Int = 0
        private set
    var recoveryMarker: W2RecoveryMarker? = null
        private set
    val readOnlyOperationAvailable: Boolean = true
    private var activeActionId: String? = null

    init {
        require(minimumVoltage.isFinite() && minimumVoltage > 0.0)
        require(proposal.state == TuningValueState.PROPOSED)
        require(!proposal.transmitted)
        require(!proposal.burnRequested)
        require(proposal.transmissionState == TuningTransmissionState.NOT_TRANSMITTED)
    }

    fun prepare(inputs: W2SafetyInputs): Boolean {
        if (state != W2TransactionState.IDLE && state != W2TransactionState.SAFE_ABORTED) return false
        if (!preconditionsMatch(inputs)) {
            state = W2TransactionState.SAFE_ABORTED
            return false
        }
        activeActionId = null
        state = W2TransactionState.READY
        return true
    }

    /** Represents the instant the transport may have started emitting the C frame. */
    fun beginTransmission(actionId: String, previewId: String, inputs: W2SafetyInputs): Boolean {
        if (state != W2TransactionState.READY) return false
        if (activeActionId != null) return false
        if (actionId.isBlank() || previewId != proposal.previewId || !preconditionsMatch(inputs)) {
            state = W2TransactionState.SAFE_ABORTED
            return false
        }
        activeActionId = actionId
        recoveryMarker = W2RecoveryMarker(
            previewId = proposal.previewId,
            sessionId = proposal.context.sessionId,
            generation = proposal.context.generation,
            ecuSignature = proposal.context.ecuSignature,
            profileFingerprint = proposal.context.profileFingerprint,
            baselineTuneFingerprint = proposal.context.tuneFingerprint,
            targetDefinitionFingerprint = proposal.target.definitionFingerprint,
            originalRawHex = proposal.originalRawHex,
            proposedRawHex = proposal.proposedRawHex
        )
        transmissionAttempts++
        state = W2TransactionState.TRANSMITTING
        return true
    }

    fun timeoutBeforeTransmission() {
        require(state == W2TransactionState.READY)
        state = W2TransactionState.SAFE_ABORTED
    }

    fun acknowledge(responseCode: Int, responseGeneration: Long) {
        require(state == W2TransactionState.TRANSMITTING)
        if (responseGeneration != proposal.context.generation) {
            requireRecovery()
            return
        }
        if (responseCode == 0x00) {
            state = W2TransactionState.ACKNOWLEDGED
        } else {
            state = W2TransactionState.REJECTED
            recoveryMarker = null
            activeActionId = null
        }
    }

    fun timeoutAfterPossibleTransmission() {
        require(state == W2TransactionState.TRANSMITTING || state == W2TransactionState.ACKNOWLEDGED)
        requireRecovery()
    }

    fun disconnectAfterPossibleTransmission() {
        require(state == W2TransactionState.TRANSMITTING || state == W2TransactionState.ACKNOWLEDGED)
        requireRecovery()
    }

    fun protocolFailureAfterPossibleTransmission() {
        require(state == W2TransactionState.TRANSMITTING || state == W2TransactionState.ACKNOWLEDGED)
        requireRecovery()
    }

    fun readBack(raw: ByteArray) {
        require(state == W2TransactionState.ACKNOWLEDGED)
        if (raw.contentEquals(proposal.encodedBytes())) {
            state = W2TransactionState.READBACK_VERIFIED
            recoveryMarker = null
            activeActionId = null
        } else {
            requireRecovery()
        }
    }

    /** T3/W2 has no burn execution path. A burn request is always rejected in every state. */
    fun requestBurn(): Boolean {
        burnRequestsRejected++
        return false
    }

    /**
     * Recovery can classify current RAM only after a new live identity and complete tune read are
     * known. It never replays the old proposal. A new generation/session needs a new proposal.
     */
    fun recover(evidence: W2RecoveryEvidence): Boolean {
        if (state != W2TransactionState.RECOVERY_REQUIRED) return false
        if (evidence.sessionId <= 0L || evidence.generation < 0L ||
            evidence.source != TuningDataSource.LIVE ||
            evidence.ecuSignature != proposal.context.ecuSignature ||
            !evidence.profileFingerprint.equals(proposal.context.profileFingerprint, ignoreCase = true) ||
            evidence.tuneFingerprint.isBlank() ||
            !evidence.completeTuneRead ||
            !evidence.onlyExpectedTargetBytesDiffer
        ) return false

        val isBaseline = evidence.observedRaw.contentEquals(proposal.originalBytes())
        val isCandidate = evidence.observedRaw.contentEquals(proposal.encodedBytes())
        if (!isBaseline && !isCandidate) return false
        if (isBaseline && !evidence.tuneFingerprint.equals(proposal.context.tuneFingerprint, ignoreCase = true)) return false

        recoveryMarker = null
        activeActionId = null
        state = W2TransactionState.IDLE
        return true
    }

    private fun preconditionsMatch(inputs: W2SafetyInputs): Boolean =
        inputs.sessionId == proposal.context.sessionId &&
            inputs.generation == proposal.context.generation &&
            inputs.source == TuningDataSource.LIVE &&
            inputs.ecuSignature == proposal.context.ecuSignature &&
            inputs.profileFingerprint.equals(proposal.context.profileFingerprint, ignoreCase = true) &&
            inputs.tuneFingerprint.equals(proposal.context.tuneFingerprint, ignoreCase = true) &&
            inputs.engineStopped &&
            !inputs.cranking &&
            inputs.voltage.isFinite() &&
            inputs.voltage >= minimumVoltage &&
            !inputs.flashWritePending &&
            inputs.flashWriteErrors == 0

    private fun requireRecovery() {
        state = W2TransactionState.RECOVERY_REQUIRED
        activeActionId = null
    }

    companion object {
        fun afterProcessRestart(marker: W2RecoveryMarker?): W2RestartDecision =
            if (marker == null) {
                W2RestartDecision(writeBlocked = false, replayAllowed = false, recoveryRequired = false)
            } else {
                W2RestartDecision(writeBlocked = true, replayAllowed = false, recoveryRequired = true)
            }
    }
}

internal data class W2DecodedResponse(val responseCode: Int, val payload: ByteArray)

/** Pure test fixture for the W0-proven msEnvelope_1.0 packet grammar. No I/O is performed. */
internal object W2WireFixture {
    fun writeBody(plan: TuningScalarChangePlan): ByteArray {
        require(plan.state == TuningValueState.PROPOSED)
        val value = plan.encodedBytes()
        require(value.size == plan.target.byteSize)
        require(plan.target.pageIdentifier in 0..0xffff)
        require(plan.target.offset in 0..0xffff)
        require(plan.target.byteSize in 1..0xffff)
        return byteArrayOf(
            'C'.code.toByte(),
            (plan.target.pageIdentifier and 0xff).toByte(),
            ((plan.target.pageIdentifier ushr 8) and 0xff).toByte(),
            (plan.target.offset and 0xff).toByte(),
            ((plan.target.offset ushr 8) and 0xff).toByte(),
            (plan.target.byteSize and 0xff).toByte(),
            ((plan.target.byteSize ushr 8) and 0xff).toByte()
        ) + value
    }

    fun readBackBody(plan: TuningScalarChangePlan): ByteArray = byteArrayOf(
        'R'.code.toByte(),
        (plan.target.pageIdentifier and 0xff).toByte(),
        ((plan.target.pageIdentifier ushr 8) and 0xff).toByte(),
        (plan.target.offset and 0xff).toByte(),
        ((plan.target.offset ushr 8) and 0xff).toByte(),
        (plan.target.byteSize and 0xff).toByte(),
        ((plan.target.byteSize ushr 8) and 0xff).toByte()
    )

    fun envelope(body: ByteArray): ByteArray {
        require(body.isNotEmpty() && body.size <= 0xffff)
        val crc = CRC32().apply { update(body) }.value
        return ByteBuffer.allocate(body.size + 6)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(body.size.toShort())
            .put(body)
            .putInt(crc.toInt())
            .array()
    }

    fun response(responseCode: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(responseCode in 0..255)
        return envelope(byteArrayOf(responseCode.toByte()) + payload)
    }

    fun decodeResponse(frame: ByteArray): W2DecodedResponse {
        require(frame.size >= 7) { "Truncated response frame" }
        val bodyLength = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
        require(bodyLength >= 1) { "Response body must include status byte" }
        require(frame.size == bodyLength + 6) { "Response frame length mismatch" }
        val body = frame.copyOfRange(2, 2 + bodyLength)
        val expectedCrc = ByteBuffer.wrap(frame, 2 + bodyLength, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffff_ffffL
        val actualCrc = CRC32().apply { update(body) }.value
        require(expectedCrc == actualCrc) { "Response CRC mismatch" }
        return W2DecodedResponse(body[0].toInt() and 0xff, body.copyOfRange(1, body.size))
    }
}
