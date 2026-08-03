'use strict';

const assert = require('assert');
const fs = require('fs');

const read = path => fs.readFileSync(path, 'utf8');
const gradle = read('app/build.gradle.kts');
const location = read('app/src/main/java/com/buttonbox/ble/LocationDataHub.kt');
const ble = read('app/src/main/java/com/buttonbox/ble/BleManager.kt');
const main = read('app/src/main/java/com/buttonbox/ble/MainActivity.kt');
const lab = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');

for (const contract of [
  'applicationId = "com.buttonbox.ble.jz"',
  'versionCode = 1114',
  'versionName = "0.11.12-stale1-jz"'
]) assert.ok(gradle.includes(contract), `missing identity contract: ${contract}`);

for (const contract of [
  'const val REQUEST_INTERVAL_MS = 100L',
  'const val MIN_UPDATE_INTERVAL_MS = 50L',
  'const val MAX_UPDATE_DELAY_MS = 100L',
  'const val REQUEST_PRIORITY = Priority.PRIORITY_HIGH_ACCURACY',
  'fused.requestLocationUpdates(request, callback, Looper.getMainLooper())'
]) assert.ok(location.includes(contract), `missing location contract: ${contract}`);

const startIndex = location.indexOf('private fun start(context: Context)');
const stopIndex = location.indexOf('private fun stopNow()');
assert.ok(startIndex >= 0 && stopIndex > startIndex, 'missing location start/stop boundaries');
assert.ok(location.includes('private fun resetCallbackIntervalDiagnostics()'));
assert.ok(location.includes('lastCallbackElapsedMs = 0L'));
assert.ok(location.includes('"lastCallbackIntervalMs", 0L'));
assert.ok(location.slice(startIndex, stopIndex).includes('resetCallbackIntervalDiagnostics()'), 'GPS start must reset callback interval diagnostics');
assert.ok(location.slice(stopIndex).includes('resetCallbackIntervalDiagnostics()'), 'GPS stop must reset callback interval diagnostics');
assert.ok(!location.includes('MainActivity remains a listener'), 'stale hidden-Main ownership comment returned');

for (const uuid of [
  '4fafc201-1fb5-459e-8fcc-c5c9c331914b',
  'beb5483e-36e1-4688-b7f5-ea07361b26a8',
  'beb5483e-36e1-4688-b7f5-ea07361b26a9',
  'beb5483e-36e1-4688-b7f5-ea07361b26aa',
  'beb5483e-36e1-4688-b7f5-ea07361b26ab'
]) assert.ok(ble.includes(uuid), `missing BLE UUID contract: ${uuid}`);

for (const activity of [main, lab]) {
  assert.ok(activity.includes('LegacyGpsPayloadMapper.map('));
  assert.ok(activity.includes('speedMetresPerSecond = location.speed'));
  assert.ok(activity.includes('bleManager.sendGpsDataBatch(plan.floatEntries)'));

  const forwardingStart = activity.indexOf('private fun sendGpsDataToCan(location: Location)');
  assert.ok(forwardingStart >= 0, 'missing legacy GPS forwarding integration');
  const forwarding = activity.slice(forwardingStart, forwardingStart + 5000);
  const hmsdState = forwarding.indexOf('legacyGpsState = legacyGpsState.copy(hmsdPacked = it)');
  const hmsdSend = forwarding.indexOf('sendGpsDataPacked(BleManager.VAR_HASH_GPS_HMSD_PACKED, it)');
  const myqsatState = forwarding.indexOf('legacyGpsState = legacyGpsState.copy(myqsatPacked = it)');
  const myqsatSend = forwarding.indexOf('sendGpsDataPacked(BleManager.VAR_HASH_GPS_MYQSAT_PACKED, it)');
  const floatState = forwarding.indexOf('speedMetresPerSecond = plan.nextState.speedMetresPerSecond');
  const floatSend = forwarding.indexOf('bleManager.sendGpsDataBatch(plan.floatEntries)');
  assert.ok(hmsdState >= 0 && hmsdState < hmsdSend, 'HMSD state must commit immediately before legacy send');
  assert.ok(myqsatState > hmsdSend && myqsatState < myqsatSend, 'MYQSAT state must commit after HMSD send');
  assert.ok(floatState > myqsatSend && floatState < floatSend, 'float suppression state must commit after packed sends');
  assert.ok(!forwarding.includes('legacyGpsState = plan.nextState'), 'all-at-once state commit changes exception behavior');
}

for (const forbidden of ['writeTune', 'burnTune', 'calibrationWrite', 'firmwareFlash']) {
  assert.ok(![location, ble, main, lab].some(source => source.includes(forbidden)), `forbidden write path added: ${forbidden}`);
}

console.log('GPS data-truth source characterization contract passed');
