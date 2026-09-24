'use strict';

const assert = require('assert');
const fs = require('fs');

const connection = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_connection_refresh.js','utf8');
const telemetry = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_telemetry_controls.js','utf8');
const dashboard = fs.readFileSync('app/src/main/assets/dashboard_lab.html','utf8');
const gradle = fs.readFileSync('app/build.gradle.kts','utf8');

assert.doesNotThrow(() => new Function(connection), '1202+ connection/UI refinement JavaScript does not parse');

for (const marker of [
  'TUNER_UI_1202_HEADER_ACTIONS_LANDSCAPE_ORIENTATION',
  "const staticSlot=document.getElementById('sharedTopPageActions')",
  'if(!staticSlot)return null;',
  't4Ui1202SharedActionSlot=staticSlot;',
  'return staticSlot;',
  "lock.classList.add('t4tw-top-action','t4tw-shell-lock')",
  "edit.classList.add('t4tw-top-action','t4tw-shell-edit')",
  "if(destination==='tuner')",
  'tunerStatus.insertBefore(lock,ecu||gear||null)',
  "edit.hidden=destination!=='dash'",
  "t4Ui1202ReadLabel.textContent='ECU'",
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-read-action .t4tw-read-label',
  'body[data-t4tw-viewport="tablet-portrait"] .t4tw-read-action .t4tw-read-label',
  'body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-nav-row .t4tw-hierarchy-selectors',
  'body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-nav-row .t4tw-hierarchy-selectors',
  'display:flex!important;flex-direction:row!important;flex-wrap:nowrap!important',
  'body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-hierarchy-chevron',
  'body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-hierarchy-chevron',
  'queueMicrotask(()=>{'
]) assert.ok(connection.includes(marker), 'missing retained 1202 marker: '+marker);

for (const marker of [
  'EPIC_SHARED_SHELL_STATIC_V1',
  'id="sharedTopPageActions"',
  'id="headerLockBtn"',
  'id="layoutEditBtn"'
]) assert.ok(dashboard.includes(marker), 'static shared shell owner missing: '+marker);

assert.ok(telemetry.includes('t4Ui1201SharedRead=null;'), '1201 binder no longer explicitly rejects shared Read ownership');
assert.ok(telemetry.includes('t4Ui1201SharedBurn=null;'), '1201 binder no longer explicitly rejects shared Burn ownership');
for (const forbidden of [
  "document.getElementById('sharedTopRead')?.remove()",
  "document.getElementById('sharedTopBurn')?.remove()",
  "t4Ui1202SharedActionSlot.id='sharedTopPageActions'",
  "t4Ui1201SharedRead=document.createElement('button')",
  "t4Ui1201SharedBurn=document.createElement('button')",
  't4tw-shared-dash-tools'
]) assert.ok(!connection.includes(forbidden) && !telemetry.includes(forbidden), 'legacy runtime action ownership returned: '+forbidden);

const versionCode = Number((gradle.match(/versionCode\s*=\s*(\d+)/) || [])[1] || 0);
assert.ok(versionCode >= 1205, 'current candidate regressed below the 1205 connected/static-shell baseline');

for (const forbidden of [
  'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'rawHex', 'pageNumber',
  'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson(', 'saveTuningToEcu('
]) assert.ok(!connection.includes(forbidden), '1202 presentation refinement gained forbidden native/write authority: '+forbidden);

console.log('Tuner 1202 static header actions / landscape baseline characterization passed');
