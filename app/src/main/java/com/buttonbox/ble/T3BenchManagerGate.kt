package com.buttonbox.ble

/**
 * Small manager-facing W3 policy owner. It contains no USB implementation and no persistence.
 * UsbEcuManager supplies only its current native generation and already-decoded native voltage.
 */
internal class T3BenchManagerGate(
    private val authorization: T3BenchSessionAuthorization = T3BenchSessionAuthorization()
) {
    /** No numeric voltage floor is asserted for W3 without hardware/firmware evidence. */
    val minimumVoltage: Double?
        get() = null

    /** No nominal support voltage is imposed by the application. */
    val nominalSupportVoltage: Double?
        get() = null

    fun enter(
        actionId: String,
        generation: Long,
        observedEcuVoltage: Double,
        nowElapsedMs: Long
    ): T3BenchAuthorizationSnapshot {
        authorization.confirm(actionId, generation, observedEcuVoltage, nowElapsedMs)
        return authorization.snapshot(generation, nowElapsedMs)
    }

    fun isAuthorized(generation: Long, nowElapsedMs: Long): Boolean =
        authorization.isConfirmed(generation, nowElapsedMs)

    fun snapshot(generation: Long, nowElapsedMs: Long): T3BenchAuthorizationSnapshot =
        authorization.snapshot(generation, nowElapsedMs)

    fun requireAuthorized(generation: Long, nowElapsedMs: Long) {
        require(isAuthorized(generation, nowElapsedMs)) {
            "T3 BENCH_RAM_ONLY is not authorized for the current USB generation"
        }
    }

    /**
     * Consume the one-shot permission exactly when the native proof starts. Expiry controls whether
     * a new proof may begin; it must not become a timer capable of blocking the mandatory restore
     * halfway through an already-started paired RAM transaction. Fresh voltage, engine, flash and
     * generation checks still run before candidate and restore.
     */
    fun consumeForAttempt(generation: Long, nowElapsedMs: Long): Boolean {
        val accepted = isAuthorized(generation, nowElapsedMs)
        authorization.clear()
        return accepted
    }

    fun clear() {
        authorization.clear()
    }

    fun writePolicy(): T3BenchWritePolicy = T3BenchWritePolicy(
        minimumVoltage = null,
        requireSupportPowerConfirmation = true
    )
}
