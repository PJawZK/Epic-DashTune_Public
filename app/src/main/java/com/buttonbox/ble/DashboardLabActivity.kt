package com.buttonbox.ble

import android.Manifest
import android.app.ActivityManager
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.buttonbox.ble.data.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Experimental dashboard workspace for live BLE data, demo scenarios,
 * TunerStudio MSL/CSV playback, derived channels and warning rules.
 */
class DashboardLabActivity : AppCompatActivity(), MslLogPlayer.Listener, BleManager.BleCallback, UsbEcuManager.Callback {

    private val activityOwnerId = LifecycleDiagnostics.registerActivity("DashboardLabActivity")
    private var webViewOwnerId: String = ""
    private var bleOwnerId: String = ""
    private var usbOwnerId: String = ""
    private var mslOwnerId: String = ""

    private lateinit var webView: WebView
    private lateinit var mslPlayer: MslLogPlayer
    private lateinit var bleManager: BleManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var usbEcuManager: UsbEcuManager
    private var usbProfile: UsbTunerStudioProfile? = null
    @Volatile private var usbProfileRestoreInProgress: Boolean = false
    @Volatile private var liveTransportPreference: String = "auto"
    @Volatile private var latestUsbState: UsbEcuManager.State = UsbEcuManager.State.NO_PROFILE
    @Volatile private var bleSuspendedForUsb: Boolean = false
    @Volatile private var autoBleFallbackScanStarted: Boolean = false
    private var variablePollingJob: Job? = null
    @Volatile private var servicesInitialized: Boolean = false
    private val bridgePushQueued = AtomicBoolean(false)
    private val bridgeForcePending = AtomicBoolean(false)
    private val bridgeDeliveryEpoch = AtomicLong(0L)
    @Volatile private var lastPushedSnapshotRevision: Long = -1L
    @Volatile private var activityResumed: Boolean = false

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val labBleControl = object : DashboardDataHub.BleControl {
        override fun scanNow() = runOnUiThread {
            if (::bleManager.isInitialized && !usbOwnsTransport()) {
                if (liveTransportPreference == "auto") autoBleFallbackScanStarted = true
                bleManager.startScan()
            } else dispatchLabNotice("BLE is paused while USB transport is selected")
        }
        override fun reconnectNow() = runOnUiThread {
            if (::bleManager.isInitialized && !usbOwnsTransport()) {
                if (liveTransportPreference == "auto") autoBleFallbackScanStarted = true
                bleManager.reconnectNow()
            } else dispatchLabNotice("BLE is paused while USB transport is selected")
        }
        override fun disconnectNow() = runOnUiThread {
            if (::bleManager.isInitialized) bleManager.disconnect()
        }
        override fun setAutomaticReconnect(enabled: Boolean) = runOnUiThread {
            if (::bleManager.isInitialized) bleManager.setAutomaticReconnectEnabled(enabled)
        }
    }

    private val locationListener = object : LocationDataHub.Listener {
        override fun onLocation(location: Location) {
            LifecycleDiagnostics.increment(activityOwnerId, "gpsCallbacks")
            if (::bleManager.isInitialized && bleManager.isConnected) {
                LifecycleDiagnostics.increment(bleOwnerId, "gpsForwardAttempts")
                sendGpsDataToCan(location)
            }
        }
    }

    private var legacyGpsState = LegacyGpsPayloadMapper.State()
    private var webPageReady = false
    private val pendingMslEvents = ArrayDeque<String>()
    private val dashboardRuntimeSnapshots = DashboardRuntimeSnapshotStore()
    @Volatile private var keepScreenAwakeEnabled: Boolean = true
    @Volatile private var pendingLayoutExportJson: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val usbProfilePersistenceGeneration = AtomicLong(0L)
    @Volatile private var usbIniImportPhase: String = "idle"
    @Volatile private var usbIniImportName: String = ""
    @Volatile private var usbIniImportMessage: String = "No INI import running"
    @Volatile private var usbIniImportStartedAtElapsedMs: Long = 0L
    @Volatile private var usbIniImportReadElapsedMs: Long = 0L
    @Volatile private var usbIniImportParseElapsedMs: Long = 0L
    @Volatile private var usbIniImportParserScanElapsedMs: Long = 0L
    @Volatile private var usbIniImportParserBitOptionsElapsedMs: Long = 0L
    @Volatile private var usbIniImportParserFinalizeElapsedMs: Long = 0L
    @Volatile private var usbIniImportParserLineCount: Int = 0
    @Volatile private var usbIniImportParserBreakdownJson: String = "{}"
    @Volatile private var usbIniImportApplyElapsedMs: Long = 0L
    @Volatile private var usbIniImportTotalElapsedMs: Long = 0L
    @Volatile private var lastTuningWriteBridgeAtEpochMs: Long = 0L
    @Volatile private var lastTuningWriteBridgeStatus: String = "idle"
    @Volatile private var lastTuningWriteBridgeReason: String = ""
    @Volatile private var lastTuningWriteBridgePayloadChars: Int = 0
    private val livePushRunnable = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || isFinishing || isDestroyed) return
            // Keep one bounded latest-state mailbox on the accepted optimized runtime path.
            requestLiveSnapshotPush(false)
            handler.postDelayed(this, 50L)
        }
    }

    private fun requestLiveSnapshotPush(force: Boolean) {
        if (force) bridgeForcePending.set(true)
        if (!activityResumed || !webPageReady) return
        if (!bridgePushQueued.compareAndSet(false, true)) {
            PerformanceMetrics.increment("bridgeFramesCoalesced")
            LifecycleDiagnostics.increment(webViewOwnerId, "framesCoalesced")
            return
        }
        runOnUiThread {
            if (!::webView.isInitialized || isFinishing || isDestroyed || !webPageReady || !activityResumed) {
                bridgePushQueued.set(false)
                return@runOnUiThread
            }
            pushLiveSnapshot(releaseQueue = true)
        }
    }

    private fun pushLiveSnapshot(releaseQueue: Boolean = false) {
        if (!::webView.isInitialized || isFinishing || isDestroyed || !webPageReady || !activityResumed) {
            if (releaseQueue) bridgePushQueued.set(false)
            return
        }
        val force = bridgeForcePending.getAndSet(false)
        val revision = DashboardDataHub.currentSnapshotRevision()
        if (!force && revision == lastPushedSnapshotRevision) {
            if (releaseQueue) bridgePushQueued.set(false)
            return
        }
        val buildStartedNs = System.nanoTime()
        val payload = DashboardDataHub.snapshotJson()
        PerformanceMetrics.timingUs("bridgeJsonBuild", System.nanoTime() - buildStartedNs)
        PerformanceMetrics.sample("bridgePayloadBytes", payload.toByteArray(Charsets.UTF_8).size.toLong())
        val deliveryStartedNs = System.nanoTime()
        val deliveryEpoch = bridgeDeliveryEpoch.get()
        webView.evaluateJavascript(
            "window.EpicDashNativeUpdate && window.EpicDashNativeUpdate($payload);"
        ) {
            PerformanceMetrics.timingUs("bridgeRoundTrip", System.nanoTime() - deliveryStartedNs)
            if (deliveryEpoch != bridgeDeliveryEpoch.get()) return@evaluateJavascript
            lastPushedSnapshotRevision = revision
            PerformanceMetrics.increment("bridgeDeliveries")
            LifecycleDiagnostics.increment(webViewOwnerId, "deliveries")
            if (releaseQueue) {
                bridgePushQueued.set(false)
                if (bridgeForcePending.get() || DashboardDataHub.currentSnapshotRevision() != revision) {
                    requestLiveSnapshotPush(false)
                }
            }
        }
    }


    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startConnectionHost()
            LocationDataHub.acquire(this, "lab")
        } else {
            DashboardDataHub.setConnectionPhase(BleManager.ConnectionPhase.OFFLINE)
            DiagnosticStore.addEvent(this, "BLE", "Required Bluetooth/location permissions declined")
            dispatchLabNotice("Bluetooth permissions are required for live ECU data")
        }
    }

    private val createDiagnosticReportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(buildDiagnosticReport())
            }
            DiagnosticStore.addEvent(this, "DIAG", "Diagnostic report exported")
            dispatchLabNotice("Diagnostic report exported")
        } catch (error: Exception) {
            DiagnosticStore.addEvent(this, "DIAG", "Diagnostic export failed: ${error.message}")
            dispatchLabNotice("Diagnostic export failed: ${error.message}")
        }
    }

    private val createLayoutFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(pendingLayoutExportJson)
            }
            DiagnosticStore.addEvent(this, "LAYOUT", "Dashboard layout exported")
            dispatchLabNotice("Dashboard layout exported")
        } catch (error: Exception) {
            DiagnosticStore.addEvent(this, "LAYOUT", "Layout export failed: ${error.message}")
            dispatchLabNotice("Layout export failed: ${error.message}")
        } finally {
            pendingLayoutExportJson = ""
        }
    }

    private val openLayoutFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            dispatchLabNotice("No layout selected")
            return@registerForActivityResult
        }
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText().take(2_000_000)
            } ?: throw IllegalArgumentException("Layout file was empty")
            val quoted = JSONObject.quote(text)
            runOnUiThread {
                if (::webView.isInitialized && webPageReady && !isFinishing && !isDestroyed) {
                    webView.evaluateJavascript(
                        "window.EpicDashNativeLayoutImported && window.EpicDashNativeLayoutImported($quoted);",
                        null
                    )
                }
            }
        } catch (error: Exception) {
            DiagnosticStore.addEvent(this, "LAYOUT", "Layout import failed: ${error.message}")
            dispatchLabNotice("Layout import failed: ${error.message}")
        }
    }

    private fun usbProfileSourceFile(): File = File(filesDir, "epicdash-mainController.ini")

    private fun readCappedIniSource(uri: Uri, limit: Int = 8_000_000): String {
        val reader = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
            ?: throw IllegalArgumentException("INI file could not be opened")
        return reader.use { source ->
            val out = StringBuilder(minOf(limit, 64 * 1024))
            val buffer = CharArray(8192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (out.length + count > limit) {
                    throw IllegalArgumentException("INI exceeds the supported 8,000,000 character limit")
                }
                out.append(buffer, 0, count)
            }
            out.toString().takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("INI file was empty")
        }
    }

    private fun usbIniImportStatusJson(): JSONObject = JSONObject()
        .put("phase", usbIniImportPhase)
        .put("busy", usbIniImportPhase in setOf("selecting", "reading", "parsing", "applying", "restoring"))
        .put("name", usbIniImportName.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        .put("message", usbIniImportMessage)
        .put("readElapsedMs", usbIniImportReadElapsedMs)
        .put("parseElapsedMs", usbIniImportParseElapsedMs)
        .put("parserScanElapsedMs", usbIniImportParserScanElapsedMs)
        .put("parserBitOptionsElapsedMs", usbIniImportParserBitOptionsElapsedMs)
        .put("parserFinalizeElapsedMs", usbIniImportParserFinalizeElapsedMs)
        .put("parserLineCount", usbIniImportParserLineCount)
        .put("parserScanBreakdownMs", runCatching { JSONObject(usbIniImportParserBreakdownJson) }.getOrElse { JSONObject() })
        .put("applyElapsedMs", usbIniImportApplyElapsedMs)
        .put("totalElapsedMs", usbIniImportTotalElapsedMs)

    private fun updateUsbIniImportStatus(
        phase: String,
        name: String = usbIniImportName,
        message: String,
        readElapsedMs: Long = usbIniImportReadElapsedMs,
        parseElapsedMs: Long = usbIniImportParseElapsedMs,
        parserScanElapsedMs: Long = usbIniImportParserScanElapsedMs,
        parserBitOptionsElapsedMs: Long = usbIniImportParserBitOptionsElapsedMs,
        parserFinalizeElapsedMs: Long = usbIniImportParserFinalizeElapsedMs,
        parserLineCount: Int = usbIniImportParserLineCount,
        parserBreakdownJson: String = usbIniImportParserBreakdownJson,
        applyElapsedMs: Long = usbIniImportApplyElapsedMs,
        totalElapsedMs: Long = usbIniImportTotalElapsedMs
    ) {
        usbIniImportPhase = phase
        usbIniImportName = name
        usbIniImportMessage = message
        usbIniImportReadElapsedMs = readElapsedMs.coerceAtLeast(0L)
        usbIniImportParseElapsedMs = parseElapsedMs.coerceAtLeast(0L)
        usbIniImportParserScanElapsedMs = parserScanElapsedMs.coerceAtLeast(0L)
        usbIniImportParserBitOptionsElapsedMs = parserBitOptionsElapsedMs.coerceAtLeast(0L)
        usbIniImportParserFinalizeElapsedMs = parserFinalizeElapsedMs.coerceAtLeast(0L)
        usbIniImportParserLineCount = parserLineCount.coerceAtLeast(0)
        usbIniImportParserBreakdownJson = parserBreakdownJson.takeIf { it.isNotBlank() } ?: "{}"
        usbIniImportApplyElapsedMs = applyElapsedMs.coerceAtLeast(0L)
        usbIniImportTotalElapsedMs = totalElapsedMs.coerceAtLeast(0L)
        val payload = usbIniImportStatusJson().toString()
        runOnUiThread {
            if (::webView.isInitialized && webPageReady && !isFinishing && !isDestroyed) {
                webView.evaluateJavascript(
                    "window.EpicDashUsbIniImportStateChanged && window.EpicDashUsbIniImportStateChanged($payload);",
                    null
                )
            }
        }
    }

    private fun channelCatalogFor(parsed: UsbTunerStudioProfile): JSONArray =
        JSONArray().also { array ->
            parsed.channels.forEach { channel ->
                array.put(JSONObject()
                    .put("key", channel.name)
                    .put("unit", channel.unit)
                    .put("type", channel.dataType)
                    .put("offset", channel.offset))
            }
        }

    private fun applyUsbProfile(
        parsed: UsbTunerStudioProfile,
        channelCatalog: JSONArray,
        announce: Boolean
    ) {
        usbProfile = parsed
        DashboardDataHub.setUsbChannelCatalog(channelCatalog)
        if (::usbEcuManager.isInitialized) {
            usbEcuManager.setProfile(parsed)
            if (liveTransportPreference != "ble") usbEcuManager.start()
        }
        DiagnosticStore.addEvent(
            this,
            "USB",
            "Loaded ${parsed.importedName} (${parsed.channels.size} channels, ${parsed.outputBlockSize} bytes)"
        )
        if (announce) dispatchLabNotice("USB profile imported • ${parsed.channels.size} channels")
    }

    private fun restoreUsbProfileAsync() {
        val restoreGeneration = usbProfilePersistenceGeneration.incrementAndGet()
        val usbPrefs = getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE)
        usbProfileRestoreInProgress = true
        lifecycleScope.launch {
            try {
                val persisted = withContext(Dispatchers.IO) {
                    val source = usbProfileSourceFile()
                    if (source.isFile) {
                        Triple(
                            "ini",
                            "",
                            usbPrefs.getString("profileName", "mainController.ini") ?: "mainController.ini"
                        )
                    } else {
                        usbPrefs.getString("profile", null)?.let { Triple("json", it, "mainController.ini") }
                    }
                } ?: return@launch
                if (usbProfilePersistenceGeneration.get() != restoreGeneration) return@launch
                usbIniImportStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                updateUsbIniImportStatus(
                    phase = "restoring",
                    name = persisted.third,
                    message = "Restoring saved TunerStudio profile…",
                    readElapsedMs = 0L,
                    parseElapsedMs = 0L,
                    parserScanElapsedMs = 0L,
                    parserBitOptionsElapsedMs = 0L,
                    parserFinalizeElapsedMs = 0L,
                    parserLineCount = 0,
                    parserBreakdownJson = "{}",
                    applyElapsedMs = 0L,
                    totalElapsedMs = 0L
                )
                val parseStartedAt = android.os.SystemClock.elapsedRealtime()
                val prepared = withContext(Dispatchers.Default) {
                    if (persisted.first == "ini") {
                        val parsed = TunerPermanentProjectStore.loadProfileForCurrentIni(this@DashboardLabActivity)
                            ?: throw IllegalStateException("Stored mainController.ini could not be restored")
                        Triple(
                            parsed,
                            channelCatalogFor(parsed),
                            UsbTunerStudioProfileParser.ParseMetrics(0, 0L, 0L, 0L, 0L)
                        )
                    } else {
                        val parsed = UsbTunerStudioProfile.fromJson(JSONObject(persisted.second))
                        Triple(
                            parsed,
                            channelCatalogFor(parsed),
                            UsbTunerStudioProfileParser.ParseMetrics(0, 0L, 0L, 0L, 0L)
                        )
                    }
                }
                val parseElapsed = android.os.SystemClock.elapsedRealtime() - parseStartedAt
                if (usbProfilePersistenceGeneration.get() != restoreGeneration) return@launch
                val applyStartedAt = android.os.SystemClock.elapsedRealtime()
                applyUsbProfile(prepared.first, prepared.second, announce = false)
                val applyElapsed = android.os.SystemClock.elapsedRealtime() - applyStartedAt
                val totalElapsed = android.os.SystemClock.elapsedRealtime() - usbIniImportStartedAtElapsedMs
                updateUsbIniImportStatus(
                    phase = "complete",
                    name = persisted.third,
                    message = "Saved INI restored • ${prepared.first.channels.size} channels",
                    parseElapsedMs = parseElapsed,
                    parserScanElapsedMs = prepared.third.scanElapsedMs,
                    parserBitOptionsElapsedMs = prepared.third.bitOptionsElapsedMs,
                    parserFinalizeElapsedMs = prepared.third.finalizeElapsedMs,
                    parserLineCount = prepared.third.lineCount,
                    parserBreakdownJson = prepared.third.toJson().optJSONObject("scanBreakdownMs")?.toString() ?: "{}",
                    applyElapsedMs = applyElapsed,
                    totalElapsedMs = totalElapsed
                )
            } catch (cancelled: CancellationException) {
                // lifecycleScope cancellation is normal Activity/WebView teardown.
                throw cancelled
            } catch (error: Exception) {
                val totalElapsed = if (usbIniImportStartedAtElapsedMs > 0L) {
                    android.os.SystemClock.elapsedRealtime() - usbIniImportStartedAtElapsedMs
                } else 0L
                updateUsbIniImportStatus(
                    phase = "failed",
                    message = "Stored INI restore failed: ${error.message}",
                    totalElapsedMs = totalElapsed
                )
                DiagnosticStore.addEvent(this@DashboardLabActivity, "USB", "Stored INI restore failed: ${error.message}")
            } finally {
                if (usbProfilePersistenceGeneration.get() == restoreGeneration) {
                    usbProfileRestoreInProgress = false
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && ::bleManager.isInitialized) {
                            reconcileTransportPolicy()
                        }
                    }
                }
            }
        }
    }

    private val openUsbIniLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            updateUsbIniImportStatus(
                phase = "cancelled",
                message = "INI import cancelled",
                totalElapsedMs = 0L
            )
            dispatchLabNotice("No TunerStudio INI selected")
            return@registerForActivityResult
        }
        val importGeneration = usbProfilePersistenceGeneration.incrementAndGet()
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "mainController.ini"
        usbIniImportStartedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        updateUsbIniImportStatus(
            phase = "reading",
            name = name,
            message = "Reading $name…",
            readElapsedMs = 0L,
            parseElapsedMs = 0L,
            parserScanElapsedMs = 0L,
            parserBitOptionsElapsedMs = 0L,
            parserFinalizeElapsedMs = 0L,
            parserLineCount = 0,
            parserBreakdownJson = "{}",
            applyElapsedMs = 0L,
            totalElapsedMs = 0L
        )
        lifecycleScope.launch {
            try {
                val readStartedAt = android.os.SystemClock.elapsedRealtime()
                val text = withContext(Dispatchers.IO) {
                    readCappedIniSource(uri)
                }
                val readElapsed = android.os.SystemClock.elapsedRealtime() - readStartedAt
                if (usbProfilePersistenceGeneration.get() != importGeneration) return@launch

                updateUsbIniImportStatus(
                    phase = "parsing",
                    name = name,
                    message = "Parsing TunerStudio INI…",
                    readElapsedMs = readElapsed
                )
                val parseStartedAt = android.os.SystemClock.elapsedRealtime()
                val prepared = withContext(Dispatchers.Default) {
                    val measured = UsbTunerStudioProfileParser.parseMeasured(text, name)
                    Triple(measured.profile, channelCatalogFor(measured.profile), measured.metrics)
                }
                val parseElapsed = android.os.SystemClock.elapsedRealtime() - parseStartedAt
                if (usbProfilePersistenceGeneration.get() != importGeneration) return@launch

                updateUsbIniImportStatus(
                    phase = "applying",
                    name = name,
                    message = "Persisting and applying ${prepared.first.channels.size} channels to USB/Tuner…",
                    readElapsedMs = readElapsed,
                    parseElapsedMs = parseElapsed
                )
                val persisted = withContext(Dispatchers.IO) {
                    TunerPermanentProjectStore.persistAuthoritativeIni(
                        this@DashboardLabActivity,
                        text,
                        name
                    )
                }
                if (!persisted) {
                    throw IllegalStateException("Exact INI source could not be committed and verified")
                }
                if (usbProfilePersistenceGeneration.get() != importGeneration) return@launch
                val applyStartedAt = android.os.SystemClock.elapsedRealtime()
                applyUsbProfile(prepared.first, prepared.second, announce = false)
                val applyElapsed = android.os.SystemClock.elapsedRealtime() - applyStartedAt
                val totalElapsed = android.os.SystemClock.elapsedRealtime() - usbIniImportStartedAtElapsedMs

                updateUsbIniImportStatus(
                    phase = "complete",
                    name = name,
                    message = "INI imported • ${prepared.first.channels.size} channels • ${totalElapsed} ms total",
                    readElapsedMs = readElapsed,
                    parseElapsedMs = parseElapsed,
                    parserScanElapsedMs = prepared.third.scanElapsedMs,
                    parserBitOptionsElapsedMs = prepared.third.bitOptionsElapsedMs,
                    parserFinalizeElapsedMs = prepared.third.finalizeElapsedMs,
                    parserLineCount = prepared.third.lineCount,
                    parserBreakdownJson = prepared.third.toJson().optJSONObject("scanBreakdownMs")?.toString() ?: "{}",
                    applyElapsedMs = applyElapsed,
                    totalElapsedMs = totalElapsed
                )
                DiagnosticStore.addEvent(
                    this@DashboardLabActivity,
                    "USB",
                    "INI import complete: $name readMs=$readElapsed parseMs=$parseElapsed scanMs=${prepared.third.scanElapsedMs} bitOptionsMs=${prepared.third.bitOptionsElapsedMs} finalizeMs=${prepared.third.finalizeElapsedMs} lines=${prepared.third.lineCount} breakdown=${prepared.third.toJson().optJSONObject("scanBreakdownMs")} applyMs=$applyElapsed totalMs=$totalElapsed channels=${prepared.first.channels.size}"
                )
                dispatchLabNotice("INI imported • ${prepared.first.channels.size} channels • ${totalElapsed} ms")
            } catch (error: Exception) {
                val totalElapsed = if (usbIniImportStartedAtElapsedMs > 0L) {
                    android.os.SystemClock.elapsedRealtime() - usbIniImportStartedAtElapsedMs
                } else 0L
                updateUsbIniImportStatus(
                    phase = "failed",
                    name = name,
                    message = "INI import failed: ${error.message}",
                    totalElapsedMs = totalElapsed
                )
                DiagnosticStore.addEvent(this@DashboardLabActivity, "USB", "INI import failed: ${error.message}")
                dispatchLabNotice("INI import failed: ${error.message}")
            }
        }
    }

    private val openMslFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            dispatchMslEvent(JSONObject().apply {
                put("type", "cancelled")
                put("message", "No log selected")
            })
            return@registerForActivityResult
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The temporary read grant from the picker is enough for this session.
        }
        if (::mslPlayer.isInitialized) {
            mslPlayer.load(uri)
        } else {
            dispatchLabNotice("Log playback is still initializing")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ButtonBoxBLE)
        super.onCreate(savedInstanceState)
        CrashTraceStore.install(this)
        LifecycleDiagnostics.mark(activityOwnerId, "onCreate", "restored=${savedInstanceState != null}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { provider -> provider.remove() }
        }
        applyDashboardWindowMode()
        if (savedInstanceState == null) {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            DiagnosticStore.startSession(this, "EpicDash JZ ${packageInfo.versionName ?: "unknown"}")
        }
        DiagnosticStore.addEvent(this, "APP", "Dashboard Lab opened directly")

        webViewOwnerId = LifecycleDiagnostics.registerManager("WebView-LAB", activityOwnerId)
        webView = WebView(this).apply {
            LifecycleDiagnostics.mark(webViewOwnerId, "constructed")
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    webPageReady = true
                    LifecycleDiagnostics.mark(webViewOwnerId, "page-ready", url ?: "")
                    T4TuningWorkspaceSurface.install(view)
                    while (pendingMslEvents.isNotEmpty()) {
                        evaluateMslEvent(pendingMslEvents.removeFirst())
                    }
                }
            }
            webChromeClient = WebChromeClient()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = false
                displayZoomControls = false
            }
            addJavascriptInterface(LabNativeBridge(), "EpicDashAndroid")
            // Recovery is deliberately installed before page load so availability never depends on
            // file-existence heuristics or a second WebView reload. The bridge owns SAF only and
            // has no ECU read/write/Burn authority.
            addJavascriptInterface(TunerRecoveryBridge(applicationContext), "EpicDashTunerRecovery")
            loadUrl("file:///android_asset/dashboard_lab.html")
        }

        setContentView(webView)
        // Let the first LAB frame replace the mandatory Android splash before
        // Bluetooth, polling and location hosting are initialized.
        webView.postOnAnimation {
            webView.post { initializeBackgroundServices() }
        }
    }

    private fun initializeBackgroundServices() {
        if (servicesInitialized || isFinishing || isDestroyed) return
        servicesInitialized = true
        LifecycleDiagnostics.mark(activityOwnerId, "services-initializing")
        mslOwnerId = LifecycleDiagnostics.registerManager("MSL-LAB", activityOwnerId)
        mslPlayer = MslLogPlayer(this, this)
        LifecycleDiagnostics.mark(mslOwnerId, "constructed")
        settingsManager = SettingsManager(this)
        bleOwnerId = LifecycleDiagnostics.registerManager("BLE-LAB", activityOwnerId)
        bleManager = BleManager(this).also { manager ->
            LifecycleDiagnostics.mark(bleOwnerId, "constructed")
            manager.setCallback(this)
            if (!manager.initialize()) {
                DashboardDataHub.setConnectionPhase(BleManager.ConnectionPhase.OFFLINE)
                DiagnosticStore.addEvent(this, "BLE", "Bluetooth adapter unavailable")
            }
        }
        DashboardDataHub.registerBleControl(labBleControl)
        DashboardDataHub.setAutomaticReconnectState(bleManager.isAutomaticReconnectEnabled)
        val usbPrefs = getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE)
        liveTransportPreference = normalizeTransport(usbPrefs.getString("transport", "auto") ?: "auto")
        DashboardDataHub.setLiveTransportPreference(liveTransportPreference)
        if (liveTransportPreference != "ble") {
            // AUTO/USB may make one bounded BLE fallback attempt, but do not run a permanent
            // background reconnect loop. Explicit BLE mode retains continuous reconnect.
            bleManager.setAutomaticReconnectEnabled(false)
        }
        // The expanded INI model can be large; restore it after manager creation without blocking
        // the Activity/UI thread.
        usbProfile = null
        usbOwnerId = LifecycleDiagnostics.registerManager("USB-LAB", activityOwnerId)
        usbEcuManager = UsbEcuManager(this, this).also { manager ->
            LifecycleDiagnostics.mark(usbOwnerId, "constructed")
            val storedPollHz = usbPrefs.getInt("pollHz", 20).coerceIn(5, 20)
            manager.setPollHz(storedPollHz)
            DashboardDataHub.setUsbPollTargetHz(storedPollHz)
            manager.setProfile(usbProfile)
            if (liveTransportPreference != "ble") manager.start()
        }
        restoreUsbProfileAsync()
        DashboardDataHub.setUsbChannelCatalog(usbEcuManager.channelCatalogJson())
        LocationDataHub.registerListener(locationListener)
        if (DashboardDataHub.labActive && hasAllPermissions()) {
            LocationDataHub.acquire(this, "lab")
        }
        checkPermissionsAndStartHost()
        LifecycleDiagnostics.mark(activityOwnerId, "services-initialized")
    }

    override fun onStart() {
        super.onStart()
        LifecycleDiagnostics.mark(activityOwnerId, "onStart")
        DashboardDataHub.labActive = true
        if (servicesInitialized && hasAllPermissions()) LocationDataHub.acquire(this, "lab")
        handler.removeCallbacks(livePushRunnable)
        handler.post(livePushRunnable)
    }

    override fun onResume() {
        super.onResume()
        LifecycleDiagnostics.mark(activityOwnerId, "onResume")
        activityResumed = true
        applyDashboardWindowMode()
        webView.onResume()
        requestLiveSnapshotPush(true)
    }

    override fun onPause() {
        LifecycleDiagnostics.mark(activityOwnerId, "onPause")
        activityResumed = false
        bridgeDeliveryEpoch.incrementAndGet()
        bridgePushQueued.set(false)
        lastPushedSnapshotRevision = -1L
        bridgeForcePending.set(true)
        webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        LifecycleDiagnostics.mark(activityOwnerId, "onStop")
        DashboardDataHub.labActive = false
        LocationDataHub.release(this, "lab")
        handler.removeCallbacks(livePushRunnable)
        if (::mslPlayer.isInitialized) mslPlayer.setPaused(true)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        LifecycleDiagnostics.mark(activityOwnerId, if (hasFocus) "window-focus" else "window-blur")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LifecycleDiagnostics.mark(activityOwnerId, "configuration-changed", "orientation=${newConfig.orientation}")
    }

    private fun notifyTuningWriteStateChanged() {
        runOnUiThread {
            if (!::webView.isInitialized || !webPageReady || !activityResumed) return@runOnUiThread
            webView.evaluateJavascript(
                "window.EpicDashTuningWriteStateChanged && window.EpicDashTuningWriteStateChanged();",
                null
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!::webView.isInitialized || !webPageReady) {
            super.onBackPressed()
            return
        }
        webView.evaluateJavascript(
            "window.EpicDashHandleBack ? String(window.EpicDashHandleBack()) : 'false';"
        ) { result ->
            val handled = result?.trim('"')?.equals("true", ignoreCase = true) == true
            if (!handled && !isFinishing && !isDestroyed) {
                runOnUiThread { finish() }
            }
        }
    }

    override fun onDestroy() {
        LifecycleDiagnostics.mark(activityOwnerId, "onDestroy")
        DashboardDataHub.labActive = false
        handler.removeCallbacks(livePushRunnable)
        variablePollingJob?.cancel()
        variablePollingJob = null
        LocationDataHub.release(this, "lab")
        LocationDataHub.unregisterListener(locationListener)
        DashboardDataHub.unregisterBleControl(labBleControl)
        if (::bleManager.isInitialized) bleManager.disconnect()
        if (bleOwnerId.isNotBlank()) LifecycleDiagnostics.close(bleOwnerId, "shutdown", "DashboardLabActivity.onDestroy")
        if (::usbEcuManager.isInitialized) usbEcuManager.shutdown()
        if (usbOwnerId.isNotBlank()) LifecycleDiagnostics.close(usbOwnerId, "shutdown", "DashboardLabActivity.onDestroy")
        if (::mslPlayer.isInitialized) mslPlayer.shutdown()
        if (mslOwnerId.isNotBlank()) LifecycleDiagnostics.close(mslOwnerId, "shutdown", "DashboardLabActivity.onDestroy")
        servicesInitialized = false
        webPageReady = false
        pendingMslEvents.clear()
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("EpicDashAndroid")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        if (webViewOwnerId.isNotBlank()) LifecycleDiagnostics.close(webViewOwnerId, "destroyed")
        LifecycleDiagnostics.close(activityOwnerId, "destroyed")
        super.onDestroy()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionsAndStartHost() {
        val missing = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startConnectionHost()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun normalizeTransport(mode: String): String = when (mode.lowercase(Locale.US)) {
        "usb", "ble" -> mode.lowercase(Locale.US)
        else -> "auto"
    }

    private fun usbOwnsTransport(): Boolean = when (liveTransportPreference) {
        "usb" -> true
        "ble" -> false
        else -> usbProfileRestoreInProgress || latestUsbState in setOf(
            UsbEcuManager.State.PERMISSION,
            UsbEcuManager.State.OPENING,
            UsbEcuManager.State.HANDSHAKE,
            UsbEcuManager.State.STREAMING,
            UsbEcuManager.State.RETRY_WAIT
        )
    }

    private fun reconcileTransportPolicy() {
        if (!::bleManager.isInitialized) return
        val usbOwns = usbOwnsTransport()
        if (usbOwns) {
            autoBleFallbackScanStarted = false
            if (!bleSuspendedForUsb) {
                bleManager.suspendForAlternateTransport(
                    if (liveTransportPreference == "usb") "USB-only mode" else "USB connection in progress"
                )
                bleSuspendedForUsb = true
            }
            if (liveTransportPreference != "ble" && ::usbEcuManager.isInitialized &&
                latestUsbState !in setOf(UsbEcuManager.State.ERROR, UsbEcuManager.State.DISABLED)) {
                usbEcuManager.start()
            }
        } else {
            if (bleSuspendedForUsb) bleSuspendedForUsb = false
            if (hasAllPermissions() && !bleManager.isConnected) {
                val supportedUsbPresent = ::usbEcuManager.isInitialized &&
                    usbEcuManager.hasSupportedUsbDevicePresent()
                val shouldScan = liveTransportPreference == "ble" ||
                    (liveTransportPreference == "auto" &&
                        !autoBleFallbackScanStarted &&
                        !supportedUsbPresent)
                if (shouldScan) {
                    if (liveTransportPreference == "auto") autoBleFallbackScanStarted = true
                    bleManager.startScan()
                }
            }
        }
    }

    private fun startConnectionHost() {
        if (!::bleManager.isInitialized) return
        reconcileTransportPolicy()
    }

    private fun startVariablePolling() {
        if (!::bleManager.isInitialized || !::settingsManager.isInitialized) return
        variablePollingJob?.cancel()
        variablePollingJob = lifecycleScope.launch {
            while (bleManager.isConnected) {
                val hashes = DashboardDataHub.requiredHashes.distinct()
                if (hashes.isNotEmpty()) bleManager.requestVariablesBatch(hashes)
                delay(settingsManager.dataDelayMs)
            }
        }
    }

    override fun onConnectionStateChanged(connected: Boolean) {
        LifecycleDiagnostics.mark(bleOwnerId, if (connected) "connected" else "disconnected")
        if (connected) LifecycleDiagnostics.increment(bleOwnerId, "connections")
        DashboardDataHub.setConnected(connected)
        if (connected) startVariablePolling() else {
            variablePollingJob?.cancel()
            variablePollingJob = null
        }
    }

    override fun onConnectionPhaseChanged(
        phase: BleManager.ConnectionPhase,
        retryDelayMs: Long,
        retryAttempt: Int
    ) {
        LifecycleDiagnostics.mark(bleOwnerId, "phase-${phase.name.lowercase()}", "retryDelayMs=$retryDelayMs retryAttempt=$retryAttempt")
        DashboardDataHub.setConnectionPhase(phase, retryDelayMs, retryAttempt)
    }

    override fun onVariableData(varHash: Int, value: Float) {
        LifecycleDiagnostics.increment(bleOwnerId, "variableCallbacks")
        DashboardDataHub.update(varHash, value)
    }

    override fun onLog(message: String) {
        DiagnosticStore.addEvent(this, "BLE", message)
    }

    override fun onUsbStateChanged(state: UsbEcuManager.State, message: String, generation: Long) {
        LifecycleDiagnostics.mark(usbOwnerId, "state-${state.name.lowercase()}", "generation=$generation ${message.take(120)}")
        LifecycleDiagnostics.setCounter(usbOwnerId, "generation", generation)
        latestUsbState = state
        DashboardDataHub.setUsbState(state == UsbEcuManager.State.STREAMING, state.name.lowercase(Locale.US), message, generation)
        if (state == UsbEcuManager.State.STREAMING) DashboardDataHub.setUsbChannelCatalog(usbEcuManager.channelCatalogJson())
        requestLiveSnapshotPush(true)
        runOnUiThread { if (!isFinishing && !isDestroyed) reconcileTransportPolicy() }
    }

    override fun onUsbValues(values: Map<String, Float>, elapsedMs: Long, generation: Long) {
        LifecycleDiagnostics.increment(usbOwnerId, "frames")
        LifecycleDiagnostics.increment(usbOwnerId, "decodedValues", values.size.toLong())
        LifecycleDiagnostics.setCounter(usbOwnerId, "generation", generation)
        val hz = if (::usbEcuManager.isInitialized) usbEcuManager.currentMeasuredHz() else 0.0
        DashboardDataHub.updateUsbValues(values, elapsedMs, hz, generation)
        requestLiveSnapshotPush(false)
    }

    override fun onUsbLog(message: String) {
        DiagnosticStore.addEvent(this, "USB", message)
    }

    override fun onScanResult(device: BluetoothDevice) = Unit

    private fun sendGpsDataToCan(location: Location) {
        if (!::bleManager.isInitialized || !bleManager.isConnected) return
        val calendar = java.util.Calendar.getInstance()
        val plan = LegacyGpsPayloadMapper.map(
            LegacyGpsPayloadMapper.Source(
                speedMetresPerSecond = location.speed,
                latitudeDegrees = location.latitude,
                longitudeDegrees = location.longitude,
                altitudeMetres = location.altitude.takeIf { location.hasAltitude() },
                bearingDegrees = location.bearing.takeIf { location.hasBearing() },
                accuracyMetres = location.accuracy.takeIf { location.hasAccuracy() },
                hours = calendar.get(java.util.Calendar.HOUR_OF_DAY),
                minutes = calendar.get(java.util.Calendar.MINUTE),
                seconds = calendar.get(java.util.Calendar.SECOND),
                dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH),
                month = calendar.get(java.util.Calendar.MONTH) + 1,
                twoDigitYear = calendar.get(java.util.Calendar.YEAR) % 100
            ),
            legacyGpsState
        )
        plan.hmsdPacked?.let {
            legacyGpsState = legacyGpsState.copy(hmsdPacked = it)
            bleManager.sendGpsDataPacked(BleManager.VAR_HASH_GPS_HMSD_PACKED, it)
        }
        plan.myqsatPacked?.let {
            legacyGpsState = legacyGpsState.copy(myqsatPacked = it)
            bleManager.sendGpsDataPacked(BleManager.VAR_HASH_GPS_MYQSAT_PACKED, it)
        }
        legacyGpsState = legacyGpsState.copy(
            speedMetresPerSecond = plan.nextState.speedMetresPerSecond,
            latitudeDegrees = plan.nextState.latitudeDegrees,
            longitudeDegrees = plan.nextState.longitudeDegrees,
            altitudeMetres = plan.nextState.altitudeMetres,
            bearingDegrees = plan.nextState.bearingDegrees,
            accuracyMetres = plan.nextState.accuracyMetres
        )
        if (plan.floatEntries.isNotEmpty()) {
            LifecycleDiagnostics.increment(bleOwnerId, "gpsForwardBatches")
            LifecycleDiagnostics.increment(bleOwnerId, "gpsValuesForwarded", plan.floatEntries.size.toLong())
            bleManager.sendGpsDataBatch(plan.floatEntries)
        }
    }

    override fun onLoading(fileName: String) {
        LifecycleDiagnostics.mark(mslOwnerId, "loading", fileName)
        dispatchMslEvent(JSONObject().apply {
            put("type", "loading")
            put("fileName", fileName)
            put("message", "Reading $fileName…")
        })
    }

    override fun onParseProgress(rows: Int) {
        dispatchMslEvent(JSONObject().apply {
            put("type", "progress")
            put("rows", rows)
            put("message", "Parsed ${formatRowCount(rows)} samples…")
        })
    }

    override fun onLoaded(metadata: JSONObject) {
        LifecycleDiagnostics.mark(mslOwnerId, "loaded", "rows=${metadata.optInt("rows")}")
        dispatchMslEvent(JSONObject().apply {
            put("type", "loaded")
            put("metadata", metadata)
        })
    }

    override fun onSample(timeSeconds: Double, values: JSONObject) {
        LifecycleDiagnostics.increment(mslOwnerId, "samples")
        dispatchMslEvent(JSONObject().apply {
            put("type", "sample")
            put("time", timeSeconds)
            put("data", values)
        })
    }

    override fun onPosition(
        positionSeconds: Double,
        durationSeconds: Double,
        paused: Boolean,
        speed: Double
    ) {
        dispatchMslEvent(JSONObject().apply {
            put("type", "position")
            put("position", positionSeconds)
            put("duration", durationSeconds)
            put("paused", paused)
            put("speed", speed)
        })
    }

    override fun onLooped() {
        LifecycleDiagnostics.increment(mslOwnerId, "loops")
        LifecycleDiagnostics.mark(mslOwnerId, "looped")
        dispatchMslEvent(JSONObject().apply {
            put("type", "looped")
            put("message", "MSL playback looped")
        })
    }

    override fun onStopped() {
        LifecycleDiagnostics.mark(mslOwnerId, "stopped")
        dispatchMslEvent(JSONObject().apply {
            put("type", "stopped")
            put("message", "MSL playback stopped")
        })
    }

    override fun onError(message: String) {
        LifecycleDiagnostics.increment(mslOwnerId, "errors")
        LifecycleDiagnostics.mark(mslOwnerId, "error", message)
        dispatchMslEvent(JSONObject().apply {
            put("type", "error")
            put("message", message)
        })
    }

    private fun tuningWriteBridgeStatusJson(): JSONObject = JSONObject()
        .put("atEpochMs", lastTuningWriteBridgeAtEpochMs)
        .put("status", lastTuningWriteBridgeStatus)
        .put("reason", lastTuningWriteBridgeReason)
        .put("payloadChars", lastTuningWriteBridgePayloadChars)

    private inner class LabNativeBridge {
        @JavascriptInterface
        fun getDiagnosticsJson(): String {
            return JSONObject()
                .put("app", appInfoJson())
                .put("store", DiagnosticStore.snapshotJson(this@DashboardLabActivity))
                .put("ble", DashboardDataHub.diagnosticsJson())
                .put("usb", (if (::usbEcuManager.isInitialized) usbEcuManager.diagnosticsJson(includeAudit = false) else JSONObject().put("state", "initializing"))
                    .put("tuningWriteBridge", tuningWriteBridgeStatusJson()))
                .put("lifecycle", LifecycleDiagnostics.snapshotJson()
                    .put("lastUncaughtCrash", CrashTraceStore.diagnosticsJson(this@DashboardLabActivity)))
                .put("dashboard", runtimeSnapshotJson())
                .toString()
        }

        @JavascriptInterface
        fun getUsbTransportFreshnessJson(): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.transportFreshnessJson().toString()
            } else {
                JSONObject()
                    .put("usbSessionId", -1L)
                    .put("streaming", false)
                    .put("packetAgeMs", -1L)
                    .put("activeSessionFrames", 0L)
                    .toString()
            }
        }

        @JavascriptInterface
        fun getAppInfoJson(): String = appInfoJson().toString()

        @JavascriptInterface
        fun updateRuntimeDiagnostics(runtimeJson: String) {
            dashboardRuntimeSnapshots.update(runtimeJson, System.currentTimeMillis())
        }

        @JavascriptInterface
        fun recordEvent(category: String, message: String): String {
            return DiagnosticStore.addEvent(this@DashboardLabActivity, category, message)
        }

        @JavascriptInterface
        fun recordWarning(ruleId: String, title: String, message: String, valuesJson: String): String {
            return DiagnosticStore.addWarning(
                this@DashboardLabActivity,
                ruleId,
                title,
                message,
                valuesJson
            )
        }

        @JavascriptInterface
        fun acknowledgeWarning(warningId: String) {
            DiagnosticStore.acknowledgeWarning(this@DashboardLabActivity, warningId)
        }

        @JavascriptInterface
        fun clearEventHistory() {
            DiagnosticStore.clearEvents(this@DashboardLabActivity)
            DiagnosticStore.addEvent(this@DashboardLabActivity, "DIAG", "Event history cleared")
        }

        @JavascriptInterface
        fun clearWarningHistory() {
            DiagnosticStore.clearWarnings(this@DashboardLabActivity)
            DiagnosticStore.addEvent(this@DashboardLabActivity, "DIAG", "Warning history cleared")
        }

        @JavascriptInterface
        fun resetSessionStatistics() {
            DashboardDataHub.resetDiagnostics()
            PerformanceMetrics.reset()
            DiagnosticStore.addEvent(this@DashboardLabActivity, "DIAG", "Session statistics reset")
        }

        @JavascriptInterface
        fun scanNow(): Boolean = DashboardDataHub.requestScanNow()

        @JavascriptInterface
        fun reconnectNow(): Boolean = DashboardDataHub.requestReconnectNow()

        @JavascriptInterface
        fun disconnectNow(): Boolean = DashboardDataHub.requestDisconnectNow()

        @JavascriptInterface
        fun setAutomaticReconnect(enabled: Boolean): Boolean =
            DashboardDataHub.requestAutomaticReconnect(enabled)

        @JavascriptInterface
        fun exportDiagnosticReport() {
            runOnUiThread {
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                createDiagnosticReportLauncher.launch("EpicDash-JZ-diagnostics-$stamp.txt")
            }
        }

        @JavascriptInterface
        fun copyText(label: String, text: String) {
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label.take(80), text.take(1_000_000)))
                dispatchLabNotice("$label copied")
            }
        }

        @JavascriptInterface
        fun setKeepScreenAwake(enabled: Boolean) {
            keepScreenAwakeEnabled = enabled
            runOnUiThread { applyKeepScreenAwakeFlag() }
        }

        @JavascriptInterface
        fun performHaptic(kind: String) {
            runOnUiThread {
                if (!::webView.isInitialized) return@runOnUiThread
                val feedback = when (kind.lowercase(Locale.US)) {
                    "tick", "tab" -> HapticFeedbackConstants.CLOCK_TICK
                    "confirm", "lock" -> HapticFeedbackConstants.LONG_PRESS
                    else -> HapticFeedbackConstants.KEYBOARD_TAP
                }
                webView.performHapticFeedback(feedback)
            }
        }

        @JavascriptInterface
        fun importUsbIni() {
            updateUsbIniImportStatus(
                phase = "selecting",
                name = "",
                message = "Select the matching mainController.ini",
                readElapsedMs = 0L,
                parseElapsedMs = 0L,
                parserScanElapsedMs = 0L,
                parserBitOptionsElapsedMs = 0L,
                parserFinalizeElapsedMs = 0L,
                parserLineCount = 0,
                applyElapsedMs = 0L,
                totalElapsedMs = 0L
            )
            runOnUiThread {
                openUsbIniLauncher.launch(arrayOf("text/plain", "application/octet-stream", "application/*"))
            }
        }

        @JavascriptInterface
        fun getUsbIniImportStatusJson(): String = usbIniImportStatusJson().toString()

        @JavascriptInterface
        fun getIniCompatibilityJson(): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.iniCompatibilityJson().toString()
            } else {
                JSONObject()
                    .put("state", "amber")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
        }

        @JavascriptInterface
        fun requestPermanentTunerProject(): String {
            val livePathBusy = !servicesInitialized || usbProfileRestoreInProgress || latestUsbState in setOf(
                UsbEcuManager.State.PERMISSION,
                UsbEcuManager.State.OPENING,
                UsbEcuManager.State.HANDSHAKE,
                UsbEcuManager.State.STREAMING,
                UsbEcuManager.State.RETRY_WAIT
            )
            if (livePathBusy) {
                return JSONObject()
                    .put("status", "deferred_live_path")
                    .put("usbState", latestUsbState.name)
                    .toString()
            }
            if (!::webView.isInitialized || isFinishing || isDestroyed) {
                return JSONObject().put("status", "unavailable").toString()
            }
            val queued = T4TuningWorkspaceSurface.requestPermanentProject(webView)
            return JSONObject()
                .put("status", if (queued) "queued" else "already_requested")
                .put("usbState", latestUsbState.name)
                .toString()
        }

        @JavascriptInterface
        fun getTuningWorkspaceJson(): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.tuningWorkspaceJson().toString()
            } else {
                JSONObject()
                    .put("status", "initializing")
                    .put("capability", "READ_ONLY")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
        }

        @JavascriptInterface
        fun getTuningArrayDetailJson(name: String): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.tuningArrayDetailJson(name).toString()
            } else {
                JSONObject()
                    .put("status", "initializing")
                    .put("capability", "READ_ONLY")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
        }

        @JavascriptInterface
        fun previewTuningArrayCellJson(name: String, cellIndex: Int, requestedValue: Double): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.previewTuningArrayCellJson(name, cellIndex, requestedValue).toString()
            } else {
                JSONObject()
                    .put("status", "initializing")
                    .put("capability", "SEMANTIC_PREVIEW")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
        }

        @JavascriptInterface
        fun previewTuningScalarJson(name: String, requestedValue: Double): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.previewTuningScalarJson(name, requestedValue).toString()
            } else {
                JSONObject()
                    .put("status", "initializing")
                    .put("capability", "SEMANTIC_PREVIEW")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
        }

        @JavascriptInterface
        fun previewTuningBitFieldJson(name: String, requestedValue: Double): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.previewTuningBitFieldJson(name, requestedValue).toString()
            } else {
                JSONObject()
                    .put("status", "initializing")
                    .put("capability", "SEMANTIC_PREVIEW")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
        }

        @JavascriptInterface
        fun readTuningFromEcuJson(): String {
            if (!::usbEcuManager.isInitialized) {
                return JSONObject()
                    .put("status", "initializing")
                    .put("capability", "LIVE_TUNING_READ")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
            return runCatching {
                usbEcuManager.queueTuningRead { notifyTuningWriteStateChanged() }.toString()
            }.getOrElse { error ->
                JSONObject()
                    .put("status", "error")
                    .put("capability", "LIVE_TUNING_READ")
                    .put("reason", error.message ?: error.javaClass.simpleName)
                    .toString()
            }
        }

        @JavascriptInterface
        fun writeTuningChangesJson(json: String): String {
            lastTuningWriteBridgeAtEpochMs = System.currentTimeMillis()
            lastTuningWriteBridgePayloadChars = json.length
            if (!::usbEcuManager.isInitialized) {
                val reason = "USB manager is still initializing"
                lastTuningWriteBridgeStatus = "initializing"
                lastTuningWriteBridgeReason = reason
                DiagnosticStore.addEvent(this@DashboardLabActivity, "TUNER", "Write bridge rejected: $reason")
                return JSONObject()
                    .put("status", "initializing")
                    .put("capability", "LIVE_TUNING_WRITE")
                    .put("reason", reason)
                    .toString()
            }
            return runCatching {
                usbEcuManager.queueTuningWriteBatchJson(json) { notifyTuningWriteStateChanged() }.also { result ->
                    lastTuningWriteBridgeStatus = result.optString("status", "queued")
                    lastTuningWriteBridgeReason = result.optString("reason", "")
                    DiagnosticStore.addEvent(
                        this@DashboardLabActivity,
                        "TUNER",
                        "Write bridge accepted: status=${result.optString("status", "")} payloadChars=${json.length}"
                    )
                }.toString()
            }.getOrElse { error ->
                val reason = (error.message ?: error.javaClass.simpleName).take(240)
                lastTuningWriteBridgeStatus = "error"
                lastTuningWriteBridgeReason = reason
                DiagnosticStore.addEvent(this@DashboardLabActivity, "TUNER", "Write bridge rejected: $reason")
                JSONObject()
                    .put("status", "error")
                    .put("capability", "LIVE_TUNING_WRITE")
                    .put("reason", reason)
                    .toString()
            }
        }

        @JavascriptInterface
        fun burnTuningChangesJson(): String {
            if (!::usbEcuManager.isInitialized) {
                return JSONObject()
                    .put("status", "initializing")
                    .put("capability", "LIVE_TUNING_PERSIST")
                    .put("reason", "USB manager is still initializing")
                    .toString()
            }
            return runCatching {
                usbEcuManager.queueTuningBurn { notifyTuningWriteStateChanged() }.toString()
            }.getOrElse { error ->
                JSONObject()
                    .put("status", "error")
                    .put("capability", "LIVE_TUNING_PERSIST")
                    .put("reason", error.message ?: error.javaClass.simpleName)
                    .toString()
            }
        }

        @JavascriptInterface
        fun getTuningWriteStatusJson(): String {
            return if (::usbEcuManager.isInitialized) {
                usbEcuManager.tuningWriteStatusJson().toString()
            } else {
                JSONObject()
                    .put("status", "initializing")
                    .put("capability", "LIVE_TUNING_READ_WRITE")
                    .put("operationRunning", false)
                    .put("uncertain", false)
                    .put("dirtyPageCount", 0)
                    .toString()
            }
        }

        @JavascriptInterface
        fun getUsbChannelCatalogJson(): String = runCatching {
            DashboardDataHub.getUsbChannelCatalog().toString()
        }.getOrElse { "[]" }

        @JavascriptInterface
        fun setUsbRequiredChannels(json: String): Boolean {
            return try {
                val array = JSONArray(json)
                val names = (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
                DashboardDataHub.setUsbRequiredChannels(names)
                if (::usbEcuManager.isInitialized) usbEcuManager.setRequiredChannels(names)
                true
            } catch (_: Exception) { false }
        }

        @JavascriptInterface
        fun usbReconnect(): Boolean {
            if (!::usbEcuManager.isInitialized) return false
            usbEcuManager.reconnectNow(); return true
        }

        @JavascriptInterface
        fun usbDisconnect(): Long {
            if (!::usbEcuManager.isInitialized) return -1L
            return usbEcuManager.disconnect()
        }

        @JavascriptInterface
        fun setLiveTransport(mode: String): Boolean {
            val normalized = normalizeTransport(mode)
            liveTransportPreference = normalized
            DashboardDataHub.setLiveTransportPreference(normalized)
            autoBleFallbackScanStarted = false
            getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE).edit().putString("transport", normalized).apply()
            runOnUiThread {
                if (::bleManager.isInitialized) {
                    bleManager.setAutomaticReconnectEnabled(normalized == "ble")
                }
                if (normalized == "ble" && ::usbEcuManager.isInitialized) {
                    usbEcuManager.disconnect("USB transport not selected")
                    latestUsbState = UsbEcuManager.State.DISABLED
                } else if (::usbEcuManager.isInitialized) {
                    usbEcuManager.start()
                }
                reconcileTransportPolicy()
            }
            return true
        }

        @JavascriptInterface
        fun setUsbPollHz(hz: Int): Boolean {
            val safe = hz.coerceIn(5, 20)
            getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE).edit().putInt("pollHz", safe).apply()
            DashboardDataHub.setUsbPollTargetHz(safe)
            if (::usbEcuManager.isInitialized) usbEcuManager.setPollHz(safe)
            DiagnosticStore.addEvent(this@DashboardLabActivity, "USB", "USB polling target set to $safe Hz")
            return true
        }

        @JavascriptInterface
        fun clearUsbProfile(): Boolean {
            usbProfilePersistenceGeneration.incrementAndGet()
            usbProfile = null
            getSharedPreferences("epicdash_usb", Context.MODE_PRIVATE).edit()
                .remove("profile")
                .remove("profileName")
                .apply()
            lifecycleScope.launch(Dispatchers.IO) {
                usbProfileSourceFile().delete()
                File(filesDir, "epicdash-mainController.ini.tmp").delete()
            }
            DashboardDataHub.setUsbChannelCatalog(JSONArray())
            if (::usbEcuManager.isInitialized) usbEcuManager.setProfile(null)
            return true
        }

        @JavascriptInterface
        fun openStockEpicDash() {
            runOnUiThread {
                LifecycleDiagnostics.mark(activityOwnerId, "navigate-stock")
                startActivity(Intent(this@DashboardLabActivity, MainActivity::class.java))
                finish()
            }
        }

        @JavascriptInterface
        fun openMainSettings() {
            runOnUiThread {
                LifecycleDiagnostics.mark(activityOwnerId, "navigate-settings")
                startActivity(Intent(this@DashboardLabActivity, SettingsActivity::class.java))
            }
        }

        @JavascriptInterface
        fun exportDashboardLayout(fileName: String, layoutJson: String) {
            pendingLayoutExportJson = layoutJson.take(2_000_000)
            runOnUiThread {
                val safeName = fileName
                    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
                    .trim('-')
                    .ifBlank { "EpicDash-JZ-layout.json" }
                    .let { if (it.endsWith(".json", ignoreCase = true)) it else "$it.json" }
                createLayoutFileLauncher.launch(safeName)
            }
        }

        @JavascriptInterface
        fun importDashboardLayout() {
            runOnUiThread {
                openLayoutFileLauncher.launch(
                    arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
                )
            }
        }

        @JavascriptInterface
        fun copyDiagnosticReport() {
            val report = buildDiagnosticReport()
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("EpicDash JZ diagnostics", report))
                DiagnosticStore.addEvent(this@DashboardLabActivity, "DIAG", "Diagnostic report copied")
                dispatchLabNotice("Diagnostic report copied")
            }
        }

        @JavascriptInterface
        fun openMslFile() {
            runOnUiThread {
                openMslFileLauncher.launch(
                    arrayOf(
                        "text/plain",
                        "text/tab-separated-values",
                        "application/octet-stream",
                        "application/*"
                    )
                )
            }
        }

        @JavascriptInterface
        fun setMslPaused(paused: Boolean) {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.setPaused(paused) }
        }

        @JavascriptInterface
        fun setMslSpeed(speed: Double) {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.setSpeed(speed) }
        }

        @JavascriptInterface
        fun seekMsl(fraction: Double) {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.seekToFraction(fraction) }
        }

        @JavascriptInterface
        fun seekMslRelative(seconds: Double) {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.seekBySeconds(seconds) }
        }

        @JavascriptInterface
        fun stepMslRows(rows: Int) {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.stepRows(rows.coerceIn(-100, 100)) }
        }

        @JavascriptInterface
        fun setMslLoop(enabled: Boolean) {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.setLoop(enabled) }
        }

        @JavascriptInterface
        fun stopMsl() {
            runOnUiThread { if (::mslPlayer.isInitialized) mslPlayer.stop() }
        }
    }

    private fun dispatchMslEvent(event: JSONObject) {
        val payload = event.toString()
        runOnUiThread {
            if (!::webView.isInitialized || isFinishing || isDestroyed) return@runOnUiThread
            if (!webPageReady) {
                if (pendingMslEvents.size >= 32) pendingMslEvents.removeFirst()
                pendingMslEvents.addLast(payload)
            } else {
                evaluateMslEvent(payload)
            }
        }
    }

    private fun evaluateMslEvent(payload: String) {
        webView.evaluateJavascript(
            "window.EpicDashNativeMslEvent && window.EpicDashNativeMslEvent($payload);",
            null
        )
    }


    private fun dispatchLabNotice(message: String) {
        val safe = JSONObject.quote(message)
        runOnUiThread {
            if (::webView.isInitialized && !isFinishing && !isDestroyed && webPageReady) {
                webView.evaluateJavascript(
                    "window.EpicDashNativeNotice && window.EpicDashNativeNotice($safe);",
                    null
                )
            }
        }
    }

    private fun appInfoJson(): JSONObject {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return JSONObject()
            .put("versionName", packageInfo.versionName ?: "unknown")
            .put("versionCode", versionCode)
            .put("packageName", packageName)
            .put("buildType", if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) "debug" else "release")
            .put("androidUserId", Process.myUid() / 100000)
            .put("lastUpdateTime", packageInfo.lastUpdateTime)
    }

    private fun runtimeSnapshotJson(): JSONObject =
        dashboardRuntimeSnapshots.snapshot(System.currentTimeMillis())

    private fun previousProcessExitReasonsJson(): JSONArray {
        val result = JSONArray()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return result
        return runCatching {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.getHistoricalProcessExitReasons(packageName, 0, 6).forEach { info ->
                result.put(
                    JSONObject()
                        .put("reason", info.reason)
                        .put("reasonLabel", processExitReasonLabel(info.reason))
                        .put("timestamp", info.timestamp)
                        .put("status", info.status)
                        .put("importance", info.importance)
                        .put("pssBytes", info.pss)
                        .put("rssBytes", info.rss)
                        .put("processName", info.processName ?: "")
                        .put("description", info.description?.toString()?.take(240) ?: "")
                )
            }
            result
        }.getOrDefault(result)
    }

    private fun processExitReasonLabel(reason: Int): String = when (reason) {
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN"
    }

    private fun buildDiagnosticReport(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val store = DiagnosticStore.snapshotJson(this)
        val ble = DashboardDataHub.diagnosticsJson()
        val usb = if (::usbEcuManager.isInitialized) {
            usbEcuManager.diagnosticsJson()
        } else {
            JSONObject().put("state", "initializing")
        }
            .put(
                "iniCompatibility",
                if (::usbEcuManager.isInitialized) usbEcuManager.iniCompatibilityJson()
                else JSONObject().put("state", "red").put("reason", "USB manager is still initializing")
            )
            .put("iniImport", usbIniImportStatusJson())
            .put("tuningWriteBridge", tuningWriteBridgeStatusJson())
        val dashboard = runtimeSnapshotJson()
        val lifecycle = LifecycleDiagnostics.snapshotJson()
            .put("previousProcessExitReasons", previousProcessExitReasonsJson())
            .put("lastUncaughtCrash", CrashTraceStore.diagnosticsJson(this))
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return DiagnosticReportFormatter.format(
            DiagnosticReportInput(
                generatedAtMs = System.currentTimeMillis(),
                timeZone = TimeZone.getDefault(),
                versionName = packageInfo.versionName,
                versionCode = versionCode,
                androidRelease = Build.VERSION.RELEASE,
                androidApi = Build.VERSION.SDK_INT,
                deviceManufacturer = Build.MANUFACTURER,
                deviceModel = Build.MODEL,
                bleJson = ble.toString(2),
                usbJson = usb.toString(2),
                lifecycleJson = lifecycle.toString(2),
                dashboardJson = if (dashboard.length() > 0) dashboard.toString(2) else null,
                events = diagnosticHistoryEntries(store.optJSONArray("events")),
                warnings = diagnosticHistoryEntries(store.optJSONArray("warnings"))
            )
        )
    }

    private fun diagnosticHistoryEntries(entries: JSONArray?): List<DiagnosticReportHistoryEntry> {
        if (entries == null || entries.length() == 0) return emptyList()
        val result = ArrayList<DiagnosticReportHistoryEntry>(entries.length())
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            result += DiagnosticReportHistoryEntry(
                timestampMs = item.optLong("timestamp", 0L),
                category = item.optString("category", item.optString("ruleId", "EVENT")),
                title = item.optString("title"),
                message = item.optString("message"),
                repeatCount = item.optInt("repeatCount", 1),
                source = item.optString("source")
            )
        }
        return result
    }

    private fun formatRowCount(rows: Int): String = when {
        rows >= 1_000_000 -> String.format("%.1fM", rows / 1_000_000.0)
        rows >= 1_000 -> String.format("%.1fk", rows / 1_000.0)
        else -> rows.toString()
    }

    private fun applyKeepScreenAwakeFlag() {
        if (keepScreenAwakeEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyDashboardWindowMode() {
        applyKeepScreenAwakeFlag()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }
}
