package com.buttonbox.ble

import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningW2FaultInjectionTest {
    @Test
    fun exactW0PilotWirePlanIsDeterministicAndResponseEnvelopeRoundTrips() {
        val fixture = fixture()
        val writeBody = W2WireFixture.writeBody(fixture.proposal)
        assertArrayEquals(
            byteArrayOf(0x43, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x00, 0x28, 0x0A),
            writeBody
        )
        assertArrayEquals(
            byteArrayOf(0x52, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x00),
            W2WireFixture.readBackBody(fixture.proposal)
        )

        val frameA = W2WireFixture.envelope(writeBody)
        val frameB = W2WireFixture.envelope(writeBody)
        assertArrayEquals(frameA, frameB)
        assertEquals(0, frameA[0].toInt() and 0xff)
        assertEquals(9, frameA[1].toInt() and 0xff)

        val response = W2WireFixture.decodeResponse(W2WireFixture.response(0x00, byteArrayOf(0x28, 0x0A)))
        assertEquals(0x00, response.responseCode)
        assertArrayEquals(byteArrayOf(0x28, 0x0A), response.payload)
    }

    @Test
    fun correctAckAndExactReadBackReachVerifiedWithoutBurn() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        assertTrue(harness.beginTransmission("pilot-action", fixture.proposal.previewId, fixture.safety))
        assertEquals(W2TransactionState.TRANSMITTING, harness.state)
        assertEquals(1, harness.transmissionAttempts)
        assertNotNull(harness.recoveryMarker)

        harness.acknowledge(0x00, fixture.safety.generation)
        assertEquals(W2TransactionState.ACKNOWLEDGED, harness.state)
        harness.readBack(fixture.proposal.encodedBytes())
        assertEquals(W2TransactionState.READBACK_VERIFIED, harness.state)
        assertNull(harness.recoveryMarker)
        assertFalse(harness.requestBurn())
        assertEquals(1, harness.burnRequestsRejected)
        assertTrue(harness.readOnlyOperationAvailable)
    }

    @Test
    fun burnRequestsAreRejectedBeforeAndAfterRamVerification() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        assertFalse(harness.requestBurn())
        assertTrue(harness.beginTransmission("burn-gate", fixture.proposal.previewId, fixture.safety))
        assertFalse(harness.requestBurn())
        harness.acknowledge(0x00, fixture.safety.generation)
        assertFalse(harness.requestBurn())
        harness.readBack(fixture.proposal.encodedBytes())
        assertFalse(harness.requestBurn())
        assertEquals(4, harness.burnRequestsRejected)
    }

    @Test
    fun explicitEcuRejectionIsDeterministicRejectionNotSuccess() {
        for (code in listOf(0x80, 0x81, 0x82, 0x83, 0x84, 0x8D)) {
            val fixture = fixture()
            val harness = fixture.harness()
            assertTrue(harness.prepare(fixture.safety))
            assertTrue(harness.beginTransmission("action-$code", fixture.proposal.previewId, fixture.safety))
            harness.acknowledge(code, fixture.safety.generation)
            assertEquals(W2TransactionState.REJECTED, harness.state)
            assertNull(harness.recoveryMarker)
            assertTrue(harness.readOnlyOperationAvailable)
        }
    }

    @Test
    fun timeoutBeforeTransmissionAbortsWithoutCreatingUncertainRamState() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        harness.timeoutBeforeTransmission()
        assertEquals(W2TransactionState.SAFE_ABORTED, harness.state)
        assertEquals(0, harness.transmissionAttempts)
        assertNull(harness.recoveryMarker)
        assertTrue(harness.readOnlyOperationAvailable)
    }

    @Test
    fun timeoutDisconnectOrProtocolFailureAfterPossibleTransmissionRequiresRecovery() {
        for (inject in listOf<(W2RamTransactionHarness) -> Unit>(
            { it.timeoutAfterPossibleTransmission() },
            { it.disconnectAfterPossibleTransmission() },
            { it.protocolFailureAfterPossibleTransmission() }
        )) {
            val fixture = fixture()
            val harness = fixture.harness()
            assertTrue(harness.prepare(fixture.safety))
            assertTrue(harness.beginTransmission("fault", fixture.proposal.previewId, fixture.safety))
            inject(harness)
            assertEquals(W2TransactionState.RECOVERY_REQUIRED, harness.state)
            assertNotNull(harness.recoveryMarker)
            assertFalse(harness.beginTransmission("retry", fixture.proposal.previewId, fixture.safety))
            assertTrue(harness.readOnlyOperationAvailable)
        }
    }

    @Test
    fun staleAcknowledgementAndWrongReadBackNeverBecomeSuccess() {
        val stale = fixture()
        val staleHarness = stale.harness()
        assertTrue(staleHarness.prepare(stale.safety))
        assertTrue(staleHarness.beginTransmission("stale", stale.proposal.previewId, stale.safety))
        staleHarness.acknowledge(0x00, stale.safety.generation + 1)
        assertEquals(W2TransactionState.RECOVERY_REQUIRED, staleHarness.state)

        val wrong = fixture()
        val wrongHarness = wrong.harness()
        assertTrue(wrongHarness.prepare(wrong.safety))
        assertTrue(wrongHarness.beginTransmission("wrong-readback", wrong.proposal.previewId, wrong.safety))
        wrongHarness.acknowledge(0x00, wrong.safety.generation)
        wrongHarness.readBack(byteArrayOf(0x29, 0x0A))
        assertEquals(W2TransactionState.RECOVERY_REQUIRED, wrongHarness.state)
    }

    @Test
    fun duplicateActionCannotCauseASecondTransmission() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        assertTrue(harness.beginTransmission("same-action", fixture.proposal.previewId, fixture.safety))
        assertFalse(harness.beginTransmission("same-action", fixture.proposal.previewId, fixture.safety))
        assertFalse(harness.beginTransmission("different-action", fixture.proposal.previewId, fixture.safety))
        assertEquals(1, harness.transmissionAttempts)
    }

    @Test
    fun processDeathAfterPossibleWriteBlocksAndNeverReplays() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        assertTrue(harness.beginTransmission("process-death", fixture.proposal.previewId, fixture.safety))
        val marker = harness.recoveryMarker
        assertNotNull(marker)

        val restart = W2RamTransactionHarness.afterProcessRestart(marker)
        assertTrue(restart.writeBlocked)
        assertTrue(restart.recoveryRequired)
        assertFalse(restart.replayAllowed)

        val cleanRestart = W2RamTransactionHarness.afterProcessRestart(null)
        assertFalse(cleanRestart.recoveryRequired)
        assertFalse(cleanRestart.replayAllowed)
    }

    @Test
    fun pendingFlashFlashErrorLowVoltageRunningEngineAndChangedIdentityAllBlockBeforeTransmission() {
        val fixture = fixture()
        val blocked = listOf(
            fixture.safety.copy(flashWritePending = true),
            fixture.safety.copy(flashWriteErrors = 1),
            fixture.safety.copy(voltage = 11.99),
            fixture.safety.copy(engineStopped = false),
            fixture.safety.copy(cranking = true),
            fixture.safety.copy(tuneFingerprint = "0".repeat(64)),
            fixture.safety.copy(profileFingerprint = "1".repeat(64)),
            fixture.safety.copy(ecuSignature = "different ECU"),
            fixture.safety.copy(sessionId = fixture.safety.sessionId + 1),
            fixture.safety.copy(generation = fixture.safety.generation + 1),
            fixture.safety.copy(source = TuningDataSource.MSL)
        )

        blocked.forEach { inputs ->
            val harness = fixture.harness()
            assertFalse(harness.prepare(inputs))
            assertEquals(W2TransactionState.SAFE_ABORTED, harness.state)
            assertEquals(0, harness.transmissionAttempts)
            assertTrue(harness.readOnlyOperationAvailable)
        }
    }

    @Test
    fun recoveryRequiresExactIdentityCompleteReadAndOnlyExpectedTargetDifference() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        assertTrue(harness.beginTransmission("uncertain", fixture.proposal.previewId, fixture.safety))
        harness.timeoutAfterPossibleTransmission()

        val validBase = recoveryEvidence(fixture, fixture.proposal.originalBytes())
        assertFalse(harness.recover(validBase.copy(completeTuneRead = false)))
        assertFalse(harness.recover(validBase.copy(onlyExpectedTargetBytesDiffer = false)))
        assertFalse(harness.recover(validBase.copy(observedRaw = byteArrayOf(0x30, 0x0A))))
        assertFalse(harness.recover(validBase.copy(ecuSignature = "different ECU")))
        assertFalse(harness.recover(validBase.copy(tuneFingerprint = "different")))
        assertTrue(harness.recover(validBase))
        assertEquals(W2TransactionState.IDLE, harness.state)
        assertNull(harness.recoveryMarker)
    }

    @Test
    fun reconnectWithNewGenerationCanRecoverStateButCannotReuseOldProposal() {
        val fixture = fixture()
        val harness = fixture.harness()
        assertTrue(harness.prepare(fixture.safety))
        assertTrue(harness.beginTransmission("detach", fixture.proposal.previewId, fixture.safety))
        harness.disconnectAfterPossibleTransmission()

        val newSession = fixture.safety.copy(
            sessionId = fixture.safety.sessionId + 100,
            generation = fixture.safety.generation + 1
        )
        val recovered = recoveryEvidence(
            fixture,
            fixture.proposal.originalBytes(),
            sessionId = newSession.sessionId,
            generation = newSession.generation
        )
        assertTrue(harness.recover(recovered))
        assertFalse(harness.prepare(newSession))
        assertEquals(W2TransactionState.SAFE_ABORTED, harness.state)
        assertEquals(1, harness.transmissionAttempts)
    }

    @Test
    fun malformedTruncatedAndCrcCorruptedResponseFramesFailClosedUnderFuzz() {
        val fixture = fixture()
        val valid = W2WireFixture.response(0x00, fixture.proposal.encodedBytes())
        val random = Random(0x5732L)

        for (cut in 0 until valid.size) {
            val truncated = valid.copyOf(cut)
            assertThrows(IllegalArgumentException::class.java) { W2WireFixture.decodeResponse(truncated) }
        }

        repeat(1000) {
            val corrupted = valid.copyOf()
            val index = 2 + random.nextInt(corrupted.size - 2)
            corrupted[index] = (corrupted[index].toInt() xor (1 shl random.nextInt(8))).toByte()
            assertThrows(IllegalArgumentException::class.java) { W2WireFixture.decodeResponse(corrupted) }
        }
    }

    private data class Fixture(
        val proposal: TuningScalarChangePlan,
        val safety: W2SafetyInputs
    ) {
        fun harness(): W2RamTransactionHarness = W2RamTransactionHarness(proposal, minimumVoltage = 12.0)
    }

    private fun recoveryEvidence(
        fixture: Fixture,
        raw: ByteArray,
        sessionId: Long = fixture.safety.sessionId,
        generation: Long = fixture.safety.generation
    ) = W2RecoveryEvidence(
        sessionId = sessionId,
        generation = generation,
        source = TuningDataSource.LIVE,
        ecuSignature = fixture.safety.ecuSignature,
        profileFingerprint = fixture.safety.profileFingerprint,
        tuneFingerprint = fixture.safety.tuneFingerprint,
        completeTuneRead = true,
        onlyExpectedTargetBytesDiffer = true,
        observedRaw = raw
    )

    private fun fixture(): Fixture {
        val scalar = UsbTuneScalar(
            name = "engineSnifferRpmThreshold",
            pageNumber = 1,
            dataType = "U16",
            offset = 12,
            unit = "RPM",
            scale = 1.0,
            translate = 0.0,
            low = 0.0,
            high = 30000.0,
            digits = 0
        )
        val profile = UsbTunerStudioProfile(
            signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132",
            queryCommand = "S",
            outputCommand = "O%2o%2c",
            outputBlockSize = 64,
            envelopeFormat = "msEnvelope_1.0",
            endianness = "little",
            channels = emptyList(),
            tunePages = listOf(UsbTunePage(1, 0x0000, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
            tuneScalars = listOf(scalar),
            importedName = "mainController.ini"
        )
        val page = ByteArray(32)
        page[12] = 0xC4.toByte()
        page[13] = 0x09
        val snapshot = TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(1, 0x0000, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, page)),
            generation = 77,
            capturedAtEpochMs = 1L
        )
        val context = TuningContext(
            sessionId = 9001,
            generation = snapshot.generation,
            source = TuningDataSource.LIVE,
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            tuneFingerprint = snapshot.fingerprint
        )
        val core = SimulationTuningCore(
            profile = profile,
            snapshot = snapshot,
            context = context,
            allowlistedScalarNames = setOf("engineSnifferRpmThreshold")
        )
        val proposal = core.propose(core.editScalar("engineSnifferRpmThreshold", 2600.0))
        val safety = W2SafetyInputs(
            sessionId = context.sessionId,
            generation = context.generation,
            source = TuningDataSource.LIVE,
            ecuSignature = context.ecuSignature,
            profileFingerprint = context.profileFingerprint,
            tuneFingerprint = context.tuneFingerprint,
            engineStopped = true,
            cranking = false,
            voltage = 12.5,
            flashWritePending = false,
            flashWriteErrors = 0
        )
        return Fixture(proposal, safety)
    }
}
