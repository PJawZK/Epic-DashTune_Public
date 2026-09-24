package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal enum class IniConditionTruth { TRUE, FALSE, UNSUPPORTED }

internal data class IniConditionExpressionResult(
    val expression: String,
    val truth: IniConditionTruth,
    val reason: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("expression", expression)
        .put("status", when (truth) {
            IniConditionTruth.TRUE -> "true"
            IniConditionTruth.FALSE -> "false"
            IniConditionTruth.UNSUPPORTED -> "unsupported"
        })
        .put("reason", reason ?: JSONObject.NULL)
}

internal data class IniUiConditionState(
    val enabled: Boolean,
    val visible: Boolean,
    val supported: Boolean,
    val evaluations: List<IniConditionExpressionResult>,
    val reason: String?
) {
    val status: String
        get() = when {
            !supported -> "unsupported"
            !visible -> "hidden"
            !enabled -> "disabled"
            else -> "active"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("status", status)
        .put("enabled", enabled)
        .put("visible", visible)
        .put("supported", supported)
        .put("reason", reason ?: JSONObject.NULL)
        .put("evaluations", JSONArray().also { array -> evaluations.forEach { array.put(it.toJson()) } })

    companion object {
        fun unconditional() = IniUiConditionState(true, true, true, emptyList(), null)

        fun evaluate(conditions: List<String>, evaluator: IniConditionEvaluator): IniUiConditionState {
            if (conditions.isEmpty()) return unconditional()

            val results = conditions.map { evaluator.evaluate(it) }
            val tooMany = conditions.size > 2
            val enable = results.getOrNull(0)
            val visibility = results.getOrNull(1)
            val supported = !tooMany && results.all { it.truth != IniConditionTruth.UNSUPPORTED }
            val enabledValue = when (enable?.truth) {
                null, IniConditionTruth.TRUE -> true
                IniConditionTruth.FALSE, IniConditionTruth.UNSUPPORTED -> false
            }
            val visibleValue = when (visibility?.truth) {
                null, IniConditionTruth.TRUE -> true
                IniConditionTruth.FALSE, IniConditionTruth.UNSUPPORTED -> false
            }
            val reason = when {
                tooMany -> "More than two INI row conditions are not supported"
                results.any { it.truth == IniConditionTruth.UNSUPPORTED } ->
                    results.first { it.truth == IniConditionTruth.UNSUPPORTED }.reason
                !visibleValue -> "INI visibility condition is false"
                !enabledValue -> "INI enabled condition is false"
                else -> null
            }
            return IniUiConditionState(
                enabled = if (tooMany) false else enabledValue,
                visible = if (tooMany) false else visibleValue,
                supported = supported,
                evaluations = results,
                reason = reason
            )
        }
    }
}

internal data class IniCompatibilityReport(
    val state: String,
    val totalConditionExpressions: Int,
    val unsupportedConditionExpressions: Int,
    val disabledEntries: Int,
    val hiddenEntries: Int,
    val reason: String,
    val unsupportedExamples: List<String>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("state", state)
        .put("totalConditionExpressions", totalConditionExpressions)
        .put("unsupportedConditionExpressions", unsupportedConditionExpressions)
        .put("disabledEntries", disabledEntries)
        .put("hiddenEntries", hiddenEntries)
        .put("reason", reason)
        .put("unsupportedExamples", JSONArray().also { array -> unsupportedExamples.forEach { array.put(it) } })
}

internal data class IniTargetConditionPolicy(
    val constrained: Boolean,
    val writeAllowed: Boolean,
    val reason: String?
)

private data class IniEffectiveGate(
    val enabled: Boolean,
    val visible: Boolean,
    val supported: Boolean,
    val reasons: List<String>
) {
    val active: Boolean get() = enabled && visible && supported

    fun combine(state: IniUiConditionState): IniEffectiveGate = IniEffectiveGate(
        enabled = enabled && state.enabled,
        visible = visible && state.visible,
        supported = supported && state.supported,
        reasons = reasons + if (!state.supported) listOfNotNull(state.reason) else emptyList()
    )

    companion object {
        fun open() = IniEffectiveGate(true, true, true, emptyList())
    }
}

internal class IniConditionAuthority private constructor(
    val menuStates: List<IniUiConditionState>,
    val dialogEntryStates: Map<String, List<IniUiConditionState>>,
    val compatibility: IniCompatibilityReport,
    private val writePolicies: Map<String, IniTargetConditionPolicy>
) {
    fun menuState(index: Int): IniUiConditionState =
        menuStates.getOrNull(index) ?: IniUiConditionState.unconditional()

    fun dialogEntryState(dialogId: String, index: Int): IniUiConditionState =
        dialogEntryStates[dialogId]?.getOrNull(index) ?: IniUiConditionState.unconditional()

    fun writePolicy(kind: TuningWriteKind, name: String): IniTargetConditionPolicy =
        writePolicies[targetKey(kind, name)] ?: IniTargetConditionPolicy(false, true, null)

    fun requireWriteAllowed(request: SemanticTuningWriteRequest) {
        val policy = writePolicy(request.kind, request.name)
        require(policy.writeAllowed) {
            policy.reason ?: "Current INI conditions block '${request.name}'"
        }
    }

    companion object {
        fun build(profile: UsbTunerStudioProfile, snapshot: TuneSnapshot): IniConditionAuthority {
            val evaluator = IniConditionEvaluator(decodeConditionValues(profile, snapshot))
            val menuStates = profile.tuneMenuItems.map { IniUiConditionState.evaluate(it.conditions, evaluator) }
            val dialogEntryStates = profile.tuneDialogs.associate { dialog ->
                dialog.id to dialog.entries.map { IniUiConditionState.evaluate(it.conditions, evaluator) }
            }

            val allStates = menuStates + dialogEntryStates.values.flatten()
            val allResults = allStates.flatMap { it.evaluations }
            val unsupported = allResults.filter { it.truth == IniConditionTruth.UNSUPPORTED }
            val compatibility = IniCompatibilityReport(
                state = if (unsupported.isEmpty()) "green" else "amber",
                totalConditionExpressions = allResults.size,
                unsupportedConditionExpressions = unsupported.size,
                disabledEntries = allStates.count { it.visible && !it.enabled },
                hiddenEntries = allStates.count { !it.visible },
                reason = if (unsupported.isEmpty()) {
                    "Matching INI active; all parsed menu/dialog conditions are supported"
                } else {
                    "Matching INI active with ${unsupported.size} unsupported or unresolved condition expression(s); affected controls fail closed"
                },
                unsupportedExamples = unsupported.map { it.expression }.distinct().take(8)
            )

            val occurrences = linkedMapOf<String, MutableList<IniEffectiveGate>>()
            val scalarNames = profile.tuneScalars.mapTo(hashSetOf()) { it.name }
            val bitNames = profile.tuneBitFields.mapTo(hashSetOf()) { it.name }
            val arrayNames = profile.tuneArrays.mapTo(hashSetOf()) { it.name }
            val dialogs = profile.tuneDialogs.associateBy { it.id }
            val tables = profile.tuneTables.associateBy { it.id }
            val curves = profile.tuneCurves.associateBy { it.id }
            val routedDialogIds = hashSetOf<String>()

            fun record(kind: TuningWriteKind, name: String, gate: IniEffectiveGate) {
                if (name.isNotBlank()) occurrences.getOrPut(targetKey(kind, name)) { mutableListOf() } += gate
            }

            fun recordSurface(surfaceId: String, gate: IniEffectiveGate): Boolean {
                tables[surfaceId]?.let {
                    record(TuningWriteKind.ARRAY_CELL, it.zBins, gate)
                    return true
                }
                curves[surfaceId]?.let {
                    it.yBins.forEach { name -> record(TuningWriteKind.ARRAY_CELL, name, gate) }
                    return true
                }
                return false
            }

            lateinit var walkDialog: (String, IniEffectiveGate, Set<String>, Int) -> Unit
            walkDialog = { dialogId, parentGate, path, depth ->
                if (depth <= 12 && dialogId !in path) {
                    val dialog = dialogs[dialogId]
                    if (dialog != null) {
                        routedDialogIds += dialogId
                        val states = dialogEntryStates[dialogId].orEmpty()
                        dialog.entries.forEachIndexed { index, entry ->
                            val gate = parentGate.combine(states.getOrNull(index) ?: IniUiConditionState.unconditional())
                            val target = entry.target
                            when (entry.kind) {
                                "field" -> when {
                                    target in scalarNames -> record(TuningWriteKind.SCALAR, target, gate)
                                    target in bitNames -> record(TuningWriteKind.BIT_FIELD, target, gate)
                                    target in arrayNames -> record(TuningWriteKind.ARRAY_CELL, target, gate)
                                    else -> recordSurface(target, gate)
                                }
                                "panel" -> if (!recordSurface(target, gate) && target in dialogs) {
                                    walkDialog(target, gate, path + dialogId, depth + 1)
                                }
                            }
                        }
                    }
                }
            }

            profile.tuneMenuItems.forEachIndexed { index, menuItem ->
                val gate = IniEffectiveGate.open().combine(
                    menuStates.getOrNull(index) ?: IniUiConditionState.unconditional()
                )
                if (!recordSurface(menuItem.dialogId, gate) && menuItem.dialogId in dialogs) {
                    walkDialog(menuItem.dialogId, gate, emptySet(), 0)
                }
            }

            profile.tuneDialogs
                .filter { it.id !in routedDialogIds }
                .forEach { walkDialog(it.id, IniEffectiveGate.open(), emptySet(), 0) }

            val policies = occurrences.mapValues { (key, gates) ->
                val allowed = gates.any { it.active }
                val unsupportedGate = gates.firstOrNull { !it.supported }
                IniTargetConditionPolicy(
                    constrained = true,
                    writeAllowed = allowed,
                    reason = if (allowed) null else if (unsupportedGate != null) {
                        val detail = unsupportedGate.reasons.firstOrNull()?.take(140)
                        "Current INI condition for '${key.substringAfter(':')}' is unsupported or unresolved; write blocked" +
                            (detail?.let { ": $it" } ?: "")
                    } else {
                        "Current INI conditions disable or hide '${key.substringAfter(':')}'; write blocked"
                    }
                )
            }

            return IniConditionAuthority(menuStates, dialogEntryStates, compatibility, policies)
        }

        private fun targetKey(kind: TuningWriteKind, name: String): String = when (kind) {
            TuningWriteKind.SCALAR -> "scalar:$name"
            TuningWriteKind.ARRAY_CELL -> "array:$name"
            TuningWriteKind.BIT_FIELD -> "bitField:$name"
        }

        private fun decodeConditionValues(
            profile: UsbTunerStudioProfile,
            snapshot: TuneSnapshot
        ): Map<String, Double> {
            val result = linkedMapOf<String, Double>()
            val pageList = snapshot.pages
            val pages = pageList.groupBy { it.pageNumber }
            // TunePageSnapshot.bytes() returns a full defensive copy. Keep one immutable local
            // copy per tune page for this complete condition evaluation instead of copying a
            // 55+ KiB page separately for every scalar and bit-field definition.
            val snapshotBytes = pageList.associate { it.pageNumber to it.bytes() }

            profile.tuneScalars.groupBy { it.name }.forEach { (name, definitions) ->
                if (definitions.size != 1) return@forEach
                val definition = definitions.single()
                val page = pages[definition.pageNumber]?.singleOrNull() ?: return@forEach
                val bytes = snapshotBytes[definition.pageNumber] ?: return@forEach
                if (definition.byteSize <= 0 || definition.offset < 0 ||
                    definition.offset + definition.byteSize > page.size
                ) return@forEach
                definition.decode(bytes.copyOfRange(definition.offset, definition.offset + definition.byteSize))
                    ?.takeIf { it.isFinite() }
                    ?.let { result[name] = it }
            }

            profile.tuneBitFields.groupBy { it.name }.forEach { (name, definitions) ->
                val first = definitions.firstOrNull() ?: return@forEach
                if (definitions.drop(1).any { !equivalentBit(first, it) }) return@forEach
                val page = pages[first.pageNumber]?.singleOrNull() ?: return@forEach
                val bytes = snapshotBytes[first.pageNumber] ?: return@forEach
                if (first.byteSize <= 0 || first.offset < 0 || first.offset + first.byteSize > page.size) return@forEach
                first.decode(bytes.copyOfRange(first.offset, first.offset + first.byteSize))
                    ?.let { result[name] = it.toDouble() }
            }

            return result
        }

        private fun equivalentBit(left: UsbTuneBitField, right: UsbTuneBitField): Boolean =
            left.pageNumber == right.pageNumber &&
                left.dataType.equals(right.dataType, ignoreCase = true) &&
                left.offset == right.offset &&
                left.bitStart == right.bitStart &&
                left.bitEnd == right.bitEnd &&
                left.options == right.options
    }
}

internal class IniConditionEvaluator(private val values: Map<String, Double>) {
    fun evaluate(expression: String): IniConditionExpressionResult {
        val source = expression.trim()
        if (source.isEmpty()) return IniConditionExpressionResult(
            source, IniConditionTruth.UNSUPPORTED, "Empty INI condition"
        )
        return try {
            val numeric = Parser(source, values).parse()
            IniConditionExpressionResult(
                source,
                if (truthy(numeric)) IniConditionTruth.TRUE else IniConditionTruth.FALSE
            )
        } catch (failure: ConditionFailure) {
            IniConditionExpressionResult(
                source,
                IniConditionTruth.UNSUPPORTED,
                failure.message ?: "Unsupported INI condition"
            )
        }
    }

    private class ConditionFailure(message: String) : IllegalArgumentException(message)
    private enum class TokenKind { NUMBER, IDENTIFIER, OPERATOR, LPAREN, RPAREN, END }
    private data class Token(val kind: TokenKind, val text: String, val number: Double? = null)

    private class Lexer(private val source: String) {
        private var index = 0

        fun next(): Token {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) return Token(TokenKind.END, "")

            val ch = source[index]
            if (ch == '(') {
                index++
                return Token(TokenKind.LPAREN, "(")
            }
            if (ch == ')') {
                index++
                return Token(TokenKind.RPAREN, ")")
            }
            if (ch.isDigit() || (ch == '.' && index + 1 < source.length && source[index + 1].isDigit())) {
                return readNumber()
            }
            if (ch.isLetter() || ch == '_' || ch == '$') {
                val start = index++
                while (index < source.length) {
                    val c = source[index]
                    if (!(c.isLetterOrDigit() || c == '_' || c == '.' || c == '$')) break
                    index++
                }
                return Token(TokenKind.IDENTIFIER, source.substring(start, index))
            }

            val operators = listOf(
                "&&", "||", "==", "!=", ">=", "<=", "<<", ">>",
                "+", "-", "*", "/", "%", "!", "~", ">", "<", "&", "|", "^", "="
            )
            val operator = operators.firstOrNull { source.startsWith(it, index) }
                ?: throw ConditionFailure("Unsupported token '${source[index]}'")
            index += operator.length
            return Token(TokenKind.OPERATOR, operator)
        }

        private fun readNumber(): Token {
            val start = index
            if (source.startsWith("0x", index, ignoreCase = true)) {
                index += 2
                val digitsStart = index
                while (index < source.length && source[index].digitToIntOrNull(16) != null) index++
                if (index == digitsStart) throw ConditionFailure("Invalid hexadecimal number")
                val raw = source.substring(digitsStart, index)
                val value = raw.toLongOrNull(16)?.toDouble()
                    ?: throw ConditionFailure("Hexadecimal number is out of range")
                return Token(TokenKind.NUMBER, source.substring(start, index), value)
            }

            var sawDigit = false
            while (index < source.length && source[index].isDigit()) {
                index++
                sawDigit = true
            }
            if (index < source.length && source[index] == '.') {
                index++
                while (index < source.length && source[index].isDigit()) {
                    index++
                    sawDigit = true
                }
            }
            if (!sawDigit) throw ConditionFailure("Invalid number")
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                index++
                if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
                val exponentStart = index
                while (index < source.length && source[index].isDigit()) index++
                if (index == exponentStart) throw ConditionFailure("Invalid numeric exponent")
            }
            val raw = source.substring(start, index)
            val value = raw.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw ConditionFailure("Invalid numeric literal '$raw'")
            return Token(TokenKind.NUMBER, raw, value)
        }
    }

    private class Parser(source: String, private val values: Map<String, Double>) {
        private val lexer = Lexer(source)
        private var token = lexer.next()

        fun parse(): Double {
            val value = logicalOr()
            if (token.kind != TokenKind.END) throw ConditionFailure("Unexpected token '${token.text}'")
            return finite(value)
        }

        private fun logicalOr(): Double {
            var value = logicalAnd()
            while (accept("||")) {
                val right = logicalAnd()
                value = bool(truthy(value) || truthy(right))
            }
            return value
        }

        private fun logicalAnd(): Double {
            var value = bitOr()
            while (accept("&&")) {
                val right = bitOr()
                value = bool(truthy(value) && truthy(right))
            }
            return value
        }

        private fun bitOr(): Double {
            var value = bitXor()
            while (accept("|")) value = integerBinary(value, bitXor()) { a, b -> a or b }
            return value
        }

        private fun bitXor(): Double {
            var value = bitAnd()
            while (accept("^")) value = integerBinary(value, bitAnd()) { a, b -> a xor b }
            return value
        }

        private fun bitAnd(): Double {
            var value = equality()
            while (accept("&")) value = integerBinary(value, equality()) { a, b -> a and b }
            return value
        }

        private fun equality(): Double {
            var value = comparison()
            while (token.kind == TokenKind.OPERATOR && token.text in setOf("==", "!=", "=")) {
                val operator = token.text
                advance()
                val right = comparison()
                value = bool(if (operator == "!=") value != right else value == right)
            }
            return value
        }

        private fun comparison(): Double {
            var value = shift()
            while (token.kind == TokenKind.OPERATOR && token.text in setOf(">", "<", ">=", "<=")) {
                val operator = token.text
                advance()
                val right = shift()
                value = bool(
                    when (operator) {
                        ">" -> value > right
                        "<" -> value < right
                        ">=" -> value >= right
                        else -> value <= right
                    }
                )
            }
            return value
        }

        private fun shift(): Double {
            var value = additive()
            while (token.kind == TokenKind.OPERATOR && token.text in setOf("<<", ">>")) {
                val operator = token.text
                advance()
                val right = requireInteger(additive())
                if (right !in 0L..63L) throw ConditionFailure("Shift count must be 0..63")
                val left = requireInteger(value)
                value = (if (operator == "<<") left shl right.toInt() else left shr right.toInt()).toDouble()
            }
            return value
        }

        private fun additive(): Double {
            var value = multiplicative()
            while (token.kind == TokenKind.OPERATOR && token.text in setOf("+", "-")) {
                val operator = token.text
                advance()
                val right = multiplicative()
                value = finite(if (operator == "+") value + right else value - right)
            }
            return value
        }

        private fun multiplicative(): Double {
            var value = unary()
            while (token.kind == TokenKind.OPERATOR && token.text in setOf("*", "/", "%")) {
                val operator = token.text
                advance()
                val right = unary()
                if ((operator == "/" || operator == "%") && right == 0.0) {
                    throw ConditionFailure("Division by zero")
                }
                value = finite(
                    when (operator) {
                        "*" -> value * right
                        "/" -> value / right
                        else -> value % right
                    }
                )
            }
            return value
        }

        private fun unary(): Double {
            if (token.kind == TokenKind.OPERATOR && token.text in setOf("!", "~", "+", "-")) {
                val operator = token.text
                advance()
                val value = unary()
                return when (operator) {
                    "!" -> bool(!truthy(value))
                    "~" -> requireInteger(value).inv().toDouble()
                    "+" -> value
                    else -> finite(-value)
                }
            }
            return primary()
        }

        private fun primary(): Double = when (token.kind) {
            TokenKind.NUMBER -> token.number!!.also { advance() }
            TokenKind.IDENTIFIER -> {
                val name = token.text
                advance()
                when {
                    name.equals("true", ignoreCase = true) -> 1.0
                    name.equals("false", ignoreCase = true) -> 0.0
                    else -> values[name]
                        ?: throw ConditionFailure("Condition identifier '$name' is unavailable in the current TuneSnapshot")
                }
            }
            TokenKind.LPAREN -> {
                advance()
                val value = logicalOr()
                if (token.kind != TokenKind.RPAREN) throw ConditionFailure("Missing closing parenthesis")
                advance()
                value
            }
            else -> throw ConditionFailure("Expected number, identifier or parenthesized expression")
        }

        private fun accept(operator: String): Boolean {
            if (token.kind == TokenKind.OPERATOR && token.text == operator) {
                advance()
                return true
            }
            return false
        }

        private fun advance() {
            token = lexer.next()
        }

        private fun requireInteger(value: Double): Long {
            if (!value.isFinite() || abs(value) > 9_007_199_254_740_991.0) {
                throw ConditionFailure("Bitwise operand is outside exact integer range")
            }
            val integer = value.toLong()
            if (integer.toDouble() != value) throw ConditionFailure("Bitwise operand must be an integer")
            return integer
        }

        private fun integerBinary(left: Double, right: Double, operation: (Long, Long) -> Long): Double =
            operation(requireInteger(left), requireInteger(right)).toDouble()

        private fun finite(value: Double): Double =
            value.takeIf { it.isFinite() } ?: throw ConditionFailure("Condition produced a non-finite value")
    }

    companion object {
        private fun truthy(value: Double): Boolean = value.isFinite() && value != 0.0
        private fun bool(value: Boolean): Double = if (value) 1.0 else 0.0
        private fun finite(value: Double): Double =
            value.takeIf { it.isFinite() } ?: throw ConditionFailure("Condition produced a non-finite value")
    }
}
