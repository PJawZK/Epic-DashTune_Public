/* TUNER_UI_RUNTIME_CORRECTION_V2
 * Behavioral correction layer for the locked EpicDash JZ Tuner presentation.
 * This source is injected inside the t4_tuning_workspace.js closure by
 * T4TuningWorkspaceSurface so it can reuse the existing semantic/native authority.
 * It deliberately does not create a transport, write path, TuneSnapshot reader,
 * profile authority, or Burn implementation.
 */

  const tunerRuntimeCorrectionStyle = document.createElement('style');
  tunerRuntimeCorrectionStyle.id = 't4-tuning-runtime-correction-style';
  tunerRuntimeCorrectionStyle.textContent = `
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-system-rail,
    body[data-t4tw-viewport="phone-portrait"] .t4tw-system-rail,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-system-rail{display:none!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-parameters-shell,
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-shell,
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-curve-shell,
    body[data-t4tw-viewport="phone-portrait"] .t4tw-parameters-shell,
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-shell,
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-shell,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-parameters-shell,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-shell,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-shell{grid-template-columns:minmax(0,1fr)!important}

    body[data-t4tw-viewport="tablet-portrait"] .t4tw-shell{padding:10px 12px 0}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-nav-row{grid-template-columns:1fr;gap:7px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-hierarchy-selectors{grid-template-columns:minmax(0,.78fr) 16px minmax(0,1fr) 16px minmax(0,1.25fr);gap:6px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-content-modes{order:2}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-inline-parameters>.t4tw-settings-root{grid-template-columns:1fr!important}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-curve-workspace{grid-template-columns:1fr;grid-template-rows:minmax(360px,1fr) auto}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-curve-manager{max-height:245px}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-head{flex-wrap:wrap;align-items:flex-start}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-table-view-controls{margin-left:auto}
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-telemetry{grid-template-columns:repeat(6,minmax(74px,1fr))}

    body[data-t4tw-viewport="phone-portrait"] .t4tw-appbar{grid-template-columns:minmax(0,1fr) auto;gap:6px;padding:6px 8px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-brand{font-size:17px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appnav{grid-column:1/-1;grid-row:2;gap:2px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appnav-btn{padding:6px 3px;font-size:10px;gap:4px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-appstatus{grid-column:1/-1;grid-row:3;justify-content:flex-start;overflow-x:auto;padding-bottom:1px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-ecu-copy{display:none}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-profile-chip{display:none}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-shell{padding:7px 7px 0}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-nav-row{grid-template-columns:1fr;gap:5px;margin-bottom:6px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-selectors{grid-template-columns:1fr!important;gap:4px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-chevron{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-select{min-height:37px;padding-top:7px;padding-bottom:7px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-content-mode{min-height:38px;font-size:10px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-head,
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-head{min-height:0;flex-wrap:wrap}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls{width:100%;justify-content:flex-end}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-viewport{min-height:330px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-workspace{grid-template-columns:1fr;grid-template-rows:minmax(300px,1fr) auto}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-manager{max-height:210px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar,
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-editbar{gap:4px;padding:6px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-telemetry{grid-template-columns:repeat(3,minmax(0,1fr));gap:4px}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-telemetry-slot:nth-child(n+7){display:none!important}

    body[data-t4tw-viewport="phone-landscape"] .t4tw-appbar{min-height:50px;grid-template-columns:150px minmax(330px,1fr) auto;gap:7px;padding:5px 8px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-brand{font-size:17px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-appnav{grid-column:2;grid-row:1;gap:2px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-appstatus{grid-column:3;grid-row:1;gap:4px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-profile-chip,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-ecu-copy{display:none}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-top-action{height:34px;padding:0 7px;font-size:10px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-shell{padding:6px 8px 0}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-nav-row{grid-template-columns:minmax(0,1.25fr) minmax(300px,.75fr);gap:7px;margin-bottom:5px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-selectors{grid-template-columns:minmax(0,.8fr) 12px minmax(0,1fr) 12px minmax(0,1.25fr);gap:3px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-select{min-height:34px;padding-top:5px;padding-bottom:5px;font-size:10px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-content-mode{min-height:34px;font-size:10px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-head,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-head{min-height:30px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-workspace{grid-template-columns:minmax(0,1fr) 190px;gap:6px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-manager{max-height:none}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-telemetry{grid-template-columns:repeat(6,minmax(64px,1fr));gap:4px;padding:5px 0 6px}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-telemetry-slot:nth-child(n+7){display:none!important}

    .t4tw-table-main.t4tw-table-3d-mode .t4tw-table-editbar{display:none!important}
    .t4tw-table-main.t4tw-table-3d-mode .t4tw-table-status{display:none!important}
    .t4tw-table-3d{height:100%;min-height:240px;position:relative;overflow:hidden;border-radius:8px;background:#030c12;touch-action:none;user-select:none}
    .t4tw-table-3d svg{width:100%;height:100%;display:block}
    .t4tw-table-3d-hint{position:absolute;left:10px;top:8px;font-size:9px;font-weight:800;letter-spacing:.5px;color:#8ca8b6;background:#07131ad9;border:1px solid #244553;border-radius:7px;padding:6px 8px;pointer-events:none}
    .t4tw-table-3d-presets{position:absolute;right:8px;top:8px;display:flex;gap:4px;flex-wrap:wrap;justify-content:flex-end;max-width:72%;z-index:2}
    .t4tw-table-3d-presets button{border:1px solid #31515f;border-radius:6px;background:#07131ae6;color:#dceaf0;padding:5px 7px;font-size:8px;font-weight:850}
    .t4tw-table-3d-axis{font:800 13px system-ui,sans-serif;fill:#b7cad3}
    .t4tw-table-3d-caption{font:700 10px system-ui,sans-serif;fill:#6f8c9a}
    .t4tw-runtime-hidden-affordance{display:none!important}
  `;
  document.head.appendChild(tunerRuntimeCorrectionStyle);

  let tunerViewportProfile = '';
  let tunerViewportTimer = 0;
  let tunerFitTimer = 0;
  function tunerProfileFor(width, height) {
    const landscape = width > height;
    if (landscape && height <= 620) return 'phone-landscape';
    if (!landscape && width <= 699) return 'phone-portrait';
    if (!landscape && width <= 1150) return 'tablet-portrait';
    return 'tablet-landscape';
  }
  function scheduleTunerGeometryRefresh(delay = 80) {
    clearTimeout(tunerFitTimer);
    tunerFitTimer = setTimeout(() => {
      requestAnimationFrame(() => requestAnimationFrame(() => {
        if (!page.classList.contains('active')) return;
        if (viewMode === 'tables') {
          if (tablePresentationMode === '3d') renderInlineTable3d();
          else fitInlineTable();
        } else if (viewMode === 'curves') renderCurveGraph();
      }));
    }, delay);
  }
  function applyTunerViewportProfile(force = false) {
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
      render();
      scheduleTunerGeometryRefresh(90);
    }
    return next;
  }
  function scheduleTunerViewportProfile() {
    clearTimeout(tunerViewportTimer);
    tunerViewportTimer = setTimeout(() => applyTunerViewportProfile(false), 90);
  }
  window.addEventListener('resize', scheduleTunerViewportProfile, {passive:true});
  window.addEventListener('orientationchange', () => setTimeout(() => applyTunerViewportProfile(true), 140), {passive:true});
  applyTunerViewportProfile(true);

  const tunerArrayDetailCache = new Map();
  const tunerSurfaceHierarchyCache = new Map();
  const baseGetSemanticArrayDetail = getSemanticArrayDetail;
  getSemanticArrayDetail = function(name) {
    const key = [workspace?.generation || -1, workspace?.tuneFingerprint || '', String(name || '')].join('|');
    if (tunerArrayDetailCache.has(key)) return tunerArrayDetailCache.get(key);
    const result = baseGetSemanticArrayDetail(name);
    if (result?.status === 'ready') tunerArrayDetailCache.set(key, result);
    return result;
  };
  const baseSurfaceHierarchy = surfaceHierarchy;
  surfaceHierarchy = function(kind, q) {
    const key = [workspace?.profileFingerprint || '', workspace?.tuneFingerprint || '', kind, String(q || '').toLowerCase()].join('|');
    if (tunerSurfaceHierarchyCache.has(key)) return tunerSurfaceHierarchyCache.get(key);
    const result = baseSurfaceHierarchy(kind, q);
    tunerSurfaceHierarchyCache.set(key, result);
    return result;
  };
  const baseRebuildWorkspaceIndex = rebuildWorkspaceIndex;
  rebuildWorkspaceIndex = function() {
    tunerArrayDetailCache.clear();
    tunerSurfaceHierarchyCache.clear();
    return baseRebuildWorkspaceIndex();
  };

  function dialogHasParameterTarget(dialogId, stack = [], depth = 0) {
    const id = String(dialogId || '');
    const dialog = workspaceDialog(id);
    if (!dialog || stack.includes(id) || depth > 8) return false;
    const nextStack = stack.concat(id);
    for (const entry of (dialog.entries || [])) {
      if (!conditionVisible(entry)) continue;
      const target = String(entry.target || '');
      if (entry.kind === 'field' && (workspaceScalar(target) || workspaceBitField(target))) return true;
      if (entry.kind === 'panel' && !workspaceSurface(target) && dialogHasParameterTarget(target, nextStack, depth + 1)) return true;
    }
    return false;
  }
  parameterFeatures = function(category) {
    return (category?.items || []).filter(item => {
      const id = String(item?.dialogId || '');
      return !!workspaceDialog(id) && dialogHasParameterTarget(id);
    });
  };
  const baseOpenSettingsDialog = openSettingsDialog;
  openSettingsDialog = function(item) {
    if (workspaceDialog(item?.dialogId)) return baseOpenSettingsDialog(item);
    meta.textContent = 'This INI item is a calibration surface, not a Parameter. Open it from Tables or Curves.';
  };
  renderSettingsDialogInto = function(container, dialogId, stack = [], depth = 0, inheritedEnabled = true) {
    const dialog = workspaceDialog(dialogId);
    if (!dialog || stack.includes(dialogId) || depth > 8) return;
    const section = document.createElement('section');
    section.className = 't4tw-settings-panel t4tw-settings-depth-' + depth + (depth === 0 ? ' t4tw-settings-root' : '');
    if (dialog.title || depth === 0) {
      const title = document.createElement('div');
      title.className = 't4tw-settings-panel-title';
      const titleText = dialog.title || dialog.id;
      if (depth > 0) {
        const icon = document.createElement('span'); icon.className = 't4tw-settings-panel-icon'; icon.textContent = parameterGroupGlyph(titleText);
        const label = document.createElement('span'); label.textContent = titleText;
        title.append(icon, label);
      } else title.textContent = titleText;
      section.appendChild(title);
    }
    let parameterCount = 0;
    for (const entry of (dialog.entries || [])) {
      if (!conditionVisible(entry)) continue;
      const target = String(entry.target || '');
      const entryEnabled = inheritedEnabled && conditionUsable(entry);
      if (entry.kind === 'field') {
        if (!(workspaceScalar(target) || workspaceBitField(target))) continue;
        section.appendChild(settingsFieldNode(entry, inheritedEnabled));
        parameterCount++;
      } else if (entry.kind === 'text') {
        const textNode = document.createElement('div');
        textNode.className = 't4tw-settings-text' + (String(entry.label || '').trim().startsWith('!') ? ' attention' : '');
        textNode.textContent = String(entry.label || '').replace(/^!\s*/, '');
        const condition = makeConditionNode(entry);
        if (condition) textNode.appendChild(condition);
        section.appendChild(textNode);
      } else if (entry.kind === 'panel') {
        if (workspaceSurface(target) || !dialogHasParameterTarget(target, stack.concat(dialogId), depth + 1)) continue;
        const wrapper = document.createElement('div');
        wrapper.className = 't4tw-settings-panel-wrap';
        const condition = makeConditionNode(entry);
        if (condition) wrapper.appendChild(condition);
        renderSettingsDialogInto(wrapper, target, stack.concat(dialogId), depth + 1, entryEnabled);
        section.appendChild(wrapper);
        parameterCount++;
      } else if (entry.kind === 'command') {
        const command = document.createElement('button');
        command.type = 'button'; command.className = 't4tw-settings-command'; command.disabled = true;
        command.textContent = (entry.label || entry.target || 'Command') + ' • ECU command not exposed';
        section.appendChild(command);
      }
    }
    if (depth === 0 && parameterCount === 0) {
      const empty = document.createElement('div'); empty.className = 't4tw-settings-text';
      empty.textContent = 'No scalar or enum parameters are exposed by this INI dialog. Calibration surfaces remain in Tables / Curves.';
      section.appendChild(empty);
    }
    container.appendChild(section);
  };

  let tunerTableSuppressClickUntil = 0;
  const baseValueCell = valueCell;
  valueCell = function(index, smallLabel) {
    const cell = baseValueCell(index, smallLabel);
    cell.dataset.t4twIndex = String(index);
    cell.onclick = event => {
      if (performance.now() < tunerTableSuppressClickUntil) return;
      selectArrayCell(index, event);
    };
    cell.ondblclick = event => {
      event.preventDefault(); selectArrayCell(index, event);
      const input = document.getElementById('t4twInlineTableValue');
      if (input && !input.disabled) { input.focus(); input.select(); }
    };
    return cell;
  };
  function updateVisibleTableSelection() {
    const selectedSet = selectedArrayCells.size ? selectedArrayCells : (selectedArrayCell >= 0 ? new Set([selectedArrayCell]) : new Set());
    const xCount = Number(selectedSurface?.xCount || 0), yCount = Number(selectedSurface?.yCount || 0);
    for (const node of page.querySelectorAll('.t4tw-cell[data-t4tw-index]')) {
      const index = Number(node.dataset.t4twIndex), chosen = selectedSet.has(index);
      node.classList.toggle('selected', index === selectedArrayCell);
      node.classList.toggle('multiselected', chosen);
      node.classList.remove('sel-top','sel-bottom','sel-left','sel-right');
      if (chosen && selectedSurface?.kind === 'table' && xCount > 0 && yCount > 0) {
        const x = index % xCount, y = Math.floor(index / xCount);
        if (y === 0 || !selectedSet.has(index - xCount)) node.classList.add('sel-top');
        if (y === yCount - 1 || !selectedSet.has(index + xCount)) node.classList.add('sel-bottom');
        if (x === 0 || !selectedSet.has(index - 1)) node.classList.add('sel-left');
        if (x === xCount - 1 || !selectedSet.has(index + 1)) node.classList.add('sel-right');
      }
    }
  }
  refreshArrayCellInspector = function(index, focusInput = true) {
    if (!selectedArray || !arrayDetail || !Array.isArray(arrayDetail.values) || index < 0 || index >= arrayDetail.values.length) return;
    selectedArrayCell = index;
    const current = Number(arrayDetail.values[index]), d = arrayDraftFor(selectedArray.name, index);
    arrayCurrent.textContent = valueWithUnit(current, selectedArray);
    arrayRequested.textContent = d ? valueWithUnit(d.requestedValue, selectedArray) : '—';
    arrayEffective.textContent = d ? valueWithUnit(d.effectiveValue, selectedArray) : '—';
    arrayInput.disabled = false; arrayApplyEdit.disabled = false; arrayWrite.hidden = !d; arrayWrite.disabled = !d;
    arrayInput.value = d ? String(d.requestedValue) : String(current);
    let coordinate = 'Cell #' + index;
    if (selectedSurface?.kind === 'table' && Array.isArray(arrayXDetail?.values) && Array.isArray(arrayYDetail?.values)) {
      const xCount = Number(selectedSurface.xCount), x = xCount > 0 ? index % xCount : -1, y = xCount > 0 ? Math.floor(index / xCount) : -1;
      if (x >= 0 && y >= 0 && x < arrayXDetail.values.length && y < arrayYDetail.values.length) coordinate = 'X ' + valueWithUnit(arrayXDetail.values[x], arrayXDetail) + ' • Y ' + valueWithUnit(arrayYDetail.values[y], arrayYDetail);
    } else if (selectedSurface?.kind === 'curve' && Array.isArray(arrayXDetail?.values) && index < arrayXDetail.values.length) coordinate = 'X ' + valueWithUnit(arrayXDetail.values[index], arrayXDetail);
    const selectionText = selectedArrayCells.size > 1 ? ' • ' + selectedArrayCells.size + ' cells selected' : '';
    arrayStatus.className = 't4tw-status' + (d ? ' good' : '');
    arrayStatus.textContent = d ? coordinate + selectionText + ' • PENDING EDIT • ' + d.changedBytes + ' encoded byte(s) will change • not yet written to ECU' : coordinate + selectionText + ' selected. Edit this cell or use Apply to selection.';
    updateArraySelectionControls(); updateVisibleTableSelection(); refreshInlineTableInspector(); refreshInlineCurveInspector();
    if (focusInput) setTimeout(() => arrayInput.focus(), 0);
  };
  clearArraySelection = function() {
    selectedArrayCells = new Set(); selectedArrayAnchor = selectedArrayCell;
    updateArraySelectionControls(); updateVisibleTableSelection(); refreshInlineTableInspector(); refreshInlineCurveInspector();
  };

  function bindInlineTableGestures(viewport) {
    if (!viewport || viewport.dataset.t4twGestures === 'v2') return;
    viewport.dataset.t4twGestures = 'v2';
    let gesture = null, pinch = null;
    const cellAt = (x, y) => { const node = document.elementFromPoint(x, y)?.closest?.('.t4tw-cell[data-t4tw-index]'); return node ? Number(node.dataset.t4twIndex) : -1; };
    const cancelGesture = () => { if (gesture?.timer) clearTimeout(gesture.timer); gesture = null; };
    viewport.addEventListener('touchstart', event => {
      if (event.touches.length >= 2) {
        cancelGesture(); const a = event.touches[0], b = event.touches[1], dx = a.clientX-b.clientX, dy = a.clientY-b.clientY;
        pinch = {distance:Math.max(1,Math.hypot(dx,dy)),zoom:tableZoom,centerX:(a.clientX+b.clientX)/2,centerY:(a.clientY+b.clientY)/2};
        tunerTableSuppressClickUntil = performance.now() + 650; return;
      }
      if (event.touches.length !== 1 || tablePresentationMode === '3d') return;
      const touch = event.touches[0], index = cellAt(touch.clientX, touch.clientY); if (index < 0) return;
      gesture = {index,startX:touch.clientX,startY:touch.clientY,last:index,active:false,timer:null};
      gesture.timer = setTimeout(() => {
        if (!gesture) return; gesture.active = true; selectedArrayAnchor = gesture.index;
        replaceArraySelection([gesture.index], gesture.index, gesture.index); updateVisibleTableSelection();
        tunerTableSuppressClickUntil = performance.now() + 650; window.EpicDashHaptic?.('selection');
      }, 430);
    }, {passive:true});
    viewport.addEventListener('touchmove', event => {
      if (pinch && event.touches.length >= 2) {
        event.preventDefault(); const a=event.touches[0], b=event.touches[1], distance=Math.max(1,Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY));
        setInlineTableZoom(Math.max(.4,Math.min(2.4,pinch.zoom*(distance/pinch.distance)))); tunerTableSuppressClickUntil = performance.now() + 650; return;
      }
      if (!gesture || event.touches.length !== 1) return;
      const touch=event.touches[0], moved=Math.hypot(touch.clientX-gesture.startX,touch.clientY-gesture.startY);
      if (!gesture.active) { if (moved > 10) cancelGesture(); return; }
      event.preventDefault(); const index=cellAt(touch.clientX,touch.clientY); if(index<0||index===gesture.last)return; gesture.last=index;
      replaceArraySelection(tableRectIndices(gesture.index,index,Number(selectedSurface?.xCount||0),Number(selectedSurface?.yCount||0)),index,gesture.index);
      updateVisibleTableSelection(); refreshInlineTableInspector();
    }, {passive:false});
    const finish=()=>{ if(gesture?.active){tunerTableSuppressClickUntil=performance.now()+650;selectedArrayAnchor=gesture.index;} cancelGesture(); pinch=null; };
    viewport.addEventListener('touchend',finish,{passive:true}); viewport.addEventListener('touchcancel',finish,{passive:true});
  }

  let tablePresentationMode = '2d', table3dYaw = 38, table3dPitch = 48, table3dZoom = 1, table3dPanX = 0, table3dPanY = 0, table3dRaf = 0;
  function clampTuner(value, low, high) { return Math.max(low, Math.min(high, value)); }
  function table3dReset(view = 'iso') {
    table3dZoom=1;table3dPanX=0;table3dPanY=0;
    if(view==='top'){table3dYaw=0;table3dPitch=8;}else if(view==='front'){table3dYaw=0;table3dPitch=82;}else if(view==='side'){table3dYaw=90;table3dPitch=82;}else{table3dYaw=38;table3dPitch=48;}
    renderInlineTable3d();
  }
  function buildInlineTable3dSvg() {
    const rows=Number(selectedSurface?.yCount||0),cols=Number(selectedSurface?.xCount||0),values=Array.isArray(arrayDetail?.values)?arrayDetail.values.map(Number):[];
    if(rows<2||cols<2||values.length<rows*cols)return''; const finite=values.filter(Number.isFinite);if(!finite.length)return'';
    const min=Math.min(...finite),max=Math.max(...finite),span=Math.max(1e-9,max-min),W=1000,H=560,yaw=table3dYaw*Math.PI/180,pitch=table3dPitch*Math.PI/180,cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
    const project=(gx,gy,n)=>{const rx=gx*cy-gy*sy,depth=gx*sy+gy*cy,z=(n-.5)*1.25;return{x:W*.5+rx*W*.34*table3dZoom+table3dPanX,y:H*.53+(depth*H*.25*cp-z*H*.42*sp)*table3dZoom+table3dPanY,depth:depth*sp+z*cp}};
    const rowStep=Math.max(1,Math.ceil((rows-1)/18)),colStep=Math.max(1,Math.ceil((cols-1)/18)),rowIds=[],colIds=[];
    for(let r=0;r<rows;r+=rowStep)rowIds.push(r);if(rowIds[rowIds.length-1]!==rows-1)rowIds.push(rows-1);for(let c=0;c<cols;c+=colStep)colIds.push(c);if(colIds[colIds.length-1]!==cols-1)colIds.push(cols-1);
    const point=(r,c)=>{const value=Number(values[r*cols+c]),n=Number.isFinite(value)?(value-min)/span:0,gx=-1+2*(c/(cols-1)),gy=1-2*(r/(rows-1));return{...project(gx,gy,n),normalized:n}};
    const faces=[];for(let ri=0;ri<rowIds.length-1;ri++)for(let ci=0;ci<colIds.length-1;ci++){const r0=rowIds[ri],r1=rowIds[ri+1],c0=colIds[ci],c1=colIds[ci+1],pts=[point(r0,c0),point(r0,c1),point(r1,c1),point(r1,c0)],ratio=pts.reduce((s,p)=>s+p.normalized,0)/4,depth=pts.reduce((s,p)=>s+p.depth,0)/4,hue=Math.round(210-ratio*205);faces.push({depth,svg:'<polygon points="'+pts.map(p=>p.x.toFixed(1)+','+p.y.toFixed(1)).join(' ')+'" fill="hsl('+hue+' 82% 45%)" fill-opacity=".88" stroke="#051017" stroke-width="1"/>'})}faces.sort((a,b)=>a.depth-b.depth);
    const origin=project(-1,1,0),xp=project(-.25,1,0),yp=project(-1,.25,0),zp=project(-1,1,.7),axes='<g class="t4tw-table-3d-axis"><line x1="'+origin.x+'" y1="'+origin.y+'" x2="'+xp.x+'" y2="'+xp.y+'" stroke="#ff655f" stroke-width="3"/><text x="'+(xp.x+8)+'" y="'+(xp.y-5)+'" fill="#ff655f">X</text><line x1="'+origin.x+'" y1="'+origin.y+'" x2="'+yp.x+'" y2="'+yp.y+'" stroke="#87df45" stroke-width="3"/><text x="'+(yp.x+8)+'" y="'+(yp.y-5)+'" fill="#87df45">Y</text><line x1="'+origin.x+'" y1="'+origin.y+'" x2="'+zp.x+'" y2="'+zp.y+'" stroke="#4dcfff" stroke-width="3"/><text x="'+(zp.x+8)+'" y="'+(zp.y-5)+'" fill="#4dcfff">Z</text></g>';
    return '<svg viewBox="0 0 '+W+' '+H+'" preserveAspectRatio="xMidYMid meet" aria-label="Rotatable 3D table interpretation"><rect width="100%" height="100%" fill="#030c12"/>'+faces.map(f=>f.svg).join('')+axes+'<text class="t4tw-table-3d-caption" x="18" y="542">X '+String(selectedSurface?.xBins||'X')+' • Y '+String(selectedSurface?.yBins||'Y')+' • Z '+String(selectedArray?.unit||selectedSurface?.unit||'value')+' • '+min.toFixed(2)+'…'+max.toFixed(2)+'</text></svg>';
  }
  function scheduleInlineTable3d(){if(table3dRaf)return;table3dRaf=requestAnimationFrame(()=>{table3dRaf=0;renderInlineTable3d();});}
  function bindInlineTable3dGestures(host){
    if(!host||host.dataset.t4tw3dBound==='1')return;host.dataset.t4tw3dBound='1';const pointers=new Map();let last=null,pinchDistance=0,pinchZoom=1,pinchCenter=null;
    const distance=()=>{const l=Array.from(pointers.values());return l.length>=2?Math.hypot(l[0].x-l[1].x,l[0].y-l[1].y):0},center=()=>{const l=Array.from(pointers.values());return l.length>=2?{x:(l[0].x+l[1].x)/2,y:(l[0].y+l[1].y)/2}:null};
    host.addEventListener('pointerdown',e=>{if(e.target.closest('.t4tw-table-3d-presets'))return;host.setPointerCapture?.(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});if(pointers.size===1)last={x:e.clientX,y:e.clientY};if(pointers.size===2){pinchDistance=Math.max(1,distance());pinchZoom=table3dZoom;pinchCenter=center();}});
    host.addEventListener('pointermove',e=>{if(!pointers.has(e.pointerId))return;pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});if(pointers.size>=2){e.preventDefault();const d=Math.max(1,distance()),c=center();table3dZoom=clampTuner(pinchZoom*(d/pinchDistance),.55,2.4);if(pinchCenter&&c){table3dPanX+=(c.x-pinchCenter.x)*1.4;table3dPanY+=(c.y-pinchCenter.y)*1.4;pinchCenter=c;}scheduleInlineTable3d();}else if(last){e.preventDefault();const dx=e.clientX-last.x,dy=e.clientY-last.y;table3dYaw+=dx*.35;table3dPitch=clampTuner(table3dPitch-dy*.32,8,172);last={x:e.clientX,y:e.clientY};scheduleInlineTable3d();}});
    const end=e=>{pointers.delete(e.pointerId);if(pointers.size===1){const only=Array.from(pointers.values())[0];last={x:only.x,y:only.y};}else{last=null;pinchDistance=0;pinchCenter=null;}};host.addEventListener('pointerup',end);host.addEventListener('pointercancel',end);
    host.addEventListener('wheel',e=>{e.preventDefault();table3dZoom=clampTuner(table3dZoom*(e.deltaY>0?.92:1.08),.55,2.4);scheduleInlineTable3d();},{passive:false});
  }
  function renderInlineTable3d(){
    if(viewMode!=='tables'||tablePresentationMode!=='3d')return;const viewport=document.getElementById('t4twInlineTableViewport'),grid=document.getElementById('t4twInlineTableGrid');if(!viewport||!grid||selectedSurface?.kind!=='table')return;grid.style.display='none';let host=document.getElementById('t4twInlineTable3d');if(!host){host=document.createElement('div');host.id='t4twInlineTable3d';host.className='t4tw-table-3d';viewport.appendChild(host);bindInlineTable3dGestures(host);}host.innerHTML=buildInlineTable3dSvg()+'<div class="t4tw-table-3d-hint">3D VIEW • drag to rotate • pinch to zoom • view only</div><div class="t4tw-table-3d-presets"><button type="button" data-t4tw-3d-view="iso">ISO</button><button type="button" data-t4tw-3d-view="front">FRONT</button><button type="button" data-t4tw-3d-view="side">SIDE</button><button type="button" data-t4tw-3d-view="top">TOP</button></div>';host.querySelectorAll('[data-t4tw-3d-view]').forEach(button=>{button.onclick=e=>{e.stopPropagation();table3dReset(button.getAttribute('data-t4tw-3d-view')||'iso');};});viewport.closest('.t4tw-table-main')?.classList.add('t4tw-table-3d-mode');
  }
  function showInlineTable2d(refit=false){const viewport=document.getElementById('t4twInlineTableViewport'),grid=document.getElementById('t4twInlineTableGrid');document.getElementById('t4twInlineTable3d')?.remove();if(grid)grid.style.display='grid';viewport?.closest('.t4tw-table-main')?.classList.remove('t4tw-table-3d-mode');if(refit)scheduleTunerGeometryRefresh(0);}
  function pruneDeadInlineAffordances(){for(const button of page.querySelectorAll('.t4tw-table-editbar button:disabled,.t4tw-curve-editbar button:disabled'))if(/undo|redo/i.test(button.textContent||''))button.classList.add('t4tw-runtime-hidden-affordance');for(const details of page.querySelectorAll('.t4tw-table-editbar details.t4tw-menu,.t4tw-curve-editbar details.t4tw-menu')){const buttons=Array.from(details.querySelectorAll('.t4tw-menu-panel button'));buttons.filter(b=>b.disabled).forEach(b=>b.classList.add('t4tw-runtime-hidden-affordance'));if(buttons.length&&buttons.every(b=>b.disabled))details.classList.add('t4tw-runtime-hidden-affordance');}}
  function patchTablePresentationControls(){
    const mainPane=page.querySelector('.t4tw-table-main');if(!mainPane)return;const controls=mainPane.querySelector('.t4tw-table-view-controls');if(!controls)return;const buttons=Array.from(controls.querySelectorAll('button')),fit=buttons.find(b=>(b.textContent||'').trim()==='Fit'),twoD=buttons.find(b=>(b.textContent||'').trim()==='2D'),threeD=buttons.find(b=>(b.textContent||'').trim()==='3D');
    if(threeD){threeD.disabled=false;threeD.title='View-only rotatable 3D table interpretation';threeD.onclick=()=>{tablePresentationMode='3d';twoD?.classList.remove('active');threeD.classList.add('active');renderInlineTable3d();};threeD.classList.toggle('active',tablePresentationMode==='3d');}
    if(twoD){twoD.onclick=()=>{tablePresentationMode='2d';threeD?.classList.remove('active');twoD.classList.add('active');showInlineTable2d(true);};twoD.classList.toggle('active',tablePresentationMode==='2d');}
    if(fit)fit.onclick=()=>{if(tablePresentationMode==='3d')table3dReset('iso');else fitInlineTable();};bindInlineTableGestures(document.getElementById('t4twInlineTableViewport'));if(tablePresentationMode==='3d')renderInlineTable3d();else showInlineTable2d(false);pruneDeadInlineAffordances();
  }
  const baseRenderTablesMode=renderTablesMode;renderTablesMode=function(q){baseRenderTablesMode(q);patchTablePresentationControls();};
  const baseRenderCurvesMode=renderCurvesMode;renderCurvesMode=function(q){baseRenderCurvesMode(q);pruneDeadInlineAffordances();};

  const tunerTelemetryNodeCache=new Map();let tunerTelemetryShapeReady=false;
  function ensurePersistentTunerTelemetry(){
    if(!telemetryStrip)return false;if(tunerTelemetryShapeReady&&tunerTelemetryNodeCache.size===TUNER_TELEMETRY_KEYS.length)return true;telemetryStrip.textContent='';tunerTelemetryNodeCache.clear();const labels={rpm:'RPM',map:'MAP',tps:'TPS',afr:'AFR',clt:'CLT',iat:'IAT',fuelPressure:'Fuel P',oilPressure:'Oil P',ign:'Ign Adv',boostDuty:'WG DC',batt:'Batt'};
    for(const key of TUNER_TELEMETRY_KEYS){const slot=document.createElement('div');slot.className='t4tw-telemetry-slot';slot.dataset.telemetryKey=key;const label=document.createElement('div');label.className='t4tw-telemetry-label';label.textContent=labels[key]||key;const value=document.createElement('div');value.className='t4tw-telemetry-value';const number=document.createElement('b');number.textContent='—';const unit=document.createElement('small');unit.textContent='';value.append(number,unit);slot.append(label,value);telemetryStrip.appendChild(slot);tunerTelemetryNodeCache.set(key,{label,number,unit,lastValue:null,lastUnit:null,lastLabel:label.textContent});}tunerTelemetryShapeReady=true;return true;
  }
  renderTunerTelemetry=function(){
    if(!telemetryStrip||!page.classList.contains('active'))return;let snapshot=null;try{snapshot=window.EpicDashTunerTelemetrySnapshot?.()||null;}catch(_){}if(!snapshot||!ensurePersistentTunerTelemetry())return;for(const key of TUNER_TELEMETRY_KEYS){const refs=tunerTelemetryNodeCache.get(key);if(!refs)continue;const label=snapshot.meta?.[key]?.label||refs.lastLabel||key,value=snapshot.valid?.[key]===false?'—':formatTelemetryValue(snapshot.values?.[key],key),unit=snapshot.meta?.[key]?.unit||'';if(refs.lastLabel!==label){refs.label.textContent=label;refs.lastLabel=label;}if(refs.lastValue!==value){refs.number.textContent=value;refs.lastValue=value;}if(refs.lastUnit!==unit){refs.unit.textContent=unit;refs.lastUnit=unit;}}syncTunerChrome();
  };

  window.addEventListener('epicdash:page-activated',event=>{if(event?.detail?.pageId!=='page-tuning')return;setTimeout(()=>{applyTunerViewportProfile(true);scheduleTunerGeometryRefresh(100);},0);});
