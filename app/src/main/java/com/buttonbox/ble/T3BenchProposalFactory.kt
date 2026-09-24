package com.buttonbox.ble

internal data class T3BenchProposalPreview(
    val previewId: String,
    val generation: Long,
    val baselineTuneFingerprint: String,
    val scalarName: String,
    val unit: String,
    val originalValue: Double,
    val requestedValue: Double,
    val effectiveEncodedValue: Double,
    val originalRawHex: String,
    val proposedRawHex: String
)

internal data class T3PreparedBenchProposal(
    val plan: TuningScalarChangePlan,
    val preview: T3BenchProposalPreview
)

/**
 * Creates the one T3 bench proposal from the current imported profile and complete TuneSnapshot.
 *
 * The logical scalar name is the only target capability fixed by application policy. Page number,
 * page identifier, page size, primitive, offset, width, unit, scale, translation, bounds and
 * definition fingerprint are resolved by SimulationTuningCore from the imported INI.
 */
internal object T3BenchProposalFactory {
    fun prepare(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        generation: Long,
        requestedValue: Double
    ): T3PreparedBenchProposal {
        require(generation > 0L) { "T3 bench proposal requires an active positive USB generation" }
        require(snapshot.generation == generation) { "T3 baseline TuneSnapshot belongs to another USB generation" }
        require(profile.signature.isNotBlank()) { "T3 imported profile signature is blank" }
        require(snapshot.ecuSignature == profile.signature) { "T3 baseline ECU signature differs from imported profile" }
        val fingerprint = profile.tuneProfileFingerprint()
        require(snapshot.profileFingerprint.equals(fingerprint, ignoreCase = true)) {
            "T3 baseline TuneSnapshot profile fingerprint differs from imported profile"
        }

        val context = TuningContext(
            sessionId = generation,
            generation = generation,
            source = TuningDataSource.LIVE,
            ecuSignature = snapshot.ecuSignature,
            profileFingerprint = fingerprint,
            tuneFingerprint = snapshot.fingerprint
        )
        val core = SimulationTuningCore(
            profile = profile,
            snapshot = snapshot,
            context = context,
            allowlistedScalarNames = setOf(T3PilotAuthority.SCALAR_NAME)
        )
        val edit = core.editScalar(T3PilotAuthority.SCALAR_NAME, requestedValue)
        require(edit.validation.valid) {
            "T3 bench proposal is invalid: ${edit.validation.errors.joinToString("; ")}"
        }
        val plan = core.propose(edit)
        T3PilotAuthority.requireAgainstProfile(plan, profile)

        return T3PreparedBenchProposal(
            plan = plan,
            preview = T3BenchProposalPreview(
                previewId = plan.previewId,
                generation = generation,
                baselineTuneFingerprint = snapshot.fingerprint,
                scalarName = plan.target.name,
                unit = plan.target.unit,
                originalValue = plan.originalValue,
                requestedValue = plan.requestedValue,
                effectiveEncodedValue = plan.effectiveEncodedValue,
                originalRawHex = plan.originalRawHex,
                proposedRawHex = plan.proposedRawHex
            )
        )
    }
}
