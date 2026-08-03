package com.buttonbox.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure characterization seam for the existing Activity-scoped legacy GPS BLE payload. */
object LegacyGpsPayloadMapper {
    const val SPEED_UNIT = "m/s"

    data class Source(
        val speedMetresPerSecond: Float,
        val latitudeDegrees: Double,
        val longitudeDegrees: Double,
        val altitudeMetres: Double?,
        val bearingDegrees: Float?,
        val accuracyMetres: Float?,
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
        val dayOfMonth: Int,
        val month: Int,
        val twoDigitYear: Int
    )

    data class State(
        val speedMetresPerSecond: Float = -1f,
        val latitudeDegrees: Double = -999.0,
        val longitudeDegrees: Double = -999.0,
        val altitudeMetres: Float = -9999f,
        val bearingDegrees: Float = -1f,
        val accuracyMetres: Float = -1f,
        val hmsdPacked: Int = -1,
        val myqsatPacked: Int = -1
    )

    data class Plan(
        val hmsdPacked: Int?,
        val myqsatPacked: Int?,
        val floatEntries: List<Pair<Int, Float>>,
        val quality: Int,
        val inferredSatellites: Int,
        val nextState: State
    )

    fun map(source: Source, previous: State): Plan {
        val quality = qualityFromAccuracy(source.accuracyMetres)
        val satellites = inferredSatellitesFromAccuracy(source.accuracyMetres)
        val hmsd = packHmsd(source.hours, source.minutes, source.seconds, source.dayOfMonth)
        val myqsat = packMyqsat(source.month, source.twoDigitYear, quality, satellites)
        val entries = mutableListOf<Pair<Int, Float>>()

        var next = previous.copy(hmsdPacked = hmsd, myqsatPacked = myqsat)
        if (source.speedMetresPerSecond != previous.speedMetresPerSecond) {
            entries += BleManager.VAR_HASH_GPS_SPEED to source.speedMetresPerSecond
            next = next.copy(speedMetresPerSecond = source.speedMetresPerSecond)
        }
        if (source.latitudeDegrees != previous.latitudeDegrees) {
            entries += BleManager.VAR_HASH_GPS_LATITUDE to source.latitudeDegrees.toFloat()
            next = next.copy(latitudeDegrees = source.latitudeDegrees)
        }
        if (source.longitudeDegrees != previous.longitudeDegrees) {
            entries += BleManager.VAR_HASH_GPS_LONGITUDE to source.longitudeDegrees.toFloat()
            next = next.copy(longitudeDegrees = source.longitudeDegrees)
        }
        source.altitudeMetres?.toFloat()?.let { altitude ->
            if (altitude != previous.altitudeMetres) {
                entries += BleManager.VAR_HASH_GPS_ALTITUDE to altitude
                next = next.copy(altitudeMetres = altitude)
            }
        }
        source.bearingDegrees?.let { bearing ->
            if (bearing != previous.bearingDegrees) {
                entries += BleManager.VAR_HASH_GPS_COURSE to bearing
                next = next.copy(bearingDegrees = bearing)
            }
        }
        source.accuracyMetres?.let { accuracy ->
            if (accuracy != previous.accuracyMetres) {
                entries += BleManager.VAR_HASH_GPS_ACCURACY to accuracy
                next = next.copy(accuracyMetres = accuracy)
            }
        }

        return Plan(
            hmsdPacked = hmsd.takeIf { it != previous.hmsdPacked },
            myqsatPacked = myqsat.takeIf { it != previous.myqsatPacked },
            floatEntries = entries,
            quality = quality,
            inferredSatellites = satellites,
            nextState = next
        )
    }

    fun qualityFromAccuracy(accuracyMetres: Float?): Int =
        if (accuracyMetres != null && accuracyMetres < 100f) 1 else 0

    fun inferredSatellitesFromAccuracy(accuracyMetres: Float?): Int =
        if (accuracyMetres != null) maxOf(4, (100f / maxOf(1f, accuracyMetres)).toInt()) else 0

    fun packHmsd(hours: Int, minutes: Int, seconds: Int, dayOfMonth: Int): Int =
        (hours and 0xFF) or ((minutes and 0xFF) shl 8) or
            ((seconds and 0xFF) shl 16) or ((dayOfMonth and 0xFF) shl 24)

    fun packMyqsat(month: Int, twoDigitYear: Int, quality: Int, satellites: Int): Int =
        (month and 0xFF) or ((twoDigitYear and 0xFF) shl 8) or
            ((quality and 0xFF) shl 16) or ((satellites and 0xFF) shl 24)

    fun encodeFloatEntries(entries: List<Pair<Int, Float>>): ByteArray =
        ByteBuffer.allocate(entries.size * 8).order(ByteOrder.BIG_ENDIAN).apply {
            entries.forEach { (hash, value) -> putInt(hash).putFloat(value) }
        }.array()

    fun encodePackedEntry(hash: Int, value: Int): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(hash).putInt(value).array()
}
