'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const html = fs.readFileSync('app/src/main/assets/dashboard_lab.html', 'utf8');
const runtime = fs.readFileSync('app/src/main/assets/dashboard_runtime_snapshot.js', 'utf8');
const hub = fs.readFileSync('app/src/main/java/com/buttonbox/ble/DashboardDataHub.kt', 'utf8');
const usb = fs.readFileSync('app/src/main/java/com/buttonbox/ble/UsbEcuManager.kt', 'utf8');

function sourceBetween(startMarker, endMarker) {
  const start = html.indexOf(startMarker);
  assert.ok(start >= 0, 'Missing source marker: ' + startMarker);
  const end = html.indexOf(endMarker, start + startMarker.length);
  assert.ok(end > start, 'Missing source marker after ' + startMarker + ': ' + endMarker);
  return html.slice(start, end).trim();
}

for (const retired of [
  'TPS end-to-end trace',
  'tpsAnalysisPipeline',
  'tpsDiagnosticPipeline',
  'tpsTraceState',
  'function renderTpsTrace'
]) assert.ok(!html.includes(retired), 'retired TPS trace surface returned: ' + retired);

for (const retired of ['TpsTrace', 'tpsTrace']) {
  assert.ok(!runtime.includes(retired), 'runtime TPS trace coordinator returned: ' + retired);
}
assert.ok(!hub.includes('tpsTrace'), 'native dashboard snapshot still emits TPS trace payload');
assert.ok(!usb.includes('_traceTps'), 'USB decoder still performs trace-only TPS decoding');

for (const id of ['tpsDaily','tps','tpsBoost','tpsAnalysisDirect','tpsDiagnosticDirect']) {
  assert.ok(html.includes('id="' + id + '"'), 'direct TPS visibility lost: ' + id);
}

const visual = sourceBetween('function tpsVisualPercent', 'function renderTpsDirect');
const render = sourceBetween('function renderTpsDirect', 'function setStyle');

function run(activePageId, direct) {
  const text = new Map();
  const style = new Map();
  const context = {
    Number, Math,
    data:{tps:direct},
    document:{querySelector:()=>({id:activePageId})},
    channelValid:key=>key==='tps' && Number.isFinite(Number(direct)),
    clamp:(value,minimum,maximum)=>Math.max(minimum,Math.min(maximum,value)),
    setNodeText:(id,value)=>text.set(id,String(value)),
    setStyle:(id,key,value)=>style.set(id+':'+key,String(value))
  };
  vm.createContext(context);
  vm.runInContext(visual + '\n' + render + '\nthis.run=renderTpsDirect;', context);
  context.run();
  return {text,style};
}

{
  const daily=run('page-daily',-7.25);
  assert.strictEqual(daily.text.get('tpsDaily'),'-7.3');
  assert.strictEqual(daily.style.get('tpsDailyMarker:left'),String((-7.25+10)/120*100)+'%');
}
{
  const drift=run('page-drift',42.25);
  assert.strictEqual(drift.text.get('tps'),'42.3');
  assert.ok(drift.style.has('tpsBar:left'));
}
{
  const boost=run('page-boost',135.5);
  assert.strictEqual(boost.text.get('tpsBoost'),'135.5','numeric TPS remains unclamped');
  assert.strictEqual(boost.style.get('tpsBoostMarker:left'),'100%','only the visual marker saturates');
}
assert.strictEqual(run('page-analysis',12.5).text.get('tpsAnalysisDirect'),'12.5');
assert.strictEqual(run('page-diagnostics',12.5).text.get('tpsDiagnosticDirect'),'12.5');
assert.strictEqual(run('page-daily',Number.NaN).text.get('tpsDaily'),'—');

console.log('TPS direct-value characterization passed; old end-to-end trace remains retired');
