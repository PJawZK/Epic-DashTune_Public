'use strict';

const assert = require('assert');
const fs = require('fs');

const telemetry = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_telemetry_controls.js','utf8');
const tuner = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js','utf8');
const dashboard = fs.readFileSync('app/src/main/assets/dashboard_lab.html','utf8');
const connection = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_connection_refresh.js','utf8');
const gradle = fs.readFileSync('app/build.gradle.kts','utf8');

assert.doesNotThrow(() => new Function(telemetry), 'shared-shell JavaScript does not parse');
assert.ok(dashboard.includes('EPIC_SHARED_SHELL_STATIC_V1'), 'accepted shared shell is not a first-paint dashboard source');
assert.ok(dashboard.includes('<header class="t4tw-topchrome t4tw-shared-topchrome">'), 'static accepted appbar header missing');
assert.ok(dashboard.includes('id="sharedTopEcuState"'), 'static ECU state owner missing');
assert.ok(dashboard.includes('id="sharedTopPageActions"'), 'static page-action slot missing');
assert.ok(dashboard.includes('id="iniStatePill"'), 'static INI control missing');
assert.ok(dashboard.includes('id="headerLockBtn"'), 'static lock owner missing');
assert.ok(dashboard.includes('id="layoutEditBtn"'), 'static Dash edit owner missing');
assert.ok(dashboard.includes('id="labSettingsBtn"'), 'static settings owner missing');

const exactTunerIcon = '<path d="M14 6l4-4 4 4-4 4"/><path d="M13 7L4 16v4h4l9-9"/>';
assert.ok(tuner.includes(exactTunerIcon), 'Tuner-page icon authority changed unexpectedly');
assert.ok(dashboard.includes(exactTunerIcon), 'static shared shell does not use the exact Tuner icon');
for (const destination of ['dash','tuner','logging','diagnostics']) {
  assert.ok(dashboard.includes(`data-shell="${destination}"`), 'static shell destination missing: '+destination);
}

for (const forbidden of [
  '<div class="logo">', '<div class="statusbar">', 'id="clock"', 'id="rateText"', 'id="ageText"'
]) assert.ok(!dashboard.includes(forbidden), 'legacy first-paint dashboard shell returned: '+forbidden);

for (const forbidden of [
  'header.replaceChildren(appbar)', "srcDot.closest('.pill')", 'appbar.append(brand,nav,status)',
  "brand.innerHTML='EpicDash <b>JZ</b>'", "button.innerHTML=(T4_UI_1201_NAV_ICONS[key]||'')",
  't4tw-shared-dash-tools'
]) assert.ok(!telemetry.includes(forbidden), 'legacy runtime shell transformation returned: '+forbidden);

assert.ok(telemetry.includes("document.getElementById('sharedTopEcuState')"), 'shared shell no longer binds the static ECU owner');
assert.ok(telemetry.includes("document.body.classList.add('t4tw-shared-shell-ready')"), 'shared shell readiness contract missing');
assert.ok(connection.includes("document.getElementById('sharedTopPageActions')"), '1202 actions do not reuse the static page-action slot');
assert.ok(!connection.includes('t4tw-shared-dash-tools'), 'legacy Dash tool wrapper returned');

for (const forbidden of [
  't4tw-compat', '<div class="t4tw-title">EpicDashTune</div>', 'Profile-derived tuning workspace',
  '<span class="t4tw-badge">LIVE ECU</span>', '<span class="t4tw-badge sim">EDIT / WRITE</span>'
]) assert.ok(!tuner.includes(forbidden), 'legacy hidden Tuner UI returned: '+forbidden);
assert.ok(tuner.includes('t4tw-internal-state'), 'nonvisual Tuner state anchors missing after legacy UI removal');

const versionCode = Number((gradle.match(/versionCode\s*=\s*(\d+)/) || [])[1] || 0);
assert.ok(versionCode >= 1205, 'physical-failure correction regressed below candidate 1205');

for (const forbidden of [
  'UsbEcuManager(', 'bulkTransfer(', 'controlTransfer(', 'rawHex', 'pageNumber',
  'requestBurn', 'writeTuningChangesJson(', 'burnTuningChangesJson(', 'saveTuningToEcu('
]) assert.ok(!telemetry.includes(forbidden), 'shared presentation shell gained forbidden native/write authority: '+forbidden);

console.log('Tuner static shared-shell / legacy-UI removal characterization passed');
