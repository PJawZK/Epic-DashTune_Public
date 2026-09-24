package com.buttonbox.ble

/** Neutral response-body decoder for framed ECU commands. */
internal data class EcuCommandResponse(
    val responseCode: Int,
    val payload: ByteArray
)

internal object EcuCommandResponseCodec {
    fun decodeBody(body: ByteArray): EcuCommandResponse {
        require(body.isNotEmpty()) { "Empty ECU response body" }
        return EcuCommandResponse(
            responseCode = body[0].toInt() and 0xff,
            payload = body.copyOfRange(1, body.size)
        )
    }
}
