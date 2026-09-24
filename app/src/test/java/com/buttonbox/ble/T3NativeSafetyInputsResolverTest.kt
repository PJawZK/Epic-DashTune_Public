package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class T3NativeSafetyInputsResolverTest {
    @Test
    fun currentProfileChannelsResolveFreshSafetySample() {
        val profile = currentProfile()
        val context = context(profile)
        val block = currentOutputBlock(voltageRaw = 36_600, flashErrors = 7)
        val result = T3NativeSafetyInputsResolver(maximumSampleAgeMs = 250L).resolve(
            profile = profile,
            context = context,
            sample = T3NativeOutputSample(block, context.generation, capturedElapsedMs = 1_000L),
            nowElapsedMs = 1_100L,
            supportPowerConfirmed = true
        )

        assertEquals(context, result.context)
        assertEquals(0.0, result.rpm, 0.0)
        assertFalse(result.cranking)
        assertEquals(12.2, result.voltage, 1e-9)
        assertTrue(result.supportPowerConfirmed)
        assertFalse(result.flashWritePending)
        assertEquals(7, result.flashWriteErrors)
        assertTrue(result.safetyDefinitionFingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun changedIniStorageDefinitionsAreUsedWithoutCodeChanges() {
        val profile = movedProfile()
        val context = context(profile)
        val block = ByteArray(profile.outputBlockSize)
        putU32(block, 8, 600) // scale 0.5 => 300 RPM
        putU16(block, 60, 12_345) // scale .001 => 12.345 V
        block[100] = 0x04 // flash pending bit 2
        putU16(block, 120, 9)
        block[130] = 0x02 // cranking bit 1

        val result = T3NativeSafetyInputsResolver(250L).resolve(
            profile,
            context,
            T3NativeOutputSample(block, context.generation, 1_000L),
            1_050L,
            true
        )

        assertEquals(300.0, result.rpm, 0.0)
        assertEquals(12.345, result.voltage, 1e-9)
        assertTrue(result.flashWritePending)
        assertEquals(9, result.flashWriteErrors)
        assertTrue(result.cranking)

        val current = resolve(currentProfile())
        assertNotEquals(current.safetyDefinitionFingerprint, result.safetyDefinitionFingerprint)
    }

    @Test
    fun historicalCrankingAliasMayResolveFromCurrentIni() {
        val base = currentProfile()
        val profile = base.copy(channels = base.channels.map {
            if (it.name == "isCranking") it.copy(name = "idleisCranking") else it
        })
        assertFalse(resolve(profile).cranking)
    }

    @Test
    fun simultaneousCrankingAliasesAreAmbiguousAndFailClosed() {
        val base = currentProfile()
        val exact = base.channels.single { it.name == "isCranking" }
        val profile = base.copy(channels = base.channels + exact.copy(name = "idleisCranking"))
        expect(T3NativeSafetyFailure.AMBIGUOUS_CHANNEL) { resolve(profile) }
    }

    @Test
    fun missingSemanticSafetyRoleFailsClosed() {
        val profile = currentProfile().let { base ->
            base.copy(channels = base.channels.filterNot { it.name == "VBatt" })
        }
        expect(T3NativeSafetyFailure.MISSING_CHANNEL) { resolve(profile) }
    }

    @Test
    fun unusableDefinitionFromCurrentIniFailsClosed() {
        val base = currentProfile()
        val profile = base.copy(channels = base.channels.map {
            if (it.name == "VBatt") it.copy(offset = base.outputBlockSize + 1) else it
        })
        expect(T3NativeSafetyFailure.CHANNEL_DEFINITION_MISMATCH) { resolve(profile) }
    }

    @Test
    fun staleNativeSampleFailsClosed() {
        val profile = currentProfile()
        val context = context(profile)
        expect(T3NativeSafetyFailure.SAMPLE_STALE) {
            T3NativeSafetyInputsResolver(maximumSampleAgeMs = 100L).resolve(
                profile,
                context,
                T3NativeOutputSample(currentOutputBlock(), context.generation, capturedElapsedMs = 1_000L),
                nowElapsedMs = 1_101L,
                supportPowerConfirmed = true
            )
        }
    }

    @Test
    fun differentUsbGenerationFailsClosed() {
        val profile = currentProfile()
        val context = context(profile)
        expect(T3NativeSafetyFailure.GENERATION_MISMATCH) {
            T3NativeSafetyInputsResolver(250L).resolve(
                profile,
                context,
                T3NativeOutputSample(currentOutputBlock(), context.generation + 1, capturedElapsedMs = 1_000L),
                nowElapsedMs = 1_050L,
                supportPowerConfirmed = true
            )
        }
    }

    @Test
    fun profileFingerprintMismatchFailsClosed() {
        val profile = currentProfile()
        val context = context(profile).copy(profileFingerprint = "0".repeat(64))
        expect(T3NativeSafetyFailure.PROFILE_FINGERPRINT_MISMATCH) {
            T3NativeSafetyInputsResolver(250L).resolve(
                profile,
                context,
                T3NativeOutputSample(currentOutputBlock(), context.generation, 1_000L),
                1_050L,
                true
            )
        }
    }

    @Test
    fun runningCrankingAndPendingStatesArePreservedForTransactionGate() {
        val profile = currentProfile()
        val context = context(profile)
        val block = currentOutputBlock(rpm = 300, cranking = true, flashPending = true, flashErrors = 1)
        val result = T3NativeSafetyInputsResolver(250L).resolve(
            profile,
            context,
            T3NativeOutputSample(block, context.generation, 1_000L),
            1_050L,
            true
        )

        assertEquals(300.0, result.rpm, 0.0)
        assertTrue(result.cranking)
        assertTrue(result.flashWritePending)
        assertEquals(1, result.flashWriteErrors)
    }

    private fun resolve(profile: UsbTunerStudioProfile, block: ByteArray = currentOutputBlock(profile.outputBlockSize)): T3RamSafetyInputs {
        val context = context(profile)
        return T3NativeSafetyInputsResolver(250L).resolve(
            profile,
            context,
            T3NativeOutputSample(block, context.generation, 1_000L),
            1_050L,
            true
        )
    }

    private fun context(profile: UsbTunerStudioProfile) = TuningContext(
        sessionId = 41L,
        generation = 7L,
        source = TuningDataSource.LIVE,
        ecuSignature = profile.signature,
        profileFingerprint = profile.tuneProfileFingerprint(),
        tuneFingerprint = "1".repeat(64)
    )

    private fun currentProfile(): UsbTunerStudioProfile = UsbTunerStudioProfile(
        signature = "rusEFI test.MEGA144H7",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 4_080,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = listOf(
            UsbOutputChannel("RPMValue", "scalar", "U16", 4, "RPM", 1.0, 0.0),
            UsbOutputChannel("VBatt", "scalar", "U16", 50, "V", 0.0003333333333333333, 0.0),
            UsbOutputChannel("flashWritePending", "bits", "U32", 1812, "", 1.0, 0.0, 24, 24),
            UsbOutputChannel("flashWriteErrors", "scalar", "U08", 2012, "", 1.0, 0.0),
            UsbOutputChannel("isCranking", "bits", "U32", 3644, "", 1.0, 0.0, 3, 3)
        ),
        tunePages = emptyList(),
        tuneScalars = emptyList()
    )

    private fun movedProfile(): UsbTunerStudioProfile = UsbTunerStudioProfile(
        signature = "rusEFI next.MEGA144H7",
        queryCommand = "S",
        outputCommand = "O%2o%2c",
        outputBlockSize = 256,
        envelopeFormat = "msEnvelope_1.0",
        endianness = "little",
        channels = listOf(
            UsbOutputChannel("RPMValue", "scalar", "U32", 8, "RPM", 0.5, 0.0),
            UsbOutputChannel("VBatt", "scalar", "U16", 60, "V", 0.001, 0.0),
            UsbOutputChannel("flashWritePending", "bits", "U08", 100, "", 1.0, 0.0, 2, 2),
            UsbOutputChannel("flashWriteErrors", "scalar", "U16", 120, "", 1.0, 0.0),
            UsbOutputChannel("isCranking", "bits", "U08", 130, "", 1.0, 0.0, 1, 1)
        ),
        tunePages = emptyList(),
        tuneScalars = emptyList()
    )

    private fun currentOutputBlock(
        blockSize: Int = 4_080,
        rpm: Int = 0,
        voltageRaw: Int = 36_600,
        cranking: Boolean = false,
        flashPending: Boolean = false,
        flashErrors: Int = 0
    ): ByteArray = ByteArray(blockSize).also { block ->
        if (blockSize >= 6) putU16(block, 4, rpm)
        if (blockSize >= 52) putU16(block, 50, voltageRaw)
        if (blockSize > 1815 && flashPending) block[1815] = 0x01
        if (blockSize > 2012) block[2012] = flashErrors.toByte()
        if (blockSize > 3644 && cranking) block[3644] = (block[3644].toInt() or 0x08).toByte()
    }

    private fun putU16(block: ByteArray, offset: Int, value: Int) {
        block[offset] = (value and 0xff).toByte()
        block[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putU32(block: ByteArray, offset: Int, value: Int) {
        block[offset] = (value and 0xff).toByte()
        block[offset + 1] = ((value ushr 8) and 0xff).toByte()
        block[offset + 2] = ((value ushr 16) and 0xff).toByte()
        block[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    private fun expect(reason: T3NativeSafetyFailure, action: () -> Unit) {
        try {
            action()
            fail("Expected T3NativeSafetyException($reason)")
        } catch (error: T3NativeSafetyException) {
            assertEquals(reason, error.reason)
        }
    }
}
