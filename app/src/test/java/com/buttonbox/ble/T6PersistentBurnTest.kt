package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class T6PersistentBurnTest {
    @Test
    fun failedPowerCycleResultRetainsObservedBootEvidence() {
        val result = T6PowerCycleVerificationResult(
            verified = false,
            generation = 11L,
            bootFlashWriteId = 37L,
            candidateTuneFingerprint = "a".repeat(64),
            observedTuneFingerprint = "b".repeat(64),
            bootConfigPrimaryStatus = 0,
            bootConfigBackupStatus = 0,
            detail = "boot id mismatch",
            observedTuneWriteId = 41L,
            observedBurnRequestCnt = 1,
            observedFlashWritePending = false,
            observedFlashWriteErrors = 0,
            observedFlashWrites = 7
        )
        val json = result.toJson()
        assertEquals(37L, json.getLong("bootFlashWriteId"))
        assertEquals(41L, json.getLong("observedTuneWriteId"))
        assertEquals(1, json.getInt("observedBurnRequestCnt"))
        assertFalse(json.getBoolean("observedFlashWritePending"))
        assertEquals(0, json.getInt("observedFlashWriteErrors"))
        assertEquals(7, json.getInt("observedFlashWrites"))
        assertEquals("b".repeat(64), json.getString("observedTuneFingerprint"))
    }

    @Test
    fun burnBodyIsExactAndProfileDerived() {
        val page1 = UsbTunePage(1, 0x0000, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, T6BurnProtocol.SUPPORTED_BURN_COMMAND)
        val page2 = UsbTunePage(2, 0x0100, 32, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, T6BurnProtocol.SUPPORTED_BURN_COMMAND)

        assertArrayEquals(byteArrayOf(0x42, 0x00, 0x00), T6BurnProtocol.buildBody(page1))
        assertArrayEquals(byteArrayOf(0x42, 0x00, 0x01), T6BurnProtocol.buildBody(page2))

        val bad = page1.copy(burnCommand = "X%2i")
        assertThrows(IllegalArgumentException::class.java) { T6BurnProtocol.buildBody(bad) }
    }

    @Test
    fun onlyBurnOkResponseIsAccepted() {
        T6BurnProtocol.requireAcceptedAck(byteArrayOf(0x04))
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnProtocol.requireAcceptedAck(byteArrayOf(0x00))
        }
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnProtocol.requireAcceptedAck(byteArrayOf(0x04, 0x00))
        }
    }

    @Test
    fun statusResolverUsesCurrentProfileDefinitions() {
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

        val status = T6FlashStatusResolver(250L).resolve(
            profile = profile,
            context = context,
            sample = T3NativeOutputSample(block, 7L, 1000L),
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
    }

    @Test
    fun staleOrWrongGenerationStatusFailsClosed() {
        val profile = profile()
        val context = context(profile, generation = 7L)
        val resolver = T6FlashStatusResolver(100L)
        val block = statusBlock()

        val stale = assertThrows(T6FlashStatusException::class.java) {
            resolver.resolve(profile, context, T3NativeOutputSample(block, 7L, 1000L), 1200L)
        }
        assertEquals(T6FlashStatusFailure.SAMPLE_STALE, stale.reason)

        val wrongGeneration = assertThrows(T6FlashStatusException::class.java) {
            resolver.resolve(profile, context, T3NativeOutputSample(block, 8L, 1000L), 1050L)
        }
        assertEquals(T6FlashStatusFailure.GENERATION_MISMATCH, wrongGeneration.reason)
    }

    @Test
    fun requestProgressRequiresExactSingleCounterProgressionIncludingWrap() {
        val profile = profile()
        val baseContext = context(profile, 7L)
        val baseline = resolve(
            profile,
            baseContext,
            statusBlock(
                pending = false,
                flashErrors = 0,
                burnRequests = 0xff,
                tuneWriteId = 0xffff_ffffL
            )
        )
        T6BurnEvidencePolicy.requireReadyForBurn(baseline)

        val requested = resolve(
            profile,
            baseContext,
            statusBlock(
                pending = true,
                flashErrors = 0,
                burnRequests = 0,
                tuneWriteId = 0
            )
        )
        val progress = T6BurnEvidencePolicy.observeRequest(baseline, requested)

        assertEquals(0L, progress.requestedTuneWriteId)
        assertEquals(0, progress.expectedBurnRequestCnt)
        assertTrue(progress.requestObservedPending)
    }

    @Test
    fun ackAloneCannotProveRequestProgress() {
        val profile = profile()
        val ctx = context(profile, 7L)
        val baseline = resolve(profile, ctx, statusBlock(burnRequests = 4, tuneWriteId = 10))
        val unchanged = resolve(profile, ctx, statusBlock(burnRequests = 4, tuneWriteId = 10))

        T6BurnProtocol.requireAcceptedAck(byteArrayOf(0x04))
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.observeRequest(baseline, unchanged)
        }
    }

    @Test
    fun completionRequiresPendingClearNoExtraRequestAndNoNewFlashError() {
        val profile = profile()
        val ctx = context(profile, 7L)
        val baseline = resolve(profile, ctx, statusBlock(burnRequests = 5, tuneWriteId = 20, flashErrors = 2))
        val request = resolve(profile, ctx, statusBlock(pending = true, burnRequests = 6, tuneWriteId = 21, flashErrors = 2))
        val progress = T6BurnEvidencePolicy.observeRequest(baseline, request)

        val complete = resolve(profile, ctx, statusBlock(pending = false, burnRequests = 6, tuneWriteId = 21, flashErrors = 2, flashWrites = 10))
        val completed = T6BurnEvidencePolicy.observeCompletion(progress, complete)
        assertFalse(completed.latest.flashWritePending)
        assertEquals(21L, completed.requestedTuneWriteId)

        val error = resolve(profile, ctx, statusBlock(pending = false, burnRequests = 6, tuneWriteId = 21, flashErrors = 3))
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.observeCompletion(progress, error)
        }

        val duplicate = resolve(profile, ctx, statusBlock(pending = false, burnRequests = 7, tuneWriteId = 22, flashErrors = 2))
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.observeCompletion(progress, duplicate)
        }
    }

    @Test
    fun persistenceRequiresNewGenerationBootWriteIdAndExactTuneFingerprint() {
        val profile = profile()
        val beforeContext = context(profile, 7L)
        val baseline = resolve(profile, beforeContext, statusBlock(burnRequests = 9, tuneWriteId = 100))
        val request = resolve(profile, beforeContext, statusBlock(pending = true, burnRequests = 10, tuneWriteId = 101))
        val progress = T6BurnEvidencePolicy.observeRequest(baseline, request)
        val completed = T6BurnEvidencePolicy.observeCompletion(
            progress,
            resolve(profile, beforeContext, statusBlock(pending = false, burnRequests = 10, tuneWriteId = 101))
        )

        val candidateBytes = ByteArray(32).also { it[3] = 0x55 }
        val persisted = snapshot(profile, candidateBytes, generation = 8L)
        val bootContext = context(profile, 8L, tuneFingerprint = persisted.fingerprint)
        val boot = resolve(
            profile,
            bootContext,
            statusBlock(
                pending = false,
                burnRequests = 10,
                tuneWriteId = 101,
                bootFlashWriteId = 101
            ),
            captured = 2000L,
            now = 2050L
        )

        T6BurnEvidencePolicy.requirePowerCyclePersistence(
            completed,
            boot,
            persisted,
            persisted.fingerprint
        )

        val zeroBootId = boot.copy(bootFlashWriteId = 0)
        T6BurnEvidencePolicy.requirePowerCyclePersistence(
            completed,
            zeroBootId,
            persisted,
            persisted.fingerprint
        )

        val zeroBootWrongTuneId = zeroBootId.copy(tuneWriteId = 100)
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.requirePowerCyclePersistence(
                completed,
                zeroBootWrongTuneId,
                persisted,
                persisted.fingerprint
            )
        }

        val wrongBootId = boot.copy(bootFlashWriteId = 100)
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.requirePowerCyclePersistence(completed, wrongBootId, persisted, persisted.fingerprint)
        }

        val wrongTune = snapshot(profile, ByteArray(32), generation = 8L)
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.requirePowerCyclePersistence(completed, boot, wrongTune, persisted.fingerprint)
        }
    }


    @Test
    fun baselineRestoreRequiresSecondBurnIdentityAndExactOriginalFingerprint() {
        val profile = profile()
        val baselineBytes = ByteArray(32)
        val candidateBytes = baselineBytes.copyOf().also { it[3] = 0x55 }

        val baselineSnapshot = snapshot(profile, baselineBytes, generation = 9L)
        val candidateSnapshot = snapshot(profile, candidateBytes, generation = 8L)
        val definitionFingerprint = "a".repeat(64)

        val marker = T6PersistentBurnRecoveryMarker(
            phase = T6RecoveryPhase.PERSISTED_CANDIDATE_VERIFIED,
            actionId = T6PersistentBurnPolicy.ENTER_PERSISTENT_SCALAR_BURN_ACTION_ID,
            previewId = "b".repeat(64),
            sourceGeneration = 7L,
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            baselineTuneFingerprint = baselineSnapshot.fingerprint,
            candidateTuneFingerprint = candidateSnapshot.fingerprint,
            scalarName = "testScalar",
            unit = "",
            targetDefinitionFingerprint = definitionFingerprint,
            originalRawHex = "00",
            proposedRawHex = "55",
            originalValue = 0.0,
            candidateEffectiveValue = 85.0,
            pageNumber = 1,
            pageIdentifier = 0,
            burnCommand = T6BurnProtocol.SUPPORTED_BURN_COMMAND,
            baselineTuneWriteId = 100L,
            baselineBurnRequestCnt = 10,
            baselineFlashWriteErrors = 0,
            requestedTuneWriteId = 101L,
            requestedBurnRequestCnt = 11,
            flashStatusDefinitionFingerprint = resolve(
                profile,
                context(profile, 8L, candidateSnapshot.fingerprint),
                statusBlock()
            ).definitionFingerprint,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1100L
        )

        val active = marker.baselineRestoreActive(currentGeneration = 8L, nowEpochMs = 1200L)
        assertEquals(T6RecoveryPhase.BASELINE_RESTORE_ACTIVE, active.phase)
        assertEquals(8L, active.restoreSourceGeneration)

        val restoreContext = context(profile, 8L, baselineSnapshot.fingerprint)
        val restoreBaseline = resolve(
            profile,
            restoreContext,
            statusBlock(
                pending = false,
                flashErrors = 0,
                burnRequests = 20,
                tuneWriteId = 200L
            )
        )
        val restoreRequest = resolve(
            profile,
            restoreContext,
            statusBlock(
                pending = true,
                flashErrors = 0,
                burnRequests = 21,
                tuneWriteId = 201L
            )
        )
        val restoreProgress = T6BurnEvidencePolicy.observeRequest(restoreBaseline, restoreRequest)
        val restoreCompleted = T6BurnEvidencePolicy.observeCompletion(
            restoreProgress,
            resolve(
                profile,
                restoreContext,
                statusBlock(
                    pending = false,
                    flashErrors = 0,
                    burnRequests = 21,
                    tuneWriteId = 201L
                )
            )
        )

        val awaiting = active.baselineAwaitingPowerCycle(restoreCompleted, nowEpochMs = 1300L)
        assertEquals(T6RecoveryPhase.BASELINE_AWAITING_POWER_CYCLE, awaiting.phase)
        assertEquals(201L, awaiting.restoreRequestedTuneWriteId)
        assertEquals(21, awaiting.restoreRequestedBurnRequestCnt)

        val bootContext = context(profile, 9L, baselineSnapshot.fingerprint)
        val bootStatus = resolve(
            profile,
            bootContext,
            statusBlock(
                pending = false,
                burnRequests = 21,
                tuneWriteId = 201L,
                bootFlashWriteId = 201L
            ),
            captured = 2000L,
            now = 2050L
        )

        T6BurnEvidencePolicy.requireBaselinePowerCycleRestored(
            awaiting,
            bootStatus,
            baselineSnapshot
        )

        val zeroBootId = bootStatus.copy(bootFlashWriteId = 0L)
        T6BurnEvidencePolicy.requireBaselinePowerCycleRestored(
            awaiting,
            zeroBootId,
            baselineSnapshot
        )

        val zeroBootWrongTuneId = zeroBootId.copy(tuneWriteId = 200L)
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.requireBaselinePowerCycleRestored(
                awaiting,
                zeroBootWrongTuneId,
                baselineSnapshot
            )
        }

        val wrongBootId = bootStatus.copy(bootFlashWriteId = 200L)
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.requireBaselinePowerCycleRestored(
                awaiting,
                wrongBootId,
                baselineSnapshot
            )
        }

        val wrongTune = snapshot(profile, candidateBytes, generation = 9L)
        assertThrows(IllegalArgumentException::class.java) {
            T6BurnEvidencePolicy.requireBaselinePowerCycleRestored(
                awaiting,
                bootStatus,
                wrongTune
            )
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
                    burnCommand = T6BurnProtocol.SUPPORTED_BURN_COMMAND
                )
            )
        )
    }

    private fun context(
        profile: UsbTunerStudioProfile,
        generation: Long,
        tuneFingerprint: String = "tune-$generation"
    ) = TuningContext(
        sessionId = generation,
        generation = generation,
        source = TuningDataSource.LIVE,
        ecuSignature = profile.signature,
        profileFingerprint = profile.tuneProfileFingerprint(),
        tuneFingerprint = tuneFingerprint
    )

    private fun resolve(
        profile: UsbTunerStudioProfile,
        context: TuningContext,
        block: ByteArray,
        captured: Long = 1000L,
        now: Long = 1050L
    ): T6FlashStatus = T6FlashStatusResolver(250L).resolve(
        profile,
        context,
        T3NativeOutputSample(block, context.generation, captured),
        now
    )

    private fun snapshot(profile: UsbTunerStudioProfile, bytes: ByteArray, generation: Long): TuneSnapshot =
        TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(
                TunePageSnapshot(
                    pageNumber = 1,
                    identifier = 0,
                    size = bytes.size,
                    readCommand = UsbTuneReadCodec.SUPPORTED_READ_COMMAND,
                    bytes = bytes
                )
            ),
            generation = generation,
            capturedAtEpochMs = generation
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
