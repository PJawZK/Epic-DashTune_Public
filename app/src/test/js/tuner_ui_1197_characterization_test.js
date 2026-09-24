'use strict';

const assert = require('assert');
const fs = require('fs');

const ui = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_table_controls.js', 'utf8');
const layout = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_ui_layout_completion.js', 'utf8');
assert.doesNotThrow(() => new Function(ui), '1197 UI correction layer does not parse');
assert.doesNotThrow(() => new Function(layout), '1197/1198 geometry authority does not parse');

// FREEZE_REGRESSION_RECOVERY_1197: selector patch must remain event/render driven, never child-list self-observing.
for (const marker of [
  'presentationSystemName=function(rawValue)',
  "return raw||'Other'",
  'sortPresentationSystems=function(systems){return systems;}',
  'TUNER_UI_CORRECTIONS_1197_NAVIGATION_INSPECTION',
  'const t4BaseHierarchySelect1197=hierarchySelect',
  "level==='feature'||level==='table'||level==='curve'",
  'optionValue?{...option,disabled:false}:option',
  'let t4TableAutoFit=true',
  't4TableFitSurfaceKey=',
  'function t4ApplyTableFit()',
  'function t4ScheduleTableFit(force=true)',
  'ResizeObserver',
  'usableWidth=',
  'widthFit=',
  'usableHeight=',
  'heightFit=',
  'largeTable=',
  'desired=largeTable?widthFit:Math.min(widthFit,heightFit)',
  "grid.style.gridTemplateColumns='52px repeat('",
  'box-sizing:border-box!important',
  'tableZoom=cell/22',
  'fitInlineTable=function(){t4ScheduleTableFit(true);}',
  'setInlineTableZoom=function(nextZoom)',
  'key!==t4TableFitSurfaceKey',
  't4ScheduleTableFit(false)',
  'TUNER_UI_CORRECTIONS_1197_BOOLEAN',
  'T4_BOOLEAN_LABEL_PAIRS',
  "'false|true'",
  "'disabled|enabled'",
  "'no|yes'",
  'function t4BooleanSpec(bit)',
  'const t4BaseBitLabel1197=bitLabel',
  "if(!Number.isFinite(numeric))return '—'",
  'bit?.valueAvailable!==false',
  "field?.classList.add('t4tw-boolean-field')",
  "toggle.classList.toggle('unknown',!valueKnown)",
  "toggle.setAttribute('aria-disabled',valueKnown?'false':'true')",
  'function t4SetTextIfChanged(node,text)',
  "t4SetTextIfChanged(toggle,'—')",
  'const t4BaseSettingsFieldNode1197=settingsFieldNode',
  "toggle.setAttribute('role','switch')",
  "toggle.setAttribute('aria-checked'",
  "t4SetTextIfChanged(toggle,'')",
  'Deliberately no MutationObserver here',
  "hit.setAttribute('r','18')",
  "hit.setAttribute('data-curve-hit'",
  'function t4PresentationAudit()',
  'unsupportedExpressions',
  'unresolvedIdentifiers',
  'unroutedMenuItems',
  'unroutedFields',
  'unroutedPanels',
  'window.__EPIC_TUNER_PRESENTATION_AUDIT__=audit'
]) assert.ok(ui.includes(marker), 'missing 1197 UI correction marker: ' + marker);

const helperMatch = ui.match(/function t4SetTextIfChanged\(node,text\)\{[^}]+\}/);
assert.ok(helperMatch, '1197 selector idempotence helper is missing');
const t4SetTextIfChanged = new Function(helperMatch[0] + '; return t4SetTextIfChanged;')();
let textValue = 'initial';
let writes = 0;
const fakeNode = {};
Object.defineProperty(fakeNode, 'textContent', {
  get(){ return textValue; },
  set(next){ writes++; textValue = next; }
});
for (let i = 0; i < 10000; i++) t4SetTextIfChanged(fakeNode, '—');
for (let i = 0; i < 10000; i++) t4SetTextIfChanged(fakeNode, '');
for (let i = 0; i < 10000; i++) t4SetTextIfChanged(fakeNode, 'Enabled');
assert.strictEqual(writes, 3, 'selector patch must settle after each actual text transition');
assert.strictEqual(textValue, 'Enabled');

for (const marker of [
  'TUNER_UI_LAYOUT_COMPLETION_V2 / TUNER_UI_GEOMETRY_AUTHORITY_1198',
  'TUNER_UI_CORRECTIONS_1197_TOP_EDGE',
  'grid-template-rows:minmax(0,1fr)!important',
  'grid-row:1!important',
  '#page-tuning,#page-tuning *{box-sizing:border-box}',
  '--t4-space-tight:4px',
  '--t4-space:8px',
  '--t4-space-section:12px',
  'grid-template-columns:minmax(0,1fr)!important;gap:var(--t4-space)!important',
  'grid-template-columns:repeat(2,minmax(0,1fr))!important',
  'row-gap:0!important',
  '.t4tw-settings-field.t4tw-boolean-field',
  '.t4tw-bit-toggle.t4tw-boolean-toggle.unknown',
  '.t4tw-curve-list{gap:0!important;padding:0!important}',
  '.t4tw-curve-editbar .t4tw-table-selection{min-width:0!important}',
  'body[data-t4tw-viewport="tablet-portrait"] .t4tw-grid',
  'body[data-t4tw-viewport="tablet-landscape"] .t4tw-parameters-shell',
  'body[data-t4tw-viewport="tablet-portrait"] .t4tw-parameters-shell',
  'body[data-t4tw-viewport="phone-portrait"] .t4tw-parameters-shell',
  'body[data-t4tw-viewport="phone-landscape"] .t4tw-parameters-shell',
  'queueMicrotask(()=>document.head.appendChild(t4UiLayoutStyle))'
]) assert.ok(layout.includes(marker), 'missing 1197/1198 geometry marker: ' + marker);

assert.ok(!ui.includes('PRESENTATION_SYSTEM_ORDER'), '1197 correction layer reintroduced WebView-owned menu ordering');
assert.ok(!ui.includes("tableZoom=1;const z="), '1197 correction layer reintroduced fake 100% table fit');
assert.ok(!ui.includes("bitLabel(bit, effectiveValue);"), '1197 selector layer still permits the old direct NaN label path');
assert.ok(!ui.includes('new MutationObserver(()=>t4PatchBooleanToggles())'), '1197 selector layer reintroduced the child-list mutation feedback loop');

for (const forbidden of [
  'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'pageNumber', 'rawHex',
  'burnCommand', 'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson('
]) {
  assert.ok(!ui.includes(forbidden), '1197 presentation layer gained forbidden authority: ' + forbidden);
  assert.ok(!layout.includes(forbidden), '1197/1198 geometry layer gained forbidden authority: ' + forbidden);
}

console.log('Tuner 1197 UI correction characterization passed');