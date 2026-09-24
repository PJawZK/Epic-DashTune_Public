package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class T3BoundTransportPortTest {
    @Test
    fun onlyApprovedProfileDerivedBodiesReachTransportClosure() {
        val fixture = fixture()
        val seen = mutableListOf<ByteArray>()
        val port = port(fixture) { body, _, _ ->
            seen += body.copyOf()
            if (body[0] == 'R'.code.toByte()) byteArrayOf(0x00) + fixture.plan.encodedBytes() else byteArrayOf(0x00)
        }

        val candidate = port.exchange(
            T3PilotRamProtocol.candidateWriteBody(fixture.plan),
            1,
            T3BoundTransportPort.LABEL_CANDIDATE
        )
        val readBack = port.exchange(
            T3PilotRamProtocol.readBackBody(fixture.plan),
            fixture.plan.target.byteSize + 1,
            T3BoundTransportPort.LABEL_CANDIDATE_READBACK
        )
        val restore = port.exchange(
            T3PilotRamProtocol.restoreWriteBody(fixture.plan),
            1,
            T3BoundTransportPort.LABEL_RESTORE
        )

        assertArrayEquals(byteArrayOf(0x00), candidate.body)
        assertArrayEquals(byteArrayOf(0x00) + fixture.plan.encodedBytes(), readBack.body)
        assertArrayEquals(byteArrayOf(0x00), restore.body)
        assertEquals(fixture.context.generation, candidate.generation)
        assertEquals(3, seen.size)
    }

    @Test
    fun arbitraryBodyWrongLabelAndWrongResponseBoundFailBeforeTransport() {
        val fixture = fixture()
        var exchanges = 0
        val port = port(fixture) { _, _, _ -> exchanges++; byteArrayOf(0x00) }

        assertThrows(IllegalArgumentException::class.java) {
            port.exchange(byteArrayOf('C'.code.toByte(), 0x55), 1, T3BoundTransportPort.LABEL_CANDIDATE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            port.exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), 1, "other")
        }
        assertThrows(IllegalArgumentException::class.java) {
            port.exchange(
                T3PilotRamProtocol.readBackBody(fixture.plan),
                64,
                T3BoundTransportPort.LABEL_CANDIDATE_READBACK
            )
        }
        assertEquals(0, exchanges)
    }

    @Test
    fun nativeOwnerThreadAndGenerationAreMandatory() {
        val fixture = fixture()
        var generation = fixture.context.generation
        var owner = false
        var exchanges = 0
        val port = port(
            fixture,
            generationProvider = { generation },
            ownerThread = { owner }
        ) { _, _, _ -> exchanges++; byteArrayOf(0x00) }

        assertThrows(IllegalArgumentException::class.java) {
            port.exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), 1, T3BoundTransportPort.LABEL_CANDIDATE)
        }
        owner = true
        generation++
        assertThrows(IllegalArgumentException::class.java) {
            port.exchange(T3PilotRamProtocol.candidateWriteBody(fixture.plan), 1, T3BoundTransportPort.LABEL_CANDIDATE)
        }
        assertEquals(0, exchanges)
    }

    @Test
    fun generationAdvanceDuringExchangeIsReturnedAsStaleResponseEvidence() {
        val fixture = fixture()
        var generation = fixture.context.generation
        val port = port(fixture, generationProvider = { generation }) { _, _, _ ->
            generation++
            byteArrayOf(0x00)
        }

        val response = port.exchange(
            T3PilotRamProtocol.candidateWriteBody(fixture.plan),
            1,
            T3BoundTransportPort.LABEL_CANDIDATE
        )
        assertEquals(fixture.context.generation + 1, response.generation)
    }

    @Test
    fun completeSnapshotReaderCannotReturnAnotherGeneration() {
        val fixture = fixture()
        val wrongGenerationSnapshot = withGeneration(fixture.baseline, fixture.context.generation + 1)
        val port = port(fixture, snapshotReader = { wrongGenerationSnapshot }) { _, _, _ -> byteArrayOf(0x00) }

        assertThrows(IllegalArgumentException::class.java) {
            port.readCompleteTuneSnapshot(fixture.context.generation)
        }
    }

    @Test
    fun changedProfileDefinitionInvalidatesPreparedPlanBeforeTransport() {
        val fixture = fixture()
        val changed = fixture.profile.copy(
            tuneScalars = fixture.profile.tuneScalars.map {
                if (it.name == T3PilotAuthority.SCALAR_NAME) it.copy(offset = it.offset + 2) else it
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            port(fixture, profileProvider = { changed }) { _, _, _ -> byteArrayOf(0x00) }
        }
    }

    @Test
    fun profileDerivedPageAndOffsetDriveProtocolBody() {
        val movedProfile = profile(pageIdentifier = 0x1234, pageSize = 2048, scalarOffset = 400)
        val moved = fixture(movedProfile)
        assertArrayEquals(
            byteArrayOf('R'.code.toByte(), 0x34, 0x12, 0x90.toByte(), 0x01, 0x02, 0x00),
            T3PilotRamProtocol.readBackBody(moved.plan)
        )
        assertArrayEquals(
            byteArrayOf('C'.code.toByte(), 0x34, 0x12, 0x90.toByte(), 0x01, 0x02, 0x00) + moved.plan.encodedBytes(),
            T3PilotRamProtocol.candidateWriteBody(moved.plan)
        )
    }

    @Test
    fun profileDerivedPrimitiveWidthDrivesProtocolCount() {
        val widened = fixture(profile(pageIdentifier = 0x2222, pageSize = 4096, scalarOffset = 200, dataType = "U32"))
        assertEquals(4, widened.plan.target.byteSize)
        assertArrayEquals(
            byteArrayOf('R'.code.toByte(), 0x22, 0x22, 0xC8.toByte(), 0x00, 0x04, 0x00),
            T3PilotRamProtocol.readBackBody(widened.plan)
        )
        assertEquals(11, T3PilotRamProtocol.candidateWriteBody(widened.plan).size)
    }

    private data class Fixture(
        val profile: UsbTunerStudioProfile,
        val baseline: TuneSnapshot,
        val context: TuningContext,
        val plan: TuningScalarChangePlan
    )

    private fun fixture(profile: UsbTunerStudioProfile = profile()): Fixture {
        val scalar = profile.tuneScalars.single { it.name == T3PilotAuthority.SCALAR_NAME }
        val page = profile.tunePages.single { it.pageNumber == scalar.pageNumber }
        val pageBytes = ByteArray(page.size)
        val original = TuningScalarCodec.encode(scalar, 2500.0)
        original.copyInto(pageBytes, scalar.offset)
        val baseline = TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(page.pageNumber, page.identifier, page.size, page.readCommand, pageBytes)),
            generation = 77L,
            capturedAtEpochMs = 1L
        )
        val prepared = T3BenchProposalFactory.prepare(profile, baseline, 77L, 2600.0)
        return Fixture(profile, baseline, prepared.plan.context, prepared.plan)
    }

    private fun profile(
        pageIdentifier: Int = 0,
        pageSize: Int = 55_764,
        scalarOffset: Int = 12,
        dataType: String = "U16"
    ) = UsbTunerStudioProfile(
        signature = "rusEFI test.MEGA144H7",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 4_080,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = safetyChannels(),
        tunePages = listOf(UsbTunePage(1, pageIdentifier, pageSize, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
        tuneScalars = listOf(
            UsbTuneScalar(
                name = T3PilotAuthority.SCALAR_NAME,
                pageNumber = 1,
                dataType = dataType,
                offset = scalarOffset,
                unit = "RPM",
                scale = 1.0,
                translate = 0.0,
                low = 0.0,
                high = 30_000.0,
                digits = 0
            )
        )
    )

    private fun safetyChannels() = listOf(
        UsbOutputChannel("RPMValue", "scalar", "U16", 4, "RPM", 1.0, 0.0),
        UsbOutputChannel("VBatt", "scalar", "U16", 50, "V", 0.0003333333333333333, 0.0),
        UsbOutputChannel("flashWritePending", "bits", "U32", 1812, "", 1.0, 0.0, 24, 24),
        UsbOutputChannel("flashWriteErrors", "scalar", "U08", 2012, "", 1.0, 0.0),
        UsbOutputChannel("isCranking", "bits", "U32", 3644, "", 1.0, 0.0, 3, 3)
    )

    private fun port(
        fixture: Fixture,
        profileProvider: () -> UsbTunerStudioProfile = { fixture.profile },
        generationProvider: () -> Long = { fixture.context.generation },
        ownerThread: () -> Boolean = { true },
        snapshotReader: (Long) -> TuneSnapshot = { fixture.baseline },
        exchange: (ByteArray, Int, String) -> ByteArray
    ) = T3BoundTransportPort(
        plan = fixture.plan,
        profileProvider = profileProvider,
        currentGeneration = generationProvider,
        isNativeOwnerThread = ownerThread,
        exchangeBody = exchange,
        snapshotReader = snapshotReader,
        nativeOutputSampleReader = {
            T3NativeOutputSample(validSafetyBlock(fixture.profile.outputBlockSize), fixture.context.generation, 1_000L)
        },
        safetyResolver = T3NativeSafetyInputsResolver(250L),
        nowElapsedMs = { 1_050L },
        supportPowerConfirmed = { true }
    )

    private fun validSafetyBlock(size: Int) = ByteArray(size).also { block ->
        block[50] = 0xF0.toByte()
        block[51] = 0x8E.toByte() // 36600 -> 12.2 V
    }

    private fun withGeneration(snapshot: TuneSnapshot, generation: Long) = TuneSnapshot.create(
        ecuSignature = snapshot.ecuSignature,
        profileFingerprint = snapshot.profileFingerprint,
        pages = snapshot.pages.map { TunePageSnapshot(it.pageNumber, it.identifier, it.size, it.readCommand, it.bytes()) },
        generation = generation,
        capturedAtEpochMs = snapshot.capturedAtEpochMs + 1
    )
}
