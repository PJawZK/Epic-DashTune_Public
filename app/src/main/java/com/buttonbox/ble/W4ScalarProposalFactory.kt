package com.buttonbox.ble

internal data class W4PreparedScalarProposal(
    val plan: TuningScalarChangePlan,
    val preview: W4VehicleRamPreview
)

/**
 * W4 semantic scalar proposal factory.
 *
 * The caller supplies only a semantic scalar name and engineering value. The exact current INI and
 * current-generation TuneSnapshot remain the sole storage/value authority.
 */
internal object W4ScalarProposalFactory {
    fun prepare(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        generation: Long,
        name: String,
        requestedValue: Double
    ): W4PreparedScalarProposal {
        require(name.isNotBlank()) { "W4 scalar name must not be blank" }
        require(requestedValue.isFinite()) { "W4 requested engineering value must be finite" }
        require(generation > 0L) { "W4 proposal requires an active positive USB generation" }
        require(snapshot.generation == generation) { "W4 baseline TuneSnapshot belongs to another USB generation" }
        require(profile.signature.isNotBlank()) { "W4 imported profile signature is blank" }
        require(snapshot.ecuSignature == profile.signature) { "W4 baseline ECU signature differs from imported profile" }

        val fingerprint = profile.tuneProfileFingerprint()
        require(snapshot.profileFingerprint.equals(fingerprint, ignoreCase = true)) {
            "W4 baseline TuneSnapshot profile fingerprint differs from imported profile"
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
            allowlistedScalarNames = setOf(name)
        )
        val edit = core.editScalar(name, requestedValue)
        require(edit.validation.valid) {
            "W4 scalar proposal is invalid: ${edit.validation.errors.joinToString("; ")}"
        }
        val plan = core.propose(edit)
        RamScalarAuthority.requireAgainstProfile(plan, profile)

        return W4PreparedScalarProposal(
            plan = plan,
            preview = W4VehicleRamPreview(
                previewId = plan.previewId,
                generation = generation,
                profileFingerprint = fingerprint,
                baselineTuneFingerprint = snapshot.fingerprint,
                scalarName = plan.target.name,
                unit = plan.target.unit,
                currentValue = plan.originalValue,
                requestedValue = plan.requestedValue,
                effectiveValue = plan.effectiveEncodedValue
            )
        )
    }
}
