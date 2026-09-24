'use strict';

const assert = require('assert');
const fs = require('fs');

const layout = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_ui_layout_completion.js', 'utf8');
const tableControls = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_table_controls.js', 'utf8');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

assert.doesNotThrow(() => new Function(layout), '1198 responsive geometry does not parse');

for (const marker of [
  'TUNER_UI_GEOMETRY_AUTHORITY_1198',
  'TUNER_UI_1198_BINARY_SELECTOR_3_TO_1',
  '--t4-binary-height:24px',
  '--t4-binary-width:72px',
  'width:var(--t4-binary-width)!important',
  'height:var(--t4-binary-height)!important',
  'TUNER_UI_1198_PHONE_RESPONSIVE_GEOMETRY',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-selectors{grid-template-columns:1fr!important',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-table-editbar,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar{overflow:visible!important;flex-wrap:wrap!important',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-table-extra-tools{flex:1 0 100%!important;display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-workspace{flex:0 0 auto!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(250px,40vh) auto!important',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-list{display:flex!important;flex-direction:row!important;overflow-x:auto!important',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-telemetry{overflow-x:auto!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-workspace{grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(180px,1fr) auto!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-list{display:flex!important;flex-direction:row!important;overflow-x:auto!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-table-editbar,body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-editbar{flex-wrap:wrap!important;overflow:visible!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-telemetry{overflow-x:auto!important'
]) assert.ok(layout.includes(marker), 'missing 1198 responsive marker: ' + marker);

const height = Number(layout.match(/--t4-binary-height:(\d+)px/)?.[1]);
const width = Number(layout.match(/--t4-binary-width:(\d+)px/)?.[1]);
assert.ok(Number.isFinite(height) && Number.isFinite(width), 'binary selector dimensions are missing');
assert.strictEqual(width / height, 3, 'binary selector target must remain exactly 3:1');

assert.ok(tableControls.includes('desired=largeTable?widthFit:Math.min(widthFit,heightFit)'), '1198 changed the physically accepted large-table width-fit behavior');
assert.ok(tableControls.includes('Deliberately no MutationObserver here'), '1198 lost the freeze-loop recovery guard');
assert.ok(!layout.includes('body[data-t4tw-viewport="phone-portrait"] .t4tw-table-editbar,body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar{overflow-x:auto!important;flex-wrap:nowrap!important'), '1198 reintroduced clipped phone portrait editor toolbars');

const versionCode = Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1]);
assert.ok(Number.isFinite(versionCode) && versionCode >= 1198, '1198-or-newer versionCode is missing');

for (const forbidden of [
  'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'rawHex',
  'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson('
]) assert.ok(!layout.includes(forbidden), '1198 responsive geometry gained forbidden tuning authority: ' + forbidden);

console.log('Tuner 1198 phone responsive characterization passed');
