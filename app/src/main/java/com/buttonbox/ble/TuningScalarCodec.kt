package com.buttonbox.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.round

/** Production engineering-value encoder shared by semantic preview and execution paths. */
internal object TuningScalarCodec {
    fun encode(definition: UsbTuneScalar, engineeringValue: Double): ByteArray {
        require(engineeringValue.isFinite()) { "Proposed value must be finite" }
        require(definition.accepts(engineeringValue)) {
            "Proposed value $engineeringValue is outside profile bounds ${definition.low}..${definition.high}"
        }
        require(definition.scale.isFinite() && definition.scale != 0.0) {
            "Invalid scalar scale ${definition.scale}"
        }
        require(definition.translate.isFinite()) {
            "Invalid scalar translation ${definition.translate}"
        }
        val raw = (engineeringValue - definition.translate) / definition.scale
        require(raw.isFinite()) { "Proposed value cannot be represented by scalar scaling" }

        val buffer = ByteBuffer.allocate(definition.byteSize).order(ByteOrder.LITTLE_ENDIAN)
        when (definition.dataType.uppercase(Locale.US)) {
            "U08" -> {
                val value = integerRaw(raw, 0L, 0xffL, definition)
                buffer.put(value.toByte())
            }
            "S08" -> {
                val value = integerRaw(raw, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong(), definition)
                buffer.put(value.toByte())
            }
            "U16" -> {
                val value = integerRaw(raw, 0L, 0xffffL, definition)
                buffer.putShort(value.toShort())
            }
            "S16" -> {
                val value = integerRaw(raw, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong(), definition)
                buffer.putShort(value.toShort())
            }
            "U32" -> {
                val value = integerRaw(raw, 0L, 0xffff_ffffL, definition)
                buffer.putInt(value.toInt())
            }
            "S32" -> {
                val value = integerRaw(raw, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong(), definition)
                buffer.putInt(value.toInt())
            }
            "F32" -> {
                val value = raw.toFloat()
                require(value.isFinite()) { "Proposed value overflows F32 raw representation" }
                buffer.putFloat(value)
            }
            else -> throw IllegalArgumentException("Unsupported scalar type ${definition.dataType}")
        }
        return buffer.array()
    }

    private fun integerRaw(raw: Double, min: Long, max: Long, definition: UsbTuneScalar): Long {
        val rounded = round(raw)
        require(rounded.isFinite() && rounded >= min.toDouble() && rounded <= max.toDouble()) {
            "Proposed value overflows ${definition.dataType} raw representation"
        }
        return rounded.toLong()
    }
}
