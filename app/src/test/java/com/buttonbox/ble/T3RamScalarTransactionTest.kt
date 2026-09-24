package com.buttonbox.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class T3RamScalarTransactionTest {
    @Test
    fun exactPilotBodiesAndFramingMatchW0Contract() {
        val fixture = fixture()
        assertArrayEquals(
            byteArrayOf(0x43, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x00, 0x28, 0x0A),
            T3PilotRamProtocol.candidateWriteBody(fixture.plan)
        )
        assertArrayEquals(
            byteArrayOf(0x43, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x00, 0xC4.toByte(), 0x09),
            T3PilotRamProtocol.restoreWriteBody(fixture.plan)
        )
        assertArrayEquals(
            byteArrayOf(0x52, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x00),
            T3PilotRamProtocol.readBackBody(fixture.plan)
        )

        val body = T3PilotRamProtocol.candidateWriteBody(fixture.plan)
        val frame = T3PilotRamProtocol.envelope(body)
        assertEquals(0, frame[0].toInt() and 0xff)
        assertEquals(9, frame[1].toInt() and 0xff)
        assertArrayEquals(body, frame.copyOfRange(2, 11))
        val expectedCrc = CRC32().apply { update(body) }.value
        val actualCrc = ByteBuffer.wrap(frame, frame.size - 4, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffff_ffffL
        assertEquals(expectedCrc, actualCrc)

        val responseBody = byteArrayOf(0x00, 0x28, 0x0A)
        val responseFrame = T3PilotRamProtocol.envelope(responseBody)
        val decoded = T3PilotRamProtocol.decodeFramedResponse(responseFrame)
        assertEquals(0, decoded.responseCode)
        assertArrayEquals(byteArrayOf(0x28, 0x0A), decoded.payload)
    }

    @Test
    fun happyPathRequiresAckReadbackWholeSnapshotThenPairedRestore() {
        val fixture = fixture()
        val store = MemoryStore()
        val tx = fixture.transaction(store)

        assertTrue(tx.prepare(fixture.safety()))
        assertEquals(T3RamTransactionState.READY, tx.state)
        assertTrue(tx.approve("w3-action-1", fixture.plan.previewId, fixture.safety()))
        assertEquals(T3RamTransactionState.APPROVED, tx.state)
        assertFalse(tx.approve("w3-action-1", fixture.plan.previewId, fixture.safety()))
        assertEquals(T3RamBlockReason.DUPLICATE_ACTION, tx.lastBlockReason)
        assertEquals(T3RamTransactionState.APPROVED, tx.state)

        assertTrue(tx.armCandidateWrite())
        assertEquals(T3RamTransactionState.ARMED, tx.state)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
        assertEquals(T3RecoveryPhase.WRITE, store.load().marker?.phase)

        assertArrayEquals(T3PilotRamProtocol.candidateWriteBody(fixture.plan), tx.beginCandidateWrite())
        assertEquals(T3RamTransactionState.TRANSMITTING, tx.state)
        tx.acknowledgeCandidateWrite(byteArrayOf(0x00), fixture.context.generation)
        assertEquals(T3RamTransactionState.ACKNOWLEDGED, tx.state)
        assertTrue(tx.verifyCandidateReadBack(byteArrayOf(0x00, 0x28, 0x0A), fixture.context.generation))
        assertEquals(T3RamTransactionState.WRITE_READBACK_VERIFIED, tx.state)

        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, fixture.plan)
        val candidateContext = fixture.context.copy(tuneFingerprint = candidate.fingerprint)
        assertTrue(tx.verifyCandidateSnapshot(candidate, candidateContext))
        assertEquals(T3RamTransactionState.CANDIDATE_SNAPSHOT_VERIFIED, tx.state)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)

        assertTrue(tx.armRestore(fixture.safety(candidateContext)))
        assertEquals(T3RecoveryPhase.RESTORE, store.load().marker?.phase)
        assertArrayEquals(T3PilotRamProtocol.restoreWriteBody(fixture.plan), tx.beginRestoreWrite())
        tx.acknowledgeRestore(byteArrayOf(0x00), fixture.context.generation)
        assertEquals(T3RamTransactionState.RESTORE_ACKNOWLEDGED, tx.state)
        assertTrue(tx.verifyRestoreReadBack(byteArrayOf(0x00, 0xC4.toByte(), 0x09), fixture.context.generation))
        assertTrue(tx.verifyBaselineRestored(fixture.baseline, fixture.context))
        assertEquals(T3RamTransactionState.BASELINE_RESTORED, tx.state)
        assertEquals(2, tx.transmissionAttempts)
        assertEquals(T3RecoveryStoreState.ABSENT, store.load().state)
        assertTrue(tx.readOnlyOperationAvailable)
    }

    @Test
    fun noNumericVoltageThresholdAllowsValidNativeVoltage() {
        val fixture = fixture()
        val tx = fixture.transaction(MemoryStore(), T3BenchWritePolicy(minimumVoltage = null))
        assertTrue(tx.prepare(fixture.safety().copy(voltage = 11.8)))
        assertEquals(T3RamTransactionState.READY, tx.state)
        assertEquals(T3RamBlockReason.NONE, tx.lastBlockReason)
        assertEquals(0, tx.transmissionAttempts)
    }

    @Test
    fun safetyInputsFailClosedBeforePossibleTransmission() {
        val fixture = fixture()
        val mutations = listOf<(T3RamSafetyInputs) -> T3RamSafetyInputs>(
            { it.copy(rpm = 1.0) },
            { it.copy(cranking = true) },
            { it.copy(voltage = 0.0) },
            { it.copy(voltage = Double.NaN) },
            { it.copy(supportPowerConfirmed = false) },
            { it.copy(flashWritePending = true) },
            { it.copy(flashWriteErrors = 4) },
            { it.copy(context = it.context.copy(generation = it.context.generation + 1)) },
            { it.copy(context = it.context.copy(tuneFingerprint = "b".repeat(64))) }
        )
        mutations.forEach { mutate ->
            val tx = fixture.transaction(MemoryStore())
            assertFalse(tx.prepare(mutate(fixture.safety())))
            assertEquals(0, tx.transmissionAttempts)
            assertEquals(T3RamTransactionState.SAFE_ABORTED, tx.state)
        }
    }

    @Test
    fun optionalEvidenceBackedMinimumIsEnforcedOnlyWhenConfigured() {
        val fixture = fixture()
        val policy = T3BenchWritePolicy(minimumVoltage = 10.0)
        val blocked = fixture.transaction(MemoryStore(), policy)
        assertFalse(blocked.prepare(fixture.safety().copy(voltage = 9.9)))
        assertEquals(T3RamBlockReason.VOLTAGE_TOO_LOW, blocked.lastBlockReason)

        val allowed = fixture.transaction(MemoryStore(), policy)
        assertTrue(allowed.prepare(fixture.safety().copy(voltage = 10.0)))
        assertEquals(T3RamTransactionState.READY, allowed.state)
    }

    @Test
    fun safetySchemaIsFrozenAfterPreparation() {
        val fixture = fixture()
        val tx = fixture.transaction(MemoryStore())
        assertTrue(tx.prepare(fixture.safety()))
        val changed = fixture.safety().copy(safetyDefinitionFingerprint = "9".repeat(64))
        assertFalse(tx.approve("schema-change", fixture.plan.previewId, changed))
        assertEquals(T3RamBlockReason.SAFETY_SCHEMA_CHANGED, tx.lastBlockReason)
        assertEquals(0, tx.transmissionAttempts)
    }

    @Test
    fun markerMustPersistAndRereadBeforeCandidateBodyCanBeReleased() {
        val fixture = fixture()
        val failPersist = MemoryStore(failPersist = true)
        val tx = fixture.transaction(failPersist)
        assertTrue(tx.prepare(fixture.safety()))
        assertTrue(tx.approve("persist-fail", fixture.plan.previewId, fixture.safety()))
        assertFalse(tx.armCandidateWrite())
        assertEquals(T3RamTransactionState.SAFE_ABORTED, tx.state)
        assertNull(tx.beginCandidateWrite())
        assertEquals(0, tx.transmissionAttempts)

        val store = MemoryStore()
        val tx2 = fixture.transaction(store)
        assertTrue(tx2.prepare(fixture.safety()))
        assertTrue(tx2.approve("marker-loss", fixture.plan.previewId, fixture.safety()))
        assertTrue(tx2.armCandidateWrite())
        store.forceAbsent()
        assertNull(tx2.beginCandidateWrite())
        assertEquals(0, tx2.transmissionAttempts)
    }

    @Test
    fun explicitWriteRejectionIsSafeButUnknownOrStaleResponseRequiresRecovery() {
        for (code in listOf(0x80, 0x81, 0x82, 0x83, 0x84, 0x8D)) {
            val fixture = fixture()
            val store = MemoryStore()
            val tx = fixture.armedTransaction(store, "reject-$code")
            assertNotNull(tx.beginCandidateWrite())
            tx.acknowledgeCandidateWrite(byteArrayOf(code.toByte()), fixture.context.generation)
            assertEquals(T3RamTransactionState.REJECTED, tx.state)
            assertEquals(T3RecoveryStoreState.ABSENT, store.load().state)
        }

        run {
            val fixture = fixture()
            val store = MemoryStore()
            val tx = fixture.armedTransaction(store, "burn-like-code")
            tx.beginCandidateWrite()
            tx.acknowledgeCandidateWrite(byteArrayOf(0x04), fixture.context.generation)
            assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
            assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
        }

        run {
            val fixture = fixture()
            val store = MemoryStore()
            val tx = fixture.armedTransaction(store, "stale")
            tx.beginCandidateWrite()
            tx.acknowledgeCandidateWrite(byteArrayOf(0x00), fixture.context.generation + 1)
            assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
            assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
        }
    }

    @Test
    fun anyFailureAfterPossibleTransmissionKeepsMarkerAndHasNoRetrySurface() {
        val fixture = fixture()
        val store = MemoryStore()
        val tx = fixture.armedTransaction(store, "uncertain")
        assertNotNull(tx.beginCandidateWrite())
        tx.markTransportOrVerificationUncertain()
        assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
        assertEquals(1, tx.transmissionAttempts)
        assertNull(tx.beginCandidateWrite())
        assertEquals(1, tx.transmissionAttempts)

        val methodNames = T3RamScalarTransaction::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue(methodNames.none { "retry" in it || "burn" in it || "flash" in it })
    }

    @Test
    fun correctTargetReadbackCannotHideCollateralSnapshotChange() {
        val fixture = fixture()
        val store = MemoryStore()
        val tx = fixture.armedTransaction(store, "collateral")
        tx.beginCandidateWrite()
        tx.acknowledgeCandidateWrite(byteArrayOf(0x00), fixture.context.generation)
        assertTrue(tx.verifyCandidateReadBack(byteArrayOf(0x00, 0x28, 0x0A), fixture.context.generation))

        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, fixture.plan)
        val page1 = candidate.pages.first { it.pageNumber == 1 }.bytes().also { it[100] = 0x55 }
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
        val context = fixture.context.copy(tuneFingerprint = collateral.fingerprint)
        assertFalse(tx.verifyCandidateSnapshot(collateral, context))
        assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
        assertEquals(T3RamBlockReason.SNAPSHOT_MISMATCH, tx.lastBlockReason)
    }

    @Test
    fun restoreRejectionRemainsRecoveryRequiredBecauseCandidateIsStillInRam() {
        val fixture = fixture()
        val store = MemoryStore()
        val tx = fixture.transaction(store)
        assertTrue(tx.prepare(fixture.safety()))
        assertTrue(tx.approve("restore-reject", fixture.plan.previewId, fixture.safety()))
        assertTrue(tx.armCandidateWrite())
        tx.beginCandidateWrite()
        tx.acknowledgeCandidateWrite(byteArrayOf(0x00), fixture.context.generation)
        assertTrue(tx.verifyCandidateReadBack(byteArrayOf(0x00, 0x28, 0x0A), fixture.context.generation))
        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, fixture.plan)
        val candidateContext = fixture.context.copy(tuneFingerprint = candidate.fingerprint)
        assertTrue(tx.verifyCandidateSnapshot(candidate, candidateContext))
        assertTrue(tx.armRestore(fixture.safety(candidateContext)))
        tx.beginRestoreWrite()
        tx.acknowledgeRestore(byteArrayOf(0x84.toByte()), fixture.context.generation)
        assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
    }

    @Test
    fun startupMarkerBlocksWritesAndRecoveryClassifiesFreshCompleteTuneWithoutReplay() {
        val fixture = fixture()
        val store = MemoryStore()
        val tx = fixture.armedTransaction(store, "restart")
        tx.beginCandidateWrite()
        assertTrue(T3RamScalarTransaction.startupDecision(store).writeBlocked)
        assertFalse(T3RamScalarTransaction.startupDecision(store).replayAllowed)

        val marker = store.load()
        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, fixture.plan)
        val candidateFresh = withGeneration(candidate, fixture.context.generation + 1)
        val freshCandidateContext = fixture.context.copy(
            sessionId = fixture.context.sessionId + 1,
            generation = fixture.context.generation + 1,
            tuneFingerprint = candidateFresh.fingerprint
        )
        assertEquals(
            T3RecoveryClassification.CANDIDATE,
            T3RamScalarTransaction.classifyRecovery(marker, candidateFresh, freshCandidateContext)
        )
        assertFalse(T3RamScalarTransaction.clearRecoveredBaseline(store, candidateFresh, freshCandidateContext))
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)

        val baselineFresh = withGeneration(fixture.baseline, fixture.context.generation + 1)
        val freshBaselineContext = freshCandidateContext.copy(tuneFingerprint = baselineFresh.fingerprint)
        assertEquals(
            T3RecoveryClassification.BASELINE,
            T3RamScalarTransaction.classifyRecovery(store.load(), baselineFresh, freshBaselineContext)
        )
        assertTrue(T3RamScalarTransaction.clearRecoveredBaseline(store, baselineFresh, freshBaselineContext))
        assertEquals(T3RecoveryStoreState.ABSENT, store.load().state)

        assertThrows(IllegalArgumentException::class.java) {
            T3RamScalarTransaction(
                fixture.plan,
                baselineFresh,
                T3BenchWritePolicy(),
                3,
                MemoryStore()
            )
        }
    }

    @Test
    fun corruptRecoveryMarkerBlocksStartupAndPreparation() {
        val fixture = fixture()
        val store = MemoryStore(corrupt = true)
        val startup = T3RamScalarTransaction.startupDecision(store)
        assertTrue(startup.writeBlocked)
        assertTrue(startup.recoveryRequired)
        assertTrue(startup.markerCorrupt)

        val tx = fixture.transaction(store)
        assertFalse(tx.prepare(fixture.safety()))
        assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
        assertEquals(T3RamBlockReason.RECOVERY_MARKER_CORRUPT, tx.lastBlockReason)
    }

    @Test
    fun genericLowerLayerAcceptsStructuralScalarButT3PolicyStillRejectsWrongPilotName() {
        val fixture = fixture()
        val generic = TuningScalarChangePlan(
            context = fixture.plan.context,
            target = fixture.plan.target.copy(name = "otherScalar"),
            originalValue = fixture.plan.originalValue,
            requestedValue = fixture.plan.requestedValue,
            effectiveEncodedValue = fixture.plan.effectiveEncodedValue,
            beforeBytes = fixture.plan.originalBytes(),
            afterBytes = fixture.plan.encodedBytes(),
            byteDiffs = fixture.plan.byteDiffs,
            affectedStartOffset = fixture.plan.affectedStartOffset,
            affectedEndOffset = fixture.plan.affectedEndOffset,
            previewId = fixture.plan.previewId,
            state = TuningValueState.PROPOSED
        )

        RamScalarAuthority.requirePlan(generic)
        assertNotNull(T3PilotRamProtocol.candidateWriteBody(generic))
        assertNotNull(T3RamScalarTransaction(generic, fixture.baseline, T3BenchWritePolicy(), 3, MemoryStore()))
        assertThrows(IllegalArgumentException::class.java) { T3PilotAuthority.requirePlan(generic) }
    }

    private data class Fixture(
        val baseline: TuneSnapshot,
        val context: TuningContext,
        val plan: TuningScalarChangePlan
    ) {
        fun safety(contextOverride: TuningContext = context) = T3RamSafetyInputs(
            context = contextOverride,
            rpm = 0.0,
            cranking = false,
            voltage = 12.2,
            supportPowerConfirmed = true,
            flashWritePending = false,
            flashWriteErrors = 3,
            safetyDefinitionFingerprint = "e".repeat(64)
        )

        fun transaction(
            store: T3RamRecoveryMarkerStore,
            policy: T3BenchWritePolicy = T3BenchWritePolicy()
        ) = T3RamScalarTransaction(plan, baseline, policy, 3, store) { 123456789L }

        fun armedTransaction(store: T3RamRecoveryMarkerStore, actionId: String): T3RamScalarTransaction =
            transaction(store).also { tx ->
                assertTrue(tx.prepare(safety()))
                assertTrue(tx.approve(actionId, plan.previewId, safety()))
                assertTrue(tx.armCandidateWrite())
            }
    }

    private fun fixture(): Fixture {
        val page1 = ByteArray(55_764)
        page1[12] = 0xC4.toByte()
        page1[13] = 0x09
        val baseline = TuneSnapshot.create(
            ecuSignature = "rusEFI test.MEGA144H7",
            profileFingerprint = "f".repeat(64),
            pages = listOf(
                TunePageSnapshot(1, 0x0000, 55_764, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, page1),
                TunePageSnapshot(2, 0x0100, 5_000, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, ByteArray(5_000))
            ),
            generation = 77,
            capturedAtEpochMs = 1L
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
            byteDiffs = listOf(
                TuningByteDiff(12, 0xC4, 0x28),
                TuningByteDiff(13, 0x09, 0x0A)
            ),
            affectedStartOffset = 12,
            affectedEndOffset = 13,
            previewId = "a".repeat(64),
            state = TuningValueState.PROPOSED
        )
        return Fixture(baseline, context, plan)
    }

    private fun withGeneration(snapshot: TuneSnapshot, generation: Long): TuneSnapshot = TuneSnapshot.create(
        ecuSignature = snapshot.ecuSignature,
        profileFingerprint = snapshot.profileFingerprint,
        pages = snapshot.pages.map { TunePageSnapshot(it.pageNumber, it.identifier, it.size, it.readCommand, it.bytes()) },
        generation = generation,
        capturedAtEpochMs = snapshot.capturedAtEpochMs + 1
    )

    private class MemoryStore(
        private var failPersist: Boolean = false,
        private var failClear: Boolean = false,
        private var corrupt: Boolean = false
    ) : T3RamRecoveryMarkerStore {
        private var marker: T3RamRecoveryMarker? = null

        override fun load(): T3RecoveryStoreRead = when {
            corrupt -> T3RecoveryStoreRead(T3RecoveryStoreState.CORRUPT, error = "corrupt")
            marker != null -> T3RecoveryStoreRead(T3RecoveryStoreState.PRESENT, marker)
            else -> T3RecoveryStoreRead(T3RecoveryStoreState.ABSENT)
        }

        override fun persist(marker: T3RamRecoveryMarker): Boolean {
            if (failPersist) return false
            corrupt = false
            this.marker = marker
            return true
        }

        override fun clear(): Boolean {
            if (failClear) return false
            corrupt = false
            marker = null
            return true
        }

        fun forceAbsent() {
            marker = null
            corrupt = false
        }
    }
}
