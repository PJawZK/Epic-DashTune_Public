'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const dashboardPath = process.env.EPICDASH_DASHBOARD_HTML || path.resolve(__dirname, '../../main/assets/dashboard_lab.html');
const html = fs.readFileSync(dashboardPath, 'utf8');

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

function compact(value) {
  return String(value).replace(/\s+/g, ' ').trim();
}

const settingsKeyMatch = html.match(/const SETTINGS_KEY\s*=\s*'([^']+)'/);
assert.ok(settingsKeyMatch, 'settings key remains explicit');
const SETTINGS_KEY = settingsKeyMatch[1];
assert.strictEqual(SETTINGS_KEY, 'epicdash-jz-lab-v060');

const saveSettingsSource = functionSource(html, 'function saveSettings');
const saveMathChannelSource = functionSource(html, 'function saveMathChannel');
const deleteMathChannelSource = functionSource(html, 'function deleteMathChannel');
const saveWarningRuleSource = functionSource(html, 'function saveWarningRule');
const deleteWarningRuleSource = functionSource(html, 'function deleteWarningRule');
const compactSave = compact(saveSettingsSource);

assert.ok(/(?:settings|nextSettings)\.mathChannels/.test(saveSettingsSource) && saveSettingsSource.includes('customMathChannels'),
  'saveSettings serializes the live custom math array');
assert.ok(/(?:settings|nextSettings)\.customWarningRules/.test(saveSettingsSource) && saveSettingsSource.includes('customWarningRules'),
  'saveSettings serializes the live custom warning array');
assert.ok(compactSave.includes('return true') && compactSave.includes('return false'),
  'saveSettings returns an explicit success result');
assert.ok(saveSettingsSource.includes('recordEvent') && saveSettingsSource.includes('showToast'),
  'save failure is surfaced to diagnostics and the user');

for (const [name, source] of [
  ['saveMathChannel', saveMathChannelSource],
  ['deleteMathChannel', deleteMathChannelSource],
  ['saveWarningRule', saveWarningRuleSource],
  ['deleteWarningRule', deleteWarningRuleSource]
]) {
  assert.ok(/if\s*\(\s*!\s*saveSettings\s*\(\s*\)\s*\)/.test(source) || /const\s+\w+\s*=\s*saveSettings\s*\(\s*\)/.test(source),
    `${name} checks the persistence result before presenting success`);
}

function compileFunction(source, name, context) {
  return vm.runInContext(`(() => { ${source}; return ${name}; })()`, context);
}

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

function saveHarness(storage) {
  const notices = [];
  const events = [];
  const elements = {
    mslSpeed: {value: '1'},
    mslLoop: {checked: true},
    historyWindow: {value: '30'},
    incidentRecorderToggle: {checked: true}
  };
  const context = vm.createContext({
    JSON,
    localStorage: storage,
    SETTINGS_KEY,
    usbRequirementsDirty: false,
    settings: {
      mathChannels: [{key: 'math_stale', formula: '0'}],
      customWarningRules: [{id: 'rule-stale', condition: 'false'}]
    },
    rules: [{id: 'lean', enabled: true, threshold: 12.2}],
    document: {querySelector: () => ({id: 'page-daily'})},
    $: id => elements[id] || null,
    lastSelfTest: {status: 'not_run', checks: []},
    lastMslInfo: {status: 'none'},
    incidents: [],
    dashboardLayouts: [{id: 'editable', protected: false, pages: []}],
    activeLayoutId: 'editable',
    uiSettings: {haptic: true},
    dashboardLocked: false,
    customMathChannels: [{key: 'math_live', name: 'Live math', unit: 'kPa', decimals: 1, formula: 'map - baro'}],
    customWarningRules: [{id: 'rule-live', label: 'Live rule', title: 'LIVE RULE', message: 'Live', enabled: true, severity: 'warning', condition: 'rpm > 5000', clearExpression: '', delay: 250, clearDelay: 500, cooldown: 5000, popup: true, incident: true, latch: false, suppressInEdit: true}],
    diagnosticRevision: 4,
    setNodeText: (id, value) => notices.push({kind: 'node', id, value}),
    showToast: (value) => notices.push({kind: 'toast', value}),
    window: {EpicDashAndroid: {recordEvent: (category, message) => events.push({category, message})}}
  });
  return {
    context,
    notices,
    events,
    saveSettings: compileFunction(saveSettingsSource, 'saveSettings', context)
  };
}

const successStorage = new ThresholdStorage();
const success = saveHarness(successStorage);
assert.strictEqual(success.saveSettings(), true, 'successful persistence returns true');
const persisted = JSON.parse(successStorage.value);
assert.deepStrictEqual(JSON.parse(JSON.stringify(persisted.mathChannels)), JSON.parse(JSON.stringify(success.context.customMathChannels)),
  'the persisted math definitions match the live normalized array');
assert.deepStrictEqual(JSON.parse(JSON.stringify(persisted.customWarningRules)), JSON.parse(JSON.stringify(success.context.customWarningRules)),
  'the persisted warning definitions match the live normalized array');
assert.strictEqual(success.notices.length, 0, 'successful persistence emits no failure notice');
assert.strictEqual(success.events.length, 0, 'successful persistence emits no failure diagnostic');

const previousValue = successStorage.value;
const failureStorage = new ThresholdStorage(1, previousValue);
const failure = saveHarness(failureStorage);
assert.strictEqual(failure.saveSettings(), false, 'rejected persistence returns false');
assert.strictEqual(failureStorage.value, previousValue, 'rejected persistence preserves the previous stored value');
assert.ok(failure.notices.some(item => item.kind === 'toast' && /not saved|could not be saved/i.test(String(item.value))),
  'rejected persistence produces a bounded user-visible failure');
assert.ok(failure.events.some(item => item.category === 'STORAGE' && /save failed/i.test(item.message)),
  'rejected persistence records a storage diagnostic event');

console.log('Dashboard storage save correctness regression passed.');
