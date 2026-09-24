package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IniConditionEvaluatorTest {
    private fun ini(
        guardCondition: String = "{ mode == 2 }",
        visibilityCondition: String? = null
    ): String {
        val visibility = visibilityCondition?.let { ", $it" }.orEmpty()
        return """
            [TunerStudio]
            queryCommand = "S"
            signature = "rusEFI condition test"

            [Constants]
            messageEnvelopeFormat = msEnvelope_1.0
            endianness = little
            pageIdentifier = "\\x00\\x00"
            pageSize = 64
            pageReadCommand = "R%2i%2o%2c"
            burnCommand = "B%2i"
            page = 1
            rpmTarget = scalar, U16, 2, "RPM", 1, 0, 0, 9000, 0
            mode = bits, U08, 4, [0:1], "Off", "On", "Auto", "Other"
            guarded = scalar, U16, 6, "RPM", 1, 0, 0, 9000, 0
            guardedArray = array, U08, 10, [4], "%", 1, 0, 0, 255, 0

            [OutputChannels]
            ochGetCommand = "O%2o%2c"
            ochBlockSize = 64
            RPMValue = scalar, U16, 0, "RPM", 1, 0

            [Menu]
            menuDialog = main
            menu = "&Setup"
                groupMenu = "Engine"
                    groupChildMenu = engineDialog, "Engine Settings", 0, { mode > 0 }

            [UserDefined]
            dialog = engineDialog, "Engine Settings", yAxis
                field = "RPM Target", rpmTarget
                field = "Guarded", guarded, ${guardCondition}${visibility}
                field = "Guarded Array", guardedArray, ${guardCondition}${visibility}
                field = "Mode", mode
        """.trimIndent()
    }

    private fun snapshot(profile: UsbTunerStudioProfile, mode: Int, rpmTarget: Int = 1000): TuneSnapshot {
        val bytes = ByteArray(64)
        bytes[2] = (rpmTarget and 0xff).toByte()
        bytes[3] = ((rpmTarget ushr 8) and 0xff).toByte()
        bytes[4] = mode.toByte()
        bytes[6] = 0x84.toByte()
        bytes[7] = 0x03
        bytes[10] = 10
        bytes[11] = 20
        bytes[12] = 30
        bytes[13] = 40
        return TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(
                TunePageSnapshot(
                    pageNumber = 1,
                    identifier = 0,
                    size = 64,
                    readCommand = UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
                    bytes = bytes
                )
            ),
            generation = 7L,
            capturedAtEpochMs = 1234L
        )
    }

    @Test
    fun expressionEvaluatorSupportsNumericBooleanAndBitwiseOperators() {
        val evaluator = IniConditionEvaluator(
            mapOf("mode" to 2.0, "rpmTarget" to 1200.0, "flags" to 5.0)
        )
        assertEquals(IniConditionTruth.TRUE, evaluator.evaluate("mode == 2 && rpmTarget >= 1000").truth)
        assertEquals(IniConditionTruth.TRUE, evaluator.evaluate("(flags & 1) == 1 || mode == 0").truth)
        assertEquals(IniConditionTruth.TRUE, evaluator.evaluate("!(mode == 0) && (2 + 3 * 4) == 14").truth)
        assertEquals(IniConditionTruth.FALSE, evaluator.evaluate("mode < 2 || rpmTarget < 500").truth)
        assertEquals(IniConditionTruth.TRUE, evaluator.evaluate("0x10 >> 2 == 4").truth)
    }

    @Test
    fun firstConditionControlsEnabledAndSecondControlsVisibility() {
        val evaluator = IniConditionEvaluator(mapOf("mode" to 1.0))
        val enabledFalse = IniUiConditionState.evaluate(listOf("mode == 2"), evaluator)
        assertTrue(enabledFalse.visible)
        assertFalse(enabledFalse.enabled)
        assertTrue(enabledFalse.supported)
        assertEquals("disabled", enabledFalse.status)

        val hidden = IniUiConditionState.evaluate(listOf("mode > 0", "mode == 2"), evaluator)
        assertFalse(hidden.visible)
        assertTrue(hidden.enabled)
        assertTrue(hidden.supported)
        assertEquals("hidden", hidden.status)
    }

    @Test
    fun unknownOrUnsupportedExpressionFailsClosed() {
        val evaluator = IniConditionEvaluator(mapOf("mode" to 2.0))
        val unknown = evaluator.evaluate("missingValue > 0")
        assertEquals(IniConditionTruth.UNSUPPORTED, unknown.truth)

        val function = evaluator.evaluate("clamp(mode, 0, 1)")
        assertEquals(IniConditionTruth.UNSUPPORTED, function.truth)

        val enabled = IniUiConditionState.evaluate(listOf("clamp(mode, 0, 1)"), evaluator)
        assertFalse(enabled.enabled)
        assertTrue(enabled.visible)
        assertFalse(enabled.supported)

        val visible = IniUiConditionState.evaluate(listOf("mode > 0", "clamp(mode, 0, 1)"), evaluator)
        assertFalse(visible.visible)
        assertFalse(visible.supported)
    }

    @Test
    fun currentTuneSnapshotControlsNativeWritePolicy() {
        val profile = UsbTunerStudioProfileParser.parse(ini())
        val allowedSnapshot = snapshot(profile, mode = 2)
        val blockedSnapshot = snapshot(profile, mode = 1)

        val allowed = IniConditionAuthority.build(profile, allowedSnapshot)
        assertTrue(allowed.writePolicy(TuningWriteKind.SCALAR, "guarded").writeAllowed)
        assertTrue(allowed.writePolicy(TuningWriteKind.ARRAY_CELL, "guardedArray").writeAllowed)

        val blocked = IniConditionAuthority.build(profile, blockedSnapshot)
        assertFalse(blocked.writePolicy(TuningWriteKind.SCALAR, "guarded").writeAllowed)
        assertFalse(blocked.writePolicy(TuningWriteKind.ARRAY_CELL, "guardedArray").writeAllowed)
        assertTrue(blocked.writePolicy(TuningWriteKind.SCALAR, "rpmTarget").writeAllowed)
    }

    @Test
    fun semanticPreviewUsesSameConditionAuthorityAsExecution() {
        val profile = UsbTunerStudioProfileParser.parse(ini())
        val blockedSnapshot = snapshot(profile, mode = 1)
        val request = SemanticTuningWriteRequest(
            kind = TuningWriteKind.SCALAR,
            name = "guarded",
            requestedValue = 950.0
        )

        try {
            TuningWritePlanner.resolveForPreview(profile, blockedSnapshot, request, 7L)
            fail("Condition-blocked semantic preview should fail closed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("disable") ||
                expected.message.orEmpty().contains("condition"))
        }

        val envelope = SemanticTuningWriteEnvelope(
            expectedGeneration = 7L,
            expectedProfileFingerprint = profile.tuneProfileFingerprint(),
            expectedTuneFingerprint = blockedSnapshot.fingerprint,
            changes = listOf(request)
        )
        try {
            TuningWritePlanner.plan(profile, blockedSnapshot, envelope, 7L)
            fail("Condition-blocked live write plan should fail closed")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("disable") ||
                expected.message.orEmpty().contains("condition"))
        }
    }

    @Test
    fun unsupportedConditionMarksCompatibilityAmberAndBlocksAffectedTarget() {
        val profile = UsbTunerStudioProfileParser.parse(
            ini(guardCondition = "{ clamp(mode, 0, 1) }")
        )
        val authority = IniConditionAuthority.build(profile, snapshot(profile, mode = 2))
        assertEquals("amber", authority.compatibility.state)
        assertTrue(authority.compatibility.unsupportedConditionExpressions > 0)
        assertFalse(authority.writePolicy(TuningWriteKind.SCALAR, "guarded").writeAllowed)
    }
}
