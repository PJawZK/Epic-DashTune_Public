'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_connection_refresh.js','utf8');
const surface = fs.readFileSync('app/src/main/java/com/buttonbox/ble/T4TuningWorkspaceSurface.kt','utf8');
const reader = fs.readFileSync('app/src/main/java/com/buttonbox/ble/AuthoritativeTuneSnapshotReader.kt','utf8');

assert.ok(source.includes('TUNER_UI_CONNECTION_REFRESH_V6'));
assert.ok(source.includes("t4TopReadButton.id='t4twTopRead'"));
assert.ok(source.includes('t4ConnectionWorkspaceIsLive'));
assert.ok(source.includes('t4ConnectionAdoptNativeWorkspace'));
assert.ok(source.includes('t4ConnectionReconcileLive'));
assert.ok(source.includes('t4ConnectionBaseWriteStateChanged'));
assert.ok(source.includes('readTuningFromEcu();'),'fallback and manual actions must use the existing normal Read ECU path');
assert.ok(source.includes("phase==='read_complete'"),'workspace must reconcile complete native reads');
assert.ok(source.includes('attempt>=300'),'slow native reads must not use the former 8 second abandonment window');
assert.ok(!source.includes('attempt>=80'),'former 8 second auto-read abandonment returned');
assert.ok(source.includes("label.textContent=live?'ECU Connected':'ECU Offline'"),'ECU chrome must reflect transport truth rather than saved workspace readiness');
assert.ok(source.includes('t4RefreshWorkspaceAfterDisconnect'));
assert.ok(!source.includes("else if(live&&page.classList.contains('active')&&!t4ConnectionWorkspaceIsLive())"),'steady connected chrome must not reload the full workspace');
assert.ok(reader.includes('TunerPermanentProjectStore.persistVerifiedSnapshot(snapshot)'),'normal native Read ECU path must commit permanent snapshot');
for(const forbidden of ['offlineEditor','UsbEcuManager(','bulkTransfer(','controlTransfer(','saveTuningToEcu(']){
  assert.ok(!source.includes(forbidden),'connection refresh retained forbidden/obsolete path: '+forbidden);
}
assert.ok(surface.includes('t4_tuning_workspace_connection_refresh.js'));
assert.ok(surface.indexOf('SNAPSHOT_ARRAY_VALUES_ASSET') < surface.indexOf('CONNECTION_REFRESH_ASSET'));
assert.ok(surface.indexOf('CONNECTION_REFRESH_ASSET') < surface.indexOf('PROJECT_STATE_ASSET'));

class FakeClassList {
  constructor(){this.values=new Set();}
  toggle(name,on){if(on)this.values.add(name);else this.values.delete(name);}
  contains(name){return this.values.has(name);}
}
class FakeNode {
  constructor(tag='div'){
    this.tagName=tag.toUpperCase();this.disabled=false;this.innerHTML='';this.id='';this.className='';this.title='';this.type='';
    this.parentNode=null;this.insertedNode=null;this.attributes={};this.textContent='';this.classList=new FakeClassList();this.bold=null;
  }
  setAttribute(name,value){this.attributes[name]=String(value);}
  insertAdjacentElement(_where,node){this.insertedNode=node;node.parentNode=this.parentNode||this;return node;}
  appendChild(){}
  querySelector(selector){return selector==='b'?this.bold:null;}
}

let connected=false,pageActive=true,loadCalls=0,readCalls=0,nativeWorkspaceReady=true;
let nativeStatus={status:'idle',operationRunning:false,generation:7,currentTuneFingerprint:'live-tune'};
const windowListeners={};
const documentListeners={};
const chainListener=(registry,name,fn)=>{
  const previous=registry[name];
  registry[name]=previous?(event=>{previous(event);fn(event);}):fn;
};
const appStatus=new FakeNode('div');
const burn=new FakeNode('button');
burn.parentNode=appStatus;
burn.insertAdjacentElement=function(_where,node){appStatus.insertedNode=node;node.parentNode=appStatus;return node;};
const ecuState=new FakeNode('div');ecuState.bold=new FakeNode('b');
const ecuSub=new FakeNode('small');
const page={classList:{contains:name=>name==='active'&&pageActive},querySelector(){return null;}};
const windowObj={
  EpicDashTunerTelemetrySnapshot:()=>({connected}),
  EpicDashAndroid:{getTuningWriteStatusJson:()=>JSON.stringify(nativeStatus)},
  EpicDashTuningWriteStateChanged:()=>{},
  addEventListener(name,fn){chainListener(windowListeners,name,fn);}
};
const documentObj={
  hidden:false,
  createElement:tag=>new FakeNode(tag),
  head:new FakeNode('head'),
  getElementById(){return null;},
  querySelector(){return null;},
  addEventListener(name,fn){chainListener(documentListeners,name,fn);}
};
const context={
  console,document:documentObj,window:windowObj,
  page,topBurnButton:burn,topEcuState:ecuState,topEcuSub:ecuSub,
  tuningWriteStatus:{operationRunning:false},workspace:{status:'ready',savedProject:true,definitionOnly:true,importedProfileName:'mainController.ini'},meta:new FakeNode('div'),
  syncTunerChrome(){},
  loadWorkspace(){
    loadCalls++;
    context.workspace=nativeWorkspaceReady
      ?{status:'ready',savedProject:false,definitionOnly:false,generation:7,tuneFingerprint:'live-tune',importedProfileName:'353'}
      :{status:'ready',savedProject:true,definitionOnly:true,generation:0,tuneFingerprint:'',importedProfileName:'353'};
  },
  readTuningFromEcu(){
    readCalls++;
    nativeStatus={status:'read_complete',operationRunning:false,generation:7,currentTuneFingerprint:'live-tune'};
    nativeWorkspaceReady=true;
  },
  // 1202 defers page-specific presentation placement until the combined browser script completes.
  // This harness exercises connection/read reconciliation, so keep that unrelated startup UI microtask deferred.
  queueMicrotask(){},
  setTimeout(fn){fn();return 1;},clearTimeout(){},Number,String,Object,JSON
};
windowObj.window=windowObj;
vm.runInNewContext(source,context,{filename:'t4_tuning_workspace_connection_refresh.js'});

const button=appStatus.insertedNode;
assert.ok(button,'Read ECU top action was not inserted');
assert.strictEqual(button.id,'t4twTopRead');
assert.strictEqual(button.disabled,true,'Read ECU must be disabled while disconnected');
assert.strictEqual(ecuState.bold.textContent,'ECU Offline','saved/definition-only workspace must not masquerade as a connected ECU');
assert.strictEqual(readCalls,0);

// Normal connection handshake already produced a complete current TuneSnapshot. The Tuner must
// adopt it immediately instead of queuing a redundant second full-tune read.
connected=true;
context.syncTunerChrome();
assert.strictEqual(readCalls,0,'handshake-ready live workspace must not trigger a redundant Read ECU');
assert.strictEqual(loadCalls,1,'first live connection must query the authoritative native workspace');
assert.strictEqual(context.workspace.definitionOnly,false);
assert.match(context.meta.textContent,/current native TuneSnapshot ready/);
assert.strictEqual(button.disabled,false);
assert.strictEqual(ecuState.bold.textContent,'ECU Connected');
context.syncTunerChrome();
assert.strictEqual(readCalls,0,'steady live telemetry must not repeat complete tune reads');

button.onclick();
assert.strictEqual(readCalls,1,'manual Read ECU remains available and uses the same native read path');
windowObj.EpicDashTuningWriteStateChanged();
assert.ok(loadCalls>=2,'native read-completion event must reconcile the live workspace');

connected=false;
context.syncTunerChrome();
assert.strictEqual(button.disabled,true);
assert.strictEqual(ecuState.bold.textContent,'ECU Offline');
const loadsAfterDisconnect=loadCalls;
context.syncTunerChrome();
assert.strictEqual(loadCalls,loadsAfterDisconnect,'steady disconnected telemetry must not repeatedly reload project state');

// If STREAMING becomes live but the native workspace is temporarily not ready, perform exactly one
// fallback Read ECU and adopt its result. This models the recovery path rather than normal startup.
nativeWorkspaceReady=false;
nativeStatus={status:'idle',operationRunning:false,generation:8,currentTuneFingerprint:null};
connected=true;
context.syncTunerChrome();
assert.strictEqual(readCalls,2,'unready live workspace must issue one fallback complete Read ECU');
assert.strictEqual(context.workspace.definitionOnly,false,'fallback completion must replace definition-only workspace');
context.syncTunerChrome();
assert.strictEqual(readCalls,2,'successful fallback must not repeat reads');


// Regression from physical 1204: if the connected transport remains live while the projected
// workspace temporarily falls back to definition/saved state, routine telemetry/chrome sync must
// not rebuild the complete workspace every frame. The connection edge/read completion paths own
// reconciliation; steady presentation is read-only.
nativeWorkspaceReady=false;
context.workspace={status:'ready',savedProject:true,definitionOnly:true,generation:0,tuneFingerprint:'',importedProfileName:'353'};
const steadyConnectedLoadCalls=loadCalls;
context.syncTunerChrome();
context.syncTunerChrome();
context.syncTunerChrome();
assert.strictEqual(loadCalls,steadyConnectedLoadCalls,'steady connected chrome sync must not reload an unavailable workspace');
assert.strictEqual(readCalls,2,'steady connected chrome sync must not queue another Read ECU');
nativeWorkspaceReady=true;

// If the ECU became live while Tuner was not active, entering Tuner later must reconcile the native
// handshake snapshot before considering an explicit read.
connected=false;pageActive=false;nativeWorkspaceReady=true;nativeStatus={status:'idle',operationRunning:false,generation:9,currentTuneFingerprint:'next-tune'};
context.syncTunerChrome();
connected=true;
context.syncTunerChrome();
assert.strictEqual(readCalls,2,'inactive Tuner must not start hidden complete reads');
pageActive=true;
windowListeners['epicdash:page-activated']?.({detail:{pageId:'page-tuning'}});
assert.strictEqual(readCalls,2,'activating Tuner with a handshake snapshot must not issue another read');
assert.strictEqual(context.workspace.definitionOnly,false);

console.log('Tuner live-workspace reconciliation/read fallback/connection-truth runtime test passed');
