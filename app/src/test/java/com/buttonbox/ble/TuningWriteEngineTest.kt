package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningWriteEngineTest {
    @Test
    fun semanticScalarAndArrayCellResolveToExactCurrentIniRangesAndExpectedSnapshot() {
        val fixture = fixture()
        val request = JSONObject()
            .put("generation", fixture.generation)
            .put("profileFingerprint", fixture.profile.tuneProfileFingerprint())
            .put("tuneFingerprint", fixture.snapshot.fingerprint)
            .put("changes", JSONArray()
                .put(JSONObject()
                    .put("kind", "scalar")
                    .put("name", "idleTarget")
                    .put("requestedValue", 200.0))
                .put(JSONObject()
                    .put("kind", "arrayCell")
                    .put("name", "veTable")
                    .put("cellIndex", 2)
                    .put("requestedValue", 35.0))
                .put(JSONObject()
                    .put("kind", "bitField")
                    .put("name", "useIdleTimingPidControl")
                    .put("requestedValue", 1.0)))

        val plan = TuningWritePlanner.plan(
            fixture.profile,
            fixture.snapshot,
            SemanticTuningWriteEnvelope.parse(request.toString()),
            fixture.generation
        )

        assertEquals(3, plan.operations.size)
        assertEquals(setOf(1), plan.dirtyPageNumbers)
        assertEquals(3, plan.changedBytes)

        val scalar = plan.operations.first { it.name == "idleTarget" }
        assertEquals(4, scalar.offset)
        assertArrayEquals(byteArrayOf(0xC8.toByte(), 0x00), scalar.afterBytes())
        assertArrayEquals(
            byteArrayOf(
                'C'.code.toByte(),
                0x34, 0x12,
                0x04, 0x00,
                0x02, 0x00,
                0xC8.toByte(), 0x00
            ),
            TuningWriteProtocol.writeBody(scalar)
        )
        assertArrayEquals(
            byteArrayOf(
                'R'.code.toByte(),
                0x34, 0x12,
                0x04, 0x00,
                0x02, 0x00
            ),
            TuningWriteProtocol.readBackBody(scalar)
        )

        val cell = plan.operations.first { it.name == "veTable" }
        assertEquals(2, cell.cellIndex)
        assertEquals(18, cell.offset)
        assertArrayEquals(byteArrayOf(35), cell.afterBytes())

        val bit = plan.operations.first { it.name == "useIdleTimingPidControl" }
        assertEquals(TuningWriteKind.BIT_FIELD, bit.kind)
        assertEquals(8, bit.offset)
        assertEquals(4, bit.byteSize)
        assertEquals(0.0, bit.currentValue, 0.0)
        assertEquals(1.0, bit.effectiveValue, 0.0)
        assertArrayEquals(
            byteArrayOf(0xA5.toByte(), 0x5A, 0xE3.toByte(), 0x3C),
            bit.afterBytes()
        )
        assertArrayEquals(
            byteArrayOf(
                'C'.code.toByte(),
                0x34, 0x12,
                0x08, 0x00,
                0x04, 0x00,
                0xA5.toByte(), 0x5A, 0xE3.toByte(), 0x3C
            ),
            TuningWriteProtocol.writeBody(bit)
        )

        val expected = plan.expectedSnapshot.pages.single().bytes()
        assertEquals(200, expected[4].toInt() and 0xff)
        assertEquals(0, expected[5].toInt() and 0xff)
        assertEquals(0xA5, expected[8].toInt() and 0xff)
        assertEquals(0x5A, expected[9].toInt() and 0xff)
        assertEquals(0xE3, expected[10].toInt() and 0xff)
        assertEquals(0x3C, expected[11].toInt() and 0xff)
        assertEquals(35, expected[18].toInt() and 0xff)
        assertEquals(40, expected[19].toInt() and 0xff)
        assertTrue(plan.expectedSnapshot.fingerprint != fixture.snapshot.fingerprint)
    }

    @Test
    fun staleTuneDuplicateTargetsAndOutOfRangeCellsFailBeforeTransport() {
        val fixture = fixture()

        fun root(changes: JSONArray, tuneFingerprint: String = fixture.snapshot.fingerprint) = JSONObject()
            .put("generation", fixture.generation)
            .put("profileFingerprint", fixture.profile.tuneProfileFingerprint())
            .put("tuneFingerprint", tuneFingerprint)
            .put("changes", changes)

        val oneScalar = JSONArray().put(JSONObject()
            .put("kind", "scalar")
            .put("name", "idleTarget")
            .put("requestedValue", 201.0))
        assertThrows(IllegalArgumentException::class.java) {
            TuningWritePlanner.plan(
                fixture.profile,
                fixture.snapshot,
                SemanticTuningWriteEnvelope.parse(root(oneScalar, "0".repeat(64)).toString()),
                fixture.generation
            )
        }

        val duplicate = JSONArray()
            .put(JSONObject().put("kind", "scalar").put("name", "idleTarget").put("requestedValue", 201.0))
            .put(JSONObject().put("kind", "scalar").put("name", "idleTarget").put("requestedValue", 202.0))
        assertThrows(IllegalArgumentException::class.java) {
            SemanticTuningWriteEnvelope.parse(root(duplicate).toString())
        }

        val badCell = JSONArray().put(JSONObject()
            .put("kind", "arrayCell")
            .put("name", "veTable")
            .put("cellIndex", 4)
            .put("requestedValue", 50.0))
        assertThrows(IllegalArgumentException::class.java) {
            TuningWritePlanner.plan(
                fixture.profile,
                fixture.snapshot,
                SemanticTuningWriteEnvelope.parse(root(badCell).toString()),
                fixture.generation
            )
        }

        val badBitOption = JSONArray().put(JSONObject()
            .put("kind", "bitField")
            .put("name", "useIdleTimingPidControl")
            .put("requestedValue", 2.0))
        assertThrows(IllegalArgumentException::class.java) {
            TuningWritePlanner.plan(
                fixture.profile,
                fixture.snapshot,
                SemanticTuningWriteEnvelope.parse(root(badBitOption).toString()),
                fixture.generation
            )
        }
    }

    @Test
    fun writeAckAndReadBackAreExactAndFailClosed() {
        val fixture = fixture()
        val request = JSONObject()
            .put("generation", fixture.generation)
            .put("profileFingerprint", fixture.profile.tuneProfileFingerprint())
            .put("tuneFingerprint", fixture.snapshot.fingerprint)
            .put("changes", JSONArray().put(JSONObject()
                .put("kind", "scalar")
                .put("name", "idleTarget")
                .put("requestedValue", 200.0)))
        val operation = TuningWritePlanner.plan(
            fixture.profile,
            fixture.snapshot,
            SemanticTuningWriteEnvelope.parse(request.toString()),
            fixture.generation
        ).operations.single()

        TuningWriteProtocol.requireWriteAck(byteArrayOf(0x00))
        TuningWriteProtocol.requireReadBack(
            byteArrayOf(0x00, 0xC8.toByte(), 0x00),
            operation
        )

        assertThrows(IllegalArgumentException::class.java) {
            TuningWriteProtocol.requireWriteAck(byteArrayOf(0x01))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TuningWriteProtocol.requireReadBack(byteArrayOf(0x00, 0xC7.toByte(), 0x00), operation)
        }
    }

    private data class Fixture(
        val profile: UsbTunerStudioProfile,
        val snapshot: TuneSnapshot,
        val generation: Long
    )

    private fun fixture(): Fixture {
        val generation = 17L
        val pageBytes = ByteArray(32)
        pageBytes[4] = 100
        pageBytes[5] = 0
        pageBytes[8] = 0xA5.toByte()
        pageBytes[9] = 0x5A
        pageBytes[10] = 0xC3.toByte()
        pageBytes[11] = 0x3C
        pageBytes[16] = 10
        pageBytes[17] = 20
        pageBytes[18] = 30
        pageBytes[19] = 40

        val profile = UsbTunerStudioProfile(
            signature = "rusEFI tuner-write-test",
            queryCommand = "S",
            outputCommand = "O%2o%2c",
            outputBlockSize = 64,
            envelopeFormat = "msEnvelope_1.0",
            endianness = "little",
            channels = emptyList(),
            tunePages = listOf(
                UsbTunePage(
                    pageNumber = 1,
                    identifier = 0x1234,
                    size = pageBytes.size,
                    readCommand = UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
                    burnCommand = T6BurnProtocol.SUPPORTED_BURN_COMMAND
                )
            ),
            tuneScalars = listOf(
                UsbTuneScalar(
                    name = "idleTarget",
                    pageNumber = 1,
                    dataType = "U16",
                    offset = 4,
                    unit = "rpm",
                    scale = 1.0,
                    translate = 0.0,
                    low = 0.0,
                    high = 1000.0,
                    digits = 0
                )
            ),
            tuneArrays = listOf(
                UsbTuneArray(
                    name = "veTable",
                    pageNumber = 1,
                    dataType = "U08",
                    offset = 16,
                    dimensions = listOf(2, 2),
                    unit = "%",
                    scale = 1.0,
                    translate = 0.0,
                    low = 0.0,
                    high = 255.0,
                    digits = 0
                )
            ),
            tuneBitFields = listOf(
                UsbTuneBitField(
                    name = "useIdleTimingPidControl",
                    pageNumber = 1,
                    dataType = "U32",
                    offset = 8,
                    bitStart = 21,
                    bitEnd = 21,
                    options = listOf(
                        UsbTuneBitOption(0, "false"),
                        UsbTuneBitOption(1, "true")
                    )
                )
            ),
            importedName = "mainController.ini"
        )
        val snapshot = TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(
                TunePageSnapshot(
                    pageNumber = 1,
                    identifier = 0x1234,
                    size = pageBytes.size,
                    readCommand = UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
                    bytes = pageBytes
                )
            ),
            generation = generation,
            capturedAtEpochMs = 1234L
        )
        return Fixture(profile, snapshot, generation)
    }
}
