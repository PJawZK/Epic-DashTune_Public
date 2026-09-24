package com.buttonbox.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningCoreSafetyContractTest {
    @Test
    fun modifiedProfileDefinitionCannotBeUsedAgainstExistingSnapshot() {
        val fixture = fixture()
        val modifiedProfile = fixture.profile.copy(
            tuneScalars = listOf(fixture.scalar.copy(scale = 2.0))
        )

        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(modifiedProfile, fixture.snapshot, fixture.context, setOf("pilot"))
        }
    }

    @Test
    fun changedEcuSignatureIsRejected() {
        val fixture = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(
                fixture.profile,
                fixture.snapshot,
                fixture.context.copy(ecuSignature = "different ECU"),
                setOf("pilot")
            )
        }
    }

    @Test
    fun demoAndSelfTestOwnershipAreRejected() {
        val fixture = fixture()
        for (source in listOf(TuningDataSource.DEMO, TuningDataSource.SELF_TEST)) {
            assertThrows(IllegalArgumentException::class.java) {
                SimulationTuningCore(
                    fixture.profile,
                    fixture.snapshot,
                    fixture.context.copy(source = source),
                    setOf("pilot")
                )
            }
        }
    }

    @Test
    fun infinityAndRawUnderflowFailValidation() {
        val fixture = fixture()
        val core = fixture.core
        assertFalse(core.editScalar("pilot", Double.POSITIVE_INFINITY).validation.valid)

        val underflowScalar = fixture.scalar.copy(dataType = "U08", low = -1000.0, high = 1000.0, offset = 0)
        val profile = fixture.profile.copy(tuneScalars = listOf(underflowScalar))
        val page = ByteArray(32)
        val snapshot = TuneSnapshot.create(
            profile.signature,
            profile.tuneProfileFingerprint(),
            listOf(TunePageSnapshot(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, page)),
            17,
            1L
        )
        val context = fixture.context.copy(
            profileFingerprint = profile.tuneProfileFingerprint(),
            tuneFingerprint = snapshot.fingerprint
        )
        val underflowCore = SimulationTuningCore(profile, snapshot, context, setOf("pilot"))
        val edit = underflowCore.editScalar("pilot", -1.0)
        assertFalse(edit.validation.valid)
        assertTrue(edit.validation.errors.any { it.contains("overflows") })
    }

    @Test
    fun tamperedPreviewIdIsRejectedBeforeSimulation() {
        val fixture = fixture()
        val proposal = fixture.core.propose(fixture.core.editScalar("pilot", 2600.0))
        val tampered = TuningScalarChangePlan(
            context = proposal.context,
            target = proposal.target,
            originalValue = proposal.originalValue,
            requestedValue = proposal.requestedValue,
            effectiveEncodedValue = proposal.effectiveEncodedValue,
            beforeBytes = proposal.originalBytes(),
            afterBytes = proposal.encodedBytes(),
            byteDiffs = proposal.byteDiffs,
            affectedStartOffset = proposal.affectedStartOffset,
            affectedEndOffset = proposal.affectedEndOffset,
            previewId = "0".repeat(64),
            state = TuningValueState.PROPOSED
        )

        assertThrows(IllegalArgumentException::class.java) { fixture.core.simulate(tampered) }
    }

    private data class Fixture(
        val scalar: UsbTuneScalar,
        val profile: UsbTunerStudioProfile,
        val snapshot: TuneSnapshot,
        val context: TuningContext,
        val core: SimulationTuningCore
    )

    private fun fixture(): Fixture {
        val scalar = UsbTuneScalar(
            name = "pilot",
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
            signature = "rusEFI t2-safety-test",
            queryCommand = "S",
            outputCommand = "O%2o%2c",
            outputBlockSize = 64,
            envelopeFormat = "msEnvelope_1.0",
            endianness = "little",
            channels = emptyList(),
            tunePages = listOf(UsbTunePage(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
            tuneScalars = listOf(scalar)
        )
        val page = ByteArray(32)
        page[12] = 0xC4.toByte()
        page[13] = 0x09
        val snapshot = TuneSnapshot.create(
            profile.signature,
            profile.tuneProfileFingerprint(),
            listOf(TunePageSnapshot(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, page)),
            17,
            1L
        )
        val context = TuningContext(
            sessionId = 101,
            generation = 17,
            source = TuningDataSource.LIVE,
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            tuneFingerprint = snapshot.fingerprint
        )
        return Fixture(
            scalar,
            profile,
            snapshot,
            context,
            SimulationTuningCore(profile, snapshot, context, setOf("pilot"))
        )
    }
}
