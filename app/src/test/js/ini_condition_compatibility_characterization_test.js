'use strict';

const assert = require('assert');
const fs = require('fs');

const dashboard = fs.readFileSync('app/src/main/assets/dashboard_lab.html','utf8');
const tuner = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js','utf8');
const workspace = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningWorkspace.kt','utf8');
const conditions = fs.readFileSync('app/src/main/java/com/buttonbox/ble/IniConditionEvaluator.kt','utf8');
const writes = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningWriteEngine.kt','utf8');
const usb = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt','utf8');
const activity = fs.readFileSync('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt','utf8');

for (const marker of [
  'id="iniStatePill"',
  'id="iniStateLabel"',
  "function applyIniCompatibility",
  "function refreshIniCompatibility",
  "getIniCompatibilityJson",
  "window.EpicDashApplyIniCompatibility=applyIniCompatibility"
]) assert.ok(dashboard.includes(marker), 'missing persistent INI compatibility UI marker: '+marker);

for (const marker of [
  'IniConditionTruth',
  'IniUiConditionState',
  'IniCompatibilityReport',
  'IniConditionAuthority',
  'Condition identifier',
  'unsupported or unresolved; write blocked'
]) assert.ok(conditions.includes(marker), 'missing native condition authority marker: '+marker);

for (const marker of [
  'iniCompatibility',
  'conditionState',
  'writeAllowed',
  'writeBlockReason',
  'IniConditionAuthority.build(profile, snapshot)'
]) assert.ok(workspace.includes(marker), 'workspace no longer projects condition authority: '+marker);

assert.ok(writes.includes('conditionAuthority.requireWriteAllowed(request)'), 'semantic preview/write planner does not enforce INI condition authority');
assert.ok(writes.includes('IniConditionAuthority.build(profile, snapshot)'), 'write planner does not derive conditions from current TuneSnapshot');

for (const marker of [
  'function conditionStateOf',
  'function conditionVisible',
  'function conditionUsable',
  'if (!conditionVisible(entry)) continue;',
  "item?.writeAllowed === false",
  'window.EpicDashApplyIniCompatibility?.(data.iniCompatibility',
  'unsupportedConditionExpressions'
]) assert.ok(tuner.includes(marker), 'Tuner does not consume evaluated INI conditions: '+marker);

assert.ok(!tuner.includes('conditions shown, not yet interpreted'), 'obsolete non-evaluated condition wording returned');
assert.ok(usb.includes('internal fun iniCompatibilityJson()'), 'USB manager lacks lightweight compatibility authority');
assert.ok(activity.includes('fun getIniCompatibilityJson(): String'), 'WebView lacks compatibility bridge');

const decodeConditionStart = conditions.indexOf('private fun decodeConditionValues(');
assert.ok(decodeConditionStart >= 0, 'missing condition-value decoder');
const decodeConditionEnd = conditions.indexOf('private fun equivalentBit', decodeConditionStart);
assert.ok(decodeConditionEnd > decodeConditionStart, 'missing condition-value decoder boundary');
const decodeConditionSection = conditions.slice(decodeConditionStart, decodeConditionEnd);
assert.ok(
  decodeConditionSection.includes('val snapshotBytes = pageList.associate { it.pageNumber to it.bytes() }'),
  'condition evaluation must copy each TuneSnapshot page once'
);
assert.strictEqual(
  (decodeConditionSection.match(/it\.bytes\(\)/g) || []).length,
  1,
  'condition evaluation reintroduced per-definition TunePageSnapshot defensive copies'
);

console.log('INI condition compatibility characterization passed');
