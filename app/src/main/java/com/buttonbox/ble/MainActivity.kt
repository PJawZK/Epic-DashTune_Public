package com.buttonbox.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.buttonbox.ble.data.ButtonConfig
import com.buttonbox.ble.data.ButtonMode
import com.buttonbox.ble.data.DashboardConfig
import com.buttonbox.ble.data.DisplayType
import com.buttonbox.ble.data.GaugeConfig
import com.buttonbox.ble.data.GaugePosition
import com.buttonbox.ble.data.SettingsManager
import com.buttonbox.ble.data.SpeedUnit
import com.buttonbox.ble.data.VariableRepository
import com.buttonbox.ble.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity(), BleManager.BleCallback {

    private val activityOwnerId = LifecycleDiagnostics.registerActivity("MainActivity")
    private var bleOwnerId: String = ""

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleManager
    private lateinit var variableRepository: VariableRepository
    private lateinit var settingsManager: SettingsManager

    private val labBleControl = object : DashboardDataHub.BleControl {
        override fun scanNow() = runOnUiThread { bleManager.startScan() }
        override fun reconnectNow() = runOnUiThread { bleManager.reconnectNow() }
        override fun disconnectNow() = runOnUiThread { bleManager.disconnect() }
        override fun setAutomaticReconnect(enabled: Boolean) =
            runOnUiThread { bleManager.setAutomaticReconnectEnabled(enabled) }
    }
    
    private val buttons = mutableListOf<Button>()
    private val buttonConfigs = mutableListOf<ButtonConfig>()
    private val toggleStates = mutableMapOf<Int, Boolean>() // buttonId -> isOn
    private val gaugeViews = ConcurrentHashMap<Int, TextView>() // hash -> TextView
    private var gpsSpeedView: TextView? = null  // Special view for GPS speed
    private var currentButtonMask = 0
    
    // Configuration - loaded from SettingsManager
    private var config = DashboardConfig()
    
    // Variable values cache
    private val variableValues = ConcurrentHashMap<Int, Float>()
    
    // ADC values (0-1023) for virtual analog outputs
    // Note: A1 is also hardware-sampled from GPIO 5 on ESP32, slider allows override
    private val adcValues = FloatArray(16) { 0f }
    private var adcValuesSentOnConnect = false

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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startConnection()
            LocationDataHub.acquire(this, "main")
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
        }
    }

    private val locationListener = object : LocationDataHub.Listener {
        override fun onLocation(location: Location) {
            LifecycleDiagnostics.increment(activityOwnerId, "gpsCallbacks")
            updateSpeed(location)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LifecycleDiagnostics.mark(activityOwnerId, "onCreate", "restored=${savedInstanceState != null}")
        applyDashboardWindowMode()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleOwnerId = LifecycleDiagnostics.registerManager("BLE-Main", activityOwnerId)
        bleManager = BleManager(this)
        bleManager.setCallback(this)
        LifecycleDiagnostics.mark(bleOwnerId, "constructed")
        variableRepository = VariableRepository(this)
        settingsManager = SettingsManager(this)

        if (!bleManager.initialize()) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        DashboardDataHub.registerBleControl(labBleControl)
        LocationDataHub.registerListener(locationListener)
        DashboardDataHub.setAutomaticReconnectState(bleManager.isAutomaticReconnectEnabled)
        if (savedInstanceState == null) {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            DiagnosticStore.startSession(this, "EpicDash JZ ${packageInfo.versionName ?: "unknown"}")
        }
        DiagnosticStore.addEvent(this, "APP", "EpicDash JZ main screen started")

        // Load config from settings
        config = settingsManager.getDashboardConfig()
        
        setupUI()
        checkPermissionsAndStart()
    }
    
    override fun onDestroy() {
        LifecycleDiagnostics.mark(activityOwnerId, "onDestroy")
        LocationDataHub.release(this, "main")
        LocationDataHub.unregisterListener(locationListener)
        DashboardDataHub.unregisterBleControl(labBleControl)
        bleManager.disconnect()
        if (bleOwnerId.isNotBlank()) LifecycleDiagnostics.close(bleOwnerId, "shutdown", "MainActivity.onDestroy")
        LifecycleDiagnostics.close(activityOwnerId, "destroyed")
        super.onDestroy()
    }

    private fun setupUI() {
        setupButtons()
        setupGauges()
        setupAdcSliders()
        
        binding.btnLab.setOnClickListener {
            LifecycleDiagnostics.mark(activityOwnerId, "navigate-lab")
            startActivity(Intent(this, DashboardLabActivity::class.java))
            finish()
        }

        binding.btnConnect.setOnClickListener {
            if (bleManager.isConnected) {
                bleManager.disconnect()
            } else {
                checkPermissionsAndConnect()
            }
        }
        
        binding.btnSettings.setOnClickListener {
            LifecycleDiagnostics.mark(activityOwnerId, "navigate-settings")
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupButtons() {
        val grid = binding.buttonGrid
        grid.removeAllViews()
        buttons.clear()
        buttonConfigs.clear()
        
        val buttonCount = config.buttonCount.coerceIn(1, 16)
        val columns = config.buttonColumns.coerceIn(2, 4)
        val rows = (buttonCount + columns - 1) / columns
        
        grid.columnCount = columns
        grid.rowCount = rows
        
        for (i in 0 until buttonCount) {
            val btnConfig = config.buttons.find { it.id == i } ?: ButtonConfig(id = i)
            buttonConfigs.add(btnConfig)
            
            // Initialize toggle state if not set
            if (!toggleStates.containsKey(i)) {
                toggleStates[i] = false
            }
            
            val isToggleOn = toggleStates[i] == true && btnConfig.mode == ButtonMode.TOGGLE
            val defaultOffColor = ContextCompat.getColor(this, R.color.button_normal)
            val defaultOnColor = ContextCompat.getColor(this, R.color.button_pressed)
            
            val button = MaterialButton(this).apply {
                // Show label or number
                text = if (btnConfig.label.isNotEmpty()) btnConfig.label else "${i + 1}"
                textSize = if (buttonCount > 8) 14f else 18f
                setTextColor(if (isToggleOn) Color.BLACK else Color.WHITE)
                
                // Set initial color based on toggle state
                val bgColor = if (isToggleOn) {
                    btnConfig.colorOn ?: defaultOnColor
                } else {
                    btnConfig.colorOff ?: defaultOffColor
                }
                setBackgroundColor(bgColor)
                
                strokeColor = android.content.res.ColorStateList.valueOf(
                    if (isToggleOn) ContextCompat.getColor(context, R.color.accent_orange)
                    else ContextCompat.getColor(context, R.color.button_border)
                )
                strokeWidth = 2
                cornerRadius = 12
                elevation = 0f
                stateListAnimator = null
                
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(i % columns, 1f)
                    rowSpec = GridLayout.spec(i / columns, 1f)
                    setMargins(6, 6, 6, 6)
                }
                
                post {
                    minimumHeight = width
                    requestLayout()
                }
                
                setOnTouchListener { _, event ->
                    handleButtonTouch(i, event)
                    true
                }
                
                // Long press to edit button
                setOnLongClickListener {
                    showButtonEditDialog(i)
                    true
                }
            }
            
            buttons.add(button)
            grid.addView(button)
        }
    }

    private fun setupGauges() {
        val topContainer = binding.topGaugesContainer
        val secondaryContainer = binding.gaugesContainer
        
        topContainer.removeAllViews()
        secondaryContainer.removeAllViews()
        gaugeViews.clear()
        gpsSpeedView = null
        
        // Split gauges by position
        val topGauges = config.gauges.filter { it.position == GaugePosition.TOP }
        val secondaryGauges = config.gauges.filter { it.position == GaugePosition.SECONDARY }
        
        // Setup top gauges (2 columns, larger)
        if (topGauges.isEmpty()) {
            topContainer.visibility = View.GONE
        } else {
            topContainer.visibility = View.VISIBLE
            topContainer.columnCount = 2
            
            for ((index, gauge) in topGauges.withIndex()) {
                val card = createGaugeCard(gauge, index, 2, isTopRow = true)
                topContainer.addView(card)
            }
        }
        
        // Setup secondary gauges (4 columns, smaller)
        if (secondaryGauges.isEmpty()) {
            secondaryContainer.visibility = View.GONE
        } else {
            secondaryContainer.visibility = View.VISIBLE
            val columns = minOf(4, secondaryGauges.size)
            secondaryContainer.columnCount = columns
            
            for ((index, gauge) in secondaryGauges.withIndex()) {
                val card = createGaugeCard(gauge, index, columns, isTopRow = false)
                secondaryContainer.addView(card)
            }
        }
    }

    private fun createGaugeCard(gauge: GaugeConfig, index: Int, columns: Int, isTopRow: Boolean = false): View {
        val card = MaterialCardView(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % columns, 1f)
                rowSpec = GridLayout.spec(index / columns)
                setMargins(4, 4, 4, 4)
            }
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            strokeColor = ContextCompat.getColor(context, R.color.button_border)
            strokeWidth = 1
            radius = if (isTopRow) 20f else 16f
            elevation = 0f
        }
        
        // Larger padding for top row
        val vertPadding = if (isTopRow) 24 else 12
        val horzPadding = if (isTopRow) 16 else 8
        
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(horzPadding, vertPadding, horzPadding, vertPadding)
        }
        
        // Label at top
        val labelView = TextView(this).apply {
            text = gauge.label.uppercase()
            textSize = if (isTopRow) 11f else 9f
            setTextColor(ContextCompat.getColor(context, R.color.gauge_label))
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }
        
        // Large value in center - top row uses uniform large size
        val baseSize = if (isTopRow) {
            56f  // All top row gauges same size
        } else {
            when (gauge.displayType) {
                DisplayType.GAUGE -> 32f
                DisplayType.NUMBER -> 28f
                DisplayType.BAR -> 24f
                DisplayType.INDICATOR -> 20f
            }
        }
        
        val valueView = TextView(this).apply {
            text = if (gauge.isGpsSpeed) "0" else "--"
            textSize = baseSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            includeFontPadding = false
        }
        
        // Unit below value - always show for top row to maintain consistent height
        val unitView = TextView(this).apply {
            text = gauge.unit.ifEmpty { if (isTopRow) " " else "" }  // Placeholder for top row
            textSize = if (isTopRow) 14f else 10f
            setTextColor(ContextCompat.getColor(context, R.color.accent_orange))
            gravity = Gravity.CENTER
        }
        
        // Store reference for updates
        if (gauge.isGpsSpeed) {
            gpsSpeedView = valueView
        } else {
            gaugeViews[gauge.variableHash] = valueView
        }
        
        content.addView(labelView)
        content.addView(valueView)
        // Always add unit for top row (for consistent height), only if non-empty for secondary
        if (isTopRow || gauge.unit.isNotEmpty()) {
            content.addView(unitView)
        }
        card.addView(content)
        
        return card
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleButtonTouch(buttonIndex: Int, event: MotionEvent) {
        val button = buttons.getOrNull(buttonIndex) as? MaterialButton ?: return
        val btnConfig = buttonConfigs.getOrNull(buttonIndex) ?: return
        
        val defaultOffColor = ContextCompat.getColor(this, R.color.button_normal)
        val defaultOnColor = ContextCompat.getColor(this, R.color.button_pressed)
        val offColor = btnConfig.colorOff ?: defaultOffColor
        val onColor = btnConfig.colorOn ?: defaultOnColor
        
        when (btnConfig.mode) {
            ButtonMode.MOMENTARY -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentButtonMask = currentButtonMask or (1 shl buttonIndex)
                        button.setBackgroundColor(onColor)
                        button.strokeColor = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.accent_orange)
                        )
                        button.setTextColor(Color.BLACK)
                        
                        if (bleManager.isConnected) {
                            bleManager.sendButtonMask(currentButtonMask)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        currentButtonMask = currentButtonMask and (1 shl buttonIndex).inv()
                        button.setBackgroundColor(offColor)
                        button.strokeColor = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.button_border)
                        )
                        button.setTextColor(Color.WHITE)
                        
                        if (bleManager.isConnected) {
                            bleManager.sendButtonMask(currentButtonMask)
                        }
                    }
                }
            }
            ButtonMode.TOGGLE -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    // Toggle visual state on press
                    val wasOn = toggleStates[buttonIndex] == true
                    val isNowOn = !wasOn
                    toggleStates[buttonIndex] = isNowOn
                    
                    // Update button appearance (visual only)
                    button.setBackgroundColor(if (isNowOn) onColor else offColor)
                    button.strokeColor = android.content.res.ColorStateList.valueOf(
                        if (isNowOn) ContextCompat.getColor(this, R.color.accent_orange)
                        else ContextCompat.getColor(this, R.color.button_border)
                    )
                    button.setTextColor(if (isNowOn) Color.BLACK else Color.WHITE)
                    
                    // Send single press (like momentary) - button ON then OFF
                    if (bleManager.isConnected) {
                        val pressedMask = currentButtonMask or (1 shl buttonIndex)
                        bleManager.sendButtonMask(pressedMask)
                    }
                }
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    // Send release - toggle doesn't hold the mask
                    if (bleManager.isConnected) {
                        bleManager.sendButtonMask(currentButtonMask)
                    }
                }
            }
        }
    }

    private fun showButtonEditDialog(buttonIndex: Int) {
        val currentConfig = buttonConfigs.getOrNull(buttonIndex) ?: ButtonConfig(id = buttonIndex)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_button, null)
        val etLabel = dialogView.findViewById<android.widget.EditText>(R.id.etButtonLabel)
        val rgMode = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgButtonMode)
        val rbMomentary = dialogView.findViewById<android.widget.RadioButton>(R.id.rbMomentary)
        val rbToggle = dialogView.findViewById<android.widget.RadioButton>(R.id.rbToggle)
        
        // Set current values
        etLabel.setText(currentConfig.label)
        if (currentConfig.mode == ButtonMode.TOGGLE) {
            rbToggle.isChecked = true
        } else {
            rbMomentary.isChecked = true
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.DarkAlertDialog)
            .setTitle("Edit Button ${buttonIndex + 1}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = etLabel.text.toString().trim()
                val newMode = if (rbToggle.isChecked) ButtonMode.TOGGLE else ButtonMode.MOMENTARY
                
                val newConfig = currentConfig.copy(
                    label = newLabel,
                    mode = newMode
                )
                
                settingsManager.updateButton(buttonIndex, newConfig)
                config = settingsManager.getDashboardConfig()
                
                // Reset toggle state if mode changed
                if (newMode != currentConfig.mode) {
                    toggleStates[buttonIndex] = false
                }
                
                setupButtons()
                Toast.makeText(this, "Button updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissionsAndStart() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isEmpty()) {
            LocationDataHub.acquire(this, "main")
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkPermissionsAndConnect() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isEmpty()) {
            startConnection()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Track last GPS values to only send on change
    private var legacyGpsState = LegacyGpsPayloadMapper.State()

    private fun updateSpeed(location: Location) {
        val speedMs = location.speed
        val speed = when (config.speedUnit) {
            SpeedUnit.MPH -> speedMs * 2.237f
            SpeedUnit.KMH -> speedMs * 3.6f
            SpeedUnit.MS -> speedMs
        }
        
        // Update GPS speed gauge if present
        gpsSpeedView?.text = speed.toInt().toString()
        
        // Send GPS data to ECU via BLE -> ESP32 -> CAN (only if changed)
        if (bleManager.isConnected) {
            sendGpsDataToCan(location)
        }
    }
    
    // ADC slider value TextViews for updating display
    private val adcValueViews = mutableListOf<TextView>()
    
    private fun setupAdcSliders() {
        val container = binding.adcSlidersContainer
        container.removeAllViews()
        adcValueViews.clear()
        
        // All 16 ADC channels (A0-A15)
        // Note: A1 is also hardware-sampled from GPIO 5 on ESP32, but slider allows override
        val adcChannels = (0..15).toList()
        
        for (channel in adcChannels) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            
            val label = TextView(this).apply {
                text = "A$channel"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.adc_label_width),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8 }
            }
            
            val slider = android.widget.SeekBar(this).apply {
                max = 1023
                progress = adcValues[channel].toInt()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                
                val ch = channel
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        adcValues[ch] = progress.toFloat()
                        adcValueViews.getOrNull(adcChannels.indexOf(ch))?.text = progress.toString()
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                        if (bleManager.isConnected) {
                            bleManager.sendAdcValue(ch, adcValues[ch])
                        }
                    }
                })
            }
            
            val valueText = TextView(this).apply {
                text = adcValues[channel].toInt().toString()
                setTextColor(ContextCompat.getColor(context, R.color.accent_orange))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.adc_value_width),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 8 }
                gravity = android.view.Gravity.END
            }
            adcValueViews.add(valueText)
            
            itemLayout.addView(label)
            itemLayout.addView(slider)
            itemLayout.addView(valueText)
            container.addView(itemLayout)
        }
    }
    
    private fun sendGpsDataToCan(location: Location) {
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

    private fun startConnection() {
        LifecycleDiagnostics.mark(bleOwnerId, "scan-requested")
        binding.tvStatus.text = "Searching"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_scanning))
        bleManager.startScan()
    }

    // BleManager.BleCallback
    override fun onConnectionStateChanged(connected: Boolean) {
        LifecycleDiagnostics.mark(bleOwnerId, if (connected) "connected" else "disconnected")
        if (connected) LifecycleDiagnostics.increment(bleOwnerId, "connections")
        DashboardDataHub.setConnected(connected)
        runOnUiThread {
            if (connected) {
                binding.statusIndicator.setBackgroundResource(R.drawable.status_dot_connected)
                // Start requesting variables
                startVariablePolling()
                // Send all ADC values on connect
                sendAllAdcValuesOnConnect()
            } else {
                adcValuesSentOnConnect = false
                binding.statusIndicator.setBackgroundResource(R.drawable.status_dot)
            }
        }
    }

    override fun onConnectionPhaseChanged(
        phase: BleManager.ConnectionPhase,
        retryDelayMs: Long,
        retryAttempt: Int
    ) {
        LifecycleDiagnostics.mark(bleOwnerId, "phase-${phase.name.lowercase()}", "retryDelayMs=$retryDelayMs retryAttempt=$retryAttempt")
        DashboardDataHub.setConnectionPhase(phase, retryDelayMs, retryAttempt)
        runOnUiThread {
            when (phase) {
                BleManager.ConnectionPhase.ONLINE -> {
                    binding.tvStatus.text = "Online"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.status_connected)
                    )
                }

                BleManager.ConnectionPhase.SCANNING -> {
                    binding.tvStatus.text = "Searching"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.status_scanning)
                    )
                }

                BleManager.ConnectionPhase.CONNECTING -> {
                    binding.tvStatus.text = "Connecting"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.accent_blue)
                    )
                }

                BleManager.ConnectionPhase.RETRY_WAIT -> {
                    val seconds = (retryDelayMs / 1000L).coerceAtLeast(1L)
                    binding.tvStatus.text = "Retry ${seconds}s"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.status_scanning)
                    )
                }

                BleManager.ConnectionPhase.OFFLINE -> {
                    binding.tvStatus.text = "Offline"
                    binding.tvStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.text_secondary)
                    )
                }
            }
        }
    }

    private fun startVariablePolling() {
        lifecycleScope.launch {
            while (bleManager.isConnected) {
                val configuredHashes = config.gauges
                    .filter { !it.isGpsSpeed }
                    .map { it.variableHash }

                val hashes = if (DashboardDataHub.labActive) {
                    (configuredHashes + DashboardDataHub.requiredHashes).distinct()
                } else {
                    configuredHashes.distinct()
                }

                if (hashes.isNotEmpty()) {
                    LifecycleDiagnostics.increment(bleOwnerId, "variableRequestBatches")
                    LifecycleDiagnostics.increment(bleOwnerId, "variableHashesRequested", hashes.size.toLong())
                    bleManager.requestVariablesBatch(hashes)
                }

                kotlinx.coroutines.delay(settingsManager.dataDelayMs)
            }
        }
    }
    
    private fun sendAllAdcValuesOnConnect() {
        if (adcValuesSentOnConnect) return
        adcValuesSentOnConnect = true
        
        // Small delay to ensure BLE is ready
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            if (bleManager.isConnected) {
                bleManager.sendAllAdcValues(adcValues)
                log("ADC: Sent all 16 values on connect")
            }
        }
    }

    override fun onVariableData(varHash: Int, value: Float) {
        LifecycleDiagnostics.increment(bleOwnerId, "variableCallbacks")
        variableValues[varHash] = value
        DashboardDataHub.update(varHash, value)
        
        runOnUiThread {
            gaugeViews[varHash]?.let { tv ->
                tv.text = String.format("%.1f", value)
                
                // Update color based on thresholds
                val gauge = config.gauges.find { it.variableHash == varHash }
                gauge?.let { g ->
                    val color = when {
                        g.criticalThreshold != null && value >= g.criticalThreshold -> Color.RED
                        g.warningThreshold != null && value >= g.warningThreshold -> Color.YELLOW
                        else -> Color.WHITE
                    }
                    tv.setTextColor(color)
                }
            }
        }
    }

    override fun onLog(message: String) {
        DiagnosticStore.addEvent(this, "BLE", message)
        runOnUiThread {
            val lines = binding.tvLog.text.toString().split("\n").takeLast(15)
            binding.tvLog.text = (lines + message).joinToString("\n")
            binding.scrollLog.post { binding.scrollLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun log(message: String) {
        onLog(message)
    }

    override fun onScanResult(device: BluetoothDevice) {
        LifecycleDiagnostics.increment(bleOwnerId, "scanResults")
    }

    override fun onResume() {
        super.onResume()
        LifecycleDiagnostics.mark(activityOwnerId, "onResume")
        applyDashboardWindowMode()
        // Reload config in case settings changed
        config = settingsManager.getDashboardConfig()
        setupButtons()
        setupGauges()
        
        // Auto-reconnect if disconnected (e.g., after returning from settings)
        if (!bleManager.isConnected && bleManager.isAutomaticReconnectEnabled && hasAllPermissions()) {
            startConnection()
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }
    }

    override fun onStart() {
        super.onStart()
        LifecycleDiagnostics.mark(activityOwnerId, "onStart")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationDataHub.acquire(this, "main")
        }
    }

    override fun onPause() {
        LifecycleDiagnostics.mark(activityOwnerId, "onPause")
        super.onPause()
    }

    override fun onStop() {
        LifecycleDiagnostics.mark(activityOwnerId, "onStop")
        LocationDataHub.release(this, "main")
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        LifecycleDiagnostics.mark(activityOwnerId, if (hasFocus) "window-focus" else "window-blur")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LifecycleDiagnostics.mark(activityOwnerId, "configuration-changed", "orientation=${newConfig.orientation}")
        // Rebuild UI when screen configuration changes (fold/unfold)
        binding.root.post {
            setupButtons()
            setupGauges()
        }
    }
    private fun applyDashboardWindowMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
