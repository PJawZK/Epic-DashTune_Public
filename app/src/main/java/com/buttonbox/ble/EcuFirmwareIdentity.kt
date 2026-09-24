package com.buttonbox.ble

/**
 * Identity reported by the ECU firmware through the TunerStudio query/signature response.
 *
 * The STM32 firmware family shares one USB VID/PID and CDC transport descriptor, so USB
 * enumeration identifies only a safe transport candidate. The generated TS_SIGNATURE is the
 * firmware-provided ECU identity. Exact signature equality with the imported INI remains the
 * authority for interpreting output channels or tune memory.
 */
internal data class EcuFirmwareIdentity(
    val signature: String,
    val firmwareFamily: String,
    val shortBoardName: String?,
    val signatureHash: String?
) {
    val displayName: String get() = shortBoardName ?: firmwareFamily

    companion object {
        private val generatedHash = Regex("^[0-9]{1,10}$")
        private val boardToken = Regex("^[A-Za-z0-9_-]+$")

        fun parse(value: String): EcuFirmwareIdentity? {
            val normalized = value.trim().trimEnd('\u0000').trim()
            if (normalized.length !in 7..240) return null
            val printable = normalized.count { it.code in 32..126 }
            if (printable < normalized.length * 0.9) return null

            val family = when {
                normalized.equals("EpicEFI", ignoreCase = true) ||
                    normalized.startsWith("EpicEFI ", ignoreCase = true) -> "EpicEFI"
                normalized.equals("rusEFI", ignoreCase = true) ||
                    normalized.startsWith("rusEFI ", ignoreCase = true) -> "rusEFI"
                else -> return null
            }

            val tokens = normalized.split('.')
            val hash = tokens.lastOrNull()?.takeIf { generatedHash.matches(it) }
            val board = if (hash != null && tokens.size >= 2) {
                tokens[tokens.lastIndex - 1]
                    .takeIf { it.isNotBlank() && boardToken.matches(it) }
            } else {
                null
            }

            return EcuFirmwareIdentity(
                signature = normalized,
                firmwareFamily = family,
                shortBoardName = board,
                signatureHash = hash
            )
        }
    }
}
