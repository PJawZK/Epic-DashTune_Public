package com.buttonbox.ble

internal object T3BenchPilotPolicy {
    const val PREFERRED_DELTA_RPM = 50.0

    fun preferredRequestedValue(currentValue: Double, low: Double, high: Double): Double {
        require(currentValue.isFinite()) { "T3 pilot current value must be finite" }
        require(!low.isNaN() && !high.isNaN() && low <= high) { "T3 pilot profile bounds are invalid" }
        val up = currentValue + PREFERRED_DELTA_RPM
        if (up <= high) return up
        val down = currentValue - PREFERRED_DELTA_RPM
        require(down >= low) { "T3 pilot has no reversible 50 RPM candidate inside current profile bounds" }
        return down
    }
}

internal data class T3BenchEntryResult(
    val authorized: Boolean,
    val generation: Long,
    val observedVoltage: Double,
    val minimumVoltage: Double?,
    val nominalSupportVoltage: Double?,
    val expiresAtElapsedMs: Long?
)

/**
 * Typed W3 interaction boundary. Authorization remains owned by the native manager/gate; this
 * class never creates a second authorization state. It deliberately has no page, offset, count,
 * width, raw bytes, USB endpoint or burn API.
 */
internal class T3BenchInteractionController(
    private val enterNativeBench: (String) -> T3BenchEntryResult,
    private val isNativeBenchAuthorized: () -> Boolean,
    private val clearNativeBench: () -> Unit,
    private val currentGeneration: () -> Long,
    private val readCurrentPilot: () -> TuningScalarCurrent,
    private val prepareNativeProposal: (Double) -> T3BenchProposalPreview,
    private val executeNativeProof: (String, String, (T3RamProofResult?, String) -> Unit) -> Unit
) {
    fun enterBenchRamOnly(actionId: String): T3BenchEntryResult = enterNativeBench(actionId)

    fun preparePreferredPilot(): T3BenchProposalPreview {
        requireCurrentAuthorization()
        val generation = currentGeneration()
        val current = readCurrentPilot()
        require(current.context.generation == generation) { "T3 current scalar belongs to another USB generation" }
        require(current.target.name == T3PilotAuthority.SCALAR_NAME) { "T3 current scalar is outside pilot capability" }
        val requested = T3BenchPilotPolicy.preferredRequestedValue(
            currentValue = current.value,
            low = current.target.low,
            high = current.target.high
        )
        val preview = prepareNativeProposal(requested)
        require(preview.generation == generation) { "T3 preview generation changed" }
        require(currentGeneration() == generation) { "T3 USB generation changed during preview creation" }
        requireCurrentAuthorization()
        return preview
    }

    fun executeApprovedPreview(
        actionId: String,
        previewId: String,
        onComplete: (T3RamProofResult?, String) -> Unit
    ) {
        require(actionId.isNotBlank()) { "T3 proof action ID must not be blank" }
        require(previewId.matches(Regex("[0-9a-fA-F]{64}"))) { "T3 preview ID is invalid" }
        requireCurrentAuthorization()
        try {
            executeNativeProof(actionId, previewId) { result, detail ->
                clearNativeBench()
                onComplete(result, detail)
            }
        } catch (error: Exception) {
            clearNativeBench()
            throw error
        }
    }

    fun clearBenchRamOnly() {
        clearNativeBench()
    }

    private fun requireCurrentAuthorization() {
        require(isNativeBenchAuthorized()) {
            "T3 BENCH_RAM_ONLY is not authorized for the current USB generation"
        }
    }
}
