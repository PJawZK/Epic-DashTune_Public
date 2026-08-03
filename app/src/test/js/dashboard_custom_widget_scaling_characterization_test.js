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
  ['visualState', 'function widgetVisualState'],
  ['applyVisual', 'function applyWidgetVisualState'],
  ['layoutVisual', 'function renderLayoutVisualStates'],
  ['controlRuntime', 'function controlRuntimeFor'],
  ['controlInterlock', 'function controlInterlockResult'],
  ['controlState', 'function controlWidgetState'],
  ['custom', 'function renderCustomWidgets'],
  ['html', 'function customWidgetHtml'],
  ['render', 'function render(now)'],
  ['normalize', 'function normalizeWidget'],
  ['duplicate', 'function duplicateSelectedWidget']
].map(([name, marker]) => [name, functionSource(html, marker)]));

assert.ok(compact(src.render).includes(compact("if(dataRevision!==lastHeavyRenderRevision||now-lastHeavyRenderAt>=250){renderCustomWidgets();lastHeavyRenderRevision=dataRevision;lastHeavyRenderAt=now;}")), 'custom widgets render on each new accepted revision or the 250 ms fallback');
assert.ok(compact(src.custom).includes(compact("performanceProfile==='full'&&!editMode?'.page.active .customWidget[data-editor-id]':'.customWidget[data-editor-id]'")), 'Full/non-edit selects active-page custom widgets only');
assert.ok(compact(src.html).includes("if(type==='control')") && compact(src.html).includes("if(type==='graph')") && compact(src.html).includes("if(type==='radial')") && compact(src.html).includes("if(type==='bar')"), 'custom HTML has the five editor widget families');
assert.ok(compact(src.duplicate).includes(compact("['number','bar','radial','graph','control'].includes(widget.type)")), 'duplicate preserves the five supported custom widget types');
assert.ok(src.normalize.includes("'tach','history','graph','control'"), 'layout normalization still accepts built-in tach/history types');
assert.ok(compact(src.custom).includes(compact('const segments=[...card.querySelectorAll(\'[data-role="segments"] span\')];segments.forEach')), 'radial segment work is performed during ordinary custom rendering');
assert.ok(compact(src.custom).endsWith(compact("const activePageId=performanceProfile==='full'&&!editMode?document.querySelector('.page.active')?.id:null;renderLayoutVisualStates(activePageId||null);}")), 'every custom render ends with a layout-wide visual-state pass');

function trackedElement(kind = 'generic') {
  const stats = { textWrites: 0, styleWrites: 0, classToggles: 0, attributeWrites: 0, innerHtmlWrites: 0 };
  let text = '';
  let inner = '';
  const styleTarget = {};
  const element = {
    kind,
    stats,
    dataset: {},
    style: new Proxy(styleTarget, {
      set(target, key, value) { stats.styleWrites++; target[key] = value; return true; },
      get(target, key) { return target[key]; }
    }),
    classList: { toggle() { stats.classToggles++; } },
    setAttribute() { stats.attributeWrites++; }
  };
  Object.defineProperty(element, 'textContent', {
    get() { return text; },
    set(value) { stats.textWrites++; text = String(value); }
  });
  Object.defineProperty(element, 'innerHTML', {
    get() { return inner; },
    set(value) { stats.innerHtmlWrites++; inner = String(value); }
  });
  return element;
}

function makeCard(widget, pageId, counters) {
  const card = trackedElement('card');
  card.dataset = { editorId: widget.id };
  const value = trackedElement('value');
  const source = trackedElement('source');
  const bar = trackedElement('bar');
  const arc = trackedElement('arc');
  const badge = trackedElement('badge');
  const controlSurface = trackedElement('controlSurface');
  controlSurface.dataset.controlMode = widget.controlMode || 'guarded';
  const controlState = trackedElement('controlState');
  const controlSubstate = trackedElement('controlSubstate');
  const controlBadge = trackedElement('controlBadge');
  const step = trackedElement('step');
  const choices = Array.from({ length: (widget.controlOptions || []).length }, (_, index) => {
    const choice = trackedElement('choice');
    choice.dataset.controlChoice = String(index);
    return choice;
  });
  const segments = Array.from({ length: 10 }, () => trackedElement('segment'));
  card.closest = selector => selector === '.page' ? { id: pageId } : null;
  card.querySelector = selector => {
    counters.cardQueries++;
    const map = {
      '[data-role="value"]': value,
      '[data-role="source"]': source,
      '[data-role="bar"]': widget.type === 'bar' ? bar : null,
      '.customRadialArc': widget.type === 'radial' ? arc : null,
      '[data-role="visual-state"]': badge,
      '.controlSurface': widget.type === 'control' ? controlSurface : null,
      '[data-role="control-state"]': widget.type === 'control' ? controlState : null,
      '[data-role="control-substate"]': widget.type === 'control' ? controlSubstate : null,
      '.controlStepValue': widget.type === 'control' && widget.controlMode === 'stepper' ? step : null,
      '.controlInterlockBadge': widget.type === 'control' ? controlBadge : null
    };
    return Object.prototype.hasOwnProperty.call(map, selector) ? map[selector] : null;
  };
  card.querySelectorAll = selector => {
    counters.cardQueryAll++;
    if (selector === '[data-role="segments"] span') return widget.type === 'radial' ? segments : [];
    if (selector === '[data-control-choice]') return widget.type === 'control' ? choices : [];
    return [];
  };
  card.parts = { value, source, bar, arc, badge, controlSurface, controlState, controlSubstate, controlBadge, step, choices, segments };
  return card;
}

function makeWidget(id, type, extra = {}) {
  return {
    id,
    type,
    channel: 'rpm',
    label: id,
    unit: 'rpm',
    decimals: 0,
    min: 0,
    max: 7000,
    graphChannels: ['rpm', 'map'],
    graphWindow: 30,
    graphScaleMode: 'shared-fixed',
    graphMin: 0,
    graphMax: 7000,
    graphLegend: true,
    graphCurrent: true,
    graphReference: null,
    controlMode: 'guarded',
    controlState: 'unknown',
    controlOptions: ['LOW', 'NORMAL', 'HIGH'],
    controlStepValue: 0,
    controlInterlock: '',
    attentionLow: null,
    attentionHigh: null,
    warningLow: null,
    warningHigh: null,
    criticalLow: null,
    criticalHigh: null,
    ...extra
  };
}

function runFixture({ pages, profile = 'full', editMode = false, activePage = pages[0].id, repeats = 1 }) {
  const counters = {
    selectorQueries: 0,
    visualQueries: 0,
    cardQueries: 0,
    cardQueryAll: 0,
    channelValid: 0,
    visualCalls: 0,
    graphCalls: 0,
    interlockEvaluations: 0,
    ensureHandles: 0,
    htmlRebuilds: 0
  };
  const layout = { pages };
  const cards = new Map();
  for (const page of pages) {
    for (const widget of page.widgets) {
      if (widget.kind === 'builtin') continue;
      cards.set(`${page.id}:${widget.id}`, makeCard(widget, page.id, counters));
    }
  }
  const customCards = (all) => pages.flatMap(page => page.widgets
    .filter(widget => widget.kind !== 'builtin' && (all || page.id === activePage))
    .map(widget => cards.get(`${page.id}:${widget.id}`)));
  const document = {
    querySelectorAll(selector) {
      counters.selectorQueries++;
      if (selector === '.page.active .customWidget[data-editor-id]') return customCards(false);
      if (selector === '.customWidget[data-editor-id]') return customCards(true);
      return [];
    },
    querySelector(selector) {
      if (selector === '.page.active') return { id: activePage };
      counters.visualQueries++;
      const match = /^#([^ ]+) \[data-editor-id="([^"]+)"\]$/.exec(selector);
      if (!match) return null;
      const existing=cards.get(`${match[1]}:${match[2]}`);if(existing)return existing;const builtin=trackedElement('builtinCard');const badge=trackedElement('builtinBadge');builtin.querySelector=selector=>selector==='[data-role="visual-state"]'?badge:null;return builtin;
    }
  };
  const context = {
    console, Math, Number, Object, Map, CSS: { escape: String },
    performanceProfile: profile,
    editMode,
    document,
    source: 'LIVE',
    data: { rpm: 3500, map: 100 },
    channelState: { rpm: { source: 'LIVE' }, map: { source: 'LIVE' } },
    CHANNEL_META: { rpm: { label: 'RPM', unit: 'rpm' }, map: { label: 'MAP', unit: 'kPa' } },
    RADIAL_ARC_LENGTH: 75,
    controlRuntime: new Map(),
    currentLayoutModel: () => layout,
    findWidget(model, pageId, widgetId) { return model.pages.find(page => page.id === pageId)?.widgets.find(widget => widget.id === widgetId); },
    channelValid(key) { counters.channelValid++; return Number.isFinite(Number(context.data[key])); },
    clamp: (value, min, max) => Math.min(max, Math.max(min, value)),
    renderGraphWidget() { counters.graphCalls++; },
    expressionResult() { counters.interlockEvaluations++; return { value: true }; },
    customWidgetHtml() { counters.htmlRebuilds++; return ''; },
    ensureEditorHandles() { counters.ensureHandles++; }
  };
  vm.createContext(context);
  vm.runInContext(`${src.visualState}\n${src.applyVisual}\n${src.layoutVisual}\n${src.controlRuntime}\n${src.controlInterlock}\n${src.controlState}\n${src.custom}\nconst __originalApply=applyWidgetVisualState;applyWidgetVisualState=function(card,widget,value){__counters.visualCalls++;return __originalApply(card,widget,value);};this.run=renderCustomWidgets;`, Object.assign(context, { __counters: counters }));
  for (let index = 0; index < repeats; index++) context.run();
  const totals = [...cards.values()].reduce((sum, card) => {
    const parts = [card, ...Object.values(card.parts).flat()];
    for (const part of parts) {
      if (!part?.stats) continue;
      for (const key of ['textWrites','styleWrites','classToggles','attributeWrites','innerHtmlWrites']) sum[key] += part.stats[key];
    }
    return sum;
  }, { textWrites: 0, styleWrites: 0, classToggles: 0, attributeWrites: 0, innerHtmlWrites: 0 });
  return { counters, totals, cards };
}

function page(id, widgets) { return { id, widgets }; }

for (const count of [1, 8, 32]) {
  const widgets = Array.from({ length: count }, (_, index) => makeWidget(`n${index}`, 'number'));
  const result = runFixture({ pages: [page('page-a', widgets)] });
  assert.strictEqual(result.counters.visualCalls, count * 2, `${count} number widgets receive two visual-state passes each`);
  assert.strictEqual(result.counters.channelValid, count * 2, `${count} number widgets validate channels twice each`);
  assert.strictEqual(result.counters.visualQueries, count, `${count} number widgets trigger one layout visual DOM lookup each`);
  assert.strictEqual(result.totals.textWrites, count * 4, `${count} number widgets perform four text assignments each`);
}

{
  const widgets = [makeWidget('n','number'), makeWidget('b','bar'), makeWidget('r','radial',{radialSegments:false}), makeWidget('g','graph'), makeWidget('c','control',{controlInterlock:'rpm > 0'})];
  const result = runFixture({ pages: [page('page-a', widgets)] });
  assert.strictEqual(result.counters.graphCalls, 1, 'one graph widget invokes one graph renderer');
  assert.strictEqual(result.counters.interlockEvaluations, 1, 'one control interlock is evaluated once');
  assert.strictEqual(result.cards.get('page-a:b').parts.bar.stats.styleWrites, 2, 'horizontal bar writes height and width each render');
  assert.strictEqual(result.cards.get('page-a:r').parts.arc.stats.styleWrites, 1, 'radial writes arc progress each render');
  assert.strictEqual(result.cards.get('page-a:r').parts.segments.reduce((sum, item) => sum + item.stats.classToggles, 0), 10, 'radial toggles all ten segments even when segments are hidden');
  assert.strictEqual(result.cards.get('page-a:c').parts.controlSurface.stats.classToggles, 8, 'control toggles all eight runtime-state classes each render');
  assert.strictEqual(result.cards.get('page-a:c').parts.choices.reduce((sum, item) => sum + item.stats.classToggles, 0), 3, 'control selector choices are checked each render');
}

{
  const active = Array.from({ length: 4 }, (_, index) => makeWidget(`a${index}`, 'number'));
  const hidden = Array.from({ length: 4 }, (_, index) => makeWidget(`h${index}`, 'number'));
  const full = runFixture({ pages: [page('page-a', active), page('page-b', hidden)], profile: 'full', editMode: false, activePage: 'page-a' });
  const legacy = runFixture({ pages: [page('page-a', active), page('page-b', hidden)], profile: 'legacy', editMode: false, activePage: 'page-a' });
  const editing = runFixture({ pages: [page('page-a', active), page('page-b', hidden)], profile: 'full', editMode: true, activePage: 'page-a' });
  assert.strictEqual(full.counters.visualCalls, 8, 'Full/non-edit renders and visually evaluates active-page custom widgets only');
  assert.strictEqual(legacy.counters.visualCalls, 16, 'Legacy renders and visually evaluates custom widgets on all pages');
  assert.strictEqual(editing.counters.visualCalls, 16, 'Edit Mode renders and visually evaluates custom widgets on all pages');
}

{
  const custom = [makeWidget('c0','number'), makeWidget('c1','bar')];
  const builtins = [makeWidget('b0','number',{kind:'builtin'}), makeWidget('b1','radial',{kind:'builtin'}), makeWidget('b2','number',{kind:'builtin'})];
  const result = runFixture({ pages: [page('page-a', [...custom, ...builtins])] });
  assert.strictEqual(result.counters.visualCalls, 7, 'two custom branch passes plus five layout visual-state passes');
  assert.strictEqual(result.counters.visualQueries, 5, 'layout visual-state pass queries custom and built-in widgets');
}

{
  const widgets = [makeWidget('n0','number'), makeWidget('n1','number')];
  const once = runFixture({ pages: [page('page-a', widgets)], repeats: 1 });
  const twice = runFixture({ pages: [page('page-a', widgets)], repeats: 2 });
  assert.strictEqual(twice.totals.textWrites, once.totals.textWrites * 2, 'unchanged number values/source/badges are assigned again on repeated renders');
  assert.strictEqual(twice.totals.classToggles, once.totals.classToggles * 2, 'unchanged class-state work repeats linearly');
}

console.log('Custom widget scaling characterization passed.');
