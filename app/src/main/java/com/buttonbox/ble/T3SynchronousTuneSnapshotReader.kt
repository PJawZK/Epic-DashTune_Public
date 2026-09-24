package com.buttonbox.ble

/**
 * Pure synchronous complete-tune reader used by the future T3 UsbEcuManager hook.
 *
 * The caller owns transport serialization. This helper only builds the already accepted T1 `R`
 * plan, bounds each expected response, validates every returned chunk and assembles one immutable
 * TuneSnapshot tied to the supplied native context. No write or burn command is represented here.
 */
internal class T3SynchronousTuneSnapshotReader(
    private val profile: UsbTunerStudioProfile,
    private val context: TuningContext,
    private val chunkBytes: Int = TuneSnapshotReadPlanner.DEFAULT_CHUNK_BYTES
) {
    init {
        require(context.sessionId > 0L && context.generation >= 0L) { "T3 snapshot reader requires valid native ownership" }
        require(context.source == TuningDataSource.LIVE) { "T3 snapshot reader requires LIVE source ownership" }
        require(profile.signature == context.ecuSignature) { "T3 snapshot reader profile/ECU signature mismatch" }
        require(profile.tuneProfileFingerprint().equals(context.profileFingerprint, ignoreCase = true)) {
            "T3 snapshot reader profile fingerprint mismatch"
        }
    }

    fun read(
        capturedAtEpochMs: Long,
        exchangeReadBody: (payload: ByteArray, maxResponseBody: Int, label: String) -> ByteArray
    ): TuneSnapshot {
        require(capturedAtEpochMs > 0L) { "T3 snapshot capture timestamp must be positive" }
        val readPlan = TuneSnapshotReadPlanner.build(profile, chunkBytes)
        require(readPlan.profileFingerprint.equals(context.profileFingerprint, ignoreCase = true)) {
            "T3 snapshot read plan fingerprint changed"
        }

        val pagesByNumber = profile.tunePages.associateBy { it.pageNumber }
        require(pagesByNumber.size == profile.tunePages.size) { "T3 snapshot reader found duplicate tune pages" }
        val pageBuffers = profile.tunePages.associate { it.pageNumber to ByteArray(it.size) }.toMutableMap()

        readPlan.chunks.forEachIndexed { index, chunk ->
            val page = pagesByNumber[chunk.pageNumber]
                ?: throw IllegalArgumentException("T3 snapshot chunk references unknown page ${chunk.pageNumber}")
            require(page.identifier == chunk.pageIdentifier && page.size == chunk.pageSize) {
                "T3 snapshot chunk page identity changed"
            }
            val expectedPayload = UsbTuneReadCodec.buildRangePayload(page, chunk.offset, chunk.count)
            require(chunk.payload.contentEquals(expectedPayload)) { "T3 snapshot read payload changed from accepted T1 codec" }

            val response = exchangeReadBody(
                chunk.payload.copyOf(),
                chunk.count + 1,
                "T3 snapshot R ${index + 1}/${readPlan.totalChunks} page=${chunk.pageNumber} offset=${chunk.offset} count=${chunk.count}"
            ).copyOf()
            val data = UsbTuneReadCodec.extractData(response, chunk.count)
            require(data.size == chunk.count) { "T3 snapshot read returned an incomplete chunk" }
            val target = pageBuffers[chunk.pageNumber]
                ?: throw IllegalArgumentException("T3 snapshot page buffer is missing")
            data.copyInto(target, chunk.offset)
        }

        val pages = profile.tunePages.sortedBy { it.pageNumber }.map { page ->
            val bytes = pageBuffers[page.pageNumber]
                ?: throw IllegalArgumentException("T3 snapshot page ${page.pageNumber} was not assembled")
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
        require(snapshot.totalBytes == readPlan.totalBytes) { "T3 complete snapshot byte count mismatch" }
        return snapshot
    }
}
