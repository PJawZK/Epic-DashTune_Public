package com.buttonbox.ble

/**
 * Synchronous complete-tune reader used by the production tuner.
 *
 * The caller owns transport serialization. This helper builds the accepted current-profile R plan,
 * bounds each response, validates every returned chunk, and assembles one immutable TuneSnapshot
 * tied to the supplied native tuning context. It owns no write or Burn behavior.
 *
 * A successfully validated production read is also the permanent-project commit point: the exact
 * native TuneSnapshot is atomically persisted before the read is reported as successful. The
 * WebView does not participate in tune persistence.
 */
internal class AuthoritativeTuneSnapshotReader(
    private val profile: UsbTunerStudioProfile,
    private val context: TuningContext,
    private val chunkBytes: Int = TuneSnapshotReadPlanner.DEFAULT_CHUNK_BYTES
) {
    init {
        require(context.sessionId > 0L && context.generation >= 0L) {
            "TuneSnapshot reader requires valid native ownership"
        }
        require(context.source == TuningDataSource.LIVE) {
            "TuneSnapshot reader requires LIVE source ownership"
        }
        require(profile.signature == context.ecuSignature) {
            "TuneSnapshot reader profile/ECU signature mismatch"
        }
        require(profile.tuneProfileFingerprint().equals(context.profileFingerprint, ignoreCase = true)) {
            "TuneSnapshot reader profile fingerprint mismatch"
        }
    }

    fun read(
        capturedAtEpochMs: Long,
        exchangeReadBody: (payload: ByteArray, maxResponseBody: Int, label: String) -> ByteArray
    ): TuneSnapshot {
        require(capturedAtEpochMs > 0L) { "TuneSnapshot capture timestamp must be positive" }
        val readPlan = TuneSnapshotReadPlanner.build(profile, chunkBytes)
        require(readPlan.profileFingerprint.equals(context.profileFingerprint, ignoreCase = true)) {
            "TuneSnapshot read plan fingerprint changed"
        }

        val pagesByNumber = profile.tunePages.associateBy { it.pageNumber }
        require(pagesByNumber.size == profile.tunePages.size) { "TuneSnapshot reader found duplicate tune pages" }
        val pageBuffers = profile.tunePages.associate { it.pageNumber to ByteArray(it.size) }.toMutableMap()

        readPlan.chunks.forEachIndexed { index, chunk ->
            val page = pagesByNumber[chunk.pageNumber]
                ?: throw IllegalArgumentException("TuneSnapshot chunk references unknown page ${chunk.pageNumber}")
            require(page.identifier == chunk.pageIdentifier && page.size == chunk.pageSize) {
                "TuneSnapshot chunk page identity changed"
            }
            val expectedPayload = UsbTuneReadCodec.buildRangePayload(page, chunk.offset, chunk.count)
            require(chunk.payload.contentEquals(expectedPayload)) {
                "TuneSnapshot read payload changed from accepted codec"
            }

            val response = exchangeReadBody(
                chunk.payload.copyOf(),
                chunk.count + 1,
                "TuneSnapshot R ${index + 1}/${readPlan.totalChunks} page=${chunk.pageNumber} offset=${chunk.offset} count=${chunk.count}"
            ).copyOf()
            val data = UsbTuneReadCodec.extractData(response, chunk.count)
            require(data.size == chunk.count) { "TuneSnapshot read returned an incomplete chunk" }
            val target = pageBuffers[chunk.pageNumber]
                ?: throw IllegalArgumentException("TuneSnapshot page buffer is missing")
            data.copyInto(target, chunk.offset)
        }

        val pages = profile.tunePages.sortedBy { it.pageNumber }.map { page ->
            val bytes = pageBuffers[page.pageNumber]
                ?: throw IllegalArgumentException("TuneSnapshot page ${page.pageNumber} was not assembled")
            TunePageSnapshot(
                pageNumber = page.pageNumber,
                identifier = page.identifier,
                size = page.size,
                readCommand = page.readCommand,
                bytes = bytes
            )
        }

        val snapshot = TuneSnapshot.create(
            ecuSignature = context.ecuSignature,
            profileFingerprint = readPlan.profileFingerprint,
            pages = pages,
            generation = context.generation,
            capturedAtEpochMs = capturedAtEpochMs
        )
        require(snapshot.totalBytes == readPlan.totalBytes) { "Complete TuneSnapshot byte count mismatch" }
        require(TunerPermanentProjectStore.persistVerifiedSnapshot(snapshot)) {
            "Complete TuneSnapshot read succeeded but permanent snapshot commit failed"
        }
        return snapshot
    }
}
