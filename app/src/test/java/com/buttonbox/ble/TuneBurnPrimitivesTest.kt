package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneBurnPrimitivesTest {
    @Test
    fun burnBodyAndAckAreExactAndProfileDerived() {
        val page1 = UsbTunePage(
            1, 0x0000, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
            TuneBurnProtocol.SUPPORTED_BURN_COMMAND
        )
        val page2 = UsbTunePage(
            2, 0x0100, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
            TuneBurnProtocol.SUPPORTED_BURN_COMMAND
        )

        assertArrayEquals(byteArrayOf(0x42, 0x00, 0x00), TuneBurnProtocol.buildBody(page1))
        assertArrayEquals(byteArrayOf(0x42, 0x00, 0x01), TuneBurnProtocol.buildBody(page2))
        assertThrows(IllegalArgumentException::class.java) {
            TuneBurnProtocol.buildBody(page1.copy(burnCommand = "X%2i"))
        }

        TuneBurnProtocol.requireAcceptedAck(byteArrayOf(0x04))
        assertThrows(IllegalArgumentException::class.java) {
            TuneBurnProtocol.requireAcceptedAck(byteArrayOf(0x00))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TuneBurnProtocol.requireAcceptedAck(byteArrayOf(0x04, 0x00))
        }
    }

    @Test
    fun statusResolverUsesCurrentProfileDefinitionsAndFailsClosedOnStaleIdentity() {
        val profile = profile()
        val context = context(profile, generation = 7L)
        val block = statusBlock(
            needFlashBurn = true,
            pending = true,
            flashWrites = 9,
            flashErrors = 2,
            burnRequests = 17,
            tuneWriteId = 123456L,
            bootFlashWriteId = 123455L,
            primaryStatus = 3,
            backupStatus = 4
        )

        val resolver = TuneFlashStatusResolver(250L)
        val status = resolver.resolve(
            profile = profile,
            context = context,
            sample = NativeOutputSample(block, 7L, 1000L),
            nowElapsedMs = 1100L
        )

        assertTrue(status.needFlashBurn)
        assertTrue(status.flashWritePending)
        assertEquals(9, status.flashWrites)
        assertEquals(2, status.flashWriteErrors)
        assertEquals(17, status.burnRequestCnt)
        assertEquals(123456L, status.tuneWriteId)
        assertEquals(123455L, status.bootFlashWriteId)
        assertEquals(3, status.bootConfigPrimaryStatus)
        assertEquals(4, status.bootConfigBackupStatus)
        assertTrue(status.definitionFingerprint.matches(Regex("[0-9a-f]{64}")))

        val stale = assertThrows(TuneFlashStatusException::class.java) {
            resolver.resolve(profile, context, NativeOutputSample(statusBlock(), 7L, 1000L), 1300L)
        }
        assertEquals(TuneFlashStatusFailure.SAMPLE_STALE, stale.reason)

        val wrongGeneration = assertThrows(TuneFlashStatusException::class.java) {
            resolver.resolve(profile, context, NativeOutputSample(statusBlock(), 8L, 1000L), 1050L)
        }
        assertEquals(TuneFlashStatusFailure.GENERATION_MISMATCH, wrongGeneration.reason)
    }

    @Test
    fun requestAndCompletionRequireExactCounterProgressionAndStableErrorState() {
        val profile = profile()
        val ctx = context(profile, 7L)
        val baseline = resolve(
            profile,
            ctx,
            statusBlock(
                pending = false,
                flashErrors = 2,
                burnRequests = 0xff,
                tuneWriteId = 0xffff_ffffL
            )
        )
        TuneBurnEvidencePolicy.requireReadyForBurn(baseline)

        val requested = resolve(
            profile,
            ctx,
            statusBlock(
                pending = true,
                flashErrors = 2,
                burnRequests = 0,
                tuneWriteId = 0
            )
        )
        val progress = TuneBurnEvidencePolicy.observeRequest(baseline, requested)
        assertEquals(0L, progress.requestedTuneWriteId)
        assertEquals(0, progress.expectedBurnRequestCnt)
        assertTrue(progress.requestObservedPending)

        val complete = resolve(
            profile,
            ctx,
            statusBlock(
                pending = false,
                flashErrors = 2,
                burnRequests = 0,
                tuneWriteId = 0,
                flashWrites = 10
            )
        )
        val completed = TuneBurnEvidencePolicy.observeCompletion(progress, complete)
        assertFalse(completed.latest.flashWritePending)

        val newError = resolve(
            profile,
            ctx,
            statusBlock(
                pending = false,
                flashErrors = 3,
                burnRequests = 0,
                tuneWriteId = 0
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TuneBurnEvidencePolicy.observeCompletion(progress, newError)
        }
    }

    @Test
    fun ackAloneCannotProveBurnRequestProgress() {
        val profile = profile()
        val ctx = context(profile, 7L)
        val baseline = resolve(profile, ctx, statusBlock(burnRequests = 4, tuneWriteId = 10))
        val unchanged = resolve(profile, ctx, statusBlock(burnRequests = 4, tuneWriteId = 10))

        TuneBurnProtocol.requireAcceptedAck(byteArrayOf(0x04))
        assertThrows(IllegalArgumentException::class.java) {
            TuneBurnEvidencePolicy.observeRequest(baseline, unchanged)
        }
    }

    private fun profile(): UsbTunerStudioProfile {
        val channels = listOf(
            UsbOutputChannel("needFlashBurn", "bits", "U32", 0, "", 1.0, 0.0, 6, 6),
            UsbOutputChannel("flashWritePending", "bits", "U32", 4, "", 1.0, 0.0, 24, 24),
            UsbOutputChannel("flashWrites", "scalar", "U08", 8, "", 1.0, 0.0),
            UsbOutputChannel("flashWriteErrors", "scalar", "U08", 9, "", 1.0, 0.0),
            UsbOutputChannel("burnRequestCnt", "scalar", "U08", 10, "", 1.0, 0.0),
            UsbOutputChannel("tuneWriteId", "scalar", "U32", 12, "", 1.0, 0.0),
            UsbOutputChannel("bootFlashWriteId", "scalar", "U32", 16, "", 1.0, 0.0),
            UsbOutputChannel("bootConfigPrimaryStatus", "scalar", "U08", 20, "", 1.0, 0.0),
            UsbOutputChannel("bootConfigBackupStatus", "scalar", "U08", 21, "", 1.0, 0.0)
        )
        return UsbTunerStudioProfile(
            signature = "rusEFI test.MEGA144H7",
            queryCommand = "S",
            outputCommand = "O%2o%2c",
            outputBlockSize = 64,
            envelopeFormat = "msEnvelope_1.0",
            endianness = "little",
            channels = channels,
            tunePages = listOf(
                UsbTunePage(
                    pageNumber = 1,
                    identifier = 0x0000,
                    size = 32,
                    readCommand = UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
                    burnCommand = TuneBurnProtocol.SUPPORTED_BURN_COMMAND
                )
            )
        )
    }

    private fun context(
        profile: UsbTunerStudioProfile,
        generation: Long
    ) = TuningContext(
        sessionId = generation,
        generation = generation,
        source = TuningDataSource.LIVE,
        ecuSignature = profile.signature,
        profileFingerprint = profile.tuneProfileFingerprint(),
        tuneFingerprint = "tune-$generation"
    )

    private fun resolve(
        profile: UsbTunerStudioProfile,
        context: TuningContext,
        block: ByteArray,
        captured: Long = 1000L,
        now: Long = 1050L
    ): TuneFlashStatus = TuneFlashStatusResolver(250L).resolve(
        profile,
        context,
        NativeOutputSample(block, context.generation, captured),
        now
    )

    private fun statusBlock(
        needFlashBurn: Boolean = false,
        pending: Boolean = false,
        flashWrites: Int = 0,
        flashErrors: Int = 0,
        burnRequests: Int = 0,
        tuneWriteId: Long = 0,
        bootFlashWriteId: Long = 0,
        primaryStatus: Int = 0,
        backupStatus: Int = 0
    ): ByteArray {
        val block = ByteArray(64)
        putU32(block, 0, if (needFlashBurn) 1L shl 6 else 0L)
        putU32(block, 4, if (pending) 1L shl 24 else 0L)
        block[8] = flashWrites.toByte()
        block[9] = flashErrors.toByte()
        block[10] = burnRequests.toByte()
        putU32(block, 12, tuneWriteId)
        putU32(block, 16, bootFlashWriteId)
        block[20] = primaryStatus.toByte()
        block[21] = backupStatus.toByte()
        return block
    }

    private fun putU32(block: ByteArray, offset: Int, value: Long) {
        block[offset] = (value and 0xff).toByte()
        block[offset + 1] = ((value ushr 8) and 0xff).toByte()
        block[offset + 2] = ((value ushr 16) and 0xff).toByte()
        block[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
