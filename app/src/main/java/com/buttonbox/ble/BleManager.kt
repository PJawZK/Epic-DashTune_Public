package com.buttonbox.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.min

/**
 * BLE Manager for connecting to the ESP32 dashboard bridge.
 *
 * v0.5 reconnect and diagnostics behaviour:
 * - one guarded scan session at a time
 * - 10 second scan windows
 * - 5/10/20/30 second retry backoff
 * - low-latency scanning initially, balanced scanning after repeated misses
 * - explicit connection timeout and cleanup of stale GATT instances
 * - reconnect reset after a fully usable service connection
 */
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"

        // Must match ESP32 firmware UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHAR_BUTTON_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val CHAR_VAR_DATA_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        val CHAR_VAR_REQUEST_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
        val CHAR_GPS_DATA_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26ab")

        // GPS variable hashes for CAN transmission
        const val VAR_HASH_GPS_HMSD_PACKED = 703958849
        const val VAR_HASH_GPS_MYQSAT_PACKED = -1519914092
        const val VAR_HASH_GPS_ACCURACY = -1489698215
        const val VAR_HASH_GPS_ALTITUDE = -2100224086
        const val VAR_HASH_GPS_COURSE = 1842893663
        const val VAR_HASH_GPS_LATITUDE = 1524934922
        const val VAR_HASH_GPS_LONGITUDE = -809214087
        const val VAR_HASH_GPS_SPEED = -1486968225

        // Virtual ADC variable hashes (A0-A15)
        val VAR_HASH_ADC = intArrayOf(
            595545759,   // A0
            595545760,   // A1
            595545761,   // A2
            595545762,   // A3
            595545763,   // A4
            595545764,   // A5
            595545765,   // A6
            595545766,   // A7
            595545767,   // A8
            595545768,   // A9
            -1821826352, // A10
            -1821826351, // A11
            -1821826350, // A12
            -1821826349, // A13
            -1821826348, // A14
            -1821826347  // A15
        )

        private const val DEVICE_NAME = "ESP32 Dashboard"
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val FAST_SCAN_WINDOWS = 2
        private val RECONNECT_DELAYS_MS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L)
    }

    enum class ConnectionPhase {
        OFFLINE,
        SCANNING,
        CONNECTING,
        RETRY_WAIT,
        ONLINE
    }

    interface BleCallback {
        fun onConnectionStateChanged(connected: Boolean)
        fun onLog(message: String)
        fun onScanResult(device: BluetoothDevice)
        fun onVariableData(varHash: Int, value: Float)

        /** Optional richer state callback retained alongside the original boolean callback. */
        fun onConnectionPhaseChanged(
            phase: ConnectionPhase,
            retryDelayMs: Long = 0L,
            retryAttempt: Int = 0
        ) = Unit
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var activeScanner: BluetoothLeScanner? = null
    private var buttonCharacteristic: BluetoothGattCharacteristic? = null
    private var varDataCharacteristic: BluetoothGattCharacteristic? = null
    private var varRequestCharacteristic: BluetoothGattCharacteristic? = null
    private var gpsDataCharacteristic: BluetoothGattCharacteristic? = null
    private var callback: BleCallback? = null

    private var isScanning = false
    private var isConnecting = false
    private var manualDisconnect = false
    private var autoReconnectEnabled = true
    private var connectionReported = false
    private var reconnectAttempt = 0
    private var scanSessionId = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null
    private var connectTimeoutRunnable: Runnable? = null

    private val reconnectRunnable = Runnable {
        if (!manualDisconnect && autoReconnectEnabled && !isConnected && !isScanning && !isConnecting) {
            log("Reconnect attempt $reconnectAttempt")
            startScanInternal()
        }
    }

    // Observational wrappers preserve the three independent legacy FIFO queues.
    private var queueDiagnosticsOwnerId = ""
    private val queueTrace = BleWriteQueueTrace { SystemClock.elapsedRealtime() }
    private val buttonWriteQueue = InstrumentedBleWriteQueue(
        BleWriteQueueKind.BUTTON,
        queueTrace,
        ::publishQueueDiagnostics
    )
    private val varRequestQueue = InstrumentedBleWriteQueue(
        BleWriteQueueKind.VARIABLE_REQUEST,
        queueTrace,
        ::publishQueueDiagnostics
    )
    private val gpsDataQueue = InstrumentedBleWriteQueue(
        BleWriteQueueKind.GPS_DATA,
        queueTrace,
        ::publishQueueDiagnostics
    )
    private var isWritingButton = false
    private var isWritingVarRequest = false
    private var isWritingGpsData = false
    private var queueCounterPublishScheduled = false
    private val queueCounterPublishRunnable = Runnable {
        queueCounterPublishScheduled = false
        publishQueueCountersNow()
    }

    init {
        ensureQueueDiagnosticsOwner()
    }

    val isConnected: Boolean
        get() = connectionReported &&
            bluetoothGatt != null &&
            buttonCharacteristic != null &&
            varDataCharacteristic != null &&
            varRequestCharacteristic != null

    val isAutomaticReconnectEnabled: Boolean
        get() = autoReconnectEnabled

    fun setCallback(callback: BleCallback) {
        this.callback = callback
    }

    fun initialize(): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothAdapter != null
    }

    /** Starts a user-requested connection cycle and resets the retry backoff. */
    fun startScan() {
        manualDisconnect = false
        reconnectAttempt = 0
        handler.removeCallbacks(reconnectRunnable)
        startScanInternal()
    }

    /** Closes any active link and starts a fresh user-requested connection cycle. */
    fun reconnectNow() {
        manualDisconnect = false
        handler.removeCallbacks(reconnectRunnable)
        cancelScanTimeout()
        cancelConnectTimeout()
        stopScanInternal("manual reconnect")
        val hadGatt = bluetoothGatt != null || isConnecting || isConnected
        closeCurrentGatt(notify = true)
        if (hadGatt) DashboardDataHub.noteDisconnected(null, "Manual reconnect")
        reconnectAttempt = 0
        log("Manual reconnect requested")
        startScanInternal()
    }

    /** Pauses or resumes scheduled reconnects without dropping a healthy connection. */
    fun setAutomaticReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        DashboardDataHub.setAutomaticReconnectState(enabled)
        if (!enabled) {
            handler.removeCallbacks(reconnectRunnable)
            if (!isConnected && !isConnecting && !isScanning) notifyPhase(ConnectionPhase.OFFLINE)
            log("Automatic reconnect paused")
        } else {
            manualDisconnect = false
            log("Automatic reconnect enabled")
            if (!manualDisconnect && !isConnected && !isConnecting && !isScanning) {
                reconnectAttempt = 0
                startScanInternal()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanInternal() {
        ensureQueueDiagnosticsOwner()
        if (manualDisconnect || isConnected || isConnecting) return
        if (isScanning) {
            log("Scan request ignored: a scan is already active")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth is disabled or unavailable")
            notifyPhase(ConnectionPhase.OFFLINE)
            scheduleReconnect("Bluetooth unavailable")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            log("BLE scanner not available")
            scheduleReconnect("Scanner unavailable")
            return
        }

        val thisSession = ++scanSessionId
        DashboardDataHub.noteScanStarted()
        activeScanner = scanner
        isScanning = true

        val scanMode = if (reconnectAttempt < FAST_SCAN_WINDOWS) {
            ScanSettings.SCAN_MODE_LOW_LATENCY
        } else {
            ScanSettings.SCAN_MODE_BALANCED
        }
        val modeName = if (scanMode == ScanSettings.SCAN_MODE_LOW_LATENCY) "low latency" else "balanced"
        log("Starting BLE scan ($modeName, window ${reconnectAttempt + 1})")
        notifyPhase(ConnectionPhase.SCANNING, retryAttempt = reconnectAttempt)

        cancelScanTimeout()
        scanTimeoutRunnable = Runnable {
            if (isScanning && scanSessionId == thisSession) {
                stopScanInternal("timeout")
                scheduleReconnect("No dashboard found")
            }
        }.also { handler.postDelayed(it, SCAN_TIMEOUT_MS) }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (error: SecurityException) {
            isScanning = false
            activeScanner = null
            cancelScanTimeout()
            log("BLE scan permission error: ${error.message}")
            notifyPhase(ConnectionPhase.OFFLINE)
        } catch (error: IllegalStateException) {
            isScanning = false
            activeScanner = null
            cancelScanTimeout()
            log("Unable to start BLE scan: ${error.message}")
            scheduleReconnect("Scan start failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanInternal("requested")
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal(reason: String): Boolean {
        cancelScanTimeout()
        if (!isScanning) return false

        // Mark stopped before calling into the vendor Bluetooth stack so a late callback
        // cannot schedule a second stop for the same session.
        isScanning = false
        scanSessionId++
        val scanner = activeScanner
        activeScanner = null

        try {
            scanner?.stopScan(scanCallback)
        } catch (error: SecurityException) {
            log("BLE stop-scan permission error: ${error.message}")
        } catch (error: IllegalStateException) {
            log("BLE scanner already stopped: ${error.message}")
        }
        log("Scan stopped ($reason)")
        return true
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isScanning) return

            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: return
            if (name == DEVICE_NAME || name.contains("ESP32", ignoreCase = true)) {
                log("Found device: $name (${device.address}), RSSI ${result.rssi} dBm")
                DashboardDataHub.noteScanSuccess(name, device.address, result.rssi)
                stopScanInternal("device found")
                callback?.onScanResult(device)
                connect(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            if (!isScanning) return
            cancelScanTimeout()
            isScanning = false
            activeScanner = null
            scanSessionId++
            log("Scan failed: $errorCode")
            DashboardDataHub.noteScanFailure()
            scheduleReconnect("Scan error $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (manualDisconnect || isConnected || isConnecting) return

        handler.removeCallbacks(reconnectRunnable)
        stopScanInternal("connecting")
        closeCurrentGatt(notify = false)

        log("Connecting to ${device.name ?: device.address}...")
        DashboardDataHub.noteConnecting(device.name, device.address)
        isConnecting = true
        notifyPhase(ConnectionPhase.CONNECTING, retryAttempt = reconnectAttempt)

        val gatt = try {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (error: SecurityException) {
            isConnecting = false
            log("BLE connect permission error: ${error.message}")
            notifyPhase(ConnectionPhase.OFFLINE)
            return
        } catch (error: IllegalArgumentException) {
            isConnecting = false
            log("Unable to connect: ${error.message}")
            scheduleReconnect("Connection start failed")
            return
        }

        bluetoothGatt = gatt
        cancelConnectTimeout()
        connectTimeoutRunnable = Runnable {
            if (isConnecting && bluetoothGatt === gatt && !connectionReported) {
                log("GATT connection timed out")
                failConnection(gatt, "Connection timeout")
            }
        }.also { handler.postDelayed(it, CONNECT_TIMEOUT_MS) }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val hadGatt = bluetoothGatt != null || isConnecting || isConnected
        manualDisconnect = true
        handler.removeCallbacks(reconnectRunnable)
        cancelScanTimeout()
        cancelConnectTimeout()
        stopScanInternal("manual disconnect")
        closeCurrentGatt(notify = true)
        reconnectAttempt = 0
        if (hadGatt) DashboardDataHub.noteDisconnected(null, "Manual disconnect")
        notifyPhase(ConnectionPhase.OFFLINE)
        closeQueueDiagnostics("manual disconnect")
    }

    /**
     * Temporarily yields the live transport to USB without changing the user's
     * automatic-reconnect preference. A later startScan() resumes BLE normally.
     */
    @SuppressLint("MissingPermission")
    fun suspendForAlternateTransport(reason: String) {
        val hadGatt = bluetoothGatt != null || isConnecting || isConnected
        manualDisconnect = true
        handler.removeCallbacks(reconnectRunnable)
        cancelScanTimeout()
        cancelConnectTimeout()
        stopScanInternal("alternate transport")
        closeCurrentGatt(notify = true)
        reconnectAttempt = 0
        if (hadGatt) DashboardDataHub.noteDisconnected(null, "BLE paused for alternate transport")
        notifyPhase(ConnectionPhase.OFFLINE)
        log("BLE transport paused: $reason")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (bluetoothGatt !== gatt) {
                // Callback from a timed-out or superseded connection.
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
                return
            }

            log("Connection state: newState=$newState status=$status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnecting = false
                    cancelConnectTimeout()
                    connectionReported = false
                    log("Connected to GATT server")
                    handler.postDelayed({
                        if (bluetoothGatt === gatt && !manualDisconnect) {
                            val started = gatt.discoverServices()
                            if (!started) failConnection(gatt, "Service discovery did not start")
                        }
                    }, 100L)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasReported = connectionReported
                    log("Disconnected from GATT server (status=$status)")
                    DashboardDataHub.noteDisconnected(status, "GATT disconnected")
                    clearCharacteristicsAndQueues()
                    connectionReported = false
                    isConnecting = false
                    cancelConnectTimeout()
                    bluetoothGatt = null
                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }
                    if (wasReported) handler.post { callback?.onConnectionStateChanged(false) }
                    if (!manualDisconnect) scheduleReconnect("GATT disconnected")
                    else notifyPhase(ConnectionPhase.OFFLINE)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (bluetoothGatt !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection(gatt, "Service discovery failed: $status")
                return
            }

            log("Services discovered")
            DashboardDataHub.noteServiceDiscovered()
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                failConnection(gatt, "Dashboard service not found")
                return
            }

            buttonCharacteristic = service.getCharacteristic(CHAR_BUTTON_UUID)
            varDataCharacteristic = service.getCharacteristic(CHAR_VAR_DATA_UUID)
            varRequestCharacteristic = service.getCharacteristic(CHAR_VAR_REQUEST_UUID)
            gpsDataCharacteristic = service.getCharacteristic(CHAR_GPS_DATA_UUID)

            log("Button char: ${if (buttonCharacteristic != null) "OK" else "MISSING"}")
            log("VarData char: ${if (varDataCharacteristic != null) "OK" else "MISSING"}")
            log("VarRequest char: ${if (varRequestCharacteristic != null) "OK" else "MISSING"}")
            log("GPS char: ${if (gpsDataCharacteristic != null) "OK" else "MISSING"}")

            if (buttonCharacteristic == null || varDataCharacteristic == null || varRequestCharacteristic == null) {
                failConnection(gatt, "Required BLE characteristic missing")
                return
            }

            // Enable notifications for variable data.
            varDataCharacteristic?.let { characteristic ->
                val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
                if (!notificationEnabled) {
                    failConnection(gatt, "Unable to enable variable notifications")
                    return
                }

                val descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                }
                log("Enabled variable data notifications")
                DashboardDataHub.noteNotificationsEnabled()
            }

            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            gatt.requestMtu(517)
            // Some devices do not provide an MTU callback. Report readiness after the
            // characteristics and notification path have been configured.
            handler.postDelayed({
                if (bluetoothGatt === gatt) reportConnected()
            }, 250L)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (bluetoothGatt !== gatt) return
            log("MTU changed to $mtu (status: $status)")
            if (status == BluetoothGatt.GATT_SUCCESS) DashboardDataHub.noteMtu(mtu)
            reportConnected()
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (bluetoothGatt !== gatt || characteristic.uuid != CHAR_VAR_DATA_UUID) return
            val data = characteristic.value ?: return
            parseVariablePacket(data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (bluetoothGatt !== gatt || characteristic.uuid != CHAR_VAR_DATA_UUID) return
            parseVariablePacket(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (bluetoothGatt !== gatt) return
            when (characteristic.uuid) {
                CHAR_BUTTON_UUID -> {
                    noteQueueCompletion(BleWriteQueueKind.BUTTON, status)
                    isWritingButton = false
                    processButtonQueue()
                }

                CHAR_VAR_REQUEST_UUID -> {
                    noteQueueCompletion(BleWriteQueueKind.VARIABLE_REQUEST, status)
                    isWritingVarRequest = false
                    processVarRequestQueue()
                }

                CHAR_GPS_DATA_UUID -> {
                    noteQueueCompletion(BleWriteQueueKind.GPS_DATA, status)
                    isWritingGpsData = false
                    processGpsDataQueue()
                }
            }
        }
    }

    private fun parseVariablePacket(data: ByteArray) {
        // Batched response: multiple 8-byte entries [hash(4) + value(4)].
        val entries = data.size / 8
        val malformedBytes = data.size % 8
        DashboardDataHub.notePacketBatch(data.size, entries, malformedBytes)
        if (malformedBytes > 0) log("Variable packet contained $malformedBytes trailing byte(s)")
        var offset = 0
        while (offset + 8 <= data.size) {
            val hash = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val value = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.BIG_ENDIAN).float
            handler.post { callback?.onVariableData(hash, value) }
            offset += 8
        }
    }

    @SuppressLint("MissingPermission")
    private fun processButtonQueue() {
        if (isWritingButton || buttonWriteQueue.isEmpty()) return

        val data = buttonWriteQueue.poll() ?: return
        val characteristic = buttonCharacteristic
            ?: return noteQueuePreconditionDrop(BleWriteQueueKind.BUTTON, "characteristic")
        val gatt = bluetoothGatt
            ?: return noteQueuePreconditionDrop(BleWriteQueueKind.BUTTON, "gatt")

        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted = gatt.writeCharacteristic(characteristic)
        isWritingButton = accepted
        noteQueueSynchronousResult(BleWriteQueueKind.BUTTON, accepted)
    }

    @SuppressLint("MissingPermission")
    private fun processVarRequestQueue() {
        if (isWritingVarRequest || varRequestQueue.isEmpty()) return

        val data = varRequestQueue.poll() ?: return
        val characteristic = varRequestCharacteristic
            ?: return noteQueuePreconditionDrop(BleWriteQueueKind.VARIABLE_REQUEST, "characteristic")
        val gatt = bluetoothGatt
            ?: return noteQueuePreconditionDrop(BleWriteQueueKind.VARIABLE_REQUEST, "gatt")

        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted = gatt.writeCharacteristic(characteristic)
        isWritingVarRequest = accepted
        noteQueueSynchronousResult(BleWriteQueueKind.VARIABLE_REQUEST, accepted)
    }

    /** Send button mask with queuing for rapid presses. */
    fun sendButtonMask(buttonMask: Int): Boolean {
        if (!isConnected) return false

        val lowByte = (buttonMask and 0xFF).toByte()
        val highByte = ((buttonMask shr 8) and 0xFF).toByte()
        buttonWriteQueue.offer(byteArrayOf(lowByte, highByte))

        if (!isWritingButton) processButtonQueue()
        return true
    }

    /** Request a variable value from ECU, queued for rapid requests. */
    fun requestVariable(varHash: Int): Boolean {
        if (!isConnected) return false

        val data = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(varHash).array()
        varRequestQueue.offer(data)

        if (!isWritingVarRequest) processVarRequestQueue()
        return true
    }

    /** Request multiple variables in one BLE write. */
    fun requestVariablesBatch(varHashes: List<Int>): Boolean {
        if (!isConnected || varHashes.isEmpty()) return false

        val data = ByteBuffer.allocate(varHashes.size * 4).order(ByteOrder.BIG_ENDIAN)
        for (hash in varHashes) data.putInt(hash)
        varRequestQueue.offer(data.array())

        if (!isWritingVarRequest) processVarRequestQueue()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun processGpsDataQueue() {
        if (isWritingGpsData || gpsDataQueue.isEmpty()) return

        val data = gpsDataQueue.poll() ?: return
        val characteristic = gpsDataCharacteristic
            ?: return noteQueuePreconditionDrop(BleWriteQueueKind.GPS_DATA, "characteristic")
        val gatt = bluetoothGatt
            ?: return noteQueuePreconditionDrop(BleWriteQueueKind.GPS_DATA, "gatt")

        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted = gatt.writeCharacteristic(characteristic)
        isWritingGpsData = accepted
        noteQueueSynchronousResult(BleWriteQueueKind.GPS_DATA, accepted)
    }

    /** Send GPS data to ESP32 for CAN transmission. */
    fun sendGpsData(varHash: Int, value: Float): Boolean {
        if (!isConnected || gpsDataCharacteristic == null) return false

        val data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(varHash)
            .putFloat(value)
            .array()
        gpsDataQueue.offer(data)

        if (!isWritingGpsData) processGpsDataQueue()
        return true
    }

    /** Send packed GPS data (uint32) to ESP32 for CAN transmission. */
    fun sendGpsDataPacked(varHash: Int, packedValue: Int): Boolean {
        if (!isConnected || gpsDataCharacteristic == null) return false

        val data = LegacyGpsPayloadMapper.encodePackedEntry(varHash, packedValue)
        gpsDataQueue.offer(data)

        if (!isWritingGpsData) processGpsDataQueue()
        return true
    }

    /** Send multiple GPS data entries in one BLE write. */
    fun sendGpsDataBatch(entries: List<Pair<Int, Float>>): Boolean {
        if (!isConnected) {
            log("GPS batch: not connected")
            return false
        }
        if (gpsDataCharacteristic == null) {
            log("GPS batch: no characteristic")
            return false
        }
        if (entries.isEmpty()) return false

        gpsDataQueue.offer(LegacyGpsPayloadMapper.encodeFloatEntries(entries))

        if (!isWritingGpsData) processGpsDataQueue()
        return true
    }

    /** Send a single ADC value to ECU. */
    fun sendAdcValue(channel: Int, value: Float): Boolean {
        if (channel !in VAR_HASH_ADC.indices) return false
        return sendGpsData(VAR_HASH_ADC[channel], value)
    }

    /** Send all 16 ADC values to ECU. */
    fun sendAllAdcValues(values: FloatArray): Boolean {
        if (!isConnected || gpsDataCharacteristic == null || values.size != 16) return false
        val entries = values.mapIndexed { index, value -> VAR_HASH_ADC[index] to value }
        return sendGpsDataBatch(entries)
    }

    private fun reportConnected() {
        if (connectionReported ||
            buttonCharacteristic == null ||
            varDataCharacteristic == null ||
            varRequestCharacteristic == null
        ) return

        connectionReported = true
        isConnecting = false
        reconnectAttempt = 0
        cancelConnectTimeout()
        handler.removeCallbacks(reconnectRunnable)
        DashboardDataHub.noteConnected()
        notifyPhase(ConnectionPhase.ONLINE)
        handler.post { callback?.onConnectionStateChanged(true) }
    }

    @SuppressLint("MissingPermission")
    private fun failConnection(gatt: BluetoothGatt, reason: String) {
        if (bluetoothGatt !== gatt) return
        log(reason)
        cancelConnectTimeout()
        isConnecting = false
        connectionReported = false
        clearCharacteristicsAndQueues()
        bluetoothGatt = null
        try {
            gatt.disconnect()
        } catch (_: Exception) {
        }
        try {
            gatt.close()
        } catch (_: Exception) {
        }
        DashboardDataHub.noteDisconnected(null, reason)
        handler.post { callback?.onConnectionStateChanged(false) }
        scheduleReconnect(reason)
    }

    @SuppressLint("MissingPermission")
    private fun closeCurrentGatt(notify: Boolean) {
        val gatt = bluetoothGatt
        val hadConnection = connectionReported
        bluetoothGatt = null
        isConnecting = false
        connectionReported = false
        clearCharacteristicsAndQueues()

        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }
            try {
                gatt.close()
            } catch (_: Exception) {
            }
        }
        if (notify && (hadConnection || gatt != null)) {
            handler.post { callback?.onConnectionStateChanged(false) }
        }
    }

    private fun clearCharacteristicsAndQueues() {
        buttonCharacteristic = null
        varDataCharacteristic = null
        varRequestCharacteristic = null
        gpsDataCharacteristic = null
        buttonWriteQueue.clear()
        varRequestQueue.clear()
        gpsDataQueue.clear()
        isWritingButton = false
        isWritingVarRequest = false
        isWritingGpsData = false
    }

    private fun ensureQueueDiagnosticsOwner() {
        if (queueDiagnosticsOwnerId.isNotBlank()) return
        queueDiagnosticsOwnerId = LifecycleDiagnostics.registerManager(
            "BLE-Queues-${context.javaClass.simpleName.take(20)}"
        )
        LifecycleDiagnostics.mark(queueDiagnosticsOwnerId, "constructed")
        publishQueueDiagnostics(null)
    }

    private fun publishQueueDiagnostics(signal: BleWriteQueueSignal?) {
        ensureQueueDiagnosticsOwner()
        signal?.let {
            LifecycleDiagnostics.mark(
                queueDiagnosticsOwnerId,
                "${it.kind.key}-${it.event}",
                it.details
            )
        }

        val counters = queueTrace.counterValues()
        if (counters.isNotEmpty()) {
            if (queueCounterPublishScheduled) {
                handler.removeCallbacks(queueCounterPublishRunnable)
                queueCounterPublishScheduled = false
            }
            publishQueueCounters(counters)
        } else if (!queueCounterPublishScheduled) {
            queueCounterPublishScheduled = true
            handler.postDelayed(
                queueCounterPublishRunnable,
                BleWriteQueueTrace.COUNTER_PUBLICATION_INTERVAL_MS
            )
        }
    }

    private fun publishQueueCountersNow() {
        val counters = queueTrace.counterValues()
        if (counters.isNotEmpty()) publishQueueCounters(counters)
    }

    private fun publishQueueCounters(counters: Map<String, Long>) {
        val ownerId = queueDiagnosticsOwnerId
        if (ownerId.isBlank()) return
        counters.forEach { (counter, value) ->
            LifecycleDiagnostics.setCounter(ownerId, counter, value)
        }
    }

    private fun noteQueuePreconditionDrop(kind: BleWriteQueueKind, reason: String) {
        publishQueueDiagnostics(queueTrace.onPreconditionDrop(kind, reason))
    }

    private fun noteQueueSynchronousResult(kind: BleWriteQueueKind, accepted: Boolean) {
        publishQueueDiagnostics(queueTrace.onSynchronousResult(kind, accepted))
    }

    private fun noteQueueCompletion(kind: BleWriteQueueKind, status: Int) {
        publishQueueDiagnostics(
            queueTrace.onCompletion(kind, status, BluetoothGatt.GATT_SUCCESS)
        )
    }

    private fun closeQueueDiagnostics(reason: String) {
        val ownerId = queueDiagnosticsOwnerId
        if (ownerId.isBlank()) return
        if (queueCounterPublishScheduled) {
            handler.removeCallbacks(queueCounterPublishRunnable)
            queueCounterPublishScheduled = false
        }
        publishQueueCountersNow()
        LifecycleDiagnostics.close(ownerId, "shutdown", reason)
        queueDiagnosticsOwnerId = ""
    }

    private fun scheduleReconnect(reason: String) {
        if (manualDisconnect || !autoReconnectEnabled || isConnected) {
            if (!isConnected) notifyPhase(ConnectionPhase.OFFLINE)
            return
        }
        handler.removeCallbacks(reconnectRunnable)

        val delayIndex = min(reconnectAttempt, RECONNECT_DELAYS_MS.lastIndex)
        val delay = RECONNECT_DELAYS_MS[delayIndex]
        reconnectAttempt++
        DashboardDataHub.noteReconnectScheduled()

        log("$reason; retrying in ${delay / 1000}s (attempt $reconnectAttempt)")
        notifyPhase(ConnectionPhase.RETRY_WAIT, delay, reconnectAttempt)
        handler.postDelayed(reconnectRunnable, delay)
    }

    private fun cancelScanTimeout() {
        scanTimeoutRunnable?.let(handler::removeCallbacks)
        scanTimeoutRunnable = null
    }

    private fun cancelConnectTimeout() {
        connectTimeoutRunnable?.let(handler::removeCallbacks)
        connectTimeoutRunnable = null
    }

    private fun notifyPhase(
        phase: ConnectionPhase,
        retryDelayMs: Long = 0L,
        retryAttempt: Int = 0
    ) {
        handler.post {
            callback?.onConnectionPhaseChanged(phase, retryDelayMs, retryAttempt)
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        handler.post { callback?.onLog(message) }
    }
}
