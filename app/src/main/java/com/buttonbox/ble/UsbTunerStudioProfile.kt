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

/** One TunerStudio calibration page. Page numbers are the 1-based INI page numbers. */
data class UsbTunePage(
    val pageNumber: Int,
    val identifier: Int,
    val size: Int,
    val readCommand: String,
    val burnCommand: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("pageNumber", pageNumber)
        .put("identifier", identifier)
        .put("size", size)
        .put("readCommand", readCommand)
        .put("burnCommand", burnCommand)

    companion object {
        fun fromJson(json: JSONObject) = UsbTunePage(
            pageNumber = json.getInt("pageNumber"),
            identifier = json.getInt("identifier"),
            size = json.getInt("size"),
            readCommand = json.getString("readCommand"),
            burnCommand = json.optString("burnCommand")
        )
    }
}

/** Read-only scalar metadata parsed from a calibration page in mainController.ini. */
data class UsbTuneScalar(
    val name: String,
    val pageNumber: Int,
    val dataType: String,
    val offset: Int,
    val unit: String,
    val scale: Double,
    val translate: Double,
    val low: Double,
    val high: Double,
    val digits: Int
) {
    private val normalizedType: String = dataType.uppercase(Locale.US)
    val byteSize: Int = when (normalizedType) {
        "U08", "S08" -> 1
        "U16", "S16" -> 2
        "U32", "S32", "F32" -> 4
        else -> 0
    }

    fun rawNumeric(raw: ByteArray): Double? {
        if (byteSize <= 0 || raw.size != byteSize) return null
        fun u8(index: Int): Int = raw[index].toInt() and 0xff
        val value = when (normalizedType) {
            "U08" -> u8(0).toDouble()
            "S08" -> raw[0].toDouble()
            "U16" -> (u8(0) or (u8(1) shl 8)).toDouble()
            "S16" -> (u8(0) or (u8(1) shl 8)).toShort().toDouble()
            "U32" -> (
                u8(0).toLong() or
                    (u8(1).toLong() shl 8) or
                    (u8(2).toLong() shl 16) or
                    (u8(3).toLong() shl 24)
                ).toDouble()
            "S32" -> (
                u8(0) or
                    (u8(1) shl 8) or
                    (u8(2) shl 16) or
                    (u8(3) shl 24)
                ).toDouble()
            "F32" -> Float.fromBits(
                u8(0) or
                    (u8(1) shl 8) or
                    (u8(2) shl 16) or
                    (u8(3) shl 24)
            ).toDouble()
            else -> return null
        }
        return value.takeIf { it.isFinite() }
    }

    fun decode(raw: ByteArray): Double? = rawNumeric(raw)
        ?.let { it * scale + translate }
        ?.takeIf { it.isFinite() }

    fun accepts(value: Double): Boolean = value.isFinite() && value >= low && value <= high

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("pageNumber", pageNumber)
        .put("dataType", dataType)
        .put("offset", offset)
        .put("unit", unit)
        .put("scale", scale)
        .put("translate", translate)
        .put("low", low)
        .put("high", high)
        .put("digits", digits)

    companion object {
        fun fromJson(json: JSONObject) = UsbTuneScalar(
            name = json.getString("name"),
            pageNumber = json.getInt("pageNumber"),
            dataType = json.getString("dataType"),
            offset = json.getInt("offset"),
            unit = json.optString("unit"),
            scale = json.optDouble("scale", 1.0),
            translate = json.optDouble("translate", 0.0),
            low = json.optDouble("low", Double.NEGATIVE_INFINITY),
            high = json.optDouble("high", Double.POSITIVE_INFINITY),
            digits = json.optInt("digits", 0)
        )
    }
}


/** Read-only array metadata parsed from a calibration page in mainController.ini. */
data class UsbTuneArray(
    val name: String,
    val pageNumber: Int,
    val dataType: String,
    val offset: Int,
    val dimensions: List<Int>,
    val unit: String,
    val scale: Double,
    val translate: Double,
    val low: Double,
    val high: Double,
    val digits: Int
) {
    private val normalizedType: String = dataType.uppercase(Locale.US)
    val byteSize: Int = when (normalizedType) {
        "U08", "S08" -> 1
        "U16", "S16" -> 2
        "U32", "S32", "F32" -> 4
        else -> 0
    }
    val elementCount: Int = dimensions.fold(1L) { product, dimension ->
        if (dimension <= 0 || product > Int.MAX_VALUE.toLong() / dimension.toLong()) Int.MAX_VALUE.toLong() + 1L
        else product * dimension.toLong()
    }.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
    val totalByteSize: Int = if (byteSize <= 0 || elementCount <= 0 ||
        elementCount.toLong() * byteSize.toLong() > Int.MAX_VALUE.toLong()
    ) 0 else elementCount * byteSize

    fun rawNumeric(raw: ByteArray): Double? {
        if (byteSize <= 0 || raw.size != byteSize) return null
        fun u8(index: Int): Int = raw[index].toInt() and 0xff
        val value = when (normalizedType) {
            "U08" -> u8(0).toDouble()
            "S08" -> raw[0].toDouble()
            "U16" -> (u8(0) or (u8(1) shl 8)).toDouble()
            "S16" -> (u8(0) or (u8(1) shl 8)).toShort().toDouble()
            "U32" -> (
                u8(0).toLong() or
                    (u8(1).toLong() shl 8) or
                    (u8(2).toLong() shl 16) or
                    (u8(3).toLong() shl 24)
                ).toDouble()
            "S32" -> (
                u8(0) or
                    (u8(1) shl 8) or
                    (u8(2) shl 16) or
                    (u8(3) shl 24)
                ).toDouble()
            "F32" -> Float.fromBits(
                u8(0) or
                    (u8(1) shl 8) or
                    (u8(2) shl 16) or
                    (u8(3) shl 24)
            ).toDouble()
            else -> return null
        }
        return value.takeIf { it.isFinite() }
    }

    fun decode(raw: ByteArray): Double? = rawNumeric(raw)
        ?.let { it * scale + translate }
        ?.takeIf { it.isFinite() }

    fun accepts(value: Double): Boolean = value.isFinite() && value >= low && value <= high

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("pageNumber", pageNumber)
        .put("dataType", dataType)
        .put("offset", offset)
        .put("dimensions", JSONArray().also { array -> dimensions.forEach { array.put(it) } })
        .put("unit", unit)
        .put("scale", scale)
        .put("translate", translate)
        .put("low", low)
        .put("high", high)
        .put("digits", digits)

    companion object {
        fun fromJson(json: JSONObject): UsbTuneArray {
            val dimsJson = json.optJSONArray("dimensions") ?: JSONArray()
            val dims = (0 until dimsJson.length()).map { dimsJson.getInt(it) }
            return UsbTuneArray(
                name = json.getString("name"),
                pageNumber = json.getInt("pageNumber"),
                dataType = json.getString("dataType"),
                offset = json.getInt("offset"),
                dimensions = dims,
                unit = json.optString("unit"),
                scale = json.optDouble("scale", 1.0),
                translate = json.optDouble("translate", 0.0),
                low = json.optDouble("low", Double.NEGATIVE_INFINITY),
                high = json.optDouble("high", Double.POSITIVE_INFINITY),
                digits = json.optInt("digits", 0)
            )
        }
    }
}


data class UsbTuneTableEditor(
    val id: String,
    val mapId: String,
    val title: String,
    val page: Int,
    val xBins: String,
    val yBins: String,
    val zBins: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("mapId", mapId)
        .put("title", title)
        .put("page", page)
        .put("xBins", xBins)
        .put("yBins", yBins)
        .put("zBins", zBins)

    companion object {
        fun fromJson(json: JSONObject) = UsbTuneTableEditor(
            id = json.getString("id"),
            mapId = json.optString("mapId"),
            title = json.optString("title"),
            page = json.optInt("page", 1),
            xBins = json.getString("xBins"),
            yBins = json.getString("yBins"),
            zBins = json.getString("zBins")
        )
    }
}

data class UsbTuneCurveEditor(
    val id: String,
    val title: String,
    val xBins: String,
    val yBins: List<String>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("xBins", xBins)
        .put("yBins", JSONArray().also { array -> yBins.forEach { array.put(it) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTuneCurveEditor {
            val values = mutableListOf<String>()
            val yArray = json.optJSONArray("yBins")
            if (yArray != null) {
                for (index in 0 until yArray.length()) values += yArray.getString(index)
            } else {
                json.optString("yBins").takeIf { it.isNotBlank() }?.let(values::add)
            }
            return UsbTuneCurveEditor(
                id = json.getString("id"),
                title = json.optString("title"),
                xBins = json.getString("xBins"),
                yBins = values
            )
        }
    }
}



data class UsbTuneBitOption(
    val value: Int,
    val label: String
) {
    fun toJson(): JSONObject = JSONObject().put("value", value).put("label", label)

    companion object {
        fun fromJson(json: JSONObject) = UsbTuneBitOption(
            value = json.getInt("value"),
            label = json.optString("label")
        )
    }
}

data class UsbTuneBitField(
    val name: String,
    val pageNumber: Int,
    val dataType: String,
    val offset: Int,
    val bitStart: Int,
    val bitEnd: Int,
    val options: List<UsbTuneBitOption> = emptyList()
) {
    private val normalizedType = dataType.uppercase(Locale.US)
    val byteSize: Int = when (normalizedType) {
        "U08", "S08" -> 1
        "U16", "S16" -> 2
        "U32", "S32" -> 4
        else -> 0
    }

    fun decode(raw: ByteArray): Int? {
        if (byteSize <= 0 || raw.size != byteSize || bitStart < 0 || bitEnd < bitStart || bitEnd >= byteSize * 8) return null
        fun u8(index: Int): Long = (raw[index].toInt() and 0xff).toLong()
        var value = 0L
        for (index in raw.indices) value = value or (u8(index) shl (index * 8))
        val width = bitEnd - bitStart + 1
        val mask = if (width >= 32) 0xffffffffL else (1L shl width) - 1L
        return ((value ushr bitStart) and mask).toInt()
    }

    fun labelFor(value: Int): String? = options.firstOrNull { it.value == value }?.label

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("pageNumber", pageNumber)
        .put("dataType", dataType)
        .put("offset", offset)
        .put("bitStart", bitStart)
        .put("bitEnd", bitEnd)
        .put("options", JSONArray().also { array -> options.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTuneBitField {
            val optionsJson = json.optJSONArray("options") ?: JSONArray()
            val options = (0 until optionsJson.length()).map { UsbTuneBitOption.fromJson(optionsJson.getJSONObject(it)) }
            return UsbTuneBitField(
                name = json.getString("name"),
                pageNumber = json.getInt("pageNumber"),
                dataType = json.getString("dataType"),
                offset = json.getInt("offset"),
                bitStart = json.getInt("bitStart"),
                bitEnd = json.getInt("bitEnd"),
                options = options
            )
        }
    }
}

data class UsbTuneMenuItem(
    val menu: String,
    val group: String,
    val dialogId: String,
    val title: String,
    val conditions: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("menu", menu)
        .put("group", group)
        .put("dialogId", dialogId)
        .put("title", title)
        .put("conditions", JSONArray().also { array -> conditions.forEach { array.put(it) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTuneMenuItem {
            val values = json.optJSONArray("conditions") ?: JSONArray()
            return UsbTuneMenuItem(
                menu = json.optString("menu"),
                group = json.optString("group"),
                dialogId = json.optString("dialogId"),
                title = json.optString("title"),
                conditions = (0 until values.length()).map { values.getString(it) }
            )
        }
    }
}

data class UsbTuneDialogEntry(
    val kind: String,
    val label: String = "",
    val target: String = "",
    val conditions: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind)
        .put("label", label)
        .put("target", target)
        .put("conditions", JSONArray().also { array -> conditions.forEach { array.put(it) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTuneDialogEntry {
            val values = json.optJSONArray("conditions") ?: JSONArray()
            return UsbTuneDialogEntry(
                kind = json.getString("kind"),
                label = json.optString("label"),
                target = json.optString("target"),
                conditions = (0 until values.length()).map { values.getString(it) }
            )
        }
    }
}

data class UsbTuneDialog(
    val id: String,
    val title: String,
    val layout: String = "",
    val topicHelp: String = "",
    val entries: List<UsbTuneDialogEntry> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("layout", layout)
        .put("topicHelp", topicHelp)
        .put("entries", JSONArray().also { array -> entries.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTuneDialog {
            val entriesJson = json.optJSONArray("entries") ?: JSONArray()
            return UsbTuneDialog(
                id = json.getString("id"),
                title = json.optString("title"),
                layout = json.optString("layout"),
                topicHelp = json.optString("topicHelp"),
                entries = (0 until entriesJson.length()).map { UsbTuneDialogEntry.fromJson(entriesJson.getJSONObject(it)) }
            )
        }
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
    val tunePages: List<UsbTunePage> = emptyList(),
    val tuneScalars: List<UsbTuneScalar> = emptyList(),
    val tuneArrays: List<UsbTuneArray> = emptyList(),
    val tuneTables: List<UsbTuneTableEditor> = emptyList(),
    val tuneCurves: List<UsbTuneCurveEditor> = emptyList(),
    val tuneBitFields: List<UsbTuneBitField> = emptyList(),
    val tuneMenuItems: List<UsbTuneMenuItem> = emptyList(),
    val tuneDialogs: List<UsbTuneDialog> = emptyList(),
    val importedName: String = "mainController.ini"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("signature", signature).put("queryCommand", queryCommand)
        .put("outputCommand", outputCommand).put("outputBlockSize", outputBlockSize)
        .put("envelopeFormat", envelopeFormat).put("endianness", endianness)
        .put("importedName", importedName)
        .put("channels", JSONArray().also { array -> channels.forEach { array.put(it.toJson()) } })
        .put("tunePages", JSONArray().also { array -> tunePages.forEach { array.put(it.toJson()) } })
        .put("tuneScalars", JSONArray().also { array -> tuneScalars.forEach { array.put(it.toJson()) } })
        .put("tuneArrays", JSONArray().also { array -> tuneArrays.forEach { array.put(it.toJson()) } })
        .put("tuneTables", JSONArray().also { array -> tuneTables.forEach { array.put(it.toJson()) } })
        .put("tuneCurves", JSONArray().also { array -> tuneCurves.forEach { array.put(it.toJson()) } })
        .put("tuneBitFields", JSONArray().also { array -> tuneBitFields.forEach { array.put(it.toJson()) } })
        .put("tuneMenuItems", JSONArray().also { array -> tuneMenuItems.forEach { array.put(it.toJson()) } })
        .put("tuneDialogs", JSONArray().also { array -> tuneDialogs.forEach { array.put(it.toJson()) } })

    companion object {
        fun fromJson(json: JSONObject): UsbTunerStudioProfile {
            val list = mutableListOf<UsbOutputChannel>()
            val array = json.optJSONArray("channels") ?: JSONArray()
            for (index in 0 until array.length()) list += UsbOutputChannel.fromJson(array.getJSONObject(index))

            val pages = mutableListOf<UsbTunePage>()
            val pagesArray = json.optJSONArray("tunePages") ?: JSONArray()
            for (index in 0 until pagesArray.length()) pages += UsbTunePage.fromJson(pagesArray.getJSONObject(index))

            val scalars = mutableListOf<UsbTuneScalar>()
            val scalarsArray = json.optJSONArray("tuneScalars") ?: JSONArray()
            for (index in 0 until scalarsArray.length()) scalars += UsbTuneScalar.fromJson(scalarsArray.getJSONObject(index))

            val tuneArrays = mutableListOf<UsbTuneArray>()
            val tuneArraysJson = json.optJSONArray("tuneArrays") ?: JSONArray()
            for (index in 0 until tuneArraysJson.length()) tuneArrays += UsbTuneArray.fromJson(tuneArraysJson.getJSONObject(index))

            val tuneTables = mutableListOf<UsbTuneTableEditor>()
            val tuneTablesJson = json.optJSONArray("tuneTables") ?: JSONArray()
            for (index in 0 until tuneTablesJson.length()) tuneTables += UsbTuneTableEditor.fromJson(tuneTablesJson.getJSONObject(index))

            val tuneCurves = mutableListOf<UsbTuneCurveEditor>()
            val tuneCurvesJson = json.optJSONArray("tuneCurves") ?: JSONArray()
            for (index in 0 until tuneCurvesJson.length()) tuneCurves += UsbTuneCurveEditor.fromJson(tuneCurvesJson.getJSONObject(index))

            val tuneBitFields = mutableListOf<UsbTuneBitField>()
            val tuneBitFieldsJson = json.optJSONArray("tuneBitFields") ?: JSONArray()
            for (index in 0 until tuneBitFieldsJson.length()) tuneBitFields += UsbTuneBitField.fromJson(tuneBitFieldsJson.getJSONObject(index))

            val tuneMenuItems = mutableListOf<UsbTuneMenuItem>()
            val tuneMenuItemsJson = json.optJSONArray("tuneMenuItems") ?: JSONArray()
            for (index in 0 until tuneMenuItemsJson.length()) tuneMenuItems += UsbTuneMenuItem.fromJson(tuneMenuItemsJson.getJSONObject(index))

            val tuneDialogs = mutableListOf<UsbTuneDialog>()
            val tuneDialogsJson = json.optJSONArray("tuneDialogs") ?: JSONArray()
            for (index in 0 until tuneDialogsJson.length()) tuneDialogs += UsbTuneDialog.fromJson(tuneDialogsJson.getJSONObject(index))

            return UsbTunerStudioProfile(
                signature = json.optString("signature"), queryCommand = json.optString("queryCommand", "S"),
                outputCommand = json.optString("outputCommand", "O%2o%2c"),
                outputBlockSize = json.optInt("outputBlockSize"),
                envelopeFormat = json.optString("envelopeFormat", "msEnvelope_1.0"),
                endianness = json.optString("endianness", "little"), channels = list,
                tunePages = pages, tuneScalars = scalars, tuneArrays = tuneArrays,
                tuneTables = tuneTables, tuneCurves = tuneCurves,
                tuneBitFields = tuneBitFields, tuneMenuItems = tuneMenuItems, tuneDialogs = tuneDialogs,
                importedName = json.optString("importedName", "mainController.ini")
            )
        }
    }
}

object UsbTunerStudioProfileParser {
    private data class PendingTable(
        val id: String,
        val mapId: String,
        val title: String,
        val page: Int,
        var xBins: String = "",
        var yBins: String = "",
        var zBins: String = ""
    ) {
        fun complete(): UsbTuneTableEditor? =
            if (id.isNotBlank() && xBins.isNotBlank() && yBins.isNotBlank() && zBins.isNotBlank()) {
                UsbTuneTableEditor(id, mapId, title, page, xBins, yBins, zBins)
            } else null
    }

    private data class PendingDialog(
        val id: String,
        val title: String,
        val layout: String,
        var topicHelp: String = "",
        val entries: MutableList<UsbTuneDialogEntry> = mutableListOf()
    ) {
        fun complete(): UsbTuneDialog? =
            id.takeIf { it.isNotBlank() }?.let { UsbTuneDialog(it, title, layout, topicHelp, entries.toList()) }
    }

    private data class PendingTuneBitField(
        val name: String,
        val pageNumber: Int,
        val dataType: String,
        val offset: Int,
        val bitStart: Int,
        val bitEnd: Int,
        val rawOptions: String
    )

    private data class PendingCurve(
        val id: String,
        val title: String,
        var xBins: String = "",
        val yBins: MutableList<String> = mutableListOf()
    ) {
        fun complete(): UsbTuneCurveEditor? =
            if (id.isNotBlank() && xBins.isNotBlank() && yBins.isNotEmpty()) {
                UsbTuneCurveEditor(id, title, xBins, yBins.toList())
            } else null
    }
    private val scalarRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*scalar\\s*,\\s*(U08|S08|U16|S16|U32|S32|F32)\\s*,\\s*(\\d+)\\s*,\\s*\"([^\"]*)\"\\s*,\\s*([^,]+)\\s*,\\s*([^,;]+)",
        RegexOption.IGNORE_CASE
    )
    private val tuneScalarRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*scalar\\s*,\\s*(U08|S08|U16|S16|U32|S32|F32)\\s*,\\s*(\\d+)\\s*,\\s*\"([^\"]*)\"\\s*,\\s*([^,]+)\\s*,\\s*([^,]+)\\s*,\\s*([^,]+)\\s*,\\s*([^,]+)\\s*,\\s*([^,;]+)",
        RegexOption.IGNORE_CASE
    )
    private val tuneArrayRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*array\\s*,\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val tuneBitsRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*bits\\s*,\\s*(U08|S08|U16|S16|U32|S32)\\s*,\\s*(\\d+)\\s*,\\s*\\[(\\d+)\\s*:\\s*(\\d+)\\]\\s*,?\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val defineRegex = Regex(
        "^#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val identifierRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    private val bitsRegex = Regex(
        "^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*bits\\s*,\\s*(U08|S08|U16|S16|U32|S32)\\s*,\\s*(\\d+)\\s*,\\s*\\[(\\d+)\\s*:\\s*(\\d+)\\]",
        RegexOption.IGNORE_CASE
    )
    private val quotedValueRegex = Regex("\"([^\"]*)\"")
    private val hexByteRegex = Regex("\\\\x([0-9A-Fa-f]{2})")

    data class ParseMetrics(
        val lineCount: Int,
        val scanElapsedMs: Long,
        val bitOptionsElapsedMs: Long,
        val finalizeElapsedMs: Long,
        val totalElapsedMs: Long,
        val scanPreprocessNs: Long = 0L,
        val scanDialogNs: Long = 0L,
        val scanTunerStudioNs: Long = 0L,
        val scanConstantsNs: Long = 0L,
        val scanMenuNs: Long = 0L,
        val scanTableNs: Long = 0L,
        val scanCurveNs: Long = 0L,
        val scanOutputNs: Long = 0L
    ) {
        private fun nsToMs(value: Long): Double = value / 1_000_000.0

        fun toJson(): JSONObject = JSONObject()
            .put("lineCount", lineCount)
            .put("scanElapsedMs", scanElapsedMs)
            .put("bitOptionsElapsedMs", bitOptionsElapsedMs)
            .put("finalizeElapsedMs", finalizeElapsedMs)
            .put("totalElapsedMs", totalElapsedMs)
            .put("scanBreakdownMs", JSONObject()
                .put("preprocess", nsToMs(scanPreprocessNs))
                .put("dialog", nsToMs(scanDialogNs))
                .put("tunerStudio", nsToMs(scanTunerStudioNs))
                .put("constants", nsToMs(scanConstantsNs))
                .put("menu", nsToMs(scanMenuNs))
                .put("table", nsToMs(scanTableNs))
                .put("curve", nsToMs(scanCurveNs))
                .put("outputChannels", nsToMs(scanOutputNs)))
    }

    data class ParseResult(
        val profile: UsbTunerStudioProfile,
        val metrics: ParseMetrics
    )

    fun parse(text: String, importedName: String = "mainController.ini"): UsbTunerStudioProfile =
        parseMeasured(text, importedName).profile

    fun parseMeasured(text: String, importedName: String = "mainController.ini"): ParseResult {
        val parseStartedNs = System.nanoTime()
        var lineCount = 0
        var section = ""
        var signature = ""
        var query = "S"
        var output = "O%2o%2c"
        var blockSize = 0
        var envelope = ""
        var endianness = ""
        var currentTunePage = 0
        var pageIdentifiers: List<Int> = emptyList()
        var pageSizes: List<Int> = emptyList()
        var pageReadCommands: List<String> = emptyList()
        var pageBurnCommands: List<String> = emptyList()
        val channels = mutableListOf<UsbOutputChannel>()
        val tuneScalars = mutableListOf<UsbTuneScalar>()
        val tuneArrays = mutableListOf<UsbTuneArray>()
        val tuneTables = mutableListOf<UsbTuneTableEditor>()
        val tuneCurves = mutableListOf<UsbTuneCurveEditor>()
        val pendingTuneBitFields = mutableListOf<PendingTuneBitField>()
        val tuneMenuItems = mutableListOf<UsbTuneMenuItem>()
        val tuneDialogs = mutableListOf<UsbTuneDialog>()
        val defines = linkedMapOf<String, String>()
        val dialogFieldTargets = linkedSetOf<String>()
        val bitOptionCache = HashMap<String, List<UsbTuneBitOption>>()
        var currentMenu = ""
        var currentMenuConditions: List<String> = emptyList()
        var currentGroup = ""
        var currentGroupConditions: List<String> = emptyList()
        var pendingTable: PendingTable? = null
        var pendingCurve: PendingCurve? = null
        var pendingDialog: PendingDialog? = null

        fun flushTable() {
            pendingTable?.complete()?.let(tuneTables::add)
            pendingTable = null
        }
        fun flushCurve() {
            pendingCurve?.complete()?.let(tuneCurves::add)
            pendingCurve = null
        }
        fun flushDialog() {
            pendingDialog?.complete()?.let(tuneDialogs::add)
            pendingDialog = null
        }

        // One pass is sufficient: keep unresolved bit definitions until all dialogs and
        // #define labels have been seen, then resolve only dialog-referenced bit fields.
        // These profiling accumulators are temporary performance evidence. They do not alter
        // parser outputs and are reported only to identify the next real optimization target.
        var scanPreprocessNs = 0L
        var scanDialogNs = 0L
        var scanTunerStudioNs = 0L
        var scanConstantsNs = 0L
        var scanMenuNs = 0L
        var scanTableNs = 0L
        var scanCurveNs = 0L
        var scanOutputNs = 0L
        val scanStartedNs = System.nanoTime()
        text.lineSequence().forEach { rawLine ->
            lineCount++
            val preprocessStartedNs = System.nanoTime()
            val line = stripComment(rawLine).trim()
            scanPreprocessNs += System.nanoTime() - preprocessStartedNs
            if (line.startsWith("#define", ignoreCase = true)) {
                defineRegex.find(line)?.let { match ->
                    defines[match.groupValues[1]] = match.groupValues[2].trim()
                    return@forEach
                }
            }
            if (line.isBlank() || line.startsWith("#")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                if (section == "tableeditor") flushTable()
                if (section == "curveeditor") flushCurve()
                flushDialog()
                section = line.substring(1, line.length - 1).trim().lowercase(Locale.US)
                if (section != "menu") {
                    currentMenu = ""
                    currentGroup = ""
                }
                return@forEach
            }
            val dialogStartedNs = System.nanoTime()
            try {
                valueAfterEquals(line, "dialog")?.let { body ->
                    flushDialog()
                    val fields = splitIniFields(body)
                    if (fields.isNotEmpty()) {
                        pendingDialog = PendingDialog(
                            id = fields[0].trim(),
                            title = fields.getOrNull(1)?.let(::unquote).orEmpty(),
                            layout = fields.getOrNull(2)?.trim().orEmpty()
                        )
                    }
                    return@forEach
                }
                pendingDialog?.let { dialog ->
                    valueAfterEquals(line, "topicHelp")?.let {
                        dialog.topicHelp = unquote(it)
                        return@forEach
                    }
                    valueAfterEquals(line, "field")?.let { body ->
                        val fields = splitIniFields(body)
                        val label = fields.firstOrNull()?.let(::unquote).orEmpty()
                        val target = fields.getOrNull(1)?.trim()?.takeIf { identifierRegex.matches(it) }.orEmpty()
                        if (target.isNotBlank()) dialogFieldTargets += target
                        dialog.entries += UsbTuneDialogEntry(
                            kind = if (target.isBlank()) "text" else "field",
                            label = label,
                            target = target,
                            conditions = parseConditions(fields.drop(2))
                        )
                        return@forEach
                    }
                    valueAfterEquals(line, "panel")?.let { body ->
                        val fields = splitIniFields(body)
                        val target = fields.firstOrNull()?.trim().orEmpty()
                        if (target.isNotBlank()) {
                            dialog.entries += UsbTuneDialogEntry(
                                kind = "panel",
                                target = target,
                                conditions = parseConditions(fields.drop(1))
                            )
                        }
                        return@forEach
                    }
                    valueAfterEquals(line, "commandButton")?.let { body ->
                        val fields = splitIniFields(body)
                        dialog.entries += UsbTuneDialogEntry(
                            kind = "command",
                            label = fields.firstOrNull()?.let(::unquote).orEmpty(),
                            target = fields.getOrNull(1)?.trim().orEmpty(),
                            conditions = parseConditions(fields.drop(2))
                        )
                        return@forEach
                    }
                }
    
            } finally {
                scanDialogNs += System.nanoTime() - dialogStartedNs
            }

            val sectionStartedNs = System.nanoTime()
            when (section) {
                "tunerstudio", "megatune" -> {
                    valueAfterEquals(line, "signature")?.let { if (it.length >= signature.length) signature = unquote(it) }
                    valueAfterEquals(line, "queryCommand")?.let { query = decodeIniString(unquote(it)) }
                }
                "constants" -> {
                    valueAfterEquals(line, "messageEnvelopeFormat")?.let { envelope = unquote(it) }
                    valueAfterEquals(line, "endianness")?.let { endianness = unquote(it) }
                    valueAfterEquals(line, "pageIdentifier")?.let { pageIdentifiers = parsePageIdentifiers(it) }
                    valueAfterEquals(line, "pageSize")?.let { pageSizes = parseIntegerList(it) }
                    valueAfterEquals(line, "pageReadCommand")?.let { pageReadCommands = parseQuotedList(it) }
                    valueAfterEquals(line, "burnCommand")?.let { pageBurnCommands = parseQuotedList(it) }
                    valueAfterEquals(line, "page")?.toIntOrNull()?.let { currentTunePage = it }
                    if (currentTunePage > 0) {
                        when {
                            assignmentValueStartsWith(line, "scalar") -> tuneScalarRegex.find(line)?.let { match ->
                                tuneScalars += UsbTuneScalar(
                                    name = match.groupValues[1],
                                    pageNumber = currentTunePage,
                                    dataType = match.groupValues[2].uppercase(Locale.US),
                                    offset = match.groupValues[3].toInt(),
                                    unit = match.groupValues[4],
                                    scale = parseNumber(match.groupValues[5], 1.0),
                                    translate = parseNumber(match.groupValues[6], 0.0),
                                    low = parseNumber(match.groupValues[7], Double.NEGATIVE_INFINITY),
                                    high = parseNumber(match.groupValues[8], Double.POSITIVE_INFINITY),
                                    digits = match.groupValues[9].trim().toIntOrNull() ?: 0
                                )
                            }
                            assignmentValueStartsWith(line, "array") -> tuneArrayRegex.find(line)?.let { match ->
                                parseTuneArray(match.groupValues[1], match.groupValues[2], currentTunePage)?.let(tuneArrays::add)
                            }
                            assignmentValueStartsWith(line, "bits") -> tuneBitsRegex.find(line)?.let { match ->
                                val start = match.groupValues[4].toInt()
                                val end = match.groupValues[5].toInt()
                                val dataType = match.groupValues[2].uppercase(Locale.US)
                                val byteSize = when (dataType) {
                                    "U08", "S08" -> 1
                                    "U16", "S16" -> 2
                                    "U32", "S32" -> 4
                                    else -> 0
                                }
                                if (byteSize > 0 && start >= 0 && end >= start && end < byteSize * 8) {
                                    pendingTuneBitFields += PendingTuneBitField(
                                        name = match.groupValues[1],
                                        pageNumber = currentTunePage,
                                        dataType = dataType,
                                        offset = match.groupValues[3].toInt(),
                                        bitStart = start,
                                        bitEnd = end,
                                        rawOptions = match.groupValues[6]
                                    )
                                }
                            }
                        }
                    }
                }
                "menu" -> {
                    valueAfterEquals(line, "menu")?.let { body ->
                        val fields = splitIniFields(body)
                        currentMenu = fields.firstOrNull()?.let(::unquote).orEmpty()
                        currentMenuConditions = parseConditions(fields.drop(1))
                        currentGroup = ""
                        currentGroupConditions = emptyList()
                    }
                    valueAfterEquals(line, "groupMenu")?.let { body ->
                        val fields = splitIniFields(body)
                        currentGroup = fields.firstOrNull()?.let(::unquote).orEmpty()
                        currentGroupConditions = parseConditions(fields.drop(1))
                    }
                    valueAfterEquals(line, "subMenu")?.let { body ->
                        val fields = splitIniFields(body)
                        val dialogId = fields.firstOrNull()?.trim().orEmpty()
                        if (dialogId.isNotBlank() && !dialogId.startsWith("std_", ignoreCase = true)) {
                            tuneMenuItems += UsbTuneMenuItem(
                                menu = currentMenu,
                                group = "",
                                dialogId = dialogId,
                                title = fields.getOrNull(1)?.let(::unquote).orEmpty(),
                                conditions = combineUiConditions(
                                    currentMenuConditions,
                                    parseConditions(fields.drop(2))
                                )
                            )
                        }
                    }
                    valueAfterEquals(line, "groupChildMenu")?.let { body ->
                        val fields = splitIniFields(body)
                        val dialogId = fields.firstOrNull()?.trim().orEmpty()
                        if (dialogId.isNotBlank() && !dialogId.startsWith("std_", ignoreCase = true)) {
                            tuneMenuItems += UsbTuneMenuItem(
                                menu = currentMenu,
                                group = currentGroup,
                                dialogId = dialogId,
                                title = fields.getOrNull(1)?.let(::unquote).orEmpty(),
                                conditions = combineUiConditions(
                                    currentMenuConditions,
                                    currentGroupConditions,
                                    parseConditions(fields.drop(2))
                                )
                            )
                        }
                    }
                }
                "tableeditor" -> {
                    valueAfterEquals(line, "table")?.let { body ->
                        flushTable()
                        val fields = splitIniFields(body)
                        if (fields.size >= 4) {
                            pendingTable = PendingTable(
                                id = fields[0].trim(),
                                mapId = fields[1].trim(),
                                title = unquote(fields[2].trim()),
                                page = fields[3].trim().toIntOrNull() ?: 1
                            )
                        }
                    }
                    valueAfterEquals(line, "xBins")?.let { body ->
                        pendingTable?.xBins = splitIniFields(body).firstOrNull()?.trim().orEmpty()
                    }
                    valueAfterEquals(line, "yBins")?.let { body ->
                        pendingTable?.yBins = splitIniFields(body).firstOrNull()?.trim().orEmpty()
                    }
                    valueAfterEquals(line, "zBins")?.let { body ->
                        pendingTable?.zBins = splitIniFields(body).firstOrNull()?.trim().orEmpty()
                    }
                }
                "curveeditor" -> {
                    valueAfterEquals(line, "curve")?.let { body ->
                        flushCurve()
                        val fields = splitIniFields(body)
                        if (fields.size >= 2) {
                            pendingCurve = PendingCurve(
                                id = fields[0].trim(),
                                title = unquote(fields[1].trim())
                            )
                        }
                    }
                    valueAfterEquals(line, "xBins")?.let { body ->
                        pendingCurve?.xBins = splitIniFields(body).firstOrNull()?.trim().orEmpty()
                    }
                    valueAfterEquals(line, "yBins")?.let { body ->
                        splitIniFields(body).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
                            pendingCurve?.yBins?.add(value)
                        }
                    }
                }
                "outputchannels" -> {
                    valueAfterEquals(line, "ochGetCommand")?.let { output = unquote(it) }
                    valueAfterEquals(line, "ochBlockSize")?.toIntOrNull()?.let { blockSize = it }
                    when {
                        assignmentValueStartsWith(line, "scalar") -> scalarRegex.find(line)?.let { match ->
                            channels += UsbOutputChannel(
                                name = match.groupValues[1], kind = "scalar",
                                dataType = match.groupValues[2].uppercase(Locale.US),
                                offset = match.groupValues[3].toInt(), unit = match.groupValues[4],
                                scale = parseNumber(match.groupValues[5], 1.0),
                                translate = parseNumber(match.groupValues[6], 0.0)
                            )
                        }
                        assignmentValueStartsWith(line, "bits") -> bitsRegex.find(line)?.let { match ->
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
            val sectionElapsedNs = System.nanoTime() - sectionStartedNs
            when (section) {
                "tunerstudio", "megatune" -> scanTunerStudioNs += sectionElapsedNs
                "constants" -> scanConstantsNs += sectionElapsedNs
                "menu" -> scanMenuNs += sectionElapsedNs
                "tableeditor" -> scanTableNs += sectionElapsedNs
                "curveeditor" -> scanCurveNs += sectionElapsedNs
                "outputchannels" -> scanOutputNs += sectionElapsedNs
            }
        }

        val scanElapsedMs = (System.nanoTime() - scanStartedNs) / 1_000_000L
        val finalizeStartedNs = System.nanoTime()

        flushTable()
        flushCurve()
        flushDialog()

        val bitOptionsStartedNs = System.nanoTime()
        val tuneBitFields = pendingTuneBitFields.asSequence()
            .filter { it.name in dialogFieldTargets }
            .map { pending ->
                UsbTuneBitField(
                    name = pending.name,
                    pageNumber = pending.pageNumber,
                    dataType = pending.dataType,
                    offset = pending.offset,
                    bitStart = pending.bitStart,
                    bitEnd = pending.bitEnd,
                    options = parseBitOptions(pending.rawOptions, defines, bitOptionCache)
                )
            }
            .toList()
        val bitOptionsElapsedMs = (System.nanoTime() - bitOptionsStartedNs) / 1_000_000L

        require(blockSize in 64..65535) { "No valid ochBlockSize found in [OutputChannels]" }
        require(channels.isNotEmpty()) { "No scalar/bit output channels found in [OutputChannels]" }
        require(envelope.equals("msEnvelope_1.0", ignoreCase = true)) {
            "Unsupported message envelope '$envelope' (expected msEnvelope_1.0)"
        }
        require(endianness.equals("little", ignoreCase = true)) { "Only little-endian output channels are supported" }
        require(query.isNotEmpty()) { "TunerStudio queryCommand is empty" }
        require(output.startsWith("O")) { "Unsupported ochGetCommand '$output'" }

        val tunePages = pageReadCommands.mapIndexedNotNull { index, readCommand ->
            val identifier = pageIdentifiers.getOrNull(index) ?: return@mapIndexedNotNull null
            val size = pageSizes.getOrNull(index) ?: return@mapIndexedNotNull null
            UsbTunePage(
                pageNumber = index + 1,
                identifier = identifier,
                size = size,
                readCommand = decodeIniString(readCommand),
                burnCommand = pageBurnCommands.getOrNull(index)?.let(::decodeIniString).orEmpty()
            )
        }

        val profile = UsbTunerStudioProfile(
            signature = signature,
            queryCommand = query,
            outputCommand = output,
            outputBlockSize = blockSize,
            envelopeFormat = envelope,
            endianness = endianness,
            channels = channels,
            tunePages = tunePages,
            tuneScalars = tuneScalars,
            tuneArrays = tuneArrays,
            tuneTables = tuneTables,
            tuneCurves = tuneCurves,
            tuneBitFields = tuneBitFields,
            tuneMenuItems = tuneMenuItems,
            tuneDialogs = tuneDialogs,
            importedName = importedName
        )
        val finalizeElapsedMs = (System.nanoTime() - finalizeStartedNs) / 1_000_000L
        val totalElapsedMs = (System.nanoTime() - parseStartedNs) / 1_000_000L
        return ParseResult(
            profile = profile,
            metrics = ParseMetrics(
                lineCount = lineCount,
                scanElapsedMs = scanElapsedMs,
                bitOptionsElapsedMs = bitOptionsElapsedMs,
                finalizeElapsedMs = finalizeElapsedMs,
                totalElapsedMs = totalElapsedMs,
                scanPreprocessNs = scanPreprocessNs,
                scanDialogNs = scanDialogNs,
                scanTunerStudioNs = scanTunerStudioNs,
                scanConstantsNs = scanConstantsNs,
                scanMenuNs = scanMenuNs,
                scanTableNs = scanTableNs,
                scanCurveNs = scanCurveNs,
                scanOutputNs = scanOutputNs
            )
        )
    }

    private fun parseTuneArray(name: String, body: String, pageNumber: Int): UsbTuneArray? {
        val fields = splitIniFields(body)
        if (fields.size < 9) return null
        val dataType = fields[0].trim().uppercase(Locale.US)
        if (dataType !in setOf("U08", "S08", "U16", "S16", "U32", "S32", "F32")) return null
        val offset = fields[1].trim().toIntOrNull() ?: return null
        val dimensions = parseDimensions(fields[2]) ?: return null
        val unitToken = fields[3].trim()
        val unit = if (unitToken.startsWith("\"") && unitToken.endsWith("\"")) unquote(unitToken) else unitToken
        return UsbTuneArray(
            name = name,
            pageNumber = pageNumber,
            dataType = dataType,
            offset = offset,
            dimensions = dimensions,
            unit = unit,
            scale = parseNumber(fields[4], Double.NaN),
            translate = parseNumber(fields[5], Double.NaN),
            low = parseNumber(fields[6], Double.NaN),
            high = parseNumber(fields[7], Double.NaN),
            digits = fields[8].trim().toIntOrNull() ?: return null
        )
    }

    private fun parseConditions(fields: List<String>): List<String> =
        fields.mapNotNull { field ->
            val value = field.trim()
            if (value.startsWith("{") && value.endsWith("}") && value.length >= 2) {
                value.substring(1, value.length - 1).trim().takeIf { it.isNotBlank() }
            } else null
        }

    private fun combineUiConditions(vararg layers: List<String>): List<String> {
        val enableParts = layers.mapNotNull { it.getOrNull(0)?.takeIf(String::isNotBlank) }
        val visibleParts = layers.mapNotNull { it.getOrNull(1)?.takeIf(String::isNotBlank) }
        val extraParts = layers.flatMap { it.drop(2) }

        fun combine(parts: List<String>): String = when (parts.size) {
            0 -> ""
            1 -> parts.single()
            else -> parts.joinToString(" && ") { "($it)" }
        }

        val combined = ArrayList<String>(2 + extraParts.size)
        if (enableParts.isNotEmpty() || visibleParts.isNotEmpty()) {
            combined += if (enableParts.isEmpty()) "1" else combine(enableParts)
        }
        if (visibleParts.isNotEmpty()) {
            combined += combine(visibleParts)
        }
        combined += extraParts
        return combined
    }

    private fun parseBitOptions(
        raw: String,
        defines: Map<String, String>,
        cache: MutableMap<String, List<UsbTuneBitOption>>
    ): List<UsbTuneBitOption> {
        var body = raw.trim().removePrefix(",").trim()
        if (body.startsWith("$")) body = defines[body.removePrefix("$").trim()].orEmpty()
        if (body.isBlank()) return emptyList()
        cache[body]?.let { return it }

        val result = ArrayList<UsbTuneBitOption>()
        var nextValue = 0
        splitIniFields(body).forEach { token ->
            val value = token.trim()
            if (value.isBlank()) return@forEach
            val explicit = parseExplicitBitOption(value)
            if (explicit != null) {
                result += UsbTuneBitOption(explicit.first, explicit.second)
                nextValue = explicit.first + 1
            } else {
                val label = if (value.startsWith("\"") && value.endsWith("\"")) unquote(value) else value
                result += UsbTuneBitOption(nextValue, label)
                nextValue++
            }
        }
        val frozen = result.toList()
        cache[body] = frozen
        return frozen
    }

    private fun parseExplicitBitOption(value: String): Pair<Int, String>? {
        var index = 0
        var negative = false
        if (index < value.length && value[index] == '-') {
            negative = true
            index++
        }
        val digitStart = index
        while (index < value.length && value[index].isDigit()) index++
        if (index == digitStart) return null
        val digitsEnd = index
        while (index < value.length && value[index].isWhitespace()) index++
        if (index >= value.length || value[index] != '=') return null
        index++
        while (index < value.length && value[index].isWhitespace()) index++
        if (index >= value.length || value[index] != '"' || !value.endsWith("\"") || index == value.lastIndex) return null
        val numeric = value.substring(digitStart, digitsEnd).toIntOrNull() ?: return null
        val label = value.substring(index + 1, value.lastIndex)
        if (label.contains('"')) return null
        return (if (negative) -numeric else numeric) to label
    }

    private fun parseDimensions(value: String): List<Int>? {
        val text = value.trim()
        if (!text.startsWith("[") || !text.endsWith("]")) return null
        val dimensions = text.substring(1, text.length - 1)
            .split('x', 'X')
            .map { it.trim().toIntOrNull() ?: return null }
        return dimensions.takeIf { it.isNotEmpty() && it.all { dimension -> dimension > 0 } }
    }

    /** Split INI comma fields only at top level; expressions may contain commas in braces/parentheses. */
    private fun splitIniFields(value: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false
        var braceDepth = 0
        var bracketDepth = 0
        var parenDepth = 0
        value.forEach { ch ->
            if (escaped) {
                current.append(ch)
                escaped = false
                return@forEach
            }
            if (ch == '\\' && quoted) {
                current.append(ch)
                escaped = true
                return@forEach
            }
            if (ch == '"') {
                quoted = !quoted
                current.append(ch)
                return@forEach
            }
            if (!quoted) {
                when (ch) {
                    '{' -> braceDepth++
                    '}' -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
                    '[' -> bracketDepth++
                    ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    '(' -> parenDepth++
                    ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    ',' -> if (braceDepth == 0 && bracketDepth == 0 && parenDepth == 0) {
                        fields += current.toString().trim()
                        current.setLength(0)
                        return@forEach
                    }
                }
            }
            current.append(ch)
        }
        fields += current.toString().trim()
        return fields
    }

    private fun stripComment(line: String): String {
        if (line.indexOf(';') < 0) return line
        var quoted = false
        var escaped = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            if (escaped) {
                escaped = false
            } else if (ch == '\\' && quoted) {
                escaped = true
            } else if (ch == '"') {
                quoted = !quoted
            } else if (ch == ';' && !quoted) {
                return line.substring(0, index)
            }
            index++
        }
        return line
    }

    private fun assignmentValueStartsWith(line: String, token: String): Boolean {
        val equals = line.indexOf('=')
        if (equals < 0) return false
        var index = equals + 1
        while (index < line.length && line[index].isWhitespace()) index++
        if (index + token.length > line.length ||
            !line.regionMatches(index, token, 0, token.length, ignoreCase = true)
        ) {
            return false
        }
        val end = index + token.length
        return end >= line.length || line[end].isWhitespace() || line[end] == ','
    }

    private fun valueAfterEquals(line: String, key: String): String? {
        var index = 0
        while (index < line.length && line[index].isWhitespace()) index++
        if (index + key.length > line.length ||
            !line.regionMatches(index, key, 0, key.length, ignoreCase = true)
        ) {
            return null
        }
        index += key.length
        while (index < line.length && line[index].isWhitespace()) index++
        if (index >= line.length || line[index] != '=') return null
        index++
        while (index < line.length && line[index].isWhitespace()) index++
        if (index >= line.length) return null
        return line.substring(index).trim().takeIf { it.isNotEmpty() }
    }

    private fun unquote(value: String): String = value.trim().removeSurrounding("\"")

    private fun decodeIniString(value: String): String {
        val source = value.trim()
        val out = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            if (index + 3 < source.length && source[index] == '\\' && source[index + 1].equals('x', ignoreCase = true)) {
                val byte = source.substring(index + 2, index + 4).toIntOrNull(16)
                if (byte != null) {
                    out.append(byte.toChar())
                    index += 4
                    continue
                }
            }
            if (index + 1 < source.length && source[index] == '\\') {
                when (source[index + 1]) {
                    'n' -> { out.append('\n'); index += 2; continue }
                    'r' -> { out.append('\r'); index += 2; continue }
                }
            }
            out.append(source[index])
            index++
        }
        return out.toString()
    }

    private fun parsePageIdentifiers(value: String): List<Int> = quotedValueRegex.findAll(value).mapNotNull { match ->
        val bytes = hexByteRegex.findAll(match.groupValues[1])
            .mapNotNull { it.groupValues[1].toIntOrNull(16) }
            .toList()
        if (bytes.size != 2) null else bytes[0] or (bytes[1] shl 8)
    }.toList()

    private fun parseQuotedList(value: String): List<String> = quotedValueRegex.findAll(value)
        .map { it.groupValues[1] }
        .toList()

    private fun parseIntegerList(value: String): List<Int> = value.split(',')
        .mapNotNull { it.trim().toIntOrNull() }

    private fun parseNumber(value: String, fallback: Double): Double = value.trim().toDoubleOrNull() ?: fallback

    fun selfTest(): JSONObject {
        val sample = """
            [TunerStudio]
            queryCommand = "S"
            signature = "rusEFI test"
            [Constants]
            messageEnvelopeFormat = msEnvelope_1.0
            endianness = little
            pageIdentifier = "\\x00\\x00", "\\x00\\x01"
            pageSize = 128, 64
            pageReadCommand = "R%2i%2o%2c", "R%2i%2o%2c"
            page = 1
            engineSnifferRpmThreshold = scalar, U16, 12, "RPM", 1, 0, 0, 30000, 0
            veTable = array, U16, 16, [2x2], "%", 0.01, 0, 0, 650, 2
            veLoadBins = array, U16, 24, [2], {bitStringValue(fuelUnits, fuelAlgorithm) }, 1, 0, 0, 650, 0
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
            val tuneScalar = profile.tuneScalars.firstOrNull { it.name == "engineSnifferRpmThreshold" }
            val tuneArray = profile.tuneArrays.firstOrNull { it.name == "veTable" }
            val expressionArray = profile.tuneArrays.firstOrNull { it.name == "veLoadBins" }
            val passed = rpm == 4660.0 && ready == 1.0 &&
                batt != null && kotlin.math.abs(batt - 11.815) < 0.0001 &&
                afrTarget != null && kotlin.math.abs(afrTarget - 14.188) < 0.0001 &&
                profile.tunePages.firstOrNull()?.identifier == 0 &&
                profile.tunePages.getOrNull(1)?.identifier == 0x0100 &&
                tuneScalar?.offset == 12 && tuneScalar.byteSize == 2 &&
                tuneArray?.dimensions == listOf(2, 2) && tuneArray.elementCount == 4 &&
                expressionArray?.unit == "{bitStringValue(fuelUnits, fuelAlgorithm) }"
            JSONObject().put("passed", passed)
                .put("channels", profile.channels.size).put("blockSize", profile.outputBlockSize)
                .put("scientificScaleVoltage", batt ?: JSONObject.NULL)
                .put("caseSensitiveTargetAFR", afrTarget ?: JSONObject.NULL)
                .put("tunePages", profile.tunePages.size)
                .put("tuneScalars", profile.tuneScalars.size)
        } catch (error: Exception) {
            JSONObject().put("passed", false).put("error", error.message)
        }
    }
}
