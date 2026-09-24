'use strict';

const assert = require('assert');
const fs = require('fs');

const workspace = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningWorkspace.kt','utf8');
const manager = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt','utf8');
const permanent = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TunerPermanentProjectStore.kt','utf8');
const arrayLayer = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_snapshot_array_values.js','utf8');
const gradle = fs.readFileSync('app/build.gradle.kts','utf8');

assert.ok(workspace.includes('fun toJson(includeValues: Boolean = true): JSONObject'),
  'array summary must support omitting bulk cell values');
assert.ok(workspace.includes('fun toJson(includeArrayValues: Boolean = false): JSONObject'),
  'live workspace must default to lean array summaries');
assert.ok(workspace.includes('it.toJson(includeArrayValues)'),
  'workspace array serialization does not honor lean/full projection');
assert.ok(permanent.includes('toJson(includeArrayValues = true)'),
  'permanent saved project must explicitly preserve embedded array values');
assert.ok(manager.includes('semanticWorkspace.toJson().also'),
  'live manager must continue through the production semantic workspace serializer');

const detailStart = workspace.indexOf('    fun arrayDetail(');
const detailEnd = workspace.indexOf('    private fun decodeArrayValues', detailStart);
assert.ok(detailStart >= 0 && detailEnd > detailStart, 'unable to isolate arrayDetail implementation');
const detail = workspace.slice(detailStart, detailEnd);
assert.ok(!detail.includes('val workspace = build('),
  'single-array detail must not rebuild the complete Tuner workspace');
assert.ok(!detail.includes('TuningWorkspaceBuilder.build'),
  'single-array detail regained full-workspace construction');
assert.ok(detail.includes("profile.tuneArrays.filter { it.name == name }"),
  'array detail must resolve by exact semantic array name');
assert.ok(detail.includes('decodeArrayValues(definition, snapshotPage.bytes())'),
  'array detail must decode only the requested array from authoritative TuneSnapshot bytes');
assert.ok(detail.includes('snapshot.profileFingerprint.equals(profileFingerprint'),
  'direct array detail lost profile fingerprint validation');
assert.ok(detail.includes('snapshot.generation == currentGeneration'),
  'direct array detail lost generation validation');
assert.ok(detail.includes('snapshotPage.identifier == profilePage.identifier'),
  'direct array detail lost page identity validation');

// Live summaries omit values, so the existing snapshot-value layer must retain the guarded native
// semantic detail fallback. Saved/offline projects still use their embedded values first.
assert.ok(arrayLayer.includes('return t4SnapshotArrayDetailBase(name);'),
  'live array detail native fallback is missing');
assert.ok(arrayLayer.includes('const embedded=t4EmbeddedSnapshotArrayDetail(name,workspace);'),
  'saved-project embedded array fallback was removed');

const currentVersionCode=Number((gradle.match(/versionCode = (\d+)/)||[])[1]||0);
assert.ok(currentVersionCode>=1206, '1206 architecture must not be tested on an older app identity');

for (const forbidden of ['pageNumber','offset','rawHex','bulkTransfer(','controlTransfer(','requestBurn']) {
  assert.ok(!arrayLayer.includes(forbidden), 'WebView array layer gained forbidden native/raw authority: '+forbidden);
}

console.log('Tuner 1206 lean live workspace / direct array-detail characterization passed');

// Later performance candidates extend this gate rather than weakening the 1206 architecture checks.
require('./tuner_ui_1207_live_lazy_crash_curve_characterization_test.js');
