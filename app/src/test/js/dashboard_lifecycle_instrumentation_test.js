'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../../../..');
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8');
const includesAll = (source, values, label) => {
  for (const value of values) {
    assert.ok(source.includes(value), `${label} missing: ${value}`);
  }
};
const between = (source, startMarker, endMarker) => {
  const start = source.indexOf(startMarker);
  assert.ok(start >= 0, `Missing start marker: ${startMarker}`);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(end > start, `Missing end marker after ${startMarker}: ${endMarker}`);
  return source.slice(start, end);
};

const manifest = read('app/src/main/AndroidManifest.xml');
const lifecycle = read('app/src/main/java/com/buttonbox/ble/LifecycleDiagnostics.kt');
const main = read('app/src/main/java/com/buttonbox/ble/MainActivity.kt');
const lab = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const location = read('app/src/main/java/com/buttonbox/ble/LocationDataHub.kt');
const report = read('app/src/main/java/com/buttonbox/ble/DiagnosticReportFormatter.kt');
const reportTest = read('app/src/test/java/com/buttonbox/ble/DiagnosticReportFormatterTest.kt');
const registryTest = read('app/src/test/java/com/buttonbox/ble/LifecycleDiagnosticsTest.kt');

assert.ok(!manifest.includes('<service'), 'instrumentation must not introduce a Service');
assert.ok(!manifest.includes('android:process='), 'instrumentation must not introduce a process split');

includesAll(lifecycle, [
  'internal class LifecycleTrace(',
  'private val maxOwners: Int = 32',
  'private val maxEvents: Int = 160',
  'owner.counters["lateEvents"]',
  'owner.counters["repeatedStateEvents"]',
  '.put("schema", 1)',
  '.put("processGeneration", processGeneration)',
  '.put("owners", ownerArray)',
  '.put("events", eventArray)',
  'internal object LifecycleDiagnostics',
  'fun registerActivity(label: String)',
  'fun registerManager(label: String, parentId: String = "")',
  'fun snapshotJson(): JSONObject'
], 'Lifecycle diagnostics registry');

includesAll(main, [
  'LifecycleDiagnostics.registerActivity("MainActivity")',
  'LifecycleDiagnostics.registerManager("BLE-Main", activityOwnerId)',
  'LifecycleDiagnostics.mark(activityOwnerId, "onCreate"',
  'LifecycleDiagnostics.mark(activityOwnerId, "onStart")',
  'LifecycleDiagnostics.mark(activityOwnerId, "onResume")',
  'LifecycleDiagnostics.mark(activityOwnerId, "onPause")',
  'LifecycleDiagnostics.mark(activityOwnerId, "onStop")',
  'LifecycleDiagnostics.close(activityOwnerId, "destroyed")',
  'LifecycleDiagnostics.increment(bleOwnerId, "variableRequestBatches")',
  'LifecycleDiagnostics.increment(bleOwnerId, "variableCallbacks")',
  'LifecycleDiagnostics.increment(activityOwnerId, "gpsCallbacks")'
], 'Main lifecycle instrumentation');

includesAll(lab, [
  'LifecycleDiagnostics.registerActivity("DashboardLabActivity")',
  'LifecycleDiagnostics.registerManager("WebView-LAB", activityOwnerId)',
  'LifecycleDiagnostics.registerManager("MSL-LAB", activityOwnerId)',
  'LifecycleDiagnostics.registerManager("BLE-LAB", activityOwnerId)',
  'LifecycleDiagnostics.registerManager("USB-LAB", activityOwnerId)',
  'LifecycleDiagnostics.mark(activityOwnerId, "onCreate"',
  'LifecycleDiagnostics.mark(activityOwnerId, "onStart")',
  'LifecycleDiagnostics.mark(activityOwnerId, "onResume")',
  'LifecycleDiagnostics.mark(activityOwnerId, "onPause")',
  'LifecycleDiagnostics.mark(activityOwnerId, "onStop")',
  'LifecycleDiagnostics.increment(webViewOwnerId, "deliveries")',
  'LifecycleDiagnostics.increment(usbOwnerId, "frames")',
  'LifecycleDiagnostics.increment(mslOwnerId, "samples")',
  '.put("lifecycle", LifecycleDiagnostics.snapshotJson())',
  'lifecycleJson = lifecycle.toString(2)'
], 'LAB lifecycle instrumentation');

includesAll(location, [
  'LifecycleDiagnostics.registerManager("LocationDataHub")',
  'LifecycleDiagnostics.increment(lifecycleOwnerId, "callbacks")',
  'LifecycleDiagnostics.setCounter(lifecycleOwnerId, "listeners"',
  'LifecycleDiagnostics.setCounter(lifecycleOwnerId, "consumers"',
  'LifecycleDiagnostics.mark(lifecycleOwnerId, "active")',
  'LifecycleDiagnostics.mark(lifecycleOwnerId, "paused")'
], 'GPS lifecycle instrumentation');

includesAll(report, [
  'val lifecycleJson: String',
  'report.appendLine("LIFECYCLE / OWNER TRACE")',
  'report.appendLine(input.lifecycleJson)'
], 'Diagnostic report lifecycle section');
assert.ok(reportTest.includes('processGeneration'), 'report tests lock lifecycle report output');
assert.ok(registryTest.includes('event and owner retention remain bounded'), 'registry retention is JVM-covered');
assert.ok(registryTest.includes('repeated transport states coalesce and late callbacks preserve shutdown'), 'field-evidence correction is JVM-covered');

// Instrumentation must remain observational: source-characterized teardown stays in onDestroy.
const mainStop = between(main, 'override fun onStop()', 'override fun onWindowFocusChanged');
assert.ok(!mainStop.includes('bleManager.disconnect()'), 'Main onStop must not gain BLE teardown');
const labStop = between(lab, 'override fun onStop()', 'override fun onWindowFocusChanged');
assert.ok(!labStop.includes('bleManager.disconnect()'), 'LAB onStop must not gain BLE teardown');
assert.ok(!labStop.includes('usbEcuManager.shutdown()'), 'LAB onStop must not gain USB teardown');
const labDestroy = between(lab, 'override fun onDestroy()', 'private fun hasAllPermissions()');
assert.ok(labDestroy.includes('if (::bleManager.isInitialized) bleManager.disconnect()'));
assert.ok(labDestroy.includes('if (::usbEcuManager.isInitialized) usbEcuManager.shutdown()'));

console.log('Lifecycle instrumentation source contract passed');
