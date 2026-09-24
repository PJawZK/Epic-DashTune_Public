/* TUNER_UI_TELEMETRY_CONTROLS_V1
 * One-row responsive telemetry with single/double/long-press interactions.
 */
  const T4_GAUGE_KEY='epicdash.tuner.telemetrySlots.v1';
  let t4GaugeConfig=null,t4GaugeModalSlot=-1,t4GaugeTapTimer=0;
  const t4GaugeStyle=document.createElement('style');
  t4GaugeStyle.textContent=`
    .t4tw-telemetry{display:flex!important;grid-template-columns:none!important;flex-wrap:nowrap!important;overflow:hidden!important;gap:6px!important;min-height:58px}
    .t4tw-telemetry-slot{min-width:0!important;flex:1 1 0!important;position:relative;touch-action:manipulation;user-select:none}
    .t4tw-telemetry-slot[hidden]{display:none!important}
    .t4tw-telemetry-slot.t4tw-gauge-focus{border-color:var(--cyan)!important;box-shadow:inset 0 0 0 1px #1fbff055}
    .t4tw-gauge-dialog{width:min(520px,94vw)}
    .t4tw-gauge-row{display:grid;grid-template-columns:120px minmax(0,1fr);gap:10px;align-items:center;margin:10px 0}
    .t4tw-gauge-row select{width:100%;background:#071018;color:#eef7fc;border:1px solid #315064;border-radius:9px;padding:9px}
    .t4tw-gauge-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:14px}.t4tw-gauge-hint{color:#829aa7;font-size:10px;line-height:1.4;margin-top:8px}`;
  document.head.appendChild(t4GaugeStyle);
  function t4GaugeDefault(){return{signals:TUNER_TELEMETRY_KEYS.slice()};}
  function t4GaugeLoad(){
    if(t4GaugeConfig)return t4GaugeConfig;
    try{const x=JSON.parse(localStorage.getItem(T4_GAUGE_KEY)||'null');t4GaugeConfig=x&&Array.isArray(x.signals)?x:t4GaugeDefault();}catch(_){t4GaugeConfig=t4GaugeDefault();}
    while(t4GaugeConfig.signals.length<TUNER_TELEMETRY_KEYS.length)t4GaugeConfig.signals.push(TUNER_TELEMETRY_KEYS[t4GaugeConfig.signals.length]);
    t4GaugeConfig.signals=t4GaugeConfig.signals.slice(0,TUNER_TELEMETRY_KEYS.length).map((k,i)=>TUNER_TELEMETRY_KEYS.includes(k)?k:TUNER_TELEMETRY_KEYS[i]);
    return t4GaugeConfig;
  }
  function t4GaugeSave(){try{localStorage.setItem(T4_GAUGE_KEY,JSON.stringify(t4GaugeLoad()));}catch(_){}}
  function t4GaugeLimit(){
    const p=String(tunerViewportProfile||document.body.dataset.t4twViewport||'');
    if(p==='phone-portrait')return 4;if(p==='phone-landscape')return 6;if(p==='tablet-portrait')return 6;return TUNER_TELEMETRY_KEYS.length;
  }
  function t4GaugeLabel(k,s){const d={rpm:'RPM',map:'MAP',tps:'TPS',afr:'AFR',clt:'CLT',batt:'Battery',iat:'IAT / MAT',fuelPressure:'Fuel pressure',oilPressure:'Oil pressure',ign:'Ignition',boostDuty:'Boost duty'};return s?.meta?.[k]?.label||d[k]||k;}

  const t4GaugeModal=document.createElement('div');t4GaugeModal.className='t4tw-modal';t4GaugeModal.hidden=true;
  t4GaugeModal.innerHTML='<div class="t4tw-dialog t4tw-gauge-dialog"><div class="t4tw-dialog-head"><div class="t4tw-dialog-title" id="t4GaugeTitle">Gauge slot</div>'+
    '<button class="t4tw-close" type="button">Close</button></div><div class="t4tw-gauge-row"><label>Signal</label><select id="t4GaugeSignal"></select></div>'+
    '<div class="t4tw-gauge-actions"><button class="t4tw-btn" id="t4GaugePin" type="button">Pin to left</button><button class="t4tw-btn" id="t4GaugeReset" type="button">Restore slot default</button></div>'+
    '<div class="t4tw-gauge-hint">Single press shows detail. Double press pins the gauge left. Long press opens this editor. Portrait physically deactivates the right-side slots instead of wrapping them.</div></div>';
  page.appendChild(t4GaugeModal);const t4GaugeSignal=t4GaugeModal.querySelector('#t4GaugeSignal');
  t4GaugeModal.querySelector('.t4tw-close').onclick=()=>t4GaugeModal.hidden=true;
  t4GaugeModal.addEventListener('click',e=>{if(e.target===t4GaugeModal)t4GaugeModal.hidden=true;});
  function t4GaugePin(i){const c=t4GaugeLoad();if(i<=0||i>=c.signals.length)return;const s=c.signals[i];c.signals.splice(i,1);c.signals.unshift(s);t4GaugeSave();t4GaugeRender();}
  function t4GaugeOpen(i){
    t4GaugeModalSlot=i;let s=null;try{s=window.EpicDashTunerTelemetrySnapshot?.()||null;}catch(_){}
    t4GaugeSignal.textContent='';TUNER_TELEMETRY_KEYS.forEach(k=>{const o=document.createElement('option');o.value=k;o.textContent=t4GaugeLabel(k,s);t4GaugeSignal.appendChild(o);});
    t4GaugeSignal.value=t4GaugeLoad().signals[i]||TUNER_TELEMETRY_KEYS[i];t4GaugeModal.querySelector('#t4GaugeTitle').textContent='Gauge slot '+(i+1);t4GaugeModal.hidden=false;
  }
  t4GaugeSignal.onchange=()=>{if(t4GaugeModalSlot<0)return;t4GaugeLoad().signals[t4GaugeModalSlot]=String(t4GaugeSignal.value||'');t4GaugeSave();t4GaugeRender();};
  t4GaugeModal.querySelector('#t4GaugePin').onclick=()=>{t4GaugePin(t4GaugeModalSlot);t4GaugeModalSlot=0;t4GaugeModal.querySelector('#t4GaugeTitle').textContent='Gauge slot 1';};
  t4GaugeModal.querySelector('#t4GaugeReset').onclick=()=>{if(t4GaugeModalSlot<0)return;t4GaugeLoad().signals[t4GaugeModalSlot]=TUNER_TELEMETRY_KEYS[t4GaugeModalSlot];t4GaugeSave();t4GaugeSignal.value=t4GaugeLoad().signals[t4GaugeModalSlot];t4GaugeRender();};

  function t4GaugeSingle(i,s){
    const k=t4GaugeLoad().signals[i]||TUNER_TELEMETRY_KEYS[i],slots=telemetryStrip?.querySelectorAll('.t4tw-telemetry-slot');
    slots?.forEach(n=>n.classList.remove('t4tw-gauge-focus'));slots?.[i]?.classList.add('t4tw-gauge-focus');
    const value=s?.valid?.[k]===false?'—':formatTelemetryValue(s?.values?.[k],k),unit=s?.meta?.[k]?.unit||'';
    meta.textContent=t4GaugeLabel(k,s)+' • '+value+(unit?' '+unit:'')+' • long press to customize';
  }
  function t4GaugeBind(slot,i){
    if(!slot||slot.dataset.t4GaugeBound==='1')return;slot.dataset.t4GaugeBound='1';
    let down=null,longTimer=0,longFired=false,lastTap=0;
    slot.addEventListener('pointerdown',e=>{
      if(e.button!==undefined&&e.button!==0)return;down={x:e.clientX,y:e.clientY,id:e.pointerId};longFired=false;clearTimeout(longTimer);
      longTimer=setTimeout(()=>{if(!down)return;longFired=true;clearTimeout(t4GaugeTapTimer);t4GaugeOpen(i);window.EpicDashHaptic?.('selection');},520);
    });
    slot.addEventListener('pointermove',e=>{if(down&&down.id===e.pointerId&&Math.hypot(e.clientX-down.x,e.clientY-down.y)>12){clearTimeout(longTimer);down=null;}});
    slot.addEventListener('pointerup',e=>{
      clearTimeout(longTimer);if(!down||down.id!==e.pointerId){down=null;return;}down=null;if(longFired){longFired=false;return;}
      const now=performance.now();if(now-lastTap<330){lastTap=0;clearTimeout(t4GaugeTapTimer);t4GaugePin(i);window.EpicDashHaptic?.('selection');meta.textContent='Gauge pinned to the left.';return;}
      lastTap=now;clearTimeout(t4GaugeTapTimer);t4GaugeTapTimer=setTimeout(()=>{let s=null;try{s=window.EpicDashTunerTelemetrySnapshot?.()||null;}catch(_){}t4GaugeSingle(i,s);},250);
    });
    slot.addEventListener('pointercancel',()=>{clearTimeout(longTimer);down=null;});slot.addEventListener('contextmenu',e=>e.preventDefault());
  }
  function t4GaugeRender(){
    if(!telemetryStrip||!ensurePersistentTunerTelemetry())return;let s=null;try{s=window.EpicDashTunerTelemetrySnapshot?.()||null;}catch(_){}
    const c=t4GaugeLoad(),limit=t4GaugeLimit(),slots=Array.from(telemetryStrip.querySelectorAll('.t4tw-telemetry-slot'));
    slots.forEach((slot,i)=>{
      const original=TUNER_TELEMETRY_KEYS[i],key=c.signals[i]||original,refs=tunerTelemetryNodeCache.get(original);
      slot.hidden=i>=limit;slot.inert=i>=limit;slot.dataset.telemetrySignal=key;t4GaugeBind(slot,i);if(!refs||!s)return;
      const label=t4GaugeLabel(key,s),value=s.valid?.[key]===false?'—':formatTelemetryValue(s.values?.[key],key),unit=s.meta?.[key]?.unit||'';
      if(refs.lastLabel!==label){refs.label.textContent=label;refs.lastLabel=label;}if(refs.lastValue!==value){refs.number.textContent=value;refs.lastValue=value;}if(refs.lastUnit!==unit){refs.unit.textContent=unit;refs.lastUnit=unit;}
    });
  }
  renderTunerTelemetry=function(){
    if(!telemetryStrip||!page.classList.contains('active'))return;t4GaugeRender();
    const now=performance.now();if(now-tunerChromeLastTelemetrySync>=350){tunerChromeLastTelemetrySync=now;syncTunerChrome();}
  };
  const t4GaugeBaseViewport=applyTunerViewportProfile;
  applyTunerViewportProfile=function(force=false){const p=t4GaugeBaseViewport(force);requestAnimationFrame(t4GaugeRender);return p;};
  window.addEventListener('epicdash:page-activated',e=>{if(e?.detail?.pageId==='page-tuning')setTimeout(t4GaugeRender,40);});
  t4GaugeLoad();

  /* TUNER_UI_1200_PHONE_CURVES_AND_UNIFIED_SHELL
   * 1200 phone Tables/Curves behavior is retained. The former delayed global-shell skin is superseded
   * by the structural shared-shell implementation below so old chrome is never the runtime authority.
   */
  const t4Ui1200Style=document.createElement('style');
  t4Ui1200Style.id='t4-tuning-ui-1200-style';
  t4Ui1200Style.textContent=`
    body[data-t4tw-viewport="phone-portrait"] #page-tuning .t4tw-telemetry,
    body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-telemetry{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-main .t4tw-zoom-cluster,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-main .t4tw-zoom-cluster{display:none!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-selectors{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important;gap:4px!important;min-width:0!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-select-wrap{min-width:0!important;overflow:hidden!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-hierarchy-picker-button{min-width:0!important;max-width:100%!important;white-space:nowrap!important;overflow:hidden!important;text-overflow:ellipsis!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-view-controls{overflow:visible!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-table-view-controls>.t4tw-btn,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-table-view-controls>.t4tw-btn{height:34px!important;min-width:38px!important;padding:0 8px!important}
    .t4tw-curve-manager-trigger{height:34px!important;min-width:88px!important;padding:0 9px!important;margin-right:auto!important;white-space:nowrap!important}
    .t4tw-curve-manager-modal{position:fixed;inset:0;z-index:10058;background:rgba(0,0,0,.72);display:flex;align-items:center;justify-content:center;padding:8px}
    .t4tw-curve-manager-modal[hidden]{display:none!important}
    .t4tw-curve-manager-dialog{width:min(720px,calc(100vw - 16px));max-width:calc(100vw - 16px);max-height:calc(100vh - 16px);display:flex;flex-direction:column;overflow:hidden;background:#0b141c;border:1px solid #315064;border-radius:14px;box-shadow:0 20px 70px rgba(0,0,0,.55)}
    .t4tw-curve-manager-dialog-head{display:flex;align-items:center;gap:8px;padding:9px 10px;border-bottom:1px solid #243f4d}.t4tw-curve-manager-dialog-head b{flex:1;font-size:13px}.t4tw-curve-manager-dialog-body{min-height:0;overflow:auto;padding:7px}
    .t4tw-curve-manager-modal .t4tw-curve-manager{display:flex!important;flex-direction:column!important;width:100%!important;min-height:0!important;max-height:none!important;border:0!important;background:transparent!important}
    .t4tw-curve-manager-modal .t4tw-curve-manager-head{display:none!important}
    .t4tw-curve-manager-modal .t4tw-curve-list{display:grid!important;grid-template-columns:1fr!important;gap:0!important;overflow:visible!important;max-height:none!important;padding:0!important}
    .t4tw-curve-manager-modal .t4tw-curve-row{width:100%!important;min-width:0!important;max-width:none!important;flex:none!important;border-right:0!important;border-bottom:1px solid #18313d!important;padding:8px 9px!important}
    .t4tw-curve-manager-modal .t4tw-curve-add{width:100%!important;flex:none!important;padding:8px 0 0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-main,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-main{display:flex!important;flex-direction:column!important;min-height:0!important;overflow:hidden!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-head,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-head{flex:0 0 auto!important;align-items:center!important;gap:5px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-mode{width:100%!important;display:flex!important;align-items:center!important;justify-content:flex-end!important;gap:4px!important;margin-left:0!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-mode{display:flex!important;align-items:center!important;justify-content:flex-end!important;gap:4px!important;margin-left:auto!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-workspace,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-workspace{display:grid!important;grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(0,1fr)!important;flex:1 1 auto!important;min-height:0!important;height:auto!important;overflow:hidden!important;gap:0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-plot-wrap,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-plot-wrap{min-height:0!important;height:100%!important;max-height:none!important;overflow:hidden!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-svg{min-height:240px!important;height:100%!important;max-height:none!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-svg{min-height:130px!important;height:100%!important;max-height:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-editbar{position:static!important;inset:auto!important;z-index:auto!important;flex:0 0 auto!important;width:100%!important;max-width:100%!important;margin:0!important;background:#07131a!important;border:1px solid #213e4c!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-editbar{flex-wrap:nowrap!important;overflow-x:auto!important;padding:5px!important;gap:4px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-editbar{flex-wrap:wrap!important;overflow:visible!important;padding:6px!important;gap:4px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-editbar>.t4tw-btn{height:32px!important;min-width:36px!important;padding:0 7px!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-value-input{height:32px!important;width:72px!important;flex:0 0 72px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-manager-modal{align-items:flex-end!important;padding:0!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-manager-dialog{width:100vw!important;max-width:100vw!important;max-height:76vh!important;border-radius:16px 16px 0 0!important}
    #t4twTopBurn{width:40px!important;min-width:40px!important;padding:0!important;justify-content:center!important;gap:0!important}
    #t4twTopBurn #t4twTopBurnLabel{display:none!important}
    #t4twTopBurn svg{width:22px!important;height:22px!important;stroke-width:1.9!important}
    #t4twTopBurn.clean{color:#657a86!important;border-color:#2a4757!important;background:#09151d!important;opacity:1!important;filter:none!important}
    #t4twTopBurn:not(.clean):not(:disabled){color:#ffad2f!important;border-color:#a86800!important;background:#1d1407!important;box-shadow:0 0 12px #ff9a1f22}
    #t4twTopBurn:disabled{color:#8f7a55!important;border-color:#5f5137!important;background:#15130e!important;opacity:.72!important;filter:none!important}
    #labSettingsBtn svg,.t4tw-gear svg{width:21px;height:21px;stroke:currentColor;fill:none;stroke-width:1.8}
    .t4tw-gear{width:40px!important;min-width:40px!important;height:40px!important;padding:0!important;justify-content:center!important}
  `;
  document.head.appendChild(t4Ui1200Style);

  const T4_UI_1200_SETTINGS_ICON='<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56V21h-4v-.08A1.7 1.7 0 0 0 8.97 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.6 15.03 1.7 1.7 0 0 0 3.04 14H3v-4h.04A1.7 1.7 0 0 0 4.6 8.97a1.7 1.7 0 0 0-.34-1.88l-.06-.06L7.03 4.2l.06.06a1.7 1.7 0 0 0 1.88.34A1.7 1.7 0 0 0 10 3.04V3h4v.04a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06a1.7 1.7 0 0 0-.34 1.88A1.7 1.7 0 0 0 20.96 10H21v4h-.04A1.7 1.7 0 0 0 19.4 15z"/></svg>';
  const T4_UI_1200_FLAME_ICON='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22c4.4 0 8-3.6 8-8 0-3.2-1.7-5.7-4.5-8 .2 2.8-1.2 4.5-2.9 5.4.3-3.8-1.8-6.8-5.1-9.4.2 3.5-1.6 5.8-2.6 7.6C3.8 11.6 3.5 13.1 3.5 15A7.5 7.5 0 0 0 11 22h1z"/><path d="M9.2 17.1c0-1.5.9-2.7 2.6-4.1.1 1.5.8 2.3 1.6 3 .8.7 1.1 1.4 1.1 2.2 0 1.8-1.4 3.1-3.2 3.1-1.3 0-2.1-1.7-2.1-4.2z"/></svg>';
  const T4_UI_1201_NAV_ICONS={
    dash:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 15a8 8 0 1 1 16 0"/><path d="M12 12l4-4"/><path d="M7 18h10"/></svg>',
    tuner:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 6l4-4 4 4-4 4"/><path d="M13 7L4 16v4h4l9-9"/></svg>',
    logging:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 20V11M12 20V4M19 20v-7"/></svg>',
    diagnostics:'<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 13h4l2-7 4 14 3-9 2 4h5"/></svg>'
  };
  const T4_UI_1201_INI_ICON='<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M6 3h8l4 4v14H6z"/><path d="M14 3v5h5"/><path d="M9 12h6M9 16h6"/></svg>';
  const T4_UI_1201_READ_ICON='<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M20 6v5h-5"/><path d="M4 18v-5h5"/><path d="M6.1 9A7 7 0 0 1 18.6 6.4L20 8"/><path d="M17.9 15A7 7 0 0 1 5.4 17.6L4 16"/></svg>';

  function t4Ui1200PatchTunerChrome(){
    const tunerSettings=page.querySelector('.t4tw-gear');
    if(tunerSettings&&tunerSettings.dataset.t4Ui1200!=='1'){tunerSettings.innerHTML=T4_UI_1200_SETTINGS_ICON;tunerSettings.dataset.t4Ui1200='1';tunerSettings.title='Settings';}
    const burn=document.getElementById('t4twTopBurn');
    if(burn&&burn.dataset.t4Ui1200!=='1'){const old=burn.querySelector('svg');old?.remove();burn.insertAdjacentHTML('afterbegin',T4_UI_1200_FLAME_ICON);burn.dataset.t4Ui1200='1';burn.setAttribute('aria-label','Burn ECU changes');burn.title='Burn ECU changes';}
  }
  t4Ui1200PatchTunerChrome();

  /* TUNER_UI_1201_STRUCTURAL_SHARED_SHELL
   * Dash / Logging / Diagnostics now reuse the Tuner appbar structure and component classes.
   * Existing interactive nodes are moved, not recreated, so bound behavior survives. This replaces
   * the previous delayed CSS/innerHTML skin and removes the old-shell -> new-shell settling effect.
   */
  const t4Ui1201SharedStyle=document.createElement('style');
  t4Ui1201SharedStyle.id='t4-tuning-ui-1201-shared-shell-style';
  t4Ui1201SharedStyle.textContent=`
    body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app{grid-template-columns:minmax(0,1fr)!important;grid-template-rows:auto auto minmax(0,1fr) auto!important;gap:0!important;padding:0!important}
    body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app>header.t4tw-topchrome{grid-row:1!important;display:block!important;min-width:0!important;max-width:100%!important;min-height:0!important;overflow:visible!important;padding:0!important;gap:0!important;border:0!important;border-bottom:1px solid #17313f!important;border-radius:0!important;background:#071018f2!important;box-shadow:none!important}
    body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app>header .t4tw-appbar{width:100%;max-width:100%}
    body.t4tw-shared-shell-ready #shellTabs.t4tw-appnav{grid-row:auto!important;display:grid!important;grid-template-columns:repeat(4,minmax(86px,1fr))!important;gap:6px!important;max-width:620px!important;min-width:0!important;overflow:visible!important;padding:0!important}
    body.t4tw-shared-shell-ready #shellTabs .shellTab.t4tw-appnav-btn{display:flex!important;gap:8px!important;align-items:center!important;justify-content:center!important;min-width:0!important;min-height:0!important;border:0!important;border-radius:10px!important;background:transparent!important;color:#c8d9e3!important;padding:9px 12px!important;font-size:13px!important;font-weight:800!important;letter-spacing:0!important;white-space:nowrap!important;box-shadow:none!important}
    body.t4tw-shared-shell-ready #shellTabs .shellTab.t4tw-appnav-btn svg{width:20px!important;height:20px!important;stroke:currentColor!important;fill:none!important;stroke-width:1.8!important;flex:0 0 auto!important}
    body.t4tw-shared-shell-ready #shellTabs .shellTab.t4tw-appnav-btn.active{background:#0b3655!important;color:#13b9ff!important}
    body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app>#tabs,body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app>#diagnosticsTabs{grid-row:2!important;margin:7px 7px 0!important}
    body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app>main{grid-row:3!important;min-height:0!important;margin:7px!important}
    body.t4tw-shared-shell-ready:not(.t4tw-tuner-active)>#app>footer{grid-row:4!important;margin:0 7px 7px!important}
    .t4tw-shared-appstatus{min-width:0}
    .t4tw-shared-appstatus .iniPill #iniStateLabel[hidden]{display:none!important}
    .t4tw-shared-read{border-color:#315f74!important;color:#c9edf9!important}
    .t4tw-shared-read:disabled{opacity:.42!important;cursor:not-allowed!important}
    .t4tw-shared-burn{width:40px!important;min-width:40px!important;padding:0!important;justify-content:center!important;gap:0!important}
    .t4tw-shared-burn svg{width:22px!important;height:22px!important;stroke-width:1.9!important}
    .t4tw-shared-burn.clean{color:#657a86!important;border-color:#2a4757!important;background:#09151d!important;opacity:1!important;filter:none!important}
    .t4tw-shared-burn:not(.clean):not(:disabled){color:#ffad2f!important;border-color:#a86800!important;background:#1d1407!important;box-shadow:0 0 12px #ff9a1f22}
    .t4tw-shared-burn:disabled{color:#8f7a55!important;border-color:#5f5137!important;background:#15130e!important;opacity:.72!important}
    .t4tw-shared-ecu-state #srcDot.dot{width:12px!important;height:12px!important;border-radius:50%!important;background:#4e6572!important;box-shadow:0 0 0 3px #4e657218!important;flex:0 0 auto!important}
    .t4tw-shared-ecu-state.connected #srcDot.dot{background:#00e676!important;box-shadow:0 0 10px #00e67680!important}
    .t4tw-shared-demo{margin-left:auto}
    @media(max-width:720px){
      body.t4tw-shared-shell-ready #shellTabs .shellTab.t4tw-appnav-btn{font-size:11px!important;gap:5px!important}
      body.t4tw-shared-shell-ready #shellTabs .shellTab.t4tw-appnav-btn svg{width:18px!important;height:18px!important}
    }
    @media(max-width:520px){
      .t4tw-shared-appstatus .t4tw-ecu-copy{display:none!important}
      .t4tw-shared-read .t4tw-read-label{display:none!important}
      body.t4tw-shared-shell-ready #shellTabs .shellTab.t4tw-appnav-btn{padding:5px 6px!important;font-size:10px!important;gap:4px!important}
    }
  `;
  document.head.appendChild(t4Ui1201SharedStyle);

  let t4Ui1201SharedRead=null,t4Ui1201SharedBurn=null,t4Ui1201SharedEcu=null,t4Ui1201SharedEcuLabel=null,t4Ui1201SharedEcuSub=null;
  function t4Ui1201InstallSharedShell(){
    const app=document.getElementById('app');
    const header=app?.querySelector(':scope > header.t4tw-shared-topchrome');
    const nav=document.getElementById('shellTabs');
    const ini=document.getElementById('iniStatePill');
    const srcDot=document.getElementById('srcDot');
    const srcLabel=document.getElementById('srcLabel');
    const settings=document.getElementById('labSettingsBtn');
    const ecu=document.getElementById('sharedTopEcuState');
    const ecuLabel=document.getElementById('sharedTopEcuLabel');
    const ecuSub=document.getElementById('sharedTopEcuSub');
    if(!app||!header||!nav||!ini||!srcDot||!srcLabel||!settings||!ecu||!ecuLabel||!ecuSub)return false;
    if(!nav.classList.contains('t4tw-appnav')||nav.querySelectorAll('.shellTab.t4tw-appnav-btn[data-shell]').length!==4)return false;
    t4Ui1201SharedRead=null;
    t4Ui1201SharedBurn=null;
    t4Ui1201SharedEcu=ecu;
    t4Ui1201SharedEcuLabel=ecuLabel;
    t4Ui1201SharedEcuSub=ecuSub;
    srcLabel.hidden=true;
    const demo=document.getElementById('demoIndicator');
    if(demo){demo.classList.add('t4tw-shared-demo');(document.querySelector('#app>footer .controls')||document.querySelector('#app>footer'))?.append(demo);}
    document.body.classList.add('t4tw-shared-shell-ready');
    return true;
  }

  function t4Ui1201LiveConnected(){try{return window.EpicDashTunerTelemetrySnapshot?.()?.connected===true;}catch(_){return false;}}
  function t4Ui1201SyncSharedConnection(){
    if(!t4Ui1201SharedEcu)return;
    const live=t4Ui1201LiveConnected(),source=String(document.getElementById('srcLabel')?.textContent||'').trim();
    t4Ui1201SharedEcu.classList.toggle('connected',live);
    if(t4Ui1201SharedEcuLabel)t4Ui1201SharedEcuLabel.textContent=live?'ECU Connected':'ECU Offline';
    if(t4Ui1201SharedEcuSub)t4Ui1201SharedEcuSub.textContent=live?(source==='USB'?'USB ECU':'Live ECU transport'):'Waiting for live ECU';
    const nativeRead=document.getElementById('t4twTopRead');if(t4Ui1201SharedRead)t4Ui1201SharedRead.disabled=!live||nativeRead?.disabled===true;
  }
  function t4Ui1201SyncSharedBurn(){
    const nativeBurn=document.getElementById('t4twTopBurn');if(!nativeBurn||!t4Ui1201SharedBurn)return;
    t4Ui1201SharedBurn.disabled=nativeBurn.disabled===true;t4Ui1201SharedBurn.classList.toggle('clean',nativeBurn.classList.contains('clean'));
  }
  t4Ui1201InstallSharedShell();
  t4Ui1201SyncSharedConnection();t4Ui1201SyncSharedBurn();
  const t4Ui1201NativeRead=document.getElementById('t4twTopRead'),t4Ui1201NativeBurn=document.getElementById('t4twTopBurn'),t4Ui1201SourceDot=document.getElementById('srcDot'),t4Ui1201SourceLabel=document.getElementById('srcLabel');
  if(typeof MutationObserver==='function'){
    if(t4Ui1201NativeRead)new MutationObserver(t4Ui1201SyncSharedConnection).observe(t4Ui1201NativeRead,{attributes:true,attributeFilter:['disabled','class']});
    if(t4Ui1201NativeBurn)new MutationObserver(t4Ui1201SyncSharedBurn).observe(t4Ui1201NativeBurn,{attributes:true,attributeFilter:['disabled','class']});
    if(t4Ui1201SourceDot)new MutationObserver(t4Ui1201SyncSharedConnection).observe(t4Ui1201SourceDot,{attributes:true,attributeFilter:['class']});
    if(t4Ui1201SourceLabel)new MutationObserver(t4Ui1201SyncSharedConnection).observe(t4Ui1201SourceLabel,{childList:true,subtree:true,characterData:true});
  }

  let t4Ui1200CurveModal=null,t4Ui1200CurveBody=null;
  function t4Ui1200EnsureCurveModal(){
    if(t4Ui1200CurveModal?.isConnected)return;
    t4Ui1200CurveModal=document.createElement('div');t4Ui1200CurveModal.id='t4twCurveManagerModal';t4Ui1200CurveModal.className='t4tw-curve-manager-modal';t4Ui1200CurveModal.hidden=true;
    const dialog=document.createElement('section');dialog.className='t4tw-curve-manager-dialog';dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');dialog.setAttribute('aria-label','Loaded curves');
    const head=document.createElement('div');head.className='t4tw-curve-manager-dialog-head';const title=document.createElement('b');title.textContent='Loaded curves';const close=document.createElement('button');close.type='button';close.className='t4tw-btn';close.textContent='Close';close.onclick=()=>t4Ui1200CurveModal.hidden=true;head.append(title,close);
    t4Ui1200CurveBody=document.createElement('div');t4Ui1200CurveBody.className='t4tw-curve-manager-dialog-body';dialog.append(head,t4Ui1200CurveBody);t4Ui1200CurveModal.appendChild(dialog);page.appendChild(t4Ui1200CurveModal);
    t4Ui1200CurveModal.addEventListener('click',event=>{if(event.target===t4Ui1200CurveModal)t4Ui1200CurveModal.hidden=true;});
  }
  function t4Ui1200PatchCurveManager(){
    if(viewMode!=='curves')return;
    const mode=page.querySelector('.t4tw-curve-mode'),manager=page.querySelector('.t4tw-curve-workspace .t4tw-curve-manager')||page.querySelector('.t4tw-curve-manager');if(!mode||!manager)return;
    t4Ui1200EnsureCurveModal();
    let trigger=mode.querySelector('#t4twCurveManagerButton');if(!trigger){trigger=document.createElement('button');trigger.type='button';trigger.id='t4twCurveManagerButton';trigger.className='t4tw-btn t4tw-curve-manager-trigger';mode.prepend(trigger);}
    const count=manager.querySelectorAll('.t4tw-curve-row').length;trigger.textContent=count?'Loaded · '+count:'Loaded curves';trigger.title='Loaded curves';trigger.onclick=()=>{t4Ui1200CurveModal.hidden=false;};
    if(t4Ui1200CurveBody.firstElementChild!==manager)t4Ui1200CurveBody.replaceChildren(manager);
  }

  const t4Ui1200BaseCurves=renderCurvesMode;
  renderCurvesMode=function(q){const result=t4Ui1200BaseCurves(q);requestAnimationFrame(()=>{t4Ui1200PatchCurveManager();t4Ui1200PatchTunerChrome();});return result;};
  const t4Ui1200BaseRender=render;
  render=function(){const result=t4Ui1200BaseRender();requestAnimationFrame(()=>{t4Ui1200PatchTunerChrome();t4Ui1201SyncSharedBurn();if(viewMode==='curves')t4Ui1200PatchCurveManager();else if(t4Ui1200CurveModal)t4Ui1200CurveModal.hidden=true;});return result;};
  window.addEventListener('epicdash:page-activated',()=>{t4Ui1201InstallSharedShell();t4Ui1201SyncSharedConnection();t4Ui1201SyncSharedBurn();if(viewMode==='curves')requestAnimationFrame(t4Ui1200PatchCurveManager);});


  /* TUNER_UI_1207_MEASURED_CURVE_GEOMETRY */
  const t4Ui1207CurveStyle=document.createElement('style');
  t4Ui1207CurveStyle.id='t4-tuning-ui-1207-curve-geometry-style';
  t4Ui1207CurveStyle.textContent=`
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-svg,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-svg{width:100%!important;height:100%!important;min-height:0!important;max-height:none!important;display:block!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-curve-plot-wrap,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-plot-wrap{height:auto!important;min-height:0!important;max-height:none!important;flex:1 1 auto!important;overflow:hidden!important}
    body[data-t4tw-viewport="phone-landscape"] .t4tw-curve-main{overflow:hidden!important}
  `;
  document.head.appendChild(t4Ui1207CurveStyle);
  let t4Ui1207CurveObserver=null,t4Ui1207CurveHost=null,t4Ui1207CurveSize='';
  function t4Ui1207ObserveCurveGeometry(){
    const host=document.getElementById('t4twCurvePlot');
    if(!host||host===t4Ui1207CurveHost)return;
    t4Ui1207CurveObserver?.disconnect?.();t4Ui1207CurveHost=host;t4Ui1207CurveSize='';
    if(typeof ResizeObserver!=='function')return;
    t4Ui1207CurveObserver=new ResizeObserver(entries=>{
      const rect=entries?.[0]?.contentRect;if(!rect||rect.width<80||rect.height<80)return;
      const key=Math.round(rect.width)+'x'+Math.round(rect.height);if(key===t4Ui1207CurveSize)return;t4Ui1207CurveSize=key;
      requestAnimationFrame(()=>{if(viewMode==='curves'&&host.isConnected)renderCurveGraph();});
    });
    t4Ui1207CurveObserver.observe(host);
  }
  const t4Ui1207BaseCurves=renderCurvesMode;
  renderCurvesMode=function(q){const result=t4Ui1207BaseCurves(q);requestAnimationFrame(t4Ui1207ObserveCurveGeometry);return result;};
  window.addEventListener('epicdash:page-activated',e=>{if(e?.detail?.pageId==='page-tuning'&&viewMode==='curves')requestAnimationFrame(t4Ui1207ObserveCurveGeometry);});
