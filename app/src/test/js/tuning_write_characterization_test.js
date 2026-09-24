'use strict';

const fs = require('fs');
const assert = require('assert');

const read = path => fs.readFileSync(path, 'utf8');
const ui = read('app/src/main/assets/t4_tuning_workspace.js');
const activity = read('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt');
const manager = read('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt');
const engine = read('app/src/main/java/com/buttonbox/ble/TuningWriteEngine.kt');
const profile = read('app/src/main/java/com/buttonbox/ble/UsbTunerStudioProfile.kt');
const workspaceModel = read('app/src/main/java/com/buttonbox/ble/TuningWorkspace.kt');

for (const marker of [
  'Read ECU',
  'Write this edit to ECU RAM',
  'Write this cell to ECU RAM',
  'WRITE ALL PENDING EDITS TO ECU RAM',
  'Save / Burn ECU',
  'Apply edit',
  'Pending changes',
  'write pending edits first',
  'writeTuningChangesJson',
  'readTuningFromEcuJson',
  'burnTuningChangesJson',
  'getTuningWriteStatusJson',
  "kind:'scalar'",
  "kind:'arrayCell'",
  "kind:'bitField'",
  'profileFingerprint',
  'tuneFingerprint',
  'Select row',
  'Select column',
  'Rectangle / range to next cell',
  'Tap target cell…',
  'Select all',
  'Apply to selection',
  'Shift-click = rectangle',
  'tableRectIndices',
  'tableRowIndices',
  'tableColumnIndices',
  'selectionOperationValue',
  'stageArraySelectionEdit',
  'isEncodedArrayNoOp',
  'button.dataset.mode = spec.mode',
  "{mode:'settings',label:'Parameters'}",
  'Search hierarchy / feature',
  'renderSettingsMode',
  'TUNER_UI_SIDEQUEST_RESPONSIVE_HIERARCHY_V1',
  'TUNER_UI_SIDEQUEST_PARAMETERS_V1',
  'TUNER_UI_SIDEQUEST_TABLES_V1',
  'TUNER_UI_SIDEQUEST_CURVES_V1',
  'responsive-curves-v1',
  'renderCurvesMode',
  'renderCurveGraph',
  'curveLoadedIds',
  'curveActiveId',
  'currentCurveDraftChanges',
  'writeCurrentCurveDrafts',
  'responsive-tables-v1',
  "{mode:'tables',label:'Tables'}",
  "{mode:'curves',label:'Curves'}",
  'renderTablesMode',
  'prepareArraySurface',
  'renderArrayGridInto',
  'fitInlineTable',
  'currentTableDraftChanges',
  'writeCurrentTableDrafts',
  'responsive-parameters-v1',
  't4tw-system-rail',
  't4tw-inline-parameters',
  'TUNER_UI_VISUAL_PARITY_V1',
  't4tw-topchrome',
  't4tw-content-modes',
  't4tw-telemetry',
  't4tw-hierarchy-selectors',
  'hierarchySelect',
  'renderSettingsDialogInto',
  'ENUM / BIT • editable',
  'stageBitFieldEdit',
  'currentBitWrite',
  'bitDraftKey',
  'ECU command not exposed',
  'conditionStateOf',
  'conditionVisible',
  'conditionUsable',
  'unsupportedConditionExpressions',
  'collectDialogRouteTargets',
  'buildIniGroupedRoutes',
  'renderGroupedScalars',
  'renderGroupedSurfaces',
  'appendUngroupedHeader',
  'Readable scalars',
  'Readable tables / curves',
  'not referenced by the parsed INI menu/dialog tree',
  'rebuildWorkspaceIndex',
  'groupedRoutesCache',
  'workspaceIndex?.scalars.get',
  'groupedRouteBlueprintCache',
  'document.createDocumentFragment',
  't4twImportIni',
  'epicdash-usb-ini-import-state',
  'getUsbIniImportStatusJson'
]) assert.ok(ui.includes(marker), 'missing normal tuner / INI-settings UX marker: ' + marker);
assert.ok(!ui.includes('conditions shown, not yet interpreted'), 'display-only INI condition wording returned');
assert.ok(!ui.includes('function hierarchyColumn('), 'legacy three-column hierarchy renderer returned');
assert.ok(ui.includes("{mode:'settings',label:'Parameters'}"), 'Parameters label is not active for the hierarchy tuning surface');
assert.ok(ui.includes("renderSettingsDialogInto(body, dialog.id, [], 0, conditionUsable(selectedFeature))"), 'Parameters feature is not rendered in the Tuner content area');
assert.ok(ui.includes("write.onclick = writeAllDrafts"), 'inline Parameters surface lost verified RAM submission path');
assert.ok(ui.includes("queueSemanticWrites(changes, arrayStatus)"), 'Table/Curve workspace bypassed shared semantic write queue');
assert.ok(ui.includes('isEncodedArrayNoOp'), 'table/curve edit flow lost encoded no-op detection');
assert.ok(ui.includes("queueSemanticWrites([change], editorStatus)"), 'scalar Parameters edits no longer write live through verified RAM flow');
assert.ok(ui.includes("queueSemanticWrites([change], null)"), 'bit/enum Parameters edits no longer write live through verified RAM flow');
assert.ok(!ui.includes("data-mode=\"arrays\""), 'superseded combined Tables / Curves mode returned');
assert.ok(!ui.includes('data-mode="scalars"'), 'Scalars returned as a normal Tuner content mode');
assert.ok(ui.includes("topBurnButton.onclick = saveTuningToEcu"), 'locked Burn control is not bound to authoritative Burn');
assert.ok(ui.includes('burnDirtySemanticKeys'), 'Burn N lost semantic dirty-item authority');
assert.ok(ui.includes('captureSemanticBurnBaselines(changes)'), 'Burn N no longer captures burned semantic baselines before verified writes');
assert.ok(ui.includes('reconcileSemanticBurnDirty(verifiedChanges)'), 'Burn N no longer reconciles verified RAM values against burned baselines');
assert.ok(ui.includes("burnSemanticKey(change)"), 'Burn N semantic-key grouping is missing');
assert.ok(!ui.includes("topBurnLabel.textContent = dirty > 0 ? ('Burn ' + dirty)"), 'Burn N regressed to dirty-page counting');
assert.ok(ui.includes("window.EpicDashTunerTelemetrySnapshot"), 'Tuner telemetry presentation bridge is not consumed');
assert.ok(ui.includes("for (const name of ['single','multi'])"), 'Curves workspace lost Single/Multi editing modes');
assert.ok(ui.includes("prepareArraySurface(item, true)"), 'Curves workspace no longer permits condition-blocked curves to remain visible read-only');
assert.ok(ui.includes("curveActiveId"), 'Curves workspace no longer enforces one active editable curve');

for (const marker of [
  'fun readTuningFromEcuJson(): String',
  'fun writeTuningChangesJson(json: String): String',
  'fun burnTuningChangesJson(): String',
  'fun getTuningWriteStatusJson(): String',
  'queueTuningRead',
  'queueTuningWriteBatchJson',
  'queueTuningBurn',
  'Dispatchers.Default',
  'usbProfilePersistenceGeneration',
  'restoreUsbProfileAsync',
  'persistAuthoritativeIni',
  'withContext(Dispatchers.IO)',
  'withContext(Dispatchers.Default)',
  'usbIniImportStatusJson',
  'updateUsbIniImportStatus',
  'channelCatalogFor'
]) assert.ok(activity.includes(marker), 'missing normal tuner bridge marker: ' + marker);
assert.ok(activity.includes('readCappedIniSource(uri)'), 'INI picker must use capped reader');
assert.ok(!activity.includes('.readText().take(8_000_000)'), 'oversized INI must never be silently truncated');
const iniImportStart = activity.indexOf('private val openUsbIniLauncher');
const iniImportEnd = activity.indexOf('private val openMslFileLauncher', iniImportStart);
assert.ok(iniImportStart >= 0 && iniImportEnd > iniImportStart, 'unable to isolate INI import transaction');
const iniImportPath = activity.slice(iniImportStart, iniImportEnd);
assert.ok(iniImportPath.indexOf('TunerPermanentProjectStore.persistAuthoritativeIni') < iniImportPath.indexOf('applyUsbProfile(prepared.first, prepared.second, announce = false)'), 'exact INI persistence must precede live profile apply');

const writeBridge = activity.match(/@JavascriptInterface\s+fun\s+writeTuningChangesJson\s*\(([^)]*)\)/s);
assert.ok(writeBridge, 'unable to parse semantic tuning write bridge');
assert.strictEqual(
  writeBridge[1].replace(/\s+/g, ' ').trim(),
  'json: String',
  'normal tuner write bridge must accept one semantic JSON payload only'
);

for (const marker of [
  'SemanticTuningWriteEnvelope',
  'TuningWritePlanner',
  'TuningWriteProtocol',
  'queueTuningWriteBatchJson',
  'readNormalTuningSnapshotOnOwner',
  'AuthoritativeTuneSnapshotReader',
  'EcuCommandResponseCodec',
  'ram_applied_verified',
  'write_uncertain',
  'queueTuningBurn',
  'TuneBurnProtocol.buildBody',
  'TuneBurnEvidencePolicy.observeRequest',
  'TuneBurnEvidencePolicy.observeCompletion',
  'save_not_needed',
  'verifying_saved_tune',
  'tuningReadWrite',
  'tuningWorkspaceCacheKey',
  'invalidateTuningWorkspaceCache',
  'lastReadElapsedMs',
  'lastWorkspaceBuildElapsedMs',
  'lastWorkspaceJsonElapsedMs',
  'workspaceCacheLastHit',
  'workspaceCacheReuseCount',
  'tuningSemanticCacheKey',
  'semanticIdentityUnchanged'
]) assert.ok(manager.includes(marker) || engine.includes(marker), 'missing normal tuner native marker: ' + marker);

const workspaceJsonStart = manager.indexOf('internal fun tuningWorkspaceJson');
const workspaceJsonEnd = manager.indexOf('/**\n     * T5 read-only semantic array detail', workspaceJsonStart);
assert.ok(workspaceJsonStart >= 0 && workspaceJsonEnd > workspaceJsonStart, 'unable to isolate workspace JSON path');
const workspaceJsonPath = manager.slice(workspaceJsonStart, workspaceJsonEnd);
assert.ok(workspaceJsonPath.includes('val cacheKey = tuningSemanticCacheKey(snapshot, generation)'),
  'workspace cache must use semantic tune identity');
assert.ok(workspaceJsonPath.includes('.put("capturedAtEpochMs", snapshot.capturedAtEpochMs)'),
  'workspace reuse must refresh the authoritative capture timestamp');
assert.ok(!workspaceJsonPath.includes('append(snapshot.capturedAtEpochMs)'),
  'snapshot timestamp must not force semantic workspace rebuild');

const adoptStart = manager.indexOf('private fun adoptVerifiedTuningSnapshot');
const adoptEnd = manager.indexOf('fun start()', adoptStart);
assert.ok(adoptStart >= 0 && adoptEnd > adoptStart, 'unable to isolate verified snapshot adoption');
const adoptPath = manager.slice(adoptStart, adoptEnd);
assert.ok(adoptPath.includes('if (!semanticIdentityUnchanged) invalidateTuningWorkspaceCache()'),
  'unchanged verified tune must retain semantic workspace caches');

assert.ok(profile.includes('parseMeasured'), 'INI parser stage timing entry point missing');
assert.ok(profile.includes('line.regionMatches'), 'INI parser direct key matching optimization missing');
assert.ok(profile.includes('bitOptionCache'), 'INI bit-option parser cache missing');
assert.ok(profile.includes('parseExplicitBitOption'), 'INI bit-option manual explicit parser missing');
assert.ok(activity.includes('parserBitOptionsElapsedMs'), 'INI parser bit-option timing missing from diagnostics');
assert.ok(!profile.includes("Regex(\"^${Regex.escape(key)}"), 'INI parser reintroduced per-key regex compilation');
assert.ok(activity.includes('parserScanElapsedMs'), 'INI parser scan timing missing from diagnostics');
assert.ok(activity.includes('parserFinalizeElapsedMs'), 'INI parser finalize timing missing from diagnostics');
assert.ok(profile.includes('scanBreakdownMs'), 'INI parser scan-stage breakdown missing');
assert.ok(profile.includes('scanPreprocessNs'), 'INI parser preprocess profiling missing');
assert.ok(profile.includes('scanDialogNs'), 'INI parser dialog profiling missing');
assert.ok(profile.includes('scanConstantsNs'), 'INI parser constants profiling missing');
assert.ok(profile.includes('scanMenuNs'), 'INI parser menu profiling missing');
assert.ok(profile.includes('scanTableNs'), 'INI parser table profiling missing');
assert.ok(profile.includes('scanCurveNs'), 'INI parser curve profiling missing');
assert.ok(profile.includes('scanOutputNs'), 'INI parser output-channel profiling missing');
assert.ok(activity.includes('parserScanBreakdownMs'), 'INI parser breakdown not exposed in diagnostics');

for (const marker of [
  'UsbTuneBitField',
  'UsbTuneMenuItem',
  'UsbTuneDialog',
  'tuneBitsRegex',
  'groupChildMenu',
  'commandButton',
  'PendingTuneBitField',
  'pendingTuneBitFields'
]) assert.ok(profile.includes(marker), 'missing INI settings parser/model marker: ' + marker);

for (const marker of [
  'TuningWorkspaceBitField',
  'totalProfileBitFields',
  'menuItems',
  'dialogs'
]) assert.ok(workspaceModel.includes(marker), 'missing INI settings workspace marker: ' + marker);

for (const marker of [
  'TuningWriteKind',
  'SCALAR',
  'ARRAY_CELL',
  'BIT_FIELD',
  'resolveBitField',
  'equivalentBitFieldDefinition',
  'TuningScalarCodec.encode',
  'expectedSnapshot',
  'duplicate semantic targets',
  'overlap',
  "rangeBody('C'.code.toByte()",
  "rangeBody('R'.code.toByte()",
  'requireWriteAck',
  'requireReadBack'
]) assert.ok(engine.includes(marker), 'missing generic tuner write-engine marker: ' + marker);

// WebView owns semantic targets only. Native code owns physical storage and protocol construction.
const payloadStart = ui.indexOf('const payload = {');
const payloadEnd = ui.indexOf('};', payloadStart);
assert.ok(payloadStart >= 0 && payloadEnd > payloadStart, 'unable to isolate semantic write payload');
const payloadBlock = ui.slice(payloadStart, payloadEnd);
for (const forbidden of ['pageNumber', 'pageIdentifier', 'offset', 'byteSize', 'bitStart', 'bitEnd', 'rawHex', 'encodedBytes', 'burnCommand']) {
  assert.ok(!payloadBlock.includes(forbidden), 'semantic write payload leaked native storage field: ' + forbidden);
}

const managerStart = manager.indexOf('internal fun queueTuningWriteBatchJson');
const managerEnd = manager.indexOf('internal fun queueTuningBurn', managerStart);
assert.ok(managerStart >= 0 && managerEnd > managerStart, 'unable to isolate normal RAM write manager path');
const managerWrite = manager.slice(managerStart, managerEnd);
assert.ok(!managerWrite.includes('safety.rpm'), 'normal live tuning incorrectly inherited W4 stopped-engine restriction');
assert.ok(!managerWrite.includes('restoreW4VehicleRam'), 'normal live tuning incorrectly auto-restores every edit');
assert.ok(managerWrite.includes('TuningWriteProtocol.writeBody'), 'normal RAM write does not use bounded generic C codec');
assert.ok(managerWrite.includes('TuningWriteProtocol.readBackBody'), 'normal RAM write does not exact-read back written ranges');
assert.ok(managerWrite.includes('plan.expectedSnapshot.compare(observed)'), 'normal RAM write lacks full TuneSnapshot verification');

const freshnessStart = manager.indexOf('fun transportFreshnessJson()');
const freshnessEnd = manager.indexOf('private fun setState', freshnessStart);
assert.ok(freshnessStart >= 0 && freshnessEnd > freshnessStart, 'unable to isolate USB freshness payload');
const freshnessBlock = manager.slice(freshnessStart, freshnessEnd);
assert.ok(freshnessBlock.includes('exclusiveOperationOwner.get()'),
  'normal tuner USB ownership must suppress dashboard stale-data warnings through the neutral operation owner');
assert.ok(freshnessBlock.includes('operationOwner?.diagnosticName ?: ""'),
  'normal tuner USB ownership must be identified through the neutral operation owner diagnostics');
assert.ok(manager.includes('UsbOperationOwner.TUNER'),
  'normal Read/Write/Burn must acquire the neutral TUNER operation owner');
assert.ok(!freshnessBlock.includes('t3ProofRunning') && !freshnessBlock.includes('w4WriteRunning') && !freshnessBlock.includes('t6BurnRunning'),
  'retired proof operation flags must not participate in transport freshness');

assert.ok(!ui.includes(">Simulate<"), 'normal tuner UI must not expose the legacy Simulate button');
assert.ok(!ui.includes(">Simulate cell<"), 'normal tuner table UI must not expose the legacy Simulate cell button');
assert.ok(ui.includes("pendingChangeCount()"), 'normal tuner UI must track pending edits explicitly');
assert.ok(ui.includes("const bitCount = Object.keys(bitDraft)"), 'bit/enum edits must participate in the normal pending-change count');
assert.ok(ui.includes("return {kind:'bitField', name:name, requestedValue:Number(d.requestedValue)}"), 'WebView bit writes must stay semantic-only');
const loadWorkspaceStart = ui.indexOf('function loadWorkspace()');
const loadWorkspaceEnd = ui.indexOf("search.addEventListener('input', render);", loadWorkspaceStart);
const loadWorkspaceBody = ui.slice(loadWorkspaceStart, loadWorkspaceEnd);
assert.ok(loadWorkspaceBody.includes('rerenderOpenSettingsDialog();'), 'successful native snapshot refresh must rebase any open Settings dialog');
assert.ok(loadWorkspaceBody.indexOf('rerenderOpenSettingsDialog();') > loadWorkspaceBody.indexOf('rebuildWorkspaceIndex();'), 'open Settings dialog must re-render after workspace indexes adopt the verified snapshot');
assert.ok(ui.includes('queuedSemanticWrite'), 'normal tuner UI must track the exact queued semantic batch until native completion');
assert.ok(ui.includes('clearQueuedDraftEntries'), 'verified writes must consume the exact queued pending edits');
assert.ok(ui.includes('settleQueuedSemanticWriteAndReload'), 'native completion must settle queued pending edits before workspace reload');
assert.ok(ui.includes('WRITE ALL rejected:'), 'write-all bridge rejection must be visible inside the Changes dialog');
assert.ok(activity.includes('tuningWriteBridgeStatusJson'), 'diagnostics must expose the last normal tuner write bridge outcome');
assert.ok(activity.includes('Write bridge rejected:'), 'rejected write bridge calls must be recorded in diagnostics');
assert.ok(activity.includes('Write bridge accepted:'), 'accepted write bridge calls must be recorded in diagnostics');
assert.ok(ui.includes("pending > 0"), 'Save/Burn must be gated while pending edits have not been written to ECU RAM');
assert.ok(!ui.includes("setInterval(() => refreshNativeWriteStatus"),
  'normal tuner UI reintroduced permanent native status polling');

function functionSource(name) {
  const start = ui.indexOf('function ' + name + '(');
  assert.ok(start >= 0, 'unable to locate helper function: ' + name);
  const brace = ui.indexOf('{', start);
  let depth = 0, quote = null, escape = false;
  for (let index = brace; index < ui.length; index++) {
    const ch = ui[index];
    if (quote) {
      if (escape) escape = false;
      else if (ch === '\\') escape = true;
      else if (ch === quote) quote = null;
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') { quote = ch; continue; }
    if (ch === '{') depth++;
    else if (ch === '}') {
      depth--;
      if (depth === 0) return ui.slice(start, index + 1);
    }
  }
  throw new Error('unterminated helper function: ' + name);
}

const helperFactory = new Function(
  [
    functionSource('tableRectIndices'),
    functionSource('tableRowIndices'),
    functionSource('tableColumnIndices'),
    functionSource('linearRangeIndices'),
    functionSource('selectionOperationValue'),
    functionSource('isEncodedArrayNoOp'),
    'return {tableRectIndices,tableRowIndices,tableColumnIndices,linearRangeIndices,selectionOperationValue,isEncodedArrayNoOp};'
  ].join('\n')
);
const helpers = helperFactory();
assert.deepStrictEqual(helpers.tableRectIndices(0, 5, 4, 3), [0,1,4,5]);
assert.deepStrictEqual(helpers.tableRectIndices(6, 11, 4, 3), [6,7,10,11]);
assert.deepStrictEqual(helpers.tableRowIndices(1, 4, 3), [4,5,6,7]);
assert.deepStrictEqual(helpers.tableColumnIndices(2, 4, 3), [2,6,10]);
assert.deepStrictEqual(helpers.linearRangeIndices(4, 1, 8), [1,2,3,4]);
assert.strictEqual(helpers.selectionOperationValue('set', 100, 42), 42);
assert.strictEqual(helpers.selectionOperationValue('add', 100, 5), 105);
assert.strictEqual(helpers.selectionOperationValue('subtract', 100, 5), 95);
assert.ok(Math.abs(helpers.selectionOperationValue('percent', 100, 10) - 110) < 1e-9);
assert.ok(Number.isNaN(helpers.selectionOperationValue('unknown', 100, 5)));
assert.strictEqual(helpers.isEncodedArrayNoOp({status:'ready',noOp:true,writeEligible:false}), true);
assert.strictEqual(helpers.isEncodedArrayNoOp({status:'ready',noOp:false,writeEligible:true}), false);
assert.strictEqual(helpers.isEncodedArrayNoOp({status:'error',reason:'outside profile bounds'}), false);

console.log('normal tuner semantic read/write/save + practical table-edit characterization contract passed');
