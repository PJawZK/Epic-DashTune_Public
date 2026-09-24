package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TuningScalarCodecVectorTest {
    @Test
    fun exactDecodeVectorsCoverEverySupportedScalarPrimitive() {
        assertEquals(200.0, scalar("U08", 0.0, 255.0).decode(byteArrayOf(0xC8.toByte()))!!, 0.0)
        assertEquals(-2.0, scalar("S08", -128.0, 127.0).decode(byteArrayOf(0xFE.toByte()))!!, 0.0)
        assertEquals(4660.0, scalar("U16", 0.0, 65535.0).decode(byteArrayOf(0x34, 0x12))!!, 0.0)
        assertEquals(-2.0, scalar("S16", -32768.0, 32767.0).decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))!!, 0.0)
        assertEquals(
            305419896.0,
            scalar("U32", 0.0, 4294967295.0).decode(byteArrayOf(0x78, 0x56, 0x34, 0x12))!!,
            0.0
        )
        assertEquals(
            -2.0,
            scalar("S32", Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).decode(
                byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
            )!!,
            0.0
        )
        assertEquals(
            1.5,
            scalar("F32", -100.0, 100.0).decode(byteArrayOf(0x00, 0x00, 0xC0.toByte(), 0x3F))!!,
            0.0
        )
    }

    @Test
    fun scaleAndTranslationApplySymmetricallyToEncodeAndDecode() {
        val definition = scalar("U16", 10.0, 100.0, scale = 0.5, translate = 10.0)
        val encoded = TuningScalarCodec.encode(definition, 37.5)
        assertArrayEquals(byteArrayOf(55, 0), encoded)
        assertEquals(37.5, definition.decode(encoded)!!, 0.0)
    }

    @Test
    fun u16BoundarySweepRoundTripsDeterministically() {
        val definition = scalar("U16", 0.0, 65535.0)
        for (value in listOf(0, 1, 2, 127, 128, 255, 256, 1023, 32767, 32768, 65534, 65535)) {
            val encodedA = TuningScalarCodec.encode(definition, value.toDouble())
            val encodedB = TuningScalarCodec.encode(definition, value.toDouble())
            assertArrayEquals(encodedA, encodedB)
            assertEquals(value.toDouble(), definition.decode(encodedA)!!, 0.0)
        }
    }

    @Test
    fun productionPilotDefinitionEncodesAcceptedPhysicalBaselineExactly() {
        val profile = UsbTunerStudioProfileParser.parse(
            """
            [TunerStudio]
            queryCommand = "S"
            signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"
            [Constants]
            messageEnvelopeFormat = msEnvelope_1.0
            endianness = little
            pageIdentifier = "\\x00\\x00", "\\x00\\x01"
            pageSize = 55764, 5000
            pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
            page = 1
            engineSnifferRpmThreshold = scalar, U16, 12, "RPM", 1, 0, 0, 30000, 0
            [OutputChannels]
            ochGetCommand = "O%2o%2c"
            ochBlockSize = 64
            RPMValue = scalar, U16, 4, "RPM", 1, 0
            """.trimIndent()
        )
        val definition = profile.tuneScalars.single { it.name == "engineSnifferRpmThreshold" }
        assertEquals(1, definition.pageNumber)
        assertEquals(12, definition.offset)
        assertEquals("U16", definition.dataType)
        assertArrayEquals(byteArrayOf(0xC4.toByte(), 0x09), TuningScalarCodec.encode(definition, 2500.0))
        assertEquals(2500.0, definition.decode(byteArrayOf(0xC4.toByte(), 0x09))!!, 0.0)
    }

    private fun scalar(
        type: String,
        low: Double,
        high: Double,
        scale: Double = 1.0,
        translate: Double = 0.0
    ) = UsbTuneScalar(
        name = "vector",
        pageNumber = 1,
        dataType = type,
        offset = 0,
        unit = "",
        scale = scale,
        translate = translate,
        low = low,
        high = high,
        digits = 3
    )
}
