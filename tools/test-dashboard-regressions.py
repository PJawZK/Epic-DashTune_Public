#!/usr/bin/env python3
"""Execute the dashboard's pure reconnect/stale decisions in Node."""
from pathlib import Path
import subprocess
import tempfile

text = (Path(__file__).resolve().parents[1] / "app/src/main/assets/dashboard_lab.html").read_text()

assert "let source = 'LIVE', scenario = 'live'" in text
assert "demoEnabled = false" in text
assert "setSourceChannels('LIVE',[]);demoEnabled=false" in text
assert 'id="demoToggleBtn">Enable Demo Data</button>' in text
assert 'id="demoIndicator"' in text and "DEMO DATA ACTIVE" in text
assert ".pill.hidden{display:none}" in text
assert "settings.demoEnabled" not in text
assert "setSourceChannels('DEMO',ECU_CHANNELS);updateValues(defaults,'DEMO');setScenario('idle')" not in text

def function_source(name: str) -> str:
    start = text.index(f"function {name}(")
    brace = text.index("{", start)
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{": depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0: return text[start:index + 1]
    raise RuntimeError(f"unterminated function {name}")

javascript = "\n".join(function_source(name) for name in (
    "nativeOrderingDecision", "staleCondition", "postSelfTestFreshnessDecision",
    "shouldRunDemo", "demoOwnsGaugeValues", "shouldLiveSupersedeDemo", "maintenanceIntervalMs",
    "shouldUpdateChannelInspector", "diagnosticsRefreshWanted"
)) + r"""
const assert = require('assert');

// DEMO is explicit-only and performs no synthesis while disabled, paused, or
// isolated by the self-test.
assert.strictEqual(shouldRunDemo({demoEnabled:false,source:'DEMO',paused:false,selfTestRunning:false}),false);
assert.strictEqual(shouldRunDemo({demoEnabled:true,source:'LIVE',paused:false,selfTestRunning:false}),false);
assert.strictEqual(shouldRunDemo({demoEnabled:true,source:'DEMO',paused:true,selfTestRunning:false}),false);
assert.strictEqual(shouldRunDemo({demoEnabled:true,source:'DEMO',paused:false,selfTestRunning:true}),false);
assert.strictEqual(shouldRunDemo({demoEnabled:true,source:'DEMO',paused:false,selfTestRunning:false}),true);
assert.strictEqual(demoOwnsGaugeValues({demoEnabled:false,source:'LIVE',selfTestRunning:false}),false);
assert.strictEqual(demoOwnsGaugeValues({demoEnabled:true,source:'DEMO',selfTestRunning:false}),true);
assert.strictEqual(demoOwnsGaugeValues({demoEnabled:true,source:'LIVE',selfTestRunning:false}),false);
assert.strictEqual(demoOwnsGaugeValues({demoEnabled:true,source:'DEMO',selfTestRunning:true}),false);
assert.strictEqual(shouldLiveSupersedeDemo({demoEnabled:true,connected:true}),true);
assert.strictEqual(shouldLiveSupersedeDemo({demoEnabled:true,connected:false}),false);
assert.strictEqual(maintenanceIntervalMs({performanceProfile:'full',source:'LIVE',liveConnectionActive:false}),1000);
assert.strictEqual(maintenanceIntervalMs({performanceProfile:'full',source:'LIVE',liveConnectionActive:true}),100);
assert.strictEqual(maintenanceIntervalMs({performanceProfile:'legacy',source:'LIVE',liveConnectionActive:false}),100);

// Healthy streaming is disarmed through self-test cleanup, so no production
// warning/overlay/incident owner can satisfy the stale predicate.
assert.strictEqual(staleCondition({scenario:'live',source:'LIVE',liveConnectionActive:true,
  staleMonitoringArmed:false,lastPacket:0,now:8000,threshold:750}), false);

// Dashboard acceptance may be delayed while authoritative current-session USB
// packet completion remains fresh. That is UI/bridge delay, not ECU staleness.
const healthyNative={usbSessionId:8,streaming:true,packetAgeMs:35,activeSessionFrames:2500};
const delayedDashboard={scenario:'live',source:'LIVE',liveConnectionActive:true,
  staleMonitoringArmed:true,lastPacket:1000,now:1900,threshold:750,transport:'usb',
  activeUsbSessionId:8,nativeFreshness:healthyNative};
const warningState={overlay:false,warningCount:4,incidentCount:2};
if(staleCondition(delayedDashboard)){warningState.overlay=true;warningState.warningCount++;warningState.incidentCount++;}
assert.deepStrictEqual(warningState,{overlay:false,warningCount:4,incidentCount:2});
assert.strictEqual(staleCondition({...delayedDashboard,
  nativeFreshness:{...healthyNative,packetAgeMs:900}}), true);
assert.strictEqual(staleCondition({...delayedDashboard,
  nativeFreshness:{...healthyNative,usbSessionId:7}}), true);
assert.strictEqual(shouldUpdateChannelInspector({force:false,analysisActive:false}),false);
assert.strictEqual(shouldUpdateChannelInspector({force:false,analysisActive:true}),true);
assert.strictEqual(diagnosticsRefreshWanted({diagnosticsActive:true,settingsActive:false}),true);
assert.strictEqual(diagnosticsRefreshWanted({diagnosticsActive:false,settingsActive:false}),false);

let state={session:8,revision:100};
let decision=nativeOrderingDecision({transport:'usb',usbSessionId:8,revision:101,connected:true},state.session,state.revision);
assert.strictEqual(decision.accepted,true); // strictly newer real same-session frame rearms
assert.strictEqual(postSelfTestFreshnessDecision(
  {transport:'usb',usbSessionId:8,revision:101,connected:true},decision,{session:8,revision:100}),true);
assert.strictEqual(postSelfTestFreshnessDecision(
  {transport:'usb',usbSessionId:8,revision:100,connected:true},{session:8,revision:100},{session:8,revision:100}),false);
state={session:decision.session,revision:decision.revision};
assert.strictEqual(staleCondition({scenario:'live',source:'LIVE',liveConnectionActive:true,
  staleMonitoringArmed:true,lastPacket:1000,now:1801,threshold:750}), true); // genuine stall

decision=nativeOrderingDecision({transport:'usb',usbSessionId:7,revision:999,connected:true},state.session,state.revision);
assert.strictEqual(decision.accepted,false); // old session cannot restore values
decision=nativeOrderingDecision({transport:'usb',usbSessionId:10,revision:102,connected:false},state.session,state.revision);
assert.strictEqual(decision.accepted,true); // physical/manual disconnect invalidation boundary
state={session:decision.session,revision:decision.revision};
decision=nativeOrderingDecision({transport:'usb',usbSessionId:8,revision:1000,connected:true},state.session,state.revision);
assert.strictEqual(decision.accepted,false);
decision=nativeOrderingDecision({transport:'usb',usbSessionId:11,revision:103,connected:true},state.session,state.revision);
assert.strictEqual(decision.accepted,true); // reconnect accepts only the new generation
console.log('Dashboard reconnect/stale regression tests passed');
"""

with tempfile.NamedTemporaryFile("w", suffix=".js") as script:
    script.write(javascript); script.flush()
    subprocess.run(["node", script.name], check=True)
