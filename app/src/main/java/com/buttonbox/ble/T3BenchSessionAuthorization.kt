package com.buttonbox.ble

/**
 * W3 bench power policy.
 *
 * No numeric Mega144H7 write-voltage limit is asserted here because the accepted firmware/profile
 * evidence does not define one for this RAM-only transaction. Native VBatt still has to be a fresh,
 * finite, positive ECU sample, and the operator must explicitly confirm stable support power.
 */
internal object T3BenchPowerPolicy {
    const val AUTHORIZATION_MAX_AGE_MS = 120_000L
    const val ENTER_BENCH_RAM_ONLY_ACTION_ID = "t3-enter-bench-ram-only-v1"
}

/**
 * Ephemeral W3 BENCH_RAM_ONLY authorization.
 *
 * It is deliberately not persisted. A process restart, reconnect/new USB generation, expiry, or
 * explicit clear removes authorization. The caller supplies no page, offset, width, raw bytes or
 * tune identity. This object only records that the operator explicitly confirmed stable bench
 * support power for one current native USB generation after a valid native VBatt sample.
 */
internal class T3BenchSessionAuthorization(
    private val maximumAgeMs: Long = T3BenchPowerPolicy.AUTHORIZATION_MAX_AGE_MS
) {
    init {
        require(maximumAgeMs > 0L) { "T3 bench authorization age must be positive" }
    }

    private var generation: Long = -1L
    private var confirmedAtElapsedMs: Long = -1L

    @Synchronized
    fun confirm(
        actionId: String,
        currentGeneration: Long,
        observedEcuVoltage: Double,
        nowElapsedMs: Long
    ): Boolean {
        clear()
        if (actionId != T3BenchPowerPolicy.ENTER_BENCH_RAM_ONLY_ACTION_ID) return false
        if (currentGeneration <= 0L || nowElapsedMs < 0L) return false
        if (!observedEcuVoltage.isFinite() || observedEcuVoltage <= 0.0) return false
        generation = currentGeneration
        confirmedAtElapsedMs = nowElapsedMs
        return true
    }

    @Synchronized
    fun isConfirmed(currentGeneration: Long, nowElapsedMs: Long): Boolean {
        if (generation <= 0L || confirmedAtElapsedMs < 0L) return false
        if (currentGeneration != generation) return false
        if (nowElapsedMs < confirmedAtElapsedMs) return false
        return nowElapsedMs - confirmedAtElapsedMs <= maximumAgeMs
    }

    @Synchronized
    fun clear() {
        generation = -1L
        confirmedAtElapsedMs = -1L
    }

    @Synchronized
    fun snapshot(currentGeneration: Long, nowElapsedMs: Long): T3BenchAuthorizationSnapshot =
        T3BenchAuthorizationSnapshot(
            authorized = isConfirmed(currentGeneration, nowElapsedMs),
            authorizedGeneration = generation.takeIf { it > 0L },
            confirmedAtElapsedMs = confirmedAtElapsedMs.takeIf { it >= 0L },
            expiresAtElapsedMs = confirmedAtElapsedMs.takeIf { it >= 0L }?.plus(maximumAgeMs)
        )
}

internal data class T3BenchAuthorizationSnapshot(
    val authorized: Boolean,
    val authorizedGeneration: Long?,
    val confirmedAtElapsedMs: Long?,
    val expiresAtElapsedMs: Long?
)
