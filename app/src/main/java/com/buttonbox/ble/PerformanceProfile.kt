package com.buttonbox.ble

import org.json.JSONObject
import java.util.Locale

/**
 * Selectable performance profiles used by Performance Lab.
 *
 * The USB protocol itself is intentionally identical in every profile. Profiles only change
 * decoding, buffering, bridge delivery, and WebView rendering policy so A/B tests stay safe.
 */
enum class PerformanceProfile(
    val key: String,
    val label: String,
    val selectiveDecode: Boolean,
    val optimizedUsbBuffers: Boolean,
    val coalescedBridge: Boolean,
    val optimizedWeb: Boolean
) {
    INSTRUMENTED_LEGACY("legacy", "Instrumented legacy", false, false, false, false),
    NATIVE_OPTIMIZED("native", "Native optimized", true, true, false, false),
    NATIVE_BRIDGE("bridge", "Native + bridge", true, true, true, false),
    FULL_OPTIMIZED("full", "Full optimized", true, true, true, true);

    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("label", label)
        .put("selectiveDecode", selectiveDecode)
        .put("optimizedUsbBuffers", optimizedUsbBuffers)
        .put("coalescedBridge", coalescedBridge)
        .put("optimizedWeb", optimizedWeb)

    companion object {
        fun fromKey(value: String?): PerformanceProfile {
            val normalized = value?.trim()?.lowercase(Locale.US)
            return entries.firstOrNull { it.key == normalized } ?: FULL_OPTIMIZED
        }
    }
}
