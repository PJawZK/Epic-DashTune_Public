'use strict';

const assert = require('assert');
const fs = require('fs');

const telemetry = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_telemetry_controls.js', 'utf8');
const tableControls = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_table_controls.js', 'utf8');
const layout = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_ui_layout_completion.js', 'utf8');
const dashboard = fs.readFileSync('app/src/main/assets/dashboard_lab.html', 'utf8');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

assert.doesNotThrow(() => new Function(telemetry), '1200 telemetry/shell JavaScript does not parse');

for (const marker of [
  'TUNER_UI_1200_PHONE_CURVES_AND_UNIFIED_SHELL',
  'body[data-t4tw-viewport="phone-portrait"] #page-tuning .t4tw-telemetry',
  'body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-telemetry{display:none!important',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-table-main .t4tw-zoom-cluster',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-table-main .t4tw-zoom-cluster{display:none!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-selectors{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important',
  'text-overflow:ellipsis!important',
  't4tw-curve-manager-trigger',
  't4tw-curve-manager-modal',
  't4twCurveManagerButton',
  "trigger.textContent=count?'Loaded · '+count:'Loaded curves'",
  't4Ui1200CurveBody.replaceChildren(manager)',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-workspace{display:grid!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(0,1fr)!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-editbar{position:static!important',
  'T4_UI_1200_SETTINGS_ICON',
  'T4_UI_1200_FLAME_ICON',
  '#t4twTopBurn #t4twTopBurnLabel{display:none!important'
]) assert.ok(telemetry.includes(marker), 'missing retained 1200 phone-curves marker: ' + marker);

for (const destination of ['dash','tuner','logging','diagnostics']) {
  assert.ok(dashboard.includes(`data-shell="${destination}"`), `existing shell destination missing: ${destination}`);
}

assert.ok(tableControls.includes('desired=largeTable?widthFit:Math.min(widthFit,heightFit)'), '1200 changed the physically accepted large-table Fit rule');
assert.ok(tableControls.includes('Deliberately no MutationObserver here'), '1200 lost the selector freeze-loop guard');
assert.ok(layout.includes('--t4-binary-width:72px') && layout.includes('--t4-binary-height:24px'), '1200 changed the accepted 3:1 selector geometry');
const versionCode = Number((gradle.match(/versionCode\s*=\s*(\d+)/) || [])[1] || 0);
assert.ok(versionCode >= 1200, 'current candidate regressed below the 1200 feature baseline');

for (const forbidden of [
  'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'rawHex',
  'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson(', 'saveTuningToEcu('
]) assert.ok(!telemetry.includes(forbidden), '1200 presentation layer gained forbidden tuning authority: ' + forbidden);

console.log('Tuner 1200 phone curves/shell characterization passed');
