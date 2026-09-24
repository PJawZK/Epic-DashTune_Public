package com.buttonbox.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale

/** Capability is explicit; T2 never has a write-capable state. */
enum class TuningCapability {
    READ_ONLY,
    SIMULATION
}

enum class TuningDataSource {
    LIVE,
    DEMO,
    MSL,
    SELF_TEST
}

enum class TuningValueState {
    ECU_CURRENT,
    EDITED,
    PROPOSED,
    SIMULATED
}

enum class TuningTransmissionState {
    NOT_TRANSMITTED
}

/** Identity that binds a proposal to one exact live ECU/tune ownership generation. */
data class TuningContext(
    val sessionId: Long,
    val generation: Long,
    val source: TuningDataSource,
    val ecuSignature: String,
    val profileFingerprint: String,
    val tuneFingerprint: String
)

data class TuningValidation(
    val valid: Boolean,
    val errors: List<String>
) {
    companion object {
        fun valid(): TuningValidation = TuningValidation(true, emptyList())
        fun invalid(vararg errors: String): TuningValidation =
            TuningValidation(false, errors.filter { it.isNotBlank() })
    }
}

/** Exact profile-derived target identity. No caller supplies page/offset/raw-byte metadata. */
data class TuningScalarIdentity(
    val name: String,
    val pageNumber: Int,
    val pageIdentifier: Int,
    val pageSize: Int,
    val dataType: String,
    val offset: Int,
    val byteSize: Int,
    val unit: String,
    val scale: Double,
    val translate: Double,
    val low: Double,
    val high: Double,
    val digits: Int,
    val definitionFingerprint: String
)

data class TuningByteDiff(
    val offset: Int,
    val beforeByte: Int,
    val afterByte: Int
)

data class TuningScalarCurrent(
    val context: TuningContext,
    val target: TuningScalarIdentity,
    val value: Double,
    val rawHex: String,
    val state: TuningValueState = TuningValueState.ECU_CURRENT
)

data class TuningScalarEdit(
    val context: TuningContext,
    val target: TuningScalarIdentity,
    val originalValue: Double,
    val requestedValue: Double,
    val effectiveEncodedValue: Double?,
    val originalRawHex: String,
    val proposedRawHex: String?,
    val validation: TuningValidation,
    val state: TuningValueState = TuningValueState.EDITED
)

/**
 * Deterministic transport-neutral plan. T3 may later consume encodedBytes() only behind its own
 * capability/policy boundary; T2 itself exposes no USB write command or transport method.
 */
class TuningScalarChangePlan internal constructor(
    val context: TuningContext,
    val target: TuningScalarIdentity,
    val originalValue: Double,
    val requestedValue: Double,
    val effectiveEncodedValue: Double,
    beforeBytes: ByteArray,
    afterBytes: ByteArray,
    val byteDiffs: List<TuningByteDiff>,
    val affectedStartOffset: Int,
    val affectedEndOffset: Int,
    val previewId: String,
    val state: TuningValueState
) {
    private val before = beforeBytes.copyOf()
    private val after = afterBytes.copyOf()

    val transmissionState: TuningTransmissionState = TuningTransmissionState.NOT_TRANSMITTED
    val transmitted: Boolean = false
    val burnRequested: Boolean = false

    fun originalBytes(): ByteArray = before.copyOf()
    fun encodedBytes(): ByteArray = after.copyOf()
    val originalRawHex: String get() = before.toHex()
    val proposedRawHex: String get() = after.toHex()
}

/**
 * T2 tuning-domain implementation. It can resolve, edit, preview and simulate one allowlisted
 * profile scalar, but there is deliberately no method capable of ECU mutation.
 */
class SimulationTuningCore(
    private val profile: UsbTunerStudioProfile,
    private val snapshot: TuneSnapshot,
    val context: TuningContext,
    allowlistedScalarNames: Set<String>,
    val capability: TuningCapability = TuningCapability.SIMULATION
) {
    private val allowlist = allowlistedScalarNames.toSet()

    init {
        require(capability == TuningCapability.SIMULATION || capability == TuningCapability.READ_ONLY) {
            "Unsupported Tuning Core capability $capability"
        }
        require(allowlist.isNotEmpty()) { "Tuning scalar allowlist must not be empty" }
        require(context.sessionId > 0L) { "Live tuning context requires a positive session ID" }
        require(context.generation >= 0L) { "Live tuning context requires a valid generation" }
        require(context.source == TuningDataSource.LIVE) { "Tuning simulation requires LIVE source ownership" }
        require(profile.signature == snapshot.ecuSignature) { "Profile/ECU signature mismatch" }
        require(profile.signature == context.ecuSignature) { "Tuning context ECU signature is stale or mismatched" }
        val exactProfileFingerprint = profile.tuneProfileFingerprint()
        require(snapshot.profileFingerprint.equals(exactProfileFingerprint, ignoreCase = true)) {
            "TuneSnapshot profile fingerprint does not match exact profile"
        }
        require(context.profileFingerprint.equals(exactProfileFingerprint, ignoreCase = true)) {
            "Tuning context profile fingerprint is stale or mismatched"
        }
        require(context.tuneFingerprint.equals(snapshot.fingerprint, ignoreCase = true)) {
            "Tuning context tune fingerprint is stale or mismatched"
        }
        require(context.generation == snapshot.generation) {
            "Tuning context generation ${context.generation} does not match TuneSnapshot generation ${snapshot.generation}"
        }
    }

    fun currentScalar(name: String): TuningScalarCurrent {
        val resolved = resolve(name)
        val raw = scalarBytes(resolved.definition, resolved.snapshotPage)
        val decoded = resolved.definition.decode(raw)
            ?: throw IllegalArgumentException("Unable to decode scalar '$name' from TuneSnapshot")
        return TuningScalarCurrent(
            context = context,
            target = resolved.identity,
            value = decoded,
            rawHex = raw.toHex()
        )
    }

    fun editScalar(name: String, requestedValue: Double): TuningScalarEdit {
        require(capability == TuningCapability.SIMULATION) {
            "Tuning Core is READ_ONLY; simulation edits are unavailable"
        }
        val resolved = resolve(name)
        val before = scalarBytes(resolved.definition, resolved.snapshotPage)
        val original = resolved.definition.decode(before)
            ?: throw IllegalArgumentException("Unable to decode scalar '$name' from TuneSnapshot")

        val errors = mutableListOf<String>()
        if (!requestedValue.isFinite()) errors += "Proposed value must be finite"
        if (requestedValue.isFinite() && !resolved.definition.accepts(requestedValue)) {
            errors += "Proposed value $requestedValue is outside profile bounds ${resolved.definition.low}..${resolved.definition.high}"
        }

        val encoded = if (errors.isEmpty()) {
            runCatching { TuningScalarCodec.encode(resolved.definition, requestedValue) }
                .onFailure { errors += (it.message ?: "Scalar encoding failed") }
                .getOrNull()
        } else null
        val effective = encoded?.let { resolved.definition.decode(it) }
        if (encoded != null && (effective == null || !effective.isFinite())) {
            errors += "Encoded scalar does not decode to a finite value"
        }
        if (effective != null && !resolved.definition.accepts(effective)) {
            errors += "Encoded scalar value $effective falls outside profile bounds"
        }

        return TuningScalarEdit(
            context = context,
            target = resolved.identity,
            originalValue = original,
            requestedValue = requestedValue,
            effectiveEncodedValue = effective,
            originalRawHex = before.toHex(),
            proposedRawHex = encoded?.toHex(),
            validation = if (errors.isEmpty()) TuningValidation.valid() else TuningValidation(false, errors.toList())
        )
    }

    fun propose(edit: TuningScalarEdit): TuningScalarChangePlan {
        require(capability == TuningCapability.SIMULATION) {
            "Tuning Core is READ_ONLY; proposals are unavailable"
        }
        require(edit.state == TuningValueState.EDITED) { "Only EDITED state can become a proposal" }
        require(contextMatches(edit.context)) { "Edit context is stale" }
        val resolved = resolve(edit.target.name)
        require(resolved.identity == edit.target) { "Scalar definition changed after edit" }
        require(edit.validation.valid) {
            "Invalid edit cannot become a proposal: ${edit.validation.errors.joinToString("; ")}"
        }

        val before = scalarBytes(resolved.definition, resolved.snapshotPage)
        require(before.toHex() == edit.originalRawHex) { "TuneSnapshot scalar bytes changed after edit" }
        val after = TuningScalarCodec.encode(resolved.definition, edit.requestedValue)
        val effective = resolved.definition.decode(after)
            ?: throw IllegalArgumentException("Unable to decode proposed scalar '${edit.target.name}'")
        require(!before.contentEquals(after)) { "Proposed value produces no raw byte change" }

        val diffs = before.indices.mapNotNull { index ->
            val oldByte = before[index].toInt() and 0xff
            val newByte = after[index].toInt() and 0xff
            if (oldByte == newByte) null
            else TuningByteDiff(resolved.definition.offset + index, oldByte, newByte)
        }
        require(diffs.isNotEmpty()) { "Proposal contains no changed bytes" }

        val previewId = computePreviewId(
            context = context,
            target = resolved.identity,
            originalValue = edit.originalValue,
            requestedValue = edit.requestedValue,
            effectiveEncodedValue = effective,
            before = before,
            after = after
        )
        return TuningScalarChangePlan(
            context = context,
            target = resolved.identity,
            originalValue = edit.originalValue,
            requestedValue = edit.requestedValue,
            effectiveEncodedValue = effective,
            beforeBytes = before,
            afterBytes = after,
            byteDiffs = diffs,
            affectedStartOffset = resolved.definition.offset,
            affectedEndOffset = resolved.definition.offset + resolved.definition.byteSize - 1,
            previewId = previewId,
            state = TuningValueState.PROPOSED
        )
    }

    fun simulate(proposal: TuningScalarChangePlan, currentContext: TuningContext = context): TuningScalarChangePlan {
        require(capability == TuningCapability.SIMULATION) { "Tuning Core is READ_ONLY; simulation is unavailable" }
        require(proposal.state == TuningValueState.PROPOSED) { "Only PROPOSED state can be simulated" }
        require(contextMatches(proposal.context) && contextMatches(currentContext)) { "Proposal context is stale" }
        val resolved = resolve(proposal.target.name)
        require(resolved.identity == proposal.target) { "Scalar definition changed after proposal" }
        val currentBytes = scalarBytes(resolved.definition, resolved.snapshotPage)
        require(currentBytes.contentEquals(proposal.originalBytes())) { "TuneSnapshot bytes no longer match proposal baseline" }

        val expectedPreviewId = computePreviewId(
            context = context,
            target = proposal.target,
            originalValue = proposal.originalValue,
            requestedValue = proposal.requestedValue,
            effectiveEncodedValue = proposal.effectiveEncodedValue,
            before = proposal.originalBytes(),
            after = proposal.encodedBytes()
        )
        require(expectedPreviewId == proposal.previewId) { "Preview ID does not match proposal content" }

        return TuningScalarChangePlan(
            context = proposal.context,
            target = proposal.target,
            originalValue = proposal.originalValue,
            requestedValue = proposal.requestedValue,
            effectiveEncodedValue = proposal.effectiveEncodedValue,
            beforeBytes = proposal.originalBytes(),
            afterBytes = proposal.encodedBytes(),
            byteDiffs = proposal.byteDiffs.toList(),
            affectedStartOffset = proposal.affectedStartOffset,
            affectedEndOffset = proposal.affectedEndOffset,
            previewId = proposal.previewId,
            state = TuningValueState.SIMULATED
        )
    }

    /** Rebuild ECU-current state from the immutable baseline; no bytes are transmitted. */
    fun revert(proposal: TuningScalarChangePlan): TuningScalarCurrent {
        require(contextMatches(proposal.context)) { "Proposal context is stale" }
        return currentScalar(proposal.target.name)
    }

    private fun contextMatches(candidate: TuningContext): Boolean =
        candidate.sessionId == context.sessionId &&
            candidate.generation == context.generation &&
            candidate.source == TuningDataSource.LIVE &&
            candidate.ecuSignature == context.ecuSignature &&
            candidate.profileFingerprint.equals(context.profileFingerprint, ignoreCase = true) &&
            candidate.tuneFingerprint.equals(context.tuneFingerprint, ignoreCase = true)

    private data class ResolvedScalar(
        val definition: UsbTuneScalar,
        val identity: TuningScalarIdentity,
        val snapshotPage: TunePageSnapshot
    )

    private fun resolve(name: String): ResolvedScalar {
        require(name in allowlist) { "Scalar '$name' is not allowlisted for tuning simulation" }
        val matches = profile.tuneScalars.filter { it.name == name }
        require(matches.size == 1) {
            when {
                matches.isEmpty() -> "Unknown scalar '$name' in exact profile"
                else -> "Ambiguous scalar '$name' appears ${matches.size} times in exact profile"
            }
        }
        val definition = matches.single()
        require(definition.byteSize > 0) { "Scalar '$name' has unsupported type ${definition.dataType}" }
        require(definition.offset >= 0) { "Scalar '$name' has invalid negative offset" }
        require(definition.scale.isFinite() && definition.scale != 0.0) { "Scalar '$name' has invalid scale ${definition.scale}" }
        require(definition.translate.isFinite()) { "Scalar '$name' has invalid translation ${definition.translate}" }
        require(!definition.low.isNaN() && !definition.high.isNaN() && definition.low <= definition.high) {
            "Scalar '$name' has invalid profile bounds"
        }

        val profilePages = profile.tunePages.filter { it.pageNumber == definition.pageNumber }
        require(profilePages.size == 1) { "Scalar '$name' page ${definition.pageNumber} is missing or ambiguous in profile" }
        val profilePage = profilePages.single()
        require(profilePage.readCommand == UsbTuneReadCodec.SUPPORTED_READ_COMMAND) {
            "Scalar '$name' page uses unsupported read command '${profilePage.readCommand}'"
        }
        require(definition.offset + definition.byteSize <= profilePage.size) {
            "Scalar '$name' range exceeds profile page ${profilePage.pageNumber}"
        }

        val snapshotPages = snapshot.pages.filter { it.pageNumber == definition.pageNumber }
        require(snapshotPages.size == 1) { "Scalar '$name' page ${definition.pageNumber} is missing or ambiguous in TuneSnapshot" }
        val snapshotPage = snapshotPages.single()
        require(snapshotPage.identifier == profilePage.identifier && snapshotPage.size == profilePage.size) {
            "Scalar '$name' TuneSnapshot page identity does not match exact profile"
        }

        val identity = TuningScalarIdentity(
            name = definition.name,
            pageNumber = definition.pageNumber,
            pageIdentifier = profilePage.identifier,
            pageSize = profilePage.size,
            dataType = definition.dataType.uppercase(Locale.US),
            offset = definition.offset,
            byteSize = definition.byteSize,
            unit = definition.unit,
            scale = definition.scale,
            translate = definition.translate,
            low = definition.low,
            high = definition.high,
            digits = definition.digits,
            definitionFingerprint = tuningScalarDefinitionFingerprint(profile.tuneProfileFingerprint(), definition, profilePage)
        )
        return ResolvedScalar(definition, identity, snapshotPage)
    }

    private fun scalarBytes(definition: UsbTuneScalar, page: TunePageSnapshot): ByteArray {
        val bytes = page.bytes()
        require(definition.offset + definition.byteSize <= bytes.size) {
            "Scalar '${definition.name}' range exceeds TuneSnapshot page"
        }
        return bytes.copyOfRange(definition.offset, definition.offset + definition.byteSize)
    }
}


internal fun tuningScalarDefinitionFingerprint(
    profileFingerprint: String,
    definition: UsbTuneScalar,
    page: UsbTunePage
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateUtf8Tuning("EpicDashTuningScalarDefinition/v1")
    digest.updateUtf8Tuning(profileFingerprint.lowercase(Locale.US))
    digest.updateUtf8Tuning(definition.name)
    digest.updateIntTuning(definition.pageNumber)
    digest.updateIntTuning(page.identifier)
    digest.updateIntTuning(page.size)
    digest.updateUtf8Tuning(definition.dataType.uppercase(Locale.US))
    digest.updateIntTuning(definition.offset)
    digest.updateIntTuning(definition.byteSize)
    digest.updateUtf8Tuning(definition.unit)
    digest.updateDoubleTuning(definition.scale)
    digest.updateDoubleTuning(definition.translate)
    digest.updateDoubleTuning(definition.low)
    digest.updateDoubleTuning(definition.high)
    digest.updateIntTuning(definition.digits)
    return digest.digest().toHex()
}

private fun computePreviewId(
    context: TuningContext,
    target: TuningScalarIdentity,
    originalValue: Double,
    requestedValue: Double,
    effectiveEncodedValue: Double,
    before: ByteArray,
    after: ByteArray
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateUtf8Tuning("EpicDashTuningPreview/v1")
    digest.updateLongTuning(context.sessionId)
    digest.updateLongTuning(context.generation)
    digest.updateUtf8Tuning(context.source.name)
    digest.updateUtf8Tuning(context.ecuSignature)
    digest.updateUtf8Tuning(context.profileFingerprint.lowercase(Locale.US))
    digest.updateUtf8Tuning(context.tuneFingerprint.lowercase(Locale.US))
    digest.updateUtf8Tuning(target.definitionFingerprint)
    digest.updateDoubleTuning(originalValue)
    digest.updateDoubleTuning(requestedValue)
    digest.updateDoubleTuning(effectiveEncodedValue)
    digest.updateIntTuning(before.size)
    digest.update(before)
    digest.updateIntTuning(after.size)
    digest.update(after)
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }

private fun MessageDigest.updateUtf8Tuning(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    updateIntTuning(bytes.size)
    update(bytes)
}

private fun MessageDigest.updateIntTuning(value: Int) {
    update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
}

private fun MessageDigest.updateLongTuning(value: Long) {
    update(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array())
}

private fun MessageDigest.updateDoubleTuning(value: Double) {
    updateLongTuning(java.lang.Double.doubleToLongBits(value))
}
