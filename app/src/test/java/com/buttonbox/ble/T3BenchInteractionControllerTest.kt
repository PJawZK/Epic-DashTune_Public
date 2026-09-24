package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class T3BenchInteractionControllerTest {
    @Test
    fun preferredPilotUsesCurrentProfileBounds() {
        assertEquals(2550.0, T3BenchPilotPolicy.preferredRequestedValue(2500.0, 0.0, 30000.0), 0.0)
        assertEquals(29950.0, T3BenchPilotPolicy.preferredRequestedValue(30000.0, 0.0, 30000.0), 0.0)
        assertEquals(945.0, T3BenchPilotPolicy.preferredRequestedValue(995.0, 900.0, 1000.0), 0.0)
    }

    @Test
    fun benchEntryIsDelegatedToNativeManagerAuthority() {
        var authorized = false
        var seenAction = ""
        val controller = controller(
            authorized = { authorized },
            enter = { action ->
                seenAction = action
                authorized = action == T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID
                entry(authorized, 7L, 12.2)
            }
        )

        val wrong = controller.enterBenchRamOnly("wrong")
        assertFalse(wrong.authorized)
        val ok = controller.enterBenchRamOnly(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID)
        assertTrue(ok.authorized)
        assertEquals(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, seenAction)
        assertEquals(7L, ok.generation)
    }

    @Test
    fun preparePreferredPilotRequiresNativeAuthorizationAndStableGeneration() {
        var authorized = false
        var generation = 3L
        var preparedValue = Double.NaN
        val preview = preview(generation = 3L, requested = 2550.0)
        val controller = controller(
            authorized = { authorized },
            generation = { generation },
            current = { currentScalar(generation, 2500.0, 0.0, 30000.0) },
            enter = {
                authorized = true
                entry(true, generation, 12.3)
            },
            prepare = { requested -> preparedValue = requested; preview }
        )

        expectFailure { controller.preparePreferredPilot() }
        assertTrue(controller.enterBenchRamOnly(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID).authorized)
        val result = controller.preparePreferredPilot()
        assertEquals(2550.0, preparedValue, 0.0)
        assertEquals(preview, result)

        generation = 4L
        expectFailure { controller.preparePreferredPilot() }
    }

    @Test
    fun completionClearsNativeAuthorizationAndBlocksSecondExecution() {
        var authorized = true
        var executions = 0
        val preview = preview(5L, 2550.0)
        val controller = controller(
            authorized = { authorized },
            generation = { 5L },
            current = { currentScalar(5L, 2500.0, 0.0, 30000.0) },
            clear = { authorized = false },
            prepare = { preview },
            execute = { _, _, complete ->
                executions++
                complete(null, "safe-abort fixture")
            }
        )

        controller.executeApprovedPreview("w3-proof-1", preview.previewId) { _, _ -> }
        assertEquals(1, executions)
        assertFalse(authorized)
        expectFailure {
            controller.executeApprovedPreview("w3-proof-2", preview.previewId) { _, _ -> }
        }
    }

    @Test
    fun synchronousNativeExecutionFailureAlsoClearsNativeAuthorization() {
        var authorized = true
        val preview = preview(6L, 2550.0)
        val controller = controller(
            authorized = { authorized },
            generation = { 6L },
            current = { currentScalar(6L, 2500.0, 0.0, 30000.0) },
            clear = { authorized = false },
            prepare = { preview },
            execute = { _, _, _ -> throw IllegalStateException("fixture") }
        )
        expectFailure {
            controller.executeApprovedPreview("w3-proof", preview.previewId) { _, _ -> }
        }
        assertFalse(authorized)
    }

    private fun controller(
        authorized: () -> Boolean,
        generation: () -> Long = { 1L },
        current: () -> TuningScalarCurrent = { currentScalar(generation(), 2500.0, 0.0, 30000.0) },
        enter: (String) -> T3BenchEntryResult = { entry(authorized(), generation(), 12.2) },
        clear: () -> Unit = {},
        prepare: (Double) -> T3BenchProposalPreview = { preview(generation(), it) },
        execute: (String, String, (T3RamProofResult?, String) -> Unit) -> Unit = { _, _, complete -> complete(null, "fixture") }
    ): T3BenchInteractionController = T3BenchInteractionController(
        enterNativeBench = enter,
        isNativeBenchAuthorized = authorized,
        clearNativeBench = clear,
        currentGeneration = generation,
        readCurrentPilot = current,
        prepareNativeProposal = prepare,
        executeNativeProof = execute
    )

    private fun currentScalar(generation: Long, value: Double, low: Double, high: Double): TuningScalarCurrent {
        val context = TuningContext(
            sessionId = generation,
            generation = generation,
            source = TuningDataSource.LIVE,
            ecuSignature = "rusEFI test",
            profileFingerprint = "f".repeat(64),
            tuneFingerprint = "b".repeat(64)
        )
        val target = TuningScalarIdentity(
            name = T3PilotAuthority.SCALAR_NAME,
            pageNumber = 2,
            pageIdentifier = 0x1234,
            pageSize = 2048,
            dataType = "U16",
            offset = 400,
            byteSize = 2,
            unit = "RPM",
            scale = 1.0,
            translate = 0.0,
            low = low,
            high = high,
            digits = 0,
            definitionFingerprint = "d".repeat(64)
        )
        return TuningScalarCurrent(context, target, value, "c409")
    }

    private fun entry(authorized: Boolean, generation: Long, voltage: Double) = T3BenchEntryResult(
        authorized = authorized,
        generation = generation,
        observedVoltage = voltage,
        minimumVoltage = null,
        nominalSupportVoltage = null,
        expiresAtElapsedMs = if (authorized) 121_000L else null
    )

    private fun preview(generation: Long, requested: Double) = T3BenchProposalPreview(
        previewId = "a".repeat(64),
        generation = generation,
        baselineTuneFingerprint = "b".repeat(64),
        scalarName = T3PilotAuthority.SCALAR_NAME,
        unit = "RPM",
        originalValue = 2500.0,
        requestedValue = requested,
        effectiveEncodedValue = requested,
        originalRawHex = "c409",
        proposedRawHex = "f609"
    )

    private fun expectFailure(action: () -> Unit) {
        try {
            action()
            fail("Expected fail-closed exception")
        } catch (_: IllegalArgumentException) {
        } catch (_: IllegalStateException) {
        }
    }
}
