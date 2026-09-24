package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class W4VehicleRamTest {
    @Test
    fun vehicleAuthorizationIsEphemeralGenerationBoundAndOneShot() {
        val gate = W4VehicleManagerGate(W4VehicleSessionAuthorization(maximumAgeMs = 1_000L))
        assertFalse(gate.enter("wrong", 7, 12.4, 100))
        assertFalse(gate.enter(W4VehicleRamPolicy.ENTER_VEHICLE_RAM_ONLY_ACTION_ID, 7, 0.0, 100))
        assertTrue(gate.enter(W4VehicleRamPolicy.ENTER_VEHICLE_RAM_ONLY_ACTION_ID, 7, 12.4, 100))
        assertTrue(gate.isAuthorized(7, 999))
        assertFalse(gate.isAuthorized(8, 999))
        assertTrue(gate.consumeForAttempt(7, 999))
        assertFalse(gate.isAuthorized(7, 999))
        assertFalse(gate.consumeForAttempt(7, 999))
    }

    @Test
    fun genericApplyHoldRestoreMatrixCoversEveryPrimitiveAndLowMidHighRangePositions() {
        val fixture = matrixFixture()
        assertEquals(
            setOf("U08", "S08", "U16", "S16", "U32", "S32", "F32"),
            fixture.cases.map { it.type }.toSet()
        )
        assertEquals(RamScalarAuthority.SUPPORTED_TYPES, fixture.cases.map { it.type }.toSet())
        assertEquals(fixture.cases.size, RamScalarAuthority.eligibleScalarCount(fixture.profile))

        var executions = 0
        fixture.cases.forEach { scalarCase ->
            scalarCase.requests.forEachIndexed { index, requested ->
                val prepared = W4ScalarProposalFactory.prepare(
                    profile = fixture.profile,
                    snapshot = fixture.baseline,
                    generation = fixture.generation,
                    name = scalarCase.name,
                    requestedValue = requested
                )
                val plan = prepared.plan
                assertEquals(scalarCase.name, plan.target.name)
                assertEquals(scalarCase.type, plan.target.dataType)
                assertEquals(scalarCase.pageNumber, plan.target.pageNumber)
                assertEquals(scalarCase.offset, plan.target.offset)
                assertTrue(plan.effectiveEncodedValue >= scalarCase.low)
                assertTrue(plan.effectiveEncodedValue <= scalarCase.high)
                RamScalarAuthority.requireAgainstProfile(plan, fixture.profile)

                val store = MemoryStore()
                val tx = T3RamScalarTransaction(
                    proposal = plan,
                    baselineSnapshot = fixture.baseline,
                    policy = T3BenchWritePolicy(),
                    baselineFlashWriteErrors = 3,
                    markerStore = store,
                    clock = { 1_000L + executions }
                )
                assertTrue(tx.prepare(fixture.safety(plan.context)))
                assertTrue(tx.approve("w4-matrix-${scalarCase.name}-$index", plan.previewId, fixture.safety(plan.context)))

                val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, plan)
                val transport = FakeTransport(fixture, plan, candidate)
                val runner = W4VehicleRamRunner(plan, tx, transport)

                val applied = runner.applyApproved()
                assertEquals(W4VehicleRamOutcome.APPLIED_RAM_ONLY, applied.result.outcome)
                assertEquals(T3RamTransactionState.CANDIDATE_SNAPSHOT_VERIFIED, tx.state)
                assertEquals(1, tx.transmissionAttempts)
                assertTrue(transport.applied)
                assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
                assertEquals(T3RecoveryPhase.WRITE, store.load().marker?.phase)
                assertEquals(candidate.fingerprint, applied.verifiedSnapshot?.fingerprint)

                val restored = runner.restoreApplied()
                assertEquals(W4VehicleRamOutcome.BASELINE_RESTORED, restored.result.outcome)
                assertEquals(T3RamTransactionState.BASELINE_RESTORED, tx.state)
                assertEquals(2, tx.transmissionAttempts)
                assertFalse(transport.applied)
                assertEquals(T3RecoveryStoreState.ABSENT, store.load().state)
                assertEquals(fixture.baseline.fingerprint, restored.verifiedSnapshot?.fingerprint)
                executions++
            }
        }

        // Seven primitives x low/mid/high request points.
        assertEquals(21, executions)
    }

    @Test
    fun candidateSnapshotMismatchRequiresRecoveryForGenericScalar() {
        val fixture = matrixFixture()
        val scalarCase = fixture.cases.first { it.type == "F32" }
        val prepared = W4ScalarProposalFactory.prepare(
            fixture.profile,
            fixture.baseline,
            fixture.generation,
            scalarCase.name,
            scalarCase.requests.last()
        )
        val plan = prepared.plan
        val store = MemoryStore()
        val tx = T3RamScalarTransaction(
            plan,
            fixture.baseline,
            T3BenchWritePolicy(),
            3,
            store,
            clock = { 1234L }
        )
        assertTrue(tx.prepare(fixture.safety(plan.context)))
        assertTrue(tx.approve("w4-collateral", plan.previewId, fixture.safety(plan.context)))

        val candidate = T3TuneSnapshotVerifier.candidateSnapshot(fixture.baseline, plan)
        val transport = FakeTransport(fixture, plan, candidate, collateralCandidate = true)
        val result = W4VehicleRamRunner(plan, tx, transport).applyApproved()
        assertEquals(W4VehicleRamOutcome.RECOVERY_REQUIRED, result.result.outcome)
        assertEquals(T3RamTransactionState.RECOVERY_REQUIRED, tx.state)
        assertEquals(1, tx.transmissionAttempts)
        assertEquals(T3RecoveryStoreState.PRESENT, store.load().state)
    }

    @Test
    fun proposalFactoryRejectsOutOfRangeBeforeAnyTransactionExists() {
        val fixture = matrixFixture()
        val scalarCase = fixture.cases.first { it.type == "S16" }
        val error = runCatching {
            W4ScalarProposalFactory.prepare(
                fixture.profile,
                fixture.baseline,
                fixture.generation,
                scalarCase.name,
                scalarCase.high + 1.0
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    private data class ScalarCase(
        val name: String,
        val pageNumber: Int,
        val type: String,
        val offset: Int,
        val low: Double,
        val high: Double,
        val original: Double,
        val requests: List<Double>
    )

    private data class MatrixFixture(
        val profile: UsbTunerStudioProfile,
        val baseline: TuneSnapshot,
        val generation: Long,
        val cases: List<ScalarCase>
    ) {
        fun safety(context: TuningContext) = T3RamSafetyInputs(
            context = context,
            rpm = 0.0,
            cranking = false,
            voltage = 12.3,
            supportPowerConfirmed = true,
            flashWritePending = false,
            flashWriteErrors = 3,
            safetyDefinitionFingerprint = "e".repeat(64)
        )
    }

    private fun matrixFixture(): MatrixFixture {
        val cases = listOf(
            ScalarCase("w4U08", 1, "U08", 7, 0.0, 250.0, 100.0, listOf(1.0, 125.0, 249.0)),
            ScalarCase("w4S08", 1, "S08", 101, -120.0, 120.0, 10.0, listOf(-119.0, 0.0, 119.0)),
            ScalarCase("w4U16", 1, "U16", 1001, 0.0, 60000.0, 12345.0, listOf(1.0, 30000.0, 59999.0)),
            ScalarCase("w4S16", 1, "S16", 2047, -30000.0, 30000.0, -1234.0, listOf(-29999.0, 0.0, 29999.0)),
            ScalarCase("w4U32", 2, "U32", 11, 0.0, 4_000_000_000.0, 123_456_789.0, listOf(1.0, 2_000_000_000.0, 3_999_999_999.0)),
            ScalarCase("w4S32", 2, "S32", 777, -2_000_000_000.0, 2_000_000_000.0, -123_456_789.0, listOf(-1_999_999_999.0, 0.0, 1_999_999_999.0)),
            ScalarCase("w4F32", 2, "F32", 3000, -100.0, 100.0, 10.25, listOf(-99.5, 0.5, 99.5))
        )
        val pages = listOf(
            UsbTunePage(1, 0x0000, 4096, UsbTuneReadCodec.SUPPORTED_READ_COMMAND),
            UsbTunePage(2, 0x0100, 4096, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)
        )
        val definitions = cases.map { scalarCase ->
            UsbTuneScalar(
                name = scalarCase.name,
                pageNumber = scalarCase.pageNumber,
                dataType = scalarCase.type,
                offset = scalarCase.offset,
                unit = "test",
                scale = 1.0,
                translate = 0.0,
                low = scalarCase.low,
                high = scalarCase.high,
                digits = if (scalarCase.type == "F32") 2 else 0
            )
        }
        val profile = UsbTunerStudioProfile(
            signature = "rusEFI test.MEGA144H7",
            queryCommand = "S",
            outputCommand = "O%2o%2c",
            outputBlockSize = 64,
            envelopeFormat = "msEnvelope_1.0",
            endianness = "little",
            channels = emptyList(),
            tunePages = pages,
            tuneScalars = definitions,
            importedName = "w4-matrix.ini"
        )

        val pageBytes = pages.associate { it.pageNumber to ByteArray(it.size) }.toMutableMap()
        cases.forEach { scalarCase ->
            val definition = definitions.single { it.name == scalarCase.name }
            val encoded = TuningScalarCodec.encode(definition, scalarCase.original)
            encoded.copyInto(pageBytes.getValue(scalarCase.pageNumber), scalarCase.offset)
        }

        val generation = 77L
        val profileFingerprint = profile.tuneProfileFingerprint()
        val baseline = TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profileFingerprint,
            pages = pages.map { page ->
                TunePageSnapshot(
                    page.pageNumber,
                    page.identifier,
                    page.size,
                    page.readCommand,
                    pageBytes.getValue(page.pageNumber)
                )
            },
            generation = generation,
            capturedAtEpochMs = 1L
        )
        return MatrixFixture(profile, baseline, generation, cases)
    }

    private class FakeTransport(
        private val fixture: MatrixFixture,
        private val plan: TuningScalarChangePlan,
        private val candidate: TuneSnapshot,
        private val collateralCandidate: Boolean = false
    ) : T3RamTransportPort {
        var applied: Boolean = false

        override fun exchange(body: ByteArray, maxResponseBody: Int, label: String): T3RamTransportResponse {
            return when {
                body.contentEquals(T3PilotRamProtocol.candidateWriteBody(plan)) -> {
                    applied = true
                    T3RamTransportResponse(byteArrayOf(0x00), plan.context.generation)
                }
                body.contentEquals(T3PilotRamProtocol.restoreWriteBody(plan)) -> {
                    applied = false
                    T3RamTransportResponse(byteArrayOf(0x00), plan.context.generation)
                }
                body.contentEquals(T3PilotRamProtocol.readBackBody(plan)) -> {
                    val bytes = if (applied) plan.encodedBytes() else plan.originalBytes()
                    T3RamTransportResponse(byteArrayOf(0x00) + bytes, plan.context.generation)
                }
                else -> error("Unexpected bounded transport body")
            }
        }

        override fun readCompleteTuneSnapshot(expectedGeneration: Long): TuneSnapshot {
            assertEquals(fixture.generation, expectedGeneration)
            if (!applied) return fixture.baseline
            if (!collateralCandidate) return candidate
            val pages = candidate.pages.map { page ->
                if (page.pageNumber == plan.target.pageNumber) {
                    val bytes = page.bytes()
                    bytes[0] = (bytes[0].toInt() xor 0x55).toByte()
                    TunePageSnapshot(page.pageNumber, page.identifier, page.size, page.readCommand, bytes)
                } else {
                    TunePageSnapshot(page.pageNumber, page.identifier, page.size, page.readCommand, page.bytes())
                }
            }
            return TuneSnapshot.create(
                candidate.ecuSignature,
                candidate.profileFingerprint,
                pages,
                candidate.generation,
                candidate.capturedAtEpochMs
            )
        }

        override fun readSafetyInputs(context: TuningContext): T3RamSafetyInputs =
            fixture.safety(context)
    }

    private class MemoryStore : T3RamRecoveryMarkerStore {
        private var marker: T3RamRecoveryMarker? = null

        override fun load(): T3RecoveryStoreRead =
            marker?.let { T3RecoveryStoreRead(T3RecoveryStoreState.PRESENT, it) }
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
}
