package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.Collections
import java.util.IdentityHashMap

/** Immutable copy of one complete calibration page. */
class TunePageSnapshot(
    val pageNumber: Int,
    val identifier: Int,
    val size: Int,
    val readCommand: String,
    bytes: ByteArray
) {
    private val data = bytes.copyOf()

    init {
        require(pageNumber > 0) { "Tune page number must be positive" }
        require(identifier in 0..0xffff) { "Invalid tune page identifier $identifier" }
        require(size > 0) { "Tune page size must be positive" }
        require(data.size == size) { "Tune page $pageNumber incomplete: ${data.size}/$size bytes" }
        require(readCommand == UsbTuneReadCodec.SUPPORTED_READ_COMMAND) {
            "Unsupported tune page read command '$readCommand'"
        }
    }

    fun bytes(): ByteArray = data.copyOf()

    internal fun updateDigest(digest: MessageDigest) {
        digest.updateInt(pageNumber)
        digest.updateInt(identifier)
        digest.updateInt(size)
        digest.updateUtf8(readCommand)
        digest.update(data)
    }

    fun toBackupJson(): JSONObject = JSONObject()
        .put("pageNumber", pageNumber)
        .put("identifier", identifier)
        .put("size", size)
        .put("readCommand", readCommand)
        .put("sha256", sha256Hex(data))
        .put("bytesBase64", Base64.getEncoder().encodeToString(data))

    companion object {
        fun fromBackupJson(json: JSONObject): TunePageSnapshot {
            val bytes = Base64.getDecoder().decode(json.getString("bytesBase64"))
            val page = TunePageSnapshot(
                pageNumber = json.getInt("pageNumber"),
                identifier = json.getInt("identifier"),
                size = json.getInt("size"),
                readCommand = json.getString("readCommand"),
                bytes = bytes
            )
            require(sha256Hex(bytes).equals(json.getString("sha256"), ignoreCase = true)) {
                "Tune backup page ${page.pageNumber} checksum mismatch"
            }
            return page
        }
    }
}

data class TunePageDiff(
    val pageNumber: Int,
    val identifier: Int,
    val changedBytes: Int,
    val firstChangedOffset: Int,
    val lastChangedOffset: Int
)

data class TuneSnapshotComparison(
    val identityMatches: Boolean,
    val tuneMatches: Boolean,
    val pageDiffs: List<TunePageDiff>
)

/**
 * Canonical, read-only tune image. Volatile capture metadata is deliberately excluded from the
 * tune fingerprint so repeated reads of unchanged ECU state hash identically.
 */
class TuneSnapshot private constructor(
    val ecuSignature: String,
    val profileFingerprint: String,
    pages: List<TunePageSnapshot>,
    val generation: Long,
    val capturedAtEpochMs: Long
) {
    private val pageImages = pages.sortedBy { it.pageNumber }.toList()
    val pages: List<TunePageSnapshot> get() = pageImages.toList()
    val totalBytes: Int = pageImages.sumOf { it.size }
    val fingerprint: String = computeFingerprint()

    init {
        require(ecuSignature.isNotBlank()) { "ECU signature is required for TuneSnapshot" }
        require(profileFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid profile fingerprint" }
        require(pageImages.isNotEmpty()) { "TuneSnapshot requires at least one page" }
        require(pageImages.map { it.pageNumber }.distinct().size == pageImages.size) { "Duplicate tune page numbers" }
        require(pageImages.map { it.identifier }.distinct().size == pageImages.size) { "Duplicate tune page identifiers" }
    }

    private fun computeFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateUtf8("EpicDashTuneSnapshot/v1")
        digest.updateUtf8(ecuSignature)
        digest.updateUtf8(profileFingerprint.lowercase(Locale.US))
        digest.updateInt(pageImages.size)
        pageImages.forEach { it.updateDigest(digest) }
        return digest.digest().toHex()
    }

    fun compare(other: TuneSnapshot): TuneSnapshotComparison {
        val identityMatches = ecuSignature == other.ecuSignature &&
            profileFingerprint.equals(other.profileFingerprint, ignoreCase = true) &&
            pageImages.map { Triple(it.pageNumber, it.identifier, it.size) } ==
            other.pageImages.map { Triple(it.pageNumber, it.identifier, it.size) }
        if (!identityMatches) return TuneSnapshotComparison(false, false, emptyList())

        val diffs = mutableListOf<TunePageDiff>()
        pageImages.zip(other.pageImages).forEach { (left, right) ->
            val a = left.bytes()
            val b = right.bytes()
            var changed = 0
            var first = -1
            var last = -1
            for (index in a.indices) {
                if (a[index] != b[index]) {
                    if (first < 0) first = index
                    last = index
                    changed++
                }
            }
            if (changed > 0) {
                diffs += TunePageDiff(left.pageNumber, left.identifier, changed, first, last)
            }
        }
        return TuneSnapshotComparison(true, diffs.isEmpty(), diffs)
    }

    fun toBackupJson(): JSONObject = JSONObject()
        .put("schema", "EpicDashTuneSnapshot/v1")
        .put("ecuSignature", ecuSignature)
        .put("profileFingerprint", profileFingerprint.lowercase(Locale.US))
        .put("tuneFingerprint", fingerprint)
        .put("generation", generation)
        .put("capturedAtEpochMs", capturedAtEpochMs)
        .put("totalBytes", totalBytes)
        .put("pages", JSONArray().also { array -> pageImages.forEach { array.put(it.toBackupJson()) } })

    companion object {
        fun create(
            ecuSignature: String,
            profileFingerprint: String,
            pages: List<TunePageSnapshot>,
            generation: Long,
            capturedAtEpochMs: Long
        ): TuneSnapshot = TuneSnapshot(ecuSignature, profileFingerprint, pages, generation, capturedAtEpochMs)

        fun fromBackupJson(json: JSONObject): TuneSnapshot {
            require(json.getString("schema") == "EpicDashTuneSnapshot/v1") { "Unsupported tune backup schema" }
            val pageArray = json.getJSONArray("pages")
            val pages = (0 until pageArray.length()).map { TunePageSnapshot.fromBackupJson(pageArray.getJSONObject(it)) }
            val snapshot = TuneSnapshot(
                ecuSignature = json.getString("ecuSignature"),
                profileFingerprint = json.getString("profileFingerprint"),
                pages = pages,
                generation = json.optLong("generation", -1L),
                capturedAtEpochMs = json.optLong("capturedAtEpochMs", 0L)
            )
            require(snapshot.totalBytes == json.getInt("totalBytes")) { "Tune backup total byte count mismatch" }
            require(snapshot.fingerprint.equals(json.getString("tuneFingerprint"), ignoreCase = true)) {
                "Tune backup fingerprint mismatch"
            }
            return snapshot
        }
    }
}

/** Deterministic identity for the tune-relevant parsed profile schema. */
private val tuneProfileFingerprintCache = Collections.synchronizedMap(
    IdentityHashMap<UsbTunerStudioProfile, String>()
)

fun UsbTunerStudioProfile.tuneProfileFingerprint(): String {
    tuneProfileFingerprintCache[this]?.let { return it }
    val digest = MessageDigest.getInstance("SHA-256")
    digest.updateUtf8("EpicDashTuneProfile/v3")
    digest.updateUtf8(signature)
    digest.updateUtf8(envelopeFormat.lowercase(Locale.US))
    digest.updateUtf8(endianness.lowercase(Locale.US))

    val pages = tunePages.sortedBy { it.pageNumber }
    digest.updateInt(pages.size)
    pages.forEach {
        digest.updateInt(it.pageNumber)
        digest.updateInt(it.identifier)
        digest.updateInt(it.size)
        digest.updateUtf8(it.readCommand)
        digest.updateUtf8(it.burnCommand)
    }

    val scalars = tuneScalars.sortedWith(compareBy<UsbTuneScalar> { it.pageNumber }.thenBy { it.offset }.thenBy { it.name })
    digest.updateInt(scalars.size)
    scalars.forEach {
        digest.updateUtf8(it.name)
        digest.updateInt(it.pageNumber)
        digest.updateUtf8(it.dataType.uppercase(Locale.US))
        digest.updateInt(it.offset)
        digest.updateUtf8(it.unit)
        digest.updateDouble(it.scale)
        digest.updateDouble(it.translate)
        digest.updateDouble(it.low)
        digest.updateDouble(it.high)
        digest.updateInt(it.digits)
    }

    val bitFields = tuneBitFields.sortedWith(compareBy<UsbTuneBitField> { it.pageNumber }.thenBy { it.offset }.thenBy { it.bitStart }.thenBy { it.name })
    digest.updateInt(bitFields.size)
    bitFields.forEach {
        digest.updateUtf8(it.name)
        digest.updateInt(it.pageNumber)
        digest.updateUtf8(it.dataType.uppercase(Locale.US))
        digest.updateInt(it.offset)
        digest.updateInt(it.bitStart)
        digest.updateInt(it.bitEnd)
        digest.updateInt(it.options.size)
        it.options.forEach { option ->
            digest.updateInt(option.value)
            digest.updateUtf8(option.label)
        }
    }

    val arrays = tuneArrays.sortedWith(compareBy<UsbTuneArray> { it.pageNumber }.thenBy { it.offset }.thenBy { it.name })
    digest.updateInt(arrays.size)
    arrays.forEach {
        digest.updateUtf8(it.name)
        digest.updateInt(it.pageNumber)
        digest.updateUtf8(it.dataType.uppercase(Locale.US))
        digest.updateInt(it.offset)
        digest.updateInt(it.dimensions.size)
        it.dimensions.forEach(digest::updateInt)
        digest.updateUtf8(it.unit)
        digest.updateDouble(it.scale)
        digest.updateDouble(it.translate)
        digest.updateDouble(it.low)
        digest.updateDouble(it.high)
        digest.updateInt(it.digits)
    }
    val tables = tuneTables.sortedBy { it.id }
    digest.updateInt(tables.size)
    tables.forEach {
        digest.updateUtf8(it.id)
        digest.updateUtf8(it.mapId)
        digest.updateUtf8(it.title)
        digest.updateInt(it.page)
        digest.updateUtf8(it.xBins)
        digest.updateUtf8(it.yBins)
        digest.updateUtf8(it.zBins)
    }

    val curves = tuneCurves.sortedBy { it.id }
    digest.updateInt(curves.size)
    curves.forEach {
        digest.updateUtf8(it.id)
        digest.updateUtf8(it.title)
        digest.updateUtf8(it.xBins)
        digest.updateInt(it.yBins.size)
        it.yBins.forEach { yBin -> digest.updateUtf8(yBin) }
    }

    return digest.digest().toHex().also { fingerprint ->
        tuneProfileFingerprintCache[this] = fingerprint
    }
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
private fun MessageDigest.updateUtf8(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    updateInt(bytes.size)
    update(bytes)
}
private fun MessageDigest.updateInt(value: Int) {
    update(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
}
private fun MessageDigest.updateDouble(value: Double) {
    update(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(java.lang.Double.doubleToLongBits(value)).array())
}
