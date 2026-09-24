'use strict';

const assert = require('assert');
const fs = require('fs');

const tableControls = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_table_controls.js', 'utf8');
const layout = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_ui_layout_completion.js', 'utf8');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

assert.doesNotThrow(() => new Function(tableControls), '1199 table-controls JavaScript does not parse');

for (const marker of [
  'TUNER_UI_1199_TABLE_EDIT_WORKFLOW',
  't4tw-zoom-cluster',
  'grid-template-columns:32px 44px 32px',
  't4twTableEditButton',
  't4twTableEditModal',
  'Edit table selection',
  't4InvertSelection',
  't4ExpandSelection',
  't4ShrinkSelection',
  "t4SelectHalf('above')",
  "t4SelectHalf('below')",
  "t4SelectHalf('left')",
  "t4SelectHalf('right')",
  'Long-press + drag selects a rectangle',
  't4tw-hierarchy-picker-button',
  't4tw-hierarchy-picker-modal',
  'width:calc(100vw - 12px)',
  '.t4tw-hierarchy-picker-option.active',
  'Array.from(select.options||[])',
  't4ClampMenuPanel',
  'max-width:calc(100vw - 16px)!important',
  'body[data-t4tw-viewport="phone-landscape"] #page-tuning:has(.t4tw-table-main) .t4tw-telemetry{display:none!important',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-selectors{grid-template-columns:repeat(3,minmax(0,1fr))!important',
  '.t4tw-bit-toggle.t4tw-boolean-toggle:before{transform:none!important'
]) assert.ok(tableControls.includes(marker), 'missing 1199 table-edit marker: ' + marker);

assert.ok(!tableControls.includes('body[data-t4tw-viewport="phone-landscape"] .t4tw-telemetry{display:none!important'), '1199 table-controls must not globally hide phone-landscape telemetry');
assert.ok(tableControls.includes('dialog.append(head,hint,bar)'), '1199 must move the persistent edit bar into the Edit dialog');
assert.ok(tableControls.includes("controls.insertBefore(editButton,modeButton||null)"), 'Edit button is not inserted into the top table toolbar');
assert.ok(tableControls.includes("button.textContent=count>0?'Edit · '+count:'Edit'"), 'Edit button does not expose current selection count');
assert.ok(tableControls.includes('disabled:!!option.disabled'), 'phone hierarchy picker must inherit the normalized native select disabled state');
assert.ok(tableControls.includes('desired=largeTable?widthFit:Math.min(widthFit,heightFit)'), '1199 changed the physically accepted large-table Fit rule');
assert.ok(tableControls.includes('Deliberately no MutationObserver here'), '1199 lost the selector freeze-loop guard');
assert.ok(layout.includes('--t4-binary-width:72px'), '1199 changed accepted 3:1 binary selector width');
assert.ok(layout.includes('--t4-binary-height:24px'), '1199 changed accepted 3:1 binary selector height');
const versionCode = Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1]);
assert.ok(Number.isFinite(versionCode) && versionCode >= 1199, '1199-or-newer versionCode is missing');

for (const forbidden of [
  'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'rawHex',
  'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson('
]) assert.ok(!tableControls.includes(forbidden), '1199 presentation layer gained forbidden tuning authority: ' + forbidden);

console.log('Tuner 1199 table edit workflow characterization passed');
