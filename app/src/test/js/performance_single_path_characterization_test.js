'use strict';

const assert = require('assert');
const fs = require('fs');

const read = path => fs.readFileSync(path, 'utf8');
const exists = path => fs.existsSync(path);

const activity = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const hub = read('app/src/main/java/com/buttonbox/ble/DashboardDataHub.kt');
const usb = read('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt');
const metrics = read('app/src/main/java/com/buttonbox/ble/PerformanceMetrics.kt');
const html = read('app/src/main/assets/dashboard_lab.html');
const runtime = read('app/src/main/assets/dashboard_runtime_snapshot.js');

assert.strictEqual(
  exists('app/src/main/java/com/buttonbox/ble/PerformanceProfile.kt'),
  false,
  'selectable PerformanceProfile source must remain retired'
);

for (const [name, source] of [
  ['Activity', activity],
  ['DataHub', hub],
  ['USB manager', usb],
  ['Performance metrics', metrics],
  ['dashboard', html],
  ['runtime snapshot', runtime]
]) {
  for (const forbidden of [
    'PerformanceProfile',
    'setPerformanceProfile',
    'currentPerformanceProfile',
    'performanceProfile',
    'requestedPerformanceProfile',
    'perfTestStrip',
    'data-perf-profile'
  ]) {
    assert.ok(!source.includes(forbidden), `${name} retained selectable performance symbol: ${forbidden}`);
  }
}

assert.ok(
  usb.includes('private fun readEnvelope(maxBody: Int): ByteArray =\n        readEnvelopeOptimized(maxBody)'),
  'USB envelope ownership must be permanently optimized'
);
assert.ok(!usb.includes('readEnvelopeLegacy'), 'legacy USB envelope implementation must remain absent');

assert.ok(
  usb.includes('decodePlan = needed.toList().sortedBy { it.offset }'),
  'USB decode plan must remain selective on the fixed optimized path'
);
assert.ok(
  hub.includes('val stable: Map<String, Float> = incoming'),
  'DataHub must retain the immutable optimized frame map directly'
);

assert.ok(activity.includes('requestLiveSnapshotPush(false)'), 'bridge pushes must use the coalesced latest-state path');
assert.ok(activity.includes('PerformanceMetrics.increment("bridgeFramesCoalesced")'), 'coalescing instrumentation must remain');

for (const marker of [
  '<div class="label">Runtime mode</div><strong id="runtimeMode">OPTIMIZED</strong>',
  "function maintenanceIntervalMs(input){return input.source==='LIVE'&&!input.liveConnectionActive?1000:100;}",
  "const activeOnly=!editMode;",
  "const selector=!editMode?'.page.active .customWidget[data-editor-id]':'.customWidget[data-editor-id]'",
  "for(const key of historySubscriptions)if(channelValid(key,false))values[key]=data[key]"
]) {
  assert.ok(html.includes(marker), `missing fixed optimized WebView marker: ${marker}`);
}

assert.ok(html.includes("function renderTpsDirect()"), 'direct TPS renderer must remain present');
assert.ok(html.includes("const activePageId=document.querySelector('.page.active')?.id||'';"), 'direct TPS rendering must remain active-page owned');
for (const page of ['page-daily','page-drift','page-boost','page-analysis','page-diagnostics']) {
  assert.ok(html.includes("activePageId==='" + page + "'"), 'direct TPS renderer lost active-page ownership for ' + page);
}
assert.ok(!runtime.includes('TpsTrace'), 'retired TPS trace runtime coordinator returned');
assert.ok(!runtime.includes('tpsTrace'), 'retired TPS trace runtime state returned');
assert.ok(!runtime.includes("input.performanceProfile"), 'runtime snapshot must not branch on a profile');

assert.ok(metrics.includes('.put("runtimeMode", "optimized")'), 'neutral metrics must expose fixed runtime mode');
for (const marker of ['timings', 'counters', 'p50', 'p95', 'maximum']) {
  assert.ok(metrics.includes(marker), `performance instrumentation lost metric marker: ${marker}`);
}

console.log('Step 0G single optimized runtime characterization passed');
