'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const dashboardPath = process.env.EPICDASH_DASHBOARD_HTML || path.resolve(__dirname, '../../main/assets/dashboard_lab.html');
const html = fs.readFileSync(dashboardPath, 'utf8');
const compact = value => String(value).replace(/\s+/g, '');

function functionSource(source, marker) {
  const start = source.indexOf(marker);
  assert.ok(start >= 0, `Missing function marker: ${marker}`);
  const parameterOpen = source.indexOf('(', start);
  assert.ok(parameterOpen > start, `Missing parameters: ${marker}`);
  let parenDepth = 0;
  let quote = null;
  let escaped = false;
  let parameterClose = -1;
  for (let index = parameterOpen; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') { quote = char; continue; }
    if (char === '(') parenDepth++;
    else if (char === ')' && --parenDepth === 0) { parameterClose = index; break; }
  }
  assert.ok(parameterClose > parameterOpen, `Unterminated parameters: ${marker}`);
  const brace = source.indexOf('{', parameterClose + 1);
  assert.ok(brace > parameterClose, `Missing body: ${marker}`);
  let depth = 0;
  quote = null;
  escaped = false;
  for (let index = brace; index < source.length; index++) {
    const char = source[index];
    if (quote) {
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'" || char === '`') { quote = char; continue; }
    if (char === '{') depth++;
    else if (char === '}' && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error(`Unterminated body: ${marker}`);
}

const src = Object.fromEntries([
  ['clock','function historyClock'],['clear','function clearTimeSeries'],['subscriptions','function refreshHistorySubscriptions'],
  ['capture','function captureTimeSeries'],['window','function historyWindowSamples'],['points','function seriesPoints'],
  ['graphValue','function graphValue'],['analysis','function renderAnalysis'],['range','function channelRange'],
  ['inspectorGate','function shouldUpdateChannelInspector'],['inspector','function updateChannelInspector'],
  ['incident','function startIncident'],['markers','function renderWarningMarkers'],
  ['graphWindow','function graphSamplesForWindow'],['graphValues','function valuesForGraphChannel'],
  ['padding','function paddedRange'],['scales','function graphWidgetScales'],['graph','function renderGraphWidget'],
  ['custom','function renderCustomWidgets'],['render','function render(now)'],['source','function setSourceChannels'],
  ['normalizeWidget','function normalizeWidget']
].map(([name, marker]) => [name, functionSource(html, marker)]));

assert.ok(compact(src.render).includes(compact("if(analysisPage&&now-lastAnalysisRenderAt>=100){renderAnalysis();lastAnalysisRenderAt=now;}")), 'Analysis is active-page owned at 100 ms');
assert.strictEqual((src.render.match(/\brenderAnalysis\s*\(/g) || []).length, 1, 'ordinary render has one Analysis call site');
assert.ok(compact(src.render).includes(compact("if(dataRevision!==lastHeavyRenderRevision||now-lastHeavyRenderAt>=250){renderCustomWidgets();lastHeavyRenderRevision=dataRevision;lastHeavyRenderAt=now;}")), 'custom widgets use revision-or-250-ms ownership');
assert.ok(compact(src.custom).includes(compact("performanceProfile==='full'&&!editMode?'.page.active .customWidget[data-editor-id]':'.customWidget[data-editor-id]'")), 'Full/non-edit custom widgets select the active page only');
assert.ok(src.custom.includes('renderGraphWidget(card,widget)'), 'custom graph cards use the graph renderer');
assert.ok(compact(src.source).includes(compact('if(!options.keepHistory)clearTimeSeries(false);')), 'source changes clear history unless preserved');
assert.ok(compact(src.capture).includes(compact('if(lastHistoryTime>=0&&t<lastHistoryTime-.25)timeSeries=[];')), 'reverse time clears retained history');
assert.ok(compact(src.incident).includes(compact('timeSeries.filter(sample=>t-sample.t<=15)')), 'incidents retain 15 seconds before trigger');
assert.ok(src.markers.includes('currentLogWarnings()') && src.markers.includes('pos/mslDuration*100'), 'warning markers remain MSL-position based');
assert.ok(src.normalizeWidget.includes('[5,10,30,60,120]') && src.normalizeWidget.includes('.slice(0,4)'), 'custom graphs retain five windows and four channels');
assert.ok(src.subscriptions.includes('widget.graphChannels') && src.subscriptions.includes('customMathChannels') && src.subscriptions.includes('customWarningRules'), 'history subscriptions include graph, math, and warning dependencies');

function historyHarness(options = {}) {
  let nowMs = options.nowMs ?? 1000;
  let selectedWindow = options.selectedWindow ?? 30;
  const context = {
    console, Math, Number, Object, Set,
    performance: { now: () => nowMs }, source: options.source || 'LIVE', mslPosition: options.mslPosition ?? 0,
    paused: !!options.paused, selfTestRunning: !!options.selfTestRunning, timeSeries: options.timeSeries || [],
    lastHistoryCapture: options.lastHistoryCapture ?? 0, lastHistoryTime: options.lastHistoryTime ?? -1,
    hist: { rpm: [], boost: [], target: [] }, performanceProfile: 'full', historySubscriptions: new Set(['rpm','map']),
    ALL_CHANNELS: ['rpm','map','tps'], data: { rpm: 1000, map: 100, tps: 10 }, settings: { historyWindow: selectedWindow },
    $: id => id === 'historyWindow' ? { value: String(selectedWindow) } : null,
    channelValid: key => Number.isFinite(Number(context.data[key])), clamp: (value,min,max) => Math.min(max,Math.max(min,value)), updatePendingIncidents() {}, renderAnalysis() {}, updateChannelInspector() {}
  };
  vm.createContext(context);
  vm.runInContext(`${src.clock}\n${src.clear}\n${src.capture}\n${src.window}\n${src.points}\nthis.capture=captureTimeSeries;this.samples=historyWindowSamples;this.makePoints=seriesPoints;this.state=()=>({timeSeries,lastHistoryCapture,lastHistoryTime});`, context);
  return { context, setNow: value => { nowMs = value; }, setWindow: value => { selectedWindow = value; }, state: () => context.state() };
}

{
  const h = historyHarness();
  h.context.capture(1000); h.setNow(1050); h.context.capture(1050);
  assert.strictEqual(h.state().timeSeries.length, 1, 'capture is capped at 10 Hz');
  h.setNow(1100); h.context.capture(1100);
  assert.strictEqual(h.state().timeSeries.length, 2, '100 ms boundary captures');
  h.context.paused = true; h.setNow(1300); h.context.capture(1300);
  assert.strictEqual(h.state().timeSeries.length, 2, 'pause suppresses capture');
  h.context.paused = false; h.context.selfTestRunning = true; h.setNow(1400); h.context.capture(1400);
  assert.strictEqual(h.state().timeSeries.length, 2, 'ordinary self-test suppresses capture');
  h.context.capture(1400, true);
  assert.strictEqual(h.state().timeSeries.length, 3, 'forced self-test capture remains available');
}

{
  const h = historyHarness({ source: 'MSL', mslPosition: 50 });
  h.context.capture(1000); h.context.mslPosition = 40; h.setNow(1100); h.context.capture(1100);
  assert.strictEqual(h.state().timeSeries.length, 1, 'MSL reverse seek clears forward history');
  assert.strictEqual(h.state().timeSeries[0].t, 40, 'post-seek sample uses new MSL position');
}

for (const [window, first] of [[30,20],[120,10]]) {
  const h = historyHarness({ timeSeries: Array.from({length:151},(_,t)=>({t,values:{rpm:t}})), lastHistoryCapture:149900, lastHistoryTime:149, nowMs:150000, selectedWindow:window });
  h.context.capture(150000);
  assert.strictEqual(h.state().timeSeries[0].t, first, `${window}-second selection retains the documented horizon`);
}

{
  const h = historyHarness({ timeSeries: Array.from({length:151},(_,t)=>({t,values:{rpm:t}})) });
  for (const [window,count] of [[30,31],[60,61],[120,121]]) {
    h.setWindow(window); const samples = h.context.samples();
    assert.strictEqual(samples.length, count, `${window}-second extraction is inclusive`);
    assert.strictEqual(samples[0].t, 150-window, `${window}-second extraction starts at its boundary`);
  }
  assert.strictEqual(h.context.makePoints([{t:0,values:{rpm:0}},{t:1,values:{}},{t:2,values:{rpm:10}}],'rpm',0,10), '0,170 600,0', 'polyline omits unavailable values');
}

{
  let windows = 0, points = 0;
  const nodes = new Map();
  const context = { console, Math, Number, data:Object.fromEntries(['rpm','boost','tps','ign','afr','afrTarget','afrError','map','boostTarget','boostDuty','clt','iat','batt','oilPressure'].map(k=>[k,1])), CHANNEL_META:{}, channelValid:()=>true,
    historyWindowSamples(){windows++;return[{t:0,values:{}},{t:1,values:{}}];}, seriesPoints(){points++;return'fixture';},
    $:id=>{if(!nodes.has(id))nodes.set(id,{value:null,setAttribute(name,value){if(name==='points')this.value=value;}});return nodes.get(id);}, setNodeText(){} };
  vm.createContext(context); vm.runInContext(`${src.graphValue}\n${src.analysis}\nthis.run=renderAnalysis;`, context); context.run();
  assert.strictEqual(windows,1,'built-in Analysis extracts one window');
  assert.strictEqual(points,15,'built-in Analysis builds fifteen polylines');
  assert.strictEqual([...nodes.values()].filter(n=>n.value==='fixture').length,15,'fifteen built-in targets receive points');
}

{
  let windows = 0;
  const context = { Number, Math, historyWindowSamples(){windows++;return[{values:{rpm:1000}},{values:{rpm:2000}}];} };
  vm.createContext(context); vm.runInContext(`${src.range}\nthis.run=channelRange;`, context);
  context.run('rpm'); context.run('rpm');
  assert.strictEqual(windows,2,'separate channelRange calls repeat window extraction');
}

{
  let active=false, now=2000, windows=0; const body={innerHTML:''};
  const context={console,Math,Number,Object,performance:{now:()=>now},document:{querySelector:s=>s==='#page-analysis.active'&&active?{}:null},$:id=>id==='channelInspector'?body:null,
    historyWindowSamples(){windows++;return[{values:{rpm:1000,map:80}},{values:{rpm:2000,map:120}}];},inspectorChannelKeys:()=>['rpm','map'],
    CHANNEL_META:{rpm:{label:'RPM',unit:'rpm'},map:{label:'MAP',unit:'kPa'}},channelState:{rpm:{available:true,updatedAt:1900,source:'LIVE'},map:{available:true,updatedAt:1900,source:'LIVE'}},
    data:{rpm:2000,map:120},channelValid:key=>Number.isFinite(Number(context.data[key])),escapeHtml:String,lastInspectorRenderAt:0};
  vm.createContext(context); vm.runInContext(`${src.inspectorGate}\n${src.inspector}\nthis.run=updateChannelInspector;`,context);
  context.run(false); assert.strictEqual(windows,0,'hidden Analysis skips inspector');
  active=true; context.run(false); assert.strictEqual(windows,1,'active inspector scans one window');
  now=2500; context.run(false); assert.strictEqual(windows,1,'inspector is one-second throttled');
  context.run(true); assert.strictEqual(windows,2,'forced inspector bypasses throttle');
}

function graphCounts(mode){
  let samples=0,scans=0,points=0;const rows=[{t:0,values:{rpm:1000,map:80,tps:10,afr:14}},{t:1,values:{rpm:2000,map:120,tps:20,afr:13}}];
  const traces=Array.from({length:4},()=>({style:{},setAttribute(){}})),reference={style:{},setAttribute(){}},legend={style:{},innerHTML:''},sourceNode={textContent:''};
  const card={querySelectorAll:s=>s==='[data-role="graph-trace"]'?traces:[],querySelector(s){return s==='[data-role="graph-reference"]'?reference:s==='[data-role="graph-legend"]'?legend:s==='[data-role="source"]'?sourceNode:null;}};
  const context={console,Math,Number,Object,graphSamplesForWindow(){samples++;return rows;},valuesForGraphChannel(input,key){scans++;return input.map(row=>Number(row.values[key])).filter(Number.isFinite);},seriesPoints(){points++;return'fixture';},
    CHANNEL_DEFAULTS:{rpm:[0,7000,0],map:[20,260,0],tps:[-10,110,1],afr:[7,20,1]},CHANNEL_META:{rpm:{label:'RPM',unit:'rpm'},map:{label:'MAP',unit:'kPa'},tps:{label:'TPS',unit:'%'},afr:{label:'AFR',unit:'AFR'}},
    data:{rpm:2000,map:120,tps:20,afr:13},channelValid:()=>true,escapeHtml:String,clamp:(v,min,max)=>Math.min(max,Math.max(min,v))};
  vm.createContext(context);vm.runInContext(`${src.padding}\n${src.scales}\n${src.graph}\nthis.run=renderGraphWidget;`,context);
  context.run(card,{channel:'rpm',graphChannels:['rpm','map','tps','afr'],graphWindow:30,graphScaleMode:mode,graphMin:0,graphMax:7000,graphReference:null,graphLegend:true,graphCurrent:true});
  return{samples,scans,points};
}
assert.deepStrictEqual(graphCounts('per-channel'),{samples:1,scans:4,points:4},'per-channel graph work');
assert.deepStrictEqual(graphCounts('shared-auto'),{samples:1,scans:4,points:4},'shared-auto graph work');
assert.deepStrictEqual(graphCounts('shared-fixed'),{samples:1,scans:0,points:4},'shared-fixed graph work');

{
  const context={timeSeries:Array.from({length:151},(_,t)=>({t}))};vm.createContext(context);vm.runInContext(`${src.graphWindow}\nthis.run=graphSamplesForWindow;`,context);
  assert.strictEqual(context.run(30).length,31,'custom 30-second extraction is inclusive');
  assert.strictEqual(context.run(120).length,121,'custom 120-second extraction is inclusive');
}
assert.ok(src.window.includes('.filter('),'built-in extraction filters retained history per call');
assert.ok(src.graphWindow.includes('.filter('),'each custom graph filters retained history');
assert.ok(src.graphValues.includes('.map(')&&src.graphValues.includes('.filter('),'custom auto-scale maps and filters each channel');

console.log('dashboard_analysis_history_scaling_characterization_test: PASS');
