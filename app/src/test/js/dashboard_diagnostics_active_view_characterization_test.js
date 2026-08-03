'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const dashboardPath = process.env.EPICDASH_DASHBOARD_HTML || path.resolve(__dirname, '../../main/assets/dashboard_lab.html');
const activityPath = process.env.EPICDASH_DASHBOARD_ACTIVITY || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const dataHubPath = process.env.EPICDASH_DATA_HUB || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/DashboardDataHub.kt');
const usbManagerPath = process.env.EPICDASH_USB_MANAGER || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/UsbEcuManager.kt');
const performanceMetricsPath = process.env.EPICDASH_PERFORMANCE_METRICS || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/PerformanceMetrics.kt');
const diagnosticStorePath = process.env.EPICDASH_DIAGNOSTIC_STORE || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/DiagnosticStore.kt');
const diagnosticCachePath = process.env.EPICDASH_DIAGNOSTIC_CACHE || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/DiagnosticStoreSnapshotCache.kt');
const runtimeStorePath = process.env.EPICDASH_RUNTIME_STORE || path.resolve(__dirname, '../../main/java/com/buttonbox/ble/DashboardRuntimeSnapshotStore.kt');

const html = fs.readFileSync(dashboardPath, 'utf8');
const activity = fs.readFileSync(activityPath, 'utf8');
const dataHub = fs.readFileSync(dataHubPath, 'utf8');
const usbManager = fs.readFileSync(usbManagerPath, 'utf8');
const performanceMetrics = fs.readFileSync(performanceMetricsPath, 'utf8');
const diagnosticStore = fs.readFileSync(diagnosticStorePath, 'utf8');
const diagnosticCache = fs.readFileSync(diagnosticCachePath, 'utf8');
const runtimeStore = fs.readFileSync(runtimeStorePath, 'utf8');

function normalize(source) {
  return source.replace(/\s+/g, ' ').trim();
}

function functionSource(source, marker) {
  const start = source.indexOf(marker);
  assert.ok(start >= 0, `Missing function marker: ${marker}`);
  const brace = source.indexOf('{', start);
  assert.ok(brace > start, `Missing function body: ${marker}`);
  let depth = 0;
  let quote = null;
  let escaped = false;
  for (let index = brace; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '{') depth++;
    if (char === '}') {
      depth--;
      if (depth === 0) return source.slice(start, index + 1);
    }
  }
  throw new Error(`Unterminated function: ${marker}`);
}

function sourceBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.ok(start >= 0, `Missing source marker: ${startMarker}`);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(end > start, `Missing end marker ${endMarker} after ${startMarker}`);
  return source.slice(start, end);
}

const refreshSource = functionSource(html, 'function refreshDiagnostics');
const wantedSource = functionSource(html, 'function diagnosticsRefreshWanted');
const syncSource = functionSource(html, 'function syncDiagnosticsRefreshTimer');

function createHarness() {
  let diagnosticsActive = false;
  let settingsActive = false;
  let nextTimerId = 1;
  const timers = new Map();
  const cleared = [];
  const calls = {
    native: 0,
    app: 0,
    ble: 0,
    usb: 0,
    history: 0,
    errors: []
  };
  let response = {
    app: { versionName: 'test' },
    ble: { connected: true },
    usb: { state: 'streaming' },
    store: { revision: 1, events: [], warnings: [] },
    dashboard: { activePage: 'page-daily' }
  };

  const context = {
    console,
    JSON,
    Number,
    window: {
      EpicDashAndroid: {
        getDiagnosticsJson() {
          calls.native++;
          return typeof response === 'string' ? response : JSON.stringify(response);
        }
      }
    },
    document: {
      querySelector(selector) {
        if (selector === '#page-diagnostics.active') return diagnosticsActive ? {} : null;
        return null;
      }
    },
    diagnosticRevision: -1,
    activeModalStack: {
      includes(id) {
        return id === 'labSettingsModal' && settingsActive;
      }
    },
    applyAppInfo() { calls.app++; },
    renderBleDiagnostics() { calls.ble++; },
    renderUsbDiagnostics() { calls.usb++; },
    renderPersistentHistory() { calls.history++; },
    reportWebError(message, source, line, column) {
      calls.errors.push({ message, source, line, column });
    },
    setInterval(handler, delay) {
      const id = nextTimerId++;
      timers.set(id, { handler, delay });
      return id;
    },
    clearInterval(id) {
      cleared.push(id);
      timers.delete(id);
    }
  };

  vm.createContext(context);
  vm.runInContext(
    `${refreshSource}\n${wantedSource}\nlet diagnosticsRefreshTimer=0;\n${syncSource}\n` +
    `this.__refreshDiagnostics=refreshDiagnostics;\n` +
    `this.__diagnosticsRefreshWanted=diagnosticsRefreshWanted;\n` +
    `this.__syncDiagnosticsRefreshTimer=syncDiagnosticsRefreshTimer;\n` +
    `this.__timerState=()=>diagnosticsRefreshTimer;`,
    context
  );

  return {
    context,
    calls,
    timers,
    cleared,
    setDiagnosticsActive(value) { diagnosticsActive = value; },
    setSettingsActive(value) { settingsActive = value; },
    setResponse(value) { response = value; },
    sync() { context.__syncDiagnosticsRefreshTimer(); },
    refresh() { context.__refreshDiagnostics(); },
    timerState() { return context.__timerState(); },
    fireOnlyTimer() {
      assert.strictEqual(timers.size, 1, 'exactly one diagnostics timer is active');
      [...timers.values()][0].handler();
    }
  };
}

assert.strictEqual(
  createHarness().context.__diagnosticsRefreshWanted({ diagnosticsActive: false, settingsActive: false }),
  false,
  'closed Diagnostics and settings do not own refresh work'
);
assert.strictEqual(
  createHarness().context.__diagnosticsRefreshWanted({ diagnosticsActive: true, settingsActive: false }),
  true,
  'the Diagnostics page owns refresh work'
);
assert.strictEqual(
  createHarness().context.__diagnosticsRefreshWanted({ diagnosticsActive: false, settingsActive: true }),
  true,
  'LAB settings owns refresh work'
);

{
  const harness = createHarness();
  harness.sync();
  assert.strictEqual(harness.calls.native, 0, 'closed state performs no synchronous diagnostics bridge call');
  assert.strictEqual(harness.timers.size, 0, 'closed state installs no periodic timer');
}

{
  const harness = createHarness();
  harness.setDiagnosticsActive(true);
  harness.sync();
  assert.strictEqual(harness.calls.native, 1, 'entering Diagnostics refreshes immediately');
  assert.strictEqual(harness.timers.size, 1, 'entering Diagnostics installs one timer');
  assert.strictEqual([...harness.timers.values()][0].delay, 1000, 'Diagnostics refresh cadence is exactly one second');
  harness.sync();
  assert.strictEqual(harness.calls.native, 1, 'repeated ownership sync does not refresh again');
  assert.strictEqual(harness.timers.size, 1, 'repeated ownership sync does not install a duplicate timer');
  harness.fireOnlyTimer();
  assert.strictEqual(harness.calls.native, 2, 'the timer performs one synchronous bridge refresh per tick');
}

{
  const harness = createHarness();
  harness.setSettingsActive(true);
  harness.sync();
  assert.strictEqual(harness.calls.native, 1, 'settings-only ownership refreshes immediately');
  assert.strictEqual(harness.timers.size, 1, 'settings-only ownership installs the shared timer');
}

{
  const harness = createHarness();
  harness.setDiagnosticsActive(true);
  harness.sync();
  const timerId = harness.timerState();
  harness.setSettingsActive(true);
  harness.sync();
  assert.strictEqual(harness.timerState(), timerId, 'Diagnostics and settings share the same timer');
  harness.setDiagnosticsActive(false);
  harness.sync();
  assert.strictEqual(harness.timerState(), timerId, 'closing Diagnostics keeps the timer while settings remains open');
  assert.strictEqual(harness.cleared.length, 0, 'the shared timer is not cleared while one owner remains');
  harness.setSettingsActive(false);
  harness.sync();
  assert.strictEqual(harness.timerState(), 0, 'closing the last owner clears timer state');
  assert.deepStrictEqual(harness.cleared, [timerId], 'the shared timer is cleared exactly once');
}

{
  const harness = createHarness();
  harness.refresh();
  assert.deepStrictEqual(
    { native: harness.calls.native, app: harness.calls.app, ble: harness.calls.ble, usb: harness.calls.usb, history: harness.calls.history },
    { native: 1, app: 1, ble: 1, usb: 1, history: 1 },
    'one refresh performs one bridge call and renders app/BLE/USB plus changed history'
  );
  harness.refresh();
  assert.deepStrictEqual(
    { native: harness.calls.native, app: harness.calls.app, ble: harness.calls.ble, usb: harness.calls.usb, history: harness.calls.history },
    { native: 2, app: 2, ble: 2, usb: 2, history: 1 },
    'unchanged store revision suppresses only persistent-history DOM work'
  );
  harness.setResponse({ app: {}, ble: {}, usb: {}, store: { revision: 2 } });
  harness.refresh();
  assert.strictEqual(harness.calls.history, 2, 'changed store revision re-renders persistent history');
}

{
  const harness = createHarness();
  harness.setResponse('{bad-json');
  harness.refresh();
  assert.strictEqual(harness.calls.errors.length, 1, 'parse failures are routed to the Web error reporter');
  assert.strictEqual(harness.calls.errors[0].source, 'diagnostics');
}

const manualDelays = [...html.matchAll(/setTimeout\s*\(\s*refreshDiagnostics\s*,\s*(\d+)\s*\)/g)]
  .map(match => Number(match[1]));
assert.deepStrictEqual(
  manualDelays,
  [40, 100, 100, 100, 100, 100, 250],
  'manual and startup refresh triggers retain their exact bounded delays'
);

assert.ok(
  html.includes("$('copyReportBtn').onclick=()=>window.EpicDashAndroid?.copyDiagnosticReport?.();"),
  'copy report uses the dedicated native report path'
);
assert.ok(
  html.includes("$('exportReportBtn').onclick=()=>window.EpicDashAndroid?.exportDiagnosticReport?.();"),
  'export report uses the dedicated native report path'
);
assert.ok(
  !refreshSource.includes('copyDiagnosticReport') && !refreshSource.includes('exportDiagnosticReport'),
  'periodic diagnostics refresh does not construct or export the full report'
);

const nativeBridgeBlock = sourceBetween(activity, 'fun getDiagnosticsJson(): String', '@JavascriptInterface\n        fun getUsbTransportFreshnessJson');
const normalizedBridge = normalize(nativeBridgeBlock);
for (const required of [
  '.put("app", appInfoJson())',
  '.put("store", DiagnosticStore.snapshotJson(this@DashboardLabActivity))',
  '.put("ble", DashboardDataHub.diagnosticsJson())',
  'usbEcuManager.diagnosticsJson(includeAudit = false)',
  '.put("dashboard", runtimeSnapshotJson())'
]) {
  assert.ok(normalizedBridge.includes(normalize(required)), `native bridge retains ${required}`);
}
assert.ok(!normalizedBridge.includes('buildDiagnosticReport'), 'periodic bridge does not build the full text report');

const reportBlock = sourceBetween(activity, 'private fun buildDiagnosticReport(): String', 'private fun diagnosticHistoryEntries');
const normalizedReport = normalize(reportBlock);
for (const required of [
  'DiagnosticStore.snapshotJson(this)',
  'DashboardDataHub.diagnosticsJson()',
  'usbEcuManager.diagnosticsJson()',
  'runtimeSnapshotJson()',
  'DiagnosticReportFormatter.format('
]) {
  assert.ok(normalizedReport.includes(normalize(required)), `full report retains ${required}`);
}
assert.ok(!normalizedReport.includes('includeAudit = false'), 'full report keeps the default USB audit/self-test content');

assert.ok(
  normalize(dataHub).includes(normalize('.put("performance", PerformanceMetrics.snapshotJson())')),
  'BLE/native diagnostics retains PerformanceMetrics representation'
);
assert.ok(
  normalize(usbManager).includes(normalize('.put("performance", PerformanceMetrics.snapshotJson())')),
  'USB diagnostics retains PerformanceMetrics representation'
);
assert.ok(
  normalize(performanceMetrics).includes(normalize('if (cachedSnapshotRevision == requestedRevision) return cachedSnapshotJson')),
  'PerformanceMetrics snapshot remains revision-cached'
);
assert.ok(
  normalize(diagnosticStore).includes(normalize('return snapshotCache.snapshot(')),
  'DiagnosticStore delegates snapshot construction to its cache'
);
assert.ok(
  normalize(diagnosticCache).includes(normalize('if (key == cachedKey) cachedSnapshot?.let { return it }')),
  'DiagnosticStore histories remain cached for unchanged persisted inputs'
);
assert.ok(
  normalize(runtimeStore).includes(normalize('val result = deepCopy(stored)')),
  'runtime diagnostics snapshot remains a complete deep copy per synchronous read'
);
assert.ok(
  normalize(runtimeStore).includes(normalize('result.put( "nativeSnapshotAgeMs"')) ||
    normalize(runtimeStore).includes(normalize('result.put( "nativeSnapshotAgeMs",')),
  'runtime diagnostics snapshot appends native age metadata per read'
);

console.log('dashboard_diagnostics_active_view_characterization_test: PASS');
