package com.buttonbox.ble

/**
 * Firmware-driven ECU recognition result.
 *
 * Recognition and compatibility authority are deliberately separate:
 * - the live TS_SIGNATURE identifies the firmware family/board when possible;
 * - only exact equality of the complete live signature and imported INI signature establishes
 *   profile authority for output decoding, TuneSnapshot interpretation, writes, or Burn.
 */
internal data class EcuRecognitionResult(
    val state: State,
    val identity: EcuFirmwareIdentity?,
    val liveSignature: String,
    val expectedSignature: String,
    val exactProfileMatch: Boolean
) {
    enum class State {
        NO_RECOGNIZED_FIRMWARE,
        RECOGNIZED_NO_PROFILE_SIGNATURE,
        RECOGNIZED_PROFILE_MISMATCH,
        MATCHED_PROFILE
    }

    val recognized: Boolean get() = identity != null
    val boardName: String? get() = identity?.shortBoardName
    val firmwareFamily: String? get() = identity?.firmwareFamily
    val displayName: String? get() = identity?.displayName

    companion object {
        fun evaluate(liveSignature: String, expectedSignature: String): EcuRecognitionResult {
            val identity = EcuFirmwareIdentity.parse(liveSignature)
            val normalizedLive = identity?.signature ?: liveSignature.trim().trimEnd('\u0000').trim()
            val normalizedExpected = expectedSignature.trim().trimEnd('\u0000').trim()
            val exactMatch = identity != null &&
                UsbDeviceSelectionPolicy.exactSignatureMatches(normalizedExpected, normalizedLive)

            val state = when {
                identity == null -> State.NO_RECOGNIZED_FIRMWARE
                normalizedExpected.isEmpty() -> State.RECOGNIZED_NO_PROFILE_SIGNATURE
                !exactMatch -> State.RECOGNIZED_PROFILE_MISMATCH
                else -> State.MATCHED_PROFILE
            }

            return EcuRecognitionResult(
                state = state,
                identity = identity,
                liveSignature = normalizedLive,
                expectedSignature = normalizedExpected,
                exactProfileMatch = exactMatch
            )
        }
    }
}
