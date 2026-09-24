package com.buttonbox.ble

/** One deterministic bounded read that contributes to a complete TuneSnapshot. */
data class TuneReadChunk(
    val pageNumber: Int,
    val pageIdentifier: Int,
    val pageSize: Int,
    val offset: Int,
    val count: Int,
    val payload: ByteArray
)

data class TuneSnapshotReadPlan(
    val profileFingerprint: String,
    val totalBytes: Int,
    val chunks: List<TuneReadChunk>
) {
    val totalChunks: Int get() = chunks.size
}

object TuneSnapshotReadPlanner {
    const val DEFAULT_CHUNK_BYTES = 1024

    fun build(
        profile: UsbTunerStudioProfile,
        chunkBytes: Int = DEFAULT_CHUNK_BYTES
    ): TuneSnapshotReadPlan {
        require(profile.signature.isNotBlank()) { "Tune profile has no ECU signature" }
        require(profile.envelopeFormat.equals("msEnvelope_1.0", ignoreCase = true)) {
            "Unsupported message envelope '${profile.envelopeFormat}'"
        }
        require(profile.endianness.equals("little", ignoreCase = true)) {
            "Unsupported tune endianness '${profile.endianness}'"
        }
        require(chunkBytes in 1..0xffff) { "Invalid tune read chunk size $chunkBytes" }

        val pages = profile.tunePages.sortedBy { it.pageNumber }
        require(pages.isNotEmpty()) { "Imported profile has no tune pages" }
        require(pages.map { it.pageNumber }.distinct().size == pages.size) { "Duplicate tune page numbers" }
        require(pages.map { it.identifier }.distinct().size == pages.size) { "Duplicate tune page identifiers" }

        val chunks = mutableListOf<TuneReadChunk>()
        var totalBytes = 0L
        pages.forEach { page ->
            require(page.size > 0) { "Tune page ${page.pageNumber} has invalid size ${page.size}" }
            var offset = 0
            while (offset < page.size) {
                val count = minOf(chunkBytes, page.size - offset)
                chunks += TuneReadChunk(
                    pageNumber = page.pageNumber,
                    pageIdentifier = page.identifier,
                    pageSize = page.size,
                    offset = offset,
                    count = count,
                    payload = UsbTuneReadCodec.buildRangePayload(page, offset, count)
                )
                offset += count
            }
            totalBytes += page.size.toLong()
        }
        require(totalBytes in 1..Int.MAX_VALUE.toLong()) { "Tune image byte count $totalBytes is unsupported" }
        require(chunks.sumOf { it.count.toLong() } == totalBytes) { "Tune read plan byte count mismatch" }

        return TuneSnapshotReadPlan(
            profileFingerprint = profile.tuneProfileFingerprint(),
            totalBytes = totalBytes.toInt(),
            chunks = chunks.toList()
        )
    }
}
