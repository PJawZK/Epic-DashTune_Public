'use strict';

const assert = require('assert');
const fs = require('fs');

const tuner = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js','utf8');

for (const marker of [
  "let hierarchySystem = ''",
  "let hierarchyCategory = ''",
  "let hierarchyFeatureId = ''",
  "HIERARCHY_GENERAL_CATEGORY = 'General'",
  "HIERARCHY_OTHER_SYSTEM = 'Other'",
  'function settingsHierarchy(q)',
  "let hierarchyCacheKey = ''",
  'let hierarchyCache = null',
  'function loadWorkspaceForActivation()',
  'function ensureHierarchySelection(systems)',
  'function hierarchySelect(label, value, options, onChange)',
  "selectors.dataset.uiContract = 'responsive-hierarchy-v1'",
  "const systemSelect = hierarchySelect(",
  "const categorySelect = hierarchySelect(",
  "const featureSelect = hierarchySelect(",
  'function presentationSystemName(rawValue)',
  'function presentationCategoryName(rawSystem, rawGroup)',
  "const PRESENTATION_SYSTEM_ORDER = ['Fuel','Ignition','Idle','Boost','Start','Limiters','Inputs','Outputs','Diagnostics','Advanced']",
  'category.items.push(item)',
  'function parameterFeatures(category)',
  "shell.dataset.uiContract = 'responsive-parameters-v1'",
  "button.className = 't4tw-system-rail-item'",
  "hierarchyFeatureId = String(value || '')",
  'renderSettingsDialogInto(body, dialog.id, [], 0, conditionUsable(selectedFeature))',
  'if (!conditionVisible(item)) continue;',
  'Search hierarchy / feature',
  "summary.textContent =",
  "systems.length + ' System'",
  "categoryCount + ' Categor'",
  "parameterFeatureCount + ' Parameter Feature'"
]) {
  assert.ok(tuner.includes(marker), 'missing hierarchical Tuner marker: ' + marker);
}

const settingsStart = tuner.indexOf('function settingsHierarchy(q)');
const editorStart = tuner.indexOf('function openEditor(item)', settingsStart);
assert.ok(settingsStart >= 0 && editorStart > settingsStart, 'unable to isolate Settings hierarchy implementation');
const hierarchyBlock = tuner.slice(settingsStart, editorStart);

assert.ok(hierarchyBlock.includes('workspace?.menuItems'), 'hierarchy must derive from current INI menuItems');
assert.ok(!hierarchyBlock.includes('Fuel:'), 'hierarchy must not hardcode a Fuel system catalog');
assert.ok(!hierarchyBlock.includes('Ignition:'), 'hierarchy must not hardcode an Ignition system catalog');
assert.ok(!hierarchyBlock.includes('Boost:'), 'hierarchy must not hardcode a Boost system catalog');
assert.ok(!hierarchyBlock.includes('SYSTEM_CATALOG'), 'hierarchy introduced a hardcoded system catalog');
assert.ok(!hierarchyBlock.includes('CATEGORY_CATALOG'), 'hierarchy introduced a hardcoded category catalog');
assert.ok(!tuner.includes('function hierarchyColumn('), 'legacy three-column hierarchy renderer returned');
assert.ok(tuner.includes('t4tw-hierarchy-selectors'), 'responsive hierarchy selector styling missing');
assert.ok(tuner.includes('TUNER_UI_VISUAL_PARITY_V1'), 'locked visual-parity shell marker missing');
assert.ok(tuner.includes('t4tw-topchrome'), 'locked Tuner top chrome missing');
assert.ok(tuner.includes("data-shell-target=\"diagnostics\""), 'Tuner-local Diagnostics navigation missing');
assert.ok(!tuner.includes('data-mode="scalars"'), 'Scalars returned as a normal Tuner content mode');
assert.ok(tuner.includes('function modeStrip()'), 'locked Parameters / Tables / Curves strip missing');
assert.ok(tuner.includes("body.t4tw-tuner-active>#app>footer{display:none!important}"), 'development footer remains visible in normal Tuner');
assert.ok(tuner.includes("return 'Advanced';"), 'non-primary raw INI systems are not consolidated into Advanced presentation');
assert.ok(tuner.includes('TUNER_UI_SIDEQUEST_PARAMETERS_V1'), 'responsive Parameters styling missing');
assert.ok(tuner.includes('.t4tw-system-rail{display:none'), 'tablet-landscape System rail contract missing');
assert.ok(tuner.includes('@media(min-width:900px) and (min-height:600px)'), 'tablet-landscape breakpoint missing');
assert.ok(tuner.includes('.t4tw-parameters-main .t4tw-hierarchy-selectors{grid-template-columns:18px'), 'tablet-landscape hierarchy does not continue from the System rail');
assert.ok(tuner.includes('.t4tw-inline-parameters>.t4tw-settings-panel{grid-template-columns:repeat(2,minmax(0,1fr))'), 'wide Parameters layout is not compact two-column');
assert.ok(tuner.includes('.t4tw-inline-parameters .t4tw-settings-panel:not(.t4tw-settings-root){grid-template-columns:repeat(2,minmax(0,1fr))'), 'locked parameter cards lost paired field geometry');
assert.ok(tuner.includes('@media(max-width:720px)'), 'phone responsive breakpoint missing');

const breadcrumbMarker = [
  'cleanMenuLabel(item.menu) || HIERARCHY_OTHER_SYSTEM',
  'cleanMenuLabel(item.group) || HIERARCHY_GENERAL_CATEGORY',
  'item.title || item.dialogId'
];
const openStart = tuner.indexOf('function openSettingsDialog(item)');
const hierarchyStart = tuner.indexOf('function settingsHierarchy(q)', openStart);
const openBlock = tuner.slice(openStart, hierarchyStart);
for (const marker of breadcrumbMarker) {
  assert.ok(openBlock.includes(marker), 'feature breadcrumb lost hierarchy level: ' + marker);
}

for (const retained of [
  'function renderGroupedScalars(q)',
  'function renderGroupedSurfaces(q, kindFilter = \'\')',
  "viewMode === 'tables'",
  "viewMode === 'curves'",
  "renderTablesMode(q);",
  "renderCurvesMode(q);",
  "renderGroupedScalars(q);"
]) {
  assert.ok(tuner.includes(retained), 'Tuner content mode regressed during responsive UI work: ' + retained);
}

for (const marker of [
  'TUNER_UI_SIDEQUEST_CURVES_V1',
  'function ensureCurveHierarchySelection(systems)',
  "shell.dataset.uiContract='responsive-curves-v1'",
  "const curveSelect=hierarchySelect('Curve'",
  "curveMode = 'multi'",
  'ensureCurveSession(selected.curve, selected.category?.items || [])',
  "for (const name of ['single','multi'])",
  'function curveSeriesForItem(item)',
  'function renderCurveGraph()',
  'function renderCurveManager(container, allCurves)',
  'function selectCurvePoint(index)',
  'function currentCurveDraftChanges()',
  'prepareArraySurface(item, true)',
  'queueSemanticWrites(changes, arrayStatus)',
  "note.textContent=mismatch+' visible curve(s) hidden: axis mismatch'"
]) {
  assert.ok(tuner.includes(marker), 'missing responsive Curves marker: ' + marker);
}

for (const marker of [
  'TUNER_UI_SIDEQUEST_TABLES_V1',
  'function surfaceHierarchy(kind, q)',
  'function ensureTableHierarchySelection(systems)',
  "shell.dataset.uiContract = 'responsive-tables-v1'",
  "const tableSelect = hierarchySelect(",
  "grid.id = 't4twInlineTableGrid'",
  "viewport.id = 't4twInlineTableViewport'",
  'function fitInlineTable()',
  'function setInlineTableZoom(nextZoom)',
  'function currentTableDraftChanges()',
  'prepareArraySurface(selected.table, true)',
  'queueSemanticWrites(changes, arrayStatus)',
  "view3d.disabled = true",
  "view3d.title = '3D table rendering is not implemented in this slice.'"
]) {
  assert.ok(tuner.includes(marker), 'missing responsive Tables marker: ' + marker);
}

assert.ok(!hierarchyBlock.includes('.pageNumber'), 'hierarchy leaked native page authority');
assert.ok(!hierarchyBlock.includes('.offset'), 'hierarchy leaked native offset authority');
assert.ok(!hierarchyBlock.includes('rawHex'), 'hierarchy leaked raw tune bytes');
const tableStart = tuner.indexOf('function surfaceHierarchy(kind, q)');
const tableEnd = tuner.indexOf('function renderGroupedSurfaces(q, kindFilter', tableStart);
assert.ok(tableStart >= 0 && tableEnd > tableStart, 'unable to isolate responsive Tables implementation');
const tableBlock = tuner.slice(tableStart, tableEnd);
assert.ok(!tableBlock.includes('.pageNumber'), 'Tables UI leaked native page authority');
assert.ok(!tableBlock.includes('.offset'), 'Tables UI leaked native offset authority');
assert.ok(!tableBlock.includes('rawHex'), 'Tables UI leaked raw tune bytes');
assert.ok(!tableBlock.includes('Fuel:'), 'Tables UI hardcoded a firmware-specific Fuel taxonomy');
const curveStart = tuner.indexOf('function ensureCurveHierarchySelection(systems)');
const curveEnd = tuner.indexOf('function renderGroupedSurfaces(q, kindFilter', curveStart);
assert.ok(curveStart >= 0 && curveEnd > curveStart, 'unable to isolate responsive Curves implementation');
const curveBlock = tuner.slice(curveStart, curveEnd);
assert.ok(!curveBlock.includes('.pageNumber'), 'Curves UI leaked native page authority');
assert.ok(!curveBlock.includes('.offset'), 'Curves UI leaked native offset authority');
assert.ok(!curveBlock.includes('rawHex'), 'Curves UI leaked raw tune bytes');
assert.ok(!curveBlock.includes('Compare'), 'Multi curve editing regressed to misleading Compare terminology');

assert.ok(tuner.includes("if (hierarchyCache && hierarchyCacheKey === cacheKey) return hierarchyCache;"), 'hierarchy grouping is rebuilt on every navigation click');
assert.ok(tuner.includes("String(workspace.tuneFingerprint || '') === fingerprint"), 'Tuner re-entry does not verify tune identity before reusing workspace');
assert.ok(tuner.includes("setTimeout(loadWorkspaceForActivation, 0)"), 'Tuner page activation still forces full workspace serialization');

for (const marker of [
  'id="t4twSettingsPending"',
  'id="t4twSettingsWriteRam"',
  'Write pending to ECU RAM',
  'settingsWriteRam.onclick = writeAllDrafts',
  'applyRamButton.hidden = false',
  'No pending edits. Change a value and apply it first.'
]) {
  assert.ok(tuner.includes(marker), 'Settings dialog RAM workflow is not explicit: ' + marker);
}

console.log('Tuner hierarchical navigation characterization passed');
