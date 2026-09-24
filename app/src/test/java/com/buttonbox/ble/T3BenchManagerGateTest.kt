package com.buttonbox.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class T3BenchManagerGateTest {
    @Test
    fun gateExposesW3PolicyWithoutInventedNumericVoltageThreshold() {
        val gate = T3BenchManagerGate()
        assertNull(gate.minimumVoltage)
        assertNull(gate.nominalSupportVoltage)
        assertNull(gate.writePolicy().minimumVoltage)
        assertTrue(gate.writePolicy().requireSupportPowerConfirmation)
        assertFalse(gate.isAuthorized(7L, 1_000L))
    }

    @Test
    fun enterBindsAuthorizationToGenerationAndValidNativeVoltage() {
        val gate = T3BenchManagerGate()
        val denied = gate.enter(
            T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID,
            7L,
            Double.NaN,
            1_000L
        )
        assertFalse(denied.authorized)

        val accepted = gate.enter(
            T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID,
            7L,
            12.1,
            1_010L
        )
        assertTrue(accepted.authorized)
        assertTrue(gate.isAuthorized(7L, 1_011L))
        assertFalse(gate.isAuthorized(8L, 1_011L))
    }

    @Test
    fun consumeForAttemptIsOneShotAndClearsPermission() {
        val gate = T3BenchManagerGate()
        gate.enter(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 4L, 12.3, 100L)
        assertTrue(gate.consumeForAttempt(4L, 101L))
        assertFalse(gate.isAuthorized(4L, 102L))
        assertFalse(gate.consumeForAttempt(4L, 103L))
    }

    @Test
    fun expiredOrWrongGenerationAuthorizationCannotStartAttempt() {
        val gate = T3BenchManagerGate(T3BenchSessionAuthorization(maximumAgeMs = 100L))
        gate.enter(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 9L, 12.2, 1_000L)
        assertFalse(gate.consumeForAttempt(8L, 1_010L))

        gate.enter(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 9L, 12.2, 2_000L)
        assertFalse(gate.consumeForAttempt(9L, 2_101L))
    }

    @Test
    fun requireAndClearFailClosed() {
        val gate = T3BenchManagerGate()
        expectFailure { gate.requireAuthorized(1L, 1L) }
        gate.enter(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 1L, 12.3, 10L)
        gate.requireAuthorized(1L, 11L)
        gate.clear()
        expectFailure { gate.requireAuthorized(1L, 12L) }
    }

    private fun expectFailure(action: () -> Unit) {
        try {
            action()
            fail("Expected fail-closed exception")
        } catch (_: IllegalArgumentException) {
        }
    }
}
