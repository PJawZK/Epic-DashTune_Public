package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject

/**
 * T4 read-only tuning workspace projected from the currently imported INI and immutable
 * TuneSnapshot. Transport metadata stays native; the WebView receives semantic values only.
 */
data class TuningWorkspaceScalar(
    val name: String,
    val value: Double,
    val unit: String,
    val low: Double?,
    val high: Double?,
    val digits: Int,
    val writeAllowed: Boolean,
    val writeBlockReason: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("value", value)
        .put("unit", unit)
        .put("low", low ?: JSONObject.NULL)
        .put("high", high ?: JSONObject.NULL)
        .put("digits", digits.coerceIn(0, 9))
        .put("writeAllowed", writeAllowed)
        .put("writeBlockReason", writeBlockReason ?: JSONObject.NULL)
}

data class TuningWorkspaceBitOption(
    val value: Int,
    val label: String
) {
    fun toJson(): JSONObject = JSONObject().put("value", value).put("label", label)
}

data class TuningWorkspaceBitField(
    val name: String,
    val value: Int,
    val valueLabel: String?,
    val options: List<TuningWorkspaceBitOption>,
    val writeAllowed: Boolean,
    val writeBlockReason: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("value", value)
        .put("valueLabel", valueLabel ?: JSONObject.NULL)
        .put("options", JSONArray().also { array -> options.forEach { array.put(it.toJson()) } })
        .put("writeAllowed", writeAllowed)
        .put("writeBlockReason", writeBlockReason ?: JSONObject.NULL)
}

data class TuningWorkspaceArray(
    val name: String,
    val dimensions: List<Int>,
    val elementCount: Int,
    val unit: String,
    val low: Double?,
    val high: Double?,
    val digits: Int,
    val minimum: Double,
    val maximum: Double,
    val writeAllowed: Boolean,
    val writeBlockReason: String?,
    val values: List<Double> = emptyList()
) {
    fun toJson(includeValues: Boolean = true): JSONObject = JSONObject()
        .put("name", name)
        .put("dimensions", JSONArray().also { array -> dimensions.forEach { array.put(it) } })
        .put("elementCount", elementCount)
        .put("unit", unit)
        .put("low", low ?: JSONObject.NULL)
        .put("high", high ?: JSONObject.NULL)
        .put("digits", digits.coerceIn(0, 9))
        .put("minimum", minimum)
        .put("maximum", maximum)
        .put("writeAllowed", writeAllowed)
        .put("writeBlockReason", writeBlockReason ?: JSONObject.NULL)
        .also { json ->
            if (includeValues) {
                json.put("values", JSONArray().also { array -> values.forEach { array.put(it) } })
            }
        }
}

data class TuningWorkspaceArrayDetail(
    val generation: Long,
    val profileFingerprint: String,
    val tuneFingerprint: String,
    val name: String,
    val dimensions: List<Int>,
    val unit: String,
    val low: Double?,
    val high: Double?,
    val digits: Int,
    val values: List<Double>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("status", "ready")
        .put("capability", "READ_ONLY")
        .put("source", "LIVE_TUNE_SNAPSHOT")
        .put("generation", generation)
        .put("profileFingerprint", profileFingerprint)
        .put("tuneFingerprint", tuneFingerprint)
        .put("name", name)
        .put("dimensions", JSONArray().also { array -> dimensions.forEach { array.put(it) } })
        .put("elementCount", values.size)
        .put("unit", unit)
        .put("low", low ?: JSONObject.NULL)
        .put("high", high ?: JSONObject.NULL)
        .put("digits", digits.coerceIn(0, 9))
        .put("values", JSONArray().also { array -> values.forEach { array.put(it) } })
}

data class TuningWorkspaceTable(
    val id: String,
    val title: String,
    val xBins: String,
    val yBins: String,
    val zBins: String,
    val xCount: Int,
    val yCount: Int,
    val unit: String,
    val minimum: Double,
    val maximum: Double,
    val writeAllowed: Boolean,
    val writeBlockReason: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", "table")
        .put("id", id)
        .put("title", title)
        .put("xBins", xBins)
        .put("yBins", yBins)
        .put("targetArray", zBins)
        .put("xCount", xCount)
        .put("yCount", yCount)
        .put("elementCount", xCount * yCount)
        .put("unit", unit)
        .put("minimum", minimum)
        .put("maximum", maximum)
        .put("writeAllowed", writeAllowed)
        .put("writeBlockReason", writeBlockReason ?: JSONObject.NULL)
}

data class TuningWorkspaceCurve(
    val id: String,
    val title: String,
    val xBins: String,
    val yBins: String,
    val pointCount: Int,
    val unit: String,
    val minimum: Double,
    val maximum: Double,
    val writeAllowed: Boolean,
    val writeBlockReason: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", "curve")
        .put("id", id)
        .put("title", title)
        .put("xBins", xBins)
        .put("targetArray", yBins)
        .put("pointCount", pointCount)
        .put("elementCount", pointCount)
        .put("unit", unit)
        .put("minimum", minimum)
        .put("maximum", maximum)
        .put("writeAllowed", writeAllowed)
        .put("writeBlockReason", writeBlockReason ?: JSONObject.NULL)
}

internal data class TuningWorkspaceMenuItem(
    val menu: String,
    val group: String,
    val dialogId: String,
    val title: String,
    val conditions: List<String>,
    val conditionState: IniUiConditionState
) {
    fun toJson(): JSONObject = JSONObject()
        .put("menu", menu)
        .put("group", group)
        .put("dialogId", dialogId)
        .put("title", title)
        .put("conditions", JSONArray().also { array -> conditions.forEach { array.put(it) } })
        .put("conditionState", conditionState.toJson())
}

internal data class TuningWorkspaceDialogEntry(
    val kind: String,
    val label: String,
    val target: String,
    val conditions: List<String>,
    val conditionState: IniUiConditionState
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind)
        .put("label", label)
        .put("target", target)
        .put("conditions", JSONArray().also { array -> conditions.forEach { array.put(it) } })
        .put("conditionState", conditionState.toJson())
}

internal data class TuningWorkspaceDialog(
    val id: String,
    val title: String,
    val layout: String,
    val topicHelp: String,
    val entries: List<TuningWorkspaceDialogEntry>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("layout", layout)
        .put("topicHelp", topicHelp)
        .put("entries", JSONArray().also { array -> entries.forEach { array.put(it.toJson()) } })
}

internal data class TuningWorkspaceSnapshot(
    val generation: Long,
    val importedProfileName: String,
    val ecuSignature: String,
    val profileFingerprint: String,
    val tuneFingerprint: String,
    val capturedAtEpochMs: Long,
    val totalProfileScalars: Int,
    val skippedScalars: Int,
    val ambiguousScalarNames: Int,
    val scalars: List<TuningWorkspaceScalar>,
    val totalProfileArrays: Int,
    val skippedArrays: Int,
    val ambiguousArrayNames: Int,
    val arrays: List<TuningWorkspaceArray>,
    val totalProfileTables: Int,
    val skippedTables: Int,
    val ambiguousTableIds: Int,
    val tables: List<TuningWorkspaceTable>,
    val totalProfileCurves: Int,
    val skippedCurves: Int,
    val ambiguousCurveIds: Int,
    val curves: List<TuningWorkspaceCurve>,
    val totalProfileBitFields: Int,
    val skippedBitFields: Int,
    val ambiguousBitFieldNames: Int,
    val bitFields: List<TuningWorkspaceBitField>,
    val menuItems: List<TuningWorkspaceMenuItem>,
    val dialogs: List<TuningWorkspaceDialog>,
    val iniCompatibility: IniCompatibilityReport
) {
    fun toJson(includeArrayValues: Boolean = false): JSONObject = JSONObject()
        .put("status", "ready")
        .put("capability", "READ_ONLY")
        .put("source", "LIVE_TUNE_SNAPSHOT")
        .put("generation", generation)
        .put("importedProfileName", importedProfileName)
        .put("ecuSignature", ecuSignature)
        .put("profileFingerprint", profileFingerprint)
        .put("tuneFingerprint", tuneFingerprint)
        .put("capturedAtEpochMs", capturedAtEpochMs)
        .put("totalProfileScalars", totalProfileScalars)
        .put("readableScalars", scalars.size)
        .put("skippedScalars", skippedScalars)
        .put("ambiguousScalarNames", ambiguousScalarNames)
        .put("scalars", JSONArray().also { array -> scalars.forEach { array.put(it.toJson()) } })
        .put("totalProfileArrays", totalProfileArrays)
        .put("readableArrays", arrays.size)
        .put("skippedArrays", skippedArrays)
        .put("ambiguousArrayNames", ambiguousArrayNames)
        .put("arrays", JSONArray().also { array -> arrays.forEach { array.put(it.toJson(includeArrayValues)) } })
        .put("totalProfileTables", totalProfileTables)
        .put("readableTables", tables.size)
        .put("skippedTables", skippedTables)
        .put("ambiguousTableIds", ambiguousTableIds)
        .put("tables", JSONArray().also { array -> tables.forEach { array.put(it.toJson()) } })
        .put("totalProfileCurves", totalProfileCurves)
        .put("readableCurves", curves.size)
        .put("skippedCurves", skippedCurves)
        .put("ambiguousCurveIds", ambiguousCurveIds)
        .put("curves", JSONArray().also { array -> curves.forEach { array.put(it.toJson()) } })
        .put("totalProfileBitFields", totalProfileBitFields)
        .put("readableBitFields", bitFields.size)
        .put("skippedBitFields", skippedBitFields)
        .put("ambiguousBitFieldNames", ambiguousBitFieldNames)
        .put("bitFields", JSONArray().also { array -> bitFields.forEach { array.put(it.toJson()) } })
        .put("menuItemCount", menuItems.size)
        .put("menuItems", JSONArray().also { array -> menuItems.forEach { array.put(it.toJson()) } })
        .put("dialogCount", dialogs.size)
        .put("dialogs", JSONArray().also { array -> dialogs.forEach { array.put(it.toJson()) } })
        .put("iniCompatibility", iniCompatibility.toJson())
}

internal object TuningWorkspaceBuilder {
    fun build(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        currentGeneration: Long
    ): TuningWorkspaceSnapshot {
        require(currentGeneration > 0L) { "Tuning workspace requires a positive live generation" }
        require(profile.signature.isNotBlank()) { "Tuning workspace profile signature is missing" }
        require(profile.signature == snapshot.ecuSignature) { "Tuning workspace profile/ECU signature mismatch" }

        val profileFingerprint = profile.tuneProfileFingerprint()
        require(snapshot.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            "Tuning workspace TuneSnapshot profile fingerprint mismatch"
        }
        require(snapshot.generation == currentGeneration) {
            "Tuning workspace TuneSnapshot belongs to generation ${snapshot.generation}, current is $currentGeneration"
        }

        val conditionAuthority = IniConditionAuthority.build(profile, snapshot)

        val profilePages = profile.tunePages.groupBy { it.pageNumber }
        val snapshotPageList = snapshot.pages
        val snapshotPages = snapshotPageList.groupBy { it.pageNumber }
        // TunePageSnapshot.bytes() deliberately returns a defensive copy. Copy each complete page
        // once per workspace build instead of once per scalar.
        val snapshotBytes = snapshotPageList.associate { it.pageNumber to it.bytes() }
        val nameCounts = profile.tuneScalars.groupingBy { it.name }.eachCount()
        val ambiguousNames = nameCounts.count { it.value != 1 }

        var skipped = 0
        val readable = ArrayList<TuningWorkspaceScalar>(profile.tuneScalars.size)

        profile.tuneScalars.forEach { definition ->
            if (nameCounts[definition.name] != 1) {
                skipped++
                return@forEach
            }

            val profilePage = profilePages[definition.pageNumber]?.singleOrNull()
            val snapshotPage = snapshotPages[definition.pageNumber]?.singleOrNull()
            if (
                profilePage == null ||
                snapshotPage == null ||
                snapshotPage.identifier != profilePage.identifier ||
                snapshotPage.size != profilePage.size ||
                definition.byteSize <= 0 ||
                definition.offset < 0 ||
                definition.offset + definition.byteSize > snapshotPage.size ||
                !definition.scale.isFinite() ||
                definition.scale == 0.0 ||
                !definition.translate.isFinite()
            ) {
                skipped++
                return@forEach
            }

            val pageBytes = snapshotBytes[definition.pageNumber]
            if (pageBytes == null) {
                skipped++
                return@forEach
            }
            val raw = pageBytes.copyOfRange(definition.offset, definition.offset + definition.byteSize)
            val value = definition.decode(raw)
            if (value == null || !value.isFinite()) {
                skipped++
                return@forEach
            }

            readable += TuningWorkspaceScalar(
                name = definition.name,
                value = value,
                unit = definition.unit,
                low = definition.low.takeIf { it.isFinite() },
                high = definition.high.takeIf { it.isFinite() },
                digits = definition.digits,
                writeAllowed = conditionAuthority.writePolicy(TuningWriteKind.SCALAR, definition.name).writeAllowed,
                writeBlockReason = conditionAuthority.writePolicy(TuningWriteKind.SCALAR, definition.name).reason
            )
        }

        val arrayGroups = profile.tuneArrays.groupBy { it.name }
        val canonicalArrays = mutableListOf<UsbTuneArray>()
        var ambiguousArrayNames = 0
        var skippedArrays = 0
        arrayGroups.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalArrays += definitions.single()
                definitions.drop(1).all { equivalentArrayDefinition(definitions.first(), it) } -> {
                    canonicalArrays += definitions.first().copy(digits = definitions.maxOf { it.digits })
                    skippedArrays += definitions.size - 1
                }
                else -> {
                    ambiguousArrayNames++
                    skippedArrays += definitions.size
                }
            }
        }

        val readableArrays = ArrayList<TuningWorkspaceArray>(canonicalArrays.size)
        canonicalArrays.forEach { definition ->
            val profilePage = profilePages[definition.pageNumber]?.singleOrNull()
            val snapshotPage = snapshotPages[definition.pageNumber]?.singleOrNull()
            val pageBytes = snapshotBytes[definition.pageNumber]
            if (
                profilePage == null ||
                snapshotPage == null ||
                pageBytes == null ||
                snapshotPage.identifier != profilePage.identifier ||
                snapshotPage.size != profilePage.size ||
                definition.byteSize <= 0 ||
                definition.elementCount <= 0 ||
                definition.totalByteSize <= 0 ||
                definition.offset < 0 ||
                definition.offset.toLong() + definition.totalByteSize.toLong() > snapshotPage.size.toLong() ||
                !definition.scale.isFinite() ||
                definition.scale == 0.0 ||
                !definition.translate.isFinite() ||
                definition.low.isNaN() ||
                definition.high.isNaN() ||
                definition.low > definition.high
            ) {
                skippedArrays++
                return@forEach
            }

            val values = decodeArrayValues(definition, pageBytes)
            if (values == null || values.isEmpty()) {
                skippedArrays++
                return@forEach
            }
            readableArrays += TuningWorkspaceArray(
                name = definition.name,
                dimensions = definition.dimensions.toList(),
                elementCount = values.size,
                unit = definition.unit,
                low = definition.low.takeIf { it.isFinite() },
                high = definition.high.takeIf { it.isFinite() },
                digits = definition.digits,
                minimum = values.minOrNull() ?: 0.0,
                maximum = values.maxOrNull() ?: 0.0,
                writeAllowed = conditionAuthority.writePolicy(TuningWriteKind.ARRAY_CELL, definition.name).writeAllowed,
                writeBlockReason = conditionAuthority.writePolicy(TuningWriteKind.ARRAY_CELL, definition.name).reason,
                values = values
            )
        }

        val readableArrayByName = readableArrays.associateBy { it.name }

        val tableGroups = profile.tuneTables.groupBy { it.id }
        val canonicalTables = mutableListOf<UsbTuneTableEditor>()
        var ambiguousTableIds = 0
        var skippedTables = 0
        tableGroups.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalTables += definitions.single()
                definitions.drop(1).all { equivalentTableDefinition(definitions.first(), it) } -> {
                    canonicalTables += definitions.first()
                    skippedTables += definitions.size - 1
                }
                else -> {
                    ambiguousTableIds++
                    skippedTables += definitions.size
                }
            }
        }

        val readableTables = mutableListOf<TuningWorkspaceTable>()
        canonicalTables.forEach { definition ->
            val x = readableArrayByName[definition.xBins]
            val y = readableArrayByName[definition.yBins]
            val z = readableArrayByName[definition.zBins]
            val dims = z?.dimensions.orEmpty()
            // TunerStudio array shape is [columns x rows]: X bins are columns, Y bins are rows.
            val compatible = x != null && y != null && z != null && dims.size == 2 &&
                dims[0] == x.elementCount && dims[1] == y.elementCount
            if (!compatible) {
                skippedTables++
                return@forEach
            }
            readableTables += TuningWorkspaceTable(
                id = definition.id,
                title = definition.title,
                xBins = definition.xBins,
                yBins = definition.yBins,
                zBins = definition.zBins,
                xCount = x!!.elementCount,
                yCount = y!!.elementCount,
                unit = z!!.unit,
                minimum = z.minimum,
                maximum = z.maximum,
                writeAllowed = z.writeAllowed,
                writeBlockReason = z.writeBlockReason
            )
        }

        val curveGroups = profile.tuneCurves.groupBy { it.id }
        val canonicalCurves = mutableListOf<UsbTuneCurveEditor>()
        var ambiguousCurveIds = 0
        var skippedCurves = 0
        curveGroups.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalCurves += definitions.single()
                definitions.drop(1).all { equivalentCurveDefinition(definitions.first(), it) } -> {
                    canonicalCurves += definitions.first()
                    skippedCurves += definitions.size - 1
                }
                else -> {
                    ambiguousCurveIds++
                    skippedCurves += definitions.size
                }
            }
        }

        val readableCurves = mutableListOf<TuningWorkspaceCurve>()
        canonicalCurves.forEach { definition ->
            // First T5 slice intentionally supports one Y series only. Multi-series curve
            // definitions are preserved in the profile/fingerprint but excluded from the UI.
            if (definition.yBins.size != 1) {
                skippedCurves++
                return@forEach
            }
            val yName = definition.yBins.single()
            val x = readableArrayByName[definition.xBins]
            val y = readableArrayByName[yName]
            if (x == null || y == null || x.elementCount != y.elementCount || x.elementCount <= 0) {
                skippedCurves++
                return@forEach
            }
            readableCurves += TuningWorkspaceCurve(
                id = definition.id,
                title = definition.title,
                xBins = definition.xBins,
                yBins = yName,
                pointCount = x.elementCount,
                unit = y.unit,
                minimum = y.minimum,
                maximum = y.maximum,
                writeAllowed = y.writeAllowed,
                writeBlockReason = y.writeBlockReason
            )
        }

        val bitGroups = profile.tuneBitFields.groupBy { it.name }
        val canonicalBits = mutableListOf<UsbTuneBitField>()
        var ambiguousBitFieldNames = 0
        var skippedBitFields = 0
        bitGroups.values.forEach { definitions ->
            when {
                definitions.size == 1 -> canonicalBits += definitions.single()
                definitions.drop(1).all { equivalentBitFieldDefinition(definitions.first(), it) } -> {
                    canonicalBits += definitions.first()
                    skippedBitFields += definitions.size - 1
                }
                else -> {
                    ambiguousBitFieldNames++
                    skippedBitFields += definitions.size
                }
            }
        }

        val readableBitFields = mutableListOf<TuningWorkspaceBitField>()
        canonicalBits.forEach { definition ->
            val profilePage = profilePages[definition.pageNumber]?.singleOrNull()
            val snapshotPage = snapshotPages[definition.pageNumber]?.singleOrNull()
            val pageBytes = snapshotBytes[definition.pageNumber]
            if (
                profilePage == null ||
                snapshotPage == null ||
                pageBytes == null ||
                snapshotPage.identifier != profilePage.identifier ||
                snapshotPage.size != profilePage.size ||
                definition.byteSize <= 0 ||
                definition.offset < 0 ||
                definition.offset + definition.byteSize > snapshotPage.size
            ) {
                skippedBitFields++
                return@forEach
            }
            val raw = pageBytes.copyOfRange(definition.offset, definition.offset + definition.byteSize)
            val value = definition.decode(raw)
            if (value == null) {
                skippedBitFields++
                return@forEach
            }
            readableBitFields += TuningWorkspaceBitField(
                name = definition.name,
                value = value,
                valueLabel = definition.labelFor(value),
                options = definition.options.map { TuningWorkspaceBitOption(it.value, it.label) },
                writeAllowed = conditionAuthority.writePolicy(TuningWriteKind.BIT_FIELD, definition.name).writeAllowed,
                writeBlockReason = conditionAuthority.writePolicy(TuningWriteKind.BIT_FIELD, definition.name).reason
            )
        }

        val workspaceMenuItems = profile.tuneMenuItems.mapIndexed { index, item ->
            TuningWorkspaceMenuItem(
                menu = item.menu,
                group = item.group,
                dialogId = item.dialogId,
                title = item.title,
                conditions = item.conditions,
                conditionState = conditionAuthority.menuState(index)
            )
        }
        val workspaceDialogs = profile.tuneDialogs.map { dialog ->
            TuningWorkspaceDialog(
                id = dialog.id,
                title = dialog.title,
                layout = dialog.layout,
                topicHelp = dialog.topicHelp,
                entries = dialog.entries.mapIndexed { index, entry ->
                    TuningWorkspaceDialogEntry(
                        kind = entry.kind,
                        label = entry.label,
                        target = entry.target,
                        conditions = entry.conditions,
                        conditionState = conditionAuthority.dialogEntryState(dialog.id, index)
                    )
                }
            )
        }

        return TuningWorkspaceSnapshot(
            generation = currentGeneration,
            importedProfileName = profile.importedName,
            ecuSignature = profile.signature,
            profileFingerprint = profileFingerprint,
            tuneFingerprint = snapshot.fingerprint,
            capturedAtEpochMs = snapshot.capturedAtEpochMs,
            totalProfileScalars = profile.tuneScalars.size,
            skippedScalars = skipped,
            ambiguousScalarNames = ambiguousNames,
            scalars = readable.toList(),
            totalProfileArrays = profile.tuneArrays.size,
            skippedArrays = skippedArrays,
            ambiguousArrayNames = ambiguousArrayNames,
            arrays = readableArrays.sortedBy { it.name },
            totalProfileTables = profile.tuneTables.size,
            skippedTables = skippedTables,
            ambiguousTableIds = ambiguousTableIds,
            tables = readableTables.sortedBy { it.title.ifBlank { it.id } },
            totalProfileCurves = profile.tuneCurves.size,
            skippedCurves = skippedCurves,
            ambiguousCurveIds = ambiguousCurveIds,
            curves = readableCurves.sortedBy { it.title.ifBlank { it.id } },
            totalProfileBitFields = profile.tuneBitFields.size,
            skippedBitFields = skippedBitFields,
            ambiguousBitFieldNames = ambiguousBitFieldNames,
            bitFields = readableBitFields.sortedBy { it.name },
            menuItems = workspaceMenuItems,
            dialogs = workspaceDialogs,
            iniCompatibility = conditionAuthority.compatibility
        )
    }

    fun arrayDetail(
        profile: UsbTunerStudioProfile,
        snapshot: TuneSnapshot,
        currentGeneration: Long,
        name: String
    ): TuningWorkspaceArrayDetail {
        require(currentGeneration > 0L) { "Tuning array detail requires a positive live generation" }
        require(profile.signature.isNotBlank()) { "Tuning array detail profile signature is missing" }
        require(profile.signature == snapshot.ecuSignature) { "Tuning array detail profile/ECU signature mismatch" }
        val profileFingerprint = profile.tuneProfileFingerprint()
        require(snapshot.profileFingerprint.equals(profileFingerprint, ignoreCase = true)) {
            "Tuning array detail TuneSnapshot profile fingerprint mismatch"
        }
        require(snapshot.generation == currentGeneration) {
            "Tuning array detail TuneSnapshot belongs to generation ${snapshot.generation}, current is $currentGeneration"
        }

        val definitions = profile.tuneArrays.filter { it.name == name }
        val definition = when {
            definitions.size == 1 -> definitions.single()
            definitions.isNotEmpty() && definitions.drop(1).all { equivalentArrayDefinition(definitions.first(), it) } ->
                definitions.first().copy(digits = definitions.maxOf { it.digits })
            else -> throw IllegalArgumentException("Array '$name' is not a readable unambiguous current-profile array")
        }
        val profilePage = profile.tunePages.filter { it.pageNumber == definition.pageNumber }.singleOrNull()
            ?: throw IllegalArgumentException("Array '$name' page definition is ambiguous or missing")
        val snapshotPage = snapshot.pages.filter { it.pageNumber == definition.pageNumber }.singleOrNull()
            ?: throw IllegalArgumentException("Array '$name' snapshot page is ambiguous or missing")
        require(snapshotPage.identifier == profilePage.identifier && snapshotPage.size == profilePage.size) {
            "Array '$name' page identity does not match the current profile"
        }
        val values = decodeArrayValues(definition, snapshotPage.bytes())
            ?: throw IllegalArgumentException("Array '$name' values cannot be decoded from the current TuneSnapshot")

        return TuningWorkspaceArrayDetail(
            generation = currentGeneration,
            profileFingerprint = profileFingerprint,
            tuneFingerprint = snapshot.fingerprint,
            name = definition.name,
            dimensions = definition.dimensions.toList(),
            unit = definition.unit,
            low = definition.low.takeIf { it.isFinite() },
            high = definition.high.takeIf { it.isFinite() },
            digits = definition.digits,
            values = values
        )
    }

    private fun decodeArrayValues(definition: UsbTuneArray, pageBytes: ByteArray): List<Double>? {
        if (definition.byteSize <= 0 || definition.elementCount <= 0 || definition.totalByteSize <= 0) return null
        if (definition.offset < 0 ||
            definition.offset.toLong() + definition.totalByteSize.toLong() > pageBytes.size.toLong()
        ) return null
        val values = ArrayList<Double>(definition.elementCount)
        for (index in 0 until definition.elementCount) {
            val start = definition.offset + index * definition.byteSize
            val raw = pageBytes.copyOfRange(start, start + definition.byteSize)
            val value = definition.decode(raw) ?: return null
            if (!value.isFinite()) return null
            values += value
        }
        return values
    }

    private fun equivalentBitFieldDefinition(left: UsbTuneBitField, right: UsbTuneBitField): Boolean =
        left.pageNumber == right.pageNumber &&
            left.dataType.equals(right.dataType, ignoreCase = true) &&
            left.offset == right.offset &&
            left.bitStart == right.bitStart &&
            left.bitEnd == right.bitEnd &&
            left.options == right.options

    private fun equivalentTableDefinition(left: UsbTuneTableEditor, right: UsbTuneTableEditor): Boolean =
        left.id == right.id &&
            left.mapId == right.mapId &&
            left.title == right.title &&
            left.page == right.page &&
            left.xBins == right.xBins &&
            left.yBins == right.yBins &&
            left.zBins == right.zBins

    private fun equivalentCurveDefinition(left: UsbTuneCurveEditor, right: UsbTuneCurveEditor): Boolean =
        left.id == right.id &&
            left.title == right.title &&
            left.xBins == right.xBins &&
            left.yBins == right.yBins

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
