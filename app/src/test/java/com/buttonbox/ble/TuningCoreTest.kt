package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class TuningCoreTest {
    @Test
    fun pilotScalarFlowsFromEcuCurrentThroughSimulationWithoutTransmission() {
        val fixture = fixture(
            scalar = scalar("engineSnifferRpmThreshold", "U16", offset = 12, low = 0.0, high = 30000.0),
            rawAtScalar = byteArrayOf(0xC4.toByte(), 0x09)
        )
        val core = fixture.core()

        val current = core.currentScalar("engineSnifferRpmThreshold")
        assertEquals(TuningValueState.ECU_CURRENT, current.state)
        assertEquals(2500.0, current.value, 0.0)
        assertEquals("c409", current.rawHex)

        val edit = core.editScalar("engineSnifferRpmThreshold", 2600.0)
        assertEquals(TuningValueState.EDITED, edit.state)
        assertTrue(edit.validation.valid)
        assertEquals(2600.0, edit.effectiveEncodedValue ?: Double.NaN, 0.0)
        assertEquals("280a", edit.proposedRawHex)

        val proposal = core.propose(edit)
        assertEquals(TuningValueState.PROPOSED, proposal.state)
        assertEquals(12, proposal.affectedStartOffset)
        assertEquals(13, proposal.affectedEndOffset)
        assertArrayEquals(byteArrayOf(0xC4.toByte(), 0x09), proposal.originalBytes())
        assertArrayEquals(byteArrayOf(0x28, 0x0A), proposal.encodedBytes())
        assertEquals(2, proposal.byteDiffs.size)
        assertTrue(proposal.previewId.matches(Regex("[0-9a-f]{64}")))
        assertFalse(proposal.transmitted)
        assertFalse(proposal.burnRequested)
        assertEquals(TuningTransmissionState.NOT_TRANSMITTED, proposal.transmissionState)

        val simulated = core.simulate(proposal)
        assertEquals(TuningValueState.SIMULATED, simulated.state)
        assertEquals(proposal.previewId, simulated.previewId)
        assertFalse(simulated.transmitted)
        assertEquals(2500.0, core.currentScalar("engineSnifferRpmThreshold").value, 0.0)
        assertArrayEquals(fixture.originalPageBytes, fixture.snapshot.pages.single().bytes())

        val reverted = core.revert(simulated)
        assertEquals(TuningValueState.ECU_CURRENT, reverted.state)
        assertEquals(2500.0, reverted.value, 0.0)
    }

    @Test
    fun integerScalingUsesDeterministicNearestRawRoundingAndReportsEffectiveValue() {
        val definition = scalar("scaled", "U16", offset = 0, scale = 0.5, translate = 10.0, low = 10.0, high = 100.0)
        val fixture = fixture(definition, byteArrayOf(0x02, 0x00))
        val edit = fixture.core().editScalar("scaled", 11.26)

        assertTrue(edit.validation.valid)
        assertEquals("0300", edit.proposedRawHex)
        assertEquals(11.5, edit.effectiveEncodedValue ?: Double.NaN, 0.0)
    }

    @Test
    fun codecProducesExactLittleEndianVectorsForEverySupportedScalarType() {
        assertArrayEquals(byteArrayOf(0xC8.toByte()), TuningScalarCodec.encode(scalar("u8", "U08", high = 255.0), 200.0))
        assertArrayEquals(byteArrayOf(0xFE.toByte()), TuningScalarCodec.encode(scalar("s8", "S08", low = -128.0, high = 127.0), -2.0))
        assertArrayEquals(byteArrayOf(0x34, 0x12), TuningScalarCodec.encode(scalar("u16", "U16", high = 65535.0), 4660.0))
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), TuningScalarCodec.encode(scalar("s16", "S16", low = -32768.0, high = 32767.0), -2.0))
        assertArrayEquals(
            byteArrayOf(0x78, 0x56, 0x34, 0x12),
            TuningScalarCodec.encode(scalar("u32", "U32", high = 4294967295.0), 305419896.0)
        )
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            TuningScalarCodec.encode(scalar("s32", "S32", low = Int.MIN_VALUE.toDouble(), high = Int.MAX_VALUE.toDouble()), -2.0)
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0xC0.toByte(), 0x3F),
            TuningScalarCodec.encode(scalar("f32", "F32", low = -100.0, high = 100.0), 1.5)
        )
    }

    @Test
    fun nonFiniteOutOfBoundsAndRawOverflowEditsFailClosedBeforeProposal() {
        val normal = fixture(scalar("pilot", "U16", high = 100.0), byteArrayOf(0, 0)).core()
        val nan = normal.editScalar("pilot", Double.NaN)
        val high = normal.editScalar("pilot", 101.0)
        assertFalse(nan.validation.valid)
        assertFalse(high.validation.valid)
        assertThrows(IllegalArgumentException::class.java) { normal.propose(nan) }
        assertThrows(IllegalArgumentException::class.java) { normal.propose(high) }

        val rawOverflowDefinition = scalar("overflow", "U08", low = 0.0, high = 1000.0)
        val overflow = fixture(rawOverflowDefinition, byteArrayOf(0)).core().editScalar("overflow", 300.0)
        assertFalse(overflow.validation.valid)
        assertTrue(overflow.validation.errors.any { it.contains("overflows") })
    }

    @Test
    fun unknownOrNonAllowlistedTargetsAreRejectedWithoutRawAddressFallback() {
        val fixture = fixture(scalar("pilot", "U16", high = 1000.0), byteArrayOf(1, 0))
        val core = SimulationTuningCore(
            fixture.profile,
            fixture.snapshot,
            fixture.context,
            allowlistedScalarNames = setOf("differentName")
        )

        assertThrows(IllegalArgumentException::class.java) { core.currentScalar("pilot") }
        assertThrows(IllegalArgumentException::class.java) { core.currentScalar("differentName") }
    }

    @Test
    fun duplicateScalarNameIsAmbiguousAndFailsClosed() {
        val first = scalar("pilot", "U16", offset = 0, high = 1000.0)
        val second = scalar("pilot", "U16", offset = 2, high = 1000.0)
        val profile = profile(listOf(first, second), pageSize = 8)
        val page = ByteArray(8)
        val snapshot = snapshot(profile, page, generation = 4)
        val core = SimulationTuningCore(profile, snapshot, context(profile, snapshot, 4), setOf("pilot"))

        assertThrows(IllegalArgumentException::class.java) { core.currentScalar("pilot") }
    }

    @Test
    fun staleGenerationProfileTuneOrNonLiveOwnershipIsRejected() {
        val fixture = fixture(scalar("pilot", "U16", high = 1000.0), byteArrayOf(1, 0))

        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(
                fixture.profile,
                fixture.snapshot,
                fixture.context.copy(generation = fixture.context.generation + 1),
                setOf("pilot")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(
                fixture.profile,
                fixture.snapshot,
                fixture.context.copy(profileFingerprint = "0".repeat(64)),
                setOf("pilot")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(
                fixture.profile,
                fixture.snapshot,
                fixture.context.copy(tuneFingerprint = "1".repeat(64)),
                setOf("pilot")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(
                fixture.profile,
                fixture.snapshot,
                fixture.context.copy(source = TuningDataSource.MSL),
                setOf("pilot")
            )
        }
    }

    @Test
    fun staleSessionAtSimulationTimeRejectsPreviouslyValidPreview() {
        val fixture = fixture(scalar("pilot", "U16", high = 1000.0), byteArrayOf(1, 0))
        val core = fixture.core()
        val proposal = core.propose(core.editScalar("pilot", 2.0))

        assertThrows(IllegalArgumentException::class.java) {
            core.simulate(proposal, fixture.context.copy(sessionId = fixture.context.sessionId + 1))
        }
    }

    @Test
    fun noOpAfterEncodingCannotBecomeProposal() {
        val fixture = fixture(
            scalar("pilot", "U16", scale = 1.0, high = 1000.0),
            byteArrayOf(0x0A, 0x00)
        )
        val core = fixture.core()
        val edit = core.editScalar("pilot", 10.2)
        assertTrue(edit.validation.valid)
        assertEquals(10.0, edit.effectiveEncodedValue ?: Double.NaN, 0.0)

        assertThrows(IllegalArgumentException::class.java) { core.propose(edit) }
    }

    @Test
    fun previewAndDefinitionIdentityAreDeterministicAndBoundToRequestedValue() {
        val fixture = fixture(scalar("pilot", "U16", high = 1000.0), byteArrayOf(1, 0))
        val coreA = fixture.core()
        val coreB = fixture.core()
        val proposalA = coreA.propose(coreA.editScalar("pilot", 2.0))
        val proposalB = coreB.propose(coreB.editScalar("pilot", 2.0))
        val proposalDifferent = coreB.propose(coreB.editScalar("pilot", 3.0))

        assertEquals(proposalA.target.definitionFingerprint, proposalB.target.definitionFingerprint)
        assertEquals(proposalA.previewId, proposalB.previewId)
        assertNotEquals(proposalA.previewId, proposalDifferent.previewId)
    }

    @Test
    fun readOnlyCapabilityCanInspectButCannotEditProposeOrSimulate() {
        val fixture = fixture(scalar("pilot", "U16", high = 1000.0), byteArrayOf(1, 0))
        val core = fixture.core(TuningCapability.READ_ONLY)
        assertEquals(1.0, core.currentScalar("pilot").value, 0.0)
        assertThrows(IllegalArgumentException::class.java) { core.editScalar("pilot", 2.0) }
    }

    private data class Fixture(
        val profile: UsbTunerStudioProfile,
        val snapshot: TuneSnapshot,
        val context: TuningContext,
        val scalarName: String,
        val originalPageBytes: ByteArray
    ) {
        fun core(capability: TuningCapability = TuningCapability.SIMULATION): SimulationTuningCore =
            SimulationTuningCore(profile, snapshot, context, setOf(scalarName), capability)
    }

    private fun fixture(scalar: UsbTuneScalar, rawAtScalar: ByteArray): Fixture {
        val pageSize = maxOf(32, scalar.offset + scalar.byteSize)
        val profile = profile(listOf(scalar), pageSize)
        val page = ByteArray(pageSize)
        require(rawAtScalar.size == scalar.byteSize)
        rawAtScalar.copyInto(page, scalar.offset)
        val snapshot = snapshot(profile, page, generation = 7)
        return Fixture(profile, snapshot, context(profile, snapshot, 7), scalar.name, page.copyOf())
    }

    private fun profile(scalars: List<UsbTuneScalar>, pageSize: Int): UsbTunerStudioProfile =
        UsbTunerStudioProfile(
            signature = "rusEFI t2-test",
            queryCommand = "S",
            outputCommand = "O%2o%2c",
            outputBlockSize = 64,
            envelopeFormat = "msEnvelope_1.0",
            endianness = "little",
            channels = emptyList(),
            tunePages = listOf(UsbTunePage(1, 0, pageSize, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
            tuneScalars = scalars,
            importedName = "mainController.ini"
        )

    private fun snapshot(profile: UsbTunerStudioProfile, pageBytes: ByteArray, generation: Long): TuneSnapshot =
        TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(
                TunePageSnapshot(1, 0, pageBytes.size, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, pageBytes)
            ),
            generation = generation,
            capturedAtEpochMs = 1234L
        )

    private fun context(profile: UsbTunerStudioProfile, snapshot: TuneSnapshot, generation: Long): TuningContext =
        TuningContext(
            sessionId = 99,
            generation = generation,
            source = TuningDataSource.LIVE,
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            tuneFingerprint = snapshot.fingerprint
        )

    private fun scalar(
        name: String,
        dataType: String,
        offset: Int = 0,
        scale: Double = 1.0,
        translate: Double = 0.0,
        low: Double = 0.0,
        high: Double = 100.0
    ): UsbTuneScalar = UsbTuneScalar(
        name = name,
        pageNumber = 1,
        dataType = dataType,
        offset = offset,
        unit = "unit",
        scale = scale,
        translate = translate,
        low = low,
        high = high,
        digits = 2
    )
}
