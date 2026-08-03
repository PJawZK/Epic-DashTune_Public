'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const runtime = require('../../main/assets/dashboard_runtime_snapshot.js');

const dashboardPath = process.env.EPICDASH_DASHBOARD_HTML || path.resolve(__dirname, '../../main/assets/dashboard_lab.html');
const html = fs.readFileSync(dashboardPath, 'utf8');

function sourceBetween(startMarker, endMarker) {
  const start = html.indexOf(startMarker);
  assert.ok(start >= 0, `Missing source marker: ${startMarker}`);
  const end = html.indexOf(endMarker, start + startMarker.length);
  assert.ok(end > start, `Missing source marker after ${startMarker}: ${endMarker}`);
  return html.slice(start, end).trim();
}

function pageMarkup(pageId) {
  const idMarker = `id="${pageId}"`;
  const idIndex = html.indexOf(idMarker);
  assert.ok(idIndex >= 0, `Missing page ${pageId}`);
  const start = html.lastIndexOf('<section', idIndex);
  const end = html.indexOf('</section>', idIndex);
  assert.ok(start >= 0 && end > idIndex, `Unable to isolate page ${pageId}`);
  return html.slice(start, end + '</section>'.length);
}

const tpsVisualSource = sourceBetween('function tpsVisualPercent', 'function renderTpsTrace');
const tpsTraceSource = sourceBetween('function renderTpsTrace', 'function setStyle');
const renderSource = sourceBetween('function render(now)', 'function renderLamps');

assert.strictEqual((html.match(/function renderTpsTrace\s*\(/g) || []).length, 1, 'one TPS trace renderer is defined');
assert.strictEqual((renderSource.match(/\brenderTpsTrace\s*\(/g) || []).length, 1, 'ordinary render calls TPS trace exactly once');
assert.ok(
  renderSource.includes("if(channelValid('tps',false)){tpsTraceState.painted=Number(data.tps);tpsTraceState.paintedRevision=lastAcceptedNativeRevision;}"),
  'painted TPS state remains captured before page-owned rendering'
);

const pageTargets = {
  'page-daily': ['tpsDaily', 'tpsDailyMeta'],
  'page-drift': ['tps'],
  'page-boost': ['tpsBoost'],
  'page-analysis': ['tpsAnalysisPipeline'],
  'page-diagnostics': ['tpsDiagnosticPipeline']
};
for (const [pageId, ids] of Object.entries(pageTargets)) {
  const markup = pageMarkup(pageId);
  for (const id of ids) assert.ok(markup.includes(`id="${id}"`), `${id} remains owned by ${pageId}`);
}

const TPS_TEXT_IDS = ['tpsDaily', 'tps', 'tpsBoost', 'tpsAnalysisPipeline', 'tpsDiagnosticPipeline', 'tpsDailyMeta'];
const TPS_STYLE_IDS = ['tpsDailyMarker', 'tpsBar', 'tpsBoostMarker'];

function createHarness(options = {}) {
  let activePage = options.activePage || 'page-daily';
  const nodes = new Map();
  const textCalls = [];
  const textMutations = [];
  const styleCalls = [];
  const styleMutations = [];
  const srcDot = { className: 'dot' };

  function node(id) {
    if (!nodes.has(id)) nodes.set(id, { textContent: '', style: {} });
    return nodes.get(id);
  }

  const direct = options.direct ?? -7.25;
  const tpsValid = options.tpsValid !== false;
  const context = {
    console,
    Math,
    Number,
    String,
    Date,
    performance: { now: () => 1000 },
    document: {
      querySelector(selector) {
        if (selector === '.page.active') return { id: activePage };
        return null;
      }
    },
    data: {
      tps: direct,
      afrError: 0,
      gpsAccuracy: 0,
      gpsSpeedAccuracy: 0,
      rpm: 0,
      map: 0,
      boostTarget: 0
    },
    channelState: { displaySpeed: { source: options.source || 'LIVE' } },
    tpsTraceState: {
      native: {
        rawByte0: 10,
        rawByte1: 255,
        rawNumeric: 1024,
        decoded: -4.125,
        profileTpsValue: 110.5,
        canonical: 135.75,
        rawTps1Primary: 2.34567,
        tpsADC: 512.34,
        throttlePedalPosition: -3.456,
        DriverThrottleIntent: 115.678,
        ...(options.native || {})
      },
      accepted: options.accepted ?? -7.5,
      painted: options.painted ?? 135.5,
      acceptedRevision: options.acceptedRevision ?? 7,
      paintedRevision: options.paintedRevision ?? 8
    },
    activeUsbSessionId: options.activeUsbSessionId ?? 42,
    bridgeTransitMs: options.bridgeTransitMs ?? 12.345,
    lastAcceptedNativeRevision: options.lastAcceptedNativeRevision ?? 8,
    source: options.source || 'LIVE',
    selfTestRunning: options.selfTestRunning === true,
    clamp(value, min, max) { return Math.min(max, Math.max(min, value)); },
    channelValid(key) { return key === 'tps' ? tpsValid && Number.isFinite(Number(context.data.tps)) : false; },
    setNodeText(id, value) {
      const next = String(value);
      textCalls.push({ id, value: next });
      const element = node(id);
      if (element.textContent !== next) {
        element.textContent = next;
        textMutations.push({ id, value: next });
      }
    },
    setStyle(id, key, value) {
      const next = String(value);
      styleCalls.push({ id, key, value: next });
      const element = node(id);
      if (element.style[key] !== next) {
        element.style[key] = next;
        styleMutations.push({ id, key, value: next });
      }
    },
    $(id) { return id === 'srcDot' ? srcDot : null; },
    setValue() {},
    updatePhysicalGpsStatus() {},
    renderLamps() {},
    markCard() {},
    drawSpark() {},
    refreshPerformanceButtons() {},
    renderCustomWidgets() {},
    renderMathChannels() {},
    renderAnalysis() {},
    performanceProfile: options.performanceProfile || 'full',
    editMode: options.editMode === true,
    RADIAL_ARC_LENGTH: 100,
    lastRenderAt: 0,
    renderFrameCount: 0,
    renderSequence: 0,
    fpsWindowStart: 1000,
    renderFps: 0,
    paused: false,
    lastPacket: 1000,
    hist: { rpm: [], boost: [], target: [] },
    rate: 20,
    packets: 0,
    liveTransport: 'usb',
    nativeLiveEnabled: true,
    livePhase: 'online',
    liveRetryDelay: 0,
    liveRetryAttempt: 0,
    lastClockSecond: -1,
    dataRevision: 1,
    lastHeavyRenderRevision: -1,
    lastHeavyRenderAt: 0,
    lastMathRenderAt: 0,
    lastAnalysisRenderAt: 0
  };

  vm.createContext(context);
  vm.runInContext(
    `${tpsVisualSource}\n${tpsTraceSource}\n${renderSource}\n` +
    `this.__tpsVisualPercent=tpsVisualPercent;
     this.__rawRenderTpsTrace=renderTpsTrace;
     this.__render=render;
     this.__tpsOwnershipState=()=>({
       performanceProfile,
       editMode,
       activePageId:document.querySelector('.page.active')?.id||'page-daily',
       environment:{
         channelValid:(key,requireFresh)=>channelValid(key,requireFresh),
         data,
         tpsTraceState,
         activeUsbSessionId,
         bridgeTransitMs,
         setNodeText:(id,value)=>setNodeText(id,value),
         setStyle:(id,key,value)=>setStyle(id,key,value),
         tpsVisualPercent:value=>tpsVisualPercent(value)
       }
     });`,
    context
  );
  const coordinator = runtime.createTpsTraceOwnershipCoordinator(
    context,
    () => context.__tpsOwnershipState()
  );
  assert.strictEqual(coordinator.install(), true, 'TPS ownership coordinator installs');
  context.__renderTpsTrace = context.__rawRenderTpsTrace;

  return {
    context,
    coordinator,
    nodes,
    textCalls,
    textMutations,
    styleCalls,
    styleMutations,
    setActivePage(pageId) { activePage = pageId; },
    textValue(id) { return node(id).textContent; },
    styleValue(id, key) { return node(id).style[key]; },
    tpsTextCalls(from = 0) { return textCalls.slice(from).filter(item => TPS_TEXT_IDS.includes(item.id)); },
    tpsTextMutations(from = 0) { return textMutations.slice(from).filter(item => TPS_TEXT_IDS.includes(item.id)); },
    tpsStyleCalls(from = 0) { return styleCalls.slice(from).filter(item => TPS_STYLE_IDS.includes(item.id)); },
    tpsStyleMutations(from = 0) { return styleMutations.slice(from).filter(item => TPS_STYLE_IDS.includes(item.id)); }
  };
}

{
  const harness = createHarness();
  harness.context.__renderTpsTrace();

  for (const id of ['tpsDaily', 'tps', 'tpsBoost']) assert.strictEqual(harness.textValue(id), '-7.3');
  assert.strictEqual(harness.styleValue('tpsDailyMarker', 'left'), `${((-7.25 + 10) / 120 * 100)}%`);
  assert.strictEqual(harness.styleValue('tpsBar', 'left'), `${((-7.25 + 10) / 120 * 100)}%`);
  assert.strictEqual(harness.styleValue('tpsBoostMarker', 'left'), `${((-7.25 + 10) / 120 * 100)}%`);

  const expectedPipeline = `Raw bytes 0A FF • raw 1024 • INI -4.125%
Profile TPSValue 110.500% • canonical 135.750% • JS -7.500% • painted 135.500%
rawTps1Primary 2.3457 • tpsADC 512.3 • pedal -3.46 • intent 115.68
Session 42 • accepted rev 7 • painted rev 8 • bridge 12.3 ms`;
  assert.strictEqual(harness.textValue('tpsAnalysisPipeline'), expectedPipeline);
  assert.strictEqual(harness.textValue('tpsDiagnosticPipeline'), expectedPipeline);
  assert.strictEqual(harness.textValue('tpsDailyMeta'), 'No smoothing • no clamp • rev 8');
}

{
  const below = createHarness({ direct: -25, painted: -25 });
  below.context.__renderTpsTrace();
  assert.strictEqual(below.textValue('tpsDaily'), '-25.0', 'numeric TPS remains visible below the fixture range');
  assert.strictEqual(below.styleValue('tpsDailyMarker', 'left'), '0%', 'only the visual marker saturates below -10%');

  const above = createHarness({ direct: 135.5, painted: 135.5 });
  above.context.__renderTpsTrace();
  assert.strictEqual(above.textValue('tpsDaily'), '135.5', 'numeric TPS remains visible above the fixture range');
  assert.strictEqual(above.styleValue('tpsDailyMarker', 'left'), '100%', 'only the visual marker saturates above 110%');
}

{
  const missing = createHarness({ direct: Number.NaN, tpsValid: false });
  missing.context.__renderTpsTrace();
  for (const id of ['tpsDaily', 'tps', 'tpsBoost']) assert.strictEqual(missing.textValue(id), '—');
  assert.strictEqual(missing.tpsStyleCalls().length, 0, 'unavailable TPS does not move any visual marker');
  assert.ok(missing.textValue('tpsDiagnosticPipeline').includes('Raw bytes 0A FF'), 'native trace evidence remains visible when canonical TPS is unavailable');
}

{
  const baseline = createHarness({ source: 'LIVE' });
  baseline.context.__renderTpsTrace();
  const expected = TPS_TEXT_IDS.map(id => [id, baseline.textValue(id)]);

  for (const state of [
    { source: 'DEMO' },
    { source: 'MSL' },
    { source: 'LIVE', selfTestRunning: true }
  ]) {
    const harness = createHarness(state);
    harness.context.__renderTpsTrace();
    assert.deepStrictEqual(TPS_TEXT_IDS.map(id => [id, harness.textValue(id)]), expected, `TPS truth text is source-independent for ${state.source}${state.selfTestRunning ? ' self-test' : ''}`);
  }
}

{
  const expectedByPage = {
    'page-daily': { text: ['tpsDaily', 'tpsDailyMeta'], style: ['tpsDailyMarker'] },
    'page-drift': { text: ['tps'], style: ['tpsBar'] },
    'page-boost': { text: ['tpsBoost'], style: ['tpsBoostMarker'] },
    'page-analysis': { text: ['tpsAnalysisPipeline'], style: [] },
    'page-diagnostics': { text: ['tpsDiagnosticPipeline'], style: [] },
    'page-rules': { text: [], style: [] }
  };

  for (const [pageId, expected] of Object.entries(expectedByPage)) {
    const harness = createHarness({ activePage: pageId, direct: 42.25, painted: -99, paintedRevision: -1 });
    harness.context.__render(1000);

    assert.deepStrictEqual(harness.tpsTextCalls().map(item => item.id), expected.text, `${pageId} updates only its owned TPS text targets`);
    assert.deepStrictEqual(harness.tpsStyleCalls().map(item => item.id), expected.style, `${pageId} updates only its owned TPS marker targets`);
    assert.strictEqual(harness.context.tpsTraceState.painted, 42.25, `${pageId} still captures the painted TPS truth value`);
    assert.strictEqual(harness.context.tpsTraceState.paintedRevision, 8, `${pageId} still captures the painted TPS revision`);

    const textCallStart = harness.textCalls.length;
    const textMutationStart = harness.textMutations.length;
    const styleCallStart = harness.styleCalls.length;
    const styleMutationStart = harness.styleMutations.length;
    harness.context.__render(1010);

    assert.deepStrictEqual(harness.tpsTextCalls(textCallStart).map(item => item.id), expected.text, `${pageId} keeps the same page-owned text calls on repeated render`);
    assert.deepStrictEqual(harness.tpsStyleCalls(styleCallStart).map(item => item.id), expected.style, `${pageId} keeps the same page-owned marker calls on repeated render`);
    assert.strictEqual(harness.tpsTextMutations(textMutationStart).length, 0, `${pageId} preserves compare-before-write text behavior`);
    assert.strictEqual(harness.tpsStyleMutations(styleMutationStart).length, 0, `${pageId} preserves compare-before-write marker behavior`);
  }
}

{
  for (const performanceProfile of ['legacy', 'native', 'bridge']) {
    const harness = createHarness({ activePage: 'page-rules', performanceProfile, direct: 42.25 });
    harness.context.__render(1000);
    assert.deepStrictEqual(harness.tpsTextCalls().map(item => item.id), TPS_TEXT_IDS, `${performanceProfile} profile preserves all-target TPS rendering`);
    assert.deepStrictEqual(harness.tpsStyleCalls().map(item => item.id), TPS_STYLE_IDS, `${performanceProfile} profile preserves all-target TPS markers`);
  }

  const editing = createHarness({ activePage: 'page-rules', performanceProfile: 'full', editMode: true, direct: 42.25 });
  editing.context.__render(1000);
  assert.deepStrictEqual(editing.tpsTextCalls().map(item => item.id), TPS_TEXT_IDS, 'edit mode preserves all-target TPS rendering');
  assert.deepStrictEqual(editing.tpsStyleCalls().map(item => item.id), TPS_STYLE_IDS, 'edit mode preserves all-target TPS markers');
}

console.log('dashboard_tps_trace_characterization_test: PASS');
