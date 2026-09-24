const fs = require('fs');
const assert = require('assert');

const ui = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js', 'utf8');
const activity = fs.readFileSync('app/src/main/java/com/buttonbox/ble/DashboardLabActivity.kt', 'utf8');
const manager = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt', 'utf8');
const preview = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningSemanticPreview.kt', 'utf8');
const writeEngine = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningWriteEngine.kt', 'utf8');
const profile = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbTunerStudioProfile.kt', 'utf8');
const workspace = fs.readFileSync('app/src/main/java/com/buttonbox/ble/TuningWorkspace.kt', 'utf8');

for (const marker of [
  "{mode:'tables',label:'Tables'}",
  "{mode:'curves',label:'Curves'}",
  'TUNER_UI_SIDEQUEST_TABLES_V1',
  'responsive-tables-v1',
  'renderTablesMode',
  'renderArrayGridInto',
  'prepareArraySurface',
  'getTuningArrayDetailJson',
  'previewTuningArrayCellJson',
  'Apply edit',
  'WRITE ALL PENDING EDITS TO ECU RAM',
  'epicdash.t5.arrayDraft.v1:',
  'workspace.tables',
  'workspace.curves',
  'targetArray',
  'surface:',
  'getSemanticArrayDetail',
  'arrayXDetail',
  'arrayYDetail',
  "y * xCount + x",
  'function displayUnit(item)',
  "raw.includes('bitStringValue(')",
  'result?.noOp === true',
  'result.writeEligible !== true'
]) assert.ok(ui.includes(marker), 'missing T5 UI marker: ' + marker);

assert.ok(activity.includes('fun previewTuningArrayCellJson(name: String, cellIndex: Int, requestedValue: Double): String'),
  'LAB bridge must expose semantic array name + cell index + engineering value only');
assert.ok(manager.includes('TuningSemanticPreviewBuilder.preview('), 'manager must own semantic array preview');
assert.ok(!manager.includes('.put("t5ArraySimulation"'), 'proof-era T5 simulation telemetry must be absent');
assert.ok(manager.includes('.put("tuneTables", selectedProfile?.tuneTables?.size ?: 0)'), 'diagnostics must export table count');
assert.ok(manager.includes('.put("tuneCurves", selectedProfile?.tuneCurves?.size ?: 0)'), 'diagnostics must export curve count');
assert.ok(profile.includes('pendingCurve?.yBins?.add(value)'), 'curve parser must preserve repeated yBins series');
assert.ok(workspace.includes('definition.yBins.size != 1'), 'first T5 slice must fail closed on multi-series curves');
assert.ok(workspace.includes('dims[0] == x.elementCount && dims[1] == y.elementCount'),
  'logical tables must enforce TunerStudio [columns x rows] orientation');

for (const marker of [
  'TuningWritePlanner.resolveForPreview(',
  '.put("noOp", noOp)',
  '.put("writeEligible", !noOp)'
]) assert.ok(preview.includes(marker), 'missing shared array preview contract: ' + marker);

assert.ok(writeEngine.includes('TuningWriteKind.ARRAY_CELL -> resolveArrayCell(profile, snapshot, request)'),
  'preview and execution must share the production array resolver');

for (const forbidden of [
  '.put("offset"',
  '.put("pageNumber"',
  '.put("rawHex"',
  'openW4VehicleRamWrite',
  'T3BoundTransportPort',
  'UsbEcuManager'
]) assert.ok(!preview.includes(forbidden), 'array preview leaks or owns forbidden transport/storage surface: ' + forbidden);

const start = ui.indexOf('function stageArrayCellEdit()');
const end = ui.indexOf('function renderChanges()', start);
assert.ok(start >= 0 && end > start, 'unable to isolate array preview UI function');
const block = ui.slice(start, end);
assert.ok(block.includes('previewTuningArrayCellJson'), 'array UI must use semantic native preview bridge');
assert.ok(!block.includes('openW4VehicleRamWrite'), 'array UI must not expose W4 scalar write path');
assert.ok(!block.includes('pageNumber') && !block.includes('offset') && !block.includes('rawHex'),
  'array UI must not own storage metadata');
assert.ok(ui.includes("grid.appendChild(axisCell(valueWithUnit(xValues[x], arrayXDetail), () => selectArrayColumn(x)"),
  'table grid must render semantic X bin values and expose column selection');
assert.ok(ui.includes("grid.appendChild(axisCell(valueWithUnit(yValues[y], arrayYDetail), () => selectArrayRow(y)"),
  'table grid must render semantic Y bin values and expose row selection');
assert.ok(ui.includes("grid.id = 't4twInlineTableGrid'"), 'full-area table grid is missing');
assert.ok(ui.includes("queueSemanticWrites(changes, arrayStatus)"), 'full-area table edits must use the accepted semantic write queue');
assert.ok(ui.includes("Validated ' + staged + ' changed cell(s) • sending to ECU RAM for exact verification…'"), 'full-area table/curve edits lost live verified RAM wording');
assert.ok(!ui.includes('data-mode="arrays"'), 'superseded combined array browse mode returned');

for (const forbidden of [
  'simulateTuningArrayCellJson',
  'LOCAL_ARRAY_SIMULATION_ONLY',
  'NOT_TRANSMITTED',
  "result.status !== 'simulated'"
]) assert.ok(!ui.includes(forbidden), 'Tuner UI retained historical array simulation marker: ' + forbidden);

assert.ok(ui.includes("if (raw.startsWith('{') || raw.endsWith('}') || raw.includes('bitStringValue(')) return '';"),
  'unresolved TunerStudio dynamic units must fail closed instead of rendering expression text');

console.log('t5_tuning_array_characterization_test: PASS');