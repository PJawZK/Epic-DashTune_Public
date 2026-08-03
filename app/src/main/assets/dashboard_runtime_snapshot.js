(function (root, factory) {
  const api = factory(root);
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.EpicDashRuntimeSnapshot = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (root) {
  'use strict';

  const PATCH_VERSION = 1;
  const DERIVED_COORDINATOR_KEY = '__epicDashDerivedEvaluationCoordinator';
  const TPS_COORDINATOR_KEY = '__epicDashTpsTraceOwnershipCoordinator';
  const RAF_WRAPPED_KEY = '__epicDashDerivedEvaluationRafWrapped';

  function createPublisher(options = {}) {
    const identityStableKeys = new Set(options.identityStableKeys || []);
    const previousValues = new Map();
    const previousJson = new Map();
    let initialized = false;

    function encode(snapshot) {
      const complete = !initialized;
      const sections = {};
      for (const [key, value] of Object.entries(snapshot || {})) {
        if (initialized && identityStableKeys.has(key) && previousValues.get(key) === value) {
          continue;
        }

        const serialized = JSON.stringify(value);
        if (!initialized || previousJson.get(key) !== serialized) sections[key] = value;
        previousValues.set(key, value);
        previousJson.set(key, serialized);
      }
      initialized = true;
      return JSON.stringify({ runtimePatchVersion: PATCH_VERSION, complete, sections });
    }

    function reset() {
      previousValues.clear();
      previousJson.clear();
      initialized = false;
    }

    return { encode, reset };
  }

  function applyPatch(current, payload) {
    if (!payload || payload.runtimePatchVersion !== PATCH_VERSION || !payload.sections) {
      return { ...(payload || {}) };
    }
    const next = payload.complete ? {} : { ...(current || {}) };
    for (const [key, value] of Object.entries(payload.sections)) next[key] = value;
    return next;
  }

  function tpsRenderTargets(input = {}) {
    if (input.performanceProfile !== 'full' || input.editMode === true) return null;
    const activePageId = String(input.activePageId || '');
    return {
      daily: activePageId === 'page-daily',
      drift: activePageId === 'page-drift',
      boost: activePageId === 'page-boost',
      analysis: activePageId === 'page-analysis',
      diagnostics: activePageId === 'page-diagnostics'
    };
  }

  function renderTpsTraceForTargets(targets, environment) {
    if (!environment) throw new Error('A TPS render environment is required');
    const all = targets == null;
    const daily = all || targets.daily === true;
    const drift = all || targets.drift === true;
    const boost = all || targets.boost === true;
    const analysis = all || targets.analysis === true;
    const diagnostics = all || targets.diagnostics === true;
    const direct = environment.channelValid('tps', false)
      ? Number(environment.data.tps)
      : Number.NaN;

    if (Number.isFinite(direct)) {
      if (daily) environment.setNodeText('tpsDaily', direct.toFixed(1));
      if (drift) environment.setNodeText('tps', '' + direct.toFixed(1));
      if (boost) environment.setNodeText('tpsBoost', direct.toFixed(1));
      const markerLeft = `${environment.tpsVisualPercent(direct)}%`;
      if (daily) environment.setStyle('tpsDailyMarker', 'left', markerLeft);
      if (drift) environment.setStyle('tpsBar', 'left', markerLeft);
      if (boost) environment.setStyle('tpsBoostMarker', 'left', markerLeft);
    } else {
      if (daily) environment.setNodeText('tpsDaily', '—');
      if (drift) environment.setNodeText('tps', '—');
      if (boost) environment.setNodeText('tpsBoost', '—');
    }

    if (analysis || diagnostics) {
      const native = environment.tpsTraceState.native || {};
      const hexByte = value => Number.isFinite(Number(value))
        ? Number(value).toString(16).toUpperCase().padStart(2, '0')
        : '—';
      const line = `Raw bytes ${hexByte(native.rawByte0)} ${hexByte(native.rawByte1)} • raw ${Number.isFinite(Number(native.rawNumeric)) ? Number(native.rawNumeric) : '—'} • INI ${Number.isFinite(Number(native.decoded)) ? Number(native.decoded).toFixed(3) : '—'}%
Profile TPSValue ${Number.isFinite(Number(native.profileTpsValue)) ? Number(native.profileTpsValue).toFixed(3) : '—'}% • canonical ${Number.isFinite(Number(native.canonical)) ? Number(native.canonical).toFixed(3) : '—'}% • JS ${Number.isFinite(Number(environment.tpsTraceState.accepted)) ? Number(environment.tpsTraceState.accepted).toFixed(3) : '—'}% • painted ${Number.isFinite(Number(environment.tpsTraceState.painted)) ? Number(environment.tpsTraceState.painted).toFixed(3) : '—'}%
rawTps1Primary ${Number.isFinite(Number(native.rawTps1Primary)) ? Number(native.rawTps1Primary).toFixed(4) : '—'} • tpsADC ${Number.isFinite(Number(native.tpsADC)) ? Number(native.tpsADC).toFixed(1) : '—'} • pedal ${Number.isFinite(Number(native.throttlePedalPosition)) ? Number(native.throttlePedalPosition).toFixed(2) : '—'} • intent ${Number.isFinite(Number(native.DriverThrottleIntent)) ? Number(native.DriverThrottleIntent).toFixed(2) : '—'}
Session ${environment.activeUsbSessionId} • accepted rev ${environment.tpsTraceState.acceptedRevision} • painted rev ${environment.tpsTraceState.paintedRevision} • bridge ${environment.bridgeTransitMs >= 0 ? environment.bridgeTransitMs.toFixed(1) + ' ms' : '—'}`;
      if (analysis) environment.setNodeText('tpsAnalysisPipeline', line);
      if (diagnostics) environment.setNodeText('tpsDiagnosticPipeline', line);
    }

    if (daily) {
      environment.setNodeText(
        'tpsDailyMeta',
        `No smoothing • no clamp • rev ${environment.tpsTraceState.paintedRevision}`
      );
    }
  }

  function createTpsTraceOwnershipCoordinator(target, readState) {
    if (!target) throw new Error('A global target is required');

    let installed = false;
    let originalRenderTpsTrace = null;
    const stateReader = typeof readState === 'function' ? readState : function () {
      const activePageId = target.document.querySelector('.page.active')?.id || 'page-daily';
      return {
        performanceProfile,
        editMode,
        activePageId,
        environment: {
          channelValid: (key, requireFresh) => channelValid(key, requireFresh),
          data,
          tpsTraceState,
          activeUsbSessionId,
          bridgeTransitMs,
          setNodeText: (id, value) => setNodeText(id, value),
          setStyle: (id, key, value) => setStyle(id, key, value),
          tpsVisualPercent: value => tpsVisualPercent(value)
        }
      };
    };

    function install() {
      if (installed) return true;
      if (typeof target.renderTpsTrace !== 'function') return false;

      originalRenderTpsTrace = target.renderTpsTrace;
      target.renderTpsTrace = function () {
        const state = stateReader();
        const targets = tpsRenderTargets(state);
        return renderTpsTraceForTargets(targets, state.environment);
      };
      installed = true;
      return true;
    }

    function scheduleInstall() {
      const documentObject = target.document;
      if (!documentObject) return false;

      let attempts = 0;
      const attempt = function () {
        if (install()) return;
        attempts++;
        if (attempts < 20 && typeof target.setTimeout === 'function') {
          target.setTimeout(attempt, 0);
        }
      };

      if (documentObject.readyState === 'loading' &&
          typeof documentObject.addEventListener === 'function') {
        documentObject.addEventListener('DOMContentLoaded', attempt, { once: true });
      } else {
        attempt();
      }
      return true;
    }

    function state() {
      return { installed, originalRenderTpsTrace };
    }

    return { install, scheduleInstall, state };
  }

  /**
   * Gives one accepted native envelope one derived/custom-math evaluation.
   *
   * The existing dashboard calls updateDerived() while accepting local values, while accepting ECU
   * values, and again from the following animation-frame logic pass. The coordinator suppresses only
   * calls made inside EpicDashNativeUpdate(), evaluates once immediately after that native envelope
   * completes, and skips only the matching animation-frame evaluation. Direct callers used by DEMO,
   * MSL, disconnect handling, source changes, and self-test remain immediate.
   */
  function createDerivedEvaluationCoordinator(target) {
    if (!target) throw new Error('A global target is required');

    let installed = false;
    let inNativeUpdate = 0;
    let inAnimationFrame = 0;
    let suppressedNativeCalls = 0;
    let skipNextAnimationFrameEvaluation = false;
    let originalUpdateDerived = null;
    let originalNativeUpdate = null;
    let originalRequestAnimationFrame = null;

    function wrapAnimationFrame() {
      const current = target.requestAnimationFrame;
      if (typeof current !== 'function') return false;
      if (current[RAF_WRAPPED_KEY]) return true;

      originalRequestAnimationFrame = current;
      const wrapped = function (callback) {
        return originalRequestAnimationFrame.call(target, function (timestamp) {
          inAnimationFrame++;
          try {
            return callback(timestamp);
          } finally {
            inAnimationFrame--;
          }
        });
      };
      Object.defineProperty(wrapped, RAF_WRAPPED_KEY, { value: true });
      target.requestAnimationFrame = wrapped;
      return true;
    }

    function install() {
      if (installed) return true;
      if (typeof target.updateDerived !== 'function' ||
          typeof target.EpicDashNativeUpdate !== 'function') return false;

      originalUpdateDerived = target.updateDerived;
      originalNativeUpdate = target.EpicDashNativeUpdate;

      target.updateDerived = function (...args) {
        if (inNativeUpdate > 0) {
          suppressedNativeCalls++;
          return undefined;
        }
        if (inAnimationFrame > 0 && skipNextAnimationFrameEvaluation) {
          skipNextAnimationFrameEvaluation = false;
          return undefined;
        }
        return originalUpdateDerived.apply(this, args);
      };

      target.EpicDashNativeUpdate = function (...args) {
        const outermost = inNativeUpdate === 0;
        if (outermost) suppressedNativeCalls = 0;
        inNativeUpdate++;
        let completed = false;
        try {
          const result = originalNativeUpdate.apply(this, args);
          completed = true;
          return result;
        } finally {
          inNativeUpdate--;
          if (outermost) {
            const shouldEvaluate = completed && suppressedNativeCalls > 0;
            suppressedNativeCalls = 0;
            if (shouldEvaluate) {
              originalUpdateDerived.call(target);
              skipNextAnimationFrameEvaluation = true;
            }
          }
        }
      };

      installed = true;
      return true;
    }

    function scheduleInstall() {
      wrapAnimationFrame();
      const documentObject = target.document;
      if (!documentObject) return false;

      let attempts = 0;
      const attempt = function () {
        if (install()) return;
        attempts++;
        if (attempts < 20 && typeof target.setTimeout === 'function') {
          target.setTimeout(attempt, 0);
        }
      };

      if (documentObject.readyState === 'loading' &&
          typeof documentObject.addEventListener === 'function') {
        documentObject.addEventListener('DOMContentLoaded', attempt, { once: true });
      } else {
        attempt();
      }
      return true;
    }

    function state() {
      return {
        installed,
        inNativeUpdate,
        inAnimationFrame,
        suppressedNativeCalls,
        skipNextAnimationFrameEvaluation
      };
    }

    return { install, scheduleInstall, state };
  }

  function autoInstallDerivedEvaluationOwnership(target = root) {
    if (!target || !target.document) return null;
    if (target[DERIVED_COORDINATOR_KEY]) return target[DERIVED_COORDINATOR_KEY];

    const coordinator = createDerivedEvaluationCoordinator(target);
    try {
      Object.defineProperty(target, DERIVED_COORDINATOR_KEY, {
        value: coordinator,
        configurable: true
      });
    } catch (_) {
      target[DERIVED_COORDINATOR_KEY] = coordinator;
    }
    coordinator.scheduleInstall();
    return coordinator;
  }

  function autoInstallTpsTraceOwnership(target = root) {
    if (!target || !target.document) return null;
    if (target[TPS_COORDINATOR_KEY]) return target[TPS_COORDINATOR_KEY];

    const coordinator = createTpsTraceOwnershipCoordinator(target);
    try {
      Object.defineProperty(target, TPS_COORDINATOR_KEY, {
        value: coordinator,
        configurable: true
      });
    } catch (_) {
      target[TPS_COORDINATOR_KEY] = coordinator;
    }
    coordinator.scheduleInstall();
    return coordinator;
  }

  autoInstallDerivedEvaluationOwnership(root);
  autoInstallTpsTraceOwnership(root);

  return {
    PATCH_VERSION,
    createPublisher,
    applyPatch,
    tpsRenderTargets,
    renderTpsTraceForTargets,
    createTpsTraceOwnershipCoordinator,
    autoInstallTpsTraceOwnership,
    createDerivedEvaluationCoordinator,
    autoInstallDerivedEvaluationOwnership
  };
});
