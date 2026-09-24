package com.buttonbox.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T3BenchSessionAuthorizationTest {
    @Test
    fun exactActionAndValidVoltageAuthorizeCurrentGenerationOnly() {
        val auth = T3BenchSessionAuthorization(maximumAgeMs = 1_000L)
        assertTrue(
            auth.confirm(
                actionId = T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID,
                currentGeneration = 7L,
                observedEcuVoltage = 12.2,
                nowElapsedMs = 1_000L
            )
        )
        assertTrue(auth.isConfirmed(7L, 1_999L))
        assertFalse(auth.isConfirmed(8L, 1_100L))
        assertFalse(auth.isConfirmed(7L, 2_001L))
    }

    @Test
    fun wrongActionAndInvalidInputsFailClosedWithoutNumericVoltageCutoff() {
        val auth = T3BenchSessionAuthorization()
        assertFalse(auth.confirm("wrong", 7L, 12.2, 1_000L))
        assertFalse(auth.confirm(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 0L, 12.2, 1_000L))
        assertFalse(auth.confirm(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 7L, Double.NaN, 1_000L))
        assertFalse(auth.confirm(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 7L, 0.0, 1_000L))
        assertFalse(auth.confirm(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 7L, 12.2, -1L))
        assertFalse(auth.isConfirmed(7L, 1_000L))
    }

    @Test
    fun confirmationIsNotPersistentAndClearRevokesImmediately() {
        val auth = T3BenchSessionAuthorization()
        assertTrue(auth.confirm(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 3L, 12.4, 10L))
        auth.clear()
        assertFalse(auth.isConfirmed(3L, 11L))
        val snapshot = auth.snapshot(3L, 11L)
        assertFalse(snapshot.authorized)
        assertNull(snapshot.authorizedGeneration)
        assertNull(snapshot.confirmedAtElapsedMs)
        assertNull(snapshot.expiresAtElapsedMs)
    }

    @Test
    fun futureClockAndExpiredConfirmationFailClosed() {
        val auth = T3BenchSessionAuthorization(maximumAgeMs = 100L)
        assertTrue(auth.confirm(T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID, 9L, 11.8, 1_000L))
        assertFalse(auth.isConfirmed(9L, 999L))
        assertTrue(auth.isConfirmed(9L, 1_100L))
        assertFalse(auth.isConfirmed(9L, 1_101L))
    }
}
