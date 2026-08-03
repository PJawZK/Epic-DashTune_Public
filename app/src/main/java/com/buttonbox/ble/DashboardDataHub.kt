package com.buttonbox.ble

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Process-local live data cache, BLE diagnostics state and Lab control bridge. */
object DashboardDataHub {

    const val HASH_RPM = 1699696209
    const val HASH_MAP = 1281101952
    const val HASH_BARO = -2066867294
    const val HASH_AFR = -1093429509
    const val HASH_AFR_TARGET = 2122891301
    const val HASH_TPS = 1272048601
    const val HASH_CLT = -746111499
    const val HASH_IAT = 81034497
    const val HASH_FUEL_HIGH = -1973799222
    const val HASH_FUEL_LOW = -628741220
    const val HASH_BATTERY = 277722310
    const val HASH_BOOST_TARGET = -955735749
    const val HASH_BOOST_DUTY = 459143268
    const val HASH_IGNITION = 352421907
    const val HASH_IDLE_TARGET = 1461165305
    const val HASH_OIL_PRESSURE = 598268994

    val requiredHashes: List<Int> = listOf(
        HASH_RPM, HASH_MAP, HASH_BARO, HASH_AFR, HASH_AFR_TARGET, HASH_TPS,
        HASH_CLT, HASH_IAT, HASH_FUEL_HIGH, HASH_FUEL_LOW, HASH_BATTERY,
        HASH_BOOST_TARGET, HASH_BOOST_DUTY, HASH_IGNITION, HASH_IDLE_TARGET,
        HASH_OIL_PRESSURE
    )

    interface BleControl {
        fun scanNow()
        fun reconnectNow()
        fun disconnectNow()
        fun setAutomaticReconnect(enabled: Boolean)
    }

    private val values = ConcurrentHashMap<Int, Float>()
    private val valueUpdatedElapsedMs = ConcurrentHashMap<Int, Long>()
    private data class UsbFrame(
        val values: Map<String, Float>,
        val elapsedMs: Long,
        val frameCount: Long,
        val rateHz: Double
    )

    private val usbFrame = AtomicReference(UsbFrame(emptyMap(), 0L, 0L, 0.0))
    private val snapshotRevision = AtomicLong(0L)
    private val usbSessionId = AtomicLong(0L)
    @Volatile private var usbStreaming: Boolean = false
    @Volatile private var usbSessionSelected: Boolean = false
    @Volatile private var usbState: String = "no_profile"
    @Volatile private var usbStateMessage: String = "Import mainController.ini"
    @Volatile private var usbPollTargetHz: Int = 20
    @Volatile private var performanceProfile: PerformanceProfile = PerformanceProfile.FULL_OPTIMIZED
    @Volatile private var liveTransportPreference: String = "auto"
    @Volatile private var usbChannelCatalog: JSONArray = JSONArray()
    @Volatile private var usbRequiredNames: Set<String> = emptySet()
    private val packetCounter = AtomicLong(0)
    private val scanCount = AtomicLong(0)
    private val scanSuccessCount = AtomicLong(0)
    private val scanFailureCount = AtomicLong(0)
    private val reconnectCount = AtomicLong(0)
    private val connectionCount = AtomicLong(0)
    private val disconnectCount = AtomicLong(0)
    private val packetBatchCount = AtomicLong(0)
    private val variableEntryCount = AtomicLong(0)
    private val malformedByteCount = AtomicLong(0)
    private val receivedByteCount = AtomicLong(0)

    @Volatile var connected: Boolean = false
        private set
    @Volatile var labActive: Boolean = false
    @Volatile private var bleControl: BleControl? = null
    @Volatile private var connectionPhase: BleManager.ConnectionPhase = BleManager.ConnectionPhase.OFFLINE
    @Volatile private var reconnectDelayMs: Long = 0L
    @Volatile private var reconnectAttempt: Int = 0
    @Volatile private var automaticReconnect: Boolean = true
    @Volatile private var lastPacketElapsedMs: Long = 0L
    @Volatile private var estimatedCallbackRateHz: Double = 0.0
    @Volatile private var longestPacketGapMs: Long = 0L
    @Volatile private var connectedSinceElapsedMs: Long = 0L
    @Volatile private var sessionStartElapsedMs: Long = SystemClock.elapsedRealtime()
    @Volatile private var deviceName: String = ""
    @Volatile private var deviceAddress: String = ""
    @Volatile private var rssi: Int? = null
    @Volatile private var mtu: Int = 23
    @Volatile private var serviceDiscovered: Boolean = false
    @Volatile private var notificationsEnabled: Boolean = false
    @Volatile private var lastDisconnectStatus: Int? = null
    @Volatile private var lastDisconnectReason: String = ""
    @Volatile private var gpsPermissionGranted: Boolean = false
    @Volatile private var gpsActive: Boolean = false
    @Volatile private var gpsUpdatedElapsedMs: Long = 0L
    @Volatile private var gpsFixTimeEpochMs: Long = 0L
    @Volatile private var gpsSpeedKmh: Float = Float.NaN
    @Volatile private var gpsAccuracyM: Float = Float.NaN
    @Volatile private var gpsSpeedAccuracyKmh: Float = Float.NaN
    @Volatile private var gpsBearingDeg: Float = Float.NaN
    @Volatile private var gpsAltitudeM: Float = Float.NaN
    private var rateWindowStartMs: Long = SystemClock.elapsedRealtime()
    private var rateWindowCycles: Long = 0L
    private var lastCycleMarkMs: Long = 0L

    fun setLiveTransportPreference(mode: String) {
        liveTransportPreference = when (mode.lowercase()) { "usb", "ble" -> mode.lowercase(); else -> "auto" }
        if (liveTransportPreference == "ble") usbSessionSelected = false
        snapshotRevision.incrementAndGet()
    }
    fun getLiveTransportPreference(): String = liveTransportPreference
    fun setUsbChannelCatalog(catalog: JSONArray) { usbChannelCatalog = catalog; snapshotRevision.incrementAndGet() }
    fun getUsbChannelCatalog(): JSONArray = usbChannelCatalog
    fun setUsbRequiredChannels(names: Collection<String>) {
        val next = names.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (next == usbRequiredNames) return
        usbRequiredNames = next
        snapshotRevision.incrementAndGet()
    }
    fun setUsbPollTargetHz(hz: Int) { usbPollTargetHz = hz.coerceIn(5, 20); snapshotRevision.incrementAndGet() }
    fun setPerformanceProfile(profile: PerformanceProfile) {
        performanceProfile = profile
        PerformanceMetrics.setProfile(profile)
        snapshotRevision.incrementAndGet()
    }
    fun currentPerformanceProfile(): PerformanceProfile = performanceProfile
    fun currentSnapshotRevision(): Long = snapshotRevision.get()
    fun setUsbState(streaming: Boolean, state: String, message: String, generation: Long) {
        if (generation < usbSessionId.get()) return
        usbSessionId.set(generation)
        usbStreaming = streaming
        if (streaming || state in setOf("opening", "handshake", "permission", "retry_wait", "error", "disabled", "waiting_device")) usbSessionSelected = true
        usbState = state
        usbStateMessage = message
        if (!streaming) {
            val previous = usbFrame.get()
            // Disconnect is an authoritative generation boundary. Clear data and rate while
            // retaining frame count only for diagnostics.
            usbFrame.set(UsbFrame(emptyMap(), 0L, previous.frameCount, 0.0))
        }
        snapshotRevision.incrementAndGet()
    }

    fun updateUsbValues(incoming: Map<String, Float>, elapsedMs: Long, measuredHz: Double, generation: Long) {
        if (generation != usbSessionId.get()) return
        val startedNs = System.nanoTime()
        val now = if (elapsedMs > 0) elapsedMs else SystemClock.elapsedRealtime()
        val previous = usbFrame.get()
        // Optimized profiles publish a fresh frame map that is never mutated after this callback,
        // so retain it directly and avoid a second per-frame map allocation. Legacy mode keeps the
        // defensive copy to provide a meaningful A/B baseline in Performance Lab.
        val stable: Map<String, Float> = if (performanceProfile.selectiveDecode) {
            incoming
        } else {
            LinkedHashMap<String, Float>(incoming.size).also { copy ->
                incoming.forEach { (key, value) -> if (value.isFinite()) copy[key] = value }
            }
        }
        usbFrame.set(UsbFrame(stable, now, previous.frameCount + 1L, measuredHz))
        usbStreaming = true
        snapshotRevision.incrementAndGet()
        PerformanceMetrics.timingUs("dataHubPublish", System.nanoTime() - startedNs)
        PerformanceMetrics.sample("publishedChannels", stable.size.toLong())
    }

    private fun usbPreferred(): Boolean = liveTransportPreference != "ble" && (usbStreaming || usbSessionSelected)

    fun registerBleControl(control: BleControl) { bleControl = control }
    fun unregisterBleControl(control: BleControl) { if (bleControl === control) bleControl = null }
    fun requestScanNow(): Boolean = bleControl?.let { it.scanNow(); true } ?: false
    fun requestReconnectNow(): Boolean = bleControl?.let { it.reconnectNow(); true } ?: false
    fun requestDisconnectNow(): Boolean = bleControl?.let { it.disconnectNow(); true } ?: false
    fun requestAutomaticReconnect(enabled: Boolean): Boolean = bleControl?.let {
        automaticReconnect = enabled
        it.setAutomaticReconnect(enabled)
        true
    } ?: false

    @Synchronized
    fun update(hash: Int, value: Float) {
        values[hash] = value
        packetCounter.incrementAndGet()
        lastPacketElapsedMs = SystemClock.elapsedRealtime()
        valueUpdatedElapsedMs[hash] = lastPacketElapsedMs
        val now = lastPacketElapsedMs
        if (lastCycleMarkMs == 0L || now - lastCycleMarkMs > 5L) {
            rateWindowCycles++
            lastCycleMarkMs = now
        }
        val elapsed = now - rateWindowStartMs
        if (elapsed >= 1000L) {
            estimatedCallbackRateHz = rateWindowCycles * 1000.0 / elapsed.toDouble()
            rateWindowCycles = 0L
            rateWindowStartMs = now
        }
        snapshotRevision.incrementAndGet()
    }

    fun noteScanStarted() { scanCount.incrementAndGet() }
    fun noteScanSuccess(name: String?, address: String?, signal: Int?) {
        scanSuccessCount.incrementAndGet()
        updateDevice(name, address, signal)
    }
    fun noteScanFailure() { scanFailureCount.incrementAndGet() }
    fun noteReconnectScheduled() { reconnectCount.incrementAndGet() }
    fun noteConnecting(name: String?, address: String?) {
        updateDevice(name, address, null)
        serviceDiscovered = false
        notificationsEnabled = false
    }
    fun noteServiceDiscovered() { serviceDiscovered = true }
    fun noteNotificationsEnabled() { notificationsEnabled = true }
    fun noteMtu(value: Int) { if (value > 0) mtu = value }
    fun noteConnected() {
        connectionCount.incrementAndGet()
        connectedSinceElapsedMs = SystemClock.elapsedRealtime()
        lastDisconnectReason = ""
        lastDisconnectStatus = null
    }
    fun noteDisconnected(status: Int?, reason: String) {
        disconnectCount.incrementAndGet()
        connectedSinceElapsedMs = 0L
        serviceDiscovered = false
        notificationsEnabled = false
        lastDisconnectStatus = status
        lastDisconnectReason = reason.take(160)
    }
    fun notePacketBatch(byteCount: Int, entries: Int, malformedBytes: Int) {
        val now = SystemClock.elapsedRealtime()
        if (lastPacketElapsedMs > 0L) {
            val gap = now - lastPacketElapsedMs
            if (gap > longestPacketGapMs) longestPacketGapMs = gap
        }
        packetBatchCount.incrementAndGet()
        receivedByteCount.addAndGet(byteCount.coerceAtLeast(0).toLong())
        variableEntryCount.addAndGet(entries.toLong())
        if (malformedBytes > 0) malformedByteCount.addAndGet(malformedBytes.toLong())
    }

    fun setAutomaticReconnectState(enabled: Boolean) { automaticReconnect = enabled }

    fun setGpsPermission(granted: Boolean) { gpsPermissionGranted = granted; snapshotRevision.incrementAndGet() }
    fun setGpsActive(active: Boolean) { gpsActive = active; snapshotRevision.incrementAndGet() }

    fun updateGps(
        speedKmh: Float,
        accuracyM: Float,
        speedAccuracyKmh: Float,
        bearingDeg: Float,
        altitudeM: Float,
        fixTimeEpochMs: Long
    ) {
        gpsSpeedKmh = GpsSampleMapper.normalizeDashboardSpeedKmh(speedKmh)
        gpsAccuracyM = accuracyM
        gpsSpeedAccuracyKmh = speedAccuracyKmh
        gpsBearingDeg = bearingDeg
        gpsAltitudeM = altitudeM
        gpsFixTimeEpochMs = fixTimeEpochMs
        gpsUpdatedElapsedMs = SystemClock.elapsedRealtime()
        snapshotRevision.incrementAndGet()
    }

    fun setConnected(value: Boolean) {
        connected = value
        if (!value) estimatedCallbackRateHz = 0.0
        snapshotRevision.incrementAndGet()
    }

    fun setConnectionPhase(phase: BleManager.ConnectionPhase, retryDelayMs: Long = 0L, retryAttempt: Int = 0) {
        connectionPhase = phase
        reconnectDelayMs = retryDelayMs.coerceAtLeast(0L)
        this.reconnectAttempt = retryAttempt.coerceAtLeast(0)
        snapshotRevision.incrementAndGet()
    }

    @Synchronized
    fun resetDiagnostics() {
        scanCount.set(0); scanSuccessCount.set(0); scanFailureCount.set(0)
        reconnectCount.set(0); connectionCount.set(0); disconnectCount.set(0)
        packetBatchCount.set(0); variableEntryCount.set(0); malformedByteCount.set(0); receivedByteCount.set(0)
        longestPacketGapMs = 0L
        sessionStartElapsedMs = SystemClock.elapsedRealtime()
    }

    fun snapshotJson(): String {
        val now = SystemClock.elapsedRealtime()
        val useUsb = usbPreferred()
        val data = JSONObject()
        val channels = JSONArray()
        val channelAges = JSONObject()

        fun putValue(key: String, value: Float?, updated: Long) {
            if (value == null || !value.isFinite()) return
            data.put(key, value.toDouble()); channels.put(key)
            channelAges.put(key, if (updated > 0L) now - updated else -1L)
        }

        if (useUsb) {
            val frame = usbFrame.get()
            val frameValues = frame.values
            val mandatory = CANONICAL_USB_CHANNELS
            val keys = LinkedHashSet<String>(mandatory.size + usbRequiredNames.size)
            keys.addAll(mandatory)
            keys.addAll(usbRequiredNames)
            keys.forEach { key -> putValue(key, frameValues[key], frame.elapsedMs) }
        } else {
            fun addBle(key: String, hash: Int, value: Float? = values[hash], updatedHash: Int = hash) =
                putValue(key, value, valueUpdatedElapsedMs[updatedHash] ?: 0L)
            addBle("rpm", HASH_RPM); addBle("map", HASH_MAP); addBle("baro", HASH_BARO); addBle("afr", HASH_AFR)
            addBle("afrTarget", HASH_AFR_TARGET); addBle("tps", HASH_TPS); addBle("clt", HASH_CLT); addBle("iat", HASH_IAT)
            addBle("batt", HASH_BATTERY); addBle("boostTarget", HASH_BOOST_TARGET); addBle("boostDuty", HASH_BOOST_DUTY)
            addBle("ign", HASH_IGNITION); addBle("idleTarget", HASH_IDLE_TARGET); addBle("oilPressure", HASH_OIL_PRESSURE)
            val highFuel = values[HASH_FUEL_HIGH]; val lowFuel = values[HASH_FUEL_LOW]
            when {
                highFuel != null && highFuel.isFinite() && abs(highFuel) > 0.01f -> addBle("fuelPressure", HASH_FUEL_HIGH, highFuel)
                lowFuel != null && lowFuel.isFinite() -> addBle("fuelPressure", HASH_FUEL_LOW, lowFuel)
            }
        }

        val localData = JSONObject(); val localChannels = JSONArray(); val localAges = JSONObject()
        val gpsAge = if (gpsUpdatedElapsedMs > 0L) now - gpsUpdatedElapsedMs else -1L
        fun addLocal(key: String, value: Float) { if (value.isFinite()) { localData.put(key, value.toDouble()); localChannels.put(key); localAges.put(key, gpsAge) } }
        addLocal("gpsSpeed", gpsSpeedKmh); addLocal("gpsAccuracy", gpsAccuracyM); addLocal("gpsSpeedAccuracy", gpsSpeedAccuracyKmh)
        addLocal("gpsBearing", gpsBearingDeg); addLocal("gpsAltitude", gpsAltitudeM)

        val transport = if (useUsb) "usb" else "ble"
        val selectedConnected = if (useUsb) usbStreaming else connected
        val selectedUsbFrame = usbFrame.get()
        val age = if (useUsb) { if (selectedUsbFrame.elapsedMs > 0L) now - selectedUsbFrame.elapsedMs else -1L }
            else if (lastPacketElapsedMs > 0L) now - lastPacketElapsedMs else -1L
        val tpsTrace = JSONObject()
        if (useUsb) {
            val trace = selectedUsbFrame.values
            fun traceValue(name: String, jsonName: String = name) {
                trace[name]?.takeIf { it.isFinite() }?.let { tpsTrace.put(jsonName, it.toDouble()) }
            }
            traceValue("_traceTpsRawNumeric", "rawNumeric")
            traceValue("_traceTpsByte0", "rawByte0")
            traceValue("_traceTpsByte1", "rawByte1")
            traceValue("_traceTpsDecoded", "decoded")
            traceValue("_traceTpsOffset", "offset")
            traceValue("_traceTpsScale", "scale")
            traceValue("TPSValue", "profileTpsValue")
            traceValue("rawTps1Primary")
            traceValue("tpsADC")
            traceValue("throttlePedalPosition")
            traceValue("DriverThrottleIntent")
            traceValue("tps", "canonical")
            tpsTrace.put("frameElapsedMs", if (selectedUsbFrame.elapsedMs > 0L) selectedUsbFrame.elapsedMs else JSONObject.NULL)
        }
        return JSONObject()
            .put("revision", snapshotRevision.get())
            .put("usbSessionId", if (useUsb) usbSessionId.get() else -1L)
            .put("capturedElapsedMs", now)
            .put("capturedEpochMs", System.currentTimeMillis())
            .put("performanceProfile", performanceProfile.toJson())
            .put("connected", selectedConnected).put("transport", transport)
            .put("transportPreference", liveTransportPreference)
            .put("connectionPhase", if (useUsb) usbState else connectionPhase.name.lowercase())
            .put("connectionMessage", if (useUsb) usbStateMessage else "")
            .put("reconnectDelayMs", if (useUsb) 0 else reconnectDelayMs).put("reconnectAttempt", if (useUsb) 0 else reconnectAttempt)
            .put("ageMs", age).put("packetCount", if (useUsb) selectedUsbFrame.frameCount else packetCounter.get())
            .put("callbackRateHz", if (useUsb) selectedUsbFrame.rateHz else estimatedCallbackRateHz)
            .put("pollTargetHz", if (useUsb) usbPollTargetHz else JSONObject.NULL)
            .put("channels", channels).put("channelAgesMs", channelAges).put("data", data)
            .put("tpsTrace", tpsTrace)
            .put("localChannels", localChannels).put("localChannelAgesMs", localAges).put("localData", localData)
            .put("gpsActive", gpsActive).put("gpsPermissionGranted", gpsPermissionGranted)
            .put("gpsFixTimeEpochMs", if (gpsFixTimeEpochMs > 0L) gpsFixTimeEpochMs else JSONObject.NULL)
            .toString()
    }

    fun diagnosticsJson(): JSONObject {
        val now = SystemClock.elapsedRealtime()
        return JSONObject()
            .put("connectionPhase", connectionPhase.name.lowercase())
            .put("connected", connected)
            .put("automaticReconnect", automaticReconnect)
            .put("reconnectDelayMs", reconnectDelayMs)
            .put("reconnectAttempt", reconnectAttempt)
            .put("sessionDurationMs", now - sessionStartElapsedMs)
            .put("connectionDurationMs", if (connectedSinceElapsedMs > 0L) now - connectedSinceElapsedMs else 0L)
            .put("lastPacketAgeMs", if (lastPacketElapsedMs > 0L) now - lastPacketElapsedMs else -1L)
            .put("callbackRateHz", estimatedCallbackRateHz)
            .put("totalValues", packetCounter.get())
            .put("availableChannelCount", values.size)
            .put("scans", scanCount.get())
            .put("scanSuccesses", scanSuccessCount.get())
            .put("scanFailures", scanFailureCount.get())
            .put("reconnects", reconnectCount.get())
            .put("connections", connectionCount.get())
            .put("disconnects", disconnectCount.get())
            .put("packetBatches", packetBatchCount.get())
            .put("variableEntries", variableEntryCount.get())
            .put("receivedBytes", receivedByteCount.get())
            .put("malformedBytes", malformedByteCount.get())
            .put("longestGapMs", longestPacketGapMs)
            .put("performanceProfile", performanceProfile.toJson())
            .put("snapshotRevision", snapshotRevision.get())
            .put("usbSessionId", usbSessionId.get())
            .put("performance", PerformanceMetrics.snapshotJson())
            .put("gps", JSONObject()
                .put("permissionGranted", gpsPermissionGranted)
                .put("active", gpsActive)
                .put("ageMs", if (gpsUpdatedElapsedMs > 0L) now - gpsUpdatedElapsedMs else -1L)
                .put("fixTimeEpochMs", if (gpsFixTimeEpochMs > 0L) gpsFixTimeEpochMs else JSONObject.NULL)
                .put("speedKmh", if (gpsSpeedKmh.isFinite()) gpsSpeedKmh.toDouble() else JSONObject.NULL)
                .put("accuracyM", if (gpsAccuracyM.isFinite()) gpsAccuracyM.toDouble() else JSONObject.NULL)
                .put("speedAccuracyKmh", if (gpsSpeedAccuracyKmh.isFinite()) gpsSpeedAccuracyKmh.toDouble() else JSONObject.NULL)
                .put("bearingDeg", if (gpsBearingDeg.isFinite()) gpsBearingDeg.toDouble() else JSONObject.NULL)
                .put("altitudeM", if (gpsAltitudeM.isFinite()) gpsAltitudeM.toDouble() else JSONObject.NULL))
            .put("device", JSONObject()
                .put("name", deviceName)
                .put("address", deviceAddress)
                .put("rssi", rssi ?: JSONObject.NULL)
                .put("mtu", mtu)
                .put("serviceDiscovered", serviceDiscovered)
                .put("notificationsEnabled", notificationsEnabled)
                .put("lastDisconnectStatus", lastDisconnectStatus ?: JSONObject.NULL)
                .put("lastDisconnectReason", lastDisconnectReason))
    }

    private fun updateDevice(name: String?, address: String?, signal: Int?) {
        if (!name.isNullOrBlank()) deviceName = name
        if (!address.isNullOrBlank()) deviceAddress = address
        if (signal != null) rssi = signal
    }

    private fun putFinite(json: JSONObject, key: String, value: Float?) {
        if (value != null && value.isFinite()) json.put(key, value.toDouble())
    }
    private val CANONICAL_USB_CHANNELS = listOf(
        "rpm", "map", "baro", "afr", "afrTarget", "tps", "clt", "iat",
        "fuelPressure", "batt", "boostTarget", "boostDuty", "ign", "idleTarget",
        "oilPressure", "vehicleSpeed", "gear"
    )

}
