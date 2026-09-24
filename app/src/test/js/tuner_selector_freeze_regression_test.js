'use strict';

const assert = require('assert');
const fs = require('fs');

const ui = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_table_controls.js', 'utf8');

assert.ok(!ui.includes('new MutationObserver(()=>t4PatchBooleanToggles())'), 'selector patch must not observe its own child-list mutations');

const helperMatch = ui.match(/function t4SetTextIfChanged\(node,text\)\{[^}]+\}/);
assert.ok(helperMatch, 'production selector idempotence helper must be present');
const t4SetTextIfChanged = new Function(`${helperMatch[0]}; return t4SetTextIfChanged;`)();

let value = 'NaN';
let writes = 0;
const fakeToggle = {};
Object.defineProperty(fakeToggle, 'textContent', {
  configurable: true,
  get() { return value; },
  set(next) { writes += 1; value = String(next); }
});

for (let i = 0; i < 10000; i += 1) t4SetTextIfChanged(fakeToggle, '—');
assert.strictEqual(value, '—', 'unavailable selector should settle on an em dash');
assert.strictEqual(writes, 1, 'repeated unavailable selector passes must settle after one text mutation');

for (let i = 0; i < 10000; i += 1) t4SetTextIfChanged(fakeToggle, 'Enabled');
assert.strictEqual(value, 'Enabled', 'descriptive selector should settle on its label');
assert.strictEqual(writes, 2, 'repeated descriptive selector passes must add only one new text mutation');

for (let i = 0; i < 10000; i += 1) t4SetTextIfChanged(fakeToggle, '');
assert.strictEqual(value, '', 'boolean switch should settle on textless state');
assert.strictEqual(writes, 3, 'repeated boolean selector passes must add only one new text mutation');

assert.ok(ui.includes('const t4BaseSettingsFieldNode1197=settingsFieldNode'), 'newly rendered fields must still receive selector patching');
assert.ok(ui.includes('requestAnimationFrame(()=>{t4PatchBooleanToggles();'), 'normal render/viewport hooks must still refresh selector state');

console.log('Tuner selector freeze regression test passed');
