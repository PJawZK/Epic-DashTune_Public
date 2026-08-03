package com.buttonbox.ble

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import kotlin.math.min

/**
 * Read-only Android USB-host transport for rusEFI/EpicEFI TunerStudio output channels.
 * No controller command capable of changing ECU state is implemented here.
 *
 * v0.11.4 retains the proven v0.11.3 CRC protocol path and replaces tiny split USB reads
 * preflight. CRC frames are submitted in exactly one Android bulk transfer, a framed S command
 * establishes parser synchronization, and tiny read-only O requests are verified before streaming.
 */
class UsbEcuManager(
    private val context: Context,
    private val callback: Callback
) {
    enum class State { DISABLED, NO_PROFILE, WAITING_DEVICE, PERMISSION, OPENING, HANDSHAKE, STREAMING, RETRY_WAIT, ERROR }

    interface Callback {
        fun onUsbStateChanged(state: State, message: String, generation: Long)
        fun onUsbValues(values: Map<String, Float>, elapsedMs: Long, generation: Long)
        fun onUsbLog(message: String)
    }

    private data class BulkPipe(
        val usbInterface: UsbInterface,
        val input: UsbEndpoint,
        val output: UsbEndpoint
    )

    private data class CdcUnion(
        val masterInterfaceId: Int,
        val slaveInterfaceIds: List<Int>,
        val source: String
    )

    private data class ProbeSpec(
        val command: Byte,
        val framed: Boolean,
        val controlLineState: Int
    ) {
        val commandText: String get() = (command.toInt() and 0xff).toChar().toString()
        val framingText: String get() = if (framed) "framed" else "plain"
        val lineStateText: String get() = if (controlLineState == CONTROL_DTR) "DTR" else "DTR+RTS"
        val label: String get() = "$framingText $commandText / $lineStateText"
    }

    private data class ProbeOpenResult(
        val lineCodingResult: Int,
        val controlLineResult: Int,
        val controlClaimed: Boolean,
        val dataClaimed: Boolean
    )

    private data class ProbeReadResult(
        val rawBytes: ByteArray,
        val payloadBytes: ByteArray,
        val signature: String,
        val responseMode: String
    )

    private data class CanonicalSpec(
        val key: String,
        val candidates: List<String>,
        val plausibleMin: Float? = null,
        val plausibleMax: Float? = null
    ) {
        fun accepts(value: Float): Boolean = value.isFinite() &&
            (plausibleMin == null || value >= plausibleMin) &&
            (plausibleMax == null || value <= plausibleMax)
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "EpicDash-USB").apply { isDaemon = true }
    }
    private val active = AtomicBoolean(false)
    private val reconnectQueued = AtomicBoolean(false)
    private val disconnectQueued = AtomicBoolean(false)
    private val shutdownStarted = AtomicBoolean(false)
    private val generationAuthority = UsbGenerationAuthority()
    private val pendingPermissionRequest = UsbPermissionRequestTracker()
    private val permissionRequestCoordinator =
        UsbPermissionRequestCoordinator(generationAuthority, pendingPermissionRequest)
    private var loopFuture: ScheduledFuture<*>? = null
    private var retryFuture: ScheduledFuture<*>? = null
    private var connection: UsbDeviceConnection? = null
    private var claimedInterfaces: List<UsbInterface> = emptyList()
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var currentDevice: UsbDevice? = null
    private var workingProbe: ProbeSpec? = null

    @Volatile private var profile: UsbTunerStudioProfile? = null
    @Volatile private var preferredPollHz: Int = 20
    @Volatile private var performanceProfile: PerformanceProfile = PerformanceProfile.FULL_OPTIMIZED
    @Volatile private var requestedChannelNames: Set<String> = emptySet()
    @Volatile private var decodePlan: List<UsbOutputChannel> = emptyList()
    @Volatile private var canonicalDecodePlan: List<Pair<CanonicalSpec, List<UsbOutputChannel>>> = emptyList()
    private val optimizedReceiveBuffer = ByteArray(4096)
    private var optimizedPacketBuffer = ByteArray(8192)
    @Volatile private var streamEnvelopeReaderConfirmed: Boolean = false
    @Volatile private var streamReadMode: String = "full_block"
    @Volatile private var fullBlockFallbacks: Long = 0L
    @Volatile private var state: State = State.NO_PROFILE
    @Volatile private var stateMessage: String = "Import mainController.ini"
    @Volatile private var ecuSignature: String = ""
    @Volatile private var signatureMatches: Boolean? = null
    @Volatile private var lastPacketElapsedMs: Long = 0L
    @Volatile private var lastError: String = ""
    @Volatile private var deviceSummary: String = ""
    @Volatile private var measuredHz: Double = 0.0
    @Volatile private var lastActiveMeasuredHz: Double = 0.0
    @Volatile private var streamingStartedElapsedMs: Long = 0L
    @Volatile private var lastStreamingDurationMs: Long = 0L
    @Volatile private var lastDisconnectCause: String = ""
    @Volatile private var latestOutputBlock: ByteArray? = null
    @Volatile private var latestOutputBlockElapsedMs: Long = 0L
    @Volatile private var longestReadMs: Long = 0L
    @Volatile private var handshakeStage: String = "idle"
    @Volatile private var selectedInterfaceId: Int = -1
    @Volatile private var selectedInterfaceClass: Int = -1
    @Volatile private var selectedInterfaceSubclass: Int = -1
    @Volatile private var selectedInterfaceProtocol: Int = -1
    @Volatile private var selectedControlInterfaceId: Int = -1
    @Volatile private var cdcUnionMasterInterfaceId: Int = -1
    @Volatile private var cdcUnionSlaveInterfaceId: Int = -1
    @Volatile private var selectedEndpointIn: Int = -1
    @Volatile private var selectedEndpointOut: Int = -1
    @Volatile private var selectedEndpointInMaxPacket: Int = 0
    @Volatile private var selectedEndpointOutMaxPacket: Int = 0
    @Volatile private var setLineCodingResult: Int = Int.MIN_VALUE
    @Volatile private var setControlLineStateResult: Int = Int.MIN_VALUE
    @Volatile private var successfulProbeMode: String = ""
    @Volatile private var consecutiveFailures: Int = 0

    private val probeLock = Any()
    private val probeAttempts = mutableListOf<String>()
    private val protocolProbeLock = Any()
    private val protocolProbeAttempts = mutableListOf<String>()
    private val frames = AtomicLong(0)
    private val requests = AtomicLong(0)
    private val bytesRx = AtomicLong(0)
    private val bytesTx = AtomicLong(0)
    private val crcErrors = AtomicLong(0)
    private val protocolErrors = AtomicLong(0)
    private val reconnects = AtomicLong(0)
    private val rawFrameRate = UsbFrameRateTracker()

    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private fun permissionIntent(generation: Long): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            (generation xor (generation ushr 32)).toInt(),
            Intent(permissionAction).setPackage(context.packageName)
                .putExtra(EXTRA_PERMISSION_GENERATION, generation),
            PendingIntent.FLAG_CANCEL_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != permissionAction) return

            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val callbackGeneration = intent.getLongExtra(EXTRA_PERMISSION_GENERATION, Long.MIN_VALUE)
            if (device == null) return
            val request = pendingPermissionRequest.consume(device.deviceId, callbackGeneration) ?: return
            val generation = request.generation
            if (granted) executor.execute { connect(device, generation) }
            else executor.execute {
                generationAuthority.runIfCurrent(generation) {
                    active.set(false)
                    lastError = "USB permission declined"
                    handshakeStage = "permission_declined"
                    setState(State.ERROR, "USB permission declined • tap Reconnect", generation)
                }
            }
        }
    }

    private val usbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (active.get()) {
                    val attached = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (attached == null) return
                    val descriptor = selectionDescriptor(attached)
                    when {
                        !UsbDeviceSelectionPolicy.isSupportedDevice(descriptor) -> {
                            callback.onUsbLog("Ignoring unrelated USB attach: ${describeDevice(attached)}")
                        }
                        state == State.STREAMING && currentDevice != null -> {
                            callback.onUsbLog(
                                "Ignoring USB attach while current ECU session is streaming: ${describeDevice(attached)}"
                            )
                        }
                        else -> {
                            val generation = beginConnectionAttempt()
                            executor.execute { discoverAndConnect(generation) }
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detached = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (detached != null && UsbDeviceSelectionPolicy.shouldInvalidateForDetach(
                            detached.deviceId,
                            currentDevice?.deviceId
                        )) {
                        // Invalidate the dashboard immediately. Waiting for the single USB executor
                        // would leave live-looking values visible until a blocking bulk read timed out.
                        loopFuture?.cancel(true)
                        loopFuture = null
                        val generation = invalidateGeneration()
                        setState(State.WAITING_DEVICE, "Waiting for USB ECU", generation)
                        try { connection?.close() } catch (_: Exception) { }
                        executor.execute { closeConnection("ECU USB disconnected", "physical_detach") }
                    }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(permissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val usbDeviceFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            usbDeviceReceiver,
            usbDeviceFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun setProfile(value: UsbTunerStudioProfile?) {
        profile = value
        rebuildDecodePlan()
        consecutiveFailures = 0
        lastError = ""
        if (value == null) {
            disconnect("USB profile removed")
            setState(State.NO_PROFILE, "Import mainController.ini", generationAuthority.current())
        } else if (active.get()) {
            reconnectNow()
        } else {
            setState(State.WAITING_DEVICE, "Profile ready • ${value.channels.size} channels", generationAuthority.current())
        }
    }

    fun setPerformanceProfile(value: PerformanceProfile) {
        performanceProfile = value
        PerformanceMetrics.setProfile(value)
        rebuildDecodePlan()
        callback.onUsbLog("Performance profile: ${value.label}")
    }

    fun currentPerformanceProfile(): PerformanceProfile = performanceProfile

    fun setRequiredChannels(names: Collection<String>) {
        val next = names.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (next == requestedChannelNames) return
        requestedChannelNames = next
        rebuildDecodePlan()
    }

    private fun rebuildDecodePlan() {
        val selectedProfile = profile
        if (selectedProfile == null) {
            decodePlan = emptyList()
            canonicalDecodePlan = emptyList()
            PerformanceMetrics.setCounter("decodePlanChannels", 0)
            return
        }
        val byLower = selectedProfile.channels.associateBy { it.name.lowercase(Locale.US) }
        val canonical = CANONICAL_SPECS.map { spec ->
            spec to spec.candidates.mapNotNull { byLower[it.lowercase(Locale.US)] }.distinctBy { it.name }
        }
        canonicalDecodePlan = canonical
        decodePlan = if (!performanceProfile.selectiveDecode) {
            selectedProfile.channels
        } else {
            val needed = LinkedHashSet<UsbOutputChannel>()
            requestedChannelNames.forEach { name -> byLower[name.lowercase(Locale.US)]?.let(needed::add) }
            canonical.forEach { (_, channels) -> needed.addAll(channels) }
            needed.toList().sortedBy { it.offset }
        }
        PerformanceMetrics.setCounter("decodePlanChannels", decodePlan.size.toLong())
        PerformanceMetrics.setCounter("profileChannels", selectedProfile.channels.size.toLong())
    }

    fun setPollHz(hz: Int) {
        preferredPollHz = hz.coerceIn(5, 20)
        if (state == State.STREAMING) {
            val generation = generationAuthority.current()
            executor.execute {
                if (state == State.STREAMING && generationAuthority.isCurrent(generation)) pollLoop(generation)
            }
        }
    }

    fun currentMeasuredHz(): Double = if (state == State.STREAMING) measuredHz else 0.0
    fun currentPollTargetHz(): Int = preferredPollHz

    fun start() {
        if (!active.compareAndSet(false, true)) return
        consecutiveFailures = 0
        lastError = ""
        retryFuture?.cancel(false)
        retryFuture = null
        val generation = beginConnectionAttempt()
        executor.execute { discoverAndConnect(generation) }
    }

    fun reconnectNow(): Long {
        if (!reconnectQueued.compareAndSet(false, true)) return generationAuthority.current()
        if (!active.get()) active.set(true)
        val generation = beginConnectionAttempt()
        setState(State.WAITING_DEVICE, "Manual USB reconnect requested", generation)
        executor.execute {
            try {
                consecutiveFailures = 0
                lastError = ""
                retryFuture?.cancel(false)
                retryFuture = null
                reconnects.incrementAndGet()
                closeConnection("Manual USB reconnect", "manual_reconnect")
                discoverAndConnect(generation)
            } finally {
                reconnectQueued.set(false)
            }
        }
        return generation
    }

    fun disconnect(reason: String = "USB disconnected by user"): Long {
        active.set(false)
        val generation = invalidateGeneration()

        // Update the dashboard immediately instead of waiting behind a blocking USB read.
        loopFuture?.cancel(true)
        loopFuture = null
        retryFuture?.cancel(false)
        retryFuture = null
        setState(if (profile == null) State.NO_PROFILE else State.DISABLED, reason, generation)

        // Closing the connection here also interrupts an in-flight bulkTransfer on many devices.
        try { connection?.close() } catch (_: Exception) { }

        if (disconnectQueued.compareAndSet(false, true)) {
            executor.execute {
                try {
                    closeConnection(reason, "user")
                } finally {
                    disconnectQueued.set(false)
                }
            }
        }
        return generation
    }

    fun shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) return
        active.set(false)
        invalidateGeneration()
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: Exception) {
        }
        try {
            context.unregisterReceiver(usbDeviceReceiver)
        } catch (_: Exception) {
        }
        loopFuture?.cancel(true)
        retryFuture?.cancel(true)
        closeConnection("USB manager stopped", "lifecycle_shutdown")
        executor.shutdownNow()
    }

    private fun discoverAndConnect(generation: Long) {
        if (!active.get() || !generationAuthority.isCurrent(generation)) return
        retryFuture = null
        val selectedProfile = profile
        if (selectedProfile == null) {
            setState(State.NO_PROFILE, "Import mainController.ini", generation)
            return
        }
        val attachedDevices = usbManager.deviceList.values.toList()
        val selectedDescriptor = UsbDeviceSelectionPolicy.selectCandidate(
            attachedDevices.map(::selectionDescriptor)
        )
        val device = selectedDescriptor?.let { selected ->
            attachedDevices.firstOrNull { it.deviceId == selected.deviceId }
        }
        if (device == null) {
            setState(State.WAITING_DEVICE, "Connect supported Mega144H7 with USB OTG", generation)
            scheduleRetry(2000, generation)
            return
        }
        currentDevice = device
        deviceSummary = describeDevice(device)
        if (!usbManager.hasPermission(device)) {
            setState(State.PERMISSION, "USB permission required", generation)
            permissionRequestCoordinator.publishIfCurrent(device.deviceId, generation) {
                usbManager.requestPermission(device, permissionIntent(generation))
            }
            return
        }
        connect(device, generation)
    }

    private fun selectionDescriptor(device: UsbDevice): UsbDeviceDescriptor {
        val interfaces = buildList {
            for (interfaceIndex in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(interfaceIndex)
                val endpoints = buildList {
                    for (endpointIndex in 0 until usbInterface.endpointCount) {
                        val endpoint = usbInterface.getEndpoint(endpointIndex)
                        add(
                            UsbEndpointDescriptor(
                                direction = endpoint.direction,
                                transferType = endpoint.type
                            )
                        )
                    }
                }
                add(
                    UsbInterfaceDescriptor(
                        id = usbInterface.id,
                        interfaceClass = usbInterface.interfaceClass,
                        interfaceSubclass = usbInterface.interfaceSubclass,
                        interfaceProtocol = usbInterface.interfaceProtocol,
                        endpoints = endpoints
                    )
                )
            }
        }
        return UsbDeviceDescriptor(
            deviceId = device.deviceId,
            vendorId = device.vendorId,
            productId = device.productId,
            deviceClass = device.deviceClass,
            deviceSubclass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            interfaces = interfaces
        )
    }

    private fun connect(device: UsbDevice, generation: Long) {
        val selectedProfile = profile ?: return
        if (!active.get() || !generationAuthority.isCurrent(generation)) return

        retryFuture?.cancel(false)
        retryFuture = null
        closeConnection("")
        ecuSignature = ""
        signatureMatches = null
        resetProbeDiagnostics()
        handshakeStage = "enumerate_usb"
        setState(State.OPENING, "Inspecting ${describeDevice(device)}", generation)
        logDeviceLayout(device)

        val descriptorConnection = usbManager.openDevice(device) ?: run {
            failTransport("Android could not open USB device", retryAllowed = false, generation = generation)
            return
        }
        val unions = try {
            parseCdcUnions(descriptorConnection.rawDescriptors)
        } catch (error: Exception) {
            callback.onUsbLog("USB CDC descriptor parse failed: ${error.message}")
            emptyList()
        } finally {
            try {
                descriptorConnection.close()
            } catch (_: Exception) {
            }
        }
        unions.forEach { union ->
            callback.onUsbLog(
                "USB CDC ${union.source}: control ${union.masterInterfaceId} -> data ${union.slaveInterfaceIds.joinToString()}"
            )
        }

        val selectedUnion = chooseCdcUnion(device, unions)
        val preferredDataId = selectedUnion?.slaveInterfaceIds?.firstOrNull()
        val pipe = findBulkPipe(device, preferredDataId) ?: run {
            failTransport("No bulk USB serial endpoints", retryAllowed = false, generation = generation)
            return
        }
        val controlInterface = findControlInterface(device, selectedUnion?.masterInterfaceId)

        cdcUnionMasterInterfaceId = selectedUnion?.masterInterfaceId ?: -1
        cdcUnionSlaveInterfaceId = preferredDataId ?: -1
        selectedControlInterfaceId = controlInterface?.id ?: -1
        updateSelectedPipe(pipe)
        callback.onUsbLog(
            "USB selected control ${selectedControlInterfaceId.takeIf { it >= 0 } ?: "none"}, " +
                "data ${pipe.usbInterface.id}, IN ${hexByte(pipe.input.address)}, OUT ${hexByte(pipe.output.address)}"
        )

        val allProbes = buildProbeSequence()
        val preferredFirst = workingProbe?.let { cached ->
            listOf(cached) + allProbes.filterNot { it == cached }
        } ?: allProbes

        setState(State.HANDSHAKE, "Running USB compatibility probe", generation)
        for ((index, spec) in preferredFirst.withIndex()) {
            if (!active.get() || !generationAuthority.isCurrent(generation)) return
            val result = try {
                runProbe(device, pipe, controlInterface, spec, index + 1, preferredFirst.size, generation)
            } catch (error: Exception) {
                recordProbeFailure(spec, index + 1, error.message ?: "Probe failed")
                callback.onUsbLog("USB probe ${index + 1}/${preferredFirst.size} ${spec.label}: ${error.message}")
                closeConnection("")
                continue
            }

            if (!isLikelySignature(result.signature)) {
                closeConnection("")
                continue
            }

            workingProbe = spec
            successfulProbeMode = spec.label
            ecuSignature = result.signature
            signatureMatches = UsbDeviceSelectionPolicy.exactSignatureMatches(
                expected = selectedProfile.signature,
                actual = ecuSignature
            )
            callback.onUsbLog(
                "USB probe success ${spec.label}; signature '$ecuSignature'; response ${result.responseMode}"
            )
            if (signatureMatches != true) {
                if (!generationAuthority.isCurrent(generation)) return
                handshakeStage = "signature_rejected"
                lastError = if (selectedProfile.signature.isBlank()) {
                    "Imported INI has no ECU signature; output streaming blocked"
                } else {
                    "ECU signature does not match imported INI; output streaming blocked"
                }
                callback.onUsbLog(
                    "$lastError: ECU '$ecuSignature' vs INI '${selectedProfile.signature}'"
                )
                active.set(false)
                closeConnection("", "signature_mismatch")
                setState(State.ERROR, "$lastError • tap Reconnect", generation)
                return
            }

            try {
                streamEnvelopeReaderConfirmed = false
                runFramedProtocolPreflight(selectedProfile, generation)
                handshakeStage = "first_output_request"
                val firstBlock = readOutputBlock(selectedProfile)
                if (!generationAuthority.isCurrent(generation)) return
                publishBlock(firstBlock, selectedProfile, generation)
                consecutiveFailures = 0
                lastError = ""
                handshakeStage = "streaming"
                setState(
                    State.STREAMING,
                    if (signatureMatches == false) "Streaming • INI signature mismatch" else "Streaming read-only",
                    generation
                )
                pollLoop(generation)
                return
            } catch (error: Exception) {
                failTransport(
                    error.message ?: "First output request failed",
                    retryAllowed = false,
                    generation = generation
                )
                return
            }
        }

        generationAuthority.runIfCurrent(generation) {
            handshakeStage = "probe_complete_no_response"
            lastError = "USB compatibility probe received no valid ECU signature"
            consecutiveFailures = 1
            active.set(false)
            closeConnection("")
            callback.onUsbLog("USB compatibility probe exhausted all ${preferredFirst.size} modes; stopping cleanly")
            setState(State.ERROR, "$lastError • tap Reconnect", generation)
        }
    }

    private fun buildProbeSequence(): List<ProbeSpec> {
        val result = mutableListOf<ProbeSpec>()
        for (lineState in listOf(CONTROL_DTR, CONTROL_DTR_RTS)) {
            result += ProbeSpec('S'.code.toByte(), framed = false, controlLineState = lineState)
            result += ProbeSpec('Q'.code.toByte(), framed = false, controlLineState = lineState)
            result += ProbeSpec('S'.code.toByte(), framed = true, controlLineState = lineState)
            result += ProbeSpec('Q'.code.toByte(), framed = true, controlLineState = lineState)
        }
        return result
    }

    private fun runProbe(
        device: UsbDevice,
        pipe: BulkPipe,
        controlInterface: UsbInterface?,
        spec: ProbeSpec,
        probeIndex: Int,
        totalProbes: Int,
        generation: Long
    ): ProbeReadResult {
        if (!generationAuthority.isCurrent(generation)) throw InterruptedException("Obsolete USB generation")
        closeConnection("")
        handshakeStage = "probe_${probeIndex}_open"
        setState(State.HANDSHAKE, "USB probe $probeIndex/$totalProbes • ${spec.label}", generation)

        val setup = openProbeConnection(device, pipe, controlInterface, spec.controlLineState)
        callback.onUsbLog(
            "USB probe $probeIndex/$totalProbes ${spec.label}: " +
                "claim control=${setup.controlClaimed}, data=${setup.dataClaimed}, " +
                "SET_LINE_CODING=${setup.lineCodingResult}, SET_CONTROL_LINE_STATE=${setup.controlLineResult}"
        )

        handshakeStage = "probe_${probeIndex}_settle"
        SystemClock.sleep(PORT_OPEN_SETTLE_MS)

        val startupBytes = readRawUntilQuiet(STARTUP_READ_MS, QUIET_WINDOW_MS)
        if (startupBytes.isNotEmpty()) {
            callback.onUsbLog("USB probe $probeIndex RX startup (${startupBytes.size}): ${hex(startupBytes)}")
            val startupResult = decodeProbeResponse(startupBytes, spec.framed)
            recordProbeResult(spec, probeIndex, setup, ByteArray(0), startupResult, "startup")
            if (isLikelySignature(startupResult.signature)) return startupResult
        } else {
            callback.onUsbLog("USB probe $probeIndex RX startup: <none>")
        }

        val command = byteArrayOf(spec.command)
        val transmitted = if (spec.framed) envelope(command) else command
        handshakeStage = "probe_${probeIndex}_write"
        callback.onUsbLog("USB probe $probeIndex TX (${transmitted.size}): ${hex(transmitted)}")
        writeFully(transmitted)

        handshakeStage = "probe_${probeIndex}_read"
        val raw = readRawUntilQuiet(PROBE_READ_TIMEOUT_MS, QUIET_WINDOW_MS)
        callback.onUsbLog(
            if (raw.isEmpty()) "USB probe $probeIndex RX: <none>"
            else "USB probe $probeIndex RX (${raw.size}): ${hex(raw)}"
        )
        val decoded = decodeProbeResponse(raw, spec.framed)
        recordProbeResult(spec, probeIndex, setup, transmitted, decoded, "query")
        return decoded
    }

    private fun openProbeConnection(
        device: UsbDevice,
        pipe: BulkPipe,
        controlInterface: UsbInterface?,
        controlLineState: Int
    ): ProbeOpenResult {
        val opened = usbManager.openDevice(device) ?: throw IllegalStateException("Android could not reopen USB device")
        val claims = mutableListOf<UsbInterface>()
        var controlClaimed = false
        var dataClaimed = false
        try {
            if (controlInterface != null && controlInterface.id != pipe.usbInterface.id) {
                controlClaimed = try {
                    opened.claimInterface(controlInterface, true)
                } catch (_: Exception) {
                    false
                }
                if (controlClaimed) claims += controlInterface
            }
            dataClaimed = opened.claimInterface(pipe.usbInterface, true)
            if (!dataClaimed) throw IllegalStateException("Could not claim USB data interface ${pipe.usbInterface.id}")
            claims += pipe.usbInterface

            connection = opened
            claimedInterfaces = claims
            endpointIn = pipe.input
            endpointOut = pipe.output
            currentDevice = device
            deviceSummary = describeDevice(device)
            updateSelectedPipe(pipe)

            val controlId = controlInterface?.id ?: 0
            val setup = configureCdc(opened, controlId, controlLineState)
            setLineCodingResult = setup.first
            setControlLineStateResult = setup.second
            return ProbeOpenResult(
                lineCodingResult = setup.first,
                controlLineResult = setup.second,
                controlClaimed = controlClaimed,
                dataClaimed = dataClaimed
            )
        } catch (error: Exception) {
            claims.forEach { claimed ->
                try {
                    opened.releaseInterface(claimed)
                } catch (_: Exception) {
                }
            }
            try {
                opened.close()
            } catch (_: Exception) {
            }
            if (connection === opened) {
                connection = null
                claimedInterfaces = emptyList()
                endpointIn = null
                endpointOut = null
            }
            throw error
        }
    }

    private fun configureCdc(
        conn: UsbDeviceConnection,
        controlInterfaceId: Int,
        controlLineState: Int
    ): Pair<Int, Int> {
        val lineCoding = byteArrayOf(
            0x00,
            0xC2.toByte(),
            0x01,
            0x00,
            0x00,
            0x00,
            0x08
        )
        val lineResult = try {
            conn.controlTransfer(
                USB_REQUEST_TYPE_CLASS_INTERFACE_OUT,
                CDC_SET_LINE_CODING,
                0,
                controlInterfaceId,
                lineCoding,
                lineCoding.size,
                CONTROL_TRANSFER_TIMEOUT_MS
            )
        } catch (error: Exception) {
            callback.onUsbLog("SET_LINE_CODING exception: ${error.message}")
            Int.MIN_VALUE
        }
        val controlResult = try {
            conn.controlTransfer(
                USB_REQUEST_TYPE_CLASS_INTERFACE_OUT,
                CDC_SET_CONTROL_LINE_STATE,
                controlLineState,
                controlInterfaceId,
                null,
                0,
                CONTROL_TRANSFER_TIMEOUT_MS
            )
        } catch (error: Exception) {
            callback.onUsbLog("SET_CONTROL_LINE_STATE exception: ${error.message}")
            Int.MIN_VALUE
        }
        return lineResult to controlResult
    }

    private fun runFramedProtocolPreflight(selectedProfile: UsbTunerStudioProfile, generation: Long) {
        handshakeStage = "crc_sync_s"
        setState(State.HANDSHAKE, "Establishing CRC protocol sync", generation)

        val syncPayload = byteArrayOf('S'.code.toByte())
        val syncFrame = envelope(syncPayload)
        val syncWrite = strictWriteFrame(syncFrame, "framed S sync", logSuccess = true)
        val syncRaw = readRawUntilQuiet(PROTOCOL_PREFLIGHT_TIMEOUT_MS, QUIET_WINDOW_MS)
        callback.onUsbLog(
            if (syncRaw.isEmpty()) "CRC sync S RX: <none>"
            else "CRC sync S RX (${syncRaw.size}): ${hexPreview(syncRaw)}"
        )
        val syncBody = decodeEnvelopeIfComplete(syncRaw)
            ?: throw IllegalStateException(
                "CRC sync S received no complete envelope after ${syncWrite.written}/${syncWrite.requested} byte write"
            )
        val syncPayloadResponse = stripStatusByte(syncBody)
        val syncSignature = sanitizeSignature(syncPayloadResponse)
        recordProtocolProbe(
            stage = "crc_sync_s",
            label = "framed S",
            transmitted = syncFrame,
            writeResult = syncWrite,
            received = syncRaw,
            decodedBody = syncBody,
            status = statusByte(syncBody),
            dataBytes = syncPayloadResponse.size,
            success = isLikelySignature(syncSignature),
            detail = syncSignature
        )
        if (!isLikelySignature(syncSignature)) {
            throw IllegalStateException("CRC sync S returned no plausible signature")
        }
        callback.onUsbLog("CRC protocol synchronized with framed S; signature '$syncSignature'")

        val probeCounts = intArrayOf(4, 16, 64, 1024)
        for (count in probeCounts) {
            if (count > selectedProfile.outputBlockSize) continue
            handshakeStage = "output_probe_$count"
            setState(State.HANDSHAKE, "Verifying read-only output request • $count bytes", generation)
            val payload = outputPayload(offset = 0, count = count)
            val frame = envelope(payload)
            requests.incrementAndGet()
            val writeResult = strictWriteFrame(frame, "O offset=0 count=$count", logSuccess = true)
            val raw = readRawUntilQuiet(PROTOCOL_PREFLIGHT_TIMEOUT_MS, QUIET_WINDOW_MS)
            callback.onUsbLog(
                if (raw.isEmpty()) "Output probe $count RX: <none>"
                else "Output probe $count RX (${raw.size}): ${hexPreview(raw)}"
            )
            val body = decodeEnvelopeIfComplete(raw)
                ?: throw IllegalStateException("Output probe $count received no complete CRC envelope")
            val data = extractOutputData(body, count)
            recordProtocolProbe(
                stage = "output_probe_$count",
                label = "O offset=0 count=$count",
                transmitted = frame,
                writeResult = writeResult,
                received = raw,
                decodedBody = body,
                status = statusByte(body),
                dataBytes = data.size,
                success = data.size == count,
                detail = "received ${data.size}/$count output bytes"
            )
            callback.onUsbLog("Output probe $count passed (${data.size} data bytes)")
        }
    }

    private data class StrictWriteResult(
        val requested: Int,
        val written: Int,
        val elapsedMs: Long,
        val transfers: Int = 1
    )

    private fun strictWriteFrame(bytes: ByteArray, label: String, logSuccess: Boolean): StrictWriteResult {
        val conn = connection ?: throw IllegalStateException("USB connection closed")
        val endpoint = endpointOut ?: throw IllegalStateException("USB OUT endpoint missing")
        val started = SystemClock.elapsedRealtime()
        val wrote = conn.bulkTransfer(endpoint, bytes, bytes.size, STRICT_WRITE_TIMEOUT_MS)
        val elapsed = SystemClock.elapsedRealtime() - started
        if (wrote > 0) bytesTx.addAndGet(wrote.toLong())
        if (logSuccess || wrote != bytes.size) {
            callback.onUsbLog(
                "Strict USB TX $label: requested=${bytes.size}, written=$wrote, transfers=1, elapsed=${elapsed}ms, frame=${hex(bytes)}"
            )
        }
        if (wrote != bytes.size) {
            throw IllegalStateException("Strict USB write $label accepted $wrote/${bytes.size} bytes")
        }
        return StrictWriteResult(bytes.size, wrote, elapsed)
    }

    private fun outputPayload(offset: Int, count: Int): ByteArray = byteArrayOf(
        'O'.code.toByte(),
        (offset and 0xff).toByte(),
        ((offset ushr 8) and 0xff).toByte(),
        (count and 0xff).toByte(),
        ((count ushr 8) and 0xff).toByte()
    )

    private fun statusByte(body: ByteArray): Int? =
        body.firstOrNull()?.let { it.toInt() and 0xff }?.takeIf { it < 32 }

    private fun extractOutputData(response: ByteArray, count: Int): ByteArray = when {
        response.size == count -> response
        response.size == count + 1 -> {
            val status = response[0].toInt() and 0xff
            if (status != 0 && status != 1) {
                protocolErrors.incrementAndGet()
                throw IllegalStateException("ECU output response status ${hexByte(status)}")
            }
            response.copyOfRange(1, response.size)
        }
        response.size > count -> response.copyOfRange(response.size - count, response.size)
        else -> throw IllegalStateException("Short output response ${response.size}/$count bytes")
    }

    private fun recordProtocolProbe(
        stage: String,
        label: String,
        transmitted: ByteArray,
        writeResult: StrictWriteResult,
        received: ByteArray,
        decodedBody: ByteArray,
        status: Int?,
        dataBytes: Int,
        success: Boolean,
        detail: String
    ) {
        val item = JSONObject()
            .put("stage", stage)
            .put("label", label)
            .put("txHex", hex(transmitted))
            .put("txRequested", writeResult.requested)
            .put("txWritten", writeResult.written)
            .put("txTransfers", writeResult.transfers)
            .put("txElapsedMs", writeResult.elapsedMs)
            .put("rxBytes", received.size)
            .put("rxHex", hexPreview(received, 256))
            .put("decodedBodyBytes", decodedBody.size)
            .put("status", status ?: JSONObject.NULL)
            .put("dataBytes", dataBytes)
            .put("success", success)
            .put("detail", detail.take(240))
        synchronized(protocolProbeLock) { protocolProbeAttempts += item.toString() }
    }

    private fun pollLoop(generation: Long) {
        loopFuture?.cancel(true)
        scheduleNextPoll(0L, generation)
    }

    private fun scheduleNextPoll(delayMs: Long, generation: Long) {
        loopFuture = executor.schedule({
            if (!active.get() || state != State.STREAMING || !generationAuthority.isCurrent(generation)) return@schedule
            val selectedProfile = profile ?: return@schedule
            val cycleStarted = SystemClock.elapsedRealtime()
            try {
                val readStartedNs = System.nanoTime()
                val block = readOutputBlock(selectedProfile)
                PerformanceMetrics.timingUs("usbRead", System.nanoTime() - readStartedNs)
                val readElapsed = SystemClock.elapsedRealtime() - cycleStarted
                if (readElapsed > longestReadMs) longestReadMs = readElapsed
                publishBlock(block, selectedProfile, generation)
            } catch (error: Exception) {
                // Manual disconnect and physical detach deliberately close the connection from
                // another thread to interrupt bulkTransfer. Do not reinterpret that expected
                // exception as a transport failure or overwrite the immediate disconnected state.
                if (!active.get() || state != State.STREAMING) return@schedule
                failTransport(
                    error.message ?: "USB read failed",
                    retryAllowed = true,
                    generation = generation
                )
                return@schedule
            }
            if (active.get() && state == State.STREAMING && generationAuthority.isCurrent(generation)) {
                val targetPeriodMs = (1000L / preferredPollHz.coerceAtLeast(1)).coerceAtLeast(1L)
                val elapsed = SystemClock.elapsedRealtime() - cycleStarted
                scheduleNextPoll((targetPeriodMs - elapsed).coerceAtLeast(0L), generation)
            }
        }, delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
    }

    private fun publishBlock(block: ByteArray, selectedProfile: UsbTunerStudioProfile, generation: Long) {
        if (!generationAuthority.isCurrent(generation)) return
        val startedNs = System.nanoTime()
        val now = SystemClock.elapsedRealtime()

        val plan = if (decodePlan.isEmpty()) selectedProfile.channels else decodePlan
        val decoded = LinkedHashMap<String, Float>(plan.size + CANONICAL_SPECS.size)
        plan.forEach { channel ->
            channel.decode(block)?.toFloat()?.takeIf { it.isFinite() }?.let { decoded[channel.name] = it }
        }
        PerformanceMetrics.timingUs("channelDecode", System.nanoTime() - startedNs)
        PerformanceMetrics.sample("decodedChannels", plan.size.toLong())

        val canonicalStartedNs = System.nanoTime()
        canonicalDecodePlan.forEach { (spec, channels) ->
            val selected = channels.firstNotNullOfOrNull { channel ->
                val value = decoded[channel.name] ?: channel.decode(block)?.toFloat()?.takeIf { it.isFinite() }
                value?.takeIf(spec::accepts)
            }
            if (selected != null) decoded[spec.key] = selected
        }
        // Keep a tiny, direct TPS truth path for development diagnostics. These internal keys are
        // not user channels and are never written back to the ECU. They let the report distinguish
        // raw output bytes, profile decoding, canonical mapping and painted UI values.
        val tpsChannel = selectedProfile.channels.firstOrNull { it.name.equals("TPSValue", ignoreCase = true) }
        if (tpsChannel != null) {
            tpsChannel.rawNumeric(block)?.toFloat()?.takeIf { it.isFinite() }?.let { decoded["_traceTpsRawNumeric"] = it }
            tpsChannel.rawBytes(block)?.let { raw ->
                if (raw.isNotEmpty()) decoded["_traceTpsByte0"] = (raw[0].toInt() and 0xff).toFloat()
                if (raw.size > 1) decoded["_traceTpsByte1"] = (raw[1].toInt() and 0xff).toFloat()
            }
            tpsChannel.decode(block)?.toFloat()?.takeIf { it.isFinite() }?.let { decoded["_traceTpsDecoded"] = it }
            decoded["_traceTpsOffset"] = tpsChannel.offset.toFloat()
            decoded["_traceTpsScale"] = tpsChannel.scale.toFloat()
        }
        listOf("TPSValue", "rawTps1Primary", "tpsADC", "throttlePedalPosition", "DriverThrottleIntent").forEach { name ->
            if (!decoded.containsKey(name)) {
                selectedProfile.channels.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?.decode(block)?.toFloat()?.takeIf { it.isFinite() }
                    ?.let { decoded[name] = it }
            }
        }
        PerformanceMetrics.timingUs("canonicalMap", System.nanoTime() - canonicalStartedNs)

        val published = generationAuthority.runIfCurrent(generation) {
            latestOutputBlock = block
            latestOutputBlockElapsedMs = now
            lastPacketElapsedMs = now
            frames.incrementAndGet()
            measuredHz = rawFrameRate.recordCompletedFrame(lastPacketElapsedMs)
            if (measuredHz.isFinite() && measuredHz > 0.0) lastActiveMeasuredHz = measuredHz
            val callbackStartedNs = System.nanoTime()
            callback.onUsbValues(decoded, lastPacketElapsedMs, generation)
            PerformanceMetrics.timingUs("usbCallback", System.nanoTime() - callbackStartedNs)
        }
        if (published) PerformanceMetrics.timingUs("publishTotal", System.nanoTime() - startedNs)
    }

    private fun readOutputBlock(profile: UsbTunerStudioProfile): ByteArray {
        if (streamReadMode == "full_block" && profile.outputBlockSize in 1..FULL_BLOCK_REQUEST_LIMIT) {
            try {
                return readOutputRange(0, profile.outputBlockSize)
            } catch (error: Exception) {
                streamReadMode = "chunked_1024"
                fullBlockFallbacks++
                callback.onUsbLog("Full-block output read failed; falling back to proven 1024-byte chunks: ${error.message}")
            }
        }

        val target = ByteArray(profile.outputBlockSize)
        var offset = 0
        while (offset < target.size) {
            val count = min(STREAM_CHUNK_SIZE, target.size - offset)
            val data = readOutputRange(offset, count)
            data.copyInto(target, offset)
            offset += count
        }
        return target
    }

    private fun readOutputRange(offset: Int, count: Int): ByteArray {
        val payload = outputPayload(offset, count)
        requests.incrementAndGet()
        strictWriteFrame(envelope(payload), "stream O offset=$offset count=$count", logSuccess = false)
        val response = readEnvelope(count + 8)
        return extractOutputData(response, count)
    }

    private fun decodeProbeResponse(raw: ByteArray, requestedFramed: Boolean): ProbeReadResult {
        if (raw.isEmpty()) return ProbeReadResult(raw, ByteArray(0), "", "none")

        val framedPayload = decodeEnvelopeIfComplete(raw)
        if (framedPayload != null) {
            val payload = stripStatusByte(framedPayload)
            return ProbeReadResult(raw, payload, sanitizeSignature(payload), "framed")
        }

        val plainPayload = stripStatusByte(raw)
        val signature = sanitizeSignature(plainPayload)
        return ProbeReadResult(
            rawBytes = raw,
            payloadBytes = plainPayload,
            signature = signature,
            responseMode = if (requestedFramed) "plain-fallback" else "plain"
        )
    }

    private fun decodeEnvelopeIfComplete(raw: ByteArray): ByteArray? {
        if (raw.size < 7) return null
        val length = ((raw[0].toInt() and 0xff) shl 8) or (raw[1].toInt() and 0xff)
        if (length !in 1..4096) return null
        val packetSize = length + 6
        if (raw.size < packetSize) return null
        val body = raw.copyOfRange(2, 2 + length)
        val expected = ByteBuffer.wrap(raw, 2 + length, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
        val actual = CRC32().apply { update(body) }.value
        if (actual != expected) {
            crcErrors.incrementAndGet()
            callback.onUsbLog(
                "USB probe framed CRC mismatch expected=${String.format(Locale.US, "%08X", expected)} " +
                    "actual=${String.format(Locale.US, "%08X", actual)}"
            )
            return null
        }
        if (raw.size > packetSize) {
            callback.onUsbLog("USB probe framed response has ${raw.size - packetSize} trailing byte(s): ${hex(raw.copyOfRange(packetSize, raw.size))}")
        }
        return body
    }

    private fun stripStatusByte(bytes: ByteArray): ByteArray =
        if (bytes.size > 1 && (bytes[0].toInt() and 0xff) < 32) bytes.copyOfRange(1, bytes.size) else bytes

    private fun sanitizeSignature(bytes: ByteArray): String = bytes
        .dropWhile { it == 0.toByte() }
        .takeWhile { it != 0.toByte() && it.toInt() != 10 && it.toInt() != 13 }
        .toByteArray()
        .toString(Charsets.ISO_8859_1)
        .trim()

    private fun isLikelySignature(value: String): Boolean {
        if (value.length !in 7..240) return false
        val printable = value.count { it.code in 32..126 }
        return printable >= value.length * 0.9 &&
            (value.contains("rusEFI", true) || value.contains("EpicEFI", true) || value.contains("MEGA144", true))
    }

    private fun envelope(payload: ByteArray): ByteArray {
        val crc = CRC32().apply { update(payload) }.value
        return ByteBuffer.allocate(payload.size + 6)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(payload.size.toShort())
            .put(payload)
            .putInt(crc.toInt())
            .array()
    }

    /**
     * Read one complete msEnvelope response using USB receive buffers that are never smaller
     * than the endpoint max-packet size. The v0.11.3 report showed that 512-byte probe reads
     * worked while a two-byte bulk-IN request for the same endpoint repeatedly returned nothing.
     *
     * v0.11.3 proved the ECU response path with 512-byte probe reads, but ordinary polling used
     * readExactly(2) for the envelope header and timed out immediately. This reader accumulates
     * the entire response while parsing the two-byte length from the buffered data.
     */
    private fun readEnvelope(maxBody: Int): ByteArray =
        if (performanceProfile.optimizedUsbBuffers) readEnvelopeOptimized(maxBody) else readEnvelopeLegacy(maxBody)

    private fun readEnvelopeOptimized(maxBody: Int): ByteArray {
        val conn = connection ?: throw IllegalStateException("USB connection closed")
        val endpoint = endpointIn ?: throw IllegalStateException("USB IN endpoint missing")
        val requiredCapacity = (maxBody + 6).coerceAtLeast(64)
        if (optimizedPacketBuffer.size < requiredCapacity) {
            optimizedPacketBuffer = ByteArray(Integer.highestOneBit(requiredCapacity - 1).shl(1))
        }
        var total = 0
        var bodyLength = -1
        var expectedPacketSize = -1
        var readCount = 0
        val deadline = SystemClock.elapsedRealtime() + ENVELOPE_READ_TIMEOUT_MS

        while (SystemClock.elapsedRealtime() < deadline) {
            if (expectedPacketSize > 0 && total >= expectedPacketSize) break
            val got = conn.bulkTransfer(endpoint, optimizedReceiveBuffer, optimizedReceiveBuffer.size, BULK_READ_SLICE_MS)
            if (got <= 0) continue
            if (total + got > optimizedPacketBuffer.size) {
                protocolErrors.incrementAndGet()
                throw IllegalStateException("USB response exceeds ${optimizedPacketBuffer.size} byte buffer")
            }
            optimizedReceiveBuffer.copyInto(optimizedPacketBuffer, total, 0, got)
            total += got
            readCount++
            bytesRx.addAndGet(got.toLong())
            if (bodyLength < 0 && total >= 2) {
                bodyLength = ((optimizedPacketBuffer[0].toInt() and 0xff) shl 8) or
                    (optimizedPacketBuffer[1].toInt() and 0xff)
                if (bodyLength !in 1..maxBody.coerceAtLeast(64)) {
                    protocolErrors.incrementAndGet()
                    throw IllegalStateException("Invalid USB response length $bodyLength")
                }
                expectedPacketSize = bodyLength + 6
            }
        }

        if (bodyLength < 0) throw IllegalStateException("USB envelope header timeout $total/2 bytes")
        if (total < expectedPacketSize) throw IllegalStateException("USB envelope timeout $total/$expectedPacketSize bytes")
        val crcOffset = 2 + bodyLength
        val expected = ((optimizedPacketBuffer[crcOffset].toLong() and 0xff) shl 24) or
            ((optimizedPacketBuffer[crcOffset + 1].toLong() and 0xff) shl 16) or
            ((optimizedPacketBuffer[crcOffset + 2].toLong() and 0xff) shl 8) or
            (optimizedPacketBuffer[crcOffset + 3].toLong() and 0xff)
        val actual = CRC32().apply { update(optimizedPacketBuffer, 2, bodyLength) }.value
        if (actual != expected) {
            crcErrors.incrementAndGet()
            throw IllegalStateException("USB CRC mismatch")
        }
        if (!streamEnvelopeReaderConfirmed) {
            streamEnvelopeReaderConfirmed = true
            callback.onUsbLog(
                "Optimized USB RX envelope confirmed: body=$bodyLength, total=$expectedPacketSize, " +
                    "reads=$readCount, buffer=${optimizedReceiveBuffer.size}"
            )
        }
        PerformanceMetrics.sample("usbReadsPerEnvelope", readCount.toLong())
        PerformanceMetrics.sample("usbEnvelopeBytes", expectedPacketSize.toLong())
        return optimizedPacketBuffer.copyOfRange(2, 2 + bodyLength)
    }

    private fun readEnvelopeLegacy(maxBody: Int): ByteArray {
        val conn = connection ?: throw IllegalStateException("USB connection closed")
        val endpoint = endpointIn ?: throw IllegalStateException("USB IN endpoint missing")
        val raw = ByteArrayOutputStream()
        val deadline = SystemClock.elapsedRealtime() + ENVELOPE_READ_TIMEOUT_MS
        val chunkSizes = mutableListOf<Int>()
        var expectedPacketSize = -1
        var bodyLength = -1

        while (SystemClock.elapsedRealtime() < deadline) {
            if (expectedPacketSize > 0 && raw.size() >= expectedPacketSize) break

            // Always request at least one full USB max packet. A tiny 2-byte IN transfer was the
            // only meaningful difference between the successful v0.11.3 probes and failed stream.
            val receiveSize = maxOf(USB_RX_BUFFER_BYTES, endpoint.maxPacketSize.coerceAtLeast(1))
            val buffer = ByteArray(receiveSize)
            val got = conn.bulkTransfer(endpoint, buffer, buffer.size, BULK_READ_SLICE_MS)
            if (got <= 0) continue

            raw.write(buffer, 0, got)
            bytesRx.addAndGet(got.toLong())
            chunkSizes += got

            if (bodyLength < 0 && raw.size() >= 2) {
                val bytes = raw.toByteArray()
                bodyLength = ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
                if (bodyLength !in 1..maxBody.coerceAtLeast(64)) {
                    protocolErrors.incrementAndGet()
                    throw IllegalStateException("Invalid USB response length $bodyLength")
                }
                expectedPacketSize = bodyLength + 6
            }
        }

        val packet = raw.toByteArray()
        if (bodyLength < 0) {
            throw IllegalStateException("USB envelope header timeout ${packet.size}/2 bytes")
        }
        if (packet.size < expectedPacketSize) {
            throw IllegalStateException("USB envelope timeout ${packet.size}/$expectedPacketSize bytes")
        }
        if (packet.size > expectedPacketSize) {
            callback.onUsbLog(
                "Buffered USB RX envelope has ${packet.size - expectedPacketSize} trailing byte(s)"
            )
        }

        val body = packet.copyOfRange(2, 2 + bodyLength)
        val expected = ByteBuffer.wrap(packet, 2 + bodyLength, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int.toLong() and 0xffffffffL
        val actual = CRC32().apply { update(body) }.value
        if (actual != expected) {
            crcErrors.incrementAndGet()
            throw IllegalStateException("USB CRC mismatch")
        }

        if (!streamEnvelopeReaderConfirmed) {
            streamEnvelopeReaderConfirmed = true
            callback.onUsbLog(
                "Buffered USB RX envelope confirmed: body=$bodyLength, total=$expectedPacketSize, " +
                    "reads=${chunkSizes.size}, chunks=${chunkSizes.joinToString("+")}, " +
                    "buffer=${maxOf(USB_RX_BUFFER_BYTES, endpoint.maxPacketSize.coerceAtLeast(1))}"
            )
        }
        return body
    }

    private fun writeFully(bytes: ByteArray) {
        val conn = connection ?: throw IllegalStateException("USB connection closed")
        val endpoint = endpointOut ?: throw IllegalStateException("USB OUT endpoint missing")
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.copyOfRange(offset, bytes.size)
            val wrote = conn.bulkTransfer(endpoint, chunk, chunk.size, 1500)
            if (wrote <= 0) throw IllegalStateException("USB write timeout")
            bytesTx.addAndGet(wrote.toLong())
            offset += wrote
        }
    }

    private fun readExactly(count: Int, timeoutMs: Int): ByteArray {
        val conn = connection ?: throw IllegalStateException("USB connection closed")
        val endpoint = endpointIn ?: throw IllegalStateException("USB IN endpoint missing")
        val out = ByteArrayOutputStream(count)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (out.size() < count && SystemClock.elapsedRealtime() < deadline) {
            val wanted = min(4096, count - out.size())
            val buffer = ByteArray(wanted)
            val got = conn.bulkTransfer(endpoint, buffer, buffer.size, 120)
            if (got > 0) {
                out.write(buffer, 0, got)
                bytesRx.addAndGet(got.toLong())
            }
        }
        if (out.size() != count) throw IllegalStateException("USB read timeout ${out.size()}/$count bytes")
        return out.toByteArray()
    }

    private fun readRawUntilQuiet(totalTimeoutMs: Int, quietMs: Int): ByteArray {
        val conn = connection ?: throw IllegalStateException("USB connection closed")
        val endpoint = endpointIn ?: throw IllegalStateException("USB IN endpoint missing")
        val out = ByteArrayOutputStream()
        val deadline = SystemClock.elapsedRealtime() + totalTimeoutMs
        var lastData = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (lastData > 0L && SystemClock.elapsedRealtime() - lastData >= quietMs) break
            val buffer = ByteArray(512)
            val got = conn.bulkTransfer(endpoint, buffer, buffer.size, BULK_READ_SLICE_MS)
            if (got > 0) {
                out.write(buffer, 0, got)
                bytesRx.addAndGet(got.toLong())
                lastData = SystemClock.elapsedRealtime()
            }
        }
        return out.toByteArray()
    }

    private fun parseCdcUnions(raw: ByteArray): List<CdcUnion> {
        val unions = mutableListOf<CdcUnion>()
        val callManagement = mutableListOf<CdcUnion>()
        var offset = 0
        while (offset + 2 <= raw.size) {
            val length = raw[offset].toInt() and 0xff
            val type = raw[offset + 1].toInt() and 0xff
            if (length < 2 || offset + length > raw.size) break
            if (type == USB_DESCRIPTOR_TYPE_CS_INTERFACE && length >= 5) {
                val subtype = raw[offset + 2].toInt() and 0xff
                when (subtype) {
                    CDC_UNION_SUBTYPE -> {
                        val master = raw[offset + 3].toInt() and 0xff
                        val slaves = (offset + 4 until offset + length).map { raw[it].toInt() and 0xff }
                        unions += CdcUnion(master, slaves, "Union descriptor")
                    }
                    CDC_CALL_MANAGEMENT_SUBTYPE -> {
                        val dataInterface = raw[offset + 4].toInt() and 0xff
                        val inferredMaster = findPreviousInterfaceNumber(raw, offset)
                        if (inferredMaster >= 0) {
                            callManagement += CdcUnion(inferredMaster, listOf(dataInterface), "Call Management descriptor")
                        }
                    }
                }
            }
            offset += length
        }
        return if (unions.isNotEmpty()) unions else callManagement
    }

    private fun findPreviousInterfaceNumber(raw: ByteArray, beforeOffset: Int): Int {
        var offset = 0
        var currentInterface = -1
        while (offset + 2 <= raw.size && offset < beforeOffset) {
            val length = raw[offset].toInt() and 0xff
            val type = raw[offset + 1].toInt() and 0xff
            if (length < 2 || offset + length > raw.size) break
            if (type == USB_DESCRIPTOR_TYPE_INTERFACE && length >= 4) {
                currentInterface = raw[offset + 2].toInt() and 0xff
            }
            offset += length
        }
        return currentInterface
    }

    private fun chooseCdcUnion(device: UsbDevice, unions: List<CdcUnion>): CdcUnion? {
        return unions.firstOrNull { union ->
            val master = findInterfaceById(device, union.masterInterfaceId)
            master?.interfaceClass == UsbConstants.USB_CLASS_COMM && union.slaveInterfaceIds.any { slaveId ->
                val slave = findInterfaceById(device, slaveId)
                slave?.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA && findBulkPipe(device, slaveId) != null
            }
        } ?: unions.firstOrNull { union -> union.slaveInterfaceIds.any { findBulkPipe(device, it) != null } }
    }

    private fun findBulkPipe(device: UsbDevice, preferredInterfaceId: Int?): BulkPipe? {
        val candidates = mutableListOf<BulkPipe>()
        for (interfaceIndex in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(interfaceIndex)
            var input: UsbEndpoint? = null
            var output: UsbEndpoint? = null
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (endpoint.direction == UsbConstants.USB_DIR_IN && input == null) input = endpoint
                if (endpoint.direction == UsbConstants.USB_DIR_OUT && output == null) output = endpoint
            }
            if (input != null && output != null) candidates += BulkPipe(usbInterface, input, output)
        }
        return candidates.sortedWith(
            compareByDescending<BulkPipe> { if (it.usbInterface.id == preferredInterfaceId) 1000 else bulkPipeScore(it) }
                .thenBy { it.usbInterface.id }
        ).firstOrNull()
    }

    private fun bulkPipeScore(pipe: BulkPipe): Int = when (pipe.usbInterface.interfaceClass) {
        UsbConstants.USB_CLASS_CDC_DATA -> 300
        UsbConstants.USB_CLASS_VENDOR_SPEC -> 200
        UsbConstants.USB_CLASS_MASS_STORAGE -> 0
        else -> 100
    }

    private fun findControlInterface(device: UsbDevice, preferredInterfaceId: Int?): UsbInterface? {
        if (preferredInterfaceId != null) {
            findInterfaceById(device, preferredInterfaceId)?.let { return it }
        }
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_COMM) return usbInterface
        }
        return null
    }

    private fun findInterfaceById(device: UsbDevice, interfaceId: Int): UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            if (usbInterface.id == interfaceId) return usbInterface
        }
        return null
    }

    private fun logDeviceLayout(device: UsbDevice) {
        callback.onUsbLog(
            "USB device ${describeDevice(device)} class=${device.deviceClass}/${device.deviceSubclass}/${device.deviceProtocol} " +
                "interfaces=${device.interfaceCount}"
        )
        for (index in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(index)
            callback.onUsbLog(
                "USB interface ${usbInterface.id}: class=${usbInterface.interfaceClass}, " +
                    "subclass=${usbInterface.interfaceSubclass}, protocol=${usbInterface.interfaceProtocol}, " +
                    "endpoints=${usbInterface.endpointCount}"
            )
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                val direction = if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                callback.onUsbLog(
                    "USB interface ${usbInterface.id} endpoint ${hexByte(endpoint.address)}: " +
                        "$direction type=${endpoint.type} maxPacket=${endpoint.maxPacketSize} interval=${endpoint.interval}"
                )
            }
        }
    }

    private fun updateSelectedPipe(pipe: BulkPipe) {
        selectedInterfaceId = pipe.usbInterface.id
        selectedInterfaceClass = pipe.usbInterface.interfaceClass
        selectedInterfaceSubclass = pipe.usbInterface.interfaceSubclass
        selectedInterfaceProtocol = pipe.usbInterface.interfaceProtocol
        selectedEndpointIn = pipe.input.address
        selectedEndpointOut = pipe.output.address
        selectedEndpointInMaxPacket = pipe.input.maxPacketSize
        selectedEndpointOutMaxPacket = pipe.output.maxPacketSize
    }

    private fun resetProbeDiagnostics() {
        synchronized(probeLock) { probeAttempts.clear() }
        synchronized(protocolProbeLock) { protocolProbeAttempts.clear() }
        setLineCodingResult = Int.MIN_VALUE
        setControlLineStateResult = Int.MIN_VALUE
        successfulProbeMode = ""
    }

    private fun recordProbeResult(
        spec: ProbeSpec,
        probeIndex: Int,
        setup: ProbeOpenResult,
        transmitted: ByteArray,
        result: ProbeReadResult,
        stage: String
    ) {
        val item = JSONObject()
            .put("index", probeIndex)
            .put("mode", spec.label)
            .put("stage", stage)
            .put("command", spec.commandText)
            .put("framed", spec.framed)
            .put("controlLineState", spec.controlLineState)
            .put("controlLineMode", spec.lineStateText)
            .put("controlClaimed", setup.controlClaimed)
            .put("dataClaimed", setup.dataClaimed)
            .put("setLineCodingResult", setup.lineCodingResult)
            .put("setControlLineStateResult", setup.controlLineResult)
            .put("txHex", hex(transmitted))
            .put("rxHex", hex(result.rawBytes))
            .put("rxBytes", result.rawBytes.size)
            .put("responseMode", result.responseMode)
            .put("signature", result.signature)
            .put("plausibleSignature", isLikelySignature(result.signature))
        synchronized(probeLock) { probeAttempts += item.toString() }
    }

    private fun recordProbeFailure(spec: ProbeSpec, probeIndex: Int, message: String) {
        val item = JSONObject()
            .put("index", probeIndex)
            .put("mode", spec.label)
            .put("command", spec.commandText)
            .put("framed", spec.framed)
            .put("controlLineMode", spec.lineStateText)
            .put("error", message.take(240))
        synchronized(probeLock) { probeAttempts += item.toString() }
    }

    private fun finishStreamingSession() {
        val started = streamingStartedElapsedMs
        if (started > 0L) {
            lastStreamingDurationMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
            streamingStartedElapsedMs = 0L
        }
        if (measuredHz.isFinite() && measuredHz > 0.0) lastActiveMeasuredHz = measuredHz
        measuredHz = 0.0
        rawFrameRate.reset()
    }

    private fun closeConnection(reason: String, cause: String = "internal") {
        val hadConnection = connection != null || loopFuture != null || state == State.STREAMING
        loopFuture?.cancel(true)
        loopFuture = null
        val conn = connection
        if (conn != null) {
            claimedInterfaces.forEach { usbInterface ->
                try {
                    conn.releaseInterface(usbInterface)
                } catch (_: Exception) {
                }
            }
        }
        try {
            conn?.close()
        } catch (_: Exception) {
        }
        connection = null
        claimedInterfaces = emptyList()
        endpointIn = null
        endpointOut = null
        if (state == State.STREAMING) finishStreamingSession()
        if (cause != "internal") {
            lastDisconnectCause = cause
            currentDevice = null
        }
        if (hadConnection && reason.isNotBlank()) callback.onUsbLog(reason)
    }

    private fun failTransport(message: String, retryAllowed: Boolean, generation: Long) {
        generationAuthority.runIfCurrent(generation) {
            lastError = message.take(240)
            consecutiveFailures++
            callback.onUsbLog("USB [$handshakeStage]: $lastError (failure $consecutiveFailures/$MAX_CONSECUTIVE_FAILURES)")
            closeConnection("", "transport_error")
            if (!retryAllowed || consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                active.set(false)
                retryFuture?.cancel(false)
                retryFuture = null
                setState(State.ERROR, "$lastError • tap Reconnect", generation)
            } else {
                setState(State.RETRY_WAIT, "$lastError • retry $consecutiveFailures/$MAX_CONSECUTIVE_FAILURES", generation)
                scheduleRetry(1500, generation)
            }
        }
    }

    private fun scheduleRetry(delayMs: Long, generation: Long) {
        if (!active.get() || !generationAuthority.isCurrent(generation)) return
        retryFuture?.cancel(false)
        retryFuture = executor.schedule({
            retryFuture = null
            if (active.get() && state != State.STREAMING && generationAuthority.isCurrent(generation)) {
                discoverAndConnect(beginConnectionAttempt())
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun beginConnectionAttempt(): Long = advanceGeneration()

    private fun invalidateGeneration(): Long = advanceGeneration()

    private fun advanceGeneration(): Long {
        return permissionRequestCoordinator.advanceGeneration()
    }

    fun currentGeneration(): Long = generationAuthority.current()

    fun transportFreshnessJson(): JSONObject {
        val now = SystemClock.elapsedRealtime()
        return generationAuthority.currentSnapshot { generation ->
            val packetAgeMs = if (lastPacketElapsedMs > 0L) {
                (now - lastPacketElapsedMs).coerceAtLeast(0L)
            } else {
                -1L
            }
            JSONObject()
                .put("usbSessionId", generation)
                .put("streaming", active.get() && state == State.STREAMING)
                .put("packetAgeMs", packetAgeMs)
                .put("activeSessionFrames", rawFrameRate.frameCount)
        }
    }

    private fun setState(value: State, message: String, generation: Long) {
        generationAuthority.runIfCurrent(generation) {
            val previous = state
            if (previous == State.STREAMING && value != State.STREAMING) finishStreamingSession()
            if (previous != State.STREAMING && value == State.STREAMING) {
                streamingStartedElapsedMs = SystemClock.elapsedRealtime()
                measuredHz = 0.0
                rawFrameRate.reset()
                streamReadMode = if ((profile?.outputBlockSize ?: 0) in 1..FULL_BLOCK_REQUEST_LIMIT) "full_block" else "chunked_1024"
            }
            state = value
            stateMessage = message
            callback.onUsbStateChanged(value, message, generation)
        }
    }

    private fun describeDevice(device: UsbDevice): String {
        val product = try {
            device.productName
        } catch (_: Exception) {
            null
        }
        return listOfNotNull(
            product?.takeIf { it.isNotBlank() },
            String.format(Locale.US, "%04X:%04X", device.vendorId, device.productId)
        ).joinToString(" • ")
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { String.format(Locale.US, "%02X", it.toInt() and 0xff) }
    private fun hexPreview(bytes: ByteArray, limit: Int = 128): String {
        if (bytes.isEmpty()) return ""
        val shown = bytes.copyOfRange(0, min(bytes.size, limit))
        return if (shown.size == bytes.size) hex(shown) else "${hex(shown)} … (+${bytes.size - shown.size} bytes)"
    }
    private fun hexByte(value: Int): String = String.format(Locale.US, "0x%02X", value and 0xff)

    fun diagnosticsJson(includeAudit: Boolean = true): JSONObject {
        val now = SystemClock.elapsedRealtime()
        val selectedProfile = profile
        val attempts = JSONArray()
        synchronized(probeLock) {
            probeAttempts.forEach { raw ->
                try {
                    attempts.put(JSONObject(raw))
                } catch (_: Exception) {
                    attempts.put(raw)
                }
            }
        }
        val framedAttempts = JSONArray()
        synchronized(protocolProbeLock) {
            protocolProbeAttempts.forEach { raw ->
                try {
                    framedAttempts.put(JSONObject(raw))
                } catch (_: Exception) {
                    framedAttempts.put(raw)
                }
            }
        }
        val result = JSONObject()
            .put("state", state.name.lowercase(Locale.US))
            .put("message", stateMessage)
            .put("active", active.get())
            .put("device", deviceSummary)
            .put("signature", ecuSignature)
            .put("expectedSignature", selectedProfile?.signature ?: "")
            .put("signatureMatches", signatureMatches ?: JSONObject.NULL)
            .put("profile", selectedProfile?.importedName ?: JSONObject.NULL)
            .put("profileChannels", selectedProfile?.channels?.size ?: 0)
            .put("outputBlockSize", selectedProfile?.outputBlockSize ?: 0)
            .put("pollTargetHz", preferredPollHz)
            .put("measuredHz", if (state == State.STREAMING) measuredHz else 0.0)
            .put("streamReadMode", streamReadMode)
            .put("fullBlockFallbacks", fullBlockFallbacks)
            .put("lastActiveMeasuredHz", lastActiveMeasuredHz)
            .put("streamingDurationMs", if (streamingStartedElapsedMs > 0L) now - streamingStartedElapsedMs else 0L)
            .put("lastStreamingDurationMs", lastStreamingDurationMs)
            .put("lastDisconnectCause", lastDisconnectCause)
            .put("lastPacketAgeMs", if (lastPacketElapsedMs > 0) now - lastPacketElapsedMs else -1)
            .put("frames", frames.get())
            .put("activeSessionFrames", rawFrameRate.frameCount)
            .put("requests", requests.get())
            .put("bytesRx", bytesRx.get())
            .put("bytesTx", bytesTx.get())
            .put("crcErrors", crcErrors.get())
            .put("protocolErrors", protocolErrors.get())
            .put("reconnects", reconnects.get())
            .put("longestReadMs", longestReadMs)
            .put("handshakeStage", handshakeStage)
            .put("consecutiveFailures", consecutiveFailures)
            .put("maxConsecutiveFailures", MAX_CONSECUTIVE_FAILURES)
            .put("cdcPairing", JSONObject()
                .put("unionMasterInterface", cdcUnionMasterInterfaceId)
                .put("unionSlaveInterface", cdcUnionSlaveInterfaceId)
                .put("controlInterface", selectedControlInterfaceId))
            .put("interface", JSONObject()
                .put("id", selectedInterfaceId)
                .put("class", selectedInterfaceClass)
                .put("subclass", selectedInterfaceSubclass)
                .put("protocol", selectedInterfaceProtocol))
            .put("endpoints", JSONObject()
                .put("inAddress", selectedEndpointIn)
                .put("outAddress", selectedEndpointOut)
                .put("inMaxPacket", selectedEndpointInMaxPacket)
                .put("outMaxPacket", selectedEndpointOutMaxPacket))
            .put("cdcSetup", JSONObject()
                .put("setLineCodingResult", if (setLineCodingResult == Int.MIN_VALUE) JSONObject.NULL else setLineCodingResult)
                .put("setControlLineStateResult", if (setControlLineStateResult == Int.MIN_VALUE) JSONObject.NULL else setControlLineStateResult))
            .put("successfulProbeMode", successfulProbeMode)
            .put("probeAttempts", attempts)
            .put("framedProtocolAttempts", framedAttempts)
            .put("performanceProfile", performanceProfile.toJson())
            .put("decodePlanChannels", decodePlan.size)
            .put("requestedChannelCount", requestedChannelNames.size)
            .put("performance", PerformanceMetrics.snapshotJson())
            .put("lastError", lastError)
        if (includeAudit) {
            result.put("channelAudit", channelAuditJson(now))
                .put("codecSelfTest", codecSelfTest())
                .put("profileParserSelfTest", UsbTunerStudioProfileParser.selfTest())
        }
        return result
    }

    private fun channelAuditJson(now: Long): JSONArray {
        val selectedProfile = profile ?: return JSONArray()
        val block = latestOutputBlock ?: return JSONArray()
        val byLower = selectedProfile.channels.associateBy { it.name.lowercase(Locale.US) }
        val specs = linkedMapOf(
            "rpm" to listOf("RPMValue"),
            "map" to listOf("MAPValue"),
            "baro" to listOf("baroPressure"),
            "tps" to listOf("TPSValue"),
            "clt" to listOf("coolant"),
            "iat" to listOf("intake"),
            "afr" to listOf("afrGasolineScale"),
            "afrTarget" to listOf("targetAFR", "afrTSCustom", "afrTarget"),
            "batt" to listOf("VBatt", "VBattAvg", "rawBattery"),
            "fuelPressure" to listOf("lowFuelPressure", "fuelPressure"),
            "oilPressure" to listOf("oilPressure"),
            "ign" to listOf("ignitionAdvance", "ignitionAdvanceCyl1"),
            "boostTarget" to listOf("boostControlTarget"),
            "boostDuty" to listOf("boostOutput"),
            "idleTarget" to listOf("idleTarget"),
            "vehicleSpeed" to listOf("vehicleSpeedKph"),
            "gear" to listOf("detectedGear")
        )
        val plausibleRanges = mapOf(
            "batt" to 4.0..24.0,
            "afr" to 5.0..30.0,
            "afrTarget" to 5.0..30.0,
            "idleTarget" to 0.0..5000.0
        )
        val result = JSONArray()
        specs.forEach { (canonical, candidates) ->
            val resolved = candidates.mapNotNull { byLower[it.lowercase(Locale.US)] }.distinctBy { it.name }
            val range = plausibleRanges[canonical]
            val selected = if (range != null) {
                resolved.firstOrNull { channel -> channel.decode(block)?.let { it.isFinite() && it in range } == true }
            } else {
                resolved.firstOrNull { channel -> channel.decode(block)?.isFinite() == true }
            }
            if (resolved.isEmpty()) {
                result.put(JSONObject()
                    .put("canonical", canonical)
                    .put("available", false)
                    .put("requestedCandidates", JSONArray(candidates)))
            } else {
                resolved.forEach { channel ->
                    val rawBytes = channel.rawBytes(block)
                    val rawNumeric = channel.rawNumeric(block)
                    val decoded = channel.decode(block)
                    result.put(JSONObject()
                        .put("canonical", canonical)
                        .put("available", decoded != null)
                        .put("selected", selected?.name == channel.name)
                        .put("iniChannel", channel.name)
                        .put("offset", channel.offset)
                        .put("dataType", channel.dataType)
                        .put("byteSize", channel.byteSize)
                        .put("signed", channel.dataType.uppercase(Locale.US).startsWith("S"))
                        .put("endianness", selectedProfile.endianness)
                        .put("scale", channel.scale)
                        .put("translate", channel.translate)
                        .put("unit", channel.unit)
                        .put("rawHex", rawBytes?.let { hex(it) } ?: JSONObject.NULL)
                        .put("rawNumeric", rawNumeric ?: JSONObject.NULL)
                        .put("decodedValue", decoded ?: JSONObject.NULL)
                        .put("formula", if (channel.kind == "bits") "bit field" else "raw * scale + translate")
                        .put("blockAgeMs", if (latestOutputBlockElapsedMs > 0L) now - latestOutputBlockElapsedMs else -1L))
                }
            }
        }
        return result
    }

    fun channelCatalogJson(): JSONArray {
        val array = JSONArray()
        profile?.channels?.forEach { channel ->
            array.put(
                JSONObject()
                    .put("key", channel.name)
                    .put("unit", channel.unit)
                    .put("type", channel.dataType)
                    .put("offset", channel.offset)
            )
        }
        return array
    }

    companion object {
        private const val EXTRA_PERMISSION_GENERATION = "usb_permission_generation"
        private val CANONICAL_SPECS = listOf(
            CanonicalSpec("rpm", listOf("RPMValue")),
            CanonicalSpec("map", listOf("MAPValue")),
            CanonicalSpec("baro", listOf("baroPressure")),
            CanonicalSpec("afr", listOf("afrGasolineScale"), 5f, 30f),
            CanonicalSpec("afrTarget", listOf("targetAFR", "afrTSCustom", "afrTarget"), 5f, 30f),
            CanonicalSpec("tps", listOf("TPSValue")),
            CanonicalSpec("clt", listOf("coolant")),
            CanonicalSpec("iat", listOf("intake")),
            CanonicalSpec("fuelPressure", listOf("lowFuelPressure", "fuelPressure")),
            CanonicalSpec("batt", listOf("VBatt", "VBattAvg", "rawBattery"), 4f, 24f),
            CanonicalSpec("boostTarget", listOf("boostControlTarget")),
            CanonicalSpec("boostDuty", listOf("boostOutput")),
            CanonicalSpec("ign", listOf("ignitionAdvance", "ignitionAdvanceCyl1")),
            CanonicalSpec("idleTarget", listOf("idleTarget"), 0f, 5000f),
            CanonicalSpec("oilPressure", listOf("oilPressure")),
            CanonicalSpec("vehicleSpeed", listOf("vehicleSpeedKph")),
            CanonicalSpec("gear", listOf("detectedGear"))
        )

        private const val MAX_CONSECUTIVE_FAILURES = 3
        private const val PORT_OPEN_SETTLE_MS = 900L
        private const val STARTUP_READ_MS = 300
        private const val FULL_BLOCK_REQUEST_LIMIT = 4000
        private const val STREAM_CHUNK_SIZE = 1024
        private const val PROBE_READ_TIMEOUT_MS = 2500
        private const val QUIET_WINDOW_MS = 220
        private const val BULK_READ_SLICE_MS = 100
        private const val USB_RX_BUFFER_BYTES = 512
        private const val ENVELOPE_READ_TIMEOUT_MS = 2500L
        private const val CONTROL_TRANSFER_TIMEOUT_MS = 700
        private const val STRICT_WRITE_TIMEOUT_MS = 1500
        private const val PROTOCOL_PREFLIGHT_TIMEOUT_MS = 2500
        private const val CONTROL_DTR = 0x01
        private const val CONTROL_DTR_RTS = 0x03
        private const val USB_REQUEST_TYPE_CLASS_INTERFACE_OUT = 0x21
        private const val CDC_SET_LINE_CODING = 0x20
        private const val CDC_SET_CONTROL_LINE_STATE = 0x22
        private const val USB_DESCRIPTOR_TYPE_INTERFACE = 0x04
        private const val USB_DESCRIPTOR_TYPE_CS_INTERFACE = 0x24
        private const val CDC_CALL_MANAGEMENT_SUBTYPE = 0x01
        private const val CDC_UNION_SUBTYPE = 0x06

        fun codecSelfTest(): JSONObject = try {
            fun makePacket(payload: ByteArray): ByteArray {
                val crc = CRC32().apply { update(payload) }.value
                return ByteBuffer.allocate(payload.size + 6)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putShort(payload.size.toShort())
                    .put(payload)
                    .putInt(crc.toInt())
                    .array()
            }
            fun packetHex(bytes: ByteArray): String = bytes.joinToString(" ") {
                String.format(Locale.US, "%02X", it.toInt() and 0xff)
            }
            val outputPayload = byteArrayOf('O'.code.toByte(), 0, 0, 4, 0)
            val outputPacket = makePacket(outputPayload)
            val syncPacket = makePacket(byteArrayOf('S'.code.toByte()))
            val decodedLength = ByteBuffer.wrap(outputPacket, 0, 2)
                .order(ByteOrder.BIG_ENDIAN)
                .short
                .toInt() and 0xffff
            val syncVector = packetHex(syncPacket)
            val outputVector = packetHex(outputPacket)
            val passed = decodedLength == outputPayload.size &&
                outputPacket.size == 11 &&
                syncVector == "00 01 53 20 60 EF C3" &&
                outputVector == "00 05 4F 00 00 04 00 78 ED FD 81"
            JSONObject()
                .put("passed", passed)
                .put("payloadBytes", outputPayload.size)
                .put("packetBytes", outputPacket.size)
                .put("framedSVector", syncVector)
                .put("output4Vector", outputVector)
        } catch (error: Exception) {
            JSONObject().put("passed", false).put("error", error.message)
        }
    }
}
