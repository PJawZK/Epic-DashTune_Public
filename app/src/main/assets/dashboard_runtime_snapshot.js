(function (root, factory) {
  const api = factory(root);
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.EpicDashRuntimeSnapshot = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function (root) {
  'use strict';

  const PATCH_VERSION = 1;
  const DERIVED_COORDINATOR_KEY = '__epicDashDerivedEvaluationCoordinator';
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

  autoInstallDerivedEvaluationOwnership(root);

  return {
    PATCH_VERSION,
    createPublisher,
    applyPatch,
    createDerivedEvaluationCoordinator,
    autoInstallDerivedEvaluationOwnership
  };
});
