/* TUNER_UI_TABLE_CONTROLS_V5 / TUNER_UI_CORRECTIONS_1197
 * Legacy characterization continuity: TUNER_UI_TABLE_CONTROLS_V1; function t4FitTable()
 * Owns table interaction/fit, exact INI order, semantic binary state, inspectable navigation,
 * curve hit targets and presentation coverage diagnostics. Geometry belongs to layout-completion V2.
 */
  const t4TableStyle=document.createElement('style');
  t4TableStyle.textContent=`
    .t4tw-table-extra-tools{display:flex;gap:4px;align-items:center;flex-wrap:wrap}
    .t4tw-table-extra-tools .t4tw-btn{padding:6px 8px;font-size:9px}
    .t4tw-table-viewport,.t4tw-table-main{min-height:0!important}.t4tw-inline-table-grid{max-width:none!important}
    .t4tw-table-grid .t4tw-cell{box-sizing:border-box!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-extra-tools{flex-wrap:nowrap;overflow-x:auto;max-width:100%}
    #t4twCurvePlot svg{touch-action:manipulation}
    #t4twCurvePlot circle[data-curve-hit]{cursor:pointer;pointer-events:all}
  `;
  document.head.appendChild(t4TableStyle);

  presentationSystemName=function(rawValue){const raw=cleanMenuLabel(rawValue);return raw||'Other';};
  presentationCategoryName=function(_rawSystem,rawGroup){return cleanMenuLabel(rawGroup)||HIERARCHY_GENERAL_CATEGORY;};
  sortPresentationSystems=function(systems){return systems;};
  hierarchyCacheKey='';hierarchyCache=null;

  /* TUNER_UI_CORRECTIONS_1197_NAVIGATION_INSPECTION
   * A parsed route/surface must remain navigable even when it is condition-inactive or read-only.
   * Edit authority remains enforced by field/surface writeAllowed + condition state after navigation.
   */
  const t4BaseHierarchySelect1197=hierarchySelect;
  hierarchySelect=function(label,value,options,onChange){
    const level=String(label||'').trim().toLowerCase();
    const inspectable=level==='feature'||level==='table'||level==='curve';
    const normalized=(Array.isArray(options)?options:[]).map(option=>{
      if(!inspectable)return option;
      const optionValue=String(option?.value??'');
      return optionValue?{...option,disabled:false}:option;
    });
    return t4BaseHierarchySelect1197(label,value,normalized,onChange);
  };

  function t4SelectRow(){if(selectedSurface?.kind!=='table'||selectedArrayCell<0)return;const x=Number(selectedSurface.xCount||0);if(x<=0)return;const row=Math.floor(selectedArrayCell/x),ids=Array.from({length:x},(_,i)=>row*x+i);replaceArraySelection(ids,selectedArrayCell,ids[0]);updateArraySelectionControls();updateVisibleTableSelection();refreshInlineTableInspector();}
  function t4SelectColumn(){if(selectedSurface?.kind!=='table'||selectedArrayCell<0)return;const x=Number(selectedSurface.xCount||0),y=Number(selectedSurface?.yCount||0);if(x<=0||y<=0)return;const col=selectedArrayCell%x,ids=Array.from({length:y},(_,i)=>i*x+col);replaceArraySelection(ids,selectedArrayCell,ids[0]);updateArraySelectionControls();updateVisibleTableSelection();refreshInlineTableInspector();}
  function t4SelectAll(){const count=Number(selectedSurface?.xCount||0)*Number(selectedSurface?.yCount||0);if(count<=0)return;replaceArraySelection(Array.from({length:count},(_,i)=>i),selectedArrayCell>=0?selectedArrayCell:0,0);updateArraySelectionControls();updateVisibleTableSelection();refreshInlineTableInspector();}
  function t4PatchTableTools(){const bar=page.querySelector('.t4tw-table-editbar');if(!bar||bar.querySelector('.t4tw-table-extra-tools'))return;const tools=document.createElement('div');tools.className='t4tw-table-extra-tools';const add=(text,fn)=>{const b=document.createElement('button');b.type='button';b.className='t4tw-btn';b.textContent=text;b.onclick=fn;tools.appendChild(b);};add('Row',t4SelectRow);add('Column',t4SelectColumn);add('All',t4SelectAll);add('Clear',()=>clearArraySelection());add('−5%',()=>applyInlineTableSelection('percent',-5));add('+5%',()=>applyInlineTableSelection('percent',5));bar.appendChild(tools);}

  let t4TableAutoFit=true,t4TableFitFrame=0,t4TableFitFrame2=0,t4ObservedTableViewport=null,t4TableResizeObserver=null,t4TableFitSurfaceKey='';
  function t4CurrentTableKey(){return selectedSurface?.kind==='table'?String(selectedSurface?.id||selectedSurface?.targetArray||''):'';}
  function t4ApplyTableFit(){t4TableFitFrame=0;t4TableFitFrame2=0;if(viewMode!=='tables'||tablePresentationMode!=='2d')return;const viewport=document.getElementById('t4twInlineTableViewport'),grid=document.getElementById('t4twInlineTableGrid');const x=Number(selectedSurface?.xCount||0),y=Number(selectedSurface?.yCount||0);if(!viewport||!grid||x<=0||y<=0)return;const r=viewport.getBoundingClientRect();if(r.width<80||r.height<80)return;const viewportStyle=getComputedStyle(viewport),gridStyle=getComputedStyle(grid);const padX=(parseFloat(viewportStyle.paddingLeft)||0)+(parseFloat(viewportStyle.paddingRight)||0),padY=(parseFloat(viewportStyle.paddingTop)||0)+(parseFloat(viewportStyle.paddingBottom)||0);const colGap=parseFloat(gridStyle.columnGap)||parseFloat(gridStyle.gap)||1,rowGap=parseFloat(gridStyle.rowGap)||parseFloat(gridStyle.gap)||1,axisWidth=52;const usableWidth=Math.max(1,r.width-padX-axisWidth-(colGap*x)),widthFit=Math.floor(usableWidth/x),rows=y+1,usableHeight=Math.max(1,r.height-padY-(rowGap*Math.max(0,rows-1))),heightFit=Math.floor(usableHeight/rows),largeTable=x>=12||y>=12||(x*y)>=144,desired=largeTable?widthFit:Math.min(widthFit,heightFit),cell=Math.max(10,Math.min(76,desired));tableZoom=cell/22;grid.style.setProperty('--t4tw-inline-cell',cell+'px');grid.style.gridTemplateColumns='52px repeat('+x+', var(--t4tw-inline-cell))';const z=document.getElementById('t4twInlineTableZoom');if(z)z.textContent=Math.round(tableZoom*100)+'%';refreshInlineTableInspector();}
  function t4ScheduleTableFit(force=true){if(force)t4TableAutoFit=true;if(!t4TableAutoFit)return;if(t4TableFitFrame)cancelAnimationFrame(t4TableFitFrame);if(t4TableFitFrame2)cancelAnimationFrame(t4TableFitFrame2);t4TableFitFrame=requestAnimationFrame(()=>{t4TableFitFrame=0;t4TableFitFrame2=requestAnimationFrame(t4ApplyTableFit);});}
  function t4EnsureTableResizeObserver(){const viewport=document.getElementById('t4twInlineTableViewport');if(!viewport||viewport===t4ObservedTableViewport)return;t4TableResizeObserver?.disconnect?.();t4ObservedTableViewport=viewport;if(typeof ResizeObserver==='function'){t4TableResizeObserver=new ResizeObserver(()=>{if(t4TableAutoFit)t4ScheduleTableFit(false);});t4TableResizeObserver.observe(viewport);}}
  fitInlineTable=function(){t4ScheduleTableFit(true);};
  setInlineTableZoom=function(nextZoom){t4TableAutoFit=false;tableZoom=Math.max(0.4,Math.min(3.4,Number(nextZoom)||1));const grid=document.getElementById('t4twInlineTableGrid');if(grid)grid.style.setProperty('--t4tw-inline-cell',Math.round(22*tableZoom)+'px');const z=document.getElementById('t4twInlineTableZoom');if(z)z.textContent=Math.round(tableZoom*100)+'%';refreshInlineTableInspector();};

  /* TUNER_UI_CORRECTIONS_1197_BOOLEAN / semantic three-state boolean */
  const T4_BOOLEAN_LABEL_PAIRS=new Set(['false|true','disabled|enabled','enabled|disabled','off|on','on|off','no|yes','yes|no','inactive|active','active|inactive','disable|enable','enable|disable']);
  function t4BooleanSpec(bit){
    const options=Array.isArray(bit?.options)?bit.options:[];if(options.length!==2)return null;
    const labels=options.map(option=>String(option?.label||'').trim().toLowerCase());
    if(!T4_BOOLEAN_LABEL_PAIRS.has(labels[0]+'|'+labels[1]))return null;
    return {falseOption:options[0],trueOption:options[1]};
  }
  const t4BaseBitLabel1197=bitLabel;
  bitLabel=function(bit,value){const numeric=Number(value);if(!Number.isFinite(numeric))return '—';return t4BaseBitLabel1197(bit,numeric);};

  function t4SetTextIfChanged(node,text){if(node&&node.textContent!==text)node.textContent=text;}
  function t4PatchBooleanToggles(root=page){
    const toggles=[];if(root?.matches?.('.t4tw-bit-toggle'))toggles.push(root);root?.querySelectorAll?.('.t4tw-bit-toggle').forEach(toggle=>toggles.push(toggle));
    toggles.forEach(toggle=>{
      const field=toggle.closest('.t4tw-settings-field'),target=String(field?.querySelector('.t4tw-settings-field-key')?.textContent||'').trim(),bit=workspaceBitField(target);
      const options=Array.isArray(bit?.options)?bit.options:[];if(options.length!==2)return;
      const pending=bitDraftFor(bit.name),rawValue=pending?pending.effectiveValue:bit.value,numericValue=Number(rawValue),matchedOption=options.find(option=>Number(option?.value)===numericValue)||null;
      const sourceAvailable=!!pending||(bit?.valueAvailable!==false&&Number.isFinite(numericValue));
      const spec=t4BooleanSpec(bit);
      if(!spec){
        toggle.classList.remove('t4tw-boolean-toggle','unknown');field?.classList.remove('t4tw-boolean-field');field?.querySelector('.t4tw-settings-field-value')?.classList.remove('t4tw-boolean-value');
        if(!sourceAvailable||!matchedOption){t4SetTextIfChanged(toggle,'—');toggle.setAttribute('aria-label',(bit?.name||'Two-state setting')+': value unavailable');toggle.title=bit?.writeBlockReason||'Current value unavailable until a matching TuneSnapshot is loaded';}
        else{t4SetTextIfChanged(toggle,String(matchedOption.label||numericValue));toggle.removeAttribute('aria-label');toggle.title='';}
        return;
      }
      const trueValue=Number(spec.trueOption.value),falseValue=Number(spec.falseOption.value),valueKnown=sourceAvailable&&Number.isFinite(trueValue)&&Number.isFinite(falseValue)&&(numericValue===trueValue||numericValue===falseValue),state=valueKnown&&numericValue===trueValue,fieldLabel=String(field?.querySelector('.t4tw-settings-field-label')?.textContent||bit.name||'Boolean setting').trim(),valueHost=field?.querySelector('.t4tw-settings-field-value');
      field?.classList.add('t4tw-boolean-field');valueHost?.classList.add('t4tw-boolean-value');toggle.classList.add('t4tw-boolean-toggle');toggle.classList.toggle('on',state);toggle.classList.toggle('unknown',!valueKnown);toggle.setAttribute('role','switch');toggle.setAttribute('aria-checked',state?'true':'false');toggle.setAttribute('aria-disabled',valueKnown?'false':'true');toggle.setAttribute('aria-label',valueKnown?(fieldLabel+': '+String(matchedOption?.label||numericValue)):(fieldLabel+': value unavailable'));toggle.title=valueKnown?'':(bit?.writeBlockReason||'Current value unavailable until a matching TuneSnapshot is loaded');t4SetTextIfChanged(toggle,'');
    });
  }
  const t4BaseSettingsFieldNode1197=settingsFieldNode;settingsFieldNode=function(entry,inheritedEnabled=true){const node=t4BaseSettingsFieldNode1197(entry,inheritedEnabled);t4PatchBooleanToggles(node);return node;};
  /* Deliberately no MutationObserver here: textContent mutation can feed back into childList observation and freeze WebView. */

  function t4PatchCurveHitTargets(){const svg=document.querySelector('#t4twCurvePlot svg');if(!svg)return;svg.querySelectorAll('circle[data-curve-point]').forEach(point=>{const index=Number(point.getAttribute('data-curve-point'));if(!Number.isInteger(index))return;if(svg.querySelector('circle[data-curve-hit="'+index+'"]'))return;const hit=document.createElementNS('http://www.w3.org/2000/svg','circle');hit.setAttribute('cx',point.getAttribute('cx')||'0');hit.setAttribute('cy',point.getAttribute('cy')||'0');hit.setAttribute('r','18');hit.setAttribute('fill','transparent');hit.setAttribute('stroke','transparent');hit.setAttribute('data-curve-hit',String(index));hit.addEventListener('pointerdown',event=>{event.preventDefault();event.stopPropagation();selectCurvePoint(index);});hit.addEventListener('click',event=>{event.preventDefault();event.stopPropagation();selectCurvePoint(index);});point.parentNode?.appendChild(hit);});}
  const t4BaseRenderCurveGraph1197=renderCurveGraph;renderCurveGraph=function(){const result=t4BaseRenderCurveGraph1197();requestAnimationFrame(t4PatchCurveHitTargets);return result;};

  function t4PresentationAudit(){const menuItems=Array.isArray(workspace?.menuItems)?workspace.menuItems:[],dialogs=Array.isArray(workspace?.dialogs)?workspace.dialogs:[],scalars=new Set((workspace?.scalars||[]).map(item=>String(item?.name||''))),bits=new Set((workspace?.bitFields||[]).map(item=>String(item?.name||''))),arrays=new Set((workspace?.arrays||[]).map(item=>String(item?.name||''))),dialogIds=new Set(dialogs.map(item=>String(item?.id||''))),surfaceIds=new Set([...(workspace?.tables||[]),...(workspace?.curves||[])].map(item=>String(item?.id||'')));let hidden=0,disabled=0,unsupported=0,unroutedMenuItems=0,unroutedFields=0,unroutedPanels=0;const unsupportedExpressions=new Set(),unresolvedIdentifiers=new Set();const consumeState=state=>{const status=String(state?.status||'');if(status==='hidden')hidden++;else if(status==='disabled')disabled++;else if(status==='unsupported')unsupported++;for(const evaluation of(state?.evaluations||[])){if(String(evaluation?.status||'')!=='unsupported')continue;const expression=String(evaluation?.expression||'').trim();if(expression)unsupportedExpressions.add(expression);const reason=String(evaluation?.reason||''),match=reason.match(/Condition identifier '([^']+)' is unavailable/);if(match)unresolvedIdentifiers.add(match[1]);}};for(const item of menuItems){consumeState(item?.conditionState);const target=String(item?.dialogId||'');if(target&&!dialogIds.has(target)&&!surfaceIds.has(target))unroutedMenuItems++;}for(const dialog of dialogs){for(const entry of(dialog?.entries||[])){consumeState(entry?.conditionState);const target=String(entry?.target||'');if(entry?.kind==='field'&&target&&!scalars.has(target)&&!bits.has(target)&&!arrays.has(target)&&!surfaceIds.has(target))unroutedFields++;if(entry?.kind==='panel'&&target&&!dialogIds.has(target)&&!surfaceIds.has(target))unroutedPanels++;}}return{menuItems:menuItems.length,dialogs:dialogs.length,hiddenEntries:hidden,disabledEntries:disabled,unsupportedEntries:unsupported,unsupportedExpressions:unsupportedExpressions.size,unresolvedIdentifiers:Array.from(unresolvedIdentifiers).sort(),unroutedMenuItems,unroutedFields,unroutedPanels};}
  function t4RefreshPresentationAudit(){const audit=t4PresentationAudit();window.__EPIC_TUNER_PRESENTATION_AUDIT__=audit;const unresolved=audit.unresolvedIdentifiers.slice(0,12).join(', ');if(topIniButton)topIniButton.title='INI presentation coverage: '+audit.hiddenEntries+' hidden, '+audit.unsupportedEntries+' unsupported, '+audit.unroutedMenuItems+' unrouted menu, '+audit.unroutedFields+' unrouted field, '+audit.unroutedPanels+' unrouted panel'+(unresolved?' • unresolved: '+unresolved:'');}

  const t4TableBaseRender=renderTablesMode;renderTablesMode=function(q){const result=t4TableBaseRender(q);t4PatchTableTools();t4EnsureTableResizeObserver();const key=t4CurrentTableKey();if(key!==t4TableFitSurfaceKey){t4TableFitSurfaceKey=key;t4TableAutoFit=true;}if(tablePresentationMode==='2d')t4ScheduleTableFit(false);return result;};
  const t4TableBaseViewport=applyTunerViewportProfile;applyTunerViewportProfile=function(force=false){const before=String(document.body.dataset.t4twViewport||''),p=t4TableBaseViewport(force),after=String(document.body.dataset.t4twViewport||'');requestAnimationFrame(()=>{t4PatchBooleanToggles();if(viewMode==='tables'&&tablePresentationMode==='2d'){t4EnsureTableResizeObserver();t4ScheduleTableFit(before!==after);}});return p;};
  const t4BaseRender1197=render;render=function(){const result=t4BaseRender1197();requestAnimationFrame(()=>{t4PatchBooleanToggles();t4PatchCurveHitTargets();t4RefreshPresentationAudit();});return result;};

  window.addEventListener('epicdash:page-activated',e=>{if(e?.detail?.pageId!=='page-tuning')return;setTimeout(()=>{t4PatchTableTools();t4PatchBooleanToggles();t4RefreshPresentationAudit();if(viewMode==='tables'){t4EnsureTableResizeObserver();t4ScheduleTableFit(false);}if(viewMode==='curves')t4PatchCurveHitTargets();},50);});
  window.addEventListener('epicdash:permanent-tuner-project-ready',()=>setTimeout(()=>{hierarchyCacheKey='';hierarchyCache=null;t4RefreshPresentationAudit();render();},0));
  t4PatchBooleanToggles();t4RefreshPresentationAudit();if(workspace?.status==='ready')setTimeout(()=>{hierarchyCacheKey='';hierarchyCache=null;render();},0);

  /* TUNER_UI_1199_TABLE_EDIT_WORKFLOW
   * Move table editing out of the persistent layout, provide selection transforms that touch gestures
   * cannot express directly, use full-width phone hierarchy pickers, and clamp all floating menus.
   */
  const t4Table1199Style=document.createElement('style');
  t4Table1199Style.id='t4-tuning-table-workflow-1199-style';
  t4Table1199Style.textContent=`
    .t4tw-bit-toggle.t4tw-boolean-toggle:before{transform:none!important}
    .t4tw-settings-field.t4tw-condition-disabled .t4tw-settings-field-value,.t4tw-settings-field.t4tw-condition-disabled .t4tw-bit-control,.t4tw-settings-field.t4tw-condition-disabled select{align-self:center!important;margin:0!important;transform:none!important}
    .t4tw-zoom-cluster{display:grid;grid-template-columns:32px 44px 32px;align-items:stretch;gap:0;height:34px}
    .t4tw-zoom-cluster>.t4tw-btn{height:34px!important;min-width:32px!important;width:32px!important;padding:0!important;border-radius:0!important}
    .t4tw-zoom-cluster>.t4tw-btn:first-child{border-radius:8px 0 0 8px!important}
    .t4tw-zoom-cluster>.t4tw-btn:last-child{border-radius:0 8px 8px 0!important}
    .t4tw-zoom-cluster>#t4twInlineTableZoom{display:grid!important;place-items:center!important;width:44px!important;min-width:44px!important;height:34px!important;margin:0!important;border-top:1px solid var(--line);border-bottom:1px solid var(--line);background:#0b151d;color:#9db1bc;font-size:10px!important;line-height:1!important}
    .t4tw-table-edit-trigger{height:34px!important;min-width:48px!important;padding:0 9px!important}
    .t4tw-table-edit-modal{position:fixed;inset:0;z-index:10055;background:rgba(0,0,0,.72);display:flex;align-items:center;justify-content:center;padding:8px}
    .t4tw-table-edit-modal[hidden]{display:none!important}
    .t4tw-table-edit-dialog{width:min(820px,calc(100vw - 16px));max-width:calc(100vw - 16px);max-height:calc(100vh - 16px);overflow:auto;background:#0b141c;border:1px solid #315064;border-radius:14px;box-shadow:0 20px 70px rgba(0,0,0,.55);padding:10px}
    .t4tw-table-edit-head{display:flex;align-items:center;gap:8px;margin-bottom:7px}.t4tw-table-edit-head b{flex:1;font-size:13px}.t4tw-table-edit-close{height:32px;padding:0 9px}
    .t4tw-table-edit-hint{font-size:9px;line-height:1.35;color:#819aa7;margin:0 0 8px}
    .t4tw-table-edit-modal .t4tw-table-editbar{display:flex!important;position:static!important;margin:0!important;width:100%!important;max-width:100%!important;overflow:visible!important;flex-wrap:wrap!important;padding:7px!important;gap:4px!important}
    .t4tw-table-edit-modal .t4tw-table-editbar>.t4tw-btn,.t4tw-table-edit-modal .t4tw-table-extra-tools .t4tw-btn{height:34px!important;min-width:38px!important;padding:0 8px!important;font-size:10px!important}
    .t4tw-table-edit-modal .t4tw-table-value-input{height:34px!important;width:86px!important;flex:0 0 86px!important;padding:4px 7px!important}
    .t4tw-table-extra-tools{flex:1 0 100%!important;display:grid!important;gap:6px!important;margin-top:2px}
    .t4tw-table-selection-tools,.t4tw-table-value-tools{display:flex;align-items:center;gap:4px;flex-wrap:wrap}
    .t4tw-table-tools-label{font-size:8px;font-weight:900;letter-spacing:.8px;color:#6f8996;text-transform:uppercase;flex:0 0 100%}
    .t4tw-hierarchy-picker-button{display:none;width:100%;min-width:0;height:34px;border:1px solid #294654;border-radius:8px;background:#07131a;color:#eef7fc;padding:0 26px 0 9px;text-align:left;font-size:10px;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;position:relative}
    .t4tw-hierarchy-picker-button:after{content:'⌄';position:absolute;right:8px;top:50%;transform:translateY(-54%);color:#9db1bc;font-size:15px}
    .t4tw-hierarchy-picker-modal{position:fixed;inset:0;z-index:10060;background:rgba(0,0,0,.72);display:flex;align-items:center;justify-content:center;padding:6px}
    .t4tw-hierarchy-picker-modal[hidden]{display:none!important}
    .t4tw-hierarchy-picker-dialog{width:calc(100vw - 12px);max-width:calc(100vw - 12px);max-height:calc(100vh - 12px);display:flex;flex-direction:column;background:#0b141c;border:1px solid #315064;border-radius:13px;overflow:hidden;box-shadow:0 20px 70px rgba(0,0,0,.55)}
    .t4tw-hierarchy-picker-head{display:flex;align-items:center;gap:8px;padding:9px 10px;border-bottom:1px solid #243f4d}.t4tw-hierarchy-picker-head b{flex:1;font-size:13px}.t4tw-hierarchy-picker-list{overflow:auto;overscroll-behavior:contain;padding:6px;display:grid;gap:3px}
    .t4tw-hierarchy-picker-option{min-height:38px;border:1px solid #1f3744;border-radius:8px;background:#08131a;color:#dcebf2;padding:7px 10px;text-align:left;font-size:11px;font-weight:800;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;align-items:center}
    .t4tw-hierarchy-picker-option.active{border-color:var(--cyan);background:#102733;color:#fff}.t4tw-hierarchy-picker-option:disabled{opacity:.45}
    .t4tw-hierarchy-picker-check{color:var(--cyan);font-size:14px}
    .t4tw-menu-panel{max-width:calc(100vw - 16px)!important;max-height:calc(100vh - 16px)!important;overflow:auto!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-select,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-select{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-hierarchy-picker-button,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-picker-button{display:block!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-nav-row{grid-template-columns:minmax(0,1fr) minmax(250px,.72fr)!important;gap:4px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-selectors{grid-template-columns:repeat(3,minmax(0,1fr))!important;gap:4px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-chevron{display:none!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-content-modes{min-width:250px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-view-controls{gap:3px!important;flex-wrap:nowrap!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-view-controls>.t4tw-btn{height:34px!important;min-width:36px!important;padding:0 7px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-viewport{flex:1 1 auto!important;height:auto!important;min-height:120px!important}
    body[data-t4tw-viewport="phone-landscape"] #page-tuning:has(.t4tw-table-main) .t4tw-telemetry{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-edit-modal{align-items:flex-end!important;padding:0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-edit-dialog{width:100vw!important;max-width:100vw!important;max-height:74vh!important;border-radius:16px 16px 0 0!important;padding:9px 8px 12px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls{gap:3px!important;flex-wrap:nowrap!important;overflow-x:auto!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls>.t4tw-btn{height:34px!important;min-width:36px!important;padding:0 7px!important}
  `;
  queueMicrotask(()=>document.head.appendChild(t4Table1199Style));

  function t4SelectionGeometry(){const x=Number(selectedSurface?.xCount||0),y=Number(selectedSurface?.yCount||0);return{x,y,count:Math.max(0,x*y)};}
  function t4CurrentSelectionSet(){if(selectedArrayCells?.size)return new Set(selectedArrayCells);return selectedArrayCell>=0?new Set([selectedArrayCell]):new Set();}
  function t4CommitSelection(ids,primary=selectedArrayCell){const {count}=t4SelectionGeometry(),clean=Array.from(new Set((ids||[]).filter(index=>Number.isInteger(index)&&index>=0&&index<count)));if(!clean.length){clearArraySelection();return;}const chosen=clean.includes(primary)?primary:clean[0];replaceArraySelection(clean,chosen,clean[0]);updateArraySelectionControls();updateVisibleTableSelection();refreshInlineTableInspector();t4SyncTableEditButton();}
  function t4InvertSelection(){const {count}=t4SelectionGeometry(),current=t4CurrentSelectionSet();if(count<=0)return;t4CommitSelection(Array.from({length:count},(_,index)=>index).filter(index=>!current.has(index)));}
  function t4ExpandSelection(){const {x,y}=t4SelectionGeometry(),current=t4CurrentSelectionSet();if(!current.size||x<=0||y<=0)return;const next=new Set(current);for(const index of current){const row=Math.floor(index/x),col=index%x;if(row>0)next.add(index-x);if(row<y-1)next.add(index+x);if(col>0)next.add(index-1);if(col<x-1)next.add(index+1);}t4CommitSelection(Array.from(next));}
  function t4ShrinkSelection(){const {x,y}=t4SelectionGeometry(),current=t4CurrentSelectionSet();if(!current.size||x<=0||y<=0)return;const next=[];for(const index of current){const row=Math.floor(index/x),col=index%x;if(row<=0||row>=y-1||col<=0||col>=x-1)continue;if(current.has(index-x)&&current.has(index+x)&&current.has(index-1)&&current.has(index+1))next.push(index);}t4CommitSelection(next);}
  function t4SelectHalf(direction){const {x,y}=t4SelectionGeometry();if(selectedArrayCell<0||x<=0||y<=0)return;const row=Math.floor(selectedArrayCell/x),col=selectedArrayCell%x,ids=[];for(let r=0;r<y;r++){for(let c=0;c<x;c++){if((direction==='above'&&r<=row)||(direction==='below'&&r>=row)||(direction==='left'&&c<=col)||(direction==='right'&&c>=col))ids.push(r*x+c);}}t4CommitSelection(ids,selectedArrayCell);}

  t4PatchTableTools=function(){
    const bar=page.querySelector('.t4tw-table-editbar');if(!bar||bar.querySelector('.t4tw-table-extra-tools'))return;
    const tools=document.createElement('div');tools.className='t4tw-table-extra-tools';
    const selection=document.createElement('div');selection.className='t4tw-table-selection-tools';
    const values=document.createElement('div');values.className='t4tw-table-value-tools';
    const label=(text,parent)=>{const node=document.createElement('div');node.className='t4tw-table-tools-label';node.textContent=text;parent.appendChild(node);};
    const add=(parent,text,fn)=>{const b=document.createElement('button');b.type='button';b.className='t4tw-btn';b.textContent=text;b.onclick=fn;parent.appendChild(b);};
    label('Selection',selection);add(selection,'Row',t4SelectRow);add(selection,'Column',t4SelectColumn);add(selection,'All',t4SelectAll);add(selection,'Clear',()=>clearArraySelection());add(selection,'Invert',t4InvertSelection);add(selection,'Expand',t4ExpandSelection);add(selection,'Shrink',t4ShrinkSelection);add(selection,'Above',()=>t4SelectHalf('above'));add(selection,'Below',()=>t4SelectHalf('below'));add(selection,'Left',()=>t4SelectHalf('left'));add(selection,'Right',()=>t4SelectHalf('right'));
    label('Value transforms',values);add(values,'−5%',()=>applyInlineTableSelection('percent',-5));add(values,'+5%',()=>applyInlineTableSelection('percent',5));
    tools.append(selection,values);bar.appendChild(tools);
  };

  function t4SyncTableEditButton(){const button=document.getElementById('t4twTableEditButton');if(!button)return;const count=typeof inlineTableSelectionCount==='function'?inlineTableSelectionCount():t4CurrentSelectionSet().size;button.textContent=count>0?'Edit · '+count:'Edit';}
  function t4PatchZoomCluster(){const controls=page.querySelector('.t4tw-table-view-controls'),zoom=document.getElementById('t4twInlineTableZoom');if(!controls||!zoom||zoom.closest('.t4tw-zoom-cluster'))return;const minus=zoom.previousElementSibling,plus=zoom.nextElementSibling;if(!minus?.classList?.contains('t4tw-btn')||!plus?.classList?.contains('t4tw-btn'))return;const cluster=document.createElement('div');cluster.className='t4tw-zoom-cluster';controls.insertBefore(cluster,minus);cluster.append(minus,zoom,plus);}
  function t4CloseTableEdit(){const modal=document.getElementById('t4twTableEditModal');if(modal)modal.hidden=true;}
  function t4PatchTableEditWorkflow(){
    const main=page.querySelector('.t4tw-table-main');if(!main)return;
    t4PatchZoomCluster();
    const controls=main.querySelector('.t4tw-table-view-controls');
    let editButton=controls?.querySelector('#t4twTableEditButton');
    if(!editButton&&controls){editButton=document.createElement('button');editButton.type='button';editButton.id='t4twTableEditButton';editButton.className='t4tw-btn t4tw-table-edit-trigger';const modeButton=Array.from(controls.children).find(node=>String(node.textContent||'').trim()==='2D');controls.insertBefore(editButton,modeButton||null);}
    if(editButton){editButton.hidden=tablePresentationMode!=='2d';editButton.onclick=()=>{const modal=document.getElementById('t4twTableEditModal');if(modal)modal.hidden=false;};}
    t4SyncTableEditButton();
    const bar=main.querySelector('.t4tw-table-editbar');if(!bar)return;
    t4PatchTableTools();
    page.querySelector('#t4twTableEditModal')?.remove();
    const modal=document.createElement('div');modal.id='t4twTableEditModal';modal.className='t4tw-table-edit-modal';modal.hidden=true;
    const dialog=document.createElement('section');dialog.className='t4tw-table-edit-dialog';dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');dialog.setAttribute('aria-label','Edit table selection');
    const head=document.createElement('div');head.className='t4tw-table-edit-head';const title=document.createElement('b');title.textContent='Edit table selection';const close=document.createElement('button');close.type='button';close.className='t4tw-btn t4tw-table-edit-close';close.textContent='Close';close.onclick=t4CloseTableEdit;head.append(title,close);
    const hint=document.createElement('div');hint.className='t4tw-table-edit-hint';hint.textContent='Tap a cell for a single selection. Long-press + drag selects a rectangle. Tap row/column headers for complete axes. Pinch changes table zoom. Selection tools below handle transforms gestures cannot express directly.';
    dialog.append(head,hint,bar);modal.appendChild(dialog);page.appendChild(modal);modal.addEventListener('click',event=>{if(event.target===modal)t4CloseTableEdit();});
  }

  let t4HierarchyPickerModal=null,t4HierarchyPickerList=null,t4HierarchyPickerTitle=null;
  function t4EnsureHierarchyPicker(){if(t4HierarchyPickerModal?.isConnected)return;t4HierarchyPickerModal=document.createElement('div');t4HierarchyPickerModal.className='t4tw-hierarchy-picker-modal';t4HierarchyPickerModal.hidden=true;const dialog=document.createElement('section');dialog.className='t4tw-hierarchy-picker-dialog';dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');const head=document.createElement('div');head.className='t4tw-hierarchy-picker-head';t4HierarchyPickerTitle=document.createElement('b');const close=document.createElement('button');close.type='button';close.className='t4tw-btn';close.textContent='Close';close.onclick=()=>t4HierarchyPickerModal.hidden=true;head.append(t4HierarchyPickerTitle,close);t4HierarchyPickerList=document.createElement('div');t4HierarchyPickerList.className='t4tw-hierarchy-picker-list';dialog.append(head,t4HierarchyPickerList);t4HierarchyPickerModal.appendChild(dialog);page.appendChild(t4HierarchyPickerModal);t4HierarchyPickerModal.addEventListener('click',event=>{if(event.target===t4HierarchyPickerModal)t4HierarchyPickerModal.hidden=true;});}
  function t4OpenHierarchyPicker(label,value,options,onChange){t4EnsureHierarchyPicker();t4HierarchyPickerTitle.textContent=String(label||'Select');t4HierarchyPickerList.textContent='';for(const option of options||[]){const button=document.createElement('button');button.type='button';button.className='t4tw-hierarchy-picker-option';button.disabled=!!option?.disabled;const text=document.createElement('span');text.textContent=String(option?.label??option?.value??'');const check=document.createElement('span');check.className='t4tw-hierarchy-picker-check';const active=String(option?.value??'')===String(value??'');if(active){button.classList.add('active');check.textContent='✓';}button.append(text,check);button.onclick=()=>{if(button.disabled)return;t4HierarchyPickerModal.hidden=true;onChange(String(option?.value??''));};t4HierarchyPickerList.appendChild(button);}t4HierarchyPickerModal.hidden=false;requestAnimationFrame(()=>t4HierarchyPickerList.querySelector('.active')?.scrollIntoView?.({block:'center'}));}
  const t4HierarchySelect1199=hierarchySelect;
  hierarchySelect=function(label,value,options,onChange){const wrap=t4HierarchySelect1199(label,value,options,onChange),select=wrap?.querySelector?.('select');if(!wrap||!select)return wrap;select.classList.add('t4tw-hierarchy-native-select');const pickerOptions=Array.from(select.options||[]).map(option=>({value:String(option.value??''),label:String(option.textContent||option.label||option.value||''),disabled:!!option.disabled}));const button=document.createElement('button');button.type='button';button.className='t4tw-hierarchy-picker-button';const active=pickerOptions.find(option=>option.value===String(value??''));button.textContent=String(active?.label??value??label??'Select');button.title=button.textContent;button.onclick=event=>{event.preventDefault();event.stopPropagation();t4OpenHierarchyPicker(label,value,pickerOptions,onChange);};wrap.appendChild(button);return wrap;};

  function t4ClampMenuPanel(panel){if(!panel)return;panel.style.transform='';panel.style.maxWidth='calc(100vw - 16px)';panel.style.maxHeight='calc(100vh - 16px)';panel.style.overflow='auto';requestAnimationFrame(()=>{const margin=8,viewportWidth=Math.max(1,window.innerWidth||document.documentElement.clientWidth||1),viewportHeight=Math.max(1,window.innerHeight||document.documentElement.clientHeight||1),rect=panel.getBoundingClientRect();let dx=0,dy=0;if(rect.right>viewportWidth-margin)dx=viewportWidth-margin-rect.right;if(rect.left+dx<margin)dx+=margin-(rect.left+dx);if(rect.bottom>viewportHeight-margin)dy=viewportHeight-margin-rect.bottom;if(rect.top+dy<margin)dy+=margin-(rect.top+dy);if(dx||dy)panel.style.transform='translate('+Math.round(dx)+'px,'+Math.round(dy)+'px)';});}
  function t4ClampOpenMenus(){page.querySelectorAll('details.t4tw-menu[open] .t4tw-menu-panel').forEach(t4ClampMenuPanel);}
  page.addEventListener('toggle',event=>{const details=event.target;if(details?.matches?.('details.t4tw-menu')&&details.open)requestAnimationFrame(()=>t4ClampMenuPanel(details.querySelector('.t4tw-menu-panel')));},true);
  window.addEventListener('resize',()=>requestAnimationFrame(t4ClampOpenMenus),{passive:true});window.addEventListener('orientationchange',()=>setTimeout(t4ClampOpenMenus,160),{passive:true});

  const t4RefreshInspector1199=refreshInlineTableInspector;refreshInlineTableInspector=function(){const result=t4RefreshInspector1199();t4SyncTableEditButton();return result;};
  const t4RenderTables1199=renderTablesMode;renderTablesMode=function(q){const result=t4RenderTables1199(q);requestAnimationFrame(()=>{t4PatchTableEditWorkflow();t4ClampOpenMenus();if(tablePresentationMode==='2d')t4ScheduleTableFit(false);});return result;};
  const t4Render1199=render;render=function(){const result=t4Render1199();requestAnimationFrame(()=>{if(viewMode==='tables')t4PatchTableEditWorkflow();t4ClampOpenMenus();});return result;};
