package com.buttonbox.ble

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class T3RamRecoveryMarkerCodecTest {
    @Test
    fun markerJsonRoundTripPreservesExactRecoveryIdentity() {
        val marker = marker()
        assertEquals(marker, T3RamRecoveryMarker.fromJson(marker.toJson()))
    }

    @Test
    fun malformedSchemaDynamicIdentityOrRawWidthFailsClosed() {
        val base = marker().toJson()
        for (mutated in listOf(
            JSONObject(base.toString()).put("schema", "wrong"),
            JSONObject(base.toString()).put("profileFingerprint", "not-a-fingerprint"),
            JSONObject(base.toString()).put("targetDefinitionFingerprint", "also-invalid"),
            JSONObject(base.toString()).put("originalRawHex", "00")
        )) {
            assertThrows(IllegalArgumentException::class.java) { T3RamRecoveryMarker.fromJson(mutated) }
        }
    }

    private fun marker() = T3RamRecoveryMarker(
        phase = T3RecoveryPhase.WRITE,
        actionId = "action-1",
        previewId = "a".repeat(64),
        sessionId = 1,
        generation = 2,
        ecuSignature = "rusEFI test.MEGA144H7",
        profileFingerprint = "f".repeat(64),
        baselineTuneFingerprint = "b".repeat(64),
        candidateTuneFingerprint = "c".repeat(64),
        targetDefinitionFingerprint = "d".repeat(64),
        originalRawHex = "c409",
        proposedRawHex = "280a",
        createdAtEpochMs = 123L
    )
}
