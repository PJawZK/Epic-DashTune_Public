package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal enum class TuningWriteKind {
    SCALAR,
    ARRAY_CELL,
    BIT_FIELD
}

internal data class SemanticTuningWriteRequest(
    val kind: TuningWriteKind,
    val name: String,
    val requestedValue: Double,
    val cellIndex: Int? = null
) {
    val semanticKey: String
        get() = when (kind) {
            TuningWriteKind.SCALAR -> "scalar:$name"
            TuningWriteKind.ARRAY_CELL -> "array:$name:${cellIndex ?: -1}"
            TuningWriteKind.BIT_FIELD -> "bitField:$name"
        }
}

internal data class SemanticTuningWriteEnvelope(
    val expectedGeneration: Long,
    val expectedProfileFingerprint: String,
    val expectedTuneFingerprint: String,
    val changes: List<SemanticTuningWriteRequest>
) {
    companion object {
        private const val MAX_CHANGES = 2048

        fun parse(json: String): SemanticTuningWriteEnvelope {
            val root = JSONObject(json)
            val generation = root.getLong("generation")
            val profileFingerprint = root.getString("profileFingerprint")
            val tuneFingerprint = root.getString("tuneFingerprint")
            require(generation > 0L) { "Tuning write generation must be positive" }
            require(profileFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Tuning write profile fingerprint is invalid"
            }
            require(tuneFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Tuning write tune fingerprint is invalid"
            }

            val array = root.getJSONArray("changes")
            require(array.length() in 1..MAX_CHANGES) {
                "Tuning write batch must contain 1..$MAX_CHANGES semantic changes"
            }
            val changes = ArrayList<SemanticTuningWriteRequest>(array.length())
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val kind = when (item.getString("kind").lowercase(Locale.US)) {
                    "scalar" -> TuningWriteKind.SCALAR
                    "arraycell", "array_cell" -> TuningWriteKind.ARRAY_CELL
                    "bitfield", "bit_field", "bit" -> TuningWriteKind.BIT_FIELD
                    else -> throw IllegalArgumentException("Unsupported tuning write kind at index $index")
                }
                val name = item.getString("name").trim()
                require(name.isNotBlank()) { "Tuning write name must not be blank" }
                val requested = item.getDouble("requestedValue")
                require(requested.isFinite()) { "Tuning write value for '$name' must be finite" }
                val cellIndex = if (kind == TuningWriteKind.ARRAY_CELL) item.getInt("cellIndex") else null
                if (cellIndex != null) require(cellIndex >= 0) { "Array cell index must be non-negative" }
                changes += SemanticTuningWriteRequest(kind, name, requested, cellIndex)
            }
            require(changes.map { it.semanticKey }.distinct().size == changes.size) {
                "Tuning write batch contains duplicate semantic targets"
            }
            return SemanticTuningWriteEnvelope(
                expectedGeneration = generation,
                expectedProfileFingerprint = profileFingerprint.lowercase(Locale.US),
                expectedTuneFingerprint = tuneFingerprint.lowercase(Locale.US),
                changes = changes
            )
        }
    }
}

internal class ResolvedTuningWrite internal constructor(
    val kind: TuningWriteKind,
    val name: String,
    val cellIndex: Int?,
    val pageNumber: Int,
    val pageIdentifier: Int,
    val pageSize: Int,
    val offset: Int,
    val byteSize: Int,
    val unit: String,
    val currentValue: Double,
    val requestedValue: Double,
    val effectiveValue: Double,
    beforeBytes: ByteArray,
    afterBytes: ByteArray
) {
    private val before = beforeBytes.copyOf()
    private val after = afterBytes.copyOf()

    fun beforeBytes(): ByteArray = before.copyOf()
    fun afterBytes(): ByteArray = after.copyOf()

    val changedBytes: Int
        get() = before.indices.count { before[it] != after[it] }

    val semanticLabel: String
        get() = if (kind == TuningWriteKind.ARRAY_CELL) "$name[${cellIndex ?: -1}]" else name

    fun toSemanticJson(): JSONObject = JSONObject()
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
}

internal data class TuningWriteBatchPlan(
    val generation: Long,
    val profileFingerprint: String,
    val baselineTuneFingerprint: String,
    val operations: List<ResolvedTuningWrite>,
    val expectedSnapshot: TuneSnapshot
) {
    val dirtyPageNumbers: Set<Int> = operations.mapTo(linkedSetOf()) { it.pageNumber }
    val changedBytes: Int = operations.sumOf { it.changedBytes }

    fun queuedJson(): JSONObject = JSONObject()
        .put("status", "queued")
        .put("capability", "LIVE_TUNING_WRITE")
        .put("generation", generation)
        .put("profileFingerprint", profileFingerprint)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("expectedTuneFingerprint", expectedSnapshot.fingerprint)
        .put("changeCount", operations.size)
        .put("changedBytes", changedBytes)
        .put("changes", JSONArray().also { array -> operations.forEach { array.put(it.toSemanticJson()) } })
}

internal data class TuningWriteBatchResult(
    val status: String,
    val generation: Long,
    val baselineTuneFingerprint: String,
    val verifiedTuneFingerprint: String?,
    val changeCount: Int,
    val changedBytes: Int,
    val dirtyPageCount: Int,
    val detail: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", status)
        .put("capability", "LIVE_TUNING_WRITE")
        .put("generation", generation)
        .put("baselineTuneFingerprint", baselineTuneFingerprint)
        .put("verifiedTuneFingerprint", verifiedTuneFingerprint ?: JSONObject.NULL)
        .put("changeCount", changeCount)
        .put("changedBytes", changedBytes)
        .put("dirtyPageCount", dirtyPageCount)
        .put("detail", detail)
}

internal data class TuningBurnResult(
    val status: String,
    val generation: Long,
    val tuneFingerprint: String?,
    val pagesRequested: Int,
    val pagesCompleted: Int,
    val requestedTuneWriteId: Long?,
    val burnRequestCnt: Int?,
    val detail: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", status)
        .put("capability", "LIVE_TUNING_PERSIST")
        .put("generation", generation)
        .put("tuneFingerprint", tuneFingerprint ?: JSONObject.NULL)
        .put("pagesRequested", pagesRequested)
        .put("pagesCompleted", pagesCompleted)
        .put("requestedTuneWriteId", requestedTuneWriteId ?: JSONObject.NULL)
        .put("burnRequestCnt", burnRequestCnt ?: JSONObject.NULL)
        .put("detail", detail)
}

internal object TuningWritePlanner {
    fun plan(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        envelope: SemanticTuningWriteEnvelope,
        currentGeneration: Long
    ): TuningWriteBatchPlan {
        require(currentGeneration > 0L && envelope.expectedGeneration == currentGeneration) {
            "Tuning write belongs to another USB generation"
        }
        require(snapshot.generation == currentGeneration) {
            "Current TuneSnapshot belongs to another USB generation"
        }
        require(profile.signature.isNotBlank() && profile.signature == snapshot.ecuSignature) {
            "Tuning write profile/ECU signature mismatch"
        }

        val profileFingerprint = profile.tuneProfileFingerprint()
        require(profileFingerprint.equals(envelope.expectedProfileFingerprint, ignoreCase = true)) {
            "Tuning write profile identity is stale"
        }
        require(snapshot.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            "Current TuneSnapshot profile identity changed"
        }
        require(snapshot.fingerprint.equals(envelope.expectedTuneFingerprint, ignoreCase = true)) {
            "Tuning write tune identity is stale; refresh before writing"
        }

        val conditionAuthority = IniConditionAuthority.build(profile, snapshot)
        val operations = envelope.changes.map { request ->
            resolveRequest(profile, snapshot, request, conditionAuthority)
        }.sortedWith(compareBy<ResolvedTuningWrite> { it.pageNumber }.thenBy { it.offset })

        operations.forEachIndexed { index, left ->
            require(left.changedBytes > 0) { "Tuning write '${left.semanticLabel}' produces no byte change" }
            for (otherIndex in index + 1 until operations.size) {
                val right = operations[otherIndex]
                if (left.pageNumber != right.pageNumber) continue
                val leftEnd = left.offset + left.byteSize
                val rightEnd = right.offset + right.byteSize
                require(leftEnd <= right.offset || rightEnd <= left.offset) {
                    "Tuning writes '${left.semanticLabel}' and '${right.semanticLabel}' overlap in the current INI"
                }
            }
        }

        val expected = patchSnapshot(snapshot, operations)
        return TuningWriteBatchPlan(
            generation = currentGeneration,
            profileFingerprint = profileFingerprint,
            baselineTuneFingerprint = snapshot.fingerprint,
            operations = operations,
            expectedSnapshot = expected
        )
    }

    /**
     * Resolve one semantic edit through the exact same profile/snapshot/codec path used by live
     * execution. This performs no transport and deliberately allows encoded no-ops so callers can
     * present effective-value and no-op information before deciding whether a write is needed.
     */
    fun resolveForPreview(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        request: SemanticTuningWriteRequest,
        currentGeneration: Long
    ): ResolvedTuningWrite {
        require(currentGeneration > 0L) { "Tuning preview requires a positive live generation" }
        require(snapshot.generation == currentGeneration) {
            "Current TuneSnapshot belongs to another USB generation"
        }
        require(profile.signature.isNotBlank() && profile.signature == snapshot.ecuSignature) {
            "Tuning preview profile/ECU signature mismatch"
        }
        val profileFingerprint = profile.tuneProfileFingerprint()
        require(snapshot.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            "Current TuneSnapshot profile identity changed"
        }
        val conditionAuthority = IniConditionAuthority.build(profile, snapshot)
        return resolveRequest(profile, snapshot, request, conditionAuthority)
    }

    private fun resolveRequest(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        request: SemanticTuningWriteRequest,
        conditionAuthority: IniConditionAuthority
    ): ResolvedTuningWrite {
        conditionAuthority.requireWriteAllowed(request)
        return when (request.kind) {
        TuningWriteKind.SCALAR -> resolveScalar(profile, snapshot, request)
        TuningWriteKind.ARRAY_CELL -> resolveArrayCell(profile, snapshot, request)
        TuningWriteKind.BIT_FIELD -> resolveBitField(profile, snapshot, request)
        }
    }

    private fun resolveScalar(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        request: SemanticTuningWriteRequest
    ): ResolvedTuningWrite {
        val matches = profile.tuneScalars.filter { it.name == request.name }
        require(matches.size == 1) {
            if (matches.isEmpty()) "Unknown scalar '${request.name}'" else "Scalar '${request.name}' is ambiguous"
        }
        val definition = matches.single()
        val page = exactPage(profile, snapshot, definition.pageNumber)
        require(definition.byteSize in setOf(1, 2, 4)) {
            "Scalar '${request.name}' uses unsupported primitive ${definition.dataType}"
        }
        require(definition.offset >= 0 && definition.offset + definition.byteSize <= page.second.size) {
            "Scalar '${request.name}' exceeds its current INI page"
        }
        require(definition.accepts(request.requestedValue)) {
            "Requested scalar value ${request.requestedValue} is outside ${definition.low}..${definition.high}"
        }

        val pageBytes = page.second.bytes()
        val before = pageBytes.copyOfRange(definition.offset, definition.offset + definition.byteSize)
        val current = definition.decode(before)
            ?: throw IllegalArgumentException("Unable to decode scalar '${request.name}'")
        val after = TuningScalarCodec.encode(definition, request.requestedValue)
        val effective = definition.decode(after)
            ?: throw IllegalArgumentException("Unable to decode encoded scalar '${request.name}'")
        require(definition.accepts(effective)) {
            "Encoded scalar '${request.name}' falls outside current INI bounds"
        }

        return ResolvedTuningWrite(
            kind = TuningWriteKind.SCALAR,
            name = request.name,
            cellIndex = null,
            pageNumber = definition.pageNumber,
            pageIdentifier = page.first.identifier,
            pageSize = page.first.size,
            offset = definition.offset,
            byteSize = definition.byteSize,
            unit = definition.unit,
            currentValue = current,
            requestedValue = request.requestedValue,
            effectiveValue = effective,
            beforeBytes = before,
            afterBytes = after
        )
    }

    private fun resolveArrayCell(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        request: SemanticTuningWriteRequest
    ): ResolvedTuningWrite {
        val index = request.cellIndex ?: throw IllegalArgumentException("Array cell index is required")
        val definitions = profile.tuneArrays.filter { it.name == request.name }
        require(definitions.isNotEmpty()) { "Unknown array '${request.name}'" }
        require(definitions.drop(1).all { equivalentArrayDefinition(definitions.first(), it) }) {
            "Array '${request.name}' is ambiguous in current INI"
        }
        val definition = definitions.first().copy(digits = definitions.maxOf { it.digits })
        require(definition.byteSize in setOf(1, 2, 4) && definition.elementCount > 0) {
            "Array '${request.name}' has an unsupported primitive or shape"
        }
        require(index in 0 until definition.elementCount) {
            "Array cell $index is outside '${request.name}'"
        }
        require(definition.accepts(request.requestedValue)) {
            "Requested array value ${request.requestedValue} is outside ${definition.low}..${definition.high}"
        }

        val page = exactPage(profile, snapshot, definition.pageNumber)
        val offset = definition.offset + index * definition.byteSize
        require(offset >= 0 && offset + definition.byteSize <= page.second.size) {
            "Array cell '${request.name}[$index]' exceeds its current INI page"
        }

        val pageBytes = page.second.bytes()
        val before = pageBytes.copyOfRange(offset, offset + definition.byteSize)
        val current = definition.decode(before)
            ?: throw IllegalArgumentException("Unable to decode '${request.name}[$index]'")
        val synthetic = UsbTuneScalar(
            name = "${request.name}[$index]",
            pageNumber = definition.pageNumber,
            dataType = definition.dataType,
            offset = offset,
            unit = definition.unit,
            scale = definition.scale,
            translate = definition.translate,
            low = definition.low,
            high = definition.high,
            digits = definition.digits
        )
        val after = TuningScalarCodec.encode(synthetic, request.requestedValue)
        val effective = definition.decode(after)
            ?: throw IllegalArgumentException("Unable to decode encoded '${request.name}[$index]'")
        require(definition.accepts(effective)) {
            "Encoded array value '${request.name}[$index]' falls outside current INI bounds"
        }

        return ResolvedTuningWrite(
            kind = TuningWriteKind.ARRAY_CELL,
            name = request.name,
            cellIndex = index,
            pageNumber = definition.pageNumber,
            pageIdentifier = page.first.identifier,
            pageSize = page.first.size,
            offset = offset,
            byteSize = definition.byteSize,
            unit = definition.unit,
            currentValue = current,
            requestedValue = request.requestedValue,
            effectiveValue = effective,
            beforeBytes = before,
            afterBytes = after
        )
    }

    private fun resolveBitField(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        request: SemanticTuningWriteRequest
    ): ResolvedTuningWrite {
        val definitions = profile.tuneBitFields.filter { it.name == request.name }
        require(definitions.isNotEmpty()) { "Unknown bit/enum field '${request.name}'" }
        require(definitions.drop(1).all { equivalentBitFieldDefinition(definitions.first(), it) }) {
            "Bit/enum field '${request.name}' is ambiguous in current INI"
        }
        val definition = definitions.first()
        require(definition.byteSize in setOf(1, 2, 4)) {
            "Bit/enum field '${request.name}' uses unsupported storage primitive ${definition.dataType}"
        }
        val width = definition.bitEnd - definition.bitStart + 1
        require(definition.bitStart >= 0 && width in 1..31 && definition.bitEnd < definition.byteSize * 8) {
            "Bit/enum field '${request.name}' uses unsupported bit range [${definition.bitStart}:${definition.bitEnd}]"
        }

        val requestedDouble = request.requestedValue
        require(requestedDouble.isFinite() && requestedDouble == requestedDouble.toLong().toDouble()) {
            "Bit/enum field '${request.name}' requires an integer semantic value"
        }
        val requestedLong = requestedDouble.toLong()
        val maxValue = (1L shl width) - 1L
        require(requestedLong in 0L..maxValue) {
            "Requested bit/enum value $requestedLong is outside 0..$maxValue for '${request.name}'"
        }
        val requested = requestedLong.toInt()
        if (definition.options.isNotEmpty()) {
            require(definition.options.any { it.value == requested }) {
                "Requested bit/enum value $requested is not a defined option for '${request.name}'"
            }
        }

        val page = exactPage(profile, snapshot, definition.pageNumber)
        require(definition.offset >= 0 && definition.offset + definition.byteSize <= page.second.size) {
            "Bit/enum field '${request.name}' exceeds its current INI page"
        }

        val pageBytes = page.second.bytes()
        val before = pageBytes.copyOfRange(definition.offset, definition.offset + definition.byteSize)
        val current = definition.decode(before)
            ?: throw IllegalArgumentException("Unable to decode bit/enum field '${request.name}'")

        var storage = 0L
        before.forEachIndexed { index, byte ->
            storage = storage or ((byte.toInt() and 0xff).toLong() shl (index * 8))
        }
        val valueMask = (1L shl width) - 1L
        val shiftedMask = valueMask shl definition.bitStart
        val patchedStorage = (storage and shiftedMask.inv()) or
            ((requested.toLong() and valueMask) shl definition.bitStart)
        val after = ByteArray(definition.byteSize) { index ->
            ((patchedStorage ushr (index * 8)) and 0xffL).toByte()
        }
        val effective = definition.decode(after)
            ?: throw IllegalArgumentException("Unable to decode encoded bit/enum field '${request.name}'")
        require(effective == requested) {
            "Encoded bit/enum field '${request.name}' does not equal requested semantic value"
        }

        return ResolvedTuningWrite(
            kind = TuningWriteKind.BIT_FIELD,
            name = request.name,
            cellIndex = null,
            pageNumber = definition.pageNumber,
            pageIdentifier = page.first.identifier,
            pageSize = page.first.size,
            offset = definition.offset,
            byteSize = definition.byteSize,
            unit = "",
            currentValue = current.toDouble(),
            requestedValue = requested.toDouble(),
            effectiveValue = effective.toDouble(),
            beforeBytes = before,
            afterBytes = after
        )
    }

    private fun exactPage(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        pageNumber: Int
    ): Pair<UsbTunePage, TunePageSnapshot> {
        val page = profile.tunePages.singleOrNull { it.pageNumber == pageNumber }
            ?: throw IllegalArgumentException("Tune page $pageNumber is missing or ambiguous")
        require(page.readCommand == UsbTuneReadCodec.SUPPORTED_READ_COMMAND) {
            "Tune page $pageNumber does not expose the supported R command"
        }
        val snapshotPage = snapshot.pages.singleOrNull { it.pageNumber == pageNumber }
            ?: throw IllegalArgumentException("TuneSnapshot page $pageNumber is missing or ambiguous")
        require(snapshotPage.identifier == page.identifier && snapshotPage.size == page.size) {
            "TuneSnapshot page $pageNumber identity differs from current INI"
        }
        return page to snapshotPage
    }

    private fun patchSnapshot(
        baseline: TuneSnapshot,
        operations: List<ResolvedTuningWrite>
    ): TuneSnapshot {
        val byPage = operations.groupBy { it.pageNumber }
        val pages = baseline.pages.map { page ->
            val bytes = page.bytes()
            byPage[page.pageNumber].orEmpty().forEach { operation ->
                require(operation.pageIdentifier == page.identifier && operation.pageSize == page.size) {
                    "Tuning write page identity changed while building expected snapshot"
                }
                require(operation.offset >= 0 && operation.offset + operation.byteSize <= bytes.size) {
                    "Tuning write range exceeds expected snapshot page"
                }
                val current = bytes.copyOfRange(operation.offset, operation.offset + operation.byteSize)
                require(current.contentEquals(operation.beforeBytes())) {
                    "Tuning write baseline bytes changed before expected snapshot construction"
                }
                operation.afterBytes().copyInto(bytes, operation.offset)
            }
            TunePageSnapshot(page.pageNumber, page.identifier, page.size, page.readCommand, bytes)
        }
        return TuneSnapshot.create(
            ecuSignature = baseline.ecuSignature,
            profileFingerprint = baseline.profileFingerprint,
            pages = pages,
            generation = baseline.generation,
            capturedAtEpochMs = baseline.capturedAtEpochMs
        )
    }

    private fun equivalentBitFieldDefinition(left: UsbTuneBitField, right: UsbTuneBitField): Boolean =
        left.pageNumber == right.pageNumber &&
            left.dataType.equals(right.dataType, ignoreCase = true) &&
            left.offset == right.offset &&
            left.bitStart == right.bitStart &&
            left.bitEnd == right.bitEnd &&
            left.options == right.options

    private fun equivalentArrayDefinition(left: UsbTuneArray, right: UsbTuneArray): Boolean =
        left.pageNumber == right.pageNumber &&
            left.dataType.equals(right.dataType, ignoreCase = true) &&
            left.offset == right.offset &&
            left.dimensions == right.dimensions &&
            left.unit == right.unit &&
            java.lang.Double.doubleToLongBits(left.scale) == java.lang.Double.doubleToLongBits(right.scale) &&
            java.lang.Double.doubleToLongBits(left.translate) == java.lang.Double.doubleToLongBits(right.translate) &&
            java.lang.Double.doubleToLongBits(left.low) == java.lang.Double.doubleToLongBits(right.low) &&
            java.lang.Double.doubleToLongBits(left.high) == java.lang.Double.doubleToLongBits(right.high)
}

internal object TuningWriteProtocol {
    fun writeBody(operation: ResolvedTuningWrite): ByteArray =
        rangeBody('C'.code.toByte(), operation) + operation.afterBytes()

    fun readBackBody(operation: ResolvedTuningWrite): ByteArray =
        rangeBody('R'.code.toByte(), operation)

    fun requireWriteAck(body: ByteArray) {
        val decoded = EcuCommandResponseCodec.decodeBody(body)
        require(decoded.responseCode == 0x00) {
            "ECU rejected tuning RAM write with response 0x${decoded.responseCode.toString(16)}"
        }
        require(decoded.payload.isEmpty()) { "Tuning RAM write acknowledgement contains unexpected payload" }
    }

    fun requireReadBack(body: ByteArray, operation: ResolvedTuningWrite) {
        val decoded = EcuCommandResponseCodec.decodeBody(body)
        require(decoded.responseCode == 0x00) {
            "Tuning read-back failed with response 0x${decoded.responseCode.toString(16)}"
        }
        require(decoded.payload.size == operation.byteSize) {
            "Tuning read-back width differs from current INI target"
        }
        require(decoded.payload.contentEquals(operation.afterBytes())) {
            "Tuning read-back does not exactly match the requested encoded value"
        }
    }

    private fun rangeBody(command: Byte, operation: ResolvedTuningWrite): ByteArray =
        byteArrayOf(command) +
            u16Le(operation.pageIdentifier) +
            u16Le(operation.offset) +
            u16Le(operation.byteSize)

    private fun u16Le(value: Int): ByteArray {
        require(value in 0..0xffff) { "Tuning protocol field is outside U16 range" }
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte()
        )
    }
}
