'use strict';

const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('app/src/main/assets/t4_tuning_workspace_recovery.js','utf8');

class FakeNode {
  constructor(tag='div') {
    this.tagName=String(tag).toUpperCase();
    this.children=[];this.parentNode=null;this.hidden=false;this.disabled=false;
    this.className='';this.id='';this.type='';this.textContent='';this._innerHTML='';this.onclick=null;
  }
  set innerHTML(value) {
    this._innerHTML=String(value);
    if(this._innerHTML.includes('id="t4twRestoreProject"')) {
      const button=new FakeNode('button');button.id='t4twRestoreProject';button.parentNode=this;this.children=[button];
    }
  }
  get innerHTML(){return this._innerHTML;}
  get isConnected(){return !!this.parentNode || this.tagName==='HEAD';}
  append(...nodes){for(const node of nodes)this.appendChild(node);}
  appendChild(node){node.parentNode=this;this.children.push(node);return node;}
  querySelector(selector){
    if(selector==='#t4twRestoreProject')return this.findById('t4twRestoreProject');
    if(selector==='.t4tw-list')return this.findByClass('t4tw-list');
    if(selector==='.t4tw-shell')return this.findByClass('t4tw-shell');
    return null;
  }
  findById(id){if(this.id===id)return this;for(const child of this.children){const found=child.findById?.(id);if(found)return found;}return null;}
  findByClass(name){if(String(this.className).split(/\s+/).includes(name))return this;for(const child of this.children){const found=child.findByClass?.(name);if(found)return found;}return null;}
}

let connected=false;
let openCalls=0;
const head=new FakeNode('head');
const page=new FakeNode('section');
const list=new FakeNode('div');list.className='t4tw-list';page.appendChild(list);
const meta=new FakeNode('div');
const listeners={};
const context={
  console,
  document:{head,createElement:tag=>new FakeNode(tag)},
  window:{
    EpicDashTunerRecovery:{openRecoveryBundle(){openCalls++;return true;}},
    addEventListener(name,fn){listeners[name]=fn;}
  },
  page,list,meta,workspace:{status:'not_ready'},
  t4ProjectLiveConnected:()=>connected,
  render(){},syncTunerChrome(){},
  setTimeout(fn){fn();return 1;},clearTimeout(){},
  // This test exercises the no-project recovery surface only. The production asset also queues the
  // independent 1204 rotation installer; retain browser-compatible queueing without executing that
  // unrelated layout boot inside this deliberately minimal VM DOM.
  queueMicrotask(){},
  String,Number,Object,Array,JSON
};
context.window.window=context.window;
vm.runInNewContext(source,context,{filename:'t4_tuning_workspace_recovery.js'});

assert.strictEqual(list.children.length,1,'no-project recovery must replace stale Tuner content with one startup region');
const startup=list.children[0];
assert.ok(String(startup.className).includes('t4tw-recovery-startup'));
assert.strictEqual(startup.hidden,false,'recovery startup must be visible with no project and no ECU');
assert.strictEqual(startup.children.length,2,'startup region must contain the mode strip and restore panel');
const modes=startup.children[0];
assert.ok(String(modes.className).includes('t4tw-content-modes'));
assert.deepStrictEqual(modes.children.map(node=>node.textContent),['Parameters','Tables','Curves']);
assert.ok(modes.children.every(node=>node.disabled===true),'startup Parameters/Tables/Curves controls must be visibly present but inactive');
const restoreButton=startup.findById('t4twRestoreProject');
assert.ok(restoreButton,'restore button must be reachable in the no-project state');
restoreButton.onclick();
assert.strictEqual(openCalls,1,'restore button must invoke the native SAF recovery bridge exactly once');
assert.match(meta.textContent,/Select EpicDash-JZ-Tuner-Recovery\.json/);

context.workspace={status:'ready',definitionOnly:true};
context.render();
assert.strictEqual(startup.hidden,true,'recovery startup must disappear once an INI/project is available');

context.workspace={status:'not_ready'};connected=true;
context.syncTunerChrome();
assert.strictEqual(startup.hidden,true,'recovery startup must stay hidden while a live ECU is connected');

connected=false;
context.syncTunerChrome();
assert.strictEqual(startup.hidden,false,'recovery startup must return when both live ECU and semantic project are absent');

console.log('Tuner no-project recovery UI runtime test passed');
