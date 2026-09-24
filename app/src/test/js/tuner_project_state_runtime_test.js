'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_project_state.js','utf8');

class FakeNode {
  constructor() {
    this.disabled=false;this.textContent='';this.dataset={};
    this.classList={toggle(){},add(){},remove(){}};
  }
  querySelectorAll(){return [];}
  setAttribute(){}
}

function readyWorkspace({profile='profile-a',tune='tune-a',generation=7,sourceName='LIVE_TUNE_SNAPSHOT'}={}){
  return {
    status:'ready',capability:'LIVE_READ_WRITE',source:sourceName,generation,
    importedProfileName:'mainController.ini',ecuSignature:'rusEFI.test',profileFingerprint:profile,
    tuneFingerprint:tune,capturedAtEpochMs:123456,
    scalars:[{name:'rpmLimit',value:6500,unit:'rpm',digits:0,writeAllowed:true}],
    arrays:[
      {name:'rpmBins',dimensions:[2],elementCount:2,values:[1000,2000],unit:'rpm',digits:0,writeAllowed:true},
      {name:'loadBins',dimensions:[2],elementCount:2,values:[50,100],unit:'kPa',digits:0,writeAllowed:true},
      {name:'veTable',dimensions:[2,2],elementCount:4,values:[40,45,50,55],unit:'%',digits:1,writeAllowed:true},
      {name:'curveY',dimensions:[2],elementCount:2,values:[1,2],unit:'%',digits:1,writeAllowed:true}
    ],
    bitFields:[{name:'feature',value:1,valueLabel:'On',options:[{value:0,label:'Off'},{value:1,label:'On'}],writeAllowed:true}],
    menuItems:[{menu:'Fuel',group:'General',dialogId:'fuelDialog',title:'Fuel',conditions:[],conditionState:{status:'active',enabled:true,visible:true,supported:true}}],
    dialogs:[{id:'fuelDialog',title:'Fuel',layout:'',topicHelp:'',entries:[{kind:'field',label:'Limit',target:'rpmLimit',conditions:[],conditionState:{status:'active',enabled:true,visible:true,supported:true}}]}],
    tables:[{kind:'table',id:'veTableEditor',title:'VE Table',xBins:'rpmBins',yBins:'loadBins',targetArray:'veTable',xCount:2,yCount:2,writeAllowed:true}],
    curves:[{kind:'curve',id:'curveEditor',title:'Curve',xBins:'rpmBins',targetArray:'curveY',pointCount:2,writeAllowed:true}],
    iniCompatibility:{state:'green',profileFingerprint:profile}
  };
}

function savedBootstrap(){
  const value=readyWorkspace({generation:0,sourceName:'SAVED_TUNE_SNAPSHOT'});
  value.capability='SAVED_PROJECT';value.savedProject=true;value.definitionOnly=false;
  for(const key of ['scalars','arrays','bitFields','tables','curves'])for(const item of value[key])item.writeAllowed=false;
  return value;
}

function definitionBootstrap(){
  return {
    status:'ready',capability:'INI_STRUCTURE',source:'INI_DEFINITION',definitionOnly:true,generation:0,
    importedProfileName:'mainController.ini',ecuSignature:'rusEFI.test',profileSignature:'rusEFI.test',
    profileFingerprint:'profile-a',tuneFingerprint:'',capturedAtEpochMs:0,
    scalars:[{name:'rpmLimit',value:'unavailable',valueAvailable:false,writeAllowed:false}],
    arrays:[{name:'veTable',dimensions:[2,2],elementCount:4,values:[],valueAvailable:false,writeAllowed:false}],
    bitFields:[{name:'feature',value:'unavailable',valueAvailable:false,options:[],writeAllowed:false}],
    menuItems:[],dialogs:[],
    tables:[{kind:'table',id:'veTableEditor',title:'VE Table',targetArray:'veTable',xBins:'rpmBins',yBins:'loadBins',xCount:2,yCount:2,writeAllowed:false}],
    curves:[{kind:'curve',id:'curveEditor',title:'Curve',targetArray:'curveY',xBins:'rpmBins',pointCount:2,writeAllowed:false}],
    iniCompatibility:{state:'amber'}
  };
}

function loadingBootstrap(){return {status:'loading',capability:'READ_ONLY',reason:'Loading saved Tuner project…'};}

function runScenario({live=false,initialWorkspace={status:'not_ready'},bootstrap=savedBootstrap(),bridgeAvailable=true,bridgeStatus='queued'}={}){
  let connected=live,loadCalls=0,renderCalls=0,rebuildCalls=0,reloads=0,nativeRequests=0;
  let scalarDraftLoads=0,arrayDraftLoads=0,bitDraftLoads=0,changeCountCalls=0;
  const listeners={},docListeners={};
  const page=new FakeNode(),meta=new FakeNode(),topBurnButton=new FakeNode(),topBurnLabel=new FakeNode();
  const windowObj={
    __EPIC_TUNER_PERMANENT_WORKSPACE__:bootstrap,
    addEventListener(name,fn){listeners[name]=fn;},
    location:{reload(){reloads++;}}
  };
  if(bridgeAvailable){
    windowObj.EpicDashAndroid={
      requestPermanentTunerProject(){nativeRequests++;return JSON.stringify({status:bridgeStatus});}
    };
  }
  const context={
    console,JSON,Date,Number,String,Object,Array,Map,Math,
    setTimeout(fn){fn();return 1;},clearTimeout(){},
    window:windowObj,document:{hidden:false,addEventListener(name,fn){docListeners[name]=fn;}},
    workspace:initialWorkspace,page,meta,topBurnButton,topBurnLabel,viewMode:'tables',
    selectedArray:{},arrayDetail:{},selectedArrayCell:1,selectedSurface:{},
    draft:{stale:{}},arrayDraft:{stale:{}},bitDraft:{stale:{}},selected:{},queuedSemanticWrite:{},currentSettingsItem:{},
    t4ConnectionIsLive:()=>connected,
    loadWorkspace(){loadCalls++;context.workspace=readyWorkspace({profile:'profile-live',tune:'tune-live'});},
    loadDraft(){scalarDraftLoads++;context.draft={};},
    loadArrayDraft(){arrayDraftLoads++;context.arrayDraft={restored:{tuneFingerprint:context.workspace?.tuneFingerprint}};},
    loadBitDraft(){bitDraftLoads++;context.bitDraft={};},
    updateChangeCount(){changeCountCalls++;},
    render(){renderCalls++;},syncTunerChrome(){},rebuildWorkspaceIndex(){rebuildCalls++;}
  };
  windowObj.window=windowObj;
  vm.runInNewContext(source,context,{filename:'t4_tuning_workspace_project_state.js'});
  return{
    context,listeners,docListeners,
    setConnected(v){connected=v;},
    deliverPermanent(value=savedBootstrap()){
      windowObj.__EPIC_TUNER_PERMANENT_WORKSPACE__=value;
      listeners['epicdash:permanent-tuner-project-ready']?.();
    },
    get loadCalls(){return loadCalls;},get renderCalls(){return renderCalls;},get rebuildCalls(){return rebuildCalls;},get reloads(){return reloads;},
    get nativeRequests(){return nativeRequests;},
    get scalarDraftLoads(){return scalarDraftLoads;},get arrayDraftLoads(){return arrayDraftLoads;},get bitDraftLoads(){return bitDraftLoads;},get changeCountCalls(){return changeCountCalls;}
  };
}

const saved=runScenario({live:false,bootstrap:savedBootstrap()});
assert.strictEqual(saved.context.workspace.status,'ready');
assert.strictEqual(saved.context.workspace.savedProject,true,'permanent native snapshot must open automatically while disconnected');
assert.strictEqual(saved.context.workspace.source,'SAVED_TUNE_SNAPSHOT');
assert.strictEqual(saved.context.workspace.scalars[0].value,6500);
assert.deepStrictEqual(Array.from(saved.context.workspace.arrays.find(item=>item.name==='veTable').values),[40,45,50,55]);
assert.strictEqual(saved.context.workspace.scalars[0].writeAllowed,false,'saved scalar must retain no ECU write authority');
assert.strictEqual(saved.context.workspace.arrays[0].writeAllowed,false,'saved array must retain no ECU write authority');
assert.strictEqual(saved.context.workspace.arrays[0].offlineEditable,true,'saved array must permit local offline drafting');
assert.strictEqual(saved.context.workspace.tables[0].offlineEditable,true,'saved table must permit local offline drafting');
assert.strictEqual(saved.context.workspace.curves[0].offlineEditable,true,'saved curve must permit local offline drafting');
assert.strictEqual(saved.context.workspace.scalars[0].offlineEditable,false,'offline drafting must stay scoped away from scalars in this slice');
assert.strictEqual(saved.context.workspace.bitFields[0].offlineEditable,false,'offline drafting must stay scoped away from bit fields in this slice');
assert.ok(saved.scalarDraftLoads>0&&saved.arrayDraftLoads>0&&saved.bitDraftLoads>0,'exact-fingerprint drafts must reload when the permanent project activates');
assert.ok(saved.changeCountCalls>0,'restored drafts must refresh pending-change presentation');
assert.strictEqual(saved.context.viewMode,'settings','permanent project activation must open on Parameters');
assert.ok(saved.renderCalls>0&&saved.rebuildCalls>0,'permanent project must render and rebuild hierarchy');
saved.context.loadWorkspace();
assert.strictEqual(saved.loadCalls,0,'disconnected refresh must not ask the live native workspace to replace the permanent project');
assert.strictEqual(saved.nativeRequests,0,'already-ready saved project must not trigger another native projection');

saved.context.document.hidden=true;saved.docListeners.visibilitychange();
saved.context.document.hidden=false;saved.docListeners.visibilitychange();
assert.strictEqual(saved.context.workspace.savedProject,true,'minimize/restore must keep permanent project active');
assert.strictEqual(saved.context.workspace.scalars[0].value,6500);

const asyncCase=runScenario({live:false,bootstrap:loadingBootstrap()});
assert.strictEqual(asyncCase.loadCalls,0,'loading permanent project must not fall through to stale live workspace');
assert.ok(asyncCase.context.meta.textContent.includes('Loading'),'loading state must be visible');
assert.strictEqual(asyncCase.context.workspace.status,'not_ready','loading marker must not invent tune values');
assert.ok(asyncCase.nativeRequests>0,'offline loading state must request the native permanent project');
assert.ok(asyncCase.listeners['epicdash:permanent-tuner-project-ready'],'async permanent project ready listener missing');
asyncCase.deliverPermanent(savedBootstrap());
assert.strictEqual(asyncCase.context.workspace.savedProject,true,'async native project delivery must activate without page reload');
assert.strictEqual(asyncCase.context.workspace.scalars[0].value,6500);
assert.strictEqual(asyncCase.context.workspace.tables[0].offlineEditable,true);

const unavailableBridge=runScenario({live:false,bootstrap:loadingBootstrap(),bridgeAvailable:false});
assert.strictEqual(unavailableBridge.context.workspace.status,'not_ready','missing native bridge must not invent values');
assert.strictEqual(unavailableBridge.loadCalls,0,'missing bridge while saved project is loading must stay fail-closed');

const definition=runScenario({live:false,bootstrap:definitionBootstrap()});
assert.strictEqual(definition.context.workspace.definitionOnly,true,'INI without a saved TuneSnapshot must remain structure-only');
assert.strictEqual(definition.context.workspace.source,'INI_DEFINITION');
assert.strictEqual(definition.context.workspace.scalars[0].valueAvailable,false,'INI-only project must not invent ECU values');
assert.strictEqual(definition.context.workspace.tables[0].offlineEditable,false,'INI-only table structure must not become editable without a value baseline');
assert.strictEqual(definition.context.workspace.curves[0].offlineEditable,false,'INI-only curve structure must not become editable without a value baseline');

const live=runScenario({live:true,initialWorkspace:readyWorkspace({profile:'profile-live',tune:'tune-live'}),bootstrap:definitionBootstrap()});
assert.strictEqual(live.context.workspace.savedProject,undefined,'live startup must retain live workspace authority');
assert.strictEqual(live.nativeRequests,0,'live startup must not build the saved project');
live.setConnected(false);
live.context.loadWorkspace();
assert.ok(live.nativeRequests>0,'disconnect must request the persisted native TuneSnapshot projection');
assert.strictEqual(live.loadCalls,0,'disconnect must not fall back to stale live workspace while native saved project loads');
assert.strictEqual(live.context.workspace.savedProject,undefined,'disconnect must not deep-clone the live workspace into an offline project');
live.deliverPermanent(savedBootstrap());
assert.strictEqual(live.context.workspace.savedProject,true,'native saved project must take ownership after disconnect delivery');
assert.strictEqual(live.context.workspace.source,'SAVED_TUNE_SNAPSHOT');
assert.strictEqual(live.context.workspace.scalars[0].value,6500);
assert.strictEqual(live.context.workspace.tables[0].offlineEditable,true,'persisted TuneSnapshot must become offline-draft-capable after disconnect');

const importCase=runScenario({live:false,bootstrap:definitionBootstrap()});
importCase.context.window.EpicDashUsbIniImportStateChanged?.({phase:'complete'});
assert.strictEqual(importCase.reloads,1,'disconnected INI replacement must reload native bootstrap from the new exact INI');

for(const forbidden of [
  't4ProjectSessionFallback','function t4ProjectCaptureLive',
  'EpicDashTunerProjectStore','saveProject','loadProject','loadIniDefinition','values.json','t4ProjectValueState',
  'putArray(','getArray(','requestIdleCallback','localStorage','READ CACHED INI + TUNE','t4twReadCachedTune','offlineEditor'
]) assert.ok(!source.includes(forbidden),'project presentation reintroduced obsolete persistence/cache path: '+forbidden);
assert.ok(source.includes('TUNER_UI_PROJECT_STATE_V7_LAZY_OFFLINE'));
assert.ok(source.includes('__EPIC_TUNER_PERMANENT_WORKSPACE__'));
assert.ok(source.includes('t4ProjectRequestPermanent'));
assert.ok(source.includes('requestPermanentTunerProject'));
assert.ok(source.includes('t4ProjectReleasePermanentForLive'));
assert.ok(source.includes('t4ProjectActivatePermanent'));
assert.ok(source.includes('t4ProjectRestoreExactDrafts'));
assert.ok(source.includes("window.addEventListener('epicdash:permanent-tuner-project-ready'"));

console.log('Tuner lazy native-snapshot project-state plus offline-draft lifecycle test passed');
