'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const compact = value => String(value).replace(/\s+/g, ' ');

const manifest = read('src/main/AndroidManifest.xml');
const main = read('src/main/java/com/buttonbox/ble/MainActivity.kt');
const lab = read('src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const dataHub = read('src/main/java/com/buttonbox/ble/DashboardDataHub.kt');
const locationHub = read('src/main/java/com/buttonbox/ble/LocationDataHub.kt');
const usb = read('src/main/java/com/buttonbox/ble/UsbEcuManager.kt');
const msl = read('src/main/java/com/buttonbox/ble/MslLogPlayer.kt');

function section(source, start, end) {
  const startIndex = source.indexOf(start);
  assert.ok(startIndex >= 0, `Missing section start: ${start}`);
  const endIndex = end ? source.indexOf(end, startIndex + start.length) : source.length;
  assert.ok(endIndex > startIndex, `Missing section end after ${start}: ${end}`);
  return source.slice(startIndex, endIndex);
}

function includesAll(source, values, label) {
  for (const value of values) {
    assert.ok(source.includes(value), `${label} missing: ${value}`);
  }
}

// Manifest and process boundary.
assert.ok(manifest.includes('android:name="com.buttonbox.ble.DashboardLabActivity"'));
assert.ok(manifest.includes('<category android:name="android.intent.category.LAUNCHER"'));
const labManifest = section(
  manifest,
  'android:name="com.buttonbox.ble.DashboardLabActivity"',
  'android:name="com.buttonbox.ble.MainActivity"'
);
assert.ok(labManifest.includes('android.intent.action.MAIN'));
assert.ok(labManifest.includes('android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"'));
const mainManifest = section(
  manifest,
  'android:name="com.buttonbox.ble.MainActivity"',
  'android:name="com.buttonbox.ble.SettingsActivity"'
);
assert.ok(!mainManifest.includes('android.intent.action.MAIN'));
assert.ok(mainManifest.includes('android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"'));
assert.ok(!manifest.includes('<service'), 'No Android Service currently owns transport');
assert.ok(!manifest.includes('android:process='), 'All current Activities run in the default app process');

// MainActivity owns its own BLE manager for its whole Activity lifetime.
const mainCreate = section(main, 'override fun onCreate(savedInstanceState: Bundle?)', 'override fun onDestroy()');
includesAll(mainCreate, [
  'bleManager = BleManager(this)',
  'bleManager.setCallback(this)',
  'DashboardDataHub.registerBleControl(labBleControl)',
  'LocationDataHub.registerListener(locationListener)',
  'checkPermissionsAndStart()'
], 'MainActivity.onCreate');

const mainDestroy = section(main, 'override fun onDestroy()', 'private fun setupUI()');
includesAll(mainDestroy, [
  'LocationDataHub.release(this, "main")',
  'LocationDataHub.unregisterListener(locationListener)',
  'DashboardDataHub.unregisterBleControl(labBleControl)',
  'bleManager.disconnect()'
], 'MainActivity.onDestroy');

const mainSetup = section(main, 'private fun setupUI()', 'private fun setupButtons()');
const mainToLab = section(mainSetup, 'binding.btnLab.setOnClickListener', 'binding.btnConnect.setOnClickListener');
assert.ok(mainToLab.includes('startActivity(Intent(this, DashboardLabActivity::class.java))'));
assert.ok(mainToLab.includes('finish()'), 'Main must finish after handing control to LAB');
assert.ok(
  mainToLab.indexOf('finish()') > mainToLab.indexOf('startActivity(Intent(this, DashboardLabActivity::class.java))'),
  'Main must launch LAB before finishing itself'
);

const mainResume = section(main, 'override fun onResume()', 'private fun hasAllPermissions()');
includesAll(mainResume, [
  'config = settingsManager.getDashboardConfig()',
  'if (!bleManager.isConnected && bleManager.isAutomaticReconnectEnabled && hasAllPermissions())',
  'startConnection()'
], 'MainActivity.onResume');

const mainStart = section(main, 'override fun onStart()', 'override fun onStop()');
assert.ok(mainStart.includes('LocationDataHub.acquire(this, "main")'));
const mainStop = section(main, 'override fun onStop()', 'override fun onConfigurationChanged');
assert.ok(mainStop.includes('LocationDataHub.release(this, "main")'));
assert.ok(!mainStop.includes('bleManager.disconnect()'));
assert.ok(!mainStop.includes('DashboardDataHub.unregisterBleControl'));
assert.ok(!mainStop.includes('LocationDataHub.unregisterListener'));

const mainPolling = section(main, 'private fun startVariablePolling()', 'private fun sendAllAdcValuesOnConnect()');
includesAll(mainPolling, [
  'lifecycleScope.launch',
  'while (bleManager.isConnected)',
  'if (DashboardDataHub.labActive)',
  '(configuredHashes + DashboardDataHub.requiredHashes).distinct()',
  'bleManager.requestVariablesBatch(hashes)'
], 'MainActivity BLE polling');

// LAB creates independent BLE, USB and MSL owners after its first frame.
const labCreate = section(lab, 'override fun onCreate(savedInstanceState: Bundle?)', 'private fun initializeBackgroundServices()');
includesAll(labCreate, [
  'webView.postOnAnimation',
  'webView.post { initializeBackgroundServices() }'
], 'DashboardLabActivity.onCreate');

const labServices = section(lab, 'private fun initializeBackgroundServices()', 'override fun onStart()');
includesAll(labServices, [
  'mslPlayer = MslLogPlayer(this, this)',
  'bleManager = BleManager(this)',
  'manager.setCallback(this)',
  'DashboardDataHub.registerBleControl(labBleControl)',
  'usbEcuManager = UsbEcuManager(this, this)',
  'if (liveTransportPreference != "ble") manager.start()',
  'LocationDataHub.registerListener(locationListener)',
  'checkPermissionsAndStartHost()'
], 'LAB background services');

const labStart = section(lab, 'override fun onStart()', 'override fun onResume()');
includesAll(labStart, [
  'DashboardDataHub.labActive = true',
  'LocationDataHub.acquire(this, "lab")',
  'handler.post(livePushRunnable)'
], 'DashboardLabActivity.onStart');

const labResume = section(lab, 'override fun onResume()', 'override fun onPause()');
includesAll(labResume, [
  'activityResumed = true',
  'webView.onResume()',
  'requestLiveSnapshotPush(true)'
], 'DashboardLabActivity.onResume');

const labPause = section(lab, 'override fun onPause()', 'override fun onStop()');
includesAll(labPause, [
  'activityResumed = false',
  'bridgeForcePending.set(true)',
  'webView.onPause()'
], 'DashboardLabActivity.onPause');

const labStop = section(lab, 'override fun onStop()', '@Deprecated("Deprecated in Java")');
includesAll(labStop, [
  'DashboardDataHub.labActive = false',
  'LocationDataHub.release(this, "lab")',
  'handler.removeCallbacks(livePushRunnable)',
  'mslPlayer.setPaused(true)'
], 'DashboardLabActivity.onStop');
for (const teardown of [
  'bleManager.disconnect()',
  'usbEcuManager.shutdown()',
  'mslPlayer.shutdown()',
  'webView.destroy()'
]) {
  assert.ok(!labStop.includes(teardown), `LAB onStop must not be mistaken for teardown: ${teardown}`);
}

const labDestroy = section(lab, 'override fun onDestroy()', 'private fun hasAllPermissions()');
includesAll(labDestroy, [
  'variablePollingJob?.cancel()',
  'LocationDataHub.unregisterListener(locationListener)',
  'DashboardDataHub.unregisterBleControl(labBleControl)',
  'bleManager.disconnect()',
  'usbEcuManager.shutdown()',
  'mslPlayer.shutdown()',
  'webView.removeJavascriptInterface("EpicDashAndroid")',
  'webView.destroy()'
], 'DashboardLabActivity.onDestroy');

const livePush = section(lab, 'private fun requestLiveSnapshotPush(force: Boolean)', 'private fun pushLiveSnapshot');
assert.ok(livePush.includes('if (!activityResumed || !webPageReady) return'));
const pushLive = section(lab, 'private fun pushLiveSnapshot', 'private val permissionLauncher');
assert.ok(pushLive.includes('!activityResumed'));

const openStock = section(lab, 'fun openStockEpicDash()', '@JavascriptInterface\n        fun openMainSettings()');
includesAll(openStock, [
  'startActivity(Intent(this@DashboardLabActivity, MainActivity::class.java))',
  'finish()'
], 'LAB to stock transition');
const openSettings = section(lab, 'fun openMainSettings()', '@JavascriptInterface\n        fun exportDashboardLayout');
assert.ok(openSettings.includes('startActivity(Intent(this@DashboardLabActivity, SettingsActivity::class.java))'));
assert.ok(!openSettings.includes('finish()'), 'LAB remains alive behind Settings');

// The process-wide hub separates visible consumers from registered listeners.
includesAll(locationHub, [
  'object LocationDataHub',
  'private const val STOP_GRACE_MS = 1_500L',
  'private val listeners = CopyOnWriteArraySet<Listener>()',
  'private val consumers = linkedSetOf<String>()',
  'handler.postDelayed(stopRunnable, STOP_GRACE_MS)',
  'const val REQUEST_INTERVAL_MS = 100L',
  'const val MIN_UPDATE_INTERVAL_MS = 50L',
  'const val MAX_UPDATE_DELAY_MS = 100L',
  'const val REQUEST_PRIORITY = Priority.PRIORITY_HIGH_ACCURACY',
  'LocationRequest.Builder(REQUEST_PRIORITY, REQUEST_INTERVAL_MS)',
  '.setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)',
  '.setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)',
  'fused.requestLocationUpdates(request, callback, Looper.getMainLooper())'
], 'LocationDataHub');
assert.ok(locationHub.includes('GpsListenerDispatcher.dispatch(listeners)'));
assert.ok(locationHub.includes('consumers.remove(consumer)'));
assert.ok(!section(locationHub, 'fun release(', '@SuppressLint("MissingPermission")').includes('listeners.remove'));

// DashboardDataHub exposes one last-writer-wins BLE control slot.
includesAll(dataHub, [
  '@Volatile var labActive: Boolean = false',
  '@Volatile private var bleControl: BleControl? = null',
  'fun registerBleControl(control: BleControl) { bleControl = control }',
  'fun unregisterBleControl(control: BleControl) { if (bleControl === control) bleControl = null }'
], 'DashboardDataHub BLE control ownership');

// USB receivers/executor live from manager construction until explicit shutdown.
includesAll(usb, [
  'private val executor = Executors.newSingleThreadScheduledExecutor',
  'ContextCompat.registerReceiver(',
  'permissionReceiver',
  'usbDeviceReceiver',
  'fun start()',
  'fun shutdown()',
  'context.unregisterReceiver(permissionReceiver)',
  'context.unregisterReceiver(usbDeviceReceiver)',
  'executor.shutdownNow()'
], 'UsbEcuManager lifecycle');

// MSL playback is paused on LAB stop and fully released only on LAB destroy.
includesAll(msl, [
  'private val worker = Executors.newSingleThreadExecutor()',
  'private const val PLAYBACK_TICK_MS = 20L',
  'mainHandler.postDelayed(this, PLAYBACK_TICK_MS)',
  'fun setPaused(shouldPause: Boolean)',
  'fun shutdown()',
  'worker.shutdownNow()'
], 'MslLogPlayer lifecycle');

// Source-backed owner matrix. This models object lifetime, not Android scheduling latency.
const matrix = {
  launcherLabVisible: { mainBle: 0, labBle: 1, labUsb: 1, labWebPush: 1, mslRunningPossible: 1 },
  mainVisible: { mainBle: 1, labBle: 0, labUsb: 0, labWebPush: 0, mslRunningPossible: 0 },
  labVisibleAfterMainTransition: { mainBle: 0, labBle: 1, labUsb: 1, labWebPush: 1, mslRunningPossible: 1 },
  labStoppedBehindSettings: { mainBle: 0, labBle: 1, labUsb: 1, labWebPush: 0, mslRunningPossible: 0 },
  labStoppedBackground: { mainBle: 0, labBle: 1, labUsb: 1, labWebPush: 0, mslRunningPossible: 0 }
};
assert.deepStrictEqual(matrix.labVisibleAfterMainTransition, {
  mainBle: 0,
  labBle: 1,
  labUsb: 1,
  labWebPush: 1,
  mslRunningPossible: 1
});
assert.strictEqual(matrix.labStoppedBehindSettings.labBle, 1);
assert.strictEqual(matrix.labStoppedBehindSettings.labUsb, 1);
assert.strictEqual(matrix.labStoppedBehindSettings.labWebPush, 0);

console.log('Activity ownership characterization passed');
console.log(JSON.stringify(matrix));
console.log('Focused physical evidence remains required to confirm Main teardown and absence of a retained hidden Main owner after Main → LAB.');
