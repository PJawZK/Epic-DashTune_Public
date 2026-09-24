package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T3RamProofRunnerTest {
    @Test
    fun runnerExecutesOnlyCandidateReadVerifyRestoreReadVerifyInOrder() {
        val fixture = fixture()
        val store = MemoryStore()
        val transaction = fixture.approvedTransaction(store)
        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, fixture.plan)
        val port = FakePort(
            generation = fixture.context.generation,
            exchanges = mutableListOf(
                Exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), byteArrayOf(0x00)),
                Exchange(T3PilotRamProtocol.readBackBody(fixture.plan), byteArrayOf(0x00, 0x28, 0x0A)),
                Exchange(T3PilotRamProtocol.restoreWriteBody(fixture.plan), byteArrayOf(0x00)),
                Exchange(T3PilotRamProtocol.readBackBody(fixture.plan), byteArrayOf(0x00, 0xC4.toByte(), 0x09))
            ),
            snapshots = mutableListOf(candidate, fixture.baseline),
            safety = fixture.safety(fixture.context.copy(tuneFingerprint = candidate.fingerprint))
        )

        val result = T3RamProofRunner(fixture.plan, transaction, port).executeApproved()

        assertEquals(T3RamProofOutcome.BASELINE_RESTORED, result.outcome)
        assertEquals(T3RamTransactionState.BASELINE_RESTORED, result.state)
        assertEquals(fixture.baseline.fingerprint, result.finalTuneFingerprint)
        assertEquals(2, result.transmissionAttempts)
        assertEquals(4, port.seenBodies.size)
        assertArrayEquals(T3PilotRamProtocol.candidateWriteBody(fixture.plan), port.seenBodies[0])
        assertArrayEquals(T3PilotRamProtocol.readBackBody(fixture.plan), port.seenBodies[1])
        assertArrayEquals(T3PilotRamProtocol.restoreWriteBody(fixture.plan), port.seenBodies[2])
        assertArrayEquals(T3PilotRamProtocol.readBackBody(fixture.plan), port.seenBodies[3])
        assertEquals(T3RecoveryStoreState.ABSENT, store.load().state)
        assertTrue(transaction.readOnlyOperationAvailable)
    }

    @Test
    fun explicitCandidateRejectionStopsBeforeReadbackOrRestoreAndClearsMarker() {
        val fixture = fixture()
        val store = MemoryStore()
        val transaction = fixture.approvedTransaction(store)
        val port = FakePort(
            generation = fixture.context.generation,
            exchanges = mutableListOf(
                Exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), byteArrayOf(0x84.toByte()))
            ),
            snapshots = mutableListOf(),
            safety = fixture.safety()
        )

        val result = T3RamProofRunner(fixture.plan, transaction, port).executeApproved()

        assertEquals(T3RamProofOutcome.EXPLICITLY_REJECTED, result.outcome)
        assertEquals(T3RamTransactionState.REJECTED, result.state)
        assertEquals(1, result.transmissionAttempts)
        assertEquals(1, port.seenBodies.size)
        assertEquals(T3RecoveryStoreState.ABSENT, store.load().state)
    }

    @Test
    fun transportFailureAfterCandidateBodyReleaseNeverRetriesAndKeepsRecoveryMarker() {
        val fixture = fixture()
        val store = MemoryStore()
        val transaction = fixture.approvedTransaction(store)
        val port = FakePort(
            generation = fixture.context.generation,
            exchanges = mutableListOf(
                Exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), RuntimeException("USB timeout"))
            ),
            snapshots = mutableListOf(),
            safety = fixture.safety()
        )

        val result = T3RamProofRunner(fixture.plan, transaction, port).executeApproved()

        assertEquals(T3RamProofOutcome.RECOVERY_REQUIRED, result.outcome)
        assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, result.state)
        assertEquals(1, result.transmissionAttempts)
        assertEquals(1, port.seenBodies.size)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
        assertFalse(T3RamScalarTransaction.startupDecision(store).replayAllowed)
    }

    @Test
    fun staleAckGenerationCannotAdvanceToReadback() {
        val fixture = fixture()
        val store = MemoryStore()
        val transaction = fixture.approvedTransaction(store)
        val port = FakePort(
            generation = fixture.context.generation + 1,
            exchanges = mutableListOf(
                Exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), byteArrayOf(0x00))
            ),
            snapshots = mutableListOf(),
            safety = fixture.safety()
        )

        val result = T3RamProofRunner(fixture.plan, transaction, port).executeApproved()

        assertEquals(T3RamProofOutcome.RECOVERY_REQUIRED, result.outcome)
        assertEquals(T3RamBlockReason.STALE_RESPONSE, result.blockReason)
        assertEquals(1, port.seenBodies.size)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
    }

    @Test
    fun targetReadbackSuccessStillFailsIfCompleteSnapshotHasCollateralByteChange() {
        val fixture = fixture()
        val store = MemoryStore()
        val transaction = fixture.approvedTransaction(store)
        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, fixture.plan)
        val page1 = candidate.pages.first { it.pageNumber == 1 }.bytes().also { it[100] = 0x5A }
        val collateral = TuneSnapshot.create(
            candidate.ecuSignature,
            candidate.profileFingerprint,
            listOf(
                TunePageSnapshot(1, 0, 55_764, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, page1),
                candidate.pages.first { it.pageNumber == 2 }
            ),
            candidate.generation,
            2L
        )
        val port = FakePort(
            generation = fixture.context.generation,
            exchanges = mutableListOf(
                Exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), byteArrayOf(0x00)),
                Exchange(T3PilotRamProtocol.readBackBody(fixture.plan), byteArrayOf(0x00, 0x28, 0x0A))
            ),
            snapshots = mutableListOf(collateral),
            safety = fixture.safety()
        )

        val result = T3RamProofRunner(fixture.plan, transaction, port).executeApproved()

        assertEquals(T3RamProofOutcome.RECOVERY_REQUIRED, result.outcome)
        assertEquals(T3RamBlockReason.SNAPSHOT_MISMATCH, result.blockReason)
        assertEquals(2, port.seenBodies.size)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
    }

    @Test
    fun runnerCannotExecuteWithoutExplicitTransactionApproval() {
        val fixture = fixture()
        val transaction = fixture.transaction(MemoryStore())
        val port = FakePort(fixture.context.generation, mutableListOf(), mutableListOf(), fixture.safety())

        val result = T3RamProofRunner(fixture.plan, transaction, port).executeApproved()

        assertEquals(T3RamProofOutcome.SAFE_ABORTED, result.outcome)
        assertEquals(0, result.transmissionAttempts)
        assertTrue(port.seenBodies.isEmpty())
    }

    private data class Exchange(val expectedBody: ByteArray, val result: Any)

    private class FakePort(
        private val generation: Long,
        private val exchanges: MutableList<Exchange>,
        private val snapshots: MutableList<TuneSnapshot>,
        private val safety: T3RamSafetyInputs
    ) : T3RamTransportPort {
        val seenBodies = mutableListOf<ByteArray>()

        override fun exchange(body: ByteArray, maxResponseBody: Int, label: String): T3RamTransportResponse {
            seenBodies += body.copyOf()
            val next = exchanges.removeFirstOrNull() ?: throw IllegalStateException("Unexpected exchange $label")
            assertArrayEquals(next.expectedBody, body)
            require(maxResponseBody in 1..64)
            if (next.result is RuntimeException) throw next.result
            @Suppress("UNCHECKED_CAST")
            return T3RamTransportResponse((next.result as ByteArray).copyOf(), generation)
        }

        override fun readCompleteTuneSnapshot(expectedGeneration: Long): TuneSnapshot {
            assertEquals(generation, expectedGeneration)
            return snapshots.removeFirstOrNull() ?: throw IllegalStateException("Unexpected TuneSnapshot read")
        }

        override fun readSafetyInputs(context: TuningContext): T3RamSafetyInputs = safety.copy(context = context)
    }

    private class MemoryStore : T3RamRecoveryMarkerStore {
        private var marker: T3RamRecoveryMarker? = null
        override fun load(): T3RecoveryStoreRead = marker?.let { T3RecoveryStoreRead(T3RecoveryStoreState.PRESENT, it) }
            ?: T3RecoveryStoreRead(T3RecoveryStoreState.ABSENT)
        override fun persist(marker: T3RamRecoveryMarker): Boolean {
            this.marker = marker
            return true
        }
        override fun clear(): Boolean {
            marker = null
            return true
        }
    }

    private data class Fixture(
        val baseline: TuneSnapshot,
        val context: TuningContext,
        val plan: TuningScalarChangePlan
    ) {
        fun transaction(store: T3RamRecoveryMarkerStore) = T3RamScalarTransaction(
            proposal = plan,
            baselineSnapshot = baseline,
            policy = T3BenchWritePolicy(12.0),
            baselineFlashWriteErrors = 3,
            markerStore = store,
            clock = { 1L }
        )

        fun approvedTransaction(store: T3RamRecoveryMarkerStore): T3RamScalarTransaction = transaction(store).also { tx ->
            assertTrue(tx.prepare(safety()))
            assertTrue(tx.approve("runner-action", plan.previewId, safety()))
        }

        fun safety(contextOverride: TuningContext = context) = T3RamSafetyInputs(
            context = contextOverride,
            rpm = 0.0,
            cranking = false,
            voltage = 13.5,
            supportPowerConfirmed = true,
            flashWritePending = false,
            flashWriteErrors = 3,
            safetyDefinitionFingerprint = "e".repeat(64)
        )
    }

    private fun fixture(): Fixture {
        val page1 = ByteArray(55_764)
        page1[12] = 0xC4.toByte()
        page1[13] = 0x09
        val baseline = TuneSnapshot.create(
            "rusEFI test.MEGA144H7",
            "f".repeat(64),
            listOf(
                TunePageSnapshot(1, 0, 55_764, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, page1),
                TunePageSnapshot(2, 0x0100, 5_000, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, ByteArray(5_000))
            ),
            77,
            1L
        )
        val context = TuningContext(
            sessionId = 9001,
            generation = 77,
            source = TuningDataSource.LIVE,
            ecuSignature = "rusEFI test.MEGA144H7",
            profileFingerprint = "f".repeat(64),
            tuneFingerprint = baseline.fingerprint
        )
        val target = TuningScalarIdentity(
            name = T3PilotAuthority.SCALAR_NAME,
            pageNumber = 1,
            pageIdentifier = 0,
            pageSize = 55_764,
            dataType = "U16",
            offset = 12,
            byteSize = 2,
            unit = "RPM",
            scale = 1.0,
            translate = 0.0,
            low = 0.0,
            high = 30_000.0,
            digits = 0,
            definitionFingerprint = "d".repeat(64)
        )
        val plan = TuningScalarChangePlan(
            context = context,
            target = target,
            originalValue = 2500.0,
            requestedValue = 2600.0,
            effectiveEncodedValue = 2600.0,
            beforeBytes = byteArrayOf(0xC4.toByte(), 0x09),
            afterBytes = byteArrayOf(0x28, 0x0A),
            byteDiffs = listOf(TuningByteDiff(12, 0xC4, 0x28), TuningByteDiff(13, 0x09, 0x0A)),
            affectedStartOffset = 12,
            affectedEndOffset = 13,
            previewId = "a".repeat(64),
            state = TuningValueState.PROPOSED
        )
        return Fixture(baseline, context, plan)
    }
}
