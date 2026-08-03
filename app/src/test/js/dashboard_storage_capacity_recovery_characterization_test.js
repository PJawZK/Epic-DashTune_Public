'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const dashboardPath = process.env.EPICDASH_DASHBOARD_HTML || path.resolve(__dirname, '../../main/assets/dashboard_lab.html');
const html = fs.readFileSync(dashboardPath, 'utf8');

function compact(value) {
  return String(value).replace(/\s+/g, ' ').trim();
}

function functionSource(source, marker) {
  const start = source.indexOf(marker);
  assert.ok(start >= 0, `Missing function marker: ${marker}`);

  let quote = null;
  let escaped = false;
  let parentheses = 0;
  let bodyStart = -1;
  for (let index = start; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '(') parentheses++;
    else if (char === ')') parentheses--;
    else if (char === '{' && parentheses === 0) {
      bodyStart = index;
      break;
    }
  }
  assert.ok(bodyStart > start, `Missing function body: ${marker}`);

  let braces = 0;
  quote = null;
  escaped = false;
  for (let index = bodyStart; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') {
      quote = char;
      continue;
    }
    if (char === '{') braces++;
    else if (char === '}') {
      braces--;
      if (braces === 0) return source.slice(start, index + 1);
    }
  }
  throw new Error(`Unterminated function: ${marker}`);
}

function sourceBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  assert.ok(start >= 0, `Missing source marker: ${startMarker}`);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.ok(end > start, `Missing end marker: ${endMarker}`);
  return source.slice(start, end);
}

const settingsKeyMatch = html.match(/const SETTINGS_KEY\s*=\s*'([^']+)'/);
assert.ok(settingsKeyMatch, 'settings key remains explicit');
const SETTINGS_KEY = settingsKeyMatch[1];
assert.strictEqual(SETTINGS_KEY, 'epicdash-jz-lab-v060');

const safeSettingsSource = functionSource(html, 'function safeSettings');
const saveSettingsSource = functionSource(html, 'function saveSettings');
const initializeLayoutStoreSource = functionSource(html, 'function initializeLayoutStore');
const loadLogicFeaturesSource = functionSource(html, 'function loadLogicFeatures');
const saveMathChannelSource = functionSource(html, 'function saveMathChannel');
const saveWarningRuleSource = functionSource(html, 'function saveWarningRule');
const resetSource = sourceBetween(html, "$('resetLabBtn')", 'window.EpicDashNativeNotice');
const startupSource = sourceBetween(html, 'function safeSettings', 'const UI_DEFAULTS');

assert.ok(compact(safeSettingsSource).includes("try { return JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}'); } catch (_) { return {}; }"),
  'startup read returns an empty object for unavailable or malformed storage');
assert.ok(saveSettingsSource.includes('nextSettings.incidents=incidents.slice(0,20);'),
  'save path caps local incidents at twenty in the transactional snapshot');
assert.ok(compact(saveSettingsSource).includes('return true') && compact(saveSettingsSource).includes('return false'),
  'save path returns explicit persistent success or failure');
assert.ok(compact(startupSource).includes('if (Array.isArray(settings.incidents)) incidents = settings.incidents;'),
  'startup accepts every stored incident without applying the save-time cap');
assert.ok(compact(initializeLayoutStoreSource).includes('try{const layout=normalizeLayout(item);'),
  'saved layouts are normalized individually');
assert.ok(compact(initializeLayoutStoreSource).includes('catch(error){log(`Ignored invalid saved layout: ${error.message}`'),
  'an invalid saved layout is skipped while other layouts continue loading');
assert.ok(loadLogicFeaturesSource.includes('settings.mathChannels') && loadLogicFeaturesSource.includes('settings.customWarningRules'),
  'logic features are loaded from the unified settings object');
assert.ok(saveMathChannelSource.includes('saveSettings()') && saveWarningRuleSource.includes('saveSettings()'),
  'logic editors rely on the central save path');
assert.ok(saveSettingsSource.includes('mathChannels') && saveSettingsSource.includes('customMathChannels') && saveSettingsSource.includes('customWarningRules'),
  'central save copies live custom logic arrays into the unified snapshot');
assert.ok(compact(resetSource).includes('try{localStorage.removeItem(SETTINGS_KEY);}catch(_){}'),
  'reset removes only the unified settings key and suppresses removal failures');

const storageCallMatches = [...html.matchAll(/localStorage\.(getItem|setItem|removeItem)\s*\(\s*([A-Za-z0-9_]+)/g)];
assert.ok(storageCallMatches.length >= 3, 'production contains storage read, write, and reset references');
assert.deepStrictEqual(
  [...new Set(storageCallMatches.map(match => match[1]))].sort(),
  ['getItem', 'removeItem', 'setItem'],
  'production uses only read, write, and reset localStorage operations'
);
assert.ok(
  storageCallMatches.every(match => match[2] === 'SETTINGS_KEY'),
  'every production localStorage operation targets the unified settings key rather than a backup key'
);

function compileFunction(source, name, context) {
  return vm.runInContext(`(() => { ${source}; return ${name}; })()`, context);
}

function makeSafeSettings(storage) {
  const context = vm.createContext({localStorage: storage, SETTINGS_KEY, JSON});
  return compileFunction(safeSettingsSource, 'safeSettings', context);
}

function assertJsonEqual(actual, expected) {
  assert.strictEqual(JSON.stringify(actual), JSON.stringify(expected));
}

assertJsonEqual(makeSafeSettings({getItem: () => '{"answer":42}'})(), {answer: 42});
assertJsonEqual(makeSafeSettings({getItem: () => '{broken'})(), {});
assertJsonEqual(makeSafeSettings({getItem: () => { throw new Error('storage unavailable'); }})(), {});
assertJsonEqual(makeSafeSettings({getItem: () => null})(), {});

class ThresholdStorage {
  constructor(limitBytes = Infinity, initialValue = null) {
    this.limitBytes = limitBytes;
    this.value = initialValue;
    this.calls = [];
  }

  setItem(key, value) {
    const bytes = Buffer.byteLength(value, 'utf8');
    this.calls.push({key, value, bytes});
    if (bytes > this.limitBytes) {
      const error = new Error('Quota exceeded');
      error.name = 'QuotaExceededError';
      throw error;
    }
    this.value = value;
  }
}

function incidentFixture(index, sampleCount = 2, textSize = 0) {
  const filler = 'x'.repeat(textSize);
  return {
    id: `incident-${index}`,
    ruleId: 'lean',
    title: `Incident ${index}`,
    message: `Message ${index}${filler}`,
    source: 'LIVE',
    triggerTime: index,
    createdAt: '2026-07-27T00:00:00.000Z',
    samples: Array.from({length: sampleCount}, (_, sample) => ({
      t: sample,
      values: {rpm: 2000 + sample, map: 140 + sample, afr: 12.5 + sample / 10}
    })),
    peak: {rpm: 3000, map: 180, afr: 12.9}
  };
}

function saveHarness({storage, incidentCount = 25, sampleCount = 2, textSize = 0, settingsSeed = {}} = {}) {
  const elements = {
    mslSpeed: {value: '2'},
    mslLoop: {checked: true},
    historyWindow: {value: '120'},
    incidentRecorderToggle: {checked: true}
  };
  const context = vm.createContext({
    JSON,
    localStorage: storage,
    SETTINGS_KEY,
    usbRequirementsDirty: false,
    settings: JSON.parse(JSON.stringify(settingsSeed)),
    rules: [
      {id: 'lean', enabled: true, threshold: 12.2},
      {id: 'custom-rule', enabled: false, threshold: undefined}
    ],
    document: {querySelector: () => ({id: 'page-analysis'})},
    $: id => elements[id] || null,
    lastSelfTest: {status: 'pass', checks: [{name: 'fixture', passed: true}]},
    lastMslInfo: {status: 'loaded', rows: 100},
    incidents: Array.from({length: incidentCount}, (_, index) => incidentFixture(index, sampleCount, textSize)),
    dashboardLayouts: [
      {id: 'factory', protected: true, pages: []},
      {id: 'editable', protected: false, pages: [{id: 'page-daily', widgets: []}]}
    ],
    activeLayoutId: 'editable',
    uiSettings: {haptic: true},
    dashboardLocked: true,
    customMathChannels: [{key: 'math_live', formula: 'map - baro'}],
    customWarningRules: [{id: 'rule-live', condition: 'rpm > 5000'}],
    diagnosticRevision: 0,
    setNodeText: () => {},
    showToast: () => {},
    window: {EpicDashAndroid: {recordEvent: () => {}}}
  });
  const saveSettings = compileFunction(saveSettingsSource, 'saveSettings', context);
  return {context, saveSettings};
}

const baselineStorage = new ThresholdStorage();
const baseline = saveHarness({
  storage: baselineStorage,
  settingsSeed: {
    mathChannels: [{key: 'math_stale', formula: '0'}],
    customWarningRules: [{id: 'rule-stale', condition: 'false'}]
  }
});
assert.strictEqual(baseline.saveSettings(), true);
assert.strictEqual(baselineStorage.calls.length, 1);
assert.strictEqual(baselineStorage.calls[0].key, SETTINGS_KEY);
const persisted = JSON.parse(baselineStorage.value);
assert.strictEqual(persisted.incidents.length, 20, 'only newest in-memory order slice is serialized');
assert.strictEqual(baseline.context.incidents.length, 25, 'save does not mutate the in-memory incident list');
assert.deepStrictEqual(persisted.dashboardLayouts.map(layout => layout.id), ['editable']);
assert.strictEqual(persisted.activeLayoutId, 'editable');
assert.strictEqual(persisted.activePage, 'page-analysis');
assert.strictEqual(persisted.historyWindow, 120);
assert.strictEqual(persisted.ui.dashboardLocked, true);
assert.deepStrictEqual(persisted.mathChannels, [{key: 'math_live', formula: 'map - baro'}],
  'live custom math changes replace stale persisted definitions');
assert.deepStrictEqual(persisted.customWarningRules, [{id: 'rule-live', condition: 'rpm > 5000'}],
  'live custom warning changes replace stale persisted definitions');

const freshStorage = new ThresholdStorage();
const fresh = saveHarness({storage: freshStorage, settingsSeed: {}});
fresh.saveSettings();
const freshPersisted = JSON.parse(freshStorage.value);
assert.deepStrictEqual(freshPersisted.mathChannels, [{key: 'math_live', formula: 'map - baro'}]);
assert.deepStrictEqual(freshPersisted.customWarningRules, [{id: 'rule-live', condition: 'rpm > 5000'}]);

const sizingStorage = new ThresholdStorage();
const sizing = saveHarness({storage: sizingStorage, incidentCount: 20, sampleCount: 12, textSize: 40});
sizing.saveSettings();
const exactLimit = sizingStorage.calls[0].bytes;
assert.ok(exactLimit > 1000, 'near-limit fixture has a material serialized payload');

const nearStorage = new ThresholdStorage(exactLimit, 'last-known-persisted-value');
const near = saveHarness({storage: nearStorage, incidentCount: 20, sampleCount: 12, textSize: 40});
near.saveSettings();
assert.notStrictEqual(nearStorage.value, 'last-known-persisted-value', 'payload at the synthetic limit is accepted');

const overStorage = new ThresholdStorage(exactLimit, nearStorage.value);
const over = saveHarness({storage: overStorage, incidentCount: 20, sampleCount: 13, textSize: 80});
assert.strictEqual(over.saveSettings(), false, 'quota failure is reported to the caller');
assert.strictEqual(overStorage.value, nearStorage.value, 'the previously persisted value survives a rejected write');
assert.ok(!Object.prototype.hasOwnProperty.call(over.context.settings, 'incidents'), 'failed write leaves the accepted in-memory settings snapshot unchanged');
assert.strictEqual(overStorage.calls.length, 1);
assert.ok(overStorage.calls[0].bytes > exactLimit, 'over-limit fixture exceeds the deterministic threshold');

console.log('Dashboard storage capacity/recovery characterization passed.');
