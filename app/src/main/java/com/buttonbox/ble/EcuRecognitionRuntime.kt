package com.buttonbox.ble

import org.json.JSONObject

/**
 * Runtime projection of the ECU identity already discovered by [UsbEcuManager].
 *
 * This deliberately consumes the manager's existing diagnostic signature fields instead of
 * introducing a second USB query or a second handshake owner. It is read-only reporting only.
 */
internal fun UsbEcuManager.currentEcuRecognition(): EcuRecognitionResult {
    val transport = diagnosticsJson(includeAudit = false)
    return EcuRecognitionResult.evaluate(
        liveSignature = transport.optString("signature"),
        expectedSignature = transport.optString("expectedSignature")
    )
}

internal fun UsbEcuManager.currentEcuRecognitionJson(): JSONObject =
    currentEcuRecognition().toJson()

internal fun EcuRecognitionResult.toJson(): JSONObject = JSONObject()
    .put("state", state.name.lowercase())
    .put("recognized", recognized)
    .put("firmwareFamily", firmwareFamily ?: JSONObject.NULL)
    .put("boardName", boardName ?: JSONObject.NULL)
    .put("displayName", displayName ?: JSONObject.NULL)
    .put("signatureHash", identity?.signatureHash ?: JSONObject.NULL)
    .put("liveSignature", liveSignature.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
    .put("expectedSignature", expectedSignature.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
    .put("exactProfileMatch", exactProfileMatch)
