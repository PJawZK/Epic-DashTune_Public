package com.buttonbox.ble

import org.json.JSONObject

/**
 * Transport-free semantic edit preview.
 *
 * Resolution is delegated to [TuningWritePlanner], so preview and live execution share the same
 * current-profile target lookup, bounds checks, primitive encoding, quantization and bit handling.
 * No page identifier, offset, raw bytes or protocol details are serialized to the WebView.
 */
internal data class TuningSemanticPreviewResult(
    val generation: Long,
    val profileFingerprint: String,
    val tuneFingerprint: String,
    val kind: TuningWriteKind,
    val name: String,
    val cellIndex: Int?,
    val unit: String,
    val currentValue: Double,
    val requestedValue: Double,
    val effectiveValue: Double,
    val changedBytes: Int
) {
    val noOp: Boolean
        get() = changedBytes == 0

    fun toJson(): JSONObject = JSONObject()
        .put("status", "ready")
        .put("capability", "SEMANTIC_PREVIEW")
        .put("generation", generation)
        .put("profileFingerprint", profileFingerprint)
        .put("tuneFingerprint", tuneFingerprint)
        .put("kind", when (kind) {
            TuningWriteKind.SCALAR -> "scalar"
            TuningWriteKind.ARRAY_CELL -> "arrayCell"
            TuningWriteKind.BIT_FIELD -> "bitField"
        })
        .put("name", name)
        .put("cellIndex", cellIndex ?: JSONObject.NULL)
        .put("unit", unit)
        .put("currentValue", currentValue)
        .put("requestedValue", requestedValue)
        .put("effectiveValue", effectiveValue)
        .put("changedBytes", changedBytes)
        .put("noOp", noOp)
        .put("writeEligible", !noOp)
}

internal object TuningSemanticPreviewBuilder {
    fun preview(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        currentGeneration: Long,
        request: SemanticTuningWriteRequest
    ): TuningSemanticPreviewResult {
        val resolved = TuningWritePlanner.resolveForPreview(
            profile = profile,
            snapshot = snapshot,
            request = request,
            currentGeneration = currentGeneration
        )
        return TuningSemanticPreviewResult(
            generation = currentGeneration,
            profileFingerprint = profile.tuneProfileFingerprint(),
            tuneFingerprint = snapshot.fingerprint,
            kind = resolved.kind,
            name = resolved.name,
            cellIndex = resolved.cellIndex,
            unit = resolved.unit,
            currentValue = resolved.currentValue,
            requestedValue = resolved.requestedValue,
            effectiveValue = resolved.effectiveValue,
            changedBytes = resolved.changedBytes
        )
    }
}
