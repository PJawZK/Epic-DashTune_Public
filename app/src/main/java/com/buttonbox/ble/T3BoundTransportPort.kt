package com.buttonbox.ble

/**
 * Production-facing adapter boundary for the future UsbEcuManager seam.
 *
 * It deliberately owns no executor and no USB object. The existing UsbEcuManager must construct
 * and invoke it only from its already-established single USB executor, supplying closures over the
 * existing strict framed exchange, complete TuneSnapshot reader and latest native output sample.
 * The adapter then prevents those closures from becoming an arbitrary command surface.
 */
internal class T3BoundTransportPort(
    private val plan: TuningScalarChangePlan,
    private val profileProvider: () -> UsbTunerStudioProfile,
    private val currentGeneration: () -> Long,
    private val isNativeOwnerThread: () -> Boolean,
    private val exchangeBody: (ByteArray, Int, String) -> ByteArray,
    private val snapshotReader: (Long) -> TuneSnapshot,
    private val nativeOutputSampleReader: () -> T3NativeOutputSample,
    private val safetyResolver: T3NativeSafetyInputsResolver,
    private val nowElapsedMs: () -> Long,
    private val supportPowerConfirmed: () -> Boolean
) : T3RamTransportPort {
    private val candidateBody = T3PilotRamProtocol.candidateWriteBody(plan)
    private val restoreBody = T3PilotRamProtocol.restoreWriteBody(plan)
    private val readBackBody = T3PilotRamProtocol.readBackBody(plan)

    init {
        RamScalarAuthority.requireAgainstProfile(plan, profileProvider())
    }

    override fun exchange(body: ByteArray, maxResponseBody: Int, label: String): T3RamTransportResponse {
        requireOwnerAndGeneration()
        requireAllowedExchange(body, maxResponseBody, label)
        val response = exchangeBody(body.copyOf(), maxResponseBody, label).copyOf()
        // The transaction owner receives the post-exchange generation. If detach/reconnect advanced
        // ownership while an exchange was in flight it will classify the response as stale/recovery.
        return T3RamTransportResponse(response, currentGeneration())
    }

    override fun readCompleteTuneSnapshot(expectedGeneration: Long): TuneSnapshot {
        requireOwnerAndGeneration()
        require(expectedGeneration == plan.context.generation) { "T3 snapshot request generation is not the approved generation" }
        val snapshot = snapshotReader(expectedGeneration)
        require(snapshot.generation == expectedGeneration) { "T3 snapshot reader returned another USB generation" }
        return snapshot
    }

    override fun readSafetyInputs(context: TuningContext): T3RamSafetyInputs {
        requireOwnerAndGeneration()
        require(context.sessionId == plan.context.sessionId) { "T3 safety context session changed" }
        require(context.generation == plan.context.generation) { "T3 safety context generation changed" }
        require(context.source == TuningDataSource.LIVE) { "T3 safety context is not LIVE" }
        require(context.ecuSignature == plan.context.ecuSignature) { "T3 safety context ECU signature changed" }
        require(context.profileFingerprint.equals(plan.context.profileFingerprint, ignoreCase = true)) {
            "T3 safety context profile fingerprint changed"
        }
        return safetyResolver.resolve(
            profile = profileProvider(),
            context = context,
            sample = nativeOutputSampleReader(),
            nowElapsedMs = nowElapsedMs(),
            supportPowerConfirmed = supportPowerConfirmed()
        )
    }

    private fun requireOwnerAndGeneration() {
        require(isNativeOwnerThread()) { "T3 transport may run only on the native USB owner thread" }
        require(currentGeneration() == plan.context.generation) { "T3 USB generation is no longer current" }
        RamScalarAuthority.requireAgainstProfile(plan, profileProvider())
    }

    private fun requireAllowedExchange(body: ByteArray, maxResponseBody: Int, label: String) {
        when {
            body.contentEquals(candidateBody) -> {
                require(label == LABEL_CANDIDATE) { "T3 candidate transport label mismatch" }
                require(maxResponseBody == 1) { "T3 candidate ACK response bound mismatch" }
            }
            body.contentEquals(restoreBody) -> {
                require(label == LABEL_RESTORE) { "T3 restore transport label mismatch" }
                require(maxResponseBody == 1) { "T3 restore ACK response bound mismatch" }
            }
            body.contentEquals(readBackBody) -> {
                require(label == LABEL_CANDIDATE_READBACK || label == LABEL_RESTORE_READBACK) {
                    "T3 read-back transport label mismatch"
                }
                require(maxResponseBody == plan.target.byteSize + 1) { "T3 read-back response bound mismatch" }
            }
            else -> throw IllegalArgumentException("T3 transport rejected a body outside the exact pilot transaction")
        }
    }

    companion object {
        const val LABEL_CANDIDATE = "T3 C candidate"
        const val LABEL_CANDIDATE_READBACK = "T3 R candidate read-back"
        const val LABEL_RESTORE = "T3 C restore"
        const val LABEL_RESTORE_READBACK = "T3 R restore read-back"
    }
}
