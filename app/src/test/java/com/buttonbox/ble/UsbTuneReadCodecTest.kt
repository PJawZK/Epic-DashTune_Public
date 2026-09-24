package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbTuneReadCodecTest {
    @Test
    fun exactMega144ProfileResolvesEngineSnifferScalarAndReadPayload() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())
        val scalar = profile.tuneScalars.first { it.name == "engineSnifferRpmThreshold" }
        val page = profile.tunePages.first { it.pageNumber == 1 }

        assertEquals("rusEFI master.2026.08.26.MEGA144H7.2273317132", profile.signature)
        assertEquals(2, profile.tunePages.size)
        assertEquals(0x0000, page.identifier)
        assertEquals(55764, page.size)
        assertEquals("R%2i%2o%2c", page.readCommand)
        assertEquals("B%2i", page.burnCommand)
        assertEquals("U16", scalar.dataType)
        assertEquals(12, scalar.offset)
        assertEquals("RPM", scalar.unit)
        assertEquals(1.0, scalar.scale, 0.0)
        assertEquals(0.0, scalar.translate, 0.0)
        assertEquals(0.0, scalar.low, 0.0)
        assertEquals(30000.0, scalar.high, 0.0)
        assertEquals(2, scalar.byteSize)

        assertArrayEquals(
            byteArrayOf(0x52, 0x00, 0x00, 0x0C, 0x00, 0x02, 0x00),
            UsbTuneReadCodec.buildPayload(profile, "engineSnifferRpmThreshold")
        )
    }

    @Test
    fun scalarResponseDecodesLittleEndianStatusPrefixedU16() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())

        val decoded = UsbTuneReadCodec.decodeResponse(
            profile,
            "engineSnifferRpmThreshold",
            byteArrayOf(0x00, 0xB8.toByte(), 0x0B)
        )

        assertArrayEquals(byteArrayOf(0xB8.toByte(), 0x0B), decoded.rawBytes)
        assertEquals(3000.0, decoded.rawNumeric, 0.0)
        assertEquals(3000.0, decoded.value, 0.0)
        assertEquals("B8 0B", decoded.rawHex())
    }

    @Test
    fun requestIsDeterministicAcrossRepeatedConstruction() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())
        val first = UsbTuneReadCodec.buildPayload(profile, "engineSnifferRpmThreshold")
        val second = UsbTuneReadCodec.buildPayload(profile, "engineSnifferRpmThreshold")

        assertArrayEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedCommandShapeFailsClosed() {
        val profile = UsbTunerStudioProfileParser.parse(
            exactProfile().replace("R%2i%2o%2c", "C%2i%2o%2c%v")
        )

        UsbTuneReadCodec.buildPayload(profile, "engineSnifferRpmThreshold")
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfBoundsDecodedValueFailsClosed() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())

        UsbTuneReadCodec.decodeResponse(
            profile,
            "engineSnifferRpmThreshold",
            byteArrayOf(0x40, 0x9C.toByte()) // 40000 RPM
        )
    }

    @Test
    fun burnCommandSurvivesProfileJsonRoundTrip() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())
        val restored = UsbTunerStudioProfile.fromJson(profile.toJson())

        assertEquals(listOf("B%2i", "B%2i"), restored.tunePages.map { it.burnCommand })
        assertEquals(profile.tuneProfileFingerprint(), restored.tuneProfileFingerprint())
    }

    @Test
    fun legacyPersistedProfileWithoutTuneMetadataStillLoads() {
        val profile = UsbTunerStudioProfileParser.parse(exactProfile())
        val legacyJson = profile.toJson()
            .remove("tunePages")
            .let { profile.toJson().apply { remove("tunePages"); remove("tuneScalars") } }

        val restored = UsbTunerStudioProfile.fromJson(legacyJson)

        assertTrue(restored.tunePages.isEmpty())
        assertTrue(restored.tuneScalars.isEmpty())
        assertEquals(profile.channels.size, restored.channels.size)
    }

    private fun exactProfile(): String = """
        [MegaTune]
        signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

        [TunerStudio]
        queryCommand = "S"
        signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

        [Constants]
        messageEnvelopeFormat = msEnvelope_1.0
        endianness = little
        nPages = 2
        pageIdentifier = "\\x00\\x00", "\\x00\\x01"
        pageSize = 55764, 5000
        pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
        burnCommand = "B%2i", "B%2i"
        page = 2
        luaScript = string, ASCII, 0, 5000
        page = 1
        engineType = bits, U16, 0, [0:6], 0="DEFAULT"
        startButtonSuppressOnStartUpMs = scalar, U16, 2, "", 1, 0, 0, 32000, 0
        launchRpm = scalar, U16, 4, "rpm", 1, 0, 0, 20000, 0
        minRpmLockLaunch = scalar, U16, 6, "rpm", 1, 0, 0, 20000, 0
        launchRpmLockAdder = scalar, S08, 8, "rpm", 20.0, 0, -2500, 2500, 0
        rpmHardLimit = scalar, U16, 10, "rpm", 1, 0, 0, 20000, 0
        engineSnifferRpmThreshold = scalar, U16, 12, "RPM", 1, 0, 0, 30000, 0

        [OutputChannels]
        ochGetCommand = "O%2o%2c"
        ochBlockSize = 64
        RPMValue = scalar, U16, 4, "RPM", 1, 0
    """.trimIndent()
}
