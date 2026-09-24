'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_snapshot_array_values.js','utf8');
const workspaceSource = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningWorkspace.kt','utf8');
const surfaceSource = fs.readFileSync('app/src/main/java/com/buttonbox/ble/T4TuningWorkspaceSurface.kt','utf8');
const permanentStore = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TunerPermanentProjectStore.kt','utf8');

assert.ok(workspaceSource.includes('val values: List<Double> = emptyList()'), 'workspace array model does not retain authoritative decoded values natively');
assert.ok(workspaceSource.includes('fun toJson(includeValues: Boolean = true): JSONObject'), 'workspace array JSON cannot distinguish lean live from saved embedded values');
assert.ok(workspaceSource.includes('fun toJson(includeArrayValues: Boolean = false): JSONObject'), 'live workspace does not default to lean array summaries');
assert.ok(workspaceSource.includes('values = values'), 'workspace builder does not retain decoded array values natively');
assert.ok(surfaceSource.includes('t4_tuning_workspace_snapshot_array_values.js'), 'snapshot array value layer is not injected');
assert.ok(surfaceSource.indexOf('SNAPSHOT_ARRAY_VALUES_ASSET') < surfaceSource.indexOf('PROJECT_STATE_ASSET'), 'snapshot values must be available before permanent project presentation');
assert.ok(permanentStore.includes('TuningWorkspaceBuilder.build(profile, decodeSnapshot, 1L)'), 'saved native snapshot must reuse the production semantic workspace decoder');
assert.ok(permanentStore.includes('toJson(includeArrayValues = true)'), 'saved project must explicitly keep complete array values');

let baseCalls = 0;
const context = {
  Number,String,Array,Object,JSON,
  workspace: {
    status:'ready',generation:7,profileFingerprint:'profile-fp',tuneFingerprint:'tune-fp',
    arrays:[{name:'veTable1',dimensions:[2,2],elementCount:4,unit:'%',low:0,high:255,digits:1}]
  },
  getSemanticArrayDetail(name) {
    baseCalls++;
    if (name === 'veTable1') {
      return {
        status:'ready',capability:'READ_ONLY',source:'LIVE_TUNE_SNAPSHOT',generation:7,
        profileFingerprint:'profile-fp',tuneFingerprint:'tune-fp',name:'veTable1',dimensions:[2,2],
        elementCount:4,unit:'%',low:0,high:255,digits:1,values:[41.5,42.0,43.25,44.75]
      };
    }
    return {status:'not_ready',reason:'No semantic array '+name};
  }
};

vm.runInNewContext(source, context, {filename:'t4_tuning_workspace_snapshot_array_values.js'});
const live = context.getSemanticArrayDetail('veTable1');
assert.strictEqual(live.status,'ready');
assert.strictEqual(live.source,'LIVE_TUNE_SNAPSHOT');
assert.deepStrictEqual(Array.from(live.values),[41.5,42.0,43.25,44.75]);
assert.deepStrictEqual(Array.from(live.dimensions),[2,2]);
assert.strictEqual(baseCalls,1,'lean live workspace must resolve requested array values through the semantic native detail bridge');

const missing=context.getSemanticArrayDetail('notEmbedded');
assert.strictEqual(missing.status,'not_ready');
assert.strictEqual(baseCalls,2,'missing live array should retain guarded native fallback behavior');

// Saved/offline projects deliberately retain complete embedded values so no live bridge is needed.
context.workspace={
  status:'ready',savedProject:true,generation:0,profileFingerprint:'profile-fp',tuneFingerprint:'tune-fp',
  arrays:[{name:'veTable1',dimensions:[2,2],elementCount:4,unit:'%',low:0,high:255,digits:1,values:[41.5,42.0,43.25,44.75]}]
};
const beforeSavedCalls=baseCalls;
const saved=context.getSemanticArrayDetail('veTable1');
assert.strictEqual(saved.status,'ready');
assert.strictEqual(saved.source,'SAVED_TUNE_SNAPSHOT');
assert.strictEqual(saved.capability,'SAVED_PROJECT');
assert.deepStrictEqual(Array.from(saved.values),[41.5,42.0,43.25,44.75]);
assert.strictEqual(baseCalls,beforeSavedCalls,'saved project must use embedded snapshot values without live native detail');

context.workspace={
  status:'ready',definitionOnly:true,source:'INI_DEFINITION',generation:0,profileFingerprint:'profile-new',tuneFingerprint:'',
  arrays:[{name:'veTable1',dimensions:[2,2],elementCount:4,unit:'%',low:0,high:255,digits:1,values:[]}]
};
const beforeDefinitionCalls=baseCalls;
const definitionOnly=context.getSemanticArrayDetail('veTable1');
assert.strictEqual(definitionOnly.status,'ready','INI-only array structure must remain renderable offline');
assert.strictEqual(definitionOnly.source,'INI_DEFINITION');
assert.strictEqual(definitionOnly.capability,'INI_STRUCTURE');
assert.strictEqual(definitionOnly.valueAvailable,false);
assert.deepStrictEqual(Array.from(definitionOnly.dimensions),[2,2]);
assert.strictEqual(definitionOnly.values.length,4);
assert.ok(Array.from(definitionOnly.values).every(value=>Number.isNaN(value)),'INI-only structure must use non-finite placeholders rather than fabricated zeros');
assert.match(definitionOnly.reason,/matching complete TuneSnapshot/i);
assert.strictEqual(baseCalls,beforeDefinitionCalls,'INI-only structure must not fall through to the live USB detail reader');

const missingDefinition=context.getSemanticArrayDetail('notInIni');
assert.strictEqual(missingDefinition.status,'not_ready');
assert.strictEqual(baseCalls,beforeDefinitionCalls,'missing INI-only array must still avoid live USB detail fallback');

assert.ok(source.includes('TUNER_UI_SNAPSHOT_ARRAY_VALUES_V5'));
assert.ok(!source.includes('offlineEditor'),'legacy hybrid offline-editor marker returned to snapshot value layer');

console.log('Tuner lean-live / embedded-saved values plus INI-only structure runtime test passed');
