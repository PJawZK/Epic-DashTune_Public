package com.buttonbox.ble

import java.util.Locale

/** Immutable native output-channel sample owned by the current USB generation. */
internal class T3NativeOutputSample(
    block: ByteArray,
    val generation: Long,
    val capturedElapsedMs: Long
) {
    private val bytes = block.copyOf()

    init {
        require(generation >= 0L) { "T3 native sample generation must be valid" }
        require(capturedElapsedMs >= 0L) { "T3 native sample time must be valid" }
        require(bytes.isNotEmpty()) { "T3 native output sample must not be empty" }
    }

    fun bytes(): ByteArray = bytes.copyOf()
}

internal enum class T3NativeSafetyFailure {
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

internal class T3NativeSafetyException(
    val reason: T3NativeSafetyFailure,
    message: String
) : IllegalStateException(message)

/**
 * Semantic names are the only application-level channel policy. Byte offsets, primitive types,
 * scaling and bit positions always come from the current imported mainController.ini.
 *
 * Aliases exist only where the same semantic signal has historically appeared under more than one
 * INI name. If more than one alias is present simultaneously the resolver fails closed rather than
 * guessing which signal is authoritative.
 */
internal object T3SafetyChannelPolicy {
    val RPM = listOf("RPMValue")
    val CRANKING = listOf("isCranking", "idleisCranking")
    val VOLTAGE = listOf("VBatt")
    val FLASH_PENDING = listOf("flashWritePending")
    val FLASH_ERRORS = listOf("flashWriteErrors")
}

/**
 * Resolves W3 safety inputs from one fresh native output block using the current imported profile
 * as the schema authority. No historical offsets/types/scales/bits are duplicated in Kotlin.
 */
internal class T3NativeSafetyInputsResolver(
    private val maximumSampleAgeMs: Long
) {
    init {
        require(maximumSampleAgeMs > 0L) { "T3 maximum safety sample age must be positive" }
    }

    fun resolve(
        profile: UsbTunerStudioProfile,
        context: TuningContext,
        sample: T3NativeOutputSample,
        nowElapsedMs: Long,
        supportPowerConfirmed: Boolean
    ): T3RamSafetyInputs {
        if (context.sessionId <= 0L || context.generation < 0L || context.source != TuningDataSource.LIVE) {
            fail(T3NativeSafetyFailure.INVALID_CONTEXT, "T3 safety inputs require positive LIVE session ownership")
        }
        if (sample.generation != context.generation) {
            fail(T3NativeSafetyFailure.GENERATION_MISMATCH, "T3 native output sample belongs to another USB generation")
        }
        if (profile.signature != context.ecuSignature) {
            fail(T3NativeSafetyFailure.PROFILE_SIGNATURE_MISMATCH, "T3 imported profile signature does not match live ECU context")
        }
        val profileFingerprint = profile.tuneProfileFingerprint()
        if (!context.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            fail(T3NativeSafetyFailure.PROFILE_FINGERPRINT_MISMATCH, "T3 imported profile fingerprint does not match live tuning context")
        }
        if (nowElapsedMs < sample.capturedElapsedMs) {
            fail(T3NativeSafetyFailure.SAMPLE_TIME_INVALID, "T3 native safety sample timestamp is in the future")
        }
        if (nowElapsedMs - sample.capturedElapsedMs > maximumSampleAgeMs) {
            fail(T3NativeSafetyFailure.SAMPLE_STALE, "T3 native safety sample is stale")
        }

        val resolved = linkedMapOf(
            "rpm" to semanticChannel(profile, "rpm", T3SafetyChannelPolicy.RPM, requireScalar = true),
            "cranking" to semanticChannel(profile, "cranking", T3SafetyChannelPolicy.CRANKING, requireScalar = false),
            "voltage" to semanticChannel(profile, "voltage", T3SafetyChannelPolicy.VOLTAGE, requireScalar = true),
            "flashPending" to semanticChannel(profile, "flash pending", T3SafetyChannelPolicy.FLASH_PENDING, requireScalar = false),
            "flashErrors" to semanticChannel(profile, "flash errors", T3SafetyChannelPolicy.FLASH_ERRORS, requireScalar = true)
        )

        val block = sample.bytes()
        if (block.size < profile.outputBlockSize) {
            fail(
                T3NativeSafetyFailure.CHANNEL_DECODE_FAILED,
                "T3 native safety sample is ${block.size} bytes but current INI requires ${profile.outputBlockSize}"
            )
        }

        val rpm = decodeFinite(resolved.getValue("rpm"), block)
        val crankingValue = decodeFinite(resolved.getValue("cranking"), block)
        val voltage = decodeFinite(resolved.getValue("voltage"), block)
        val pendingValue = decodeFinite(resolved.getValue("flashPending"), block)
        val flashErrorsValue = decodeFinite(resolved.getValue("flashErrors"), block)

        if (rpm < 0.0) {
            fail(T3NativeSafetyFailure.CHANNEL_VALUE_INVALID, "T3 RPM safety value is negative")
        }
        if (voltage <= 0.0) {
            fail(T3NativeSafetyFailure.CHANNEL_VALUE_INVALID, "T3 battery-voltage safety value is not positive")
        }
        if (pendingValue != 0.0 && pendingValue != 1.0) {
            fail(T3NativeSafetyFailure.CHANNEL_VALUE_INVALID, "T3 flash-pending safety value is not binary")
        }
        if (crankingValue != 0.0 && crankingValue != 1.0) {
            fail(T3NativeSafetyFailure.CHANNEL_VALUE_INVALID, "T3 cranking safety value is not binary")
        }
        if (flashErrorsValue < 0.0 || flashErrorsValue > Int.MAX_VALUE.toDouble() || flashErrorsValue % 1.0 != 0.0) {
            fail(T3NativeSafetyFailure.CHANNEL_VALUE_INVALID, "T3 flash-error safety value is not a non-negative integer")
        }
        val flashErrors = flashErrorsValue.toInt()

        return T3RamSafetyInputs(
            context = context,
            rpm = rpm,
            cranking = crankingValue == 1.0,
            voltage = voltage,
            supportPowerConfirmed = supportPowerConfirmed,
            flashWritePending = pendingValue == 1.0,
            flashWriteErrors = flashErrors,
            safetyDefinitionFingerprint = safetyDefinitionFingerprint(profile, resolved)
        )
    }

    private fun semanticChannel(
        profile: UsbTunerStudioProfile,
        role: String,
        aliases: List<String>,
        requireScalar: Boolean
    ): UsbOutputChannel {
        val aliasSet = aliases.map { it.lowercase(Locale.US) }.toSet()
        val matches = profile.channels.filter { it.name.lowercase(Locale.US) in aliasSet }
        if (matches.isEmpty()) {
            fail(
                T3NativeSafetyFailure.MISSING_CHANNEL,
                "T3 $role safety channel is missing; accepted current-profile names: ${aliases.joinToString()}"
            )
        }
        if (matches.size != 1) {
            fail(
                T3NativeSafetyFailure.AMBIGUOUS_CHANNEL,
                "T3 $role safety channel resolves ${matches.size} times (${matches.joinToString { it.name }})"
            )
        }
        val channel = matches.single()
        validateProfileDefinition(profile, channel, requireScalar)
        return channel
    }

    private fun validateProfileDefinition(
        profile: UsbTunerStudioProfile,
        channel: UsbOutputChannel,
        requireScalar: Boolean
    ) {
        val scalar = channel.kind.equals("scalar", ignoreCase = true)
        val bits = channel.kind.equals("bits", ignoreCase = true)
        val bitCapacity = channel.byteSize * 8
        val validBits = bits && channel.bitStart >= 0 && channel.bitEnd >= channel.bitStart && channel.bitEnd < bitCapacity
        val validScalar = scalar && channel.scale.isFinite() && channel.scale != 0.0 && channel.translate.isFinite()
        if (channel.byteSize <= 0 || channel.offset < 0 || channel.offset + channel.byteSize > profile.outputBlockSize ||
            (!validScalar && !validBits) || (requireScalar && !validScalar)
        ) {
            fail(
                T3NativeSafetyFailure.CHANNEL_DEFINITION_MISMATCH,
                "T3 safety channel '${channel.name}' has an unusable definition in the current imported profile"
            )
        }
    }

    private fun safetyDefinitionFingerprint(
        profile: UsbTunerStudioProfile,
        channels: LinkedHashMap<String, UsbOutputChannel>
    ): String {
        val canonical = buildString {
            append("EpicDashT3SafetyDefinition/v2|")
            append(profile.signature).append('|').append(profile.outputBlockSize)
            channels.forEach { (role, channel) ->
                append('|').append(role)
                append('|').append(channel.name)
                append('|').append(channel.kind.lowercase(Locale.US))
                append('|').append(channel.dataType.uppercase(Locale.US))
                append('|').append(channel.offset)
                append('|').append(channel.byteSize)
                append('|').append(channel.unit)
                append('|').append(java.lang.Double.doubleToLongBits(channel.scale))
                append('|').append(java.lang.Double.doubleToLongBits(channel.translate))
                append('|').append(channel.bitStart)
                append('|').append(channel.bitEnd)
            }
        }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun decodeFinite(channel: UsbOutputChannel, block: ByteArray): Double {
        val value = channel.decode(block)
            ?: fail(
                T3NativeSafetyFailure.CHANNEL_DECODE_FAILED,
                "T3 safety channel '${channel.name}' could not be decoded from native output block"
            )
        if (!value.isFinite()) {
            fail(T3NativeSafetyFailure.CHANNEL_VALUE_INVALID, "T3 safety channel '${channel.name}' is not finite")
        }
        return value
    }

    private fun fail(reason: T3NativeSafetyFailure, message: String): Nothing =
        throw T3NativeSafetyException(reason, message)
}
