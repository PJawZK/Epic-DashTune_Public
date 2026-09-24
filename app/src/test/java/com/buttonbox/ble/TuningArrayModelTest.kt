package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningArrayModelTest {
    @Test
    fun parserReadsOneAndTwoDimensionalArraysIncludingExpressionUnits() {
        val profile = UsbTunerStudioProfileParser.parse(profileText())

        assertEquals(3, profile.tuneArrays.size)
        assertEquals(1, profile.tuneTables.size)
        assertEquals(1, profile.tuneCurves.size)
        val curve = profile.tuneArrays.first { it.name == "curveValues" }
        val table = profile.tuneArrays.first { it.name == "tableValues" }
        val bins = profile.tuneArrays.first { it.name == "expressionBins" }

        assertEquals(listOf(4), curve.dimensions)
        assertEquals(4, curve.elementCount)
        assertEquals(4, curve.totalByteSize)

        assertEquals(listOf(2, 2), table.dimensions)
        assertEquals(4, table.elementCount)
        assertEquals(8, table.totalByteSize)
        assertEquals("S16", table.dataType)

        assertEquals("{bitStringValue(fuelUnits, fuelAlgorithm) }", bins.unit)
        assertEquals(4, bins.totalByteSize)

        val tableEditor = profile.tuneTables.single()
        assertEquals("tableValuesEditor", tableEditor.id)
        assertEquals("Test Table", tableEditor.title)
        assertEquals("expressionBins", tableEditor.xBins)
        assertEquals("expressionBins", tableEditor.yBins)
        assertEquals("tableValues", tableEditor.zBins)

        val curveEditor = profile.tuneCurves.single()
        assertEquals("curveValuesEditor", curveEditor.id)
        assertEquals("Test Curve", curveEditor.title)
        assertEquals("curveValues", curveEditor.xBins)
        assertEquals(listOf("curveValues"), curveEditor.yBins)
    }

    @Test
    fun profileJsonRoundTripPreservesArraySchema() {
        val profile = UsbTunerStudioProfileParser.parse(profileText(), "array-test.ini")
        val restored = UsbTunerStudioProfile.fromJson(profile.toJson())

        assertEquals(profile.tuneArrays, restored.tuneArrays)
        assertEquals(profile.tuneTables, restored.tuneTables)
        assertEquals(profile.tuneCurves, restored.tuneCurves)
        assertEquals("array-test.ini", restored.importedName)
    }

    @Test
    fun tuneProfileFingerprintIncludesArraySchema() {
        val original = UsbTunerStudioProfileParser.parse(profileText())
        val moved = UsbTunerStudioProfileParser.parse(profileText().replace(
            "tableValues = array, S16, 20",
            "tableValues = array, S16, 22"
        ))

        assertNotEquals(original.tuneProfileFingerprint(), moved.tuneProfileFingerprint())

        val remappedTable = UsbTunerStudioProfileParser.parse(
            profileText().replace("zBins = tableValues", "zBins = curveValues")
        )
        assertNotEquals(original.tuneProfileFingerprint(), remappedTable.tuneProfileFingerprint())
    }

    @Test
    fun workspaceDecodesSemanticArraySummaryAndDetailWithoutRawAddressMetadata() {
        val profile = UsbTunerStudioProfileParser.parse(profileText())
        val page = ByteArray(128)

        // curveValues U08 @ 16, scale .5 -> 5, 10, 15, 20 engineering.
        page[16] = 10
        page[17] = 20
        page[18] = 30
        page[19] = 40

        // tableValues S16 @ 20, scale .1 -> 10, -5, 20, 0 engineering.
        putS16(page, 20, 100)
        putS16(page, 22, -50)
        putS16(page, 24, 200)
        putS16(page, 26, 0)

        // expressionBins U16 @ 28.
        putU16(page, 28, 100)
        putU16(page, 30, 200)

        val snapshot = snapshot(profile, page)
        val workspace = TuningWorkspaceBuilder.build(profile, snapshot, 7L)
        val summary = workspace.arrays.first { it.name == "tableValues" }

        assertEquals(listOf(2, 2), summary.dimensions)
        assertEquals(4, summary.elementCount)
        assertEquals(-5.0, summary.minimum, 0.0001)
        assertEquals(20.0, summary.maximum, 0.0001)

        val detail = TuningWorkspaceBuilder.arrayDetail(profile, snapshot, 7L, "tableValues")
        assertEquals(listOf(10.0, -5.0, 20.0, 0.0), detail.values)

        assertEquals(1, workspace.tables.size)
        assertEquals(1, workspace.curves.size)
        val logicalTable = workspace.tables.single()
        assertEquals("tableValuesEditor", logicalTable.id)
        assertEquals("tableValues", logicalTable.zBins)
        assertEquals(2, logicalTable.xCount)
        assertEquals(2, logicalTable.yCount)
        val logicalCurve = workspace.curves.single()
        assertEquals("curveValuesEditor", logicalCurve.id)
        assertEquals(4, logicalCurve.pointCount)

        val json = workspace.toJson().toString()
        assertTrue(json.contains("\"readableArrays\":3"))
        assertTrue(json.contains("\"tableValues\""))
        assertFalse(json.contains("\"offset\""))
        assertFalse(json.contains("\"pageNumber\""))
        assertFalse(json.contains("rawHex"))

        val detailJson = detail.toJson().toString()
        assertTrue(detailJson.contains("\"values\""))
        assertFalse(detailJson.contains("\"offset\""))
        assertFalse(detailJson.contains("\"dataType\""))
    }

    @Test
    fun conditionalDuplicatesCollapseOnlyWhenStorageSemanticsMatch() {
        val base = UsbTunerStudioProfileParser.parse(profileText())
        val page = base.tunePages.single()
        val sameA = UsbTuneArray("same", 1, "U16", 40, listOf(2, 2), "%", 0.01, 0.0, 0.0, 650.0, 1)
        val sameB = sameA.copy(digits = 2)
        val ambiguousA = UsbTuneArray("ambiguous", 1, "U08", 48, listOf(2), "afr", 0.1, 0.0, 0.0, 25.0, 1)
        val ambiguousB = ambiguousA.copy(unit = "lambda", scale = 0.01, low = 0.6, high = 1.5)
        val profile = base.copy(
            tunePages = listOf(page),
            tuneArrays = listOf(sameA, sameB, ambiguousA, ambiguousB)
        )
        val bytes = ByteArray(128)
        putU16(bytes, 40, 1000)
        putU16(bytes, 42, 2000)
        val snapshot = snapshot(profile, bytes)

        val workspace = TuningWorkspaceBuilder.build(profile, snapshot, 7L)

        assertEquals(1, workspace.ambiguousArrayNames)
        assertEquals(3, workspace.skippedArrays)
        assertEquals(listOf("same"), workspace.arrays.map { it.name })
        assertEquals(2, workspace.arrays.single().digits)
    }

    @Test
    fun logicalTableRequiresTunerStudioColumnsXRowsOrientation() {
        val base = UsbTunerStudioProfileParser.parse(profileText())
        val x = UsbTuneArray("xBinsRect", 1, "U16", 40, listOf(3), "rpm", 1.0, 0.0, 0.0, 10000.0, 0)
        val y = UsbTuneArray("yBinsRect", 1, "U16", 46, listOf(2), "kPa", 1.0, 0.0, 0.0, 400.0, 0)
        val zGood = UsbTuneArray("zRect", 1, "S16", 50, listOf(3, 2), "deg", 0.1, 0.0, -90.0, 90.0, 1)
        val editor = UsbTuneTableEditor("rect", "rectMap", "Rectangular", 1, "xBinsRect", "yBinsRect", "zRect")
        val bytes = ByteArray(128)

        listOf(1000, 2000, 3000).forEachIndexed { index, value -> putU16(bytes, 40 + index * 2, value) }
        listOf(50, 100).forEachIndexed { index, value -> putU16(bytes, 46 + index * 2, value) }
        repeat(6) { index -> putS16(bytes, 50 + index * 2, index * 10) }

        val good = base.copy(tuneArrays = listOf(x, y, zGood), tuneTables = listOf(editor), tuneCurves = emptyList())
        val goodWorkspace = TuningWorkspaceBuilder.build(good, snapshot(good, bytes), 7L)
        assertEquals(1, goodWorkspace.tables.size)
        assertEquals(3, goodWorkspace.tables.single().xCount)
        assertEquals(2, goodWorkspace.tables.single().yCount)

        val reversed = good.copy(tuneArrays = listOf(x, y, zGood.copy(dimensions = listOf(2, 3))))
        val reversedWorkspace = TuningWorkspaceBuilder.build(reversed, snapshot(reversed, bytes), 7L)
        assertEquals(0, reversedWorkspace.tables.size)
        assertEquals(1, reversedWorkspace.skippedTables)
    }

    @Test
    fun multiSeriesCurveIsPreservedButExcludedFromFirstEditorSlice() {
        val base = UsbTunerStudioProfileParser.parse(profileText())
        val secondSeries = UsbTuneArray(
            "curveValues2", 1, "U08", 32, listOf(4), "%", 0.5, 0.0, 0.0, 100.0, 1
        )
        val multi = UsbTuneCurveEditor(
            id = "multiCurve",
            title = "Multi Curve",
            xBins = "curveValues",
            yBins = listOf("curveValues", "curveValues2")
        )
        val profile = base.copy(
            tuneArrays = base.tuneArrays + secondSeries,
            tuneTables = emptyList(),
            tuneCurves = listOf(multi)
        )
        val bytes = ByteArray(128)
        val workspace = TuningWorkspaceBuilder.build(profile, snapshot(profile, bytes), 7L)

        assertEquals(listOf("curveValues", "curveValues2"), profile.tuneCurves.single().yBins)
        assertEquals(0, workspace.curves.size)
        assertEquals(1, workspace.skippedCurves)

        val restored = UsbTunerStudioProfile.fromJson(profile.toJson())
        assertEquals(profile.tuneCurves, restored.tuneCurves)
    }

    @Test
    fun outOfPageArrayFailsClosedByExclusion() {
        val base = UsbTunerStudioProfileParser.parse(profileText())
        val bad = UsbTuneArray("bad", 1, "U16", 126, listOf(2, 2), "", 1.0, 0.0, 0.0, 1000.0, 0)
        val profile = base.copy(tuneArrays = listOf(bad))
        val workspace = TuningWorkspaceBuilder.build(profile, snapshot(profile, ByteArray(128)), 7L)

        assertEquals(0, workspace.arrays.size)
        assertEquals(1, workspace.skippedArrays)
    }

    @Test
    fun arrayCellSemanticPreviewUsesProductionResolverAndHidesStorageMetadata() {
        val profile = UsbTunerStudioProfileParser.parse(profileText())
        val bytes = ByteArray(128)
        putS16(bytes, 20, 100) // tableValues cell 0 = 10.0 deg
        val snapshot = snapshot(profile, bytes)

        val result = TuningSemanticPreviewBuilder.preview(
            profile = profile,
            snapshot = snapshot,
            currentGeneration = 7L,
            request = SemanticTuningWriteRequest(
                kind = TuningWriteKind.ARRAY_CELL,
                name = "tableValues",
                cellIndex = 0,
                requestedValue = 10.06
            )
        )

        assertEquals(10.0, result.currentValue, 0.0001)
        assertEquals(10.06, result.requestedValue, 0.0001)
        assertEquals(10.1, result.effectiveValue, 0.0001)
        assertTrue(result.changedBytes > 0)
        assertFalse(result.noOp)

        val json = result.toJson().toString()
        assertTrue(json.contains("\"capability\":\"SEMANTIC_PREVIEW\""))
        assertTrue(json.contains("\"writeEligible\":true"))
        assertFalse(json.contains("\"offset\""))
        assertFalse(json.contains("\"pageNumber\""))
        assertFalse(json.contains("rawHex"))
        assertFalse(json.contains("proposedRaw"))
    }

    @Test
    fun arrayCellSemanticPreviewRejectsBoundsAndIndexButReportsNoOp() {
        val profile = UsbTunerStudioProfileParser.parse(profileText())
        val bytes = ByteArray(128)
        putS16(bytes, 20, 100) // 10.0
        val snapshot = snapshot(profile, bytes)

        val outOfBounds = runCatching {
            TuningSemanticPreviewBuilder.preview(
                profile, snapshot, 7L,
                SemanticTuningWriteRequest(TuningWriteKind.ARRAY_CELL, "tableValues", 900.0, 0)
            )
        }.exceptionOrNull()
        assertTrue(outOfBounds is IllegalArgumentException)

        val badIndex = runCatching {
            TuningSemanticPreviewBuilder.preview(
                profile, snapshot, 7L,
                SemanticTuningWriteRequest(TuningWriteKind.ARRAY_CELL, "tableValues", 11.0, 99)
            )
        }.exceptionOrNull()
        assertTrue(badIndex is IllegalArgumentException)

        val noOp = TuningSemanticPreviewBuilder.preview(
            profile, snapshot, 7L,
            SemanticTuningWriteRequest(TuningWriteKind.ARRAY_CELL, "tableValues", 10.0, 0)
        )
        assertTrue(noOp.noOp)
        assertEquals(0, noOp.changedBytes)
        assertFalse(noOp.toJson().getBoolean("writeEligible"))
    }

    private fun snapshot(profile: UsbTunerStudioProfile, bytes: ByteArray): TuneSnapshot =
        TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(1, 0, bytes.size, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, bytes)),
            generation = 7L,
            capturedAtEpochMs = 1234L
        )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putS16(bytes: ByteArray, offset: Int, value: Int) =
        putU16(bytes, offset, value and 0xffff)

    private fun profileText(): String = """
        [TunerStudio]
        queryCommand = "S"
        signature = "rusEFI t5-array-test"

        [Constants]
        messageEnvelopeFormat = msEnvelope_1.0
        endianness = little
        pageIdentifier = "\\x00\\x00"
        pageSize = 128
        pageReadCommand = "R%2i%2o%2c"
        page = 1
        pilot = scalar, U16, 2, "RPM", 1, 0, 0, 30000, 0
        curveValues = array, U08, 16, [4], "%", 0.5, 0, 0, 100, 1
        tableValues = array, S16, 20, [2x2], "deg", 0.1, 0, -90, 90, 1
        expressionBins = array, U16, 28, [2], {bitStringValue(fuelUnits, fuelAlgorithm) }, 1, 0, 0, 650, 0

        [OutputChannels]
        ochGetCommand = "O%2o%2c"
        ochBlockSize = 64
        RPMValue = scalar, U16, 4, "RPM", 1, 0

        [TableEditor]
        table = tableValuesEditor, tableValuesMap, "Test Table", 1
        xBins = expressionBins, RPMValue
        yBins = expressionBins, fuelingLoad
        zBins = tableValues

        [CurveEditor]
        curve = curveValuesEditor, "Test Curve"
        xBins = curveValues, RPMValue
        yBins = curveValues
    """.trimIndent()
}
