'use strict';

const assert = require('assert');
const fs = require('fs');

const connection = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_connection_refresh.js','utf8');
const recovery = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_recovery.js','utf8');
const runtime = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_runtime_extension.js','utf8');
const dashboard = fs.readFileSync('app/src/main/assets/dashboard_lab.html','utf8');
const gradle = fs.readFileSync('app/build.gradle.kts','utf8');

assert.doesNotThrow(() => new Function(connection), 'connection/UI JavaScript does not parse');
assert.doesNotThrow(() => new Function(recovery), '1204 recovery/rotation JavaScript does not parse');

// 1203 remains historical source on this side-quest branch, but 1204 must explicitly retire its
// predictive event owners after the earlier queued installer runs.
for (const marker of [
  'TUNER_UI_1203_ROTATION_AUTHORITY',
  'function t4Ui1203ScheduleViewportNow()',
  't4Ui1203InstallRotationAuthority()'
]) assert.ok(connection.includes(marker), 'missing retained 1203 source marker: '+marker);

for (const marker of [
  'TUNER_UI_1204_MEASURED_ROTATION_AUTHORITY',
  'function t4Ui1204MeasuredViewport()',
  'Number(window.innerWidth)',
  'Number(window.innerHeight)',
  'Number(root?.clientWidth)',
  'Number(root?.clientHeight)',
  'tunerProfileFor(measured.width,measured.height)',
  "document.body.dataset.t4twViewport=next",
  "page.dataset.t4twViewport=next",
  'function t4Ui1204ScheduleMeasuredViewport()',
  'requestAnimationFrame(()=>',
  'function t4Ui1204MeasureUntilStable()',
  't4Ui1204StableFrames>=2',
  't4Ui1204QueueStableGeometry()',
  "window.removeEventListener('resize',t4Ui1203ScheduleViewportNow)",
  "window.visualViewport?.removeEventListener('resize',t4Ui1203ScheduleViewportNow)",
  "window.removeEventListener('orientationchange',t4Ui1203ScheduleViewportNow)",
  'applyTunerViewportProfile=function(){return t4Ui1204ApplyMeasuredProfile().profile;}',
  'scheduleTunerViewportProfile=t4Ui1204ScheduleMeasuredViewport',
  "window.addEventListener('resize',t4Ui1204ScheduleMeasuredViewport",
  "window.visualViewport?.addEventListener('resize',t4Ui1204ScheduleMeasuredViewport",
  "window.addEventListener('orientationchange',t4Ui1204ScheduleMeasuredViewport",
  'queueMicrotask(t4Ui1204InstallMeasuredRotationAuthority)'
]) assert.ok(recovery.includes(marker), 'missing 1204 measured-rotation marker: '+marker);

const rotationStart = recovery.indexOf('/* TUNER_UI_1204_MEASURED_ROTATION_AUTHORITY');
assert.ok(rotationStart >= 0, '1204 rotation block not found');
const rotation1204 = recovery.slice(rotationStart);
assert.ok(!rotation1204.includes('screen.orientation'), '1204 must not predict layout from screen.orientation');
assert.ok(!rotation1204.includes("matchMedia('(orientation:"), '1204 must not predict layout from CSS orientation media');
assert.ok(!rotation1204.includes('visualViewport.width'), '1204 profile authority must use the CSS viewport, not a second visualViewport geometry authority');
assert.ok(!rotation1204.includes('setTimeout('), '1204 rotation path must not add delayed timer retries');

const firstMeasure = rotation1204.indexOf('t4Ui1204ApplyMeasuredProfile()');
const stabilityGate = rotation1204.indexOf('t4Ui1204StableFrames>=2');
const stableGeometry = rotation1204.indexOf('t4Ui1204QueueStableGeometry();', stabilityGate);
assert.ok(firstMeasure >= 0 && stabilityGate > firstMeasure && stableGeometry > stabilityGate,
  '1204 must apply measured profile before deferring expensive geometry until viewport stability');

assert.ok(runtime.includes('setTimeout(() => applyTunerViewportProfile(true), 140)'), 'legacy runtime characterization unexpectedly changed');
assert.ok(connection.includes("window.removeEventListener('resize',scheduleTunerViewportProfile)"), '1203 must still detach the original 90 ms resize owner before 1204 takes final authority');

// Later candidates retain the accepted measured-viewport and static first-paint ownership.
for (const marker of [
  "const staticSlot=document.getElementById('sharedTopPageActions')",
  "const lock=document.getElementById('headerLockBtn');",
  "const edit=document.getElementById('layoutEditBtn');",
  'return {lock,edit};'
]) assert.ok(connection.includes(marker), 'missing retained static shell ownership marker: '+marker);
for (const forbidden of ['legacyTools','t4tw-shared-dash-tools']) {
  assert.ok(!connection.includes(forbidden), 'legacy shell ownership returned: '+forbidden);
}

for (const marker of [
  'EPIC_SHARED_SHELL_STATIC_V1',
  'id="sharedTopPageActions"',
  'id="headerLockBtn"',
  'id="layoutEditBtn"'
]) assert.ok(dashboard.includes(marker), 'static shell owner missing from dashboard source: '+marker);
assert.ok(dashboard.includes("$('headerLockBtn').onclick="), 'screen-lock behavior owner changed unexpectedly');
assert.ok(dashboard.includes("$('layoutEditBtn')"), 'layout-edit behavior owner changed unexpectedly');

const versionCode = Number((gradle.match(/versionCode\s*=\s*(\d+)/) || [])[1] || 0);
assert.ok(versionCode >= 1205, 'current candidate regressed below 1205');

for (const source of [connection,recovery]) {
  for (const forbidden of [
    'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'rawHex', 'pageNumber',
    'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson(', 'saveTuningToEcu('
  ]) assert.ok(!source.includes(forbidden), 'presentation/rotation layer gained forbidden native/write authority: '+forbidden);
}

console.log('Tuner static-shell / measured viewport rotation authority characterization passed');
