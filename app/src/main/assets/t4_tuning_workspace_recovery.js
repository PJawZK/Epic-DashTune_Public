/* TUNER_UI_RECOVERY_V3
 * User-authorized reinstall recovery only. This layer never reads/writes ECU data and never becomes
 * a second workspace/cache authority. When no Tuner project exists it renders a bounded startup
 * state, then opens the native SAF recovery picker on explicit user action.
 */
  const t4RecoveryStyle=document.createElement('style');
  t4RecoveryStyle.textContent=`
    .t4tw-recovery-startup{display:flex;flex-direction:column;align-items:center;gap:9px;min-height:0;padding:10px 0 4px}
    .t4tw-recovery-modes{width:min(620px,100%);flex:0 0 auto}
    .t4tw-recovery-modes .t4tw-content-mode:disabled{opacity:.48;cursor:not-allowed;background:#07121a;color:#78909b}
    .t4tw-recovery-panel{width:min(760px,100%);box-sizing:border-box;display:flex;flex-direction:column;align-items:center;gap:7px;margin:0;padding:12px;border:1px dashed #24485b;border-radius:10px;background:#07131b}
    .t4tw-recovery-startup[hidden],.t4tw-recovery-panel[hidden]{display:none!important}
    .t4tw-recovery-button{min-height:36px;border:1px solid #2d6983;border-radius:8px;background:#0a2634;color:#cdefff;padding:8px 13px;font-size:10px;font-weight:900;letter-spacing:.04em}
    .t4tw-recovery-copy{max-width:680px;text-align:center;color:#839da9;font-size:9px;line-height:1.35}
  `;
  document.head.appendChild(t4RecoveryStyle);

  const t4RecoveryStartup=document.createElement('div');
  t4RecoveryStartup.className='t4tw-recovery-startup';
  t4RecoveryStartup.hidden=true;

  const t4RecoveryModes=document.createElement('div');
  t4RecoveryModes.className='t4tw-content-modes t4tw-recovery-modes';
  for(const label of ['Parameters','Tables','Curves']){
    const button=document.createElement('button');
    button.className='t4tw-content-mode';
    button.type='button';
    button.disabled=true;
    button.textContent=label;
    t4RecoveryModes.appendChild(button);
  }

  const t4RecoveryPanel=document.createElement('div');
  t4RecoveryPanel.className='t4tw-recovery-panel';
  t4RecoveryPanel.innerHTML='<button id="t4twRestoreProject" class="t4tw-recovery-button" type="button">RESTORE SAVED TUNER PROJECT</button><div class="t4tw-recovery-copy">After a true uninstall/reinstall, select <b>EpicDash-JZ-Tuner-Recovery.json</b> to restore the exact INI and the last complete native TuneSnapshot. ECU writes and Burn remain unavailable until a live ECU connection exists.</div>';
  t4RecoveryStartup.append(t4RecoveryModes,t4RecoveryPanel);
  const t4RecoveryButton=t4RecoveryPanel.querySelector('#t4twRestoreProject');

  function t4RecoveryBridge(){return window.EpicDashTunerRecovery||null;}
  function t4RecoveryShouldShow(){
    const live=typeof t4ProjectLiveConnected==='function'?t4ProjectLiveConnected():false;
    return !live&&workspace?.status!=='ready';
  }
  function t4RecoveryHost(){return typeof list!=='undefined'&&list?list:(page.querySelector('.t4tw-list')||page.querySelector('.t4tw-shell')||page);}
  function t4MountRecoveryStartup(){
    const host=t4RecoveryHost();
    if(!host||t4RecoveryStartup.isConnected)return;
    host.textContent='';
    host.appendChild(t4RecoveryStartup);
  }
  function t4RefreshRecoveryPanel(){
    const show=t4RecoveryShouldShow();
    if(show)t4MountRecoveryStartup();
    t4RecoveryStartup.hidden=!show;
  }

  t4RecoveryButton.onclick=()=>{
    let launched=false;
    try{launched=t4RecoveryBridge()?.openRecoveryBundle?.()===true;}catch(_){launched=false;}
    meta.textContent=launched
      ?'Select EpicDash-JZ-Tuner-Recovery.json to restore the saved Tuner project.'
      :'Recovery picker is unavailable in this app session.';
  };

  const t4RecoveryBaseRender=render;
  render=function(){t4RecoveryBaseRender();t4RefreshRecoveryPanel();};
  const t4RecoveryBaseSync=syncTunerChrome;
  syncTunerChrome=function(){t4RecoveryBaseSync();t4RefreshRecoveryPanel();};
  window.addEventListener('epicdash:page-activated',e=>{
    if(e?.detail?.pageId==='page-tuning')setTimeout(t4RefreshRecoveryPanel,40);
  });
  setTimeout(t4RefreshRecoveryPanel,0);

  /* TUNER_UI_1204_MEASURED_ROTATION_AUTHORITY
   * Rotation profile selection follows the WebView's measured CSS viewport only. Orientation APIs
   * are wake-up signals, never geometry predictors. The light-weight CSS profile is updated on the
   * next animation frame while table/curve geometry waits until the measured viewport has remained
   * unchanged for two consecutive frames. This keeps transitional Android/WebView sizes from doing
   * repeated expensive fits without holding the shell in a predicted old/new orientation.
   */
  let t4Ui1204ViewportFrame=0;
  let t4Ui1204StableFrame=0;
  let t4Ui1204GeometryFrame=0;
  let t4Ui1204LastWidth=-1;
  let t4Ui1204LastHeight=-1;
  let t4Ui1204StableFrames=0;

  function t4Ui1204MeasuredViewport(){
    const root=document.documentElement;
    return {
      width:Math.max(0,Math.round(Number(window.innerWidth)||Number(root?.clientWidth)||0)),
      height:Math.max(0,Math.round(Number(window.innerHeight)||Number(root?.clientHeight)||0))
    };
  }

  function t4Ui1204ApplyMeasuredProfile(){
    const measured=t4Ui1204MeasuredViewport();
    const next=tunerProfileFor(measured.width,measured.height);
    const changed=next!==tunerViewportProfile;
    if(changed){
      tunerViewportProfile=next;
      document.body.dataset.t4twViewport=next;
      page.dataset.t4twViewport=next;
    }
    return {profile:next,width:measured.width,height:measured.height,changed};
  }

  function t4Ui1204QueueStableGeometry(){
    if(t4Ui1204GeometryFrame)cancelAnimationFrame(t4Ui1204GeometryFrame);
    t4Ui1204GeometryFrame=requestAnimationFrame(()=>{
      t4Ui1204GeometryFrame=0;
      if(viewMode==='tables'){
        if(tablePresentationMode==='3d'&&typeof renderInlineTable3d==='function')renderInlineTable3d();
        else if(typeof fitInlineTable==='function')fitInlineTable();
      }else if(viewMode==='curves'&&typeof renderCurveGraph==='function')renderCurveGraph();
      if(typeof relayoutActivePlots==='function')relayoutActivePlots();
      if(typeof repairDisplacedCurveSettings==='function')repairDisplacedCurveSettings();
    });
  }

  function t4Ui1204MeasureUntilStable(){
    t4Ui1204StableFrame=0;
    const measured=t4Ui1204ApplyMeasuredProfile();
    if(measured.width===t4Ui1204LastWidth&&measured.height===t4Ui1204LastHeight){
      t4Ui1204StableFrames+=1;
    }else{
      t4Ui1204LastWidth=measured.width;
      t4Ui1204LastHeight=measured.height;
      t4Ui1204StableFrames=0;
    }
    if(t4Ui1204StableFrames>=2){
      t4Ui1204QueueStableGeometry();
      return;
    }
    t4Ui1204StableFrame=requestAnimationFrame(t4Ui1204MeasureUntilStable);
  }

  function t4Ui1204ScheduleMeasuredViewport(){
    if(t4Ui1204ViewportFrame)return;
    t4Ui1204ViewportFrame=requestAnimationFrame(()=>{
      t4Ui1204ViewportFrame=0;
      const measured=t4Ui1204ApplyMeasuredProfile();
      t4Ui1204LastWidth=measured.width;
      t4Ui1204LastHeight=measured.height;
      t4Ui1204StableFrames=0;
      if(t4Ui1204StableFrame)cancelAnimationFrame(t4Ui1204StableFrame);
      t4Ui1204StableFrame=requestAnimationFrame(t4Ui1204MeasureUntilStable);
    });
  }

  function t4Ui1204InstallMeasuredRotationAuthority(){
    // 1203 is installed from an earlier queued microtask. Remove its predictive event owners first.
    try{window.removeEventListener('resize',t4Ui1203ScheduleViewportNow);}catch(_){}
    try{window.visualViewport?.removeEventListener('resize',t4Ui1203ScheduleViewportNow);}catch(_){}
    try{window.removeEventListener('orientationchange',t4Ui1203ScheduleViewportNow);}catch(_){}
    try{if(t4Ui1203ViewportFrame)cancelAnimationFrame(t4Ui1203ViewportFrame);}catch(_){}

    applyTunerViewportProfile=function(){return t4Ui1204ApplyMeasuredProfile().profile;};
    scheduleTunerViewportProfile=t4Ui1204ScheduleMeasuredViewport;

    window.addEventListener('resize',t4Ui1204ScheduleMeasuredViewport,{passive:true});
    window.visualViewport?.addEventListener('resize',t4Ui1204ScheduleMeasuredViewport,{passive:true});
    // Orientation events only request a measurement; their reported direction is deliberately unused.
    window.addEventListener('orientationchange',t4Ui1204ScheduleMeasuredViewport,{passive:true});
    t4Ui1204ScheduleMeasuredViewport();
  }

  // FIFO queueing makes this run after the 1203 installer that was queued by connection_refresh.
  queueMicrotask(t4Ui1204InstallMeasuredRotationAuthority);
