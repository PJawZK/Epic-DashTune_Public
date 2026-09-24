package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneSnapshotTest {
    @Test
    fun exactMega144PagesProduceCompleteDeterministicReadPlan() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())
        val first = TuneSnapshotReadPlanner.build(profile)
        val second = TuneSnapshotReadPlanner.build(profile)

        assertEquals(60_764, first.totalBytes)
        assertEquals(60, first.totalChunks)
        assertEquals(55, first.chunks.count { it.pageNumber == 1 })
        assertEquals(5, first.chunks.count { it.pageNumber == 2 })
        assertEquals(468, first.chunks.filter { it.pageNumber == 1 }.last().count)
        assertEquals(904, first.chunks.filter { it.pageNumber == 2 }.last().count)
        assertArrayEquals(
            byteArrayOf(0x52, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04),
            first.chunks.first().payload
        )
        assertArrayEquals(
            byteArrayOf(0x52, 0x00, 0x01, 0x00, 0x00, 0x00, 0x04),
            first.chunks.first { it.pageNumber == 2 }.payload
        )
        assertEquals(first.profileFingerprint, second.profileFingerprint)
        assertTrue(first.profileFingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun bitOptionParserPreservesExplicitAndSequentialValuesWithoutDynamicRegex() {
        val source = exactProfile().replace(
            "engineSnifferRpmThreshold = scalar, U16, 12, \"RPM\", 1, 0, 0, 30000, 0",
            "engineSnifferRpmThreshold = scalar, U16, 12, \"RPM\", 1, 0, 0, 30000, 0\n" +
                "testMode = bits, U08, 14, [0:1], 0 = \"Off\", 2 = \"Auto\", \"Fallback\""
        ) + "\n[UserDefined]\ndialog = testDialog, \"Test\"\nfield = \"Mode\", testMode\n"
        val profile = UsbTunerStudioProfileParser.parse(source)
        val options = profile.tuneBitFields.first { it.name == "testMode" }.options

        assertEquals(listOf(0, 2, 3), options.map { it.value })
        assertEquals(listOf("Off", "Auto", "Fallback"), options.map { it.label })
    }

    @Test
    fun parserFastPathPreservesQuotedSemicolonsAndSpacedDefinitionKinds() {
        val source = exactProfile()
            .replace(
                "engineSnifferRpmThreshold = scalar, U16, 12, \"RPM\", 1, 0, 0, 30000, 0",
                "engineSnifferRpmThreshold =    scalar, U16, 12, \"RPM\", 1, 0, 0, 30000, 0 ; tune comment"
            )
            .replace(
                "RPMValue = scalar, U16, 4, \"RPM\", 1, 0",
                "RPMValue =   scalar, U16, 4, \"R;PM\", 1, 0 ; output comment"
            )

        val profile = UsbTunerStudioProfileParser.parse(source)

        assertEquals("R;PM", profile.channels.first { it.name == "RPMValue" }.unit)
        assertEquals(12, profile.tuneScalars.first { it.name == "engineSnifferRpmThreshold" }.offset)
    }

    @Test
    fun measuredParserPreservesProfileAndReportsStages() {
        val source = exactProfile()
        val measured = UsbTunerStudioProfileParser.parseMeasured(source, "mainController.ini")
        val normal = UsbTunerStudioProfileParser.parse(source, "mainController.ini")

        assertEquals(normal.signature, measured.profile.signature)
        assertEquals(normal.outputBlockSize, measured.profile.outputBlockSize)
        assertEquals(normal.tunePages, measured.profile.tunePages)
        assertEquals(normal.tuneScalars, measured.profile.tuneScalars)
        assertTrue(measured.metrics.lineCount > 0)
        assertTrue(measured.metrics.scanElapsedMs >= 0)
        assertTrue(measured.metrics.bitOptionsElapsedMs >= 0)
        assertTrue(measured.metrics.finalizeElapsedMs >= 0)
        assertTrue(measured.metrics.totalElapsedMs >= 0)
    }

    @Test
    fun completeSnapshotBackupRoundTripPreservesFingerprintAndBytes() {
        val profile = UsbTunerStudioProfileParser.parse(smallProfile())
        val pages = listOf(
            TunePageSnapshot(1, 0x0000, 4, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(1, 2, 3, 4)),
            TunePageSnapshot(2, 0x0100, 3, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(5, 6, 7))
        )
        val snapshot = TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = pages,
            generation = 7,
            capturedAtEpochMs = 123456789L
        )

        val restored = TuneSnapshot.fromBackupJson(snapshot.toBackupJson())

        assertEquals(7, snapshot.totalBytes)
        assertEquals(snapshot.fingerprint, restored.fingerprint)
        assertEquals(snapshot.profileFingerprint, restored.profileFingerprint)
        assertArrayEquals(snapshot.pages[0].bytes(), restored.pages[0].bytes())
        assertArrayEquals(snapshot.pages[1].bytes(), restored.pages[1].bytes())
        assertTrue(snapshot.compare(restored).tuneMatches)
    }

    @Test
    fun snapshotIsImmutableAgainstCallerByteMutation() {
        val source = byteArrayOf(1, 2, 3, 4)
        val page = TunePageSnapshot(1, 0, 4, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, source)
        source[0] = 99
        val exported = page.bytes()
        exported[1] = 88

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), page.bytes())
    }

    @Test
    fun comparisonReportsExactChangedPageRange() {
        val profile = UsbTunerStudioProfileParser.parse(smallProfile())
        val fingerprint = profile.tuneProfileFingerprint()
        val base = TuneSnapshot.create(
            profile.signature,
            fingerprint,
            listOf(
                TunePageSnapshot(1, 0, 4, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(1, 2, 3, 4)),
                TunePageSnapshot(2, 0x0100, 3, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(5, 6, 7))
            ),
            1,
            1L
        )
        val changed = TuneSnapshot.create(
            profile.signature,
            fingerprint,
            listOf(
                TunePageSnapshot(1, 0, 4, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(1, 9, 8, 4)),
                TunePageSnapshot(2, 0x0100, 3, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(5, 6, 7))
            ),
            2,
            2L
        )

        val comparison = base.compare(changed)
        assertTrue(comparison.identityMatches)
        assertFalse(comparison.tuneMatches)
        assertEquals(1, comparison.pageDiffs.size)
        assertEquals(2, comparison.pageDiffs.single().changedBytes)
        assertEquals(1, comparison.pageDiffs.single().firstChangedOffset)
        assertEquals(2, comparison.pageDiffs.single().lastChangedOffset)
    }

    @Test
    fun burnCommandChangeInvalidatesTuneProfileIdentity() {
        val original = UsbTunerStudioProfileParser.parse(exactProfile())
        val changed = UsbTunerStudioProfileParser.parse(
            exactProfile().replace("B%2i", "X%2i")
        )

        assertFalse(original.tuneProfileFingerprint().equals(changed.tuneProfileFingerprint(), ignoreCase = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompletePageFailsClosed() {
        TunePageSnapshot(1, 0, 4, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, byteArrayOf(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rangePastPageEndFailsClosed() {
        val page = UsbTunePage(1, 0, 100, UsbTuneReadCodec.SUPPORTED_READ_COMMAND)
        UsbTuneReadCodec.buildRangePayload(page, 90, 11)
    }

    private fun exactProfile(): String = """
        [TunerStudio]
        queryCommand = "S"
        signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"
        [Constants]
        messageEnvelopeFormat = msEnvelope_1.0
        endianness = little
        pageIdentifier = "\\x00\\x00", "\\x00\\x01"
        pageSize = 55764, 5000
        pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
        burnCommand = "B%2i", "B%2i"
        page = 1
        engineSnifferRpmThreshold = scalar, U16, 12, "RPM", 1, 0, 0, 30000, 0
        [OutputChannels]
        ochGetCommand = "O%2o%2c"
        ochBlockSize = 64
        RPMValue = scalar, U16, 4, "RPM", 1, 0
    """.trimIndent()

    private fun smallProfile(): String = """
        [TunerStudio]
        queryCommand = "S"
        signature = "rusEFI test snapshot"
        [Constants]
        messageEnvelopeFormat = msEnvelope_1.0
        endianness = little
        pageIdentifier = "\\x00\\x00", "\\x00\\x01"
        pageSize = 4, 3
        pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
        page = 1
        pilot = scalar, U16, 0, "", 1, 0, 0, 65535, 0
        [OutputChannels]
        ochGetCommand = "O%2o%2c"
        ochBlockSize = 64
        RPMValue = scalar, U16, 4, "RPM", 1, 0
    """.trimIndent()
}
