package com.buttonbox.ble

import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Remaining deterministic W1 closure checks that were not already explicit in T2 acceptance. */
class TuningW1ClosureTest {
    @Test
    fun tuningCoreHasNoMutationBurnRawAddressOrMultiTargetExecutionSurface() {
        val methods = SimulationTuningCore::class.java.declaredMethods
        val forbiddenNames = listOf("write", "burn", "transfer", "execute", "apply", "flash", "send")

        assertTrue(methods.none { method -> forbiddenNames.any { token -> method.name.contains(token, ignoreCase = true) } })
        assertTrue(methods.none { method -> method.parameterTypes.any { it == ByteArray::class.java } })

        val publicNames = methods.map { it.name }.toSet()
        assertTrue("currentScalar" in publicNames)
        assertTrue("editScalar" in publicNames)
        assertTrue("propose" in publicNames)
        assertTrue("simulate" in publicNames)
        assertTrue("revert" in publicNames)

        val fixture = fixture()
        assertThrows(IllegalArgumentException::class.java) { fixture.core.currentScalar("veTable") }
        assertThrows(IllegalArgumentException::class.java) { fixture.core.currentScalar("raw:0:12:2") }
    }

    @Test
    fun everyIntegralPrimitiveRejectsRawUnderflowAndOverflowAndF32RejectsOverflow() {
        val cases = listOf(
            Triple(scalar("U08", -1.0, 256.0), -1.0, 256.0),
            Triple(scalar("S08", -129.0, 128.0), -129.0, 128.0),
            Triple(scalar("U16", -1.0, 65536.0), -1.0, 65536.0),
            Triple(scalar("S16", -32769.0, 32768.0), -32769.0, 32768.0),
            Triple(scalar("U32", -1.0, 4294967296.0), -1.0, 4294967296.0),
            Triple(
                scalar("S32", Int.MIN_VALUE.toDouble() - 1.0, Int.MAX_VALUE.toDouble() + 1.0),
                Int.MIN_VALUE.toDouble() - 1.0,
                Int.MAX_VALUE.toDouble() + 1.0
            )
        )

        cases.forEach { (definition, below, above) ->
            assertThrows(IllegalArgumentException::class.java) { TuningScalarCodec.encode(definition, below) }
            assertThrows(IllegalArgumentException::class.java) { TuningScalarCodec.encode(definition, above) }
        }

        val f32 = scalar("F32", -Double.MAX_VALUE, Double.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { TuningScalarCodec.encode(f32, Double.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { TuningScalarCodec.encode(f32, -Double.MAX_VALUE) }
    }

    @Test
    fun invalidScaleTranslationAndPageRangesFailClosed() {
        val fixture = fixture()
        for (badScale in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val profile = fixture.profile.copy(tuneScalars = listOf(fixture.scalar.copy(scale = badScale)))
            val snapshot = snapshotFor(profile)
            val context = contextFor(profile, snapshot)
            assertThrows(IllegalArgumentException::class.java) {
                SimulationTuningCore(profile, snapshot, context, setOf("pilot")).currentScalar("pilot")
            }
        }

        val badTranslationProfile = fixture.profile.copy(
            tuneScalars = listOf(fixture.scalar.copy(translate = Double.POSITIVE_INFINITY))
        )
        val badTranslationSnapshot = snapshotFor(badTranslationProfile)
        val badTranslationContext = contextFor(badTranslationProfile, badTranslationSnapshot)
        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(
                badTranslationProfile,
                badTranslationSnapshot,
                badTranslationContext,
                setOf("pilot")
            ).currentScalar("pilot")
        }

        val overrunScalar = fixture.scalar.copy(offset = 31)
        val overrunProfile = fixture.profile.copy(tuneScalars = listOf(overrunScalar))
        val overrunSnapshot = snapshotFor(overrunProfile)
        val overrunContext = contextFor(overrunProfile, overrunSnapshot)
        assertThrows(IllegalArgumentException::class.java) {
            SimulationTuningCore(overrunProfile, overrunSnapshot, overrunContext, setOf("pilot")).currentScalar("pilot")
        }
    }

    @Test
    fun boundedReadPayloadFuzzIsDeterministicAndLittleEndian() {
        val random = Random(0x5741L)
        repeat(2000) {
            val pageSize = 1 + random.nextInt(65535)
            val pageIdentifier = random.nextInt(65536)
            val page = UsbTunePage(1, pageIdentifier, pageSize, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)
            val offset = random.nextInt(pageSize)
            val count = 1 + random.nextInt(pageSize - offset)

            val a = UsbTuneReadCodec.buildRangePayload(page, offset, count)
            val b = UsbTuneReadCodec.buildRangePayload(page, offset, count)
            assertArrayEquals(a, b)
            assertEquals(7, a.size)
            assertEquals('R'.code, a[0].toInt() and 0xff)
            assertEquals(pageIdentifier, u16le(a, 1))
            assertEquals(offset, u16le(a, 3))
            assertEquals(count, u16le(a, 5))
        }
    }

    @Test
    fun malformedReadRangesAndRepliesFailClosedAcrossBoundarySweep() {
        val page = UsbTunePage(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)
        val invalidRanges = listOf(
            -1 to 1,
            0 to 0,
            32 to 1,
            31 to 2,
            33 to 1,
            0 to 33
        )
        invalidRanges.forEach { (offset, count) ->
            assertThrows(IllegalArgumentException::class.java) {
                UsbTuneReadCodec.buildRangePayload(page, offset, count)
            }
        }

        val expected = 2
        for (size in 0..8) {
            if (size == expected || size == expected + 1) continue
            assertThrows(IllegalArgumentException::class.java) {
                UsbTuneReadCodec.extractData(ByteArray(size), expected)
            }
        }
        for (status in 2..255) {
            assertThrows(IllegalArgumentException::class.java) {
                UsbTuneReadCodec.extractData(byteArrayOf(status.toByte(), 0x00, 0x00), expected)
            }
        }
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
        val profile = profileOf(scalar)
        val snapshot = snapshotFor(profile)
        val context = contextFor(profile, snapshot)
        return Fixture(scalar, profile, snapshot, context, SimulationTuningCore(profile, snapshot, context, setOf("pilot")))
    }

    private fun profileOf(scalar: UsbTuneScalar): UsbTunerStudioProfile = UsbTunerStudioProfile(
        signature = "rusEFI w1-test",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 64,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = emptyList(),
        tunePages = listOf(UsbTunePage(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
        tuneScalars = listOf(scalar)
    )

    private fun snapshotFor(profile: UsbTunerStudioProfile): TuneSnapshot {
        val bytes = ByteArray(32)
        bytes[12] = 0xC4.toByte()
        bytes[13] = 0x09
        return TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, bytes)),
            generation = 23,
            capturedAtEpochMs = 1L
        )
    }

    private fun contextFor(profile: UsbTunerStudioProfile, snapshot: TuneSnapshot): TuningContext = TuningContext(
        sessionId = 42,
        generation = snapshot.generation,
        source = TuningDataSource.LIVE,
        ecuSignature = profile.signature,
        profileFingerprint = profile.tuneProfileFingerprint(),
        tuneFingerprint = snapshot.fingerprint
    )

    private fun scalar(type: String, low: Double, high: Double): UsbTuneScalar = UsbTuneScalar(
        name = "vector",
        pageNumber = 1,
        dataType = type,
        offset = 0,
        unit = "",
        scale = 1.0,
        translate = 0.0,
        low = low,
        high = high,
        digits = 0
    )

    private fun u16le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
}
