/* TUNER_UI_PROJECT_STATE_V7_LAZY_OFFLINE
 * TunerStudio-style project lifecycle.
 *
 * The currently loaded INI and the last complete native TuneSnapshot are projected by Android.
 * Surface installation stays lightweight: the persisted offline project is requested only when the
 * live ECU path is absent. This layer never owns ECU transport or write/Burn authority. Saved
 * TuneSnapshot tables/curves may stage local drafts, but live native validation remains mandatory
 * before any ECU RAM write.
 */
  let t4ProjectDetachedActive=false;
  let t4ProjectRequestPending=false;
  let t4ProjectRequestTimer=0;
  let t4ProjectSavedAt=0;

  function t4ProjectLiveConnected(){try{return typeof t4ConnectionIsLive==='function'&&t4ConnectionIsLive();}catch(_){return false;}}
  function t4ProjectRequestPermanent(reason='offline'){
    if(t4ProjectLiveConnected())return false;
    const source=window.__EPIC_TUNER_PERMANENT_WORKSPACE__;
    if(source?.status==='ready'||t4ProjectRequestPending)return false;
    const request=window.EpicDashAndroid?.requestPermanentTunerProject;
    if(typeof request!=='function')return false;
    let result={status:'unavailable'};
    try{
      const raw=request.call(window.EpicDashAndroid);
      if(raw)result=JSON.parse(raw);
    }catch(_){}
    const status=String(result?.status||'unavailable');
    if(status==='queued'||status==='already_requested'){
      t4ProjectRequestPending=true;
      window.__EPIC_TUNER_PERMANENT_WORKSPACE__={status:'loading',capability:'READ_ONLY',reason:'Loading saved Tuner project…'};
      return true;
    }
    if(status==='deferred_live_path'){
      window.__EPIC_TUNER_PERMANENT_WORKSPACE__={status:'loading',capability:'READ_ONLY',reason:'Waiting for live ECU bootstrap to settle…'};
      clearTimeout(t4ProjectRequestTimer);
      t4ProjectRequestTimer=setTimeout(()=>{t4ProjectRequestTimer=0;t4ProjectRequestPermanent(reason);},500);
    }
    return false;
  }
  function t4ProjectReleasePermanentForLive(){
    if(!t4ProjectLiveConnected())return;
    t4ProjectRequestPending=false;
    clearTimeout(t4ProjectRequestTimer);t4ProjectRequestTimer=0;
    const source=window.__EPIC_TUNER_PERMANENT_WORKSPACE__;
    if(source?.status==='ready'||source?.status==='loading'){
      window.__EPIC_TUNER_PERMANENT_WORKSPACE__={status:'deferred_live',capability:'READ_ONLY',reason:'Saved project released while ECU is live'};
    }
  }
  function t4ProjectBootstrap(){
    const source=window.__EPIC_TUNER_PERMANENT_WORKSPACE__;
    if(!source||typeof source!=='object')return null;
    try{return JSON.parse(JSON.stringify(source));}catch(_){return null;}
  }
  function t4ProjectReadOnlyClone(data,source='SAVED_TUNE_SNAPSHOT'){
    if(data?.status!=='ready')return null;
    let next=null;
    try{next=JSON.parse(JSON.stringify(data));}catch(_){return null;}
    next.generation=0;
    next.capability=next.definitionOnly===true?'INI_STRUCTURE':'SAVED_PROJECT';
    next.source=next.definitionOnly===true?'INI_DEFINITION':source;
    next.savedProject=next.definitionOnly!==true;
    for(const key of ['scalars','arrays','bitFields','tables','curves']){
      for(const item of next[key]||[]){
        item.writeAllowed=false;
        item.offlineEditable=next.definitionOnly!==true&&['arrays','tables','curves'].includes(key);
        item.writeBlockReason=next.definitionOnly===true
          ?'No TuneSnapshot value is available; INI structure only.'
          :item.offlineEditable
            ?'ECU is offline; edits may be staged against this saved TuneSnapshot and require live validation before RAM write.'
            :'ECU is offline; values are from the last complete native TuneSnapshot.';
      }
    }
    return next;
  }
  function t4ProjectClearTransientEdits(){
    draft={};arrayDraft={};bitDraft={};selected=null;queuedSemanticWrite=null;currentSettingsItem=null;
    selectedArray=null;arrayDetail=null;selectedArrayCell=-1;selectedSurface=null;
  }
  function t4ProjectRestoreExactDrafts(){
    try{loadDraft();}catch(_){}
    try{loadArrayDraft();}catch(_){}
    try{loadBitDraft();}catch(_){}
    try{updateChangeCount();}catch(_){}
  }
  function t4ProjectApplyChrome(){
    page.classList.toggle('t4tw-saved-project',t4ProjectDetachedActive);
    if(!t4ProjectDetachedActive)return;
    page.querySelectorAll('.t4tw-realwrite,#t4twTopBurn,#t4twSaveEcu').forEach(node=>{node.disabled=true;node.setAttribute('aria-disabled','true');});
    if(topBurnButton){topBurnButton.disabled=true;if(topBurnLabel)topBurnLabel.textContent='Burn unavailable';}
    if(workspace?.definitionOnly===true){
      meta.textContent=(workspace?.importedProfileName||'mainController.ini')+' • INI structure loaded • table/curve values require a matching TuneSnapshot';
    }else if(workspace?.status==='ready'){
      const stamp=Number(workspace?.capturedAtEpochMs||t4ProjectSavedAt||0);
      const label=stamp?new Date(stamp).toLocaleString():'';
      meta.textContent=(workspace?.importedProfileName||'mainController.ini')+' • permanent TuneSnapshot'+(label?' • '+label:'')+' • ECU offline • table/curve drafts enabled';
    }
  }
  function t4ProjectPermanentCandidate(){
    const bootstrap=t4ProjectBootstrap();
    return bootstrap?.status==='ready'?t4ProjectReadOnlyClone(bootstrap,String(bootstrap?.source||'SAVED_TUNE_SNAPSHOT')):bootstrap;
  }
  function t4ProjectShowPending(candidate=t4ProjectPermanentCandidate()){
    if(!['loading','deferred','deferred_live'].includes(String(candidate?.status||'')))return false;
    meta.textContent=String(candidate?.reason||'Loading saved Tuner project…');
    return true;
  }
  function t4ProjectActivatePermanent(forceRender=false){
    if(t4ProjectLiveConnected())return false;
    const next=t4ProjectPermanentCandidate();
    if(!next||next?.status!=='ready'){
      t4ProjectRequestPermanent('activate');
      t4ProjectShowPending(next);
      return false;
    }
    const same=!forceRender&&t4ProjectDetachedActive&&
      String(workspace?.source||'')===String(next?.source||'')&&
      String(workspace?.profileFingerprint||'')===String(next?.profileFingerprint||'')&&
      String(workspace?.tuneFingerprint||'')===String(next?.tuneFingerprint||'');
    if(same){t4ProjectApplyChrome();return true;}
    workspace=next;
    t4ProjectDetachedActive=true;
    t4ProjectSavedAt=Number(next?.capturedAtEpochMs||0);
    viewMode='settings';
    t4ProjectClearTransientEdits();
    t4ProjectRestoreExactDrafts();
    rebuildWorkspaceIndex();
    render();
    syncTunerChrome();
    t4ProjectApplyChrome();
    return true;
  }

  const t4ProjectBaseLoadWorkspace=loadWorkspace;
  loadWorkspace=function(){
    // Once the ECU is absent, never ask the live native workspace first while the persisted project
    // is loading. The native TuneSnapshot projection must take ownership deterministically.
    if(!t4ProjectLiveConnected()){
      t4ProjectRequestPermanent('load-workspace');
      if(t4ProjectActivatePermanent(false))return;
      if(t4ProjectShowPending())return;
      t4ProjectBaseLoadWorkspace();
      if(workspace?.status!=='ready')t4ProjectActivatePermanent(false);
      return;
    }
    t4ProjectBaseLoadWorkspace();
    if(workspace?.status==='ready'&&workspace?.savedProject!==true&&workspace?.definitionOnly!==true){
      t4ProjectDetachedActive=false;
      t4ProjectReleasePermanentForLive();
    }
  };

  const t4ProjectBaseRender=render;
  render=function(){
    t4ProjectBaseRender();
    if(t4ProjectLiveConnected()&&workspace?.status==='ready'&&workspace?.savedProject!==true&&workspace?.definitionOnly!==true){
      t4ProjectDetachedActive=false;
      t4ProjectReleasePermanentForLive();
    }
    if(t4ProjectDetachedActive)t4ProjectApplyChrome();
    else if(!t4ProjectLiveConnected())t4ProjectShowPending();
  };

  const t4ProjectBaseSync=syncTunerChrome;
  syncTunerChrome=function(){
    t4ProjectBaseSync();
    if(t4ProjectDetachedActive)t4ProjectApplyChrome();
    else if(!t4ProjectLiveConnected())t4ProjectShowPending();
  };

  window.addEventListener('epicdash:permanent-tuner-project-ready',()=>{
    t4ProjectRequestPending=false;
    clearTimeout(t4ProjectRequestTimer);t4ProjectRequestTimer=0;
    if(!t4ProjectLiveConnected())t4ProjectActivatePermanent(true);
    else t4ProjectReleasePermanentForLive();
  });
  document.addEventListener('visibilitychange',()=>{
    if(document.hidden)return;
    if(!t4ProjectLiveConnected())t4ProjectActivatePermanent(true);
    else if(t4ProjectDetachedActive)t4ProjectApplyChrome();
  });
  window.addEventListener('epicdash:page-activated',e=>{
    if(e?.detail?.pageId!=='page-tuning')return;
    if(!t4ProjectLiveConnected()){t4ProjectRequestPermanent('page-activated');t4ProjectActivatePermanent(false);}
    else if(workspace?.status==='ready')t4ProjectReleasePermanentForLive();
  });

  const t4ProjectBaseIniStateChanged=window.EpicDashUsbIniImportStateChanged;
  window.EpicDashUsbIniImportStateChanged=function(payload){
    try{t4ProjectBaseIniStateChanged?.(payload);}catch(_){}
    if(String(payload?.phase||'').toLowerCase()==='complete'&&!t4ProjectLiveConnected()){
      // The authoritative INI has changed. Reload once so Android hashes/recompiles the new source
      // and the UI structure exactly follows that firmware definition.
      setTimeout(()=>window.location.reload(),250);
    }
  };

  if(t4ProjectLiveConnected()){
    t4ProjectReleasePermanentForLive();
  }else{
    t4ProjectRequestPermanent('initial-offline');
    if(!t4ProjectActivatePermanent(false))t4ProjectShowPending();
  }
