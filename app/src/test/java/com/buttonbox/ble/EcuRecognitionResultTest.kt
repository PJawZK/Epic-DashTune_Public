package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EcuRecognitionResultTest {
    @Test
    fun exactCompleteSignatureEstablishesMatchedProfileAuthority() {
        val signature = "rusEFI master.2026.08.26.MEGA144H7.2273317132"

        val result = EcuRecognitionResult.evaluate(signature, signature)

        assertEquals(EcuRecognitionResult.State.MATCHED_PROFILE, result.state)
        assertTrue(result.recognized)
        assertTrue(result.exactProfileMatch)
        assertEquals("MEGA144H7", result.boardName)
        assertEquals("rusEFI", result.firmwareFamily)
    }

    @Test
    fun sameBoardWithDifferentGeneratedHashIsRecognizedButNotAuthorized() {
        val live = "rusEFI master.2026.09.21.proteus_f7.1234567890"
        val ini = "rusEFI master.2026.09.21.proteus_f7.1234567891"

        val result = EcuRecognitionResult.evaluate(live, ini)

        assertEquals(EcuRecognitionResult.State.RECOGNIZED_PROFILE_MISMATCH, result.state)
        assertTrue(result.recognized)
        assertFalse(result.exactProfileMatch)
        assertEquals("proteus_f7", result.boardName)
    }

    @Test
    fun recognizedFirmwareWithoutIniSignatureNeverGainsProfileAuthority() {
        val result = EcuRecognitionResult.evaluate(
            "rusEFI master.2026.09.21.super-uaefi.1234567890",
            ""
        )

        assertEquals(EcuRecognitionResult.State.RECOGNIZED_NO_PROFILE_SIGNATURE, result.state)
        assertTrue(result.recognized)
        assertFalse(result.exactProfileMatch)
        assertEquals("super-uaefi", result.boardName)
    }

    @Test
    fun actualBoardNamingStylesRemainRecognizableWithoutWhitelist() {
        val cases = mapOf(
            "rusEFI master.2026.09.21.f407-discovery.1" to "f407-discovery",
            "rusEFI master.2026.09.21.uaefi_pro_h7.2" to "uaefi_pro_h7",
            "rusEFI master.2026.09.21.MEGA144H7X20.3" to "MEGA144H7X20",
            "EpicEFI master.2026.09.21.epicECUv1.4" to "epicECUv1"
        )

        cases.forEach { (signature, expectedBoard) ->
            val result = EcuRecognitionResult.evaluate(signature, signature)
            assertEquals(EcuRecognitionResult.State.MATCHED_PROFILE, result.state)
            assertEquals(expectedBoard, result.boardName)
        }
    }

    @Test
    fun unrelatedTextDoesNotBecomeAnEcuIdentityEvenWhenIniTextMatchesIt() {
        val text = "Generic USB Serial Device"

        val result = EcuRecognitionResult.evaluate(text, text)

        assertEquals(EcuRecognitionResult.State.NO_RECOGNIZED_FIRMWARE, result.state)
        assertFalse(result.recognized)
        assertFalse(result.exactProfileMatch)
        assertNull(result.boardName)
    }

    @Test
    fun runtimeJsonReportsIdentityAndKeepsAuthoritySeparate() {
        val live = "rusEFI master.2026.09.21.uaefi_pro_h7.987654321"
        val ini = "rusEFI master.2026.09.21.uaefi_pro_h7.987654322"
        val json = EcuRecognitionResult.evaluate(live, ini).toJson()

        assertEquals("recognized_profile_mismatch", json.getString("state"))
        assertTrue(json.getBoolean("recognized"))
        assertEquals("rusEFI", json.getString("firmwareFamily"))
        assertEquals("uaefi_pro_h7", json.getString("boardName"))
        assertEquals("987654321", json.getString("signatureHash"))
        assertFalse(json.getBoolean("exactProfileMatch"))
        assertEquals(live, json.getString("liveSignature"))
        assertEquals(ini, json.getString("expectedSignature"))
    }
}
