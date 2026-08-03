package com.buttonbox.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LegacyGpsPayloadMapperCharacterizationTest {
    private data class LegacyState(
        var speed: Float = -1f,
        var latitude: Double = -999.0,
        var longitude: Double = -999.0,
        var altitude: Float = -9999f,
        var bearing: Float = -1f,
        var accuracy: Float = -1f,
        var hmsd: Int = -1,
        var myqsat: Int = -1
    )

    private data class LegacyResult(val writes: List<ByteArray>, val state: LegacyState)

    private fun source(
        speed: Float = 10f,
        latitude: Double = 59.123456789,
        longitude: Double = 18.987654321,
        altitude: Double? = 42.75,
        bearing: Float? = 270.5f,
        accuracy: Float? = 4f,
        hour: Int = 12,
        minute: Int = 34,
        second: Int = 56,
        day: Int = 1,
        month: Int = 8,
        year: Int = 26
    ) = LegacyGpsPayloadMapper.Source(
        speedMetresPerSecond = speed,
        latitudeDegrees = latitude,
        longitudeDegrees = longitude,
        altitudeMetres = altitude,
        bearingDegrees = bearing,
        accuracyMetres = accuracy,
        hours = hour,
        minutes = minute,
        seconds = second,
        dayOfMonth = day,
        month = month,
        twoDigitYear = year
    )

    @Test fun `preserves legacy GPS speed field and metres per second unit`() {
        val plan = LegacyGpsPayloadMapper.map(source(speed = 10f), LegacyGpsPayloadMapper.State())
        assertEquals("m/s", LegacyGpsPayloadMapper.SPEED_UNIT)
        assertEquals(10f, plan.floatEntries.first { it.first == BleManager.VAR_HASH_GPS_SPEED }.second)
    }

    @Test fun `zero non finite and missing optional data preserve legacy change semantics`() {
        val zero = LegacyGpsPayloadMapper.map(source(speed = 0f, altitude = null, bearing = null, accuracy = null), LegacyGpsPayloadMapper.State())
        assertEquals(0f, zero.floatEntries.first { it.first == BleManager.VAR_HASH_GPS_SPEED }.second)
        assertEquals(3, zero.floatEntries.size)
        assertEquals(0, zero.quality)
        assertEquals(0, zero.inferredSatellites)

        val nan = LegacyGpsPayloadMapper.map(source(speed = Float.NaN), LegacyGpsPayloadMapper.State())
        assertTrue(nan.floatEntries.first { it.first == BleManager.VAR_HASH_GPS_SPEED }.second.isNaN())
        val infinity = LegacyGpsPayloadMapper.map(source(speed = Float.POSITIVE_INFINITY), LegacyGpsPayloadMapper.State())
        assertEquals(Float.POSITIVE_INFINITY, infinity.floatEntries.first().second)
    }

    @Test fun `unchanged finite fields are suppressed but NaN speed repeats`() {
        val first = LegacyGpsPayloadMapper.map(source(), LegacyGpsPayloadMapper.State())
        val repeated = LegacyGpsPayloadMapper.map(source(), first.nextState)
        assertTrue(repeated.floatEntries.isEmpty())
        assertEquals(null, repeated.hmsdPacked)
        assertEquals(null, repeated.myqsatPacked)

        val nanFirst = LegacyGpsPayloadMapper.map(source(speed = Float.NaN), LegacyGpsPayloadMapper.State())
        val nanRepeated = LegacyGpsPayloadMapper.map(source(speed = Float.NaN), nanFirst.nextState)
        assertTrue(nanRepeated.floatEntries.any { it.first == BleManager.VAR_HASH_GPS_SPEED })
    }

    @Test fun `preserves inferred quality boundaries`() {
        assertEquals(1, LegacyGpsPayloadMapper.qualityFromAccuracy(0f))
        assertEquals(1, LegacyGpsPayloadMapper.qualityFromAccuracy(99.999f))
        assertEquals(0, LegacyGpsPayloadMapper.qualityFromAccuracy(100f))
        assertEquals(0, LegacyGpsPayloadMapper.qualityFromAccuracy(null))
        assertEquals(0, LegacyGpsPayloadMapper.qualityFromAccuracy(Float.NaN))
        assertEquals(1, LegacyGpsPayloadMapper.qualityFromAccuracy(Float.NEGATIVE_INFINITY))
    }

    @Test fun `preserves inferred satellite count and minimum four behavior`() {
        assertEquals(0, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(null))
        assertEquals(100, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(0f))
        assertEquals(100, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(1f))
        assertEquals(25, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(4f))
        assertEquals(4, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(25f))
        assertEquals(4, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(100f))
        assertEquals(4, LegacyGpsPayloadMapper.inferredSatellitesFromAccuracy(Float.POSITIVE_INFINITY))
    }

    @Test fun `packed wall clock quality and inferred satellites preserve byte fields`() {
        val plan = LegacyGpsPayloadMapper.map(source(), LegacyGpsPayloadMapper.State())
        assertEquals(0x0138220c, plan.hmsdPacked)
        assertEquals(0x19011a08, plan.myqsatPacked)
    }

    @Test fun `float batch payload preserves ordering size precision and big endian bytes`() {
        val entries = listOf(
            BleManager.VAR_HASH_GPS_SPEED to 10.25f,
            BleManager.VAR_HASH_GPS_ACCURACY to 4.5f
        )
        val encoded = LegacyGpsPayloadMapper.encodeFloatEntries(entries)
        assertEquals(16, encoded.size)
        val decoded = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        assertEquals(BleManager.VAR_HASH_GPS_SPEED, decoded.int)
        assertEquals(10.25f, decoded.float)
        assertEquals(BleManager.VAR_HASH_GPS_ACCURACY, decoded.int)
        assertEquals(4.5f, decoded.float)
    }

    @Test fun `packed entry is exactly hash then uint32 big endian`() {
        val value = 0x19011a08
        val expected = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(BleManager.VAR_HASH_GPS_MYQSAT_PACKED).putInt(value).array()
        assertArrayEquals(expected, LegacyGpsPayloadMapper.encodePackedEntry(BleManager.VAR_HASH_GPS_MYQSAT_PACKED, value))
    }

    @Test fun `extracted mapper is byte identical to independent legacy oracle across edge fixtures`() {
        val fixtures = listOf(
            source(altitude = null, bearing = null, accuracy = null),
            source(),
            source(speed = 0f),
            source(speed = 12.345678f),
            source(speed = -2f),
            source(speed = Float.NaN),
            source(speed = Float.POSITIVE_INFINITY),
            source(accuracy = 99.999f),
            source(accuracy = 100f)
        )

        fixtures.forEach { fixture ->
            val legacy = legacyOracle(fixture, LegacyState())
            val extracted = LegacyGpsPayloadMapper.map(fixture, LegacyGpsPayloadMapper.State())
            assertWritesEqual(legacy.writes, extractedWrites(extracted))
            assertStateEqual(legacy.state, extracted.nextState)
        }
    }

    @Test fun `extracted mapper matches legacy oracle for unchanged and NaN repeated values`() {
        listOf(source(), source(speed = Float.NaN)).forEach { fixture ->
            val legacyFirst = legacyOracle(fixture, LegacyState())
            val extractedFirst = LegacyGpsPayloadMapper.map(fixture, LegacyGpsPayloadMapper.State())
            val legacySecond = legacyOracle(fixture, legacyFirst.state)
            val extractedSecond = LegacyGpsPayloadMapper.map(fixture, extractedFirst.nextState)
            assertWritesEqual(legacySecond.writes, extractedWrites(extractedSecond))
            assertStateEqual(legacySecond.state, extractedSecond.nextState)
        }
    }

    private fun legacyOracle(source: LegacyGpsPayloadMapper.Source, state: LegacyState): LegacyResult {
        val writes = mutableListOf<ByteArray>()
        val entries = mutableListOf<Pair<Int, Float>>()
        val accuracy = source.accuracyMetres
        val quality = if (accuracy != null && accuracy < 100f) 1 else 0
        val satellites = if (accuracy != null) {
            maxOf(4, (100f / maxOf(1f, accuracy)).toInt())
        } else 0
        val hmsd = (source.hours and 0xFF) or ((source.minutes and 0xFF) shl 8) or
            ((source.seconds and 0xFF) shl 16) or ((source.dayOfMonth and 0xFF) shl 24)
        if (hmsd != state.hmsd) {
            state.hmsd = hmsd
            writes += encodeLegacyInts(BleManager.VAR_HASH_GPS_HMSD_PACKED, hmsd)
        }
        val myqsat = (source.month and 0xFF) or ((source.twoDigitYear and 0xFF) shl 8) or
            ((quality and 0xFF) shl 16) or ((satellites and 0xFF) shl 24)
        if (myqsat != state.myqsat) {
            state.myqsat = myqsat
            writes += encodeLegacyInts(BleManager.VAR_HASH_GPS_MYQSAT_PACKED, myqsat)
        }
        if (source.speedMetresPerSecond != state.speed) {
            state.speed = source.speedMetresPerSecond
            entries += BleManager.VAR_HASH_GPS_SPEED to source.speedMetresPerSecond
        }
        if (source.latitudeDegrees != state.latitude) {
            state.latitude = source.latitudeDegrees
            entries += BleManager.VAR_HASH_GPS_LATITUDE to source.latitudeDegrees.toFloat()
        }
        if (source.longitudeDegrees != state.longitude) {
            state.longitude = source.longitudeDegrees
            entries += BleManager.VAR_HASH_GPS_LONGITUDE to source.longitudeDegrees.toFloat()
        }
        source.altitudeMetres?.toFloat()?.let {
            if (it != state.altitude) {
                state.altitude = it
                entries += BleManager.VAR_HASH_GPS_ALTITUDE to it
            }
        }
        source.bearingDegrees?.let {
            if (it != state.bearing) {
                state.bearing = it
                entries += BleManager.VAR_HASH_GPS_COURSE to it
            }
        }
        source.accuracyMetres?.let {
            if (it != state.accuracy) {
                state.accuracy = it
                entries += BleManager.VAR_HASH_GPS_ACCURACY to it
            }
        }
        if (entries.isNotEmpty()) writes += encodeLegacyFloats(entries)
        return LegacyResult(writes, state)
    }

    private fun extractedWrites(plan: LegacyGpsPayloadMapper.Plan): List<ByteArray> = buildList {
        plan.hmsdPacked?.let { add(LegacyGpsPayloadMapper.encodePackedEntry(BleManager.VAR_HASH_GPS_HMSD_PACKED, it)) }
        plan.myqsatPacked?.let { add(LegacyGpsPayloadMapper.encodePackedEntry(BleManager.VAR_HASH_GPS_MYQSAT_PACKED, it)) }
        if (plan.floatEntries.isNotEmpty()) add(LegacyGpsPayloadMapper.encodeFloatEntries(plan.floatEntries))
    }

    private fun encodeLegacyFloats(entries: List<Pair<Int, Float>>): ByteArray =
        entries.flatMap { (hash, value) -> intBytes(hash).asIterable() + intBytes(value.toRawBits()).asIterable() }
            .toByteArray()

    private fun encodeLegacyInts(hash: Int, value: Int): ByteArray =
        (intBytes(hash).asIterable() + intBytes(value).asIterable()).toByteArray()

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun assertWritesEqual(expected: List<ByteArray>, actual: List<ByteArray>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedWrite, actualWrite) ->
            assertArrayEquals(expectedWrite, actualWrite)
        }
    }

    private fun assertStateEqual(expected: LegacyState, actual: LegacyGpsPayloadMapper.State) {
        assertEquals(expected.speed, actual.speedMetresPerSecond)
        assertEquals(expected.latitude, actual.latitudeDegrees, 0.0)
        assertEquals(expected.longitude, actual.longitudeDegrees, 0.0)
        assertEquals(expected.altitude, actual.altitudeMetres)
        assertEquals(expected.bearing, actual.bearingDegrees)
        assertEquals(expected.accuracy, actual.accuracyMetres)
        assertEquals(expected.hmsd, actual.hmsdPacked)
        assertEquals(expected.myqsat, actual.myqsatPacked)
    }
}
