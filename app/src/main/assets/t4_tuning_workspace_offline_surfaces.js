/* TUNER_UI_OFFLINE_SURFACES_V1
 * Offline Tables/Curves behavior is deliberately split from ECU write authority.
 *
 * - INI-only projects expose table/curve structure with unavailable values.
 * - A matching saved complete TuneSnapshot may stage table/curve drafts locally.
 * - No offline path calls native preview/write/Burn. Drafts are keyed by the exact saved tune
 *   fingerprint and must be revalidated by the normal live native path after reconnect.
 */
  function t4OfflineSavedSurfaceMode(){
    return workspace?.status==='ready'&&workspace?.savedProject===true&&workspace?.definitionOnly!==true&&
      String(workspace?.tuneFingerprint||'').length>0&&!t4ProjectLiveConnected();
  }

  function t4OfflineStructureMode(){
    return workspace?.status==='ready'&&workspace?.definitionOnly===true&&!t4ProjectLiveConnected();
  }

  function t4OfflineSurfaceCanDraft(){
    return t4OfflineSavedSurfaceMode()&&selectedSurface?.offlineEditable===true&&selectedArray?.offlineEditable===true&&
      Array.isArray(arrayDetail?.values)&&arrayDetail.values.every(value=>Number.isFinite(Number(value)));
  }

  function t4OfflineFiniteBound(value){
    if(value===null||value===undefined||value==='')return null;
    const numeric=Number(value);
    return Number.isFinite(numeric)?numeric:null;
  }

  function t4OfflineSameValue(left,right){
    const a=Number(left),b=Number(right);
    if(!Number.isFinite(a)||!Number.isFinite(b))return false;
    return Math.abs(a-b)<=Number.EPSILON*Math.max(1,Math.abs(a),Math.abs(b))*8;
  }

  function t4OfflineValidateRequested(index,requested){
    if(!t4OfflineSurfaceCanDraft())return {ok:false,reason:'A matching saved TuneSnapshot is required for offline table/curve editing.'};
    if(!Number.isInteger(index)||index<0||index>=arrayDetail.values.length)return {ok:false,reason:'Offline draft cell is outside the saved TuneSnapshot array.'};
    const current=Number(arrayDetail.values[index]);
    const value=Number(requested);
    if(!Number.isFinite(current)||!Number.isFinite(value))return {ok:false,reason:'Offline drafts require finite saved and requested values.'};
    const low=t4OfflineFiniteBound(selectedArray?.low??selectedArray?.minimum);
    const high=t4OfflineFiniteBound(selectedArray?.high??selectedArray?.maximum);
    if(low!==null&&value<low)return {ok:false,reason:'Requested value is below the INI minimum '+low+'.'};
    if(high!==null&&value>high)return {ok:false,reason:'Requested value is above the INI maximum '+high+'.'};
    return {ok:true,current,value};
  }

  function t4OfflineApplyDraft(index,requested){
    const checked=t4OfflineValidateRequested(index,requested);
    if(!checked.ok)return checked;
    const id=arrayDraftId(selectedArray.name,index);
    if(t4OfflineSameValue(checked.current,checked.value)){
      delete arrayDraft[id];
      return {ok:true,noOp:true,current:checked.current,value:checked.value};
    }
    arrayDraft[id]={
      id:id,kind:'arrayCell',arrayName:selectedArray.name,cellIndex:index,
      currentValue:checked.current,requestedValue:checked.value,effectiveValue:checked.value,
      unit:String(selectedArray.unit||''),digits:Number(selectedArray.digits||0),
      changedBytes:0,generation:0,
      profileFingerprint:String(workspace.profileFingerprint||''),tuneFingerprint:String(workspace.tuneFingerprint||''),
      savedAt:Date.now(),offlineDraft:true,validationState:'pending_live_validation'
    };
    return {ok:true,noOp:false,current:checked.current,value:checked.value};
  }

  function t4OfflineCommitDraftUi(message){
    saveArrayDraft();
    updateChangeCount();
    renderArrayGrid();
    render();
    if(arrayWrite){arrayWrite.hidden=true;arrayWrite.disabled=true;}
    if(arrayStatus){arrayStatus.className='t4tw-status good';arrayStatus.textContent=message;}
  }

  const t4OfflineBaseStageCell=stageArrayCellEdit;
  stageArrayCellEdit=function(){
    if(t4OfflineStructureMode()){
      if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='INI structure has no trusted value baseline. Load a matching complete TuneSnapshot before editing.';}
      return;
    }
    if(!t4OfflineSavedSurfaceMode())return t4OfflineBaseStageCell();
    if(!t4OfflineSurfaceCanDraft()){
      if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='Offline edit requires a matching saved complete TuneSnapshot.';}
      return;
    }
    if(selectedArrayCell<0)return;
    const requested=Number(arrayInput?.value);
    const result=t4OfflineApplyDraft(selectedArrayCell,requested);
    if(!result.ok){if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent=result.reason;}return;}
    t4OfflineCommitDraftUi(result.noOp
      ?'Offline draft removed: requested value matches the saved TuneSnapshot baseline.'
      :'OFFLINE DRAFT • saved locally for this exact TuneSnapshot • reconnect to validate/write ECU RAM.');
  };

  const t4OfflineBaseStageSelection=stageArraySelectionEdit;
  stageArraySelectionEdit=function(){
    if(t4OfflineStructureMode()){
      if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='INI structure has no trusted value baseline. Load a matching complete TuneSnapshot before editing.';}
      return;
    }
    if(!t4OfflineSavedSurfaceMode())return t4OfflineBaseStageSelection();
    if(!t4OfflineSurfaceCanDraft()){
      if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='Offline edit requires a matching saved complete TuneSnapshot.';}
      return;
    }
    const indices=selectedArrayIndices();
    if(!indices.length){if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='Select one or more cells first.';}return;}
    if(indices.length>MAX_TABLE_SELECTION){if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='Selection exceeds the 2048-change batch limit.';}return;}
    const operand=Number(regionValue?.value);
    const mode=String(regionOperation?.value||'set');
    if(!Number.isFinite(operand)){if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='Enter a finite selection edit value.';}return;}
    const planned=[];
    for(const index of indices){
      const requested=selectionOperationValue(mode,arrayBaseValue(index),operand);
      const checked=t4OfflineValidateRequested(index,requested);
      if(!checked.ok){if(arrayStatus){arrayStatus.className='t4tw-status bad';arrayStatus.textContent='Cell #'+index+': '+checked.reason;}return;}
      planned.push({index,requested});
    }
    let staged=0,cleared=0;
    for(const entry of planned){
      const result=t4OfflineApplyDraft(entry.index,entry.requested);
      if(result.noOp)cleared++;else staged++;
    }
    t4OfflineCommitDraftUi('OFFLINE DRAFT • '+staged+' changed cell(s) staged'+(cleared?' • '+cleared+' baseline/no-op cell(s) cleared':'')+' • live validation required before ECU RAM write.');
  };

  const t4OfflineBaseApplyTableSelection=applyInlineTableSelection;
  applyInlineTableSelection=function(mode,operand){
    if(t4OfflineStructureMode()){
      const status=document.getElementById('t4twInlineTableStatus');
      if(status)status.textContent='INI table structure has no value baseline and cannot be edited.';
      return;
    }
    if(!t4OfflineSavedSurfaceMode())return t4OfflineBaseApplyTableSelection(mode,operand);
    if(!ensureInlineTableSelection()){
      const status=document.getElementById('t4twInlineTableStatus');
      if(status)status.textContent='Select one or more cells first.';
      return;
    }
    if(!Number.isFinite(Number(operand)))return;
    regionOperation.value=mode;
    regionValue.value=String(operand);
    stageArraySelectionEdit();
  };

  const t4OfflineBaseApplyCurveSelection=applyInlineCurveSelection;
  applyInlineCurveSelection=function(mode,operand){
    if(t4OfflineStructureMode()){
      const status=document.getElementById('t4twInlineCurveStatus');
      if(status)status.textContent='INI curve structure has no value baseline and cannot be edited.';
      return;
    }
    if(!t4OfflineSavedSurfaceMode())return t4OfflineBaseApplyCurveSelection(mode,operand);
    if(!ensureInlineTableSelection()){
      const status=document.getElementById('t4twInlineCurveStatus');
      if(status)status.textContent='Select one or more active-curve points first.';
      return;
    }
    if(!Number.isFinite(Number(operand)))return;
    regionOperation.value=mode;
    regionValue.value=String(operand);
    stageArraySelectionEdit();
  };

  const t4OfflineBaseQueueWrites=queueSemanticWrites;
  queueSemanticWrites=function(changes,statusNode){
    if(!t4ProjectLiveConnected()){
      if(statusNode)statusNode.textContent='ECU is offline. Drafts cannot be written until a matching live TuneSnapshot is validated.';
      return false;
    }
    return t4OfflineBaseQueueWrites(changes,statusNode);
  };

  const t4OfflineBaseHierarchySelect=hierarchySelect;
  hierarchySelect=function(label,value,options,onChange){
    const level=String(label||'').toLowerCase();
    const navigableOffline=!t4ProjectLiveConnected()&&(level==='table'||level==='curve');
    const nextOptions=navigableOffline?(options||[]).map(option=>Object.assign({},option,{disabled:false})):options;
    return t4OfflineBaseHierarchySelect(label,value,nextOptions,onChange);
  };

  function t4OfflineEnableDraftControls(rootSelector,allowedLabels){
    if(!t4OfflineSavedSurfaceMode())return;
    const root=document.querySelector(rootSelector);
    if(!root)return;
    root.querySelectorAll('button').forEach(button=>{
      const label=String(button.textContent||'').trim();
      if(allowedLabels.has(label))button.disabled=false;
    });
  }

  const t4OfflineBaseRefreshTableInspector=refreshInlineTableInspector;
  refreshInlineTableInspector=function(){
    const result=t4OfflineBaseRefreshTableInspector();
    const input=document.getElementById('t4twInlineTableValue');
    const status=document.getElementById('t4twInlineTableStatus');
    if(t4OfflineSavedSurfaceMode()&&input)input.disabled=selectedArrayCell<0;
    if(t4OfflineStructureMode()){
      if(input){input.value='';input.disabled=true;}
      if(status)status.textContent='INI table structure loaded. Cell values require a matching complete TuneSnapshot.';
    }else if(t4OfflineSavedSurfaceMode()&&status&&selectedArrayCell>=0){
      const pending=arrayDraftFor(selectedArray?.name,selectedArrayCell);
      status.textContent=(pending?'OFFLINE DRAFT • ':'')+'Saved TuneSnapshot baseline • '+(selectedArrayCells.size||1)+' selected • live validation required before ECU write.';
    }
    return result;
  };

  const t4OfflineBaseRefreshCurveInspector=refreshInlineCurveInspector;
  refreshInlineCurveInspector=function(){
    const result=t4OfflineBaseRefreshCurveInspector();
    const input=document.getElementById('t4twInlineCurveValue');
    const status=document.getElementById('t4twInlineCurveStatus');
    if(t4OfflineSavedSurfaceMode()&&input)input.disabled=selectedArrayCell<0;
    if(t4OfflineStructureMode()){
      if(input){input.value='';input.disabled=true;}
      if(status)status.textContent='INI curve structure loaded. Point values require a matching complete TuneSnapshot.';
    }else if(t4OfflineSavedSurfaceMode()&&status&&selectedArrayCell>=0){
      const pending=arrayDraftFor(selectedArray?.name,selectedArrayCell);
      status.textContent=(pending?'OFFLINE DRAFT • ':'')+'Saved TuneSnapshot baseline • live validation required before ECU write.';
    }
    return result;
  };

  const t4OfflineBaseRefreshCellInspector=refreshArrayCellInspector;
  refreshArrayCellInspector=function(index,focusInput=true){
    const result=t4OfflineBaseRefreshCellInspector(index,focusInput);
    if(t4OfflineSavedSurfaceMode()){
      if(arrayInput)arrayInput.disabled=false;
      if(arrayApplyEdit)arrayApplyEdit.disabled=false;
      if(arrayWrite){arrayWrite.hidden=true;arrayWrite.disabled=true;}
      if(arrayStatus&&index>=0)arrayStatus.textContent='Saved TuneSnapshot cell selected • offline draft editing enabled • live validation required before ECU write.';
    }
    return result;
  };

  const t4OfflineBaseRenderTables=renderTablesMode;
  renderTablesMode=function(q){
    const result=t4OfflineBaseRenderTables(q);
    if(t4OfflineSavedSurfaceMode()){
      t4OfflineEnableDraftControls('.t4tw-table-editbar',new Set(['-10','-1','+1','+10','Set']));
      refreshInlineTableInspector();
    }else if(t4OfflineStructureMode()){
      const status=document.getElementById('t4twInlineTableStatus');
      if(status)status.textContent='INI table structure only • values shown as — until a matching complete TuneSnapshot exists.';
    }
    return result;
  };

  const t4OfflineBaseRenderCurves=renderCurvesMode;
  renderCurvesMode=function(q){
    const result=t4OfflineBaseRenderCurves(q);
    if(t4OfflineSavedSurfaceMode()){
      t4OfflineEnableDraftControls('.t4tw-curve-editbar',new Set(['-1','-0.1','+0.1','+1','Set']));
      refreshInlineCurveInspector();
    }else if(t4OfflineStructureMode()){
      const plot=document.getElementById('t4twCurvePlot');
      if(plot){
        const note=document.createElement('div');
        note.className='t4tw-empty';
        note.textContent='INI curve structure loaded. X/Y values require a matching complete TuneSnapshot.';
        plot.appendChild(note);
      }
      const status=document.getElementById('t4twInlineCurveStatus');
      if(status)status.textContent='INI curve structure only • values unavailable.';
    }
    return result;
  };

  const t4OfflineBaseWriteTable=writeCurrentTableDrafts;
  writeCurrentTableDrafts=function(){
    if(!t4ProjectLiveConnected()){
      const status=document.getElementById('t4twInlineTableStatus');
      if(status)status.textContent=t4OfflineSavedSurfaceMode()
        ?'ECU offline • table drafts are saved locally. Reconnect the matching tune before writing.'
        :'ECU offline • no table values can be written.';
      return;
    }
    return t4OfflineBaseWriteTable();
  };

  const t4OfflineBaseWriteCurve=writeCurrentCurveDrafts;
  writeCurrentCurveDrafts=function(){
    if(!t4ProjectLiveConnected()){
      const status=document.getElementById('t4twInlineCurveStatus');
      if(status)status.textContent=t4OfflineSavedSurfaceMode()
        ?'ECU offline • curve drafts are saved locally. Reconnect the matching tune before writing.'
        :'ECU offline • no curve values can be written.';
      return;
    }
    return t4OfflineBaseWriteCurve();
  };
