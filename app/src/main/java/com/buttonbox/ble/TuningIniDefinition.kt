package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject

/**
 * Projects the currently imported TunerStudio INI into the semantic Tuner shape without requiring
 * an ECU or TuneSnapshot. The INI is the sole structural authority; values deliberately remain
 * unavailable until a complete ECU read exists. No page, offset, primitive type, or protocol
 * metadata is exposed to the WebView.
 */
internal object TuningIniDefinitionBuilder {
    private const val VALUE_UNAVAILABLE = "unavailable"
    private const val AWAITING_ECU = "Awaiting a complete ECU read for current values"

    fun build(profile: UsbTunerStudioProfile): JSONObject {
        require(profile.signature.isNotBlank()) { "Tuner INI signature is missing" }

        val profilePages = profile.tunePages.groupBy { it.pageNumber }
        val scalarGroups = profile.tuneScalars.groupBy { it.name }
        val scalars = JSONArray()
        var skippedScalars = 0
        var ambiguousScalars = 0
        scalarGroups.values.forEach { definitions ->
            if (definitions.size != 1) {
                ambiguousScalars++
                skippedScalars += definitions.size
                return@forEach
            }
            val definition = definitions.single()
            val page = profilePages[definition.pageNumber]?.singleOrNull()
            val structurallyUsable = page != null &&
                definition.byteSize > 0 &&
                definition.offset >= 0 &&
                definition.offset + definition.byteSize <= page.size &&
                definition.scale.isFinite() && definition.scale != 0.0 && definition.translate.isFinite()
            if (!structurallyUsable) {
                skippedScalars++
                return@forEach
            }
            scalars.put(JSONObject()
                .put("name", definition.name)
                .put("value", VALUE_UNAVAILABLE)
                .put("valueAvailable", false)
                .put("unit", definition.unit)
                .put("low", finiteOrNull(definition.low))
                .put("high", finiteOrNull(definition.high))
                .put("digits", definition.digits.coerceIn(0, 9))
                .put("writeAllowed", false)
                .put("writeBlockReason", AWAITING_ECU))
        }

        val canonicalArrays = mutableListOf<UsbTuneArray>()
        var skippedArrays = 0
        var ambiguousArrays = 0
        profile.tuneArrays.groupBy { it.name }.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalArrays += definitions.single()
                definitions.drop(1).all { equivalentArrayDefinition(definitions.first(), it) } -> {
                    canonicalArrays += definitions.first().copy(digits = definitions.maxOf { it.digits })
                    skippedArrays += definitions.size - 1
                }
                else -> {
                    ambiguousArrays++
                    skippedArrays += definitions.size
                }
            }
        }

        val arrayJsonByName = linkedMapOf<String, JSONObject>()
        canonicalArrays.forEach { definition ->
            val page = profilePages[definition.pageNumber]?.singleOrNull()
            val structurallyUsable = page != null &&
                definition.byteSize > 0 && definition.elementCount > 0 && definition.totalByteSize > 0 &&
                definition.offset >= 0 && definition.offset.toLong() + definition.totalByteSize.toLong() <= page.size.toLong() &&
                definition.scale.isFinite() && definition.scale != 0.0 && definition.translate.isFinite() &&
                !definition.low.isNaN() && !definition.high.isNaN() && definition.low <= definition.high
            if (!structurallyUsable) {
                skippedArrays++
                return@forEach
            }
            arrayJsonByName[definition.name] = JSONObject()
                .put("name", definition.name)
                .put("dimensions", JSONArray(definition.dimensions))
                .put("elementCount", definition.elementCount)
                .put("unit", definition.unit)
                .put("low", finiteOrNull(definition.low))
                .put("high", finiteOrNull(definition.high))
                .put("digits", definition.digits.coerceIn(0, 9))
                .put("minimum", finiteOrZero(definition.low))
                .put("maximum", finiteOrZero(definition.high))
                .put("valueAvailable", false)
                .put("writeAllowed", false)
                .put("writeBlockReason", AWAITING_ECU)
                .put("values", JSONArray())
        }
        val arrays = JSONArray().also { out ->
            arrayJsonByName.values.sortedBy { it.optString("name") }.forEach(out::put)
        }

        val tables = JSONArray()
        var skippedTables = 0
        var ambiguousTables = 0
        val canonicalTables = mutableListOf<UsbTuneTableEditor>()
        profile.tuneTables.groupBy { it.id }.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalTables += definitions.single()
                definitions.drop(1).all { equivalentTableDefinition(definitions.first(), it) } -> {
                    canonicalTables += definitions.first()
                    skippedTables += definitions.size - 1
                }
                else -> {
                    ambiguousTables++
                    skippedTables += definitions.size
                }
            }
        }
        canonicalTables.sortedBy { it.title.ifBlank { it.id } }.forEach { definition ->
            val x = arrayJsonByName[definition.xBins]
            val y = arrayJsonByName[definition.yBins]
            val z = arrayJsonByName[definition.zBins]
            val zDims = z?.optJSONArray("dimensions")
            val xCount = x?.optInt("elementCount", 0) ?: 0
            val yCount = y?.optInt("elementCount", 0) ?: 0
            val compatible = x != null && y != null && z != null && zDims != null && zDims.length() == 2 &&
                zDims.optInt(0, -1) == xCount && zDims.optInt(1, -1) == yCount
            if (!compatible) {
                skippedTables++
                return@forEach
            }
            val tableValues = z ?: return@forEach
            tables.put(JSONObject()
                .put("kind", "table")
                .put("id", definition.id)
                .put("title", definition.title)
                .put("xBins", definition.xBins)
                .put("yBins", definition.yBins)
                .put("targetArray", definition.zBins)
                .put("xCount", xCount)
                .put("yCount", yCount)
                .put("elementCount", xCount * yCount)
                .put("unit", tableValues.optString("unit"))
                .put("minimum", tableValues.optDouble("minimum", 0.0))
                .put("maximum", tableValues.optDouble("maximum", 0.0))
                .put("valueAvailable", false)
                .put("writeAllowed", false)
                .put("writeBlockReason", AWAITING_ECU))
        }

        val curves = JSONArray()
        var skippedCurves = 0
        var ambiguousCurves = 0
        val canonicalCurves = mutableListOf<UsbTuneCurveEditor>()
        profile.tuneCurves.groupBy { it.id }.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalCurves += definitions.single()
                definitions.drop(1).all { equivalentCurveDefinition(definitions.first(), it) } -> {
                    canonicalCurves += definitions.first()
                    skippedCurves += definitions.size - 1
                }
                else -> {
                    ambiguousCurves++
                    skippedCurves += definitions.size
                }
            }
        }
        canonicalCurves.sortedBy { it.title.ifBlank { it.id } }.forEach { definition ->
            if (definition.yBins.size != 1) {
                skippedCurves++
                return@forEach
            }
            val yName = definition.yBins.single()
            val x = arrayJsonByName[definition.xBins]
            val y = arrayJsonByName[yName]
            val count = x?.optInt("elementCount", 0) ?: 0
            if (x == null || y == null || count <= 0 || y.optInt("elementCount", 0) != count) {
                skippedCurves++
                return@forEach
            }
            curves.put(JSONObject()
                .put("kind", "curve")
                .put("id", definition.id)
                .put("title", definition.title)
                .put("xBins", definition.xBins)
                .put("targetArray", yName)
                .put("pointCount", count)
                .put("elementCount", count)
                .put("unit", y.optString("unit"))
                .put("minimum", y.optDouble("minimum", 0.0))
                .put("maximum", y.optDouble("maximum", 0.0))
                .put("valueAvailable", false)
                .put("writeAllowed", false)
                .put("writeBlockReason", AWAITING_ECU))
        }

        val bitFields = JSONArray()
        var skippedBits = 0
        var ambiguousBits = 0
        val canonicalBits = mutableListOf<UsbTuneBitField>()
        profile.tuneBitFields.groupBy { it.name }.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalBits += definitions.single()
                definitions.drop(1).all { equivalentBitFieldDefinition(definitions.first(), it) } -> {
                    canonicalBits += definitions.first()
                    skippedBits += definitions.size - 1
                }
                else -> {
                    ambiguousBits++
                    skippedBits += definitions.size
                }
            }
        }
        canonicalBits.sortedBy { it.name }.forEach { definition ->
            val page = profilePages[definition.pageNumber]?.singleOrNull()
            val usable = page != null && definition.byteSize > 0 && definition.offset >= 0 &&
                definition.offset + definition.byteSize <= page.size
            if (!usable) {
                skippedBits++
                return@forEach
            }
            bitFields.put(JSONObject()
                .put("name", definition.name)
                .put("value", VALUE_UNAVAILABLE)
                .put("valueAvailable", false)
                .put("valueLabel", "—")
                .put("options", JSONArray().also { out ->
                    definition.options.forEach { option ->
                        out.put(JSONObject().put("value", option.value).put("label", option.label))
                    }
                })
                .put("writeAllowed", false)
                .put("writeBlockReason", AWAITING_ECU))
        }

        val pendingConditionCount = profile.tuneMenuItems.sumOf { it.conditions.size } +
            profile.tuneDialogs.sumOf { dialog -> dialog.entries.sumOf { it.conditions.size } }
        val menuItems = JSONArray().also { out ->
            profile.tuneMenuItems.forEach { item ->
                out.put(JSONObject()
                    .put("menu", item.menu)
                    .put("group", item.group)
                    .put("dialogId", item.dialogId)
                    .put("title", item.title)
                    .put("conditions", JSONArray(item.conditions))
                    .put("conditionState", definitionConditionState(item.conditions)))
            }
        }
        val dialogs = JSONArray().also { out ->
            profile.tuneDialogs.forEach { dialog ->
                out.put(JSONObject()
                    .put("id", dialog.id)
                    .put("title", dialog.title)
                    .put("layout", dialog.layout)
                    .put("topicHelp", dialog.topicHelp)
                    .put("entries", JSONArray().also { entries ->
                        dialog.entries.forEach { entry ->
                            entries.put(JSONObject()
                                .put("kind", entry.kind)
                                .put("label", entry.label)
                                .put("target", entry.target)
                                .put("conditions", JSONArray(entry.conditions))
                                .put("conditionState", definitionConditionState(entry.conditions)))
                        }
                    }))
            }
        }

        return JSONObject()
            .put("status", "ready")
            .put("capability", "INI_STRUCTURE")
            .put("source", "INI_DEFINITION")
            .put("definitionOnly", true)
            .put("generation", 0L)
            .put("importedProfileName", profile.importedName)
            .put("ecuSignature", profile.signature)
            .put("profileSignature", profile.signature)
            .put("profileFingerprint", profile.tuneProfileFingerprint())
            .put("tuneFingerprint", "")
            .put("capturedAtEpochMs", 0L)
            .put("totalProfileScalars", profile.tuneScalars.size)
            .put("readableScalars", scalars.length())
            .put("skippedScalars", skippedScalars)
            .put("ambiguousScalarNames", ambiguousScalars)
            .put("scalars", scalars)
            .put("totalProfileArrays", profile.tuneArrays.size)
            .put("readableArrays", arrays.length())
            .put("skippedArrays", skippedArrays)
            .put("ambiguousArrayNames", ambiguousArrays)
            .put("arrays", arrays)
            .put("totalProfileTables", profile.tuneTables.size)
            .put("readableTables", tables.length())
            .put("skippedTables", skippedTables)
            .put("ambiguousTableIds", ambiguousTables)
            .put("tables", tables)
            .put("totalProfileCurves", profile.tuneCurves.size)
            .put("readableCurves", curves.length())
            .put("skippedCurves", skippedCurves)
            .put("ambiguousCurveIds", ambiguousCurves)
            .put("curves", curves)
            .put("totalProfileBitFields", profile.tuneBitFields.size)
            .put("readableBitFields", bitFields.length())
            .put("skippedBitFields", skippedBits)
            .put("ambiguousBitFieldNames", ambiguousBits)
            .put("bitFields", bitFields)
            .put("menuItemCount", menuItems.length())
            .put("menuItems", menuItems)
            .put("dialogCount", dialogs.length())
            .put("dialogs", dialogs)
            .put("iniCompatibility", JSONObject()
                .put("state", "amber")
                .put("totalConditionExpressions", pendingConditionCount)
                .put("unsupportedConditionExpressions", 0)
                .put("disabledEntries", profile.tuneMenuItems.count { it.conditions.isNotEmpty() } +
                    profile.tuneDialogs.sumOf { dialog -> dialog.entries.count { it.conditions.isNotEmpty() } })
                .put("hiddenEntries", 0)
                .put("reason", "INI structure loaded; ECU values are required to evaluate runtime conditions")
                .put("unsupportedExamples", JSONArray())
                .put("importedProfileName", profile.importedName)
                .put("profileSignature", profile.signature)
                .put("signatureMatches", JSONObject.NULL)
                .put("profileFingerprint", profile.tuneProfileFingerprint()))
    }

    private fun definitionConditionState(conditions: List<String>): JSONObject = if (conditions.isEmpty()) {
        JSONObject()
            .put("status", "active")
            .put("enabled", true)
            .put("visible", true)
            .put("supported", true)
            .put("reason", JSONObject.NULL)
            .put("evaluations", JSONArray())
    } else {
        JSONObject()
            .put("status", "disabled")
            .put("enabled", false)
            .put("visible", true)
            .put("supported", true)
            .put("reason", "Awaiting ECU values to evaluate INI conditions")
            .put("evaluations", JSONArray())
    }

    private fun finiteOrNull(value: Double): Any = if (value.isFinite()) value else JSONObject.NULL
    private fun finiteOrZero(value: Double): Double = value.takeIf { it.isFinite() } ?: 0.0

    private fun equivalentBitFieldDefinition(left: UsbTuneBitField, right: UsbTuneBitField): Boolean =
        left.pageNumber == right.pageNumber &&
            left.dataType.equals(right.dataType, ignoreCase = true) &&
            left.offset == right.offset &&
            left.bitStart == right.bitStart &&
            left.bitEnd == right.bitEnd &&
            left.options == right.options

    private fun equivalentTableDefinition(left: UsbTuneTableEditor, right: UsbTuneTableEditor): Boolean =
        left.id == right.id && left.mapId == right.mapId && left.title == right.title &&
            left.page == right.page && left.xBins == right.xBins && left.yBins == right.yBins && left.zBins == right.zBins

    private fun equivalentCurveDefinition(left: UsbTuneCurveEditor, right: UsbTuneCurveEditor): Boolean =
        left.id == right.id && left.title == right.title && left.xBins == right.xBins && left.yBins == right.yBins

    private fun equivalentArrayDefinition(left: UsbTuneArray, right: UsbTuneArray): Boolean =
        left.pageNumber == right.pageNumber &&
            left.dataType.equals(right.dataType, ignoreCase = true) &&
            left.offset == right.offset &&
            left.dimensions == right.dimensions &&
            left.unit == right.unit &&
            left.scale == right.scale &&
            left.translate == right.translate &&
            left.low == right.low &&
            left.high == right.high
}
