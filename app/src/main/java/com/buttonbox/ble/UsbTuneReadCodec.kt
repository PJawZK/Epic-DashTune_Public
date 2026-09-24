package com.buttonbox.ble

import java.util.Locale

/**
 * Pure read-only codec for TunerStudio calibration-page requests.
 *
 * Supported current command shape:
 * `R%2i%2o%2c` = command + uint16 page identifier + uint16 offset + uint16 count.
 * No write or burn command is represented by this API.
 */
object UsbTuneReadCodec {
    const val SUPPORTED_READ_COMMAND = "R%2i%2o%2c"

    data class DecodedScalar(
        val definition: UsbTuneScalar,
        val rawBytes: ByteArray,
        val rawNumeric: Double,
        val value: Double
    ) {
        fun rawHex(): String = rawBytes.joinToString(" ") {
            String.format(Locale.US, "%02X", it.toInt() and 0xff)
        }
    }

    fun buildPayload(profile: UsbTunerStudioProfile, scalarName: String): ByteArray {
        val scalar = profile.tuneScalars.firstOrNull { it.name.equals(scalarName, ignoreCase = true) }
            ?: throw IllegalArgumentException("Tune scalar '$scalarName' is not defined by imported profile")
        val page = profile.tunePages.firstOrNull { it.pageNumber == scalar.pageNumber }
            ?: throw IllegalArgumentException("Tune page ${scalar.pageNumber} is not defined by imported profile")
        return buildPayload(page, scalar)
    }

    fun buildPayload(page: UsbTunePage, scalar: UsbTuneScalar): ByteArray {
        require(page.pageNumber == scalar.pageNumber) {
            "Scalar ${scalar.name} belongs to page ${scalar.pageNumber}, not ${page.pageNumber}"
        }
        require(scalar.byteSize > 0) { "Scalar ${scalar.name} has unsupported type ${scalar.dataType}" }
        return buildRangePayload(page, scalar.offset, scalar.byteSize)
    }

    /** Build one bounded read-only page-range request. */
    fun buildRangePayload(page: UsbTunePage, offset: Int, count: Int): ByteArray {
        require(page.readCommand == SUPPORTED_READ_COMMAND) {
            "Unsupported tune read command '${page.readCommand}'"
        }
        require(page.identifier in 0..0xffff) { "Invalid page identifier ${page.identifier}" }
        require(page.size > 0) { "Tune page ${page.pageNumber} has invalid size ${page.size}" }
        require(offset in 0..0xffff) { "Invalid tune read offset $offset" }
        require(count in 1..0xffff) { "Invalid tune read count $count" }
        require(offset.toLong() + count.toLong() <= page.size.toLong()) {
            "Tune read range $offset+$count exceeds page ${page.pageNumber} size ${page.size}"
        }

        return byteArrayOf(
            'R'.code.toByte(),
            (page.identifier and 0xff).toByte(),
            ((page.identifier ushr 8) and 0xff).toByte(),
            (offset and 0xff).toByte(),
            ((offset ushr 8) and 0xff).toByte(),
            (count and 0xff).toByte(),
            ((count ushr 8) and 0xff).toByte()
        )
    }

    fun extractData(responseBody: ByteArray, expectedCount: Int): ByteArray {
        require(expectedCount > 0) { "Expected tune read byte count must be positive" }
        return when (responseBody.size) {
            expectedCount -> responseBody.copyOf()
            expectedCount + 1 -> {
                val status = responseBody[0].toInt() and 0xff
                require(status == 0 || status == 1) {
                    "ECU tune read response status ${String.format(Locale.US, "0x%02X", status)}"
                }
                responseBody.copyOfRange(1, responseBody.size)
            }
            else -> throw IllegalArgumentException(
                "Unexpected tune read response size ${responseBody.size}; expected $expectedCount or ${expectedCount + 1}"
            )
        }
    }

    fun decodeResponse(
        profile: UsbTunerStudioProfile,
        scalarName: String,
        responseBody: ByteArray
    ): DecodedScalar {
        val scalar = profile.tuneScalars.firstOrNull { it.name.equals(scalarName, ignoreCase = true) }
            ?: throw IllegalArgumentException("Tune scalar '$scalarName' is not defined by imported profile")
        val raw = extractData(responseBody, scalar.byteSize)
        val rawNumeric = scalar.rawNumeric(raw)
            ?: throw IllegalArgumentException("Could not decode ${scalar.dataType} bytes for ${scalar.name}")
        val value = scalar.decode(raw)
            ?: throw IllegalArgumentException("Could not scale ${scalar.name}")
        require(scalar.accepts(value)) {
            "Decoded ${scalar.name} value $value ${scalar.unit} is outside profile bounds ${scalar.low}..${scalar.high}"
        }
        return DecodedScalar(scalar, raw, rawNumeric, value)
    }
}
