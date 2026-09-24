/* TUNER_UI_INTERACTION_CLEANUP_V3
 * Interaction/performance cleanup layered after the locked Tuner runtime correction.
 * Runs inside the existing T4 workspace closure and deliberately reuses the current
 * semantic/native tuning authority. This file owns presentation-local state only.
 */

  const tunerInteractionCleanupStyle = document.createElement('style');
  tunerInteractionCleanupStyle.id = 't4-tuning-interaction-cleanup-style';
  tunerInteractionCleanupStyle.textContent = `
    .t4tw-cell[data-t4tw-index],
    .t4tw-curve-row,
    .t4tw-curve-svg [data-curve-point],
    .t4tw-hierarchy-select,
    .t4tw-content-mode,
    .t4tw-btn{touch-action:manipulation}
    .t4tw-settings-command:disabled{display:none!important}
    .t4tw-table-3d-surface{position:absolute;inset:0;overflow:hidden}
    .t4tw-table-3d-surface svg{width:100%;height:100%;display:block}
  `;
  document.head.appendChild(tunerInteractionCleanupStyle);

  // Viewport changes are CSS/profile changes. Do not rebuild the complete Tuner DOM
  // merely because width/height crossed a runtime breakpoint.
  applyTunerViewportProfile = function(force = false) {
    const width = Math.max(1, window.innerWidth || document.documentElement.clientWidth || 1);
    const height = Math.max(1, window.innerHeight || document.documentElement.clientHeight || 1);
    const next = tunerProfileFor(width, height);
    const changed = next !== tunerViewportProfile;
    if (!force && !changed) return next;
    tunerViewportProfile = next;
    document.body.dataset.t4twViewport = next;
    page.dataset.t4twViewport = next;
    if (changed && page.classList.contains('active') && workspace?.status === 'ready') {
      tableFitPending = viewMode === 'tables';
      scheduleTunerGeometryRefresh(0);
    }
    return next;
  };

  // Maintain an index -> DOM-node map for the current grid. Range drag now updates
  // only cells whose selection/border state can actually have changed.
  let tunerSelectionNodeMap = new Map();
  let tunerSelectionNodeMarker = null;
  let tunerSelectionNodeCount = 0;
  let tunerSelectionSnapshot = new Set();
  let tunerSelectionPrimary = -1;

  function tunerRebuildSelectionNodeMap() {
    const nodes = Array.from(page.querySelectorAll('.t4tw-cell[data-t4tw-index]'));
    const marker = nodes[0] || null;
    if (marker === tunerSelectionNodeMarker && nodes.length === tunerSelectionNodeCount && (!marker || marker.isConnected)) return false;
    tunerSelectionNodeMap = new Map();
    for (const node of nodes) {
      const index = Number(node.dataset.t4twIndex);
      if (!Number.isFinite(index)) continue;
      if (!tunerSelectionNodeMap.has(index)) tunerSelectionNodeMap.set(index, []);
      tunerSelectionNodeMap.get(index).push(node);
    }
    tunerSelectionNodeMarker = marker;
    tunerSelectionNodeCount = nodes.length;
    tunerSelectionSnapshot = new Set();
    tunerSelectionPrimary = -1;
    return true;
  }

  function tunerSelectionTouchNeighborhood(target, index, xCount, yCount) {
    if (!Number.isFinite(index) || index < 0) return;
    target.add(index);
    if (xCount <= 0 || yCount <= 0) return;
    const x = index % xCount;
    const y = Math.floor(index / xCount);
    if (x > 0) target.add(index - 1);
    if (x < xCount - 1) target.add(index + 1);
    if (y > 0) target.add(index - xCount);
    if (y < yCount - 1) target.add(index + xCount);
  }

  function tunerApplySelectionNodeState(index, selectedSet, primary, xCount, yCount) {
    const nodes = tunerSelectionNodeMap.get(index) || [];
    const chosen = selectedSet.has(index);
    for (const node of nodes) {
      node.classList.toggle('selected', index === primary);
      node.classList.toggle('multiselected', chosen);
      node.classList.remove('sel-top','sel-bottom','sel-left','sel-right');
      if (chosen && selectedSurface?.kind === 'table' && xCount > 0 && yCount > 0) {
        const x = index % xCount;
        const y = Math.floor(index / xCount);
        if (y === 0 || !selectedSet.has(index - xCount)) node.classList.add('sel-top');
        if (y === yCount - 1 || !selectedSet.has(index + xCount)) node.classList.add('sel-bottom');
        if (x === 0 || !selectedSet.has(index - 1)) node.classList.add('sel-left');
        if (x === xCount - 1 || !selectedSet.has(index + 1)) node.classList.add('sel-right');
      }
    }
  }

  updateVisibleTableSelection = function() {
    const rebuilt = tunerRebuildSelectionNodeMap();
    const selectedSet = selectedArrayCells.size
      ? new Set(selectedArrayCells)
      : (selectedArrayCell >= 0 ? new Set([selectedArrayCell]) : new Set());
    const primary = selectedArrayCell;
    const xCount = Number(selectedSurface?.xCount || 0);
    const yCount = Number(selectedSurface?.yCount || 0);
    const touched = new Set();

    if (rebuilt) {
      for (const index of tunerSelectionNodeMap.keys()) touched.add(index);
    } else {
      for (const index of tunerSelectionSnapshot) {
        if (!selectedSet.has(index)) tunerSelectionTouchNeighborhood(touched, index, xCount, yCount);
      }
      for (const index of selectedSet) {
        if (!tunerSelectionSnapshot.has(index)) tunerSelectionTouchNeighborhood(touched, index, xCount, yCount);
      }
      if (tunerSelectionPrimary !== primary) {
        tunerSelectionTouchNeighborhood(touched, tunerSelectionPrimary, xCount, yCount);
        tunerSelectionTouchNeighborhood(touched, primary, xCount, yCount);
      }
    }

    for (const index of touched) tunerApplySelectionNodeState(index, selectedSet, primary, xCount, yCount);
    tunerSelectionSnapshot = selectedSet;
    tunerSelectionPrimary = primary;
  };

  // Keep 3D chrome/preset buttons persistent. Camera motion replaces only the SVG
  // surface instead of reconstructing the complete 3D host on every animation frame.
  renderInlineTable3d = function() {
    if (viewMode !== 'tables' || tablePresentationMode !== '3d') return;
    const viewport = document.getElementById('t4twInlineTableViewport');
    const grid = document.getElementById('t4twInlineTableGrid');
    if (!viewport || !grid || selectedSurface?.kind !== 'table') return;
    grid.style.display = 'none';
    let host = document.getElementById('t4twInlineTable3d');
    if (!host) {
      host = document.createElement('div');
      host.id = 't4twInlineTable3d';
      host.className = 't4tw-table-3d';
      viewport.appendChild(host);
      bindInlineTable3dGestures(host);
    }
    let surface = host.querySelector('.t4tw-table-3d-surface');
    if (!surface) {
      host.textContent = '';
      surface = document.createElement('div');
      surface.className = 't4tw-table-3d-surface';
      const hint = document.createElement('div');
      hint.className = 't4tw-table-3d-hint';
      hint.textContent = '3D VIEW • drag to rotate • pinch to zoom • view only';
      const presets = document.createElement('div');
      presets.className = 't4tw-table-3d-presets';
      for (const preset of ['iso','front','side','top']) {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.t4tw3dView = preset;
        button.textContent = preset.toUpperCase();
        button.onclick = event => {
          event.stopPropagation();
          table3dReset(preset);
        };
        presets.appendChild(button);
      }
      host.append(surface, hint, presets);
    }
    surface.innerHTML = buildInlineTable3dSvg();
    viewport.closest('.t4tw-table-main')?.classList.add('t4tw-table-3d-mode');
  };

  function tunerCurveManagerHost() {
    return page.querySelector('.t4tw-curve-manager');
  }

  function tunerRefreshCurveHeaderLocal() {
    const item = workspaceCurveById(curveActiveId);
    if (!item) return;
    const main = page.querySelector('.t4tw-curve-main');
    const activeName = main?.querySelector('.t4tw-curve-editbar .t4tw-table-selection b');
    if (activeName) activeName.textContent = String(item.title || item.id || 'Curve');
    const activeSeries = curveSeriesForItem(item);
    const sub = main?.querySelector('.t4tw-curve-sub');
    if (sub) {
      sub.textContent = [
        String(item.pointCount || '') + ' points',
        activeSeries?.xUnit ? ('X ' + activeSeries.xUnit) : '',
        activeSeries?.yUnit ? ('Y ' + activeSeries.yUnit) : '',
        curveMode === 'multi' ? (curveLoadedIds.length + ' loaded') : ''
      ].filter(Boolean).join(' • ');
    }
    const selectors = main?.querySelectorAll('.t4tw-hierarchy-selectors select');
    if (selectors && selectors.length >= 3) selectors[2].value = String(curveActiveId || '');
  }

  function tunerRefreshCurveLocal(allCurves, rebuildManager = false) {
    const manager = tunerCurveManagerHost();
    if (rebuildManager && manager) {
      manager.textContent = '';
      tunerCleanupBaseRenderCurveManager(manager, allCurves);
      tunerPatchCurveManagerLocal(manager, allCurves);
    }
    tunerRefreshCurveHeaderLocal();
    renderCurveGraph();
    refreshInlineCurveInspector();
  }

  function tunerPatchCurveManagerLocal(container, allCurves) {
    if (!container) return;
    const rows = Array.from(container.querySelectorAll('.t4tw-curve-row'));
    let rowIndex = 0;
    for (const id of curveLoadedIds) {
      const item = workspaceCurveById(id);
      if (!item) continue;
      const row = rows[rowIndex++];
      if (!row) continue;
      row.dataset.t4twCurveId = id;
      const visible = row.querySelector('input[type="checkbox"]');
      if (visible) {
        visible.onclick = event => event.stopPropagation();
        visible.onchange = () => {
          if (visible.checked) curveVisibility.add(id); else curveVisibility.delete(id);
          if (id === curveActiveId) curveVisibility.add(id);
          renderCurveGraph();
          refreshInlineCurveInspector();
        };
      }
      const remove = row.querySelector('.t4tw-curve-row-actions button');
      if (remove && id !== curveActiveId) {
        remove.onclick = event => {
          event.stopPropagation();
          curveLoadedIds = curveLoadedIds.filter(value => value !== id);
          curveVisibility.delete(id);
          tunerRefreshCurveLocal(allCurves, true);
        };
      }
      const activateLocal = () => {
        if (id === curveActiveId) return;
        if (activateCurve(id)) {
          curveVisibility.add(id);
          tunerRefreshCurveLocal(allCurves, true);
        }
      };
      row.onclick = event => {
        if (event.target === visible || event.target.closest('button')) return;
        activateLocal();
      };
      row.onkeydown = event => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          activateLocal();
        }
      };
    }

    const add = container.querySelector('.t4tw-curve-add');
    const addSelect = add?.querySelector('select');
    const addButton = add?.querySelector('button');
    if (add && addSelect && addButton) {
      addButton.onclick = () => {
        const id = String(addSelect.value || '');
        if (!id) return;
        if (!curveLoadedIds.includes(id)) curveLoadedIds.push(id);
        curveVisibility.add(id);
        add.open = false;
        tunerRefreshCurveLocal(allCurves, true);
      };
    }
  }

  const tunerCleanupBaseRenderCurveManager = renderCurveManager;
  renderCurveManager = function(container, allCurves) {
    tunerCleanupBaseRenderCurveManager(container, allCurves);
    tunerPatchCurveManagerLocal(container, allCurves);
  };

  function tunerPatchCurveHierarchySelector() {
    if (viewMode !== 'curves') return;
    const main = page.querySelector('.t4tw-curve-main');
    const selectors = main?.querySelectorAll('.t4tw-hierarchy-selectors select');
    if (!selectors || selectors.length < 3) return;
    const curveSelect = selectors[2];
    curveSelect.onchange = () => {
      hierarchyCurveId = String(curveSelect.value || '');
      const item = workspaceCurveById(hierarchyCurveId);
      if (!item) return;
      if (curveMode === 'single') {
        curveLoadedIds = [hierarchyCurveId];
        curveVisibility = new Set([hierarchyCurveId]);
      } else if (!curveLoadedIds.includes(hierarchyCurveId)) {
        curveLoadedIds.push(hierarchyCurveId);
        curveVisibility.add(hierarchyCurveId);
      }
      if (activateCurve(hierarchyCurveId)) tunerRefreshCurveLocal(workspace?.curves || [], true);
    };
  }

  const tunerCleanupBaseRenderCurvesMode = renderCurvesMode;
  renderCurvesMode = function(q) {
    tunerCleanupBaseRenderCurvesMode(q);
    tunerPatchCurveHierarchySelector();
  };

  // Live values remain full-rate, while the comparatively expensive top-chrome sync
  // is limited to a few updates per second. Explicit write/Burn state paths still call
  // syncTunerChrome directly and therefore remain immediate.
  let tunerChromeLastTelemetrySync = 0;
  renderTunerTelemetry = function() {
    if (!telemetryStrip || !page.classList.contains('active')) return;
    let snapshot = null;
    try { snapshot = window.EpicDashTunerTelemetrySnapshot?.() || null; } catch (_) {}
    if (!snapshot || !ensurePersistentTunerTelemetry()) return;
    for (const key of TUNER_TELEMETRY_KEYS) {
      const refs = tunerTelemetryNodeCache.get(key);
      if (!refs) continue;
      const label = snapshot.meta?.[key]?.label || refs.lastLabel || key;
      const value = snapshot.valid?.[key] === false ? '—' : formatTelemetryValue(snapshot.values?.[key], key);
      const unit = snapshot.meta?.[key]?.unit || '';
      if (refs.lastLabel !== label) { refs.label.textContent = label; refs.lastLabel = label; }
      if (refs.lastValue !== value) { refs.number.textContent = value; refs.lastValue = value; }
      if (refs.lastUnit !== unit) { refs.unit.textContent = unit; refs.lastUnit = unit; }
    }
    const now = performance.now();
    if (now - tunerChromeLastTelemetrySync >= 350) {
      tunerChromeLastTelemetrySync = now;
      syncTunerChrome();
    }
  };
