'use strict';

const assert = require('assert');
const fs = require('fs');

const read = path => fs.readFileSync(path, 'utf8');
const ui = read('app/src/main/assets/t4_tuning_workspace.js');
const activity = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const manager = read('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt');
const preview = read('app/src/main/java/com/buttonbox/ble/TuningSemanticPreview.kt');
const writeEngine = read('app/src/main/java/com/buttonbox/ble/TuningWriteEngine.kt');

for (const marker of [
  'Apply edit',
  'Pending changes',
  'previewTuningScalarJson',
  'previewTuningBitFieldJson',
  'epicdash.t4.scalarDraft.v1',
  'noOp',
  'writeEligible',
  'Changes',
  'write pending edits first'
]) assert.ok(ui.includes(marker), `missing pending-edit tuner UX marker: ${marker}`);

for (const marker of [
  'previewTuningScalarJson(name: String, requestedValue: Double)',
  'usbEcuManager.previewTuningScalarJson(name, requestedValue)',
  'previewTuningBitFieldJson(name: String, requestedValue: Double)',
  'usbEcuManager.previewTuningBitFieldJson(name, requestedValue)'
]) assert.ok(activity.includes(marker), `missing semantic preview bridge contract: ${marker}`);

for (const marker of [
  'internal fun previewTuningScalarJson(name: String, requestedValue: Double)',
  'internal fun previewTuningBitFieldJson(name: String, requestedValue: Double)',
  'TuningSemanticPreviewBuilder.preview(',
  'SemanticTuningWriteRequest('
]) assert.ok(manager.includes(marker), `missing manager semantic-preview contract: ${marker}`);

for (const marker of [
  'SEMANTIC_PREVIEW',
  'TuningWritePlanner.resolveForPreview(',
  '.put("noOp", noOp)',
  '.put("writeEligible", !noOp)'
]) assert.ok(preview.includes(marker), `missing semantic-preview contract: ${marker}`);

for (const marker of [
  'fun resolveForPreview(',
  'private fun resolveRequest(',
  'resolveRequest(profile, snapshot, request, conditionAuthority)'
]) assert.ok(writeEngine.includes(marker), `preview/execution resolver sharing is missing: ${marker}`);
assert.ok(writeEngine.includes('conditionAuthority.requireWriteAllowed(request)'), 'preview/execution no longer share fail-closed INI condition authority');

for (const forbidden of [
  'simulateTuningScalarJson',
  'simulateTuningArrayCellJson',
  'LOCAL_SIMULATION_ONLY',
  'LOCAL_ARRAY_SIMULATION_ONLY',
  '.put("t4Simulation"',
  '.put("t5ArraySimulation"'
]) {
  assert.ok(!ui.includes(forbidden), `Tuner UI retained historical simulation marker: ${forbidden}`);
  assert.ok(!activity.includes(forbidden), `Activity retained historical simulation marker: ${forbidden}`);
  assert.ok(!manager.includes(forbidden), `Manager retained historical simulation marker: ${forbidden}`);
}

for (const forbidden of [
  '.put("offset"',
  '.put("pageNumber"',
  '.put("pageIdentifier"',
  '.put("rawHex"',
  '.put("proposedRaw"',
  '.put("originalRawHex"',
  '.put("encodedBytes"'
]) {
  assert.ok(!preview.includes(forbidden), `semantic preview JSON leaked storage/raw metadata: ${forbidden}`);
}

for (const forbidden of [
  'bulkTransfer(',
  'controlTransfer(',
  'requestBurn',
  'burnCommand',
  'TuningWriteProtocol.writeBody'
]) assert.ok(!preview.includes(forbidden), `semantic preview gained forbidden transport/mutation surface: ${forbidden}`);

console.log('Step 0F semantic preview + shared resolver characterization passed');
