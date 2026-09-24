package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningWorkspaceTest {
    private fun profile(scalars: List<UsbTuneScalar>, pageSize: Int = 32) = UsbTunerStudioProfile(
        signature = "rusEFI test",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 64,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = emptyList(),
        tunePages = listOf(UsbTunePage(1, 0, pageSize, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
        tuneScalars = scalars,
        importedName = "current.ini"
    )

    private fun scalar(name: String, offset: Int, scale: Double = 1.0) = UsbTuneScalar(
        name = name,
        pageNumber = 1,
        dataType = "U16",
        offset = offset,
        unit = "RPM",
        scale = scale,
        translate = 0.0,
        low = 0.0,
        high = 30000.0,
        digits = 0
    )

    private fun snapshot(profile: UsbTunerStudioProfile, bytes: ByteArray, generation: Long = 7L) =
        TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(1, 0, bytes.size, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, bytes)),
            generation = generation,
            capturedAtEpochMs = 1234L
        )

    @Test
    fun workspaceReadsSemanticValuesFromCurrentProfileAndSnapshotWithoutRawAddressMetadata() {
        val profile = profile(listOf(scalar("alpha", 2), scalar("beta", 4, 0.5)))
        val bytes = ByteArray(32)
        bytes[2] = 0xD2.toByte(); bytes[3] = 0x04 // 1234
        bytes[4] = 0xD0.toByte(); bytes[5] = 0x07 // 2000 raw -> 1000 engineering
        val workspace = TuningWorkspaceBuilder.build(profile, snapshot(profile, bytes), 7L)

        assertEquals(2, workspace.scalars.size)
        assertEquals(1234.0, workspace.scalars[0].value, 0.0)
        assertEquals(1000.0, workspace.scalars[1].value, 0.0)
        assertEquals("current.ini", workspace.importedProfileName)

        val json = workspace.toJson().toString()
        assertTrue(json.contains("\"capability\":\"READ_ONLY\""))
        assertTrue(json.contains("\"alpha\""))
        assertFalse(json.contains("\"offset\""))
        assertFalse(json.contains("\"pageNumber\""))
        assertFalse(json.contains("rawHex"))
        assertFalse(json.contains("proposedRaw"))
    }

    @Test
    fun duplicateScalarNamesAreReportedAndExcludedInsteadOfBecomingAmbiguousUiTargets() {
        val profile = profile(listOf(scalar("same", 2), scalar("same", 4), scalar("unique", 6)))
        val bytes = ByteArray(32)
        bytes[6] = 10
        val workspace = TuningWorkspaceBuilder.build(profile, snapshot(profile, bytes), 7L)

        assertEquals(1, workspace.ambiguousScalarNames)
        assertEquals(2, workspace.skippedScalars)
        assertEquals(listOf("unique"), workspace.scalars.map { it.name })
    }

    @Test
    fun obsoleteGenerationFailsClosed() {
        val profile = profile(listOf(scalar("alpha", 2)))
        val snapshot = snapshot(profile, ByteArray(32), generation = 7L)
        assertThrows(IllegalArgumentException::class.java) {
            TuningWorkspaceBuilder.build(profile, snapshot, 8L)
        }
    }

    @Test
    fun mismatchedProfileFingerprintFailsClosed() {
        val profile = profile(listOf(scalar("alpha", 2)))
        val other = profile(listOf(scalar("alpha", 4)))
        val snapshot = snapshot(other, ByteArray(32), generation = 7L)
        assertThrows(IllegalArgumentException::class.java) {
            TuningWorkspaceBuilder.build(profile, snapshot, 7L)
        }
    }
}
