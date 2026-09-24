package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningSemanticPreviewTest {
    private fun scalar(name: String, offset: Int, low: Double = 0.0, high: Double = 5000.0) =
        UsbTuneScalar(
            name = name,
            pageNumber = 1,
            dataType = "U16",
            offset = offset,
            unit = "RPM",
            scale = 1.0,
            translate = 0.0,
            low = low,
            high = high,
            digits = 0
        )

    private fun profile(vararg scalars: UsbTuneScalar) = UsbTunerStudioProfile(
        signature = "rusEFI test",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 64,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = emptyList(),
        tunePages = listOf(UsbTunePage(1, 0, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)),
        tuneScalars = scalars.toList(),
        importedName = "current.ini"
    )

    private fun snapshot(profile: UsbTunerStudioProfile, raw: Int = 2500, generation: Long = 9L): TuneSnapshot {
        val bytes = ByteArray(32)
        bytes[2] = (raw and 0xff).toByte()
        bytes[3] = ((raw ushr 8) and 0xff).toByte()
        return TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(1, 0, bytes.size, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, bytes)),
            generation = generation,
            capturedAtEpochMs = 1L
        )
    }

    @Test
    fun validScalarPreviewUsesProductionResolverAndHidesStorageMetadata() {
        val profile = profile(scalar("engineSnifferRpmThreshold", 2))
        val preview = TuningSemanticPreviewBuilder.preview(
            profile,
            snapshot(profile),
            9L,
            SemanticTuningWriteRequest(TuningWriteKind.SCALAR, "engineSnifferRpmThreshold", 2550.0)
        )

        assertEquals(2500.0, preview.currentValue, 0.0)
        assertEquals(2550.0, preview.requestedValue, 0.0)
        assertEquals(2550.0, preview.effectiveValue, 0.0)
        assertTrue(preview.changedBytes > 0)
        assertFalse(preview.noOp)

        val json = preview.toJson().toString()
        assertTrue(json.contains("\"capability\":\"SEMANTIC_PREVIEW\""))
        assertTrue(json.contains("\"writeEligible\":true"))
        assertFalse(json.contains("\"offset\""))
        assertFalse(json.contains("\"pageNumber\""))
        assertFalse(json.contains("rawHex"))
        assertFalse(json.contains("encodedBytes"))
    }

    @Test
    fun noOpIsReportedExplicitlyInsteadOfRaisedAsPreviewFailure() {
        val profile = profile(scalar("pilot", 2))
        val preview = TuningSemanticPreviewBuilder.preview(
            profile,
            snapshot(profile),
            9L,
            SemanticTuningWriteRequest(TuningWriteKind.SCALAR, "pilot", 2500.0)
        )
        assertTrue(preview.noOp)
        assertEquals(0, preview.changedBytes)
        assertTrue(preview.toJson().getBoolean("noOp"))
        assertFalse(preview.toJson().getBoolean("writeEligible"))
    }

    @Test
    fun outOfBoundsDuplicateAndStaleGenerationFailClosed() {
        val bounded = profile(scalar("pilot", 2, low = 1000.0, high = 3000.0))
        assertThrows(IllegalArgumentException::class.java) {
            TuningSemanticPreviewBuilder.preview(
                bounded,
                snapshot(bounded),
                9L,
                SemanticTuningWriteRequest(TuningWriteKind.SCALAR, "pilot", 3500.0)
            )
        }

        val duplicate = profile(scalar("same", 2), scalar("same", 4))
        assertThrows(IllegalArgumentException::class.java) {
            TuningSemanticPreviewBuilder.preview(
                duplicate,
                snapshot(duplicate),
                9L,
                SemanticTuningWriteRequest(TuningWriteKind.SCALAR, "same", 2600.0)
            )
        }

        val current = profile(scalar("pilot", 2))
        assertThrows(IllegalArgumentException::class.java) {
            TuningSemanticPreviewBuilder.preview(
                current,
                snapshot(current, generation = 9L),
                10L,
                SemanticTuningWriteRequest(TuningWriteKind.SCALAR, "pilot", 2600.0)
            )
        }
    }
}
