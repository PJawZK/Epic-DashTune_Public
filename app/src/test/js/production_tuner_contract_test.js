'use strict';

const fs = require('fs');
const assert = require('assert');

const read = path => fs.readFileSync(path, 'utf8');
const ui = read('app/src/main/assets/t4_tuning_workspace.js');
const activity = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const manager = read('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt');
const engine = read('app/src/main/java/com/buttonbox/ble/TuningWriteEngine.kt');
const burn = read('app/src/main/java/com/buttonbox/ble/TuneBurnPrimitives.kt');
const snapshotReader = read('app/src/main/java/com/buttonbox/ble/AuthoritativeTuneSnapshotReader.kt');
const responseCodec = read('app/src/main/java/com/buttonbox/ble/EcuCommandResponseCodec.kt');
const scalarCodec = read('app/src/main/java/com/buttonbox/ble/TuningScalarCodec.kt');

function region(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(start >= 0, 'missing start marker: ' + startMarker);
  assert.ok(end > start, 'missing end marker after ' + startMarker + ': ' + endMarker);
  return source.slice(start, end);
}

// Production authority: complete current-generation TuneSnapshot + semantic target identity.
for (const marker of [
  'generation',
  'profileFingerprint',
  'tuneFingerprint',
  'SemanticTuningWriteEnvelope',
  'TuningWritePlanner',
  'SCALAR',
  'ARRAY_CELL',
  'BIT_FIELD',
  'expectedSnapshot'
]) assert.ok(engine.includes(marker), 'missing production semantic-tuner contract marker: ' + marker);

// WebView submits semantic identity/value only; physical storage/protocol authority stays native.
const payloadStart = ui.indexOf('const payload = {');
const payloadEnd = ui.indexOf('};', payloadStart);
assert.ok(payloadStart >= 0 && payloadEnd > payloadStart, 'unable to isolate semantic WebView write payload');
const payload = ui.slice(payloadStart, payloadEnd);
for (const forbidden of [
  'pageNumber','pageIdentifier','offset','byteSize','bitStart','bitEnd',
  'rawHex','encodedBytes','burnCommand'
]) {
  assert.ok(!payload.includes(forbidden), 'WebView semantic payload leaked physical authority: ' + forbidden);
}

// One bridge accepts the semantic batch. No raw-address arguments are exposed.
const writeBridge = activity.match(/@JavascriptInterface\s+fun\s+writeTuningChangesJson\s*\(([^)]*)\)/s);
assert.ok(writeBridge, 'normal semantic write bridge missing');
assert.strictEqual(writeBridge[1].replace(/\s+/g, ' ').trim(), 'json: String',
  'normal write bridge must accept only one semantic envelope');

// Normal RAM write must re-read the exact baseline, transmit bounded C, exact R-readback,
// verify the complete expected TuneSnapshot, then mark dirty pages.
const writePath = region(manager, 'internal fun queueTuningWriteBatchJson', 'internal fun queueTuningBurn');
for (const marker of [
  'readNormalTuningSnapshotOnOwner',
  'plan.baselineTuneFingerprint',
  'TuningWriteProtocol.writeBody',
  'TuningWriteProtocol.readBackBody',
  'TuningWriteProtocol.requireWriteAck',
  'TuningWriteProtocol.requireReadBack',
  'plan.expectedSnapshot.compare(observed)',
  'tuningDirtyPageNumbers.addAll',
  'ram_applied_verified'
]) assert.ok(writePath.includes(marker), 'normal RAM write lost invariant: ' + marker);

assert.ok(writePath.includes('possibleTransmission'), 'normal RAM write must track possible transmission');
assert.ok(writePath.includes('write_uncertain'), 'post-transmission failure must become uncertain');
assert.ok(!writePath.includes('queueTuningBurn('), 'RAM write path must never auto-burn');

// Burn remains a separate explicit operation with current INI page authority, flash evidence,
// post-Burn complete reread and dirty-page clearing only after verification.
const burnPath = region(manager, 'internal fun queueTuningBurn', 'internal fun tuningWriteStatusJson');
for (const marker of [
  'explicitlyDirty',
  'TuneBurnProtocol.buildBody',
  'TuneBurnProtocol.requireAcceptedAck',
  'TuneBurnEvidencePolicy.requireReadyForBurn',
  'TuneBurnEvidencePolicy.observeRequest',
  'TuneBurnEvidencePolicy.observeCompletion',
  'readNormalTuningSnapshotOnOwner',
  'needFlashBurn',
  'flashWritePending',
  'tuningDirtyPageNumbers.remove',
  'saved'
]) assert.ok(burnPath.includes(marker), 'explicit Burn lost invariant: ' + marker);

assert.ok(burnPath.includes('save_uncertain'), 'possible Burn transmission failure must become uncertain');

// Ordinary stale-data warnings must recognize normal tuner exclusive ownership.
const freshness = region(manager, 'fun transportFreshnessJson()', 'private fun setState');
assert.ok(freshness.includes('exclusiveOperationOwner.get()'), 'neutral production exclusive ownership missing from freshness');
assert.ok(freshness.includes('operationOwner?.diagnosticName ?: ""'), 'normal tuner freshness owner must come from the neutral owner');
assert.ok(manager.includes('UsbOperationOwner.TUNER'), 'normal Read/Write/Burn must acquire the neutral TUNER owner');
assert.ok(!freshness.includes('t3ProofRunning') && !freshness.includes('w4WriteRunning') && !freshness.includes('t6BurnRunning'),
  'retired proof flags must not participate in production freshness ownership');

// Pending UI state and verified native state remain distinct; Burn is blocked while edits are only staged.
for (const marker of [
  'pendingChangeCount()',
  'WRITE ALL PENDING EDITS TO ECU RAM',
  'Save / Burn ECU',
  'write pending edits first',
  'settleQueuedSemanticWriteAndReload',
  'clearQueuedDraftEntries'
]) assert.ok(ui.includes(marker), 'normal tuner pending/verified-state contract missing: ' + marker);


// Production path must no longer depend on proof-era T3/T6 implementations for these primitives.
assert.ok(manager.includes('AuthoritativeTuneSnapshotReader(selectedProfile, context)'),
  'normal tuner must use the neutral authoritative TuneSnapshot reader');
assert.ok(!region(manager, 'private fun readNormalTuningSnapshotOnOwner', 'internal fun queueTuningRead').includes('T3SynchronousTuneSnapshotReader'),
  'normal tuner snapshot read still depends on T3 proof reader');
assert.ok(engine.includes('EcuCommandResponseCodec.decodeBody(body)'),
  'normal C/R response handling must use neutral ECU response decoding');
assert.ok(!engine.includes('T3PilotRamProtocol.decodeResponseBody'),
  'normal write protocol still depends on T3 pilot response decoding');
assert.ok(snapshotReader.includes('class AuthoritativeTuneSnapshotReader'),
  'neutral authoritative TuneSnapshot reader source missing');
assert.ok(responseCodec.includes('object EcuCommandResponseCodec'),
  'neutral ECU response codec source missing');
assert.ok(burn.includes('object TuneBurnProtocol') && burn.includes('class TuneFlashStatusResolver') &&
  burn.includes('object TuneBurnEvidencePolicy'),
  'neutral production Burn primitives missing');
assert.ok(scalarCodec.includes('object TuningScalarCodec'),
  'production scalar codec must live outside the simulation core');

// The obsolete T0 single-scalar connection proof must not run before the authoritative full tune read.
assert.ok(!manager.includes('runTuneReadProof'), 'connection path still runs the redundant T0 scalar proof');
assert.ok(!manager.includes('tuneReadProof'), 'T0 scalar proof diagnostics/state still exist');
assert.ok(!manager.includes('T0_TUNE_READ_SCALAR'), 'T0 scalar proof target constant still exists');
const preflightAt = manager.indexOf('runFramedProtocolPreflight(selectedProfile, generation)');
const snapshotAt = manager.indexOf('runTuneSnapshotRead(selectedProfile, generation)', preflightAt);
const firstOutputAt = manager.indexOf('val firstBlock = readOutputBlock(selectedProfile)', snapshotAt);
assert.ok(preflightAt >= 0 && snapshotAt > preflightAt && firstOutputAt > snapshotAt,
  'connection sequence must be framed preflight → complete TuneSnapshot → first output block');

console.log('production tuner authority/safety contract passed');
