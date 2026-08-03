'use strict';

const assert = require('assert');
const runtime = require('../../main/assets/dashboard_runtime_snapshot.js');

const publisher = runtime.createPublisher({
  identityStableKeys: ['layout', 'selfTest', 'lastMsl']
});

const layout = { name: 'Daily', widgets: [{ id: 'rpm' }] };
const selfTest = { status: 'pass', checks: [{ name: 'A', passed: true }] };
const first = {
  source: 'LIVE',
  layout,
  selfTest,
  channelValues: { rpm: 800 }
};

const firstPayload = JSON.parse(publisher.encode(first));
assert.strictEqual(firstPayload.runtimePatchVersion, 1);
assert.strictEqual(firstPayload.complete, true);
let merged = runtime.applyPatch({ staleFutureKey: true }, firstPayload);
assert.deepStrictEqual(merged, first);

const secondPayload = JSON.parse(publisher.encode({
  source: 'LIVE',
  layout,
  selfTest,
  channelValues: { rpm: 900 }
}));
assert.strictEqual(secondPayload.runtimePatchVersion, 1);
assert.strictEqual(secondPayload.complete, false);
assert.deepStrictEqual(Object.keys(secondPayload.sections), ['channelValues']);
merged = runtime.applyPatch(merged, secondPayload);
assert.strictEqual(merged.channelValues.rpm, 900);
assert.deepStrictEqual(merged.layout, layout);

const replacementLayout = { name: 'Drift', widgets: [{ id: 'tach' }] };
const thirdPayload = JSON.parse(publisher.encode({
  source: 'MSL',
  layout: replacementLayout,
  selfTest,
  channelValues: { rpm: 2500 }
}));
assert.strictEqual(thirdPayload.sections.source, 'MSL');
assert.deepStrictEqual(thirdPayload.sections.layout, replacementLayout);
merged = runtime.applyPatch(merged, thirdPayload);
assert.strictEqual(merged.layout.name, 'Drift');

const mutable = { count: 1 };
const dynamicPublisher = runtime.createPublisher();
dynamicPublisher.encode({ history: mutable });
mutable.count = 2;
const mutablePayload = JSON.parse(dynamicPublisher.encode({ history: mutable }));
assert.strictEqual(mutablePayload.sections.history.count, 2);

dynamicPublisher.reset();
const resetPayload = JSON.parse(dynamicPublisher.encode({ history: mutable }));
assert.strictEqual(resetPayload.complete, true);
assert.strictEqual(resetPayload.sections.history.count, 2);

function createFakeBrowser() {
  const frameQueue = [];
  let domReadyHandler = null;
  const root = {
    document: {
      readyState: 'loading',
      addEventListener(name, handler) {
        if (name === 'DOMContentLoaded') domReadyHandler = handler;
      }
    },
    setTimeout(handler) {
      handler();
      return 1;
    },
    requestAnimationFrame(handler) {
      frameQueue.push(handler);
      return frameQueue.length;
    }
  };

  return {
    root,
    fireDomReady() {
      root.document.readyState = 'complete';
      assert.ok(domReadyHandler, 'DOMContentLoaded handler was registered');
      domReadyHandler();
    },
    runFrame(timestamp = 16) {
      assert.ok(frameQueue.length, 'an animation frame is queued');
      frameQueue.shift()(timestamp);
    }
  };
}

{
  const browser = createFakeBrowser();
  const { root } = browser;
  const coordinator = runtime.createDerivedEvaluationCoordinator(root);
  let derivedEvaluations = 0;
  let customMathEvaluations = 0;
  let dataRevision = 0;
  let lastLogicRevision = -1;

  coordinator.scheduleInstall();
  root.updateDerived = function () {
    derivedEvaluations++;
    customMathEvaluations++;
  };
  root.EpicDashNativeUpdate = function (payload) {
    if (payload.rejected) return false;
    if (payload.localData) {
      dataRevision++;
      root.updateDerived();
    }
    if (payload.connected) {
      dataRevision++;
      root.updateDerived();
    }
    return true;
  };

  function tick() {
    if (dataRevision !== lastLogicRevision) {
      root.updateDerived();
      lastLogicRevision = dataRevision;
    }
    root.requestAnimationFrame(tick);
  }
  root.requestAnimationFrame(tick);
  browser.fireDomReady();
  assert.strictEqual(coordinator.state().installed, true);

  assert.strictEqual(root.EpicDashNativeUpdate({ localData: true, connected: true }), true);
  assert.strictEqual(derivedEvaluations, 1, 'one evaluation occurs when the envelope completes');
  assert.strictEqual(customMathEvaluations, 1, 'custom math shares the single derived pass');
  browser.runFrame();
  assert.strictEqual(derivedEvaluations, 1, 'the matching animation-frame pass is skipped');

  root.EpicDashNativeUpdate({ localData: true, connected: true });
  root.EpicDashNativeUpdate({ localData: true, connected: true });
  assert.strictEqual(derivedEvaluations, 3, 'each accepted envelope evaluates exactly once');
  browser.runFrame();
  assert.strictEqual(derivedEvaluations, 3, 'multiple envelopes before one frame still skip only the duplicate frame pass');

  root.EpicDashNativeUpdate({ rejected: true });
  browser.runFrame();
  assert.strictEqual(derivedEvaluations, 3, 'rejected envelopes do not evaluate derived channels');

  root.updateDerived();
  assert.strictEqual(derivedEvaluations, 4, 'direct non-native callers remain immediate');
}

{
  const browser = createFakeBrowser();
  const { root } = browser;
  const coordinator = runtime.createDerivedEvaluationCoordinator(root);
  let derivedEvaluations = 0;

  coordinator.scheduleInstall();
  root.updateDerived = function () {
    derivedEvaluations++;
  };
  root.EpicDashNativeUpdate = function () {
    root.updateDerived();
    throw new Error('native failure');
  };
  browser.fireDomReady();

  assert.throws(() => root.EpicDashNativeUpdate({}), /native failure/);
  assert.strictEqual(derivedEvaluations, 0, 'failed native processing does not publish a derived pass');
  root.updateDerived();
  assert.strictEqual(derivedEvaluations, 1, 'suppression state is restored after an exception');
}

console.log('dashboard_runtime_snapshot_test: PASS');
