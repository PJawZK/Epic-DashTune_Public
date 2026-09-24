'use strict';

const assert = require('assert');
const fs = require('fs');

const html = fs.readFileSync('app/src/main/assets/dashboard_lab.html', 'utf8');
const tuner = fs.readFileSync('app/src/main/assets/t4_tuning_workspace.js', 'utf8');

function sectionBetween(source, start, end) {
  const from = source.indexOf(start);
  assert.ok(from >= 0, `missing start marker: ${start}`);
  const to = source.indexOf(end, from);
  assert.ok(to > from, `missing end marker after: ${start}`);
  return source.slice(from, to + end.length);
}

const shell = sectionBetween(html, '<nav id="shellTabs"', '</nav>');
for (const destination of ['dash', 'tuner', 'logging', 'diagnostics']) {
  assert.ok(shell.includes(`data-shell="${destination}"`), `missing top-level shell destination: ${destination}`);
}
assert.ok(!shell.includes('data-shell="settings"'), 'redundant Settings shell destination returned');
assert.ok(
  shell.indexOf('data-shell="dash"') < shell.indexOf('data-shell="tuner"') &&
  shell.indexOf('data-shell="tuner"') < shell.indexOf('data-shell="logging"') &&
  shell.indexOf('data-shell="logging"') < shell.indexOf('data-shell="diagnostics"'),
  'top-level shell destination order changed'
);

const dashTabs = sectionBetween(html, '<nav id="tabs"', '</nav>');
for (const page of ['page-daily', 'page-drift', 'page-boost']) {
  assert.ok(dashTabs.includes(`data-page="${page}"`), `Dash lost core page ${page}`);
}
for (const page of ['page-analysis', 'page-diagnostics', 'page-rules', 'page-tuning']) {
  assert.ok(!dashTabs.includes(`data-page="${page}"`), `utility/tuner page leaked back into Dash tabs: ${page}`);
}

const diagnosticTabs = sectionBetween(html, '<nav id="diagnosticsTabs"', '</nav>');
assert.ok(diagnosticTabs.includes('data-page="page-diagnostics"'), 'Diagnostics destination lost diagnostics page');
assert.ok(diagnosticTabs.includes('data-page="page-rules"'), 'Diagnostics destination lost warnings page');

for (const contract of [
  "if(pageId==='page-tuning')return 'tuner'",
  "if(pageId==='page-analysis')return 'logging'",
  "if(pageId==='page-diagnostics'||pageId==='page-rules')return 'diagnostics'",
  "return 'dash'",
  "if(destination==='settings'){openModal('labSettingsModal');return true;}",
  "else if(destination==='tuner'){target='page-tuning'",
  "else if(destination==='logging')target='page-analysis'",
  "else if(destination==='diagnostics')target=$(lastDiagnosticsPageId)?lastDiagnosticsPageId:'page-diagnostics'",
  "$('openLogPlaybackBtn').onclick=()=>openModal('logModal')",
  "window.addEventListener('epicdash:tuner-ready'",
  "window.dispatchEvent(new CustomEvent('epicdash:page-activated'"
]) {
  assert.ok(html.includes(contract), `missing application-shell routing contract: ${contract}`);
}

assert.ok(html.includes("const root=activeShellDestination==='dash'?$('tabs'):activeShellDestination==='diagnostics'?$('diagnosticsTabs'):null"), 'swipe navigation is not constrained to the active shell context');
assert.ok(html.includes("||(layout.pages.find(page=>page.visible)?.id||'page-daily'))"), 'dashboard layout fallback no longer stays inside Dash');

assert.ok(tuner.includes("page.id = 'page-tuning'"), 'tuner page no longer exists');
assert.ok(tuner.includes("window.dispatchEvent(new CustomEvent('epicdash:tuner-ready'))"), 'tuner does not announce readiness to shell');
assert.ok(!tuner.includes("tab.dataset.page = 'page-tuning'"), 'tuner still injects a dashboard tab');
assert.ok(!tuner.includes('tabs.insertBefore(tab'), 'tuner still mutates dashboard tab ownership');
assert.ok(!tuner.includes("tab.addEventListener('click'"), 'stale injected-tab click listener can abort remaining tuner bindings');
assert.ok(tuner.includes("window.addEventListener('epicdash:page-activated'"), 'tuner no longer reloads from authoritative shell page activation');

assert.ok(html.includes("$('labSettingsBtn').onclick=()=>openModal('labSettingsModal')"), 'gear no longer owns Settings');
assert.ok(html.includes('id="iniQuickModal"'), 'INI short-press status/import menu missing');
assert.ok(html.includes('openIniDiagnosticsDeepLink'), 'INI long-press diagnostics deep link missing');
assert.ok(html.includes("scrollIntoView({behavior:'smooth',block:'start'})"), 'INI deep link no longer scrolls to the evidence card');

for (const marker of [
  "window.EpicDashHaptic=haptic",
  "document.addEventListener('click',event=>{",
  "control.disabled",
  "settingHaptic",
  "Haptic feedback"
]) {
  assert.ok(html.includes(marker), 'global enabled-control haptic contract missing: ' + marker);
}

console.log('Application shell navigation characterization passed');
