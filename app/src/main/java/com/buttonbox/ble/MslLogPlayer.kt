package com.buttonbox.ble

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Arrays
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Native TunerStudio .msl parser and timestamp-driven playback engine.
 *
 * EpicEFI logs can contain well over a thousand columns and hundreds of MB of
 * text. The parser scans each row but creates strings only for the small set of
 * dashboard fields. Samples are stored column-wise in primitive arrays so a
 * large log remains practical on older Android hardware.
 */
class MslLogPlayer(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onLoading(fileName: String)
        fun onParseProgress(rows: Int)
        fun onLoaded(metadata: JSONObject)
        fun onSample(timeSeconds: Double, values: JSONObject)
        fun onPosition(positionSeconds: Double, durationSeconds: Double, paused: Boolean, speed: Double)
        fun onLooped()
        fun onStopped()
        fun onError(message: String)
    }

    private data class ChannelSpec(
        val key: String,
        val aliases: List<String>,
        val zeroOnlyMeansDisabled: Boolean = false
    )

    private data class ParsedLog(
        val fileName: String,
        val signature: String,
        val times: DoubleArray,
        val values: Array<FloatArray>,
        val enabledChannels: BooleanArray,
        val mappedChannels: List<String>,
        val recognizedChannels: List<String>,
        val missingChannels: List<String>,
        val disabledChannels: JSONArray,
        val channelMapping: JSONArray
    ) {
        val size: Int get() = times.size
        val duration: Double get() = if (times.isEmpty()) 0.0 else times.last()
    }

    companion object {
        private val CHANNELS = listOf(
            ChannelSpec("rpm", listOf("RPM", "Engine Speed")),
            ChannelSpec("map", listOf("MAP", "MAP Pressure")),
            ChannelSpec("baro", listOf("baroPressure", "Barometric Pressure", "BARO")),
            ChannelSpec("afr", listOf("Air/Fuel Ratio _Gas Scale", "AFR Gas Scale", "Air/Fuel Ratio", "AFRValue", "AFR")),
            ChannelSpec("afrTarget", listOf("Fuel: target AFR", "AFR Target", "AFRTarget", "Target AFR")),
            ChannelSpec("tps", listOf("TPS", "TP", "Throttle Position")),
            ChannelSpec("clt", listOf("CLT", "Coolant Temperature", "Coolant")),
            ChannelSpec("iat", listOf("MAT", "IAT", "Intake Air Temperature")),
            ChannelSpec("fuelPressure", listOf("Fuel pressure _low", "Fuel Pressure Low", "fuelPressure", "Rail Pressure"), zeroOnlyMeansDisabled = true),
            ChannelSpec("batt", listOf("Batt V", "rawBattery", "Battery Voltage", "batteryVoltage")),
            ChannelSpec("boostTarget", listOf("Boost: Target", "Boost Target", "MAP Target", "boostTarget")),
            ChannelSpec("boostDuty", listOf("Boost: Output", "Boost Output", "Boost Duty", "boostDuty")),
            ChannelSpec("ign", listOf("Ignition: Running advance", "Ignition Advance", "Ignition", "Advance")),
            ChannelSpec("oilPressure", listOf("Oil Pressure", "oilPressure"), zeroOnlyMeansDisabled = true),
            ChannelSpec("idleTarget", listOf("Idle: Target RPM", "Idle Target RPM", "idleTarget")),
            ChannelSpec("vehicleSpeed", listOf("Vehicle Speed", "VSS", "Speed")),
            ChannelSpec("gear", listOf("Detected Gear", "Gear"))
        )

        private const val TIME_SENTINEL = -2
        private const val INITIAL_CAPACITY = 16_384
        private const val PLAYBACK_TICK_MS = 20L
        private const val POSITION_UPDATE_MS = 100L
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadGeneration = AtomicInteger(0)

    private var parsedLog: ParsedLog? = null
    private var speed = 1.0
    private var loopEnabled = true
    private var paused = true
    private var positionSeconds = 0.0
    private var anchorPositionSeconds = 0.0
    private var anchorRealtimeNanos = 0L
    private var lastSampleIndex = -1
    private var lastPositionUpdateMs = 0L

    private val playbackRunnable = object : Runnable {
        override fun run() {
            val log = parsedLog ?: return
            if (paused || log.size == 0) return

            val nowNanos = SystemClock.elapsedRealtimeNanos()
            var position = anchorPositionSeconds +
                ((nowNanos - anchorRealtimeNanos) / 1_000_000_000.0) * speed

            if (position >= log.duration) {
                if (loopEnabled && log.duration > 0.0) {
                    position %= log.duration
                    anchorPositionSeconds = position
                    anchorRealtimeNanos = nowNanos
                    lastSampleIndex = -1
                    listener.onLooped()
                } else {
                    position = log.duration
                    pauseAt(position)
                    emitSampleAt(position, force = true)
                    emitPosition(force = true)
                    return
                }
            }

            positionSeconds = position
            emitSampleAt(position, force = false)
            emitPosition(force = false)
            mainHandler.postDelayed(this, PLAYBACK_TICK_MS)
        }
    }

    fun load(uri: Uri) {
        val generation = loadGeneration.incrementAndGet()
        stopInternal(notify = false)
        val fileName = queryDisplayName(uri)
        listener.onLoading(fileName)

        worker.execute {
            try {
                val log = parse(uri, fileName) { rows ->
                    if (generation == loadGeneration.get()) {
                        mainHandler.post { listener.onParseProgress(rows) }
                    }
                }
                if (generation != loadGeneration.get()) return@execute

                mainHandler.post {
                    if (generation != loadGeneration.get()) return@post
                    parsedLog = log
                    positionSeconds = 0.0
                    speed = 1.0
                    loopEnabled = true
                    paused = false
                    lastSampleIndex = -1
                    anchorPositionSeconds = 0.0
                    anchorRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                    val rate = if (log.duration > 0.0) {
                        (log.size - 1).coerceAtLeast(0) / log.duration
                    } else {
                        0.0
                    }
                    val metadata = JSONObject().apply {
                        put("fileName", log.fileName)
                        put("signature", log.signature)
                        put("rows", log.size)
                        put("duration", log.duration)
                        put("sampleRate", rate)
                        put("channels", JSONArray(log.mappedChannels))
                        put("recognized", JSONArray(log.recognizedChannels))
                        put("missing", JSONArray(log.missingChannels))
                        put("disabled", log.disabledChannels)
                        put("mapping", log.channelMapping)
                    }
                    listener.onLoaded(metadata)
                    emitSampleAt(0.0, force = true)
                    emitPosition(force = true)
                    mainHandler.removeCallbacks(playbackRunnable)
                    mainHandler.post(playbackRunnable)
                }
            } catch (error: Exception) {
                if (generation == loadGeneration.get()) {
                    mainHandler.post {
                        listener.onError(error.message ?: "Unable to read the selected MSL log")
                    }
                }
            }
        }
    }

    fun setPaused(shouldPause: Boolean) {
        val log = parsedLog ?: return
        if (paused == shouldPause) return

        if (shouldPause) {
            positionSeconds = currentPosition(log)
            paused = true
            mainHandler.removeCallbacks(playbackRunnable)
        } else {
            paused = false
            anchorPositionSeconds = positionSeconds.coerceIn(0.0, log.duration)
            anchorRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            mainHandler.removeCallbacks(playbackRunnable)
            mainHandler.post(playbackRunnable)
        }
        emitPosition(force = true)
    }

    fun setSpeed(newSpeed: Double) {
        val log = parsedLog ?: return
        val clamped = newSpeed.coerceIn(0.1, 8.0)
        positionSeconds = currentPosition(log)
        speed = clamped
        anchorPositionSeconds = positionSeconds
        anchorRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        emitPosition(force = true)
    }

    fun setLoop(enabled: Boolean) {
        loopEnabled = enabled
    }

    fun seekToFraction(fraction: Double) {
        val log = parsedLog ?: return
        val newPosition = log.duration * fraction.coerceIn(0.0, 1.0)
        positionSeconds = newPosition
        anchorPositionSeconds = newPosition
        anchorRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        lastSampleIndex = -1
        emitSampleAt(newPosition, force = true)
        emitPosition(force = true)
    }

    fun seekBySeconds(deltaSeconds: Double) {
        val log = parsedLog ?: return
        val base = currentPosition(log)
        val newPosition = (base + deltaSeconds).coerceIn(0.0, log.duration)
        seekToPosition(log, newPosition)
    }

    fun stepRows(deltaRows: Int) {
        val log = parsedLog ?: return
        if (log.size == 0 || deltaRows == 0) return
        val baseIndex = findSampleIndex(log.times, currentPosition(log))
        val targetIndex = (baseIndex + deltaRows).coerceIn(0, log.times.lastIndex)
        paused = true
        mainHandler.removeCallbacks(playbackRunnable)
        seekToPosition(log, log.times[targetIndex])
    }

    private fun seekToPosition(log: ParsedLog, newPosition: Double) {
        positionSeconds = newPosition.coerceIn(0.0, log.duration)
        anchorPositionSeconds = positionSeconds
        anchorRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        lastSampleIndex = -1
        emitSampleAt(positionSeconds, force = true)
        emitPosition(force = true)
        if (!paused) {
            mainHandler.removeCallbacks(playbackRunnable)
            mainHandler.post(playbackRunnable)
        }
    }

    fun stop() {
        loadGeneration.incrementAndGet()
        stopInternal(notify = true)
    }

    fun shutdown() {
        loadGeneration.incrementAndGet()
        stopInternal(notify = false)
        worker.shutdownNow()
    }

    private fun stopInternal(notify: Boolean) {
        mainHandler.removeCallbacks(playbackRunnable)
        parsedLog = null
        paused = true
        positionSeconds = 0.0
        anchorPositionSeconds = 0.0
        lastSampleIndex = -1
        if (notify) listener.onStopped()
    }

    private fun pauseAt(position: Double) {
        positionSeconds = position
        anchorPositionSeconds = position
        paused = true
        mainHandler.removeCallbacks(playbackRunnable)
    }

    private fun currentPosition(log: ParsedLog): Double {
        if (paused) return positionSeconds.coerceIn(0.0, log.duration)
        val elapsed = (SystemClock.elapsedRealtimeNanos() - anchorRealtimeNanos) / 1_000_000_000.0
        return (anchorPositionSeconds + elapsed * speed).coerceIn(0.0, log.duration)
    }

    private fun emitSampleAt(position: Double, force: Boolean) {
        val log = parsedLog ?: return
        if (log.size == 0) return
        val index = findSampleIndex(log.times, position)
        if (!force && index == lastSampleIndex) return
        lastSampleIndex = index

        val values = JSONObject()
        CHANNELS.forEachIndexed { channelIndex, spec ->
            if (!log.enabledChannels[channelIndex]) return@forEachIndexed
            val value = log.values[channelIndex][index]
            if (value.isFinite()) values.put(spec.key, value.toDouble())
        }
        listener.onSample(log.times[index], values)
    }

    private fun emitPosition(force: Boolean) {
        val log = parsedLog ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPositionUpdateMs < POSITION_UPDATE_MS) return
        lastPositionUpdateMs = now
        listener.onPosition(positionSeconds, log.duration, paused, speed)
    }

    private fun findSampleIndex(times: DoubleArray, position: Double): Int {
        var low = 0
        var high = times.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (times[mid] <= position) low = mid + 1 else high = mid - 1
        }
        return high.coerceIn(0, times.lastIndex)
    }

    private fun parse(uri: Uri, fileName: String, progress: (Int) -> Unit): ParsedLog {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri)
            ?: error("Android could not open the selected file")

        input.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 1 shl 20).use { reader ->
                var signature = ""
                var headerLine: String? = null

                while (true) {
                    val line = reader.readLine() ?: break
                    val clean = line.removePrefix("\uFEFF")
                    if (signature.isEmpty() && clean.startsWith("\"")) {
                        signature = clean.trim().trim('"')
                    }
                    if (clean.contains('\t') && normalize(clean.substringBefore('\t')) == "time") {
                        headerLine = clean
                        break
                    }
                }

                val header = headerLine ?: error("No TunerStudio MSL header beginning with Time was found")
                val headers = header.split('\t')
                val normalizedHeaders = headers.map(::normalize)
                val timeColumn = normalizedHeaders.indexOf("time")
                if (timeColumn < 0) error("The MSL log has no Time column")

                val sourceColumns = IntArray(CHANNELS.size) { channelIndex ->
                    findPreferredColumn(normalizedHeaders, CHANNELS[channelIndex].aliases)
                }
                val recognized = CHANNELS.indices
                    .filter { sourceColumns[it] >= 0 }
                    .map { CHANNELS[it].key }
                val missing = CHANNELS.indices
                    .filter { sourceColumns[it] < 0 }
                    .map { CHANNELS[it].key }

                if (recognized.none { it == "rpm" || it == "map" || it == "afr" }) {
                    error("The file is tabular, but no EpicEFI RPM, MAP or AFR channels were recognized")
                }

                val maxSourceColumn = max(timeColumn, sourceColumns.maxOrNull() ?: 0)
                val lookup = IntArray(maxSourceColumn + 1) { -1 }
                lookup[timeColumn] = TIME_SENTINEL
                sourceColumns.forEachIndexed { channelIndex, sourceIndex ->
                    if (sourceIndex >= 0 && sourceIndex < lookup.size) lookup[sourceIndex] = channelIndex
                }

                // The row immediately following the channel names is the unit row.
                reader.readLine()

                var capacity = INITIAL_CAPACITY
                var times = DoubleArray(capacity)
                var values = Array(CHANNELS.size) { FloatArray(capacity) { Float.NaN } }
                val rowValues = FloatArray(CHANNELS.size)
                val finiteCounts = IntArray(CHANNELS.size)
                val minimumValues = FloatArray(CHANNELS.size) { Float.POSITIVE_INFINITY }
                val maximumValues = FloatArray(CHANNELS.size) { Float.NEGATIVE_INFINITY }
                var rowCount = 0
                var firstAbsoluteTime = Double.NaN
                var lastRelativeTime = 0.0

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    Arrays.fill(rowValues, Float.NaN)
                    val absoluteTime = extractSelectedFields(line, lookup, rowValues)
                    if (!absoluteTime.isFinite()) continue

                    if (!firstAbsoluteTime.isFinite()) firstAbsoluteTime = absoluteTime
                    val relativeTime = absoluteTime - firstAbsoluteTime
                    if (relativeTime < -0.001 || relativeTime + 0.001 < lastRelativeTime) continue

                    if (rowCount == capacity) {
                        capacity = (capacity * 3) / 2
                        times = times.copyOf(capacity)
                        values = Array(CHANNELS.size) { channelIndex ->
                            values[channelIndex].copyOf(capacity)
                        }
                    }

                    times[rowCount] = relativeTime.coerceAtLeast(0.0)
                    CHANNELS.indices.forEach { channelIndex ->
                        val value = rowValues[channelIndex]
                        values[channelIndex][rowCount] = value
                        if (value.isFinite()) {
                            finiteCounts[channelIndex]++
                            if (value < minimumValues[channelIndex]) minimumValues[channelIndex] = value
                            if (value > maximumValues[channelIndex]) maximumValues[channelIndex] = value
                        }
                    }
                    lastRelativeTime = times[rowCount]
                    rowCount++

                    if (rowCount % 5_000 == 0) {
                        if (Thread.currentThread().isInterrupted) error("MSL loading cancelled")
                        progress(rowCount)
                    }
                }

                if (rowCount < 2) error("No usable numeric samples were found in the MSL log")

                val enabledChannels = BooleanArray(CHANNELS.size)
                val active = mutableListOf<String>()
                val disabled = JSONArray()
                val mapping = JSONArray()
                CHANNELS.forEachIndexed { channelIndex, spec ->
                    val sourceIndex = sourceColumns[channelIndex]
                    val finiteCount = finiteCounts[channelIndex]
                    val minimum = if (finiteCount > 0) minimumValues[channelIndex].toDouble() else Double.NaN
                    val maximum = if (finiteCount > 0) maximumValues[channelIndex].toDouble() else Double.NaN
                    val zeroOnly = finiteCount > 0 && kotlin.math.abs(minimum) <= 1.0 && kotlin.math.abs(maximum) <= 1.0
                    val reason = when {
                        sourceIndex < 0 -> "missing"
                        finiteCount == 0 -> "no numeric samples"
                        spec.zeroOnlyMeansDisabled && zeroOnly -> "constant zero / sensor disabled"
                        else -> "active"
                    }
                    val enabled = reason == "active"
                    enabledChannels[channelIndex] = enabled
                    if (enabled) active += spec.key
                    if (sourceIndex >= 0 && !enabled) {
                        disabled.put(
                            JSONObject()
                                .put("key", spec.key)
                                .put("header", headers[sourceIndex])
                                .put("reason", reason)
                                .put("finiteSamples", finiteCount)
                                .put("minimum", if (minimum.isFinite()) minimum else JSONObject.NULL)
                                .put("maximum", if (maximum.isFinite()) maximum else JSONObject.NULL)
                        )
                    }
                    mapping.put(
                        JSONObject()
                            .put("key", spec.key)
                            .put("header", if (sourceIndex >= 0) headers[sourceIndex] else JSONObject.NULL)
                            .put("status", reason)
                            .put("finiteSamples", finiteCount)
                            .put("minimum", if (minimum.isFinite()) minimum else JSONObject.NULL)
                            .put("maximum", if (maximum.isFinite()) maximum else JSONObject.NULL)
                    )
                }

                return ParsedLog(
                    fileName = fileName,
                    signature = signature,
                    times = times.copyOf(rowCount),
                    values = Array(CHANNELS.size) { values[it].copyOf(rowCount) },
                    enabledChannels = enabledChannels,
                    mappedChannels = active,
                    recognizedChannels = recognized,
                    missingChannels = missing,
                    disabledChannels = disabled,
                    channelMapping = mapping
                )
            }
        }
    }

    private fun extractSelectedFields(
        line: String,
        lookup: IntArray,
        output: FloatArray
    ): Double {
        var fieldIndex = 0
        var fieldStart = 0
        var time = Double.NaN
        var cursor = 0

        while (cursor <= line.length && fieldIndex < lookup.size) {
            if (cursor == line.length || line[cursor] == '\t') {
                when (val destination = lookup[fieldIndex]) {
                    TIME_SENTINEL -> time = parseDouble(line, fieldStart, cursor)
                    in output.indices -> output[destination] = parseFloat(line, fieldStart, cursor)
                }
                fieldIndex++
                fieldStart = cursor + 1
            }
            cursor++
        }
        return time
    }

    private fun parseDouble(text: String, start: Int, end: Int): Double {
        if (start >= end) return Double.NaN
        return text.substring(start, end).toDoubleOrNull() ?: Double.NaN
    }

    private fun parseFloat(text: String, start: Int, end: Int): Float {
        if (start >= end) return Float.NaN
        return text.substring(start, end).toFloatOrNull() ?: Float.NaN
    }

    private fun findPreferredColumn(headers: List<String>, aliases: List<String>): Int {
        aliases.forEach { alias ->
            val index = headers.indexOf(normalize(alias))
            if (index >= 0) return index
        }
        return -1
    }

    private fun normalize(value: String): String = buildString(value.length) {
        value.lowercase().forEach { character ->
            if (character.isLetterOrDigit()) append(character)
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) ?: "TunerStudio log.msl" else "TunerStudio log.msl"
            } else {
                uri.lastPathSegment ?: "TunerStudio log.msl"
            }
        } catch (_: Exception) {
            uri.lastPathSegment ?: "TunerStudio log.msl"
        } finally {
            cursor?.close()
        }
    }
}
