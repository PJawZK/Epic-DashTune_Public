/* TUNER_UI_CONNECTION_REFRESH_V6
 * A successful native USB handshake already owns a complete current-generation TuneSnapshot before
 * STREAMING is published. Adopt that authoritative live workspace first instead of forcing a second
 * complete read merely because the Tuner page became visible. If the live workspace is genuinely
 * unavailable, fall back to the normal explicit Read ECU operation.
 *
 * Native tuning-operation completion is event-driven through EpicDashTuningWriteStateChanged. A
 * bounded status poll remains only as a recovery path for lifecycle windows where the WebView could
 * not receive the native completion callback.
 */
  const t4ConnectionRefreshStyle=document.createElement('style');
  t4ConnectionRefreshStyle.textContent=`
    .t4tw-read-action{border-color:#315f74!important;color:#c9edf9!important}
    .t4tw-read-action:disabled{opacity:.42!important;cursor:not-allowed!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-read-action .t4tw-read-label{display:none!important}
  `;
  document.head.appendChild(t4ConnectionRefreshStyle);

  const t4TopReadButton=document.createElement('button');
  t4TopReadButton.id='t4twTopRead';
  t4TopReadButton.className='t4tw-top-action t4tw-read-action';
  t4TopReadButton.type='button';
  t4TopReadButton.title='Read the complete current tune from the ECU, persist it, and refresh the Tuner view';
  t4TopReadButton.innerHTML='<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 6v5h-5"/><path d="M4 18v-5h5"/><path d="M6.1 9A7 7 0 0 1 18.6 6.4L20 8"/><path d="M17.9 15A7 7 0 0 1 5.4 17.6L4 16"/></svg><span class="t4tw-read-label">Read ECU</span>';
  if(topBurnButton?.parentNode)topBurnButton.insertAdjacentElement('beforebegin',t4TopReadButton);

  let t4ConnectionWasLive=false;
  let t4ConnectionAutoReadIssued=false;
  let t4ConnectionRefreshToken=0;
  let t4ConnectionRefreshTimer=0;

  function t4ConnectionSnapshot(){
    try{return window.EpicDashTunerTelemetrySnapshot?.()||null;}catch(_){return null;}
  }
  function t4ConnectionIsLive(){return t4ConnectionSnapshot()?.connected===true;}
  function t4ConnectionReadStatus(){
    try{
      const raw=window.EpicDashAndroid?.getTuningWriteStatusJson?.();
      return raw?JSON.parse(raw):null;
    }catch(_){return null;}
  }
  function t4ConnectionWorkspaceIsLive(){
    return workspace?.status==='ready'&&
      workspace?.savedProject!==true&&
      workspace?.definitionOnly!==true&&
      Number(workspace?.generation||0)>0&&
      String(workspace?.tuneFingerprint||'').length>0;
  }
  function t4ConnectionApplyTruthChrome(){
    const live=t4ConnectionIsLive();
    if(topEcuState)topEcuState.classList.toggle('connected',live);
    const label=topEcuState?.querySelector?.('b');
    if(label)label.textContent=live?'ECU Connected':'ECU Offline';
    if(!live&&topEcuSub){
      topEcuSub.textContent=workspace?.definitionOnly===true
        ?'INI structure loaded'
        :(workspace?.savedProject===true?'Saved TuneSnapshot':(workspace?.importedProfileName||workspace?.profileName||'mainController.ini'));
    }
  }
  function t4ConnectionUpdateReadButton(){
    const live=t4ConnectionIsLive();
    const busy=tuningWriteStatus?.operationRunning===true||t4ConnectionReadStatus()?.operationRunning===true;
    t4TopReadButton.disabled=!live||busy;
    t4TopReadButton.setAttribute('aria-disabled',t4TopReadButton.disabled?'true':'false');
    t4ConnectionApplyTruthChrome();
  }
  function t4ConnectionAdoptNativeWorkspace(message='ECU connected • current native TuneSnapshot ready'){
    try{loadWorkspace();}catch(_){return false;}
    if(!t4ConnectionWorkspaceIsLive())return false;
    t4ConnectionAutoReadIssued=true;
    meta.textContent=message;
    t4ConnectionUpdateReadButton();
    return true;
  }
  function t4AwaitAutomaticRead(attempt=0,token=t4ConnectionRefreshToken){
    if(token!==t4ConnectionRefreshToken||!t4ConnectionIsLive())return;
    const status=t4ConnectionReadStatus();
    const phase=String(status?.status||'');
    if(phase==='read_complete'){
      if(!t4ConnectionAdoptNativeWorkspace('ECU connected • complete tune read and permanent TuneSnapshot saved')){
        meta.textContent='ECU read completed • live workspace is still unavailable';
      }
      t4ConnectionUpdateReadButton();
      return;
    }
    if(phase==='read_failed'){
      meta.textContent='Automatic Read ECU failed • use Read ECU to retry';
      t4ConnectionUpdateReadButton();
      return;
    }
    if(attempt>=300){
      // Do not queue a second operation here. The native completion event can still reconcile a
      // slow read, and page activation/visibility will re-check the authoritative native status.
      meta.textContent='ECU connected • tune read still running • waiting for native completion';
      t4ConnectionUpdateReadButton();
      return;
    }
    clearTimeout(t4ConnectionRefreshTimer);
    t4ConnectionRefreshTimer=setTimeout(()=>t4AwaitAutomaticRead(attempt+1,token),100);
  }
  function t4StartAutomaticRead(token=t4ConnectionRefreshToken){
    if(token!==t4ConnectionRefreshToken||!page.classList.contains('active')||!t4ConnectionIsLive())return;
    if(t4ConnectionAdoptNativeWorkspace())return;
    const existing=t4ConnectionReadStatus();
    if(existing?.operationRunning===true){
      clearTimeout(t4ConnectionRefreshTimer);
      t4ConnectionRefreshTimer=setTimeout(()=>t4AwaitAutomaticRead(0,token),100);
      return;
    }
    if(String(existing?.status||'')==='read_complete'&&t4ConnectionAdoptNativeWorkspace('ECU connected • complete tune read and permanent TuneSnapshot saved'))return;
    meta.textContent='ECU connected • reading complete current tune…';
    try{readTuningFromEcu();}catch(_){
      meta.textContent='Automatic Read ECU could not start • use Read ECU to retry';
      t4ConnectionUpdateReadButton();
      return;
    }
    clearTimeout(t4ConnectionRefreshTimer);
    t4ConnectionRefreshTimer=setTimeout(()=>t4AwaitAutomaticRead(0,token),100);
  }
  function t4RefreshWorkspaceAfterDisconnect(token=t4ConnectionRefreshToken){
    if(token!==t4ConnectionRefreshToken||t4ConnectionIsLive()||!page.classList.contains('active'))return;
    // PROJECT_STATE_ASSET wraps loadWorkspace() and immediately switches to either the in-session
    // read-only snapshot or the permanent native snapshot bootstrap.
    loadWorkspace();
    t4ConnectionApplyTruthChrome();
  }
  function t4ConnectionReconcileLive(){
    if(!t4ConnectionIsLive()||!page.classList.contains('active'))return false;
    const status=t4ConnectionReadStatus();
    const phase=String(status?.status||'');
    if(phase==='read_complete'&&t4ConnectionAdoptNativeWorkspace('ECU connected • complete tune read and permanent TuneSnapshot saved'))return true;
    if(t4ConnectionAdoptNativeWorkspace())return true;
    return false;
  }
  function t4ConnectionCheck(){
    const live=t4ConnectionIsLive();
    t4ConnectionUpdateReadButton();
    if(live&&page.classList.contains('active')&&!t4ConnectionAutoReadIssued){
      t4ConnectionAutoReadIssued=true;
      t4ConnectionRefreshToken++;
      clearTimeout(t4ConnectionRefreshTimer);
      const token=t4ConnectionRefreshToken;
      if(!t4ConnectionReconcileLive())t4ConnectionRefreshTimer=setTimeout(()=>t4StartAutomaticRead(token),0);
    }
    if(!live&&t4ConnectionWasLive){
      t4ConnectionRefreshToken++;
      clearTimeout(t4ConnectionRefreshTimer);
      const token=t4ConnectionRefreshToken;
      t4ConnectionRefreshTimer=setTimeout(()=>t4RefreshWorkspaceAfterDisconnect(token),0);
    }
    if(!live)t4ConnectionAutoReadIssued=false;
    t4ConnectionWasLive=live;
  }

  t4TopReadButton.onclick=()=>{
    if(!t4ConnectionIsLive()){
      meta.textContent='Read ECU requires a live ECU connection.';
      return;
    }
    meta.textContent='Reading complete current tune…';
    readTuningFromEcu();
    t4ConnectionUpdateReadButton();
  };

  const t4ConnectionBaseWriteStateChanged=window.EpicDashTuningWriteStateChanged;
  window.EpicDashTuningWriteStateChanged=()=>{
    try{t4ConnectionBaseWriteStateChanged?.();}catch(_){}
    if(!page.classList.contains('active'))return;
    const status=t4ConnectionReadStatus();
    const phase=String(status?.status||'');
    if(phase==='read_complete'){
      t4ConnectionRefreshToken++;
      clearTimeout(t4ConnectionRefreshTimer);
      t4ConnectionAdoptNativeWorkspace('ECU connected • complete tune read and permanent TuneSnapshot saved');
    }else if(phase==='read_failed'){
      clearTimeout(t4ConnectionRefreshTimer);
      meta.textContent='Read ECU failed • use Read ECU to retry';
    }
    t4ConnectionUpdateReadButton();
  };

  const t4ConnectionBaseSync=syncTunerChrome;
  syncTunerChrome=function(){
    t4ConnectionBaseSync();
    t4ConnectionCheck();
    t4ConnectionApplyTruthChrome();
  };

  window.addEventListener('epicdash:page-activated',e=>{
    if(e?.detail?.pageId==='page-tuning')setTimeout(t4ConnectionCheck,40);
  });
  document.addEventListener('visibilitychange',()=>{
    if(!document.hidden&&page.classList.contains('active'))setTimeout(t4ConnectionCheck,0);
  });
  setTimeout(t4ConnectionCheck,0);

  /* TUNER_UI_1202_HEADER_ACTIONS_LANDSCAPE_ORIENTATION
   * Refines the accepted 1201 structural shell without creating any new ECU authority. The existing
   * dashboard lock/edit nodes are relocated so their event ownership and lock state remain singular.
   */
  const t4Ui1202Style=document.createElement('style');
  t4Ui1202Style.id='t4-tuning-ui-1202-refinement-style';
  t4Ui1202Style.textContent=`
    .t4tw-shell-actions{display:flex!important;align-items:center!important;gap:5px!important;min-width:0!important}
    .t4tw-shell-lock,.t4tw-shell-edit{width:40px!important;min-width:40px!important;height:40px!important;min-height:40px!important;padding:0!important;display:inline-flex!important;align-items:center!important;justify-content:center!important;gap:0!important;border-radius:9px!important;background:#09151d!important;border:1px solid #2a4757!important;color:#c8d9e3!important;box-shadow:none!important}
    .t4tw-shell-lock .lockWord,.t4tw-shell-edit .editWord{display:none!important}
    .t4tw-shell-lock .padlock.small{margin:0!important}
    .t4tw-shell-edit .layoutGlyph{font-size:20px!important;line-height:1!important;margin:0!important}
    .t4tw-shell-edit[hidden]{display:none!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-shell-lock,body[data-t4tw-viewport="phone-portrait"] .t4tw-shell-edit,
    body[data-t4tw-viewport="phone-landscape"] .t4tw-shell-lock,body[data-t4tw-viewport="phone-landscape"] .t4tw-shell-edit{width:34px!important;min-width:34px!important;height:34px!important;min-height:34px!important}
    body[data-t4tw-viewport="phone-portrait"] .t4tw-read-action .t4tw-read-label,
    body[data-t4tw-viewport="tablet-portrait"] .t4tw-read-action .t4tw-read-label{display:none!important}
    body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-nav-row .t4tw-hierarchy-selectors,
    body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-nav-row .t4tw-hierarchy-selectors{display:flex!important;flex-direction:row!important;flex-wrap:nowrap!important;align-items:stretch!important;gap:4px!important;width:100%!important;min-width:0!important;max-width:100%!important}
    body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-hierarchy-chevron,
    body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-hierarchy-chevron{display:none!important}
    body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-hierarchy-select-wrap,
    body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-hierarchy-select-wrap{display:grid!important;flex:1 1 0!important;width:auto!important;min-width:0!important;max-width:none!important}
    body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-hierarchy-picker-button,
    body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-hierarchy-picker-button,
    body[data-t4tw-viewport="phone-landscape"] #page-tuning .t4tw-hierarchy-select,
    body[data-t4tw-viewport="tablet-landscape"] #page-tuning .t4tw-hierarchy-select{width:100%!important;min-width:0!important;max-width:100%!important}
  `;

  const t4Ui1202ReadLabel=t4TopReadButton.querySelector('.t4tw-read-label');
  if(t4Ui1202ReadLabel)t4Ui1202ReadLabel.textContent='ECU';

  let t4Ui1202SharedActionSlot=null;
  function t4Ui1202DestinationForPage(pageId){
    if(pageId==='page-tuning')return 'tuner';
    if(pageId==='page-analysis')return 'logging';
    if(pageId==='page-diagnostics'||pageId==='page-rules')return 'diagnostics';
    return 'dash';
  }
  function t4Ui1202EnsureSharedActionSlot(){
    if(t4Ui1202SharedActionSlot?.isConnected)return t4Ui1202SharedActionSlot;
    const staticSlot=document.getElementById('sharedTopPageActions');
    if(!staticSlot)return null;
    t4Ui1202SharedActionSlot=staticSlot;
    return staticSlot;
  }
  function t4Ui1202PrepareOwnedControls(){
    const lock=document.getElementById('headerLockBtn');
    const edit=document.getElementById('layoutEditBtn');
    if(lock){lock.classList.add('t4tw-top-action','t4tw-shell-lock');lock.title='Screen lock';}
    if(edit){edit.classList.add('t4tw-top-action','t4tw-shell-edit');edit.title='Edit dashboard';}
    return {lock,edit};
  }
  function t4Ui1202PlacePageActions(pageId=document.querySelector('.page.active')?.id||'page-daily'){
    const {lock,edit}=t4Ui1202PrepareOwnedControls();
    const destination=t4Ui1202DestinationForPage(String(pageId||''));
    if(destination==='tuner'){
      const tunerStatus=page.querySelector('.t4tw-appstatus'),ecu=document.getElementById('t4twTopEcu'),gear=document.getElementById('t4twTopGear');
      if(lock&&tunerStatus){lock.hidden=false;if(lock.parentNode!==tunerStatus)tunerStatus.insertBefore(lock,ecu||gear||null);}
      if(edit&&tunerStatus){edit.hidden=true;if(edit.parentNode!==tunerStatus)tunerStatus.insertBefore(edit,ecu||gear||null);}
      return;
    }
    const slot=t4Ui1202EnsureSharedActionSlot();
    if(lock&&slot){lock.hidden=false;if(lock.parentNode!==slot)slot.appendChild(lock);}
    if(edit&&slot){if(edit.parentNode!==slot)slot.appendChild(edit);edit.hidden=destination!=='dash';}
  }

  /* TUNER_UI_1203_ROTATION_AUTHORITY
   * The dashboard pages already respond on the browser's resize frame. Tuner previously had three
   * overlapping timing paths (90 ms resize debounce, 140 ms orientation retry, and the 1202 32 ms
   * overlay), so its data-viewport attribute could remain in the old orientation while Android was
   * already rotating the WebView. Install one final coalesced authority after every Tuner layer has
   * loaded. screen.orientation supplies the direction immediately; visualViewport supplies the most
   * current geometry; stale layout dimensions retain the already-known phone/tablet family until the
   * browser publishes its new width/height. Delayed legacy callbacks become harmless because force
   * never reflows an unchanged profile.
   */
  let t4Ui1203ViewportFrame=0;
  function t4Ui1203ViewportMetrics(){
    const visual=window.visualViewport;
    const width=Math.max(1,Number(visual?.width)||Number(window.innerWidth)||Number(document.documentElement?.clientWidth)||1);
    const height=Math.max(1,Number(visual?.height)||Number(window.innerHeight)||Number(document.documentElement?.clientHeight)||1);
    const orientationType=String(window.screen?.orientation?.type||'').toLowerCase();
    let landscape;
    if(orientationType.startsWith('landscape'))landscape=true;
    else if(orientationType.startsWith('portrait'))landscape=false;
    else{try{const query=window.matchMedia?.('(orientation: landscape)');landscape=query&&typeof query.matches==='boolean'?query.matches:width>height;}catch(_){landscape=width>height;}}
    return {width,height,landscape};
  }
  function t4Ui1203ProfileForCurrentViewport(){
    const {width,height,landscape}=t4Ui1203ViewportMetrics(),dimensionsLandscape=width>height;
    if(landscape!==dimensionsLandscape&&/^(phone|tablet)-(portrait|landscape)$/.test(String(tunerViewportProfile||''))){
      const family=String(tunerViewportProfile).startsWith('phone-')?'phone':'tablet';
      return family+'-'+(landscape?'landscape':'portrait');
    }
    return tunerProfileFor(width,height);
  }
  function t4Ui1203RefreshGeometry(){
    if(!page.classList.contains('active')||workspace?.status!=='ready')return;
    tableFitPending=viewMode==='tables';
    requestAnimationFrame(()=>{
      if(!page.classList.contains('active'))return;
      if(viewMode==='tables'){
        if(tablePresentationMode==='3d')renderInlineTable3d();
        else fitInlineTable();
      }else if(viewMode==='curves')renderCurveGraph();
    });
  }
  function t4Ui1203ApplyViewportProfile(_force=false){
    const next=t4Ui1203ProfileForCurrentViewport(),changed=next!==tunerViewportProfile;
    if(!changed)return next;
    tunerViewportProfile=next;
    document.body.dataset.t4twViewport=next;
    page.dataset.t4twViewport=next;
    t4Ui1203RefreshGeometry();
    return next;
  }
  function t4Ui1203ScheduleViewportNow(){
    t4Ui1203ApplyViewportProfile(false);
    if(t4Ui1203ViewportFrame)cancelAnimationFrame(t4Ui1203ViewportFrame);
    t4Ui1203ViewportFrame=requestAnimationFrame(()=>{t4Ui1203ViewportFrame=0;t4Ui1203ApplyViewportProfile(false);});
  }
  function t4Ui1203InstallRotationAuthority(){
    try{window.removeEventListener('resize',scheduleTunerViewportProfile);}catch(_){}
    applyTunerViewportProfile=t4Ui1203ApplyViewportProfile;
    window.addEventListener('resize',t4Ui1203ScheduleViewportNow,{passive:true});
    window.visualViewport?.addEventListener('resize',t4Ui1203ScheduleViewportNow,{passive:true});
    window.addEventListener('orientationchange',t4Ui1203ScheduleViewportNow,{passive:true});
    t4Ui1203ScheduleViewportNow();
  }

  window.addEventListener('epicdash:page-activated',e=>t4Ui1202PlacePageActions(String(e?.detail?.pageId||'')));
  queueMicrotask(()=>{
    document.head.appendChild(t4Ui1202Style);
    t4Ui1202PlacePageActions();
    t4Ui1203InstallRotationAuthority();
  });