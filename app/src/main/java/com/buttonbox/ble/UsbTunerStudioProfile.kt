package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Parsed read-only subset of a TunerStudio mainController.ini. */
data class UsbOutputChannel(
    val name: String,
    val kind: String,
    val dataType: String,
    val offset: Int,
    val unit: String,
    val scale: Double,
    val translate: Double,
    val bitStart: Int = -1,
    val bitEnd: Int = -1
) {
    private val normalizedType: String = dataType.uppercase(Locale.US)
    val byteSize: Int = when (normalizedType) {
        "U08", "S08" -> 1
        "U16", "S16" -> 2
        "U32", "S32", "F32" -> 4
        else -> 0
    }

    /** Direct little-endian primitive decode with no per-frame ByteBuffer allocation. */
    fun rawNumeric(block: ByteArray): Double? {
        if (offset < 0 || byteSize <= 0 || offset + byteSize > block.size) return null
        fun u8(index: Int): Int = block[index].toInt() and 0xff
        val value = when (normalizedType) {
            "U08" -> u8(offset).toDouble()
            "S08" -> block[offset].toDouble()
            "U16" -> (u8(offset) or (u8(offset + 1) shl 8)).toDouble()
            "S16" -> (u8(offset) or (u8(offset + 1) shl 8)).toShort().toDouble()
            "U32" -> (
                u8(offset).toLong() or
                    (u8(offset + 1).toLong() shl 8) or
                    (u8(offset + 2).toLong() shl 16) or
                    (u8(offset + 3).toLong() shl 24)
                ).toDouble()
            "S32" -> (
                u8(offset) or
                    (u8(offset + 1) shl 8) or
                    (u8(offset + 2) shl 16) or
                    (u8(offset + 3) shl 24)
                ).toDouble()
            "F32" -> Float.fromBits(
                u8(offset) or
                    (u8(offset + 1) shl 8) or
                    (u8(offset + 2) shl 16) or
                    (u8(offset + 3) shl 24)
            ).toDouble()
            else -> return null
        }
        return value.takeIf { it.isFinite() }
    }

    fun rawBytes(block: ByteArray): ByteArray? =
        if (offset < 0 || byteSize <= 0 || offset + byteSize > block.size) null
        else block.copyOfRange(offset, offset + byteSize)

    fun decode(block: ByteArray): Double? {
        val base = rawNumeric(block) ?: return null
        val value = if (kind == "bits") {
            val low = bitStart.coerceAtLeast(0)
            val high = bitEnd.coerceAtLeast(low)
            val width = (high - low + 1).coerceIn(1, 31)
            val mask = if (width == 31) 0x7fffffffL else (1L shl width) - 1L
            ((base.toLong() ushr low) and mask).toDouble()
        } else base * scale + translate
        return value.takeIf { it.isFinite() }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("kind", kind).put("dataType", dataType)
        .put("offset", offset).put("unit", unit).put("scale", scale)
        .put("translate", translate).put("bitStart", bitStart).put("bitEnd", bitEnd)

    companion object {
        fun fromJson(json: JSONObject) = UsbOutputChannel(
            name = json.getString("name"), kind = json.optString("kind", "scalar"),
            dataType = json.getString("dataType"), offset = json.getInt("offset"),
            unit = json.optString("unit"), scale = json.optDouble("scale", 1.0),
            translate = json.optDouble("translate", 0.0),
            bitStart = json.optInt("bitStart", -1), bitEnd = json.optInt("bitEnd", -1)
        )
    }
}

data class UsbTunerStudioProfile(
    val signature: String,
    val queryCommand: String,
    val outputCommand: String,
    val outputBlockSize: Int,
    val envelopeFormat: String,
    val endianness: String,
    val channels: List<UsbOutputChannel>,
    val importedName: String = "mainController.ini"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("signature", signature).put("queryCommand", queryCommand)
        .put("outputCommand", outputCommand).put("outputBlockSize", outputBlockSize)
        .put("envelopeFormat", envelopeFormat).put("endianness", endianness)
        .put("importedName", importedName)
        .put("channels", JSONArray().also { array -> channels.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTunerStudioProfile {
            val list = mutableListOf<UsbOutputChannel>()
            val array = json.optJSONArray("channels") ?: JSONArray()
            for (index in 0 until array.length()) list += UsbOutputChannel.fromJson(array.getJSONObject(index))
            return UsbTunerStudioProfile(
                signature = json.optString("signature"), queryCommand = json.optString("queryCommand", "S"),
                outputCommand = json.optString("outputCommand", "O%2o%2c"),
                outputBlockSize = json.optInt("outputBlockSize"),
                envelopeFormat = json.optString("envelopeFormat", "msEnvelope_1.0"),
                endianness = json.optString("endianness", "little"), channels = list,
                importedName = json.optString("importedName", "mainController.ini")
            )
        }
    }
}

object UsbTunerStudioProfileParser {
    private val scalarRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*scalar\\s*,\\s*(U08|S08|U16|S16|U32|S32|F32)\\s*,\\s*(\\d+)\\s*,\\s*\"([^\"]*)\"\\s*,\\s*([^,]+)\\s*,\\s*([^,;]+)",
        RegexOption.IGNORE_CASE
    )
    private val bitsRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*bits\\s*,\\s*(U08|S08|U16|S16|U32|S32)\\s*,\\s*(\\d+)\\s*,\\s*\\[(\\d+)\\s*:\\s*(\\d+)\\]",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String, importedName: String = "mainController.ini"): UsbTunerStudioProfile {
        var section = ""
        var signature = ""
        var query = "S"
        var output = "O%2o%2c"
        var blockSize = 0
        var envelope = ""
        var endianness = ""
        val channels = mutableListOf<UsbOutputChannel>()
        text.lineSequence().forEach { rawLine ->
            val line = stripComment(rawLine).trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase(Locale.US)
                return@forEach
            }
            when (section) {
                "tunerstudio", "megatune" -> {
                    valueAfterEquals(line, "signature")?.let { if (it.length >= signature.length) signature = unquote(it) }
                    valueAfterEquals(line, "queryCommand")?.let { query = decodeIniString(unquote(it)) }
                }
                "constants" -> {
                    valueAfterEquals(line, "messageEnvelopeFormat")?.let { envelope = unquote(it) }
                    valueAfterEquals(line, "endianness")?.let { endianness = unquote(it) }
                }
                "outputchannels" -> {
                    valueAfterEquals(line, "ochGetCommand")?.let { output = unquote(it) }
                    valueAfterEquals(line, "ochBlockSize")?.toIntOrNull()?.let { blockSize = it }
                    scalarRegex.find(line)?.let { match ->
                        channels += UsbOutputChannel(
                            name = match.groupValues[1], kind = "scalar",
                            dataType = match.groupValues[2].uppercase(Locale.US),
                            offset = match.groupValues[3].toInt(), unit = match.groupValues[4],
                            scale = parseNumber(match.groupValues[5], 1.0),
                            translate = parseNumber(match.groupValues[6], 0.0)
                        )
                    } ?: bitsRegex.find(line)?.let { match ->
                        channels += UsbOutputChannel(
                            name = match.groupValues[1], kind = "bits",
                            dataType = match.groupValues[2].uppercase(Locale.US),
                            offset = match.groupValues[3].toInt(), unit = "", scale = 1.0, translate = 0.0,
                            bitStart = match.groupValues[4].toInt(), bitEnd = match.groupValues[5].toInt()
                        )
                    }
                }
            }
        }
        require(blockSize in 64..65535) { "No valid ochBlockSize found in [OutputChannels]" }
        require(channels.isNotEmpty()) { "No scalar/bit output channels found in [OutputChannels]" }
        require(envelope.equals("msEnvelope_1.0", ignoreCase = true)) {
            "Unsupported message envelope '$envelope' (expected msEnvelope_1.0)"
        }
        require(endianness.equals("little", ignoreCase = true)) { "Only little-endian output channels are supported" }
        require(query.isNotEmpty()) { "TunerStudio queryCommand is empty" }
        require(output.startsWith("O")) { "Unsupported ochGetCommand '$output'" }
        return UsbTunerStudioProfile(signature, query, output, blockSize, envelope, endianness, channels, importedName)
    }

    private fun stripComment(line: String): String {
        var quoted = false
        line.forEachIndexed { index, ch ->
            if (ch == '"' && (index == 0 || line[index - 1] != '\\')) quoted = !quoted
            if (ch == ';' && !quoted) return line.substring(0, index)
        }
        return line
    }

    private fun valueAfterEquals(line: String, key: String): String? {
        val match = Regex("^${Regex.escape(key)}\\s*=\\s*(.+)$", RegexOption.IGNORE_CASE).find(line) ?: return null
        return match.groupValues[1].trim()
    }

    private fun unquote(value: String): String = value.trim().removeSurrounding("\"")
    private fun decodeIniString(value: String): String = value
        .replace("\\x00", "\u0000", ignoreCase = true)
        .replace("\\n", "\n").replace("\\r", "\r")
    private fun parseNumber(value: String, fallback: Double): Double = value.trim().toDoubleOrNull() ?: fallback

    fun selfTest(): JSONObject {
        val sample = """
            [TunerStudio]
            queryCommand = "S"
            signature = "rusEFI test"
            [Constants]
            messageEnvelopeFormat = msEnvelope_1.0
            endianness = little
            [OutputChannels]
            ochGetCommand = "O%2o%2c"
            ochBlockSize = 16
            RPMValue = scalar, U16, 4, "RPM", 1, 0
            VBatt = scalar, U16, 8, "V", 3.333333333333333E-4, 0
            targetAFR = scalar, U16, 10, "ratio", 0.001, 0
            ready = bits, U32, 0, [2:2]
        """.trimIndent().replace("ochBlockSize = 16", "ochBlockSize = 64")
        return try {
            val profile = parse(sample, "self-test.ini")
            val block = ByteArray(64)
            block[0] = 4
            block[4] = 0x34
            block[5] = 0x12
            block[8] = 0x75
            block[9] = 0x8A.toByte()
            block[10] = 0x6C
            block[11] = 0x37
            val rpm = profile.channels.first { it.name == "RPMValue" }.decode(block)
            val batt = profile.channels.first { it.name == "VBatt" }.decode(block)
            val afrTarget = profile.channels.first { it.name == "targetAFR" }.decode(block)
            val ready = profile.channels.first { it.name == "ready" }.decode(block)
            val passed = rpm == 4660.0 && ready == 1.0 &&
                batt != null && kotlin.math.abs(batt - 11.815) < 0.0001 &&
                afrTarget != null && kotlin.math.abs(afrTarget - 14.188) < 0.0001
            JSONObject().put("passed", passed)
                .put("channels", profile.channels.size).put("blockSize", profile.outputBlockSize)
                .put("scientificScaleVoltage", batt ?: JSONObject.NULL)
                .put("caseSensitiveTargetAFR", afrTarget ?: JSONObject.NULL)
        } catch (error: Exception) {
            JSONObject().put("passed", false).put("error", error.message)
        }
    }
}
