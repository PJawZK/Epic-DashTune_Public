package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthoritativeTuneSnapshotReaderTest {
    @Test
    fun readsEveryPlannedChunkAndBuildsExactImmutableSnapshot() {
        val profile = profile()
        val context = context(profile)
        val pageData = mapOf(
            1 to byteArrayOf(1, 2, 3, 4, 5),
            2 to byteArrayOf(9, 8, 7)
        )
        val seenPayloads = mutableListOf<ByteArray>()
        val seenBounds = mutableListOf<Int>()
        val reader = AuthoritativeTuneSnapshotReader(profile, context, chunkBytes = 2)

        val snapshot = reader.read(capturedAtEpochMs = 1234L) { payload, maxResponseBody, _ ->
            seenPayloads += payload.copyOf()
            seenBounds += maxResponseBody
            val pageIdentifier = u16(payload, 1)
            val offset = u16(payload, 3)
            val count = u16(payload, 5)
            val pageNumber = profile.tunePages.single { it.identifier == pageIdentifier }.pageNumber
            byteArrayOf(0x00) + pageData.getValue(pageNumber).copyOfRange(offset, offset + count)
        }

        assertEquals(5, seenPayloads.size)
        assertEquals(listOf(3, 3, 2, 3, 2), seenBounds)
        assertEquals(8, snapshot.totalBytes)
        assertEquals(context.generation, snapshot.generation)
        assertEquals(1234L, snapshot.capturedAtEpochMs)
        assertEquals(profile.signature, snapshot.ecuSignature)
        assertEquals(profile.tuneProfileFingerprint(), snapshot.profileFingerprint)
        assertArrayEquals(pageData.getValue(1), snapshot.pages.single { it.pageNumber == 1 }.bytes())
        assertArrayEquals(pageData.getValue(2), snapshot.pages.single { it.pageNumber == 2 }.bytes())

        val leaked = snapshot.pages.single { it.pageNumber == 1 }.bytes()
        leaked[0] = 99
        assertArrayEquals(pageData.getValue(1), snapshot.pages.single { it.pageNumber == 1 }.bytes())
    }

    @Test
    fun identityAndResponseFailuresFailClosed() {
        val profile = profile()
        val wrongContext = context(profile).copy(profileFingerprint = "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) {
            AuthoritativeTuneSnapshotReader(profile, wrongContext, chunkBytes = 2)
        }

        val reader = AuthoritativeTuneSnapshotReader(profile, context(profile), chunkBytes = 2)
        assertThrows(IllegalArgumentException::class.java) {
            reader.read(1234L) { _, _, _ -> byteArrayOf(0x00) }
        }

        assertThrows(IllegalArgumentException::class.java) {
            reader.read(1234L) { _, _, _ -> byteArrayOf(0x82.toByte(), 1, 2) }
        }
    }

    @Test
    fun payloadIsExactlyTheAcceptedRangeReadPlan() {
        val profile = profile()
        val expected = TuneSnapshotReadPlanner.build(profile, 2).chunks.map { it.payload }
        val actual = mutableListOf<ByteArray>()
        val reader = AuthoritativeTuneSnapshotReader(profile, context(profile), chunkBytes = 2)
        reader.read(1234L) { payload, _, _ ->
            actual += payload.copyOf()
            val count = u16(payload, 5)
            ByteArray(count + 1)
        }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (left, right) -> assertArrayEquals(left, right) }
    }

    private fun profile() = UsbTunerStudioProfile(
        signature = "rusEFI test.MEGA144H7",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 128,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = emptyList(),
        tunePages = listOf(
            UsbTunePage(1, 0x0000, 5, UsbTuneReadCodec.SUPPORTED_READ_COMMAND),
            UsbTunePage(2, 0x0100, 3, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)
        ),
        tuneScalars = emptyList()
    )

    private fun context(profile: UsbTunerStudioProfile) = TuningContext(
        sessionId = 5L,
        generation = 11L,
        source = TuningDataSource.LIVE,
        ecuSignature = profile.signature,
        profileFingerprint = profile.tuneProfileFingerprint(),
        tuneFingerprint = "a".repeat(64)
    )

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
}
