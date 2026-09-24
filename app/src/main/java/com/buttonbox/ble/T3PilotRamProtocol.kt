package com.buttonbox.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Capability authority for the first T3 RAM-only proof.
 *
 * Only the logical pilot scalar is fixed in application policy. Its page, identifier, size, offset,
 * primitive, width, unit, scaling, bounds and definition fingerprint are resolved from the current
 * imported mainController.ini. The resolved plan is then immutable for one transaction and must
 * continue to match that exact imported profile until the transaction completes.
 */
internal object RamScalarAuthority {
    private val FP = Regex("[0-9a-fA-F]{64}")
    val SUPPORTED_TYPES: Set<String> = setOf("U08", "S08", "U16", "S16", "U32", "S32", "F32")

    /**
     * Structural authority shared by guarded RAM-only scalar writers.
     *
     * The target name is not hard-coded here. All storage metadata must already have been resolved
     * from the exact imported profile and frozen into an immutable proposal.
     */
    fun requirePlan(plan: TuningScalarChangePlan) {
        require(plan.state == TuningValueState.PROPOSED) { "RAM scalar write requires a fresh PROPOSED plan" }
        require(!plan.transmitted && !plan.burnRequested) { "RAM scalar proposal must be untransmitted and RAM-only" }
        require(plan.transmissionState == TuningTransmissionState.NOT_TRANSMITTED) {
            "RAM scalar proposal already has transmission state"
        }
        require(plan.context.source == TuningDataSource.LIVE) { "RAM scalar write requires LIVE source ownership" }
        require(plan.context.ecuSignature.isNotBlank()) { "Live ECU signature is blank" }
        require(plan.context.profileFingerprint.matches(FP)) { "Invalid profile fingerprint" }
        require(plan.context.tuneFingerprint.matches(FP)) { "Invalid baseline tune fingerprint" }
        require(plan.context.sessionId > 0L && plan.context.generation >= 0L) { "Invalid live tuning context" }

        val target = plan.target
        require(target.name.isNotBlank()) { "RAM scalar target name is blank" }
        require(target.pageNumber > 0) { "RAM scalar page number is invalid" }
        require(target.pageIdentifier in 0..0xffff) { "RAM scalar page identifier cannot be encoded by protocol" }
        require(target.pageSize > 0) { "RAM scalar page size is invalid" }
        require(target.dataType.uppercase() in SUPPORTED_TYPES && target.byteSize in setOf(1, 2, 4)) {
            "RAM scalar primitive is unsupported"
        }
        require(target.offset >= 0 && target.offset <= 0xffff) { "RAM scalar offset cannot be encoded by protocol" }
        require(target.offset + target.byteSize <= target.pageSize) { "RAM scalar range exceeds profile page" }
        require(target.scale.isFinite() && target.scale != 0.0) { "RAM scalar scale is invalid" }
        require(target.translate.isFinite()) { "RAM scalar translation is invalid" }
        require(!target.low.isNaN() && !target.high.isNaN() && target.low <= target.high) { "RAM scalar bounds are invalid" }
        require(target.definitionFingerprint.matches(FP)) { "Invalid RAM scalar definition fingerprint" }

        val original = plan.originalBytes()
        val encoded = plan.encodedBytes()
        require(original.size == target.byteSize && encoded.size == target.byteSize) { "RAM scalar byte width mismatch" }
        require(plan.affectedStartOffset == target.offset && plan.affectedEndOffset == target.offset + target.byteSize - 1) {
            "RAM scalar affected range does not match profile-derived target"
        }
        require(plan.byteDiffs.isNotEmpty() && plan.byteDiffs.all { it.offset in target.offset until target.offset + target.byteSize }) {
            "RAM scalar byte diff escapes profile-derived target range"
        }
        require(plan.previewId.matches(FP)) { "Invalid RAM scalar preview ID" }
    }

    fun requireAgainstProfile(plan: TuningScalarChangePlan, profile: UsbTunerStudioProfile) {
        requirePlan(plan)
        require(profile.signature == plan.context.ecuSignature) { "Imported profile signature changed after preview" }
        val profileFingerprint = profile.tuneProfileFingerprint()
        require(profileFingerprint.equals(plan.context.profileFingerprint, ignoreCase = true)) {
            "Imported profile fingerprint changed after preview"
        }

        val target = plan.target
        val definitions = profile.tuneScalars.filter { it.name == target.name }
        require(definitions.size == 1) {
            if (definitions.isEmpty()) "RAM scalar '${target.name}' is missing from imported profile"
            else "RAM scalar '${target.name}' is ambiguous in imported profile"
        }
        val definition = definitions.single()
        require(definition.byteSize > 0) { "RAM scalar type ${definition.dataType} is unsupported" }
        val pages = profile.tunePages.filter { it.pageNumber == definition.pageNumber }
        require(pages.size == 1) { "RAM scalar page ${definition.pageNumber} is missing or ambiguous" }
        val page = pages.single()
        require(page.readCommand == UsbTuneReadCodec.SUPPORTED_READ_COMMAND) { "RAM scalar page read command is unsupported" }

        val expectedFingerprint = tuningScalarDefinitionFingerprint(profileFingerprint, definition, page)
        require(target.pageNumber == definition.pageNumber && target.pageIdentifier == page.identifier) {
            "RAM scalar page no longer matches imported profile"
        }
        require(target.pageSize == page.size) { "RAM scalar page size no longer matches imported profile" }
        require(target.dataType.equals(definition.dataType, ignoreCase = true) && target.byteSize == definition.byteSize) {
            "RAM scalar primitive no longer matches imported profile"
        }
        require(target.offset == definition.offset) { "RAM scalar offset no longer matches imported profile" }
        require(target.unit == definition.unit && sameDouble(target.scale, definition.scale) && sameDouble(target.translate, definition.translate)) {
            "RAM scalar scaling no longer matches imported profile"
        }
        require(sameDouble(target.low, definition.low) && sameDouble(target.high, definition.high) && target.digits == definition.digits) {
            "RAM scalar bounds/precision no longer match imported profile"
        }
        require(target.definitionFingerprint.equals(expectedFingerprint, ignoreCase = true)) {
            "RAM scalar definition fingerprint no longer matches imported profile"
        }
    }

    fun eligibleScalarCount(profile: UsbTunerStudioProfile): Int =
        profile.tuneScalars.count { definition ->
            val page = profile.tunePages.singleOrNull { it.pageNumber == definition.pageNumber }
            profile.tuneScalars.count { it.name == definition.name } == 1 &&
                page != null &&
                page.identifier in 0..0xffff &&
                page.size > 0 &&
                page.readCommand == UsbTuneReadCodec.SUPPORTED_READ_COMMAND &&
                definition.byteSize in setOf(1, 2, 4) &&
                definition.dataType.uppercase() in SUPPORTED_TYPES &&
                definition.offset >= 0 &&
                definition.offset <= 0xffff &&
                definition.offset + definition.byteSize <= page.size &&
                definition.scale.isFinite() &&
                definition.scale != 0.0 &&
                definition.translate.isFinite() &&
                !definition.low.isNaN() &&
                !definition.high.isNaN() &&
                definition.low <= definition.high
        }

    private fun sameDouble(left: Double, right: Double): Boolean =
        java.lang.Double.doubleToLongBits(left) == java.lang.Double.doubleToLongBits(right)
}

/**
 * T3 remains intentionally narrow even though the lower RAM scalar machinery is generic.
 * This wrapper is the accepted W3 policy gate for one named pilot only.
 */
internal object T3PilotAuthority {
    const val SCALAR_NAME = "engineSnifferRpmThreshold"

    fun requirePlan(plan: TuningScalarChangePlan) {
        RamScalarAuthority.requirePlan(plan)
        require(plan.target.name == SCALAR_NAME) { "T3 pilot target is not $SCALAR_NAME" }
    }

    fun requireAgainstProfile(plan: TuningScalarChangePlan, profile: UsbTunerStudioProfile) {
        requirePlan(plan)
        RamScalarAuthority.requireAgainstProfile(plan, profile)
    }
}

internal data class T3DecodedResponse(val responseCode: Int, val payload: ByteArray)

/** Pure protocol construction/parsing for the one profile-derived pilot. No I/O ownership exists here. */
internal object T3PilotRamProtocol {
    private val knownRejectionCodes = setOf(0x80, 0x81, 0x82, 0x83, 0x84, 0x8D)

    fun candidateWriteBody(plan: TuningScalarChangePlan): ByteArray {
        RamScalarAuthority.requirePlan(plan)
        return writeBody(plan, plan.encodedBytes())
    }

    fun restoreWriteBody(plan: TuningScalarChangePlan): ByteArray {
        RamScalarAuthority.requirePlan(plan)
        return writeBody(plan, plan.originalBytes())
    }

    fun readBackBody(plan: TuningScalarChangePlan): ByteArray {
        RamScalarAuthority.requirePlan(plan)
        return rangeBody('R'.code.toByte(), plan.target)
    }

    private fun writeBody(plan: TuningScalarChangePlan, value: ByteArray): ByteArray {
        require(value.size == plan.target.byteSize) { "T3 pilot value width differs from profile-derived target" }
        return rangeBody('C'.code.toByte(), plan.target) + value
    }

    private fun rangeBody(command: Byte, target: TuningScalarIdentity): ByteArray = byteArrayOf(command) +
        u16Le(target.pageIdentifier) + u16Le(target.offset) + u16Le(target.byteSize)

    private fun u16Le(value: Int): ByteArray {
        require(value in 0..0xffff) { "T3 protocol field is outside U16 range" }
        return byteArrayOf((value and 0xff).toByte(), ((value ushr 8) and 0xff).toByte())
    }

    /** msEnvelope_1.0 request framing: BE body length, body, BE CRC32(body). */
    fun envelope(body: ByteArray): ByteArray {
        require(body.isNotEmpty() && body.size <= 0xffff) { "Invalid T3 envelope body size" }
        val crc = CRC32().apply { update(body) }.value
        return ByteBuffer.allocate(body.size + 6)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(body.size.toShort())
            .put(body)
            .putInt(crc.toInt())
            .array()
    }

    /** Strict framed response parser used by tests and the USB adapter. */
    fun decodeFramedResponse(frame: ByteArray): T3DecodedResponse {
        require(frame.size >= 7) { "Truncated T3 response frame" }
        val bodyLength = ((frame[0].toInt() and 0xff) shl 8) or (frame[1].toInt() and 0xff)
        require(bodyLength >= 1) { "T3 response body must contain a status byte" }
        require(frame.size == bodyLength + 6) { "T3 response frame length mismatch" }
        val body = frame.copyOfRange(2, 2 + bodyLength)
        val expectedCrc = ByteBuffer.wrap(frame, 2 + bodyLength, 4)
            .order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffff_ffffL
        val actualCrc = CRC32().apply { update(body) }.value
        require(expectedCrc == actualCrc) { "T3 response CRC mismatch" }
        return decodeResponseBody(body)
    }

    /** The existing UsbEcuManager envelope reader returns response body bytes, including status. */
    fun decodeResponseBody(body: ByteArray): T3DecodedResponse {
        require(body.isNotEmpty()) { "Empty T3 response body" }
        return T3DecodedResponse(body[0].toInt() and 0xff, body.copyOfRange(1, body.size))
    }

    fun requireWriteAck(body: ByteArray): Int {
        val decoded = decodeResponseBody(body)
        require(decoded.payload.isEmpty()) { "T3 write acknowledgement contains unexpected payload" }
        return decoded.responseCode
    }

    fun requireReadBack(body: ByteArray, plan: TuningScalarChangePlan): ByteArray {
        RamScalarAuthority.requirePlan(plan)
        val decoded = decodeResponseBody(body)
        require(decoded.responseCode == 0x00) { "T3 read-back response code 0x${decoded.responseCode.toString(16)}" }
        require(decoded.payload.size == plan.target.byteSize) { "T3 read-back payload width mismatch" }
        return decoded.payload.copyOf()
    }

    fun isKnownExplicitRejection(responseCode: Int): Boolean = responseCode in knownRejectionCodes
}

/** Whole-image verification prevents a correct target read-back from hiding collateral tune changes. */
internal object T3TuneSnapshotVerifier {
    fun candidateSnapshot(baseline: TuneSnapshot, plan: TuningScalarChangePlan): TuneSnapshot {
        RamScalarAuthority.requirePlan(plan)
        require(baseline.ecuSignature == plan.context.ecuSignature) { "T3 baseline ECU signature mismatch" }
        require(baseline.profileFingerprint.equals(plan.context.profileFingerprint, ignoreCase = true)) {
            "T3 baseline profile fingerprint mismatch"
        }
        require(baseline.fingerprint.equals(plan.context.tuneFingerprint, ignoreCase = true)) {
            "T3 baseline tune fingerprint mismatch"
        }
        require(baseline.generation == plan.context.generation) { "T3 baseline generation mismatch" }

        val target = plan.target
        var patched = false
        val pages = baseline.pages.map { page ->
            if (page.pageNumber == target.pageNumber) {
                require(page.identifier == target.pageIdentifier && page.size == target.pageSize) {
                    "T3 baseline pilot page identity differs from profile-derived target"
                }
                val bytes = page.bytes()
                require(target.offset + target.byteSize <= bytes.size) { "T3 baseline pilot range exceeds page" }
                val before = bytes.copyOfRange(target.offset, target.offset + target.byteSize)
                require(before.contentEquals(plan.originalBytes())) { "T3 baseline pilot bytes changed after proposal" }
                plan.encodedBytes().copyInto(bytes, target.offset)
                patched = true
                TunePageSnapshot(page.pageNumber, page.identifier, page.size, page.readCommand, bytes)
            } else {
                TunePageSnapshot(page.pageNumber, page.identifier, page.size, page.readCommand, page.bytes())
            }
        }
        require(patched) { "T3 baseline is missing profile-derived pilot page" }
        return TuneSnapshot.create(
            ecuSignature = baseline.ecuSignature,
            profileFingerprint = baseline.profileFingerprint,
            pages = pages,
            generation = baseline.generation,
            capturedAtEpochMs = baseline.capturedAtEpochMs
        )
    }

    fun matchesCandidate(observed: TuneSnapshot, baseline: TuneSnapshot, plan: TuningScalarChangePlan): Boolean {
        val candidate = candidateSnapshot(baseline, plan)
        return sameIdentity(observed, candidate) && observed.fingerprint.equals(candidate.fingerprint, ignoreCase = true)
    }

    fun matchesBaseline(observed: TuneSnapshot, baseline: TuneSnapshot): Boolean =
        sameIdentity(observed, baseline) && observed.fingerprint.equals(baseline.fingerprint, ignoreCase = true)

    private fun sameIdentity(left: TuneSnapshot, right: TuneSnapshot): Boolean =
        left.ecuSignature == right.ecuSignature &&
            left.profileFingerprint.equals(right.profileFingerprint, ignoreCase = true) &&
            left.pages.map { Triple(it.pageNumber, it.identifier, it.size) } ==
            right.pages.map { Triple(it.pageNumber, it.identifier, it.size) }
}
