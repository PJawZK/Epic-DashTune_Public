'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_offline_surfaces.js','utf8');
const surface = fs.readFileSync('app/src/main/java/com/buttonbox/ble/T4TuningWorkspaceSurface.kt','utf8');

let connected=false;
let baseStageCellCalls=0;
let baseStageSelectionCalls=0;
let baseQueueCalls=0;
let saveCalls=0;
let renderCalls=0;
let lastHierarchyOptions=null;
const nodes=new Map();
const node=id=>{
  if(!nodes.has(id))nodes.set(id,{id,textContent:'',value:'',disabled:false,hidden:false,className:'',classList:{toggle(){},add(){},remove(){}},querySelectorAll(){return[];},appendChild(){}});
  return nodes.get(id);
};

const context={
  console,Number,String,Array,Object,JSON,Date,Math,Set,Map,
  workspace:{status:'ready',savedProject:true,definitionOnly:false,generation:0,profileFingerprint:'profile-a',tuneFingerprint:'tune-a'},
  selectedSurface:{kind:'table',id:'ve',offlineEditable:true,writeAllowed:false},
  selectedArray:{name:'veTable',offlineEditable:true,writeAllowed:false,low:0,high:255,unit:'%',digits:1},
  arrayDetail:{status:'ready',values:[40,45],elementCount:2},
  selectedArrayCell:1,
  selectedArrayCells:new Set([1]),
  arrayDraft:{},
  arrayInput:{value:'47.5',disabled:false},
  arrayApplyEdit:{disabled:false},arrayWrite:{hidden:true,disabled:true},arrayStatus:{className:'',textContent:''},
  regionOperation:{value:'set'},regionValue:{value:'5'},
  MAX_TABLE_SELECTION:2048,
  tuningWriteStatus:{},
  t4ProjectLiveConnected:()=>connected,
  arrayDraftId:(name,index)=>String(name)+':'+String(index),
  arrayDraftFor(name,index){const entry=context.arrayDraft[String(name)+':'+String(index)];return entry&&entry.tuneFingerprint===context.workspace.tuneFingerprint?entry:null;},
  saveArrayDraft(){saveCalls++;},updateChangeCount(){},renderArrayGrid(){},render(){renderCalls++;},
  selectedArrayIndices(){return Array.from(context.selectedArrayCells);},
  arrayBaseValue(index){const d=context.arrayDraftFor(context.selectedArray.name,index);return d?Number(d.effectiveValue):Number(context.arrayDetail.values[index]);},
  selectionOperationValue(mode,current,operand){
    const c=Number(current),o=Number(operand);
    if(mode==='add')return c+o;if(mode==='subtract')return c-o;if(mode==='percent')return c*(1+o/100);return o;
  },
  ensureInlineTableSelection(){return context.selectedArrayCells.size>0;},
  stageArrayCellEdit(){baseStageCellCalls++;},stageArraySelectionEdit(){baseStageSelectionCalls++;},
  applyInlineTableSelection(){},applyInlineCurveSelection(){},
  queueSemanticWrites(){baseQueueCalls++;return true;},
  hierarchySelect(label,value,options){lastHierarchyOptions=options;return {label,value};},
  refreshInlineTableInspector(){},refreshInlineCurveInspector(){},refreshArrayCellInspector(){},
  renderTablesMode(){},renderCurvesMode(){},writeCurrentTableDrafts(){},writeCurrentCurveDrafts(){},
  document:{
    getElementById(id){return node(id);},
    querySelector(){return null;},
    createElement(){return node('created-'+nodes.size);}
  }
};

vm.runInNewContext(source,context,{filename:'t4_tuning_workspace_offline_surfaces.js'});

context.stageArrayCellEdit();
assert.strictEqual(baseStageCellCalls,0,'saved offline edit must not invoke live/native preview staging');
assert.strictEqual(saveCalls,1,'saved offline edit must persist its exact-fingerprint draft');
assert.strictEqual(renderCalls,1,'saved offline edit must refresh presentation');
const draft=context.arrayDraft['veTable:1'];
assert.ok(draft,'saved offline edit did not create a table/curve draft');
assert.strictEqual(draft.offlineDraft,true);
assert.strictEqual(draft.validationState,'pending_live_validation');
assert.strictEqual(draft.currentValue,45);
assert.strictEqual(draft.requestedValue,47.5);
assert.strictEqual(draft.effectiveValue,47.5);
assert.strictEqual(draft.generation,0);
assert.strictEqual(draft.profileFingerprint,'profile-a');
assert.strictEqual(draft.tuneFingerprint,'tune-a');
assert.match(context.arrayStatus.textContent,/OFFLINE DRAFT/);

const writeStatus={textContent:''};
assert.strictEqual(context.queueSemanticWrites([{kind:'arrayCell'}],writeStatus),false,'detached project must reject ECU write queue');
assert.strictEqual(baseQueueCalls,0,'detached project reached the live ECU write queue');
assert.match(writeStatus.textContent,/ECU is offline/);

context.arrayInput.value='45';
context.stageArrayCellEdit();
assert.ok(!context.arrayDraft['veTable:1'],'returning to saved baseline must remove the offline draft');

context.hierarchySelect('Table','ve',[{value:'ve',disabled:true}],()=>{});
assert.strictEqual(lastHierarchyOptions[0].disabled,false,'offline table selector must remain navigable when ECU write is disabled');
context.hierarchySelect('Curve','curve',[{value:'curve',disabled:true}],()=>{});
assert.strictEqual(lastHierarchyOptions[0].disabled,false,'offline curve selector must remain navigable when ECU write is disabled');

context.workspace={status:'ready',savedProject:false,definitionOnly:true,generation:0,profileFingerprint:'profile-a',tuneFingerprint:''};
context.selectedSurface={kind:'table',offlineEditable:false,writeAllowed:false};
context.selectedArray={name:'veTable',offlineEditable:false,writeAllowed:false};
context.arrayDetail={status:'ready',values:[Number.NaN,Number.NaN],elementCount:2};
context.arrayInput.value='50';
context.stageArrayCellEdit();
assert.strictEqual(baseStageCellCalls,0,'INI-only edit must fail before live/native preview staging');
assert.match(context.arrayStatus.textContent,/no trusted value baseline/i);
assert.strictEqual(context.queueSemanticWrites([],writeStatus),false,'INI-only detached state must reject ECU write queue');
assert.strictEqual(baseQueueCalls,0);

connected=true;
context.workspace={status:'ready',savedProject:false,definitionOnly:false,generation:4,profileFingerprint:'profile-a',tuneFingerprint:'live-a'};
context.stageArrayCellEdit();
assert.strictEqual(baseStageCellCalls,1,'live edit must delegate to the normal native validation path');
context.queueSemanticWrites([{kind:'arrayCell'}],writeStatus);
assert.strictEqual(baseQueueCalls,1,'live write must delegate to the authoritative native queue');

assert.ok(source.includes('TUNER_UI_OFFLINE_SURFACES_V1'));
assert.ok(source.includes("validationState:'pending_live_validation'"));
assert.ok(source.includes('if(!t4ProjectLiveConnected())'));
assert.ok(!source.includes('previewTuningArrayCellJson'),'offline layer must not acquire native semantic-preview authority');
assert.ok(!source.includes('burnTuningChangesJson'),'offline layer must not acquire Burn authority');
assert.ok(surface.includes('t4_tuning_workspace_offline_surfaces.js'),'offline surfaces layer is not installed');
assert.ok(surface.indexOf('PROJECT_STATE_ASSET') < surface.indexOf('OFFLINE_SURFACES_ASSET'),'offline surfaces must run after project-state ownership');
assert.ok(surface.indexOf('OFFLINE_SURFACES_ASSET') < surface.indexOf('TELEMETRY_ASSET'),'offline surfaces must install before later presentation controls');

console.log('Tuner offline Tables/Curves structure + exact-snapshot draft runtime test passed');
