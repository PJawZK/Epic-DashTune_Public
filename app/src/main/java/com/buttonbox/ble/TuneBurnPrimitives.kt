package com.buttonbox.ble

import java.util.Locale

/** Immutable output-channel sample owned by one current USB generation. */
internal class NativeOutputSample(
    block: ByteArray,
    val generation: Long,
    val capturedElapsedMs: Long
) {
    private val bytes = block.copyOf()

    init {
        require(generation >= 0L) { "Native output sample generation must be valid" }
        require(capturedElapsedMs >= 0L) { "Native output sample time must be valid" }
        require(bytes.isNotEmpty()) { "Native output sample must not be empty" }
    }

    fun bytes(): ByteArray = bytes.copyOf()
}

/**
 * Production page-Burn protocol boundary.
 *
 * The only accepted current profile shape is exactly B%2i and the page identifier always comes
 * from the currently imported matching INI.
 */
internal object TuneBurnProtocol {
    const val SUPPORTED_BURN_COMMAND = "B%2i"
    const val BURN_OK_RESPONSE = 0x04

    fun buildBody(page: UsbTunePage): ByteArray {
        require(page.burnCommand == SUPPORTED_BURN_COMMAND) {
            "Unsupported burn command '${page.burnCommand}' for page ${page.pageNumber}"
        }
        require(page.identifier in 0..0xffff) { "Burn page identifier is outside U16 range" }
        return byteArrayOf(
            'B'.code.toByte(),
            (page.identifier and 0xff).toByte(),
            ((page.identifier ushr 8) and 0xff).toByte()
        )
    }

    fun requireAcceptedAck(body: ByteArray) {
        require(body.size == 1 && (body[0].toInt() and 0xff) == BURN_OK_RESPONSE) {
            "Burn acknowledgement is not TS_RESPONSE_BURN_OK"
        }
    }
}

internal enum class TuneFlashStatusFailure {
    INVALID_CONTEXT,
    GENERATION_MISMATCH,
    PROFILE_SIGNATURE_MISMATCH,
    PROFILE_FINGERPRINT_MISMATCH,
    SAMPLE_TIME_INVALID,
    SAMPLE_STALE,
    MISSING_CHANNEL,
    AMBIGUOUS_CHANNEL,
    CHANNEL_DEFINITION_MISMATCH,
    CHANNEL_DECODE_FAILED,
    CHANNEL_VALUE_INVALID
}

internal class TuneFlashStatusException(
    val reason: TuneFlashStatusFailure,
    message: String
) : IllegalStateException(message)

internal data class TuneFlashStatus(
    val context: TuningContext,
    val needFlashBurn: Boolean,
    val flashWritePending: Boolean,
    val flashWrites: Int,
    val flashWriteErrors: Int,
    val burnRequestCnt: Int,
    val tuneWriteId: Long,
    val bootFlashWriteId: Long,
    val bootConfigPrimaryStatus: Int,
    val bootConfigBackupStatus: Int,
    val definitionFingerprint: String
)

internal object TuneFlashChannelPolicy {
    const val NEED_FLASH_BURN = "needFlashBurn"
    const val FLASH_PENDING = "flashWritePending"
    const val FLASH_WRITES = "flashWrites"
    const val FLASH_ERRORS = "flashWriteErrors"
    const val BURN_REQUEST_COUNT = "burnRequestCnt"
    const val TUNE_WRITE_ID = "tuneWriteId"
    const val BOOT_FLASH_WRITE_ID = "bootFlashWriteId"
    const val BOOT_PRIMARY_STATUS = "bootConfigPrimaryStatus"
    const val BOOT_BACKUP_STATUS = "bootConfigBackupStatus"
}

/**
 * Resolves authoritative Burn state from a fresh native output sample. Storage metadata always
 * comes from the current imported profile; no historical offsets/types/bit positions are copied.
 */
internal class TuneFlashStatusResolver(
    private val maximumSampleAgeMs: Long
) {
    init {
        require(maximumSampleAgeMs > 0L) { "Maximum flash-status sample age must be positive" }
    }

    fun resolve(
        profile: UsbTunerStudioProfile,
        context: TuningContext,
        sample: NativeOutputSample,
        nowElapsedMs: Long
    ): TuneFlashStatus {
        if (context.sessionId <= 0L || context.generation < 0L || context.source != TuningDataSource.LIVE) {
            fail(TuneFlashStatusFailure.INVALID_CONTEXT, "Flash status requires a positive LIVE session")
        }
        if (sample.generation != context.generation) {
            fail(TuneFlashStatusFailure.GENERATION_MISMATCH, "Native output sample belongs to another USB generation")
        }
        if (profile.signature != context.ecuSignature) {
            fail(TuneFlashStatusFailure.PROFILE_SIGNATURE_MISMATCH, "Imported profile signature differs from live ECU")
        }
        val profileFingerprint = profile.tuneProfileFingerprint()
        if (!context.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            fail(TuneFlashStatusFailure.PROFILE_FINGERPRINT_MISMATCH, "Imported profile fingerprint differs from live tuning context")
        }
        if (nowElapsedMs < sample.capturedElapsedMs) {
            fail(TuneFlashStatusFailure.SAMPLE_TIME_INVALID, "Native status sample timestamp is in the future")
        }
        if (nowElapsedMs - sample.capturedElapsedMs > maximumSampleAgeMs) {
            fail(TuneFlashStatusFailure.SAMPLE_STALE, "Native flash-status sample is stale")
        }

        val channels = linkedMapOf(
            "needFlashBurn" to exactChannel(profile, TuneFlashChannelPolicy.NEED_FLASH_BURN, allowBits = true),
            "flashPending" to exactChannel(profile, TuneFlashChannelPolicy.FLASH_PENDING, allowBits = true),
            "flashWrites" to exactChannel(profile, TuneFlashChannelPolicy.FLASH_WRITES),
            "flashErrors" to exactChannel(profile, TuneFlashChannelPolicy.FLASH_ERRORS),
            "burnRequests" to exactChannel(profile, TuneFlashChannelPolicy.BURN_REQUEST_COUNT),
            "tuneWriteId" to exactChannel(profile, TuneFlashChannelPolicy.TUNE_WRITE_ID),
            "bootFlashWriteId" to exactChannel(profile, TuneFlashChannelPolicy.BOOT_FLASH_WRITE_ID),
            "bootPrimaryStatus" to exactChannel(profile, TuneFlashChannelPolicy.BOOT_PRIMARY_STATUS),
            "bootBackupStatus" to exactChannel(profile, TuneFlashChannelPolicy.BOOT_BACKUP_STATUS)
        )

        val block = sample.bytes()
        if (block.size < profile.outputBlockSize) {
            fail(
                TuneFlashStatusFailure.CHANNEL_DECODE_FAILED,
                "Native flash-status sample is ${block.size} bytes but current INI requires ${profile.outputBlockSize}"
            )
        }

        return TuneFlashStatus(
            context = context,
            needFlashBurn = decodeBinary(channels.getValue("needFlashBurn"), block),
            flashWritePending = decodeBinary(channels.getValue("flashPending"), block),
            flashWrites = decodeUnsignedInteger(channels.getValue("flashWrites"), block, 0xffL).toInt(),
            flashWriteErrors = decodeUnsignedInteger(channels.getValue("flashErrors"), block, 0xffL).toInt(),
            burnRequestCnt = decodeUnsignedInteger(channels.getValue("burnRequests"), block, 0xffL).toInt(),
            tuneWriteId = decodeUnsignedInteger(channels.getValue("tuneWriteId"), block, 0xffff_ffffL),
            bootFlashWriteId = decodeUnsignedInteger(channels.getValue("bootFlashWriteId"), block, 0xffff_ffffL),
            bootConfigPrimaryStatus = decodeUnsignedInteger(channels.getValue("bootPrimaryStatus"), block, 0xffL).toInt(),
            bootConfigBackupStatus = decodeUnsignedInteger(channels.getValue("bootBackupStatus"), block, 0xffL).toInt(),
            definitionFingerprint = definitionFingerprint(profile, channels)
        )
    }

    private fun exactChannel(
        profile: UsbTunerStudioProfile,
        name: String,
        allowBits: Boolean = false
    ): UsbOutputChannel {
        val matches = profile.channels.filter { it.name.equals(name, ignoreCase = true) }
        if (matches.isEmpty()) {
            fail(TuneFlashStatusFailure.MISSING_CHANNEL, "Required flash-status channel '$name' is missing")
        }
        if (matches.size != 1) {
            fail(TuneFlashStatusFailure.AMBIGUOUS_CHANNEL, "Required flash-status channel '$name' is ambiguous")
        }
        val channel = matches.single()
        val scalar = channel.kind.equals("scalar", ignoreCase = true)
        val bits = allowBits && channel.kind.equals("bits", ignoreCase = true)
        val bitCapacity = channel.byteSize * 8
        val validBits = bits &&
            channel.bitStart >= 0 &&
            channel.bitEnd >= channel.bitStart &&
            channel.bitEnd < bitCapacity
        val validScalar = scalar &&
            channel.scale.isFinite() &&
            channel.scale != 0.0 &&
            channel.translate.isFinite()
        if (channel.byteSize <= 0 ||
            channel.offset < 0 ||
            channel.offset + channel.byteSize > profile.outputBlockSize ||
            (!validScalar && !validBits)
        ) {
            fail(
                TuneFlashStatusFailure.CHANNEL_DEFINITION_MISMATCH,
                "Flash-status channel '${channel.name}' has an unusable current-profile definition"
            )
        }
        return channel
    }

    private fun decodeBinary(channel: UsbOutputChannel, block: ByteArray): Boolean {
        val value = decodeFinite(channel, block)
        if (value != 0.0 && value != 1.0) {
            fail(TuneFlashStatusFailure.CHANNEL_VALUE_INVALID, "Channel '${channel.name}' is not binary")
        }
        return value == 1.0
    }

    private fun decodeUnsignedInteger(channel: UsbOutputChannel, block: ByteArray, maximum: Long): Long {
        val value = decodeFinite(channel, block)
        if (value < 0.0 || value > maximum.toDouble() || value % 1.0 != 0.0) {
            fail(TuneFlashStatusFailure.CHANNEL_VALUE_INVALID, "Channel '${channel.name}' is not a valid unsigned integer")
        }
        return value.toLong()
    }

    private fun decodeFinite(channel: UsbOutputChannel, block: ByteArray): Double {
        val value = channel.decode(block)
            ?: fail(TuneFlashStatusFailure.CHANNEL_DECODE_FAILED, "Channel '${channel.name}' could not be decoded")
        if (!value.isFinite()) {
            fail(TuneFlashStatusFailure.CHANNEL_VALUE_INVALID, "Channel '${channel.name}' is not finite")
        }
        return value
    }

    private fun definitionFingerprint(
        profile: UsbTunerStudioProfile,
        channels: LinkedHashMap<String, UsbOutputChannel>
    ): String {
        val canonical = buildString {
            // Preserve the accepted definition identity while extracting the production primitive.
            append("EpicDashT6FlashStatus/v1|")
            append(profile.signature).append('|').append(profile.outputBlockSize)
            channels.forEach { (role, channel) ->
                append('|').append(role)
                append('|').append(channel.name)
                append('|').append(channel.kind.lowercase(Locale.US))
                append('|').append(channel.dataType.uppercase(Locale.US))
                append('|').append(channel.offset)
                append('|').append(channel.byteSize)
                append('|').append(channel.bitStart)
                append('|').append(channel.bitEnd)
                append('|').append(java.lang.Double.doubleToLongBits(channel.scale))
                append('|').append(java.lang.Double.doubleToLongBits(channel.translate))
            }
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun fail(reason: TuneFlashStatusFailure, message: String): Nothing =
        throw TuneFlashStatusException(reason, message)
}

internal data class TuneBurnProgress(
    val baseline: TuneFlashStatus,
    val requestedTuneWriteId: Long,
    val expectedBurnRequestCnt: Int,
    val requestObservedPending: Boolean,
    val latest: TuneFlashStatus
)

/**
 * Production Burn evidence policy. ACK is transport evidence only; request identity and completion
 * are established from authoritative flash-status progression.
 */
internal object TuneBurnEvidencePolicy {
    fun requireReadyForBurn(status: TuneFlashStatus) {
        require(!status.flashWritePending) { "Cannot Burn while a flash write is already pending" }
        require(status.flashWriteErrors >= 0) { "Flash error baseline is invalid" }
    }

    fun requestHasStarted(baseline: TuneFlashStatus, current: TuneFlashStatus): Boolean {
        requireSameSession(baseline, current)
        require(current.definitionFingerprint == baseline.definitionFingerprint) {
            "Flash-status schema changed during Burn request"
        }
        require(current.flashWriteErrors == baseline.flashWriteErrors) {
            "Flash error counter changed during Burn request"
        }

        val sameWriteId = current.tuneWriteId == baseline.tuneWriteId
        val sameBurnCount = current.burnRequestCnt == baseline.burnRequestCnt
        if (sameWriteId && sameBurnCount) return false

        require(current.tuneWriteId == nextU32(baseline.tuneWriteId)) {
            "tuneWriteId did not advance exactly once"
        }
        require(current.burnRequestCnt == nextU8(baseline.burnRequestCnt)) {
            "burnRequestCnt did not advance exactly once"
        }
        return true
    }

    fun observeRequest(
        baseline: TuneFlashStatus,
        current: TuneFlashStatus,
        pendingWasObserved: Boolean = false
    ): TuneBurnProgress {
        require(requestHasStarted(baseline, current)) {
            "Burn request counters have not advanced yet"
        }

        return TuneBurnProgress(
            baseline = baseline,
            requestedTuneWriteId = nextU32(baseline.tuneWriteId),
            expectedBurnRequestCnt = nextU8(baseline.burnRequestCnt),
            requestObservedPending = pendingWasObserved || current.flashWritePending,
            latest = current
        )
    }

    fun observeCompletion(progress: TuneBurnProgress, current: TuneFlashStatus): TuneBurnProgress {
        requireSameSession(progress.baseline, current)
        require(current.definitionFingerprint == progress.baseline.definitionFingerprint) {
            "Flash-status schema changed before Burn completion"
        }
        require(current.tuneWriteId == progress.requestedTuneWriteId) {
            "tuneWriteId changed again before Burn completion"
        }
        require(current.burnRequestCnt == progress.expectedBurnRequestCnt) {
            "burnRequestCnt changed again before Burn completion"
        }
        require(current.flashWriteErrors == progress.baseline.flashWriteErrors) {
            "Flash error counter changed before Burn completion"
        }
        require(!current.flashWritePending) { "Flash write is still pending" }

        return progress.copy(
            requestObservedPending = progress.requestObservedPending || current.flashWritePending,
            latest = current
        )
    }

    private fun requireSameSession(first: TuneFlashStatus, second: TuneFlashStatus) {
        require(second.context.sessionId == first.context.sessionId) { "USB session changed during Burn" }
        require(second.context.generation == first.context.generation) { "USB generation changed during Burn" }
        require(second.context.ecuSignature == first.context.ecuSignature) { "ECU signature changed during Burn" }
        require(second.context.profileFingerprint.equals(first.context.profileFingerprint, ignoreCase = true)) {
            "Profile identity changed during Burn"
        }
    }

    private fun nextU8(value: Int): Int = (value + 1) and 0xff
    private fun nextU32(value: Long): Long = (value + 1L) and 0xffff_ffffL
}
