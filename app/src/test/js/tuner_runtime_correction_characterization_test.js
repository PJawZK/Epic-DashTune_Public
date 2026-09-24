'use strict';

const assert = require('assert');
const fs = require('fs');

const extension = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_runtime_extension.js','utf8');
const cleanup = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_interaction_cleanup.js','utf8');
const layout = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_ui_layout_completion.js','utf8');
const project = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_project_state.js','utf8');
const offline = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_offline_surfaces.js','utf8');
const telemetry = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_telemetry_controls.js','utf8');
const tableControls = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_table_controls.js','utf8');
const surface = fs.readFileSync('app/src/main/java/com/buttonbox/ble/T4TuningWorkspaceSurface.kt','utf8');
const permanentStore = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TunerPermanentProjectStore.kt','utf8');
const reader = fs.readFileSync('app/src/main/java/com/buttonbox/ble/AuthoritativeTuneSnapshotReader.kt','utf8');
const iniDefinition = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningIniDefinition.kt','utf8');

for (const [name, source] of Object.entries({extension, cleanup, layout, project, offline, telemetry, tableControls})) {
  assert.doesNotThrow(() => new Function(source), name + ' JavaScript does not parse');
}

for (const marker of [
  'TUNER_UI_RUNTIME_CORRECTION_V2',
  "return 'phone-landscape'", "return 'phone-portrait'", "return 'tablet-portrait'", "return 'tablet-landscape'",
  'window.addEventListener(\'orientationchange\'', 'scheduleTunerGeometryRefresh',
  'requestAnimationFrame(() => requestAnimationFrame', 'dialogHasParameterTarget',
  'parameterFeatures = function(category)', '!workspaceSurface(target)',
  'This INI item is a calibration surface, not a Parameter.', 'tunerArrayDetailCache',
  'tunerSurfaceHierarchyCache', 'updateVisibleTableSelection', 'ensurePersistentTunerTelemetry',
  'tunerTelemetryNodeCache', 'renderInlineTable3d', '3D VIEW • drag to rotate • pinch to zoom • view only',
  "data-t4tw-3d-view=\"iso\"", 'bindInlineTable3dGestures', 'pruneDeadInlineAffordances', 'showInlineTable2d(true)'
]) assert.ok(extension.includes(marker), 'missing runtime correction marker: ' + marker);

for (const marker of [
  'TUNER_UI_INTERACTION_CLEANUP_V3', 'applyTunerViewportProfile = function(force = false)',
  'tunerSelectionNodeMap', 'tunerSelectionTouchNeighborhood', 'tunerApplySelectionNodeState',
  't4tw-table-3d-surface', 'tunerPatchCurveManagerLocal', 'tunerPatchCurveHierarchySelector',
  'tunerRefreshCurveLocal', 'tunerChromeLastTelemetrySync', 'ensurePersistentTunerTelemetry()'
]) assert.ok(cleanup.includes(marker), 'missing interaction cleanup marker: ' + marker);

for (const marker of [
  'TUNER_UI_LAYOUT_COMPLETION_V1', '#t4twTopProfile', 'conditionVisible=function(){return true;}',
  'makeConditionNode=function(){return null;}', 't4tw-help-button', 't4tw-help-copy',
  't4tw-condition-disabled', "point.addEventListener('pointerdown'", "table3dPitch=142;table3dYaw=38",
  'grid-template-rows:auto auto', 'margin-top:0!important;padding-top:0!important'
]) assert.ok(layout.includes(marker), 'missing UI layout completion marker: ' + marker);

for (const marker of [
  'TUNER_UI_PROJECT_STATE_V7_LAZY_OFFLINE', '__EPIC_TUNER_PERMANENT_WORKSPACE__', 't4ProjectReadOnlyClone',
  't4ProjectRequestPermanent', 't4ProjectReleasePermanentForLive', 't4ProjectActivatePermanent',
  't4ProjectRestoreExactDrafts', 'SAVED_TUNE_SNAPSHOT', 'SAVED_PROJECT', 'offlineEditable',
  'requestPermanentTunerProject', "window.addEventListener('epicdash:permanent-tuner-project-ready'",
  "document.addEventListener('visibilitychange'", 'EpicDashUsbIniImportStateChanged',
  'Loading saved Tuner project', 'if(!t4ProjectLiveConnected())'
]) assert.ok(project.includes(marker), 'missing lazy native-snapshot project-state marker: ' + marker);
for (const forbidden of [
  't4ProjectSessionFallback', 'function t4ProjectCaptureLive',
  'EpicDashTunerProjectStore', 'saveProject', 'loadProject', 'loadIniDefinition', 't4ProjectValueState',
  't4ProjectHasCompleteValues', 'putArray(', 'getArray(', 'requestIdleCallback', 'offlineWorkspace',
  'offlineArray', 'offlineEditor', 'READ CACHED INI + TUNE', 't4twReadCachedTune', 'persistBundle',
  'localStorage', 'values.json', 'workspace:data'
]) assert.ok(!project.includes(forbidden), 'obsolete/duplicated persistence behavior remains in project state: ' + forbidden);

for (const marker of [
  'TUNER_UI_OFFLINE_SURFACES_V1', 't4OfflineSavedSurfaceMode', 't4OfflineStructureMode',
  'pending_live_validation', 'offlineDraft:true', 't4OfflineSurfaceCanDraft',
  't4OfflineBaseQueueWrites', 'if(!t4ProjectLiveConnected())'
]) assert.ok(offline.includes(marker), 'missing offline Tables/Curves marker: ' + marker);
for (const forbidden of ['previewTuningArrayCellJson', 'writeTuningChangesJson', 'burnTuningChangesJson', 'UsbEcuManager', 'bulkTransfer', 'controlTransfer']) {
  assert.ok(!offline.includes(forbidden), 'offline Tables/Curves layer gained native/transport write authority: ' + forbidden);
}

for (const marker of [
  'TUNER_UI_TELEMETRY_CONTROLS_V1',
  '.t4tw-telemetry{display:flex!important;grid-template-columns:none!important;flex-wrap:nowrap!important',
  "if(p==='phone-portrait')return 4", "if(p==='phone-landscape')return 6", "if(p==='tablet-portrait')return 6",
  'slot.hidden=i>=limit;slot.inert=i>=limit', "slot.addEventListener('pointerdown'", 'now-lastTap<330',
  'Long press opens this editor', 't4GaugeSignal', 'TUNER_UI_1207_MEASURED_CURVE_GEOMETRY', 'ResizeObserver'
]) assert.ok(telemetry.includes(marker), 'missing telemetry interaction marker: ' + marker);

for (const marker of [
  'TUNER_UI_TABLE_CONTROLS_V1', "add('Row',t4SelectRow)", "add('Column',t4SelectColumn)",
  "add('All',t4SelectAll)", "add('Clear',()=>clearArraySelection())", "applyInlineTableSelection('percent',-5)",
  "applyInlineTableSelection('percent',5)", 'function t4FitTable()', 'viewport.getBoundingClientRect()',
  "grid.style.setProperty('--t4tw-inline-cell'"
]) assert.ok(tableControls.includes(marker), 'missing table interaction marker: ' + marker);

assert.ok(!cleanup.includes('render();'), 'interaction cleanup reintroduced complete Tuner renders');
assert.ok(cleanup.includes('scheduleTunerGeometryRefresh(0)'), 'viewport cleanup does not defer geometry-only refresh');
assert.ok(cleanup.includes('surface.innerHTML = buildInlineTable3dSvg()'), '3D cleanup does not isolate surface redraws');
assert.ok(cleanup.includes('renderCurveGraph();'), 'Curve cleanup lost local graph refresh');
assert.ok(layout.includes("table3dYaw=38;table3dPitch=142"), '3D ISO reset did not adopt the proven UXUI camera hemisphere');

for (const source of [extension, cleanup, layout, project, offline, telemetry, tableControls]) {
  for (const forbidden of [
    'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'pageNumber', 'rawHex', 'burnCommand',
    'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson(', 'saveTuningToEcu('
  ]) assert.ok(!source.includes(forbidden), 'Tuner presentation layer gained forbidden native/transport authority: ' + forbidden);
}

for (const marker of [
  'internal object TunerPermanentProjectStore', 'last-tune.snapshot.json', 'AtomicFile(target)',
  'persistVerifiedSnapshot', 'TuneSnapshot.fromBackupJson', 'loadSnapshotForProfile',
  'bootstrapWorkspacePayload', 'bootstrapWorkspaceJson', 'UsbTunerStudioProfileParser.parse', 'TuningIniDefinitionBuilder.build',
  'TuningWorkspaceBuilder.build', 'forceReadOnly', 'SAVED_TUNE_SNAPSHOT',
  'TunerRecoveryBundleStore.mirrorAsync(context)', 'values.json', 'project.json',
  'mainController.compiled-profile.json', 'EpicDashTunerCompiledIni/v1', 'sourceSha256',
  'process_bootstrap_hit', 'compiled_profile_hit', 'bootstrapCacheHits', 'compiledProfileHits',
  'tunerOfflineProjectionDeferred', 'hasLocalProject', 'LocalProjectState', 'localProjectState'
]) assert.ok(permanentStore.includes(marker), 'missing permanent native TuneSnapshot store marker: ' + marker);
for (const forbidden of [
  'primeBootstrapCacheAsync', 'JavascriptInterface', 'UsbEcuManager', 'bulkTransfer', 'controlTransfer', 'queueTuningRead',
  'queueTuningWrite', 'queueTuningBurn', 'putArray(', 'getArray(', 'localStorage'
]) assert.ok(!permanentStore.includes(forbidden), 'permanent project store regained eager/forbidden behavior: ' + forbidden);
assert.ok(reader.includes('TunerPermanentProjectStore.persistVerifiedSnapshot(snapshot)'), 'authoritative normal read does not commit the native TuneSnapshot');
assert.ok(reader.indexOf('require(snapshot.totalBytes == readPlan.totalBytes)') < reader.indexOf('persistVerifiedSnapshot(snapshot)'), 'snapshot must be complete before permanent commit');

for (const marker of [
  'object TuningIniDefinitionBuilder', 'INI_STRUCTURE', 'INI_DEFINITION', 'definitionOnly',
  'profile.tuneProfileFingerprint()', 'Awaiting a complete ECU read for current values',
  'profile.tuneMenuItems', 'profile.tuneDialogs', 'profile.tuneScalars', 'profile.tuneArrays',
  'profile.tuneTables', 'profile.tuneCurves', 'profile.tuneBitFields', '.put("values", JSONArray())'
]) assert.ok(iniDefinition.includes(marker), 'missing INI-definition projection marker: ' + marker);
for (const forbidden of [
  'bulkTransfer', 'controlTransfer', 'queueTuningRead', 'queueTuningWrite', 'queueTuningBurn',
  'TuneSnapshot.create', 'TuneSnapshot(', 'latestTuneSnapshot'
]) {
  assert.ok(!iniDefinition.includes(forbidden), 'INI structural projector gained live ECU/value authority: ' + forbidden);
}

for (const marker of [
  't4_tuning_workspace_runtime_extension.js', 't4_tuning_workspace_interaction_cleanup.js',
  't4_tuning_workspace_ui_layout_completion.js', 't4_tuning_workspace_snapshot_array_values.js',
  't4_tuning_workspace_connection_refresh.js', 't4_tuning_workspace_project_state.js',
  't4_tuning_workspace_offline_surfaces.js', 't4_tuning_workspace_telemetry_controls.js', 't4_tuning_workspace_table_controls.js',
  'TunerPermanentProjectStore', 'bootstrapWorkspacePayload', '__EPIC_TUNER_PERMANENT_WORKSPACE__',
  'base.lastIndexOf(CLOSURE_END)', 'layers.forEach', "status:\'deferred\'", 'requestPermanentProject',
  'loadPermanentProjectAsync', "epicdash:permanent-tuner-project-ready", 'installCombinedScript(view, token)',
  'installTokens', 'installStillCurrent', 'tunerPermanentProjectDeferredAtInstall'
]) assert.ok(surface.includes(marker), 'Tuner surface is missing lazy native-project marker: ' + marker);
for(const forbidden of [
  'T4TuningProjectStoreBridge','EpicDashTunerProjectStore','T4TuningOfflineCacheBridge','t4_tuning_workspace_offline_editor.js',
  'bridgeReloadRequested', 'view.reload()', 'hasLocalProject(context)', 'addJavascriptInterface(TunerRecoveryBridge'
]) assert.ok(!surface.includes(forbidden),'obsolete project/cache/recovery reload behavior is still installed: '+forbidden);
assert.ok(surface.includes('if (closureIndex < 0) return@runCatching base'), 'Tuner surface does not fail safely when closure injection marker is unavailable');
assert.ok(surface.indexOf('EXTENSION_ASSET') < surface.indexOf('CLEANUP_ASSET'), 'runtime correction must remain before interaction cleanup');
assert.ok(surface.indexOf('CLEANUP_ASSET') < surface.indexOf('LAYOUT_ASSET'), 'layout completion must run after interaction cleanup');
assert.ok(surface.indexOf('SNAPSHOT_ARRAY_VALUES_ASSET') < surface.indexOf('PROJECT_STATE_ASSET'), 'embedded TuneSnapshot values must exist before permanent project presentation');
assert.ok(surface.indexOf('CONNECTION_REFRESH_ASSET') < surface.indexOf('PROJECT_STATE_ASSET'), 'connection detection must exist before project-state lifecycle logic');
assert.ok(surface.indexOf('PROJECT_STATE_ASSET') < surface.indexOf('OFFLINE_SURFACES_ASSET'), 'offline drafting must run after permanent project ownership');
assert.ok(surface.indexOf('OFFLINE_SURFACES_ASSET') < surface.indexOf('TELEMETRY_ASSET'), 'offline drafting must install before later presentation controls');
assert.ok(surface.indexOf('TELEMETRY_ASSET') < surface.indexOf('TABLE_CONTROLS_ASSET'), 'table controls must be the final Tuner completion layer');
const installStart = surface.indexOf('private fun installCombinedScript');
const installEnd = surface.indexOf('private fun tryRegisterCandidateDelivery', installStart);
const installBody = surface.slice(installStart, installEnd);
assert.ok(!installBody.includes('loadPermanentProjectAsync(view, token)'), 'surface install must not eagerly project the permanent project');
assert.ok(surface.indexOf('fun requestPermanentProject(view: WebView?): Boolean') < surface.indexOf('private fun loadPermanentProjectAsync(view: WebView, token: Long)'), 'explicit offline request must own permanent project projection');
const combinedStart = surface.indexOf('private fun buildCombinedScript');
const combinedEnd = surface.indexOf('}.getOrNull()', combinedStart);
const combined = surface.slice(combinedStart, combinedEnd);
assert.ok(!combined.includes('bootstrapWorkspacePayload'), 'combined-script construction must not block on permanent project projection');
assert.ok(!combined.includes('bootstrapWorkspaceJson'), 'combined-script construction must not decode the permanent project');

console.log('Tuner runtime/UI completion characterization passed');
